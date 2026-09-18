# Rakefile - Payment Orchestration Layer (poc-camel)
# Requires: Ruby 3.x, Podman, Maven, kubectl, kind (for k8s:* tasks)
#
# You should never need raw kubectl/kind: every platform operation is wrapped here.
# Quickstart:
#   rake infra:start       # local infra (compose)
#   rake test:run          # all tests + coverage gate 98%
#   rake k8s:deploy        # full deploy (auto-creates Kind cluster + infra + apps)
#   rake k8s:check SMOKE=1 # verify pods + e2e payment
#   rake k8s:undeploy      # remove everything (APPS_ONLY=1 for apps only)
#   rake help              # full list with flags
#
# knobs: OVERLAY=dev|prod (default dev), CLUSTER=poc-camel, NAMESPACE=poc-camel,
#        CONTAINER_ENGINE=podman|docker

require "rake"
require "fileutils"
require "net/http"
require "json"
require "securerandom"
require "tmpdir"
require "socket"
require "time"

CONTAINER_ENGINE = ENV['CONTAINER_ENGINE'] || 'podman'
PODMAN_DOCKER_HOST = "npipe:////./pipe/podman-machine-default"
CLUSTER   = ENV['CLUSTER']   || 'poc-camel'
NAMESPACE = ENV['NAMESPACE'] || 'poc-camel'
OVERLAY   = ENV['OVERLAY']   || 'dev'

ROOT = File.expand_path(__dir__)
INFRA_DIR = File.join(ROOT, "kubernetes", "infrastructure")
OVERLAY_DIR = File.join(ROOT, "kubernetes", "overlays", OVERLAY)
IMAGES = {
  'api-gateway' => File.join(ROOT, "docker", "Dockerfile.api-gateway"),
  'payment-processor' => File.join(ROOT, "docker", "Dockerfile.payment-processor"),
}.freeze

def setup_podman_env
  ENV['DOCKER_HOST'] ||= PODMAN_DOCKER_HOST
  ENV['TESTCONTAINERS_RYUK_DISABLED'] = 'true'
end

# Auto-configure Podman socket so every kubectl backtick/system call works
# without the caller needing to export DOCKER_HOST manually.
setup_podman_env

# Run kubectl and return stdout (stderr suppressed). Used by ps/kafka tasks.
def kc_out(args)
  require 'open3'
  out, _err, _st = Open3.capture3(ENV, "kubectl #{args}")
  out
end

def run_cmd(cmd, fail: true)
  puts ">> #{cmd}"
  ok = system(cmd)
  raise "Command failed: #{cmd}" unless ok || !fail
  ok
end

def mvn(args)
  setup_podman_env
  cmd = File.exist?('mvnw') ? './mvnw' : 'mvn'
  sh "#{cmd} #{args}"
end

def kc(args)
  run_cmd("kubectl #{args}", fail: false)
end

def kind_cluster_exists?
  out = `kind get clusters 2>&1`
  out.include?(CLUSTER)
rescue StandardError
  false
end

def app_targets(app)
  case (app || 'all').downcase
  when 'gateway' then [['api-gateway', 'app=api-gateway']]
  when 'processor' then [['payment-processor', 'app=payment-processor']]
  else [['api-gateway', 'app=api-gateway'], ['payment-processor', 'app=payment-processor']]
  end
end

def k8s_wait(label, timeout: 120)
  kc("wait --for=condition=ready pod -l #{label} -n #{NAMESPACE} --timeout=#{timeout}s")
end

# ──────────────────────────────────────────────────────────────────────────────
# JaCoCo Coverage Report Helpers
# ──────────────────────────────────────────────────────────────────────────────
JACOCO_CSV_PATHS = {
  'shared'             => 'shared/target/site/jacoco/jacoco.csv',
  'backend'            => 'backend/target/site/jacoco/jacoco.csv',
  'payment-processor'  => 'payment-processor/target/site/jacoco/jacoco.csv',
}.freeze

def parse_jacoco_csv(path)
  return nil unless File.exist?(path)

  totals = Hash.new(0)
  File.foreach(path).with_index do |line, idx|
    next if idx == 0  # skip header
    cols = line.chomp.split(',')
    next if cols.size < 12

    # Maven JaCoCo CSV columns:
    # GROUP, PACKAGE, CLASS, INSTRUCTION_MISSED, INSTRUCTION_COVERED,
    # BRANCH_MISSED, BRANCH_COVERED, LINE_MISSED, LINE_COVERED,
    # COMPLEXITY_MISSED, COMPLEXITY_COVERED, METHOD_MISSED, METHOD_COVERED
    totals[:instr_missed]  += cols[3].to_i
    totals[:instr_covered] += cols[4].to_i
    totals[:branch_missed] += cols[5].to_i
    totals[:branch_covered]+= cols[6].to_i
    totals[:line_missed]   += cols[7].to_i
    totals[:line_covered]  += cols[8].to_i
    totals[:method_missed] += cols[11].to_i
    totals[:method_covered]+= cols[12].to_i
  end
  totals
end

def pct(covered, missed)
  total = covered + missed
  return 'N/A' if total == 0
  (covered.to_f / total * 100).round(1)
