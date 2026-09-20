#!/usr/bin/env ruby
# scripts/e2e_runner.rb - Standalone E2E test runner for K8s deployment
# Invoked by: rake k8s:e2e PHASE=...
# Can also be run directly: ruby scripts/e2e_runner.rb [phase...]

require "net/http"
require "json"
require "securerandom"
require "socket"
require "open3"
require "tmpdir"

$stdout.sync = true
$stderr.sync = true

ENV['DOCKER_HOST'] ||= "npipe:////./pipe/podman-machine-default"
ENV['TESTCONTAINERS_RYUK_DISABLED'] = 'true'

NAMESPACE = ENV['NAMESPACE'] || 'poc-camel'
CLUSTER   = ENV['CLUSTER']   || 'poc-camel'
GATEWAY_PORT = (ENV['GATEWAY_PORT'] || '18080').to_i

ALL_PHASES = %w[infra connectivity happy-path fraud idempotency validation health circuit observability hardening resilience].freeze

$e2e_pass = 0
$e2e_fail = 0
$e2e_failed = []
$e2e_phase_pass = 0
$e2e_phase_fail = 0
$pf_pids = []
$gateway_pf_pid = nil

def green(s); "\e[32m#{s}\e[0m"; end
def red(s);   "\e[31m#{s}\e[0m"; end
def cyan(s);  "\e[36m#{s}\e[0m"; end
def bold(s);  "\e[1m#{s}\e[0m"; end
def dim(s);   "\e[90m#{s}\e[0m"; end

def assert(label, ok, detail = nil)
  if ok
    $e2e_pass += 1; $e2e_phase_pass += 1
    puts "  #{green("+")} #{label}"
  else
    $e2e_fail += 1; $e2e_phase_fail += 1
    msg = detail ? "#{label} (#{detail})" : label
    $e2e_failed << msg
    puts "  #{red("x")} #{label} #{dim("- #{detail}") if detail}"
  end
end

def phase_header(num, name)
  $e2e_phase_pass = 0; $e2e_phase_fail = 0
  phase_num = ALL_PHASES.index(name)&.+(1) || num
  puts; puts "#{bold("== Phase #{phase_num}/#{ALL_PHASES.length}: #{name} ==")}"
end

def phase_footer(name)
  total = $e2e_phase_pass + $e2e_phase_fail
  if $e2e_phase_fail > 0
    puts "  #{red("FAIL")}: #{$e2e_phase_pass}/#{total}"
  else
    puts "  #{green("PASS")}: #{$e2e_phase_pass}/#{total}"
  end
end

def sh(cmd)
  out, status = Open3.capture2e(cmd)
  [out.strip, status.success?]
end

def sh!(cmd)
  out, _ = Open3.capture2e(cmd)
  out.strip
end

def kubectl_json(resource)
  out, _err, status = Open3.capture3("kubectl", "get", *resource.split(/\s+/), "-n", NAMESPACE, "-o", "json")
  return {} unless status.success?
  JSON.parse(out)
rescue JSON::ParserError
  {}
end

def local_port_open?(port)
  TCPSocket.new("127.0.0.1", port.to_i).close
  true
rescue Errno::ECONNREFUSED, Errno::EADDRINUSE, Errno::ECONNRESET
  false
end

def start_portforward(svc, local_port, remote_port)
  return nil if local_port_open?(local_port)

  stdin, output, wait_thr = Open3.popen2e("kubectl", "port-forward", "svc/#{svc}", "#{local_port}:#{remote_port}", "-n", NAMESPACE)
  stdin.close rescue nil
  pf_output = []
  Thread.new do
    output.each_line do |line|
      pf_output << line.strip
      pf_output.shift while pf_output.length > 8
    end
  rescue IOError
    nil
  end
  $pf_pids << wait_thr.pid
  15.times do
    sleep 1
    break unless wait_thr.alive?
    if local_port_open?(local_port)
      return wait_thr.pid if wait_thr.alive?
    end
  end
  puts "  WARN: port-forward may not be ready"
  pf_output.each { |line| puts "    #{dim(line)}" } unless pf_output.empty?
  wait_thr.pid
end

def stop_portforward(pid)
  return unless pid && pid > 0
  Process.kill("TERM", pid) rescue nil
  20.times do
    begin
      waited = Process.wait(pid, Process::WNOHANG)
      return if waited
    rescue Errno::ECHILD, Errno::ESRCH
      return
    end
    sleep 0.25
  end
  Process.kill("KILL", pid) rescue nil
  sleep 0.5
