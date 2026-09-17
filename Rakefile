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

  task :status do
    run_cmd("kubectl get pods -n #{NAMESPACE} -o wide", fail: false)
    run_cmd("kubectl get svc -n #{NAMESPACE}", fail: false)
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
      rake k8s:cluster        Create Kind cluster (idempotent)
      rake k8s:build         Build JARs + Docker images + load into Kind
      rake k8s:deploy        Deploy to Kubernetes (auto-creates cluster)
      rake k8s:undeploy      Undeploy from Kubernetes (APPS_ONLY=1, CLUSTER_DELETE=1)
      rake k8s:check         Verify pods + log asserts (APP=, EXPECT_ABSENT=, EXPECT_PRESENT=, SMOKE=1)

    Flags (ENV, hidden from rake -T):
      test:run  COVERAGE=0 (skip coverage), SCOPE=processor|backend|shared
      infra:start SVC=name (follow logs after start)
      k8s:*     APP=gateway|processor|all, OVERLAY=dev|prod
      k8s:check EXPECT_ABSENT=, EXPECT_PRESENT=, SMOKE=1
    Env knobs: OVERLAY=#{OVERLAY} CLUSTER=#{CLUSTER} NAMESPACE=#{NAMESPACE} CONTAINER_ENGINE=#{CONTAINER_ENGINE}
  HELP
end

task :default => :help