end

def color_pct(val)
  return val if val == 'N/A'
  if val >= 80
    "\e[32m#{val}%\e[0m"      # green
  elsif val >= 60
    "\e[33m#{val}%\e[0m"      # yellow
  else
    "\e[31m#{val}%\e[0m"      # red
  end
end

def print_coverage_table
  puts
  puts "\e[1m\e[36m#{'=' * 76}\e[0m"
  puts "\e[1m\e[36m  JACOCO COVERAGE REPORT\e[0m"
  puts "\e[1m\e[36m#{'=' * 76}\e[0m"
  puts

  header = sprintf("  %-28s  %10s  %10s  %10s  %10s", "Module", "Instruction", "Branch", "Line", "Method")
  puts "\e[1m#{header}\e[0m"
  puts "  " + "-" * 74

  grand = Hash.new(0)
  any_report = false

  JACOCO_CSV_PATHS.each do |module_name, csv_path|
    full_path = File.join(ROOT, csv_path)
    stats = parse_jacoco_csv(full_path)
    unless stats
      printf("  %-28s  %s\n", module_name, "\e[90m(no report found)\e[0m")
      next
    end
    any_report = true

    instr_pct  = pct(stats[:instr_covered],  stats[:instr_missed])
    branch_pct = pct(stats[:branch_covered], stats[:branch_missed])
    line_pct   = pct(stats[:line_covered],   stats[:line_missed])
    method_pct = pct(stats[:method_covered], stats[:method_missed])

    grand[:instr_missed]   += stats[:instr_missed]
    grand[:instr_covered]  += stats[:instr_covered]
    grand[:branch_missed]  += stats[:branch_missed]
    grand[:branch_covered] += stats[:branch_covered]
    grand[:line_missed]    += stats[:line_missed]
    grand[:line_covered]   += stats[:line_covered]
    grand[:method_missed]  += stats[:method_missed]
    grand[:method_covered] += stats[:method_covered]

    printf("  %-28s  %10s  %10s  %10s  %10s\n",
      module_name,
      color_pct(instr_pct).to_s.ljust(10),
      color_pct(branch_pct).to_s.ljust(10),
      color_pct(line_pct).to_s.ljust(10),
      color_pct(method_pct).to_s
    )
  end

  if any_report
    puts "  " + "-" * 74
    g_instr  = pct(grand[:instr_covered],  grand[:instr_missed])
    g_branch = pct(grand[:branch_covered], grand[:branch_missed])
    g_line   = pct(grand[:line_covered],   grand[:line_missed])
    g_method = pct(grand[:method_covered], grand[:method_missed])

    printf("\e[1m  %-28s  %10s  %10s  %10s  %10s\e[0m\n",
      "TOTAL",
      color_pct(g_instr).to_s.ljust(10),
      color_pct(g_branch).to_s.ljust(10),
      color_pct(g_line).to_s.ljust(10),
      color_pct(g_method).to_s
    )
  end

  puts
  puts "  HTML reports: payment-processor/target/site/jacoco/index.html"
  puts
end