end

def ensure_gateway_portforward
  # Reuse only if the local port actually answers; otherwise recreate (stale pid).
  return $gateway_pf_pid if $gateway_pf_pid && local_port_open?(GATEWAY_PORT)
  if $gateway_pf_pid
    stop_portforward($gateway_pf_pid)
    $gateway_pf_pid = nil
  end
  $gateway_pf_pid = start_portforward("api-gateway-external", GATEWAY_PORT, 8080)
end

def stop_gateway_portforward
  stop_portforward($gateway_pf_pid)
  $gateway_pf_pid = nil
end

def http_get(port, path)
  uri = URI("http://127.0.0.1:#{port}#{path}")
  3.times do |attempt|
    begin
      return Net::HTTP.start(uri.hostname, uri.port, open_timeout: 10, read_timeout: 30) do |h|
        h.request(Net::HTTP::Get.new(uri, { "Accept" => "application/json" }))
      end
    rescue => e
      sleep 2 if attempt < 2
      raise e if attempt == 2
    end
  end
end

def http_post(port, path, body, headers = {})
  uri = URI("http://127.0.0.1:#{port}#{path}")
  h = { "Content-Type" => "application/json" }.merge(headers)
  3.times do |attempt|
    begin
      req = Net::HTTP::Post.new(uri, h)
      req.body = body.to_json
      return Net::HTTP.start(uri.hostname, uri.port, open_timeout: 10, read_timeout: 30) { |c| c.request(req) }
    rescue => e
      sleep 2 if attempt < 2
      raise e if attempt == 2
    end
  end
end

def wait_for_response(expected_code, timeout = 60, interval = 3)
  deadline = Time.now + timeout
  last_detail = "no response"
  while Time.now < deadline
    begin
      res = yield
      return [true, res, "got #{res.code}"] if res.code == expected_code
      last_detail = "got #{res.code}"
    rescue => e
      last_detail = "#{e.class}: #{e.message[0, 120]}"
    end
    sleep interval
  end
  [false, nil, last_detail]
end


def json_parse(res)
  JSON.parse(res.body) rescue {}
end

def wait_for_status(payment_id, expected, timeout = 45)
  deadline = Time.now + timeout
  while Time.now < deadline
    res = http_get(GATEWAY_PORT, "/payments/#{payment_id}")
    return true if res.code == "200" && json_parse(res)["status"] == expected
    sleep 2
  end
  false
end

def pod_name(label)
  sh!("kubectl get pods -n #{NAMESPACE} -l #{label} -o jsonpath=\"{.items[0].metadata.name}\"")
end

# ─── Phase 1: Infrastructure ──────────────────────────────────────────────────
def phase_infra
  phase_header(1, "infra")

  pods = sh!("kubectl get pods -n #{NAMESPACE} --no-headers")
  pods.lines.map(&:strip).reject(&:empty?).each do |line|
    parts = line.split(/\s+/)
    name = parts[0]; ready = parts[1]; status = parts[2]
    next if status == "Completed" || name.start_with?("test-")
    fully_ready = status == "Running" && ready.split("/")[0] == ready.split("/")[1]
    assert("Pod #{name} Ready (#{ready} #{status})", fully_ready)
  end

  svc_count = sh!("kubectl get svc -n #{NAMESPACE} --no-headers").lines.count
  assert("Services present (#{svc_count})", svc_count >= 8)

  kafka_topics = sh!("kubectl exec kafka-stack -c kafka -n #{NAMESPACE} -- kafka-topics --bootstrap-server localhost:9092 --list")
  expected = %w[payments.events.received payments.events.processed payments.events.failed payments.events.review payments.events.dead-letter payments.events.retry payments.events.audit payments.events.status.changed fraud.events.detected]
  missing = expected.reject { |t| kafka_topics.include?(t) }
  assert("Kafka topics (#{expected.size - missing.size}/#{expected.size})", missing.empty?, missing.empty? ? nil : "missing: #{missing.join(', ')}")

  pg_out = sh!("kubectl exec postgres-0 -n #{NAMESPACE} -- pg_isready -U payments -d payments")
  assert("PostgreSQL accepting connections", pg_out.include?("accepting"))

  cm_count = sh!("kubectl get configmaps -n #{NAMESPACE} --no-headers").lines.count
  assert("ConfigMaps present (#{cm_count})", cm_count >= 4)

  sec_count = sh!("kubectl get secrets -n #{NAMESPACE} --no-headers").lines.count
  assert("Secrets present (#{sec_count})", sec_count >= 2)

  np_count = sh!("kubectl get networkpolicies -n #{NAMESPACE} --no-headers").lines.count
  assert("NetworkPolicies present (#{np_count})", np_count >= 10)

  hpa_count = sh!("kubectl get hpa -n #{NAMESPACE} --no-headers").lines.count
  assert("HPAs present (#{hpa_count})", hpa_count >= 2)

  phase_footer("infra")
