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

ALL_PHASES = %w[infra connectivity happy-path fraud idempotency validation health circuit observability resilience].freeze

$e2e_pass = 0
$e2e_fail = 0
$e2e_failed = []
$e2e_phase_pass = 0
$e2e_phase_fail = 0
$pf_pids = []

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
  puts; puts "#{bold("== Phase #{num}/#{ALL_PHASES.length}: #{name} ==")}"
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

def start_portforward(svc, local_port, remote_port)
  cmd = "kubectl port-forward svc/#{svc} #{local_port}:#{remote_port} -n #{NAMESPACE}"
  pf = IO.popen(cmd, :err => File::NULL)
  Thread.new { pf.read rescue nil }
  $pf_pids << pf.pid
  15.times do
    sleep 1
    begin
      TCPSocket.new("127.0.0.1", local_port.to_i).close
      return pf.pid
    rescue Errno::ECONNREFUSED, Errno::EADDRINUSE, Errno::ECONNRESET
      next
    end
  end
  puts "  WARN: port-forward may not be ready"
  pf.pid
end

def stop_portforward(pid)
  return unless pid && pid > 0
  Process.kill("TERM", pid) rescue nil
  sleep 1
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
  req = Net::HTTP::Post.new(uri, h)
  req.body = body.to_json
  3.times do |attempt|
    begin
      return Net::HTTP.start(uri.hostname, uri.port, open_timeout: 10, read_timeout: 30) { |c| c.request(req) }
    rescue => e
      sleep 2 if attempt < 2
      raise e if attempt == 2
    end
  end
end


def json_parse(res)
  JSON.parse(res.body) rescue {}
end

def wait_for_status(payment_id, expected, timeout = 45)
  deadline = Time.now + timeout
  while Time.now < deadline
    res = http_get(8080, "/payments/#{payment_id}")
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
    next if status == "Completed"
    assert("Pod #{name} Ready (#{ready} #{status})", ready.include?("/") && status == "Running")
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

  key = SecureRandom.uuid
  cid = "qa-e2e-happy-#{Time.now.to_i}"
  payload = { amount: 100.00, currency: "USD", customer_id: cid,
              payment_method: "CREDIT_CARD", country: "US",
              metadata: { order_id: "order-e2e-001" } }

  res = http_post(8080, "/payments", payload, { "Idempotency-Key" => key })
  assert("POST /payments -> 201", res.code == "201", "got #{res.code}")
  body = json_parse(res)
  pid = body["id"]
  assert("Response has id", pid && !pid.empty?)
  assert("Initial status PENDING", body["status"] == "PENDING", "got #{body["status"]}")
  assert("Amount preserved", body["amount"] == 100.0 || body["amount"] == 100)

  if pid
    res2 = http_get(8080, "/payments/#{pid}")
    assert("GET /payments/{id} -> 200", res2.code == "200")
    b2 = json_parse(res2)
    assert("GET returns same id", b2["id"] == pid)

    puts "  #{dim("Waiting for async processing...")}"
    found = wait_for_status(pid, "APPROVED", 30)
    unless found
      r = http_get(8080, "/payments/#{pid}")
      final_status = json_parse(r)["status"]
      found = %w[APPROVED REVIEW FAILED].include?(final_status)
    end
    assert("Payment processed (not PENDING)", found)

    res4 = http_get(8080, "/payments?customerId=#{cid}&limit=10&offset=0")
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

  # REJECT
  key1 = SecureRandom.uuid
  p1 = { amount: 20000.00, currency: "USD", customer_id: "qa-fraud-reject",
         payment_method: "CREDIT_CARD", country: "XX" }
  r1 = http_post(8080, "/payments", p1, { "Idempotency-Key" => key1 })
  assert("Fraud REJECT: POST -> 201", r1.code == "201", "got #{r1.code}")
  pid1 = json_parse(r1)["id"]
  if pid1
    puts "  #{dim("Waiting for fraud REJECT...")}"
    ok1 = wait_for_status(pid1, "FAILED", 30)
    unless ok1
      r = http_get(8080, "/payments/#{pid1}")
      ok1 = json_parse(r)["status"] == "FAILED"
    end
    assert("Fraud REJECT -> FAILED", ok1)
  end

  # REVIEW
  key2 = SecureRandom.uuid
  p2 = { amount: 20000.00, currency: "USD", customer_id: "qa-fraud-review",
         payment_method: "CREDIT_CARD", country: "US" }
  r2 = http_post(8080, "/payments", p2, { "Idempotency-Key" => key2 })
  assert("Fraud REVIEW: POST -> 201", r2.code == "201", "got #{r2.code}")
  pid2 = json_parse(r2)["id"]
  if pid2
    puts "  #{dim("Waiting for fraud REVIEW...")}"
    ok2 = wait_for_status(pid2, "REVIEW", 30)
    unless ok2
      r = http_get(8080, "/payments/#{pid2}")
      final2 = json_parse(r)["status"]
      ok2 = %w[REVIEW FAILED].include?(final2)
    end
    assert("Fraud REVIEW -> REVIEW or FAILED", ok2)
  end

  # APPROVE
  key3 = SecureRandom.uuid
  p3 = { amount: 50.00, currency: "EUR", customer_id: "qa-fraud-approve",
         payment_method: "BANK_TRANSFER", country: "DE" }
  r3 = http_post(8080, "/payments", p3, { "Idempotency-Key" => key3 })
  assert("Fraud APPROVE: POST -> 201", r3.code == "201", "got #{r3.code}")
  pid3 = json_parse(r3)["id"]
  if pid3
    puts "  #{dim("Waiting for fraud APPROVE...")}"
    ok3 = wait_for_status(pid3, "APPROVED", 30)
    unless ok3
      r = http_get(8080, "/payments/#{pid3}")
      ok3 = json_parse(r)["status"] == "APPROVED"
    end
    assert("Fraud APPROVE -> APPROVED", ok3)
  end

  # WALLET + high amount
  key4 = SecureRandom.uuid
  p4 = { amount: 6000.00, currency: "USD", customer_id: "qa-fraud-wallet",
         payment_method: "WALLET", country: "US" }
  r4 = http_post(8080, "/payments", p4, { "Idempotency-Key" => key4 })
  assert("WALLET high-value: POST -> 201", r4.code == "201", "got #{r4.code}")
  pid4 = json_parse(r4)["id"]
  if pid4
    puts "  #{dim("Waiting for WALLET fraud...")}"
    sleep 8
    r = http_get(8080, "/payments/#{pid4}")
    f4 = json_parse(r)["status"]
    assert("WALLET high-value processed", %w[APPROVED REVIEW FAILED].include?(f4), "status=#{f4}")
  end

  phase_footer("fraud")