# ──────────────────────────────────────────────────────────────────────────────
# Test Tasks
# ──────────────────────────────────────────────────────────────────────────────
namespace :test do
  desc 'Run all tests (unit + integration + e2e) with coverage gate 98%'
  task :run do
    args = ["clean", "verify", "-Dsurefire.useFile=false"]
    args << "-Djacoco.skip=true" if ENV['COVERAGE'] == '0'
    scope = { 'processor' => 'payment-processor', 'backend' => 'backend',
              'shared' => 'shared' }[(ENV['SCOPE'] || '').downcase] || ENV['SCOPE']
    args << "-pl #{scope} -am" if scope && !scope.empty?
    begin
      mvn(args.join(" "))
    ensure
      print_coverage_table unless ENV['COVERAGE'] == '0'
    end
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Local Infrastructure Tasks (podman-compose, no K8s)
# ──────────────────────────────────────────────────────────────────────────────
namespace :infra do
  desc 'Start all infrastructure services'
  task :start do
    run_cmd("#{CONTAINER_ENGINE}-compose up -d", fail: false)
    run_cmd("#{CONTAINER_ENGINE} pod ps", fail: false)
    run_cmd("#{CONTAINER_ENGINE} ps", fail: false)
    svc = ENV['SVC'] || ''
    run_cmd("#{CONTAINER_ENGINE}-compose logs -f --tail=100 #{svc}", fail: false) unless svc.empty?
  end

  desc 'Stop all infrastructure services'
  task :stop do
    run_cmd("#{CONTAINER_ENGINE}-compose down -v", fail: false)
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Kubernetes Tasks (Kind) — no raw kubectl/kind needed
# ──────────────────────────────────────────────────────────────────────────────
namespace :k8s do
  desc 'Create Kind cluster if it does not exist'
  task :cluster do
    unless kind_cluster_exists?
      puts "Creating Kind cluster #{CLUSTER}..."
      run_cmd("kind create cluster --name #{CLUSTER}")
      puts "Waiting for node to be Ready..."
      30.times do
        status = `kubectl get nodes -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}' 2>&1`.strip
        break if status == "True"
        sleep 2
      end
      puts "\e[32mKind cluster #{CLUSTER} ready.\e[0m"
    else
      puts "Kind cluster #{CLUSTER} already exists."
    end
  end

  task :namespace do
    run_cmd("kubectl create namespace #{NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -", fail: false)
  end

  task :infra do
    Dir.glob("#{INFRA_DIR}/*.yaml").sort.each do |manifest|
      run_cmd("kubectl apply -f #{manifest}", fail: false)
    end
    puts "\nWaiting for infrastructure to be ready..."
    k8s_wait("app=kafka", timeout: 180)
    k8s_wait("app=postgres", timeout: 180)
    kc("wait --for=condition=ready pod/jaeger -n #{NAMESPACE} --timeout=60s")
    kc("wait --for=condition=ready pod/monitoring-stack -n #{NAMESPACE} --timeout=60s")
  end

  desc 'Deploy to Kubernetes'
  task :deploy do
    Rake::Task['k8s:cluster'].invoke
    unless Dir.exist?(OVERLAY_DIR)
      raise "Overlay not found: #{OVERLAY_DIR} (use OVERLAY=dev|prod)"
    end
    if ENV['RESTART_ONLY'] == '1'
      app_targets(ENV['APP']).each do |d, label|
        kc("rollout restart deployment/#{d} -n #{NAMESPACE}")
        k8s_wait(label, timeout: 180)
      end
    else
      Rake::Task['k8s:namespace'].invoke
      Rake::Task['k8s:infra'].invoke
      run_cmd("kubectl apply -k #{OVERLAY_DIR}")
      puts "\nWaiting for applications to be ready..."
      k8s_wait("app=api-gateway", timeout: 180)
      k8s_wait("app=payment-processor", timeout: 180)
      puts "\n\e[32m=== Deployment complete (OVERLAY=#{OVERLAY}) ===\e[0m"
      run_cmd("kubectl get pods -n #{NAMESPACE} -o wide", fail: false)
      run_cmd("kubectl get svc -n #{NAMESPACE}", fail: false)
    end
  end

  desc 'Undeploy from Kubernetes'
  task :undeploy do
    run_cmd("kubectl delete -k #{OVERLAY_DIR}", fail: false)
    unless ENV['APPS_ONLY'] == '1'
      run_cmd("kubectl delete -f #{INFRA_DIR} --recursive", fail: false)
      run_cmd("kubectl delete namespace #{NAMESPACE}", fail: false)
    end
    run_cmd("kind delete cluster --name #{CLUSTER}", fail: false) if ENV['CLUSTER_DELETE'] == '1'
  end

  desc 'Verify pods and assert log patterns (APP=..., EXPECT_ABSENT=..., EXPECT_PRESENT=..., SMOKE=1)'
  task :check do
    raise "No cluster context (is podman machine / Kind up?) — recover the environment first" unless system("kubectl cluster-info >NUL 2>&1") || system("kubectl cluster-info >/dev/null 2>&1")
    failed = false
    app_targets(ENV['APP']).each do |_deploy, label|
      puts "== #{label} =="
      run_cmd("kubectl get pods -l #{label} -n #{NAMESPACE} -o wide", fail: false)
      pods = `kubectl get pods -l #{label} -n #{NAMESPACE} -o jsonpath={.items[*].metadata.name} 2>&1`.gsub("'", "").split
      if pods.empty?
        puts "WARN: no pods found for #{label}"
        next
      end
      combined = ""
      pods.each do |pod|
        logs = `kubectl logs #{pod} -n #{NAMESPACE} --tail=100 2>&1`
        unless $?.success?
          puts "WARN: could not read logs of #{pod}: #{logs.lines.first}"
          next
        end
        puts "-- #{pod} --"
        errs = logs.lines.grep(/ERROR|Caused by|Exception|WARN.*(Kafka|DNS|resolv|bootstrap|connection)/).last(5)
        puts(errs.empty? ? "(no error lines)" : errs)
        tail = logs.lines.last(3).map(&:strip).reject(&:empty?)
        puts "  tail: #{tail.join(' | ')[0, 300]}" unless tail.empty?
        combined += logs
      end
      if combined.empty?
        puts "WARN: no logs captured for #{label}, assertions skipped"
        next
      end
      absent = ENV['EXPECT_ABSENT']
      present = ENV['EXPECT_PRESENT']
      if absent && !absent.empty? && combined.include?(absent)
        puts "FAIL: found forbidden pattern #{absent.inspect}"
        failed = true
      end
      if present && !present.empty? && !combined.include?(present)
        puts "FAIL: missing expected pattern #{present.inspect}"
        failed = true
      end
    end
    raise "k8s:check FAILED" if failed
    puts "\e[32mCHECK OK\e[0m"
    Rake::Task['k8s:smoke'].invoke if ENV['SMOKE'] == '1'
  end

  desc 'Show pods + services status as formatted tables'
  task :status => [:ps] do end

  # ────────────────────────────────────────────────────────────────────────────
  # rake k8s:ps — rich pod/service/hpa status table (hides kubectl complexity)
  # ────────────────────────────────────────────────────────────────────────────
  desc 'Rich formatted table of pods, services and HPAs in the namespace'
  task :ps do
    # helpers
    def col(text, width, align: :left)
      s = (text || '').to_s.gsub(/\e\[[0-9;]*m/, '')   # strip ANSI for measuring
      padded = align == :right ? s.rjust(width) : s.ljust(width)
      text.to_s.include?("\e[") ? text.to_s + (' ' * [width - s.length, 0].max) : padded
    end
    def bar(widths); "+#{widths.map { |w| '-' * (w + 2) }.join('+')}+"; end
    def row(cells, widths)
      "| #{cells.each_with_index.map { |c, i| col(c, widths[i]) }.join(' | ')} |"
    end
    def section(title)
      puts
      puts "\e[1m\e[36m  #{title}\e[0m"
    end

    # ── PODS ──────────────────────────────────────────────────────────────────
    section("PODS  (namespace: #{NAMESPACE})")
    raw = kc_out("get pods -n #{NAMESPACE} -o json")
    begin
      items = JSON.parse(raw)['items'] || []
    rescue JSON::ParserError
      puts "  \e[31mERROR: could not parse pod list — is kubectl context set?\e[0m"
      items = []
    end

    STATUS_COLOR = {
      'Running'   => "\e[32m",   # green
      'Pending'   => "\e[33m",   # yellow
      'Succeeded' => "\e[90m",   # grey
      'Completed' => "\e[90m",
      'Failed'    => "\e[31m",   # red
      'Unknown'   => "\e[35m",   # magenta
      'Terminating' => "\e[31m",
    }.freeze

    pod_rows = items.map do |p|
      name      = p.dig('metadata', 'name')
      phase     = p.dig('status', 'phase') || 'Unknown'
      # override phase if pod is being deleted
      phase     = 'Terminating' if p.dig('metadata', 'deletionTimestamp')
      ctrs      = p.dig('status', 'containerStatuses') || []
      ready_n   = ctrs.count { |c| c['ready'] }
      total_n   = ctrs.size
      ready_s   = "#{ready_n}/#{total_n}"
      restarts  = ctrs.sum { |c| c.dig('restartCount') || 0 }
      age_s     = begin
        created = Time.parse(p.dig('metadata', 'creationTimestamp'))
        secs    = (Time.now - created).to_i
        if    secs < 120   then "#{secs}s"
        elsif secs < 7200  then "#{secs / 60}m"
        elsif secs < 86400 then "#{secs / 3600}h"
        else "#{secs / 86400}d"
        end
      rescue
        '?'
      end
      ip        = p.dig('status', 'podIP') || '<none>'
      node      = p.dig('spec', 'nodeName') || '<none>'
      color     = STATUS_COLOR[phase] || ''
      reset     = "\e[0m"
      phase_col = "#{color}#{phase}#{reset}"
      # readiness coloring
      ready_col = (ready_n == total_n && total_n > 0) ?
        "\e[32m#{ready_s}\e[0m" : "\e[33m#{ready_s}\e[0m"
      rst_col   = restarts > 0 ? "\e[33m#{restarts}\e[0m" : "\e[90m#{restarts}\e[0m"
      [name, ready_col, phase_col, rst_col, age_s, ip, node]
    end

    hdrs  = %w[NAME READY STATUS RESTARTS AGE POD-IP NODE]
    # compute column widths from plain-text content
    plain_rows = items.map do |p|
      name     = p.dig('metadata', 'name') || ''
      phase    = p.dig('status', 'phase') || 'Unknown'
      phase    = 'Terminating' if p.dig('metadata', 'deletionTimestamp')
      ctrs     = p.dig('status', 'containerStatuses') || []
      ready_n  = ctrs.count { |c| c['ready'] }
      total_n  = ctrs.size
      restarts = ctrs.sum { |c| c.dig('restartCount') || 0 }
      [name, "#{ready_n}/#{total_n}", phase, restarts.to_s, '', p.dig('status','podIP')||'<none>', p.dig('spec','nodeName')||'<none>']
    end
    all_text_rows = [hdrs] + plain_rows
    widths = hdrs.each_with_index.map { |h, i| all_text_rows.map { |r| (r[i] || '').length }.max }

    puts bar(widths)
    puts row(hdrs.map { |h| "\e[1m#{h}\e[0m" }, widths)
    puts bar(widths)
    if pod_rows.empty?
      puts row(["(no pods found in namespace #{NAMESPACE})", '', '', '', '', '', ''], widths)
    else
      pod_rows.each { |r| puts row(r, widths) }
    end
    puts bar(widths)
    puts "  Total: #{pod_rows.size} pod(s)"

    # ── SERVICES ──────────────────────────────────────────────────────────────
    section("SERVICES")
    svc_raw = kc_out("get svc -n #{NAMESPACE} -o json")
    begin
      svcs = JSON.parse(svc_raw)['items'] || []
    rescue JSON::ParserError
      svcs = []
    end


    svc_rows = svcs.map do |s|
      name      = s.dig('metadata', 'name')
      type      = s.dig('spec', 'type') || 'ClusterIP'
      cluster_ip = s.dig('spec', 'clusterIP') || '<none>'
      ext_ip    = (s.dig('status', 'loadBalancer', 'ingress') || []).map { |i| i['ip'] || i['hostname'] }.join(',')
      ext_ip    = '<none>' if ext_ip.empty?
      ports     = (s.dig('spec', 'ports') || []).map do |p|
        np   = p['nodePort'] ? ":#{p['nodePort']}" : ''
        "#{p['port']}#{np}/#{p['protocol']}"
      end.join(', ')
      type_col  = type == 'NodePort' ? "\e[33m#{type}\e[0m" : type
      [name, type_col, cluster_ip, ext_ip, ports]
    end

    sh  = %w[NAME TYPE CLUSTER-IP EXTERNAL-IP PORT(S)]
    sw  = [sh, svcs.map { |s|
      [
        s.dig('metadata','name')||'',
        s.dig('spec','type')||'ClusterIP',
        s.dig('spec','clusterIP')||'',
        '<none>',
        (s.dig('spec','ports')||[]).map{|p|"#{p['port']}/#{p['protocol']}"}.join(', ')
      ]
    }].flatten(1).then { |all| sh.each_with_index.map { |h, i| all.map { |r| (r[i]||'').length }.max } }

    puts bar(sw)
    puts row(sh.map { |h| "\e[1m#{h}\e[0m" }, sw)
    puts bar(sw)
    svc_rows.empty? ? puts(row(['(none)', '', '', '', ''], sw)) : svc_rows.each { |r| puts row(r, sw) }
    puts bar(sw)

    # ── HPAs ──────────────────────────────────────────────────────────────────
    section("HORIZONTAL POD AUTOSCALERS")
    hpa_raw = kc_out("get hpa -n #{NAMESPACE} -o json")
    begin
      hpas = JSON.parse(hpa_raw)['items'] || []
    rescue JSON::ParserError
      hpas = []
    end


    hpa_rows = hpas.map do |h|
      name    = h.dig('metadata', 'name')
      target  = "#{h.dig('spec','scaleTargetRef','kind')}/#{h.dig('spec','scaleTargetRef','name')}"
      min_r   = h.dig('spec', 'minReplicas')
      max_r   = h.dig('spec', 'maxReplicas')
      curr_r  = h.dig('status', 'currentReplicas')
      desired = h.dig('status', 'desiredReplicas')
      metrics = (h.dig('status', 'currentMetrics') || []).map do |m|
        case m['type']
        when 'Resource'
          r = m.dig('resource')
          "#{r['name']}=#{r.dig('current','averageUtilization') || '?'}%"
        else
          m['type']
        end
      end.join(', ')
      metrics = '(metrics-server unavailable)' if metrics.empty?
      [name, target, min_r.to_s, max_r.to_s, curr_r.to_s, desired.to_s, metrics]
    end

    hh  = ['NAME', 'TARGET', 'MIN', 'MAX', 'CURRENT', 'DESIRED', 'METRICS']
    hw  = [hh, hpa_rows.map { |r| r }].flatten(1).then { |all| hh.each_with_index.map { |_, i| all.map { |r| (r[i]||'').gsub(/\e\[[0-9;]*m/,'').length }.max } }

    puts bar(hw)
    puts row(hh.map { |h| "\e[1m#{h}\e[0m" }, hw)
    puts bar(hw)
    hpa_rows.empty? ? puts(row(['(none)', '', '', '', '', '', ''], hw)) : hpa_rows.each { |r| puts row(r, hw) }
    puts bar(hw)
    puts
  end

  # ────────────────────────────────────────────────────────────────────────────
  # rake k8s:kafka — Kafka topics + consumer groups as formatted tables
  # Knobs: CONSUMER_GROUPS=1 (show group lag), TOPIC=name (filter)
  # ────────────────────────────────────────────────────────────────────────────
  desc 'Show Kafka topics (and optionally consumer group lag) as formatted tables (TOPIC=name, CONSUMER_GROUPS=1)'
  task :kafka do
    kafka_pod = 'kafka-stack'
    kafka_c   = 'kafka'
    bs        = 'localhost:9092'
    filter    = ENV['TOPIC'] || ''

    def k_col(text, width)
      plain = text.to_s.gsub(/\e\[[0-9;]*m/, '')
      pad   = [width - plain.length, 0].max
      "#{text}#{' ' * pad}"
    end
    def k_bar(widths); "+#{widths.map { |w| '-' * (w + 2) }.join('+')}+"; end
    def k_row(cells, widths)
      "| #{cells.each_with_index.map { |c, i| k_col(c, widths[i]) }.join(' | ')} |"
    end

    # ── TOPICS ────────────────────────────────────────────────────────────────
    puts
    puts "\e[1m\e[36m  KAFKA TOPICS  (broker: kafka:9092)\e[0m"

    raw_topics = kc_out("exec #{kafka_pod} -c #{kafka_c} -n #{NAMESPACE} -- kafka-topics --bootstrap-server #{bs} --describe")
    topic_rows = []
    current = nil
    raw_topics.each_line do |line|
      line = line.strip
      if line.start_with?('Topic:') && !line.include?('Partition:')
        # new topic header line:  Topic: name  PartitionCount: N  ReplicationFactor: N  Configs: ...
        m = line.match(/Topic:\s+(\S+)\s+PartitionCount:\s+(\d+)\s+ReplicationFactor:\s+(\d+)/)
        next unless m
        tname = m[1]; parts = m[2]; rf = m[3]
        next if tname == '__consumer_offsets'
        next if !filter.empty? && !tname.include?(filter)
        current = { name: tname, parts: parts, rf: rf, leaders: [], isrs: [] }
        topic_rows << current
      elsif line.start_with?('Topic:') && line.include?('Partition:') && current
        # partition detail line
        m = line.match(/Leader:\s+(\d+)\s+Replicas:\s+[\d,]+\s+Isr:\s+([\d,]+)/)
        if m
          current[:leaders] << m[1]
          current[:isrs]    << m[2].split(',').size
        end
      end
    end

    # If --describe gave nothing useful, fall back to --list
    if topic_rows.empty?
      list = kc_out("exec #{kafka_pod} -c #{kafka_c} -n #{NAMESPACE} -- kafka-topics --bootstrap-server #{bs} --list")
      list.each_line do |t|
        t = t.strip
        next if t.empty? || t == '__consumer_offsets'
        next if !filter.empty? && !t.include?(filter)
        topic_rows << { name: t, parts: '?', rf: '?', leaders: [], isrs: [] }
      end
    end


    EXPECTED_TOPICS = %w[
      payments.events.received  payments.events.processed payments.events.failed
      payments.events.review    payments.events.dead-letter payments.events.retry
      payments.events.audit     payments.events.status.changed fraud.events.detected
    ].freeze

    th    = ['TOPIC', 'PARTITIONS', 'REP.FACTOR', 'LEADER(S)', 'STATUS']
    tdata = topic_rows.map do |t|
      expected = EXPECTED_TOPICS.include?(t[:name])
      status   = expected ? "\e[32m✓ expected\e[0m" : "\e[90m(extra)\e[0m"
      leaders  = t[:leaders].uniq.join(',')
      leaders  = '–' if leaders.empty?
      [t[:name], t[:parts].to_s, t[:rf].to_s, leaders, status]
    end
    tw = th.each_with_index.map do |h, i|
      ([h] + tdata.map { |r| r[i].gsub(/\e\[[0-9;]*m/, '') }).map(&:length).max
    end

    puts k_bar(tw)
    puts k_row(th.map { |h| "\e[1m#{h}\e[0m" }, tw)
    puts k_bar(tw)
    if tdata.empty?
      puts k_row(["(no topics found#{filter.empty? ? '' : " matching '#{filter}'"})", '', '', '', ''], tw)
    else
      tdata.each { |r| puts k_row(r, tw) }
    end
    puts k_bar(tw)

    # missing expected topics
    present_names = topic_rows.map { |t| t[:name] }
    missing = EXPECTED_TOPICS.reject { |e| present_names.include?(e) }
    if missing.empty?
      puts "  \e[32m✓ All #{EXPECTED_TOPICS.size} expected topics present\e[0m"
    else
      puts "  \e[31m✗ Missing topics (#{missing.size}): #{missing.join(', ')}\e[0m"
    end

    # ── CONSUMER GROUPS ───────────────────────────────────────────────────────
    if ENV['CONSUMER_GROUPS'] == '1'
      puts
      puts "\e[1m\e[36m  CONSUMER GROUPS & LAG\e[0m"

      groups_raw = kc_out("exec #{kafka_pod} -c #{kafka_c} -n #{NAMESPACE} -- kafka-consumer-groups --bootstrap-server #{bs} --list")
      groups = groups_raw.lines.map(&:strip).reject(&:empty?).reject { |g| g.include?('Error') || g.include?('WARN') }

      if groups.empty?
        puts "  \e[90m(no consumer groups found)\e[0m"
      else
        cg_rows = []
        groups.each do |grp|
          desc = kc_out("exec #{kafka_pod} -c #{kafka_c} -n #{NAMESPACE} -- kafka-consumer-groups --bootstrap-server #{bs} --describe --group \"#{grp}\"")

          desc.each_line do |line|
            parts = line.split(/\s+/).map(&:strip)
            # output columns: GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST CLIENT-ID
            next if parts.size < 6
            next if parts[0] == 'GROUP' # header
            next if parts[1].nil? || parts[1].empty? || parts[1] == '-'
            next if !filter.empty? && !parts[1].include?(filter)
            lag      = parts[5] || '?'
            lag_col  = begin
              lag_i = Integer(lag)
              lag_i > 100  ? "\e[31m#{lag}\e[0m" :
              lag_i > 0    ? "\e[33m#{lag}\e[0m" :
                             "\e[32m#{lag}\e[0m"
            rescue ArgumentError
              "\e[90m#{lag}\e[0m"
            end
            cg_rows << [grp, parts[1], parts[2], parts[3], parts[4], lag_col]
          end
        end

        cgh   = ['GROUP', 'TOPIC', 'PARTITION', 'CURRENT-OFFSET', 'LOG-END', 'LAG']
        cgw   = cgh.each_with_index.map do |h, i|
          ([h] + cg_rows.map { |r| r[i].gsub(/\e\[[0-9;]*m/, '') }).map(&:length).max
        end

        puts k_bar(cgw)
        puts k_row(cgh.map { |h| "\e[1m#{h}\e[0m" }, cgw)
        puts k_bar(cgw)
        if cg_rows.empty?
          puts k_row(['(no partitions with offsets)', '', '', '', '', ''], cgw)
        else
          cg_rows.each { |r| puts k_row(r, cgw) }
        end
        puts k_bar(cgw)
      end
    else
      puts "  \e[90mTip: run with CONSUMER_GROUPS=1 to show group lag\e[0m"
    end
    puts
  end

  task :logs do
    app = (ENV['APP'] || ENV['app'] || 'all').downcase
    selector = case app
               when 'gateway' then 'app=api-gateway'
               when 'processor' then 'app=payment-processor'
               else nil
               end
    if selector
      run_cmd("kubectl logs -f -l #{selector} -n #{NAMESPACE} --tail=100", fail: false)
    else
      run_cmd("kubectl logs -f -l app=api-gateway -n #{NAMESPACE} --tail=50", fail: false)
      run_cmd("kubectl logs -f -l app=payment-processor -n #{NAMESPACE} --tail=50", fail: false)
    end
  end

  desc 'Build and load container images (APP=gateway|processor|all)'
  task :build do
    puts "\n=== Building JARs locally (prod profile) ==="
    mvn("clean package -DskipTests -Dquarkus.profile=prod -pl shared,test-support,backend,payment-processor -am")
    puts "\n=== Building Docker images ==="
    names = app_targets(ENV['APP']).map(&:first)
    names.each do |name|
      short = "poc-camel/#{name}:dev"
      run_cmd("#{CONTAINER_ENGINE} build -t #{short} -t localhost/#{short} -f #{IMAGES[name]} .")
    end
    Rake::Task['k8s:load'].invoke
  end

  task :load do
    Rake::Task['k8s:cluster'].invoke unless kind_cluster_exists?
    names = app_targets(ENV['APP']).map(&:first)
    names.flat_map { |n| ["poc-camel/#{n}:dev", "localhost/poc-camel/#{n}:dev"] }.each do |img|
      tar = File.join(Dir.tmpdir, "kind-load-#{img.gsub(/[\/:]/, '_')}.tar")
      if run_cmd("#{CONTAINER_ENGINE} save -o #{tar} #{img}", fail: false)
        run_cmd("kind load image-archive #{tar} --name #{CLUSTER}", fail: false)
        FileUtils.rm_f(tar)
      else
        puts "WARN: could not save image #{img}, skipping"
      end
    end
  end

  task :portforward do
    puts "Forwarding api-gateway:8080 -> localhost:8080 (Ctrl+C to stop)"
    run_cmd("kubectl port-forward svc/api-gateway-external 8080:8080 -n #{NAMESPACE}", fail: false)
  end

  task :smoke do
    pf = IO.popen("kubectl port-forward svc/api-gateway-external 8080:8080 -n #{NAMESPACE}")
    sleep 6
    begin
      key = SecureRandom.uuid
      payload = { amount: 42.50, currency: "EUR", customer_id: "qa-smoke",
                  payment_method: "CARD", country: "ES" }
      uri = URI("http://localhost:8080/payments")
      req = Net::HTTP::Post.new(uri, { "Content-Type" => "application/json",
                                       "Idempotency-Key" => key })
      req.body = payload.to_json
      res = Net::HTTP.start(uri.hostname, uri.port,
                            open_timeout: 10, read_timeout: 20) { |h| h.request(req) }
      puts "POST /payments -> #{res.code} (Idempotency-Key: #{key})"
      puts res.body[0, 400]
      raise "smoke FAILED (expected 200/201, got #{res.code})" unless %w[200 201].include?(res.code)
      id = JSON.parse(res.body).dig("id") rescue nil
      if id
        get = Net::HTTP::Get.new("/payments/#{id}")
        res2 = Net::HTTP.start("localhost", 8080,
                               open_timeout: 10, read_timeout: 20) { |h| h.request(get) }
        puts "GET /payments/#{id} -> #{res2.code}"
        raise "smoke FAILED on GET (#{res2.code})" unless res2.code == "200"
      end
      puts "\e[32mSMOKE OK\e[0m"
    ensure
      begin
        Process.kill("TERM", pf.pid)
      rescue StandardError
        nil
      end
    end
  end

  # ────────────────────────────────────────────────────────────────────────────
  # E2E Test Helpers
  # ────────────────────────────────────────────────────────────────────────────
  E2E_PHASES = %w[infra connectivity happy-path fraud idempotency validation health circuit observability resilience].freeze

  # ────────────────────────────────────────────────────────────────────────────
  # rake k8s:e2e [PHASE=...]
  # ────────────────────────────────────────────────────────────────────────────
  desc 'Run comprehensive E2E tests against K8s deployment (PHASE=infra|connectivity|happy-path|fraud|idempotency|validation|health|circuit|observability|resilience)'
  task :e2e do
    raise "No cluster context — is Kind running? (rake k8s:cluster)" unless kc_out("cluster-info").include?("control plane") || kc_out("cluster-info").include?("Kubernetes")

    phase = ENV['PHASE'] || ''
    phases = phase.empty? ? [] : [phase]

    needs_pf = phases.empty? || (phases & %w[happy-path fraud idempotency validation]).any?
    pf = nil
    if needs_pf
      puts "Starting port-forward to api-gateway:8080..."
      pf = IO.popen("kubectl port-forward svc/api-gateway-external 8080:8080 -n #{NAMESPACE}")
      Thread.new { pf.read rescue nil }
      sleep 5
      begin
        require "socket"
        TCPSocket.new("127.0.0.1", 8080).close
        puts "Port-forward ready."
      rescue
        puts "WARN: port-forward may not be ready, tests may fail"
      end
    end

    script = File.join(ROOT, "scripts", "e2e_runner.rb")
    raise "E2E runner not found: #{script}" unless File.exist?(script)

    cmd = "ruby \"#{script}\""
    cmd += " PHASE=#{phase}" unless phase.empty?
    ok = sh(cmd)

  ensure
    if pf
      Process.kill("TERM", pf.pid) rescue nil
      pf.close rescue nil
    end
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Architecture Decision Records (ADRs) & Specs Validation
# ──────────────────────────────────────────────────────────────────────────────
namespace :adr do
  desc 'Validate Architecture Decision Records in openspec/adrs'
  task :validate do
    adrs_dir = File.join(ROOT, "openspec", "adrs")
    unless Dir.exist?(adrs_dir)
      raise "ADR directory not found: #{adrs_dir}"
    end
    files = Dir.glob(File.join(adrs_dir, "*.md"))
    if files.empty?
      raise "No ADRs found in #{adrs_dir}"
    end
    puts "Validating #{files.size} ADR(s)..."
    required_sections = ["# ADR-", "## Estado", "## Contexto", "## Decisión", "## Consecuencias"]
    failed = false
    files.each do |file|
      content = File.read(file)
      missing = required_sections.reject { |s| content.include?(s) }
      if missing.empty?
        puts "  \e[32m✓\e[0m #{File.basename(file)}"
      else
        puts "  \e[31m✗\e[0m #{File.basename(file)} (missing: #{missing.join(', ')})"
        failed = true
      end
    end
    raise "ADR validation failed" if failed
    puts "\e[32mAll ADRs valid!\e[0m"
  end
end

namespace :spec do
  desc 'Validate OpenAPI, AsyncAPI and ADR specifications'
  task :validate => ['adr:validate'] do
    puts "Validating OpenSpecs..."
    run_cmd("npx openspec validate --all", fail: false)
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Help
# ──────────────────────────────────────────────────────────────────────────────
desc 'Show all available tasks'
task :help do
  puts <<~HELP
    poc-camel - Available Tasks
    ===========================
      rake help              Show all available tasks
      rake infra:start       Start all infrastructure services
      rake infra:stop        Stop all infrastructure services
      rake test:run          Run all tests (unit + integration + e2e) with coverage gate 98%
      rake adr:validate      Validate Architecture Decision Records in openspec/adrs
      rake spec:validate     Validate OpenAPI, AsyncAPI and ADR specifications
      rake k8s:cluster       Create Kind cluster (idempotent)
      rake k8s:build         Build JARs + Docker images + load into Kind
      rake k8s:deploy        Deploy to Kubernetes (auto-creates cluster)
      rake k8s:undeploy      Undeploy from Kubernetes (APPS_ONLY=1, CLUSTER_DELETE=1)
      rake k8s:status        Alias for k8s:ps (pods + services + HPAs table)
      rake k8s:ps            Rich table: pods (ready/status/restarts/age/ip), services, HPAs
      rake k8s:kafka         Rich table: Kafka topics + optional consumer-group lag
      rake k8s:check         Verify pods + log asserts (APP=, EXPECT_ABSENT=, EXPECT_PRESENT=, SMOKE=1)
      rake k8s:e2e           Comprehensive E2E tests (PHASE=phase-name)

    Flags (ENV, hidden from rake -T):
      test:run     COVERAGE=0 (skip coverage), SCOPE=processor|backend|shared
      infra:start  SVC=name (follow logs after start)
      k8s:*        APP=gateway|processor|all, OVERLAY=dev|prod
      k8s:check    EXPECT_ABSENT=, EXPECT_PRESENT=, SMOKE=1
      k8s:kafka    TOPIC=<substr>  filter topics by name substring
                   CONSUMER_GROUPS=1  show per-partition lag for all consumer groups
      k8s:e2e      PHASE=infra|connectivity|happy-path|fraud|idempotency|validation|health|circuit|observability|resilience
    Env knobs: OVERLAY=#{OVERLAY} CLUSTER=#{CLUSTER} NAMESPACE=#{NAMESPACE} CONTAINER_ENGINE=#{CONTAINER_ENGINE}
  HELP
end

task :default => :help