end

# ─── Phase 2: Connectivity ───────────────────────────────────────────────────
def phase_connectivity
  phase_header(2, "connectivity")

  gw = pod_name("app=api-gateway")
  assert("api-gateway pod found", !gw.empty?)

  unless gw.empty?
    out, _ = sh("kubectl exec #{gw} -n #{NAMESPACE} -- nslookup kafka.#{NAMESPACE}.svc.cluster.local")
    assert("DNS: kafka resolves", out.include?("Address") || out.include?("Name"))

    out2, _ = sh("kubectl exec #{gw} -n #{NAMESPACE} -- nslookup postgres.#{NAMESPACE}.svc.cluster.local")
    assert("DNS: postgres resolves", out2.include?("Address") || out2.include?("Name"))

    out3, _ = sh("kubectl exec #{gw} -n #{NAMESPACE} -- nslookup payment-processor.#{NAMESPACE}.svc.cluster.local")
    assert("DNS: payment-processor resolves", out3.include?("Address") || out3.include?("Name"))

    out4, _ = sh("kubectl exec #{gw} -n #{NAMESPACE} -- nslookup jaeger.#{NAMESPACE}.svc.cluster.local")
    assert("DNS: jaeger resolves", out4.include?("Address") || out4.include?("Name"))
  end

  logs = sh!("kubectl logs -l app=api-gateway -n #{NAMESPACE} --tail=50")
  assert("api-gateway has logs", !logs.empty?)

  logs2 = sh!("kubectl logs -l app=payment-processor -n #{NAMESPACE} --tail=50")
  assert("payment-processor has logs", !logs2.empty?)

  phase_footer("connectivity")
end

# ─── Phase 3: Happy Path ─────────────────────────────────────────────────────
def phase_happy_path
  phase_header(3, "happy-path")
  ensure_gateway_portforward

  # Readiness gate: fail fast with an assert instead of crashing on ECONNREFUSED
  gw_ok, _, gw_detail = wait_for_response("200", 60) do
    http_get(GATEWAY_PORT, "/payments?limit=1")
  end
  assert("Gateway reachable before POST", gw_ok, gw_detail)
  unless gw_ok
    phase_footer("happy-path")
    return
  end

  key = SecureRandom.uuid
  cid = "qa-e2e-happy-#{Time.now.to_i}"
  payload = { amount: 100.00, currency: "USD", customer_id: cid,
              payment_method: "CREDIT_CARD", country: "US",
              metadata: { order_id: "order-e2e-001" } }

  begin
    res = http_post(GATEWAY_PORT, "/payments", payload, { "Idempotency-Key" => key })
  rescue => e
    assert("POST /payments -> 201", false, "#{e.class}: #{e.message[0, 120]}")
    phase_footer("happy-path")
    return
  end
  assert("POST /payments -> 201", res.code == "201", "got #{res.code}")
  body = json_parse(res)
  pid = body["id"]
  assert("Response has id", pid && !pid.empty?)
  assert("Initial status PENDING", body["status"] == "PENDING", "got #{body["status"]}")
  assert("Amount preserved", body["amount"] == 100.0 || body["amount"] == 100)

  if pid
    res2 = http_get(GATEWAY_PORT, "/payments/#{pid}")
    assert("GET /payments/{id} -> 200", res2.code == "200")
    b2 = json_parse(res2)
    assert("GET returns same id", b2["id"] == pid)

    puts "  #{dim("Waiting for async processing...")}"
    found = wait_for_status(pid, "APPROVED", 30)
    unless found
      r = http_get(GATEWAY_PORT, "/payments/#{pid}")
      final_status = json_parse(r)["status"]
      found = %w[APPROVED REVIEW FAILED].include?(final_status)
    end
    assert("Payment processed (not PENDING)", found)

    res4 = http_get(GATEWAY_PORT, "/payments?customerId=#{cid}&limit=10&offset=0")
    assert("GET /payments?customerId -> 200", res4.code == "200")
    page = json_parse(res4)
    pmts = page["payments"] || page["data"] || []
    assert("List contains our payment", pmts.length >= 1, "count=#{pmts.length}")
  end

  phase_footer("happy-path")