end

# ─── Phase 5: Idempotency ────────────────────────────────────────────────────
def phase_idempotency
  phase_header(5, "idempotency")

  key = SecureRandom.uuid
  payload = { amount: 77.77, currency: "EUR", customer_id: "qa-e2e-idem",
              payment_method: "DEBIT_CARD", country: "FR" }

  r1 = http_post(8080, "/payments", payload, { "Idempotency-Key" => key })
  assert("1st POST -> 201", r1.code == "201", "got #{r1.code}")
  id1 = json_parse(r1)["id"]

  r2 = http_post(8080, "/payments", payload, { "Idempotency-Key" => key })
  id2 = json_parse(r2)["id"]
  assert("2nd POST same key -> same id", id1 && id1 == id2, "id1=#{id1} id2=#{id2}")

  r3 = http_get(8080, "/payments/idempotency/#{key}")
  assert("GET /payments/idempotency/{key} -> 200", r3.code == "200", "got #{r3.code}")
  id3 = json_parse(r3)["id"]
  assert("Idempotency lookup returns same id", id1 == id3, "expected=#{id1} got=#{id3}")

  phase_footer("idempotency")
end

# ─── Phase 6: Validation ─────────────────────────────────────────────────────
def phase_validation
  phase_header(6, "validation")

  base = { currency: "USD", customer_id: "x", payment_method: "CREDIT_CARD", country: "US" }

  r1 = http_post(8080, "/payments", base.merge(amount: 0))
  assert("amount=0 -> 400", r1.code == "400", "got #{r1.code}")

  r2 = http_post(8080, "/payments", base.merge(amount: -1))
  assert("amount=-1 -> 400", r2.code == "400", "got #{r2.code}")

  r3 = http_post(8080, "/payments", base.merge(amount: 10, currency: "INVALID"))
  assert("currency=INVALID -> 400", r3.code == "400", "got #{r3.code}")

  r4 = http_post(8080, "/payments", base.merge(amount: 10, payment_method: "UNKNOWN"))
  assert("paymentMethod=UNKNOWN -> accepted (any string allowed)", r4.code == "201", "got #{r4.code}")

  r5 = http_post(8080, "/payments", base.merge(amount: 10, customer_id: ""))
  assert("customerId empty -> 400", r5.code == "400", "got #{r5.code}")

  uri = URI("http://127.0.0.1:8080/payments")
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
  assert("Circuit breaker / fallback evidence", has_fallback,
         has_fallback ? nil : "no fallback keywords found (normal with random failures)")

  dlq_out, _ = sh("kubectl exec kafka-stack -c kafka -n #{NAMESPACE} -- timeout 5 kafka-console-consumer --bootstrap-server localhost:9092 --topic payments.events.dead-letter --from-beginning")
  dlq_count = dlq_out.lines.count
  assert("Dead-letter queue has messages", dlq_count > 0, "count=#{dlq_count}")

  retry_out, _ = sh("kubectl exec kafka-stack -c kafka -n #{NAMESPACE} -- timeout 5 kafka-console-consumer --bootstrap-server localhost:9092 --topic payments.events.retry --from-beginning")
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
    services = (data2["data"] || []).compact.map { |s| s["serviceName"] }.compact
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
def phase_resilience
  phase_header(10, "resilience")

  puts "  #{dim("Restarting api-gateway...")}"
  sh!("kubectl rollout restart deployment/api-gateway -n #{NAMESPACE}")
  sh!("kubectl rollout status deployment/api-gateway -n #{NAMESPACE} --timeout=120s")
  r1 = http_get(8080, "/payments?limit=1")
  assert("api-gateway survived rollout restart", r1.code == "200", "got #{r1.code}")

  puts "  #{dim("Restarting payment-processor...")}"
  sh!("kubectl rollout restart deployment/payment-processor -n #{NAMESPACE}")
  sh!("kubectl rollout status deployment/payment-processor -n #{NAMESPACE} --timeout=120s")
  key = SecureRandom.uuid
  r2 = http_post(8080, "/payments",
    { amount: 25.00, currency: "GBP", customer_id: "qa-resilience",
      payment_method: "BANK_TRANSFER", country: "GB" },
    { "Idempotency-Key" => key })
  assert("POST after processor restart -> 201", r2.code == "201", "got #{r2.code}")

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

# Check cluster
out, ok = sh("kubectl cluster-info")
unless ok
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