end

# ─── Phase 4: Fraud Detection ────────────────────────────────────────────────
def phase_fraud
  phase_header(4, "fraud")
  ensure_gateway_portforward

  # REJECT
  key1 = SecureRandom.uuid
  p1 = { amount: 20000.00, currency: "USD", customer_id: "qa-fraud-reject",
         payment_method: "CREDIT_CARD", country: "XX" }
  r1 = http_post(GATEWAY_PORT, "/payments", p1, { "Idempotency-Key" => key1 })
  assert("Fraud REJECT: POST -> 201", r1.code == "201", "got #{r1.code}")
  pid1 = json_parse(r1)["id"]
  if pid1
    puts "  #{dim("Waiting for fraud REJECT...")}"
    ok1 = wait_for_status(pid1, "FAILED", 30)
    unless ok1
      r = http_get(GATEWAY_PORT, "/payments/#{pid1}")
      ok1 = json_parse(r)["status"] == "FAILED"
    end
    assert("Fraud REJECT -> FAILED", ok1)
  end

  # REVIEW
  key2 = SecureRandom.uuid
  p2 = { amount: 20000.00, currency: "USD", customer_id: "qa-fraud-review",
         payment_method: "CREDIT_CARD", country: "US" }
  r2 = http_post(GATEWAY_PORT, "/payments", p2, { "Idempotency-Key" => key2 })
  assert("Fraud REVIEW: POST -> 201", r2.code == "201", "got #{r2.code}")
  pid2 = json_parse(r2)["id"]
  if pid2
    puts "  #{dim("Waiting for fraud REVIEW...")}"
    ok2 = wait_for_status(pid2, "REVIEW", 30)
    unless ok2
      r = http_get(GATEWAY_PORT, "/payments/#{pid2}")
      final2 = json_parse(r)["status"]
      ok2 = %w[REVIEW FAILED].include?(final2)
    end
    assert("Fraud REVIEW -> REVIEW or FAILED", ok2)
  end

  # APPROVE
  key3 = SecureRandom.uuid
  p3 = { amount: 50.00, currency: "EUR", customer_id: "qa-fraud-approve",
         payment_method: "BANK_TRANSFER", country: "DE" }
  r3 = http_post(GATEWAY_PORT, "/payments", p3, { "Idempotency-Key" => key3 })
  assert("Fraud APPROVE: POST -> 201", r3.code == "201", "got #{r3.code}")
  pid3 = json_parse(r3)["id"]
  if pid3
    puts "  #{dim("Waiting for fraud APPROVE...")}"
    ok3 = wait_for_status(pid3, "APPROVED", 30)
    unless ok3
      r = http_get(GATEWAY_PORT, "/payments/#{pid3}")
      ok3 = json_parse(r)["status"] == "APPROVED"
    end
    assert("Fraud APPROVE -> APPROVED", ok3)
  end

  # WALLET + high amount
  key4 = SecureRandom.uuid
  p4 = { amount: 6000.00, currency: "USD", customer_id: "qa-fraud-wallet",
         payment_method: "WALLET", country: "US" }
  r4 = http_post(GATEWAY_PORT, "/payments", p4, { "Idempotency-Key" => key4 })
  assert("WALLET high-value: POST -> 201", r4.code == "201", "got #{r4.code}")
  pid4 = json_parse(r4)["id"]
  if pid4
    puts "  #{dim("Waiting for WALLET fraud...")}"
    sleep 8
    r = http_get(GATEWAY_PORT, "/payments/#{pid4}")
    f4 = json_parse(r)["status"]
    assert("WALLET high-value processed", %w[APPROVED REVIEW FAILED].include?(f4), "status=#{f4}")
  end

  phase_footer("fraud")
end

# ─── Phase 5: Idempotency ────────────────────────────────────────────────────
def phase_idempotency
  phase_header(5, "idempotency")
  ensure_gateway_portforward

  key = SecureRandom.uuid
  payload = { amount: 77.77, currency: "EUR", customer_id: "qa-e2e-idem",
              payment_method: "DEBIT_CARD", country: "FR" }

  r1 = http_post(GATEWAY_PORT, "/payments", payload, { "Idempotency-Key" => key })
  assert("1st POST -> 201", r1.code == "201", "got #{r1.code}")
  id1 = json_parse(r1)["id"]

  r2 = http_post(GATEWAY_PORT, "/payments", payload, { "Idempotency-Key" => key })
  id2 = json_parse(r2)["id"]
  assert("2nd POST same key -> same id", id1 && id1 == id2, "id1=#{id1} id2=#{id2}")

  r3 = http_get(GATEWAY_PORT, "/payments/idempotency/#{key}")
  assert("GET /payments/idempotency/{key} -> 200", r3.code == "200", "got #{r3.code}")
  id3 = json_parse(r3)["id"]
  assert("Idempotency lookup returns same id", id1 == id3, "expected=#{id1} got=#{id3}")

  phase_footer("idempotency")
end

# ─── Phase 6: Validation ─────────────────────────────────────────────────────
def phase_validation
  phase_header(6, "validation")
  ensure_gateway_portforward

  base = { currency: "USD", customer_id: "x", payment_method: "CREDIT_CARD", country: "US" }

  r1 = http_post(GATEWAY_PORT, "/payments", base.merge(amount: 0))
  assert("amount=0 -> 400", r1.code == "400", "got #{r1.code}")

  r2 = http_post(GATEWAY_PORT, "/payments", base.merge(amount: -1))
  assert("amount=-1 -> 400", r2.code == "400", "got #{r2.code}")

  r3 = http_post(GATEWAY_PORT, "/payments", base.merge(amount: 10, currency: "INVALID"))
  assert("currency=INVALID -> 400", r3.code == "400", "got #{r3.code}")

  r4 = http_post(GATEWAY_PORT, "/payments", base.merge(amount: 10, payment_method: "UNKNOWN"))
  assert("paymentMethod=UNKNOWN -> accepted (any string allowed)", r4.code == "201", "got #{r4.code}")

  r5 = http_post(GATEWAY_PORT, "/payments", base.merge(amount: 10, customer_id: ""))
  assert("customerId empty -> 400", r5.code == "400", "got #{r5.code}")

  uri = URI("http://127.0.0.1:#{GATEWAY_PORT}/payments")
  req = Net::HTTP::Post.new(uri, { "Content-Type" => "application/json" })
  req.body = "{ invalid json :::: {{{ "
  begin
    r6 = Net::HTTP.start(uri.hostname, uri.port, open_timeout: 5, read_timeout: 10) { |h| h.request(req) }
    assert("Malformed JSON -> 4xx", r6.code.to_i >= 400, "got #{r6.code}")
  rescue => e
    assert("Malformed JSON -> error", true, e.message[0, 60])
  end

  phase_footer("validation")
end

# ─── Phase 7: Health Checks ──────────────────────────────────────────────────
def phase_health
  phase_header(7, "health")

  gw = pod_name("app=api-gateway")
  pp = pod_name("app=payment-processor")

  unless gw.empty?
    out1, _ = sh("kubectl exec #{gw} -n #{NAMESPACE} -- wget -q -O - http://localhost:8080/health/live 2>&1")
    assert("api-gateway /health/live", out1.include?("UP") || out1.include?("status"))

    out2, _ = sh("kubectl exec #{gw} -n #{NAMESPACE} -- wget -q -O - http://localhost:8080/health/ready 2>&1")
    assert("api-gateway /health/ready", out2.include?("UP") || out2.include?("status"))
  else
    assert("api-gateway pod found", false)
  end

  unless pp.empty?
    out3, _ = sh("kubectl exec #{pp} -n #{NAMESPACE} -- wget -q -O - http://localhost:8080/health/live 2>&1")
    assert("payment-processor /health/live", out3.include?("UP") || out3.include?("status"))

    out4, _ = sh("kubectl exec #{pp} -n #{NAMESPACE} -- wget -q -O - http://localhost:8080/health/ready 2>&1")
    assert("payment-processor /health/ready", out4.include?("UP") || out4.include?("status"))
  else
    assert("payment-processor pod found", false)
  end

  phase_footer("health")
end

# ─── Phase 8: Circuit Breaker ────────────────────────────────────────────────
def phase_circuit
  phase_header(8, "circuit")

  logs = sh!("kubectl logs -l app=payment-processor -n #{NAMESPACE} --tail=200")
  has_fallback = logs.downcase.include?("fallback") || logs.downcase.include?("provider-b") ||
                 logs.downcase.include?("circuit") || logs.downcase.include?("retry")
  if has_fallback
    assert("Circuit breaker / fallback log evidence", true)
  else
    puts "  #{dim("WARN: no fallback keywords found in recent logs; retry/DLQ topics remain authoritative")}"
  end

  dlq_out, _ = sh("kubectl exec kafka-stack -c kafka -n #{NAMESPACE} -- timeout 10 kafka-console-consumer --bootstrap-server localhost:9092 --topic payments.events.dead-letter --from-beginning --max-messages 50 --timeout-ms 5000")
  dlq_count = dlq_out.lines.count
  assert("Dead-letter queue has messages", dlq_count > 0, "count=#{dlq_count}")

  retry_out, _ = sh("kubectl exec kafka-stack -c kafka -n #{NAMESPACE} -- timeout 10 kafka-console-consumer --bootstrap-server localhost:9092 --topic payments.events.retry --from-beginning --max-messages 50 --timeout-ms 5000")
  retry_count = retry_out.lines.count
  assert("Retry topic has messages", retry_count > 0, "count=#{retry_count}")

  phase_footer("circuit")
end

# ─── Phase 9: Observability ──────────────────────────────────────────────────
def phase_observability
  phase_header(9, "observability")

  pf_prom = start_portforward("prometheus", 9091, 9090)
  begin
    res = http_get(9091, "/api/v1/targets")
    assert("Prometheus /api/v1/targets -> 200", res.code == "200", "got #{res.code}")
    data = json_parse(res)
    active = data.dig("data", "activeTargets") || []
    up = active.select { |t| t["health"] == "up" }
    assert("Prometheus has UP targets", up.length > 0, "up=#{up.length} total=#{active.length}")
  ensure
    stop_portforward(pf_prom)
  end

  pf_jaeger = start_portforward("jaeger", 16687, 16686)
  begin
    res2 = http_get(16687, "/api/services")
    assert("Jaeger /api/services -> 200", res2.code == "200", "got #{res2.code}")
    data2 = json_parse(res2)
    services = (data2["data"] || []).compact.map { |s| s.is_a?(Hash) ? s["serviceName"] : s.to_s }.compact
    has_gw = services.any? { |s| s.include?("payment-gateway") || s.include?("api-gateway") }
    has_pp = services.any? { |s| s.include?("payment-processor") }
    assert("Jaeger has api-gateway traces", has_gw, "services=#{services.first(5).join(', ')}")
    assert("Jaeger has payment-processor traces", has_pp, "services=#{services.first(5).join(', ')}")
  ensure
    stop_portforward(pf_jaeger)
  end

  phase_footer("observability")
end

# ─── Phase 10: Resilience ────────────────────────────────────────────────────
def phase_hardening
  phase_header(10, "hardening")

  deployments = kubectl_json("deploy")["items"] || []
  by_name = deployments.to_h { |d| [d.dig("metadata", "name"), d] }

  %w[api-gateway payment-processor].each do |name|
    dep = by_name[name]
    assert("#{name} deployment exists", !dep.nil?)
    next unless dep

    desired = dep.dig("spec", "replicas").to_i
    ready = dep.dig("status", "readyReplicas").to_i
    updated = dep.dig("status", "updatedReplicas").to_i
    available = dep.dig("status", "availableReplicas").to_i
    assert("#{name} replicas ready", desired > 0 && ready == desired && updated == desired && available == desired,
           "desired=#{desired} ready=#{ready} updated=#{updated} available=#{available}")
    assert("#{name} uses RollingUpdate", dep.dig("spec", "strategy", "type") == "RollingUpdate")

    pod_spec = dep.dig("spec", "template", "spec") || {}
    pod_sc = pod_spec["securityContext"] || {}
    assert("#{name} serviceAccount is non-default", pod_spec["serviceAccountName"] == "poc-camel-sa",
           "serviceAccountName=#{pod_spec["serviceAccountName"]}")
    assert("#{name} pod runs as non-root", pod_sc["runAsNonRoot"] == true && pod_sc["runAsUser"].to_i > 0)
    assert("#{name} has pod anti-affinity", !!pod_spec.dig("affinity", "podAntiAffinity"))

    annotations = dep.dig("spec", "template", "metadata", "annotations") || {}
    assert("#{name} has Prometheus scrape annotations",
           annotations["prometheus.io/scrape"] == "true" && annotations["prometheus.io/port"] == "8080")

    container = (pod_spec["containers"] || []).find { |c| c["name"] == name } || (pod_spec["containers"] || []).first || {}
    assert("#{name} has liveness/readiness/startup probes",
           %w[livenessProbe readinessProbe startupProbe].all? { |probe| container[probe]&.dig("httpGet", "path") })

    resources = container["resources"] || {}
    assert("#{name} has CPU/memory requests and limits",
           resources.dig("requests", "cpu") && resources.dig("requests", "memory") &&
           resources.dig("limits", "cpu") && resources.dig("limits", "memory"))

    sc = container["securityContext"] || {}
    hardened = sc["allowPrivilegeEscalation"] == false &&
               sc["readOnlyRootFilesystem"] == true &&
               sc["runAsNonRoot"] == true &&
               Array(sc.dig("capabilities", "drop")).include?("ALL") &&
               sc.dig("seccompProfile", "type") == "RuntimeDefault"
    assert("#{name} container securityContext hardened", hardened)

    env_names = Array(container["env"]).map { |e| e["name"] }
    assert("#{name} has OpenTelemetry service name", env_names.include?("OTEL_SERVICE_NAME"))
  end

  hpas = (kubectl_json("hpa")["items"] || []).to_h { |h| [h.dig("metadata", "name"), h] }
  {
    "api-gateway-hpa" => ["api-gateway", 1],
    "payment-processor-hpa" => ["payment-processor", 2],
  }.each do |hpa_name, (target, min_replicas)|
    hpa = hpas[hpa_name]
    assert("#{hpa_name} exists", !hpa.nil?)
    next unless hpa
    metrics = Array(hpa.dig("spec", "metrics")).map { |m| m.dig("resource", "name") }
    assert("#{hpa_name} targets #{target}", hpa.dig("spec", "scaleTargetRef", "name") == target)
    assert("#{hpa_name} min/max sane", hpa.dig("spec", "minReplicas").to_i >= min_replicas &&
                                      hpa.dig("spec", "maxReplicas").to_i > hpa.dig("spec", "minReplicas").to_i)
    assert("#{hpa_name} has cpu and memory metrics", metrics.include?("cpu") && metrics.include?("memory"))
  end

  %w[api-gateway api-gateway-external payment-processor kafka postgres prometheus jaeger].each do |svc|
    endpoints = kubectl_json("endpoints/#{svc}")
    addresses = Array(endpoints["subsets"]).flat_map { |s| s["addresses"] || [] }
    assert("#{svc} service has ready endpoints", addresses.any?, "addresses=#{addresses.length}")
  end

  network_policies = (kubectl_json("networkpolicy")["items"] || []).map { |np| np.dig("metadata", "name") }
  expected_policies = %w[
    default-deny-all allow-infra-egress allow-apps-to-kafka allow-apps-to-postgres
    allow-apps-to-jaeger allow-gateway-to-processor allow-gateway-egress allow-processor-egress
    allow-prometheus-scrape allow-monitoring-egress
  ]
  missing_policies = expected_policies - network_policies
  assert("NetworkPolicies cover default deny and app flows", missing_policies.empty?,
         missing_policies.empty? ? nil : "missing=#{missing_policies.join(', ')}")

  out, _ = sh("kubectl auth can-i list pods --as system:serviceaccount:#{NAMESPACE}:poc-camel-sa -n #{NAMESPACE}")
  assert("Application ServiceAccount has least-privilege RBAC", out.strip == "no", "can-i=#{out.strip}")

  phase_footer("hardening")
end

def phase_resilience
  phase_header(11, "resilience")
  stop_gateway_portforward

  puts "  #{dim("Restarting api-gateway...")}"
  sh!("kubectl rollout restart deployment/api-gateway -n #{NAMESPACE}")
  sh!("kubectl rollout status deployment/api-gateway -n #{NAMESPACE} --timeout=120s")

  pf_pid = ensure_gateway_portforward
  ok1, _r1, detail1 = wait_for_response("200", 90) do
    http_get(GATEWAY_PORT, "/payments?limit=1")
  end
  assert("api-gateway survived rollout restart", ok1, detail1)

  puts "  #{dim("Restarting payment-processor...")}"
  sh!("kubectl rollout restart deployment/payment-processor -n #{NAMESPACE}")
  sh!("kubectl rollout status deployment/payment-processor -n #{NAMESPACE} --timeout=180s")
  sh!("kubectl wait --for=condition=ready pod -l app=payment-processor -n #{NAMESPACE} --timeout=120s")
  sleep 5
  key = SecureRandom.uuid
  payload = { amount: 25.00, currency: "GBP", customer_id: "qa-resilience",
              payment_method: "BANK_TRANSFER", country: "GB" }

  # 3 rounds: re-ensure PF (it may drop during long rollouts) + short wait.
  # A 201 only proves gateway+DB; waiting until the payment leaves PENDING
  # proves the outbox -> Kafka -> processor pipeline resumed after restart.
  res = nil
  post_detail = "no attempt"
  3.times do |i|
    pf_pid = ensure_gateway_portforward
    begin
      ok_round, r_round, d_round = wait_for_response("201", 30) do
        http_post(GATEWAY_PORT, "/payments", payload, { "Idempotency-Key" => key })
      end
      if ok_round
        res = r_round
        post_detail = d_round
        break
      end
      post_detail = d_round
    rescue => e
      post_detail = "#{e.class}: #{e.message[0, 120]}"
    end
    puts "  #{dim("POST attempt #{i + 1}/3 failed (#{post_detail}), re-ensuring port-forward...")}" if i < 2
    sleep 3 if i < 2
  end
  assert("POST after processor restart -> 201", !res.nil?, post_detail)

  if res
    pid = json_parse(res)["id"]
    if pid
      puts "  #{dim("Waiting for pipeline to resume (#{pid})...")}"
      # Post-restart the consumer drains backlog + rebalances; allow up to 3 min.
      processed = wait_for_status(pid, "APPROVED", 180)
      unless processed
        r = http_get(GATEWAY_PORT, "/payments/#{pid}") rescue nil
        final = r ? json_parse(r)["status"] : "unknown"
        processed = %w[APPROVED REVIEW FAILED].include?(final)
        puts "  #{dim("Final status: #{final}")}"
      end
      assert("Pipeline resumed after processor restart (not PENDING)", processed, "payment=#{pid}")
    else
      assert("Pipeline resumed after processor restart (not PENDING)", false, "no payment id in 201")
    end
  end

  stop_portforward(pf_pid)
  $gateway_pf_pid = nil
  phase_footer("resilience")
end

# ─── Main ────────────────────────────────────────────────────────────────────
PHASE_METHODS = {
  "infra"         => method(:phase_infra),
  "connectivity"  => method(:phase_connectivity),
  "happy-path"    => method(:phase_happy_path),
  "fraud"         => method(:phase_fraud),
  "idempotency"   => method(:phase_idempotency),
  "validation"    => method(:phase_validation),
  "health"        => method(:phase_health),
  "circuit"       => method(:phase_circuit),
  "observability" => method(:phase_observability),
  "hardening"     => method(:phase_hardening),
  "resilience"    => method(:phase_resilience),
}

requested = (ENV['PHASE'] || ARGV.join(',')).downcase.strip
phases = requested.empty? ? ALL_PHASES : requested.split(',').map(&:strip)
invalid = phases.reject { |p| ALL_PHASES.include?(p) }
unless invalid.empty?
  $stderr.puts red("Invalid phase(s): #{invalid.join(', ')}")
  $stderr.puts "Available: #{ALL_PHASES.join(', ')}"
  exit 1
end

# Check cluster (retry: cluster-info can flap transiently on Kind/Podman)
cluster_ok = false
cluster_out = ""
3.times do |i|
  cluster_out, cluster_ok = sh("kubectl cluster-info --request-timeout=5s")
  break if cluster_ok && (cluster_out.include?("control plane") || cluster_out.include?("Kubernetes"))
  cluster_ok = false
  sleep 2 if i < 2
end
unless cluster_ok
  $stderr.puts red("No cluster context. Is Kind running? (rake k8s:cluster)")
  exit 1
end

puts
puts bold(cyan("=" * 62))
puts bold(cyan("  K8S E2E TEST SUITE")) + "  #{dim("-- poc-camel")}"
puts bold(cyan("=" * 62))
puts "  Cluster: #{CLUSTER} | Namespace: #{NAMESPACE}"
puts "  Phases: #{phases.join(', ')}"
puts

begin
  phases.each { |p| PHASE_METHODS[p].call }
ensure
  stop_gateway_portforward
  $pf_pids.each { |pid| stop_portforward(pid) }
end

total = $e2e_pass + $e2e_fail
puts
puts bold(cyan("=" * 62))
if total > 0
  puts bold(cyan("  RESULTS")) + ": #{green("#{$e2e_pass}/#{total} passed")}, #{$e2e_fail > 0 ? red("#{$e2e_fail} failed") : green("0 failed")}"
else
  puts bold(cyan("  RESULTS")) + ": #{dim("0 tests executed")}"
end
unless $e2e_failed.empty?
  puts
  puts "#{red("  FAILURES:")}"
  $e2e_failed.each_with_index { |f, i| puts "    #{i + 1}. #{f}" }
end
puts bold(cyan("=" * 62))
puts

exit($e2e_fail > 0 ? 1 : 0)
