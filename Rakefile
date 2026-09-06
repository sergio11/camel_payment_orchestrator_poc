# Rakefile - Payment Orchestration Layer (poc-camel)
# Requires: Ruby 3.x, Podman, Maven, kubectl, kind (for k8s:* tasks)
#
# You should never need raw kubectl/kind: every platform operation is wrapped here.
# Quickstart (fresh Kind):
#   rake up                # full deploy: namespace + infra + images + apps
#   rake status            # pods + services
#   rake smoke             # e2e payment check through port-forward
#   rake logs APP=gateway  # follow logs (gateway|processor|all)
#   rake down              # undeploy everything, keep cluster
#   rake k8s:reset         # down + up from scratch
#   rake k8s:cluster_delete# delete the Kind cluster itself
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
  desc 'Run all tests with JaCoCo coverage'
  task :run do
    begin
      mvn("clean test -Dsurefire.useFile=false")
    ensure
      print_coverage_table
    end
  end

  desc 'Run all tests without coverage (faster)'
  task :quick do
    mvn("clean test -Dsurefire.useFile=false -Djacoco.skip=true")
  end

  desc 'Run only payment-processor tests'
  task :payment do
    begin
      mvn("test -pl payment-processor -am -Dsurefire.useFile=false")
    ensure
      print_coverage_table
    end
  end

  desc 'Verify coverage meets minimum threshold (98%)'
  task :verify do
    mvn("clean verify -Dsurefire.useFile=false")
  end

  desc 'Alias of test:run'
  task :all => :run

  desc 'Show coverage report'
  task :coverage do
    print_coverage_table
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Local Infrastructure Tasks (podman-compose, no K8s)
# ──────────────────────────────────────────────────────────────────────────────
namespace :infra do
  desc 'Start local infrastructure (podman-compose: kafka, postgres, monitoring)'
  task :up do
    run_cmd("#{CONTAINER_ENGINE}-compose up -d", fail: false)
    Rake::Task['infra:ps'].invoke
  end

  desc 'Stop local infrastructure'
  task :down do
    run_cmd("#{CONTAINER_ENGINE}-compose down -v", fail: false)
  end

  desc 'Show infrastructure status'
  task :ps do
    run_cmd("#{CONTAINER_ENGINE} pod ps", fail: false)
    run_cmd("#{CONTAINER_ENGINE} ps", fail: false)
  end

  desc 'Show infrastructure logs (SVC=name, default all)'
  task :logs do
    svc = ENV['SVC'] || ''
    run_cmd("#{CONTAINER_ENGINE}-compose logs -f --tail=100 #{svc}", fail: false)
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Kubernetes Tasks (Kind) — no raw kubectl/kind needed
# ──────────────────────────────────────────────────────────────────────────────
namespace :k8s do
  desc "Full deployment into Kind (OVERLAY=#{OVERLAY})"
  task :up do
    Rake::Task['k8s:namespace'].invoke
    Rake::Task['k8s:infra'].invoke
    Rake::Task['k8s:build'].invoke
    if kind_cluster_exists?
      Rake::Task['k8s:load'].invoke
    else
      puts "Skipping k8s:load (no Kind cluster #{CLUSTER} found — run rake k8s:cluster first)"
    end
    Rake::Task['k8s:deploy'].invoke
    puts "\n\e[32m=== Deployment complete (OVERLAY=#{OVERLAY}) ===\e[0m"
    Rake::Task['k8s:status'].invoke
  end

  desc 'Reset from scratch (down + up)'
  task :reset do
    Rake::Task['k8s:down'].invoke
    Rake::Task['k8s:up'].invoke
  end

  desc "Create namespace #{NAMESPACE} (idempotent)"
  task :namespace do
    run_cmd("kubectl create namespace #{NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -", fail: false)
  end

  desc 'Deploy infrastructure (Kafka, Postgres, Jaeger, Monitoring)'
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

  desc 'Deploy applications via Kustomize (OVERLAY=dev|prod)'
  task :deploy do
    unless Dir.exist?(OVERLAY_DIR)
      raise "Overlay not found: #{OVERLAY_DIR} (use OVERLAY=dev|prod)"
    end
    run_cmd("kubectl apply -k #{OVERLAY_DIR}")
    puts "\nWaiting for applications to be ready..."
    k8s_wait("app=api-gateway", timeout: 180)
    k8s_wait("app=payment-processor", timeout: 180)
  end

  desc 'Create Kind cluster (idempotent)'
  task :cluster do
    if kind_cluster_exists?
      puts "Kind cluster #{CLUSTER} already exists, skipping creation"
    else
      run_cmd("kind create cluster --name #{CLUSTER}")
    end
  end

  desc 'Delete Kind cluster'
  task :cluster_delete do
    run_cmd("kind delete cluster --name #{CLUSTER}", fail: false)
  end

  desc 'Undeploy everything (apps first, infra second, namespace last)'
  task :down do
    run_cmd("kubectl delete -k #{OVERLAY_DIR}", fail: false)
    run_cmd("kubectl delete -f #{INFRA_DIR} --recursive", fail: false)
    run_cmd("kubectl delete namespace #{NAMESPACE}", fail: false)
  end

  desc 'Undeploy apps only (keep infra + namespace)'
  task :undeploy do
    run_cmd("kubectl delete -k #{OVERLAY_DIR}", fail: false)
  end

  desc 'Restart app deployments (APP=gateway|processor|all)'
  task :restart do
    app = (ENV['APP'] || 'all').downcase
    targets = case app
              when 'gateway' then ['api-gateway']
              when 'processor' then ['payment-processor']
              else ['api-gateway', 'payment-processor']
              end
    targets.each { |d| kc("rollout restart deployment/#{d} -n #{NAMESPACE}") }
    targets.each do |d|
      label = d == 'api-gateway' ? 'app=api-gateway' : 'app=payment-processor'
      k8s_wait(label, timeout: 180)
    end
  end

  desc 'Rebuild image(s), load into Kind and restart (APP=gateway|processor|all)'
  task :rebuild do
    names = app_targets(ENV['APP']).map(&:first)
    names.each do |name|
      short = "poc-camel/#{name}:dev"
      run_cmd("#{CONTAINER_ENGINE} build -t #{short} -t localhost/#{short} -f #{IMAGES[name]} .")
    end
    Rake::Task['k8s:load'].invoke
    Rake::Task['k8s:restart'].invoke
  end

  desc 'Verify pods and assert log patterns (APP=..., EXPECT_ABSENT=..., EXPECT_PRESENT=...)'
  task :check do
    raise "No cluster context (is podman machine / Kind up?) — recover the environment first" unless system("kubectl cluster-info >NUL 2>&1") || system("kubectl cluster-info >/dev/null 2>&1")
    failed = false
    app_targets(ENV['APP']).each do |_deploy, label|
      puts "== #{label} =="
      run_cmd("kubectl get pods -l #{label} -n #{NAMESPACE} -o wide", fail: false)
      logs = `kubectl logs -l #{label} -n #{NAMESPACE} --tail=200 2>&1`
      absent = ENV['EXPECT_ABSENT']
      present = ENV['EXPECT_PRESENT']
      if absent && !absent.empty? && logs.include?(absent)
        puts "FAIL: found forbidden pattern #{absent.inspect}"
        failed = true
      end
      if present && !present.empty? && !logs.include?(present)
        puts "FAIL: missing expected pattern #{present.inspect}"
        failed = true
      end
      errs = logs.lines.grep(/ERROR|Caused by|Exception/).last(8)
      puts(errs.empty? ? "(no error lines in last 200 log lines)" : errs)
    end
    raise "k8s:check FAILED" if failed
    puts "\e[32mCHECK OK\e[0m"
  end

  desc 'Show pods + services'
  task :status do
    run_cmd("kubectl get pods -n #{NAMESPACE} -o wide")
    run_cmd("kubectl get svc -n #{NAMESPACE}")
  end

  desc 'Alias of k8s:status'
  task :pods => :status

  desc 'Show services'
  task :svc do
    run_cmd("kubectl get svc -n #{NAMESPACE}")
  end

  desc 'Follow logs (APP=gateway|processor|all, default all)'
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
      run_cmd("kubectl logs -f -l app=api-gateway -n #{NAMESPACE} --tail=50 & kubectl logs -f -l app=payment-processor -n #{NAMESPACE} --tail=50", fail: false)
    end
  end

  desc 'Show pod logs (api-gateway)'
  task :logs_api do
    ENV['APP'] = 'gateway'
    Rake::Task['k8s:logs'].invoke
  end

  desc 'Show pod logs (payment-processor)'
  task :logs_processor do
    ENV['APP'] = 'processor'
    Rake::Task['k8s:logs'].invoke
  end

  desc 'Build container images (dual-tag for Kind/dev overlay)'
  task :build do
    IMAGES.each do |name, dockerfile|
      next unless File.exist?(dockerfile)
      short = "poc-camel/#{name}:dev"
      kind_name = "localhost/#{short}"
      run_cmd("#{CONTAINER_ENGINE} build -t #{short} -t #{kind_name} -f #{dockerfile} .")
    end
  end

  desc 'Load images into Kind cluster (Windows-safe via tar file)'
  task :load do
    unless kind_cluster_exists?
      raise "No Kind cluster #{CLUSTER} — run rake k8s:cluster first"
    end
    ['poc-camel/api-gateway:dev', 'localhost/poc-camel/api-gateway:dev',
     'poc-camel/payment-processor:dev', 'localhost/poc-camel/payment-processor:dev'].each do |img|
      tar = File.join(Dir.tmpdir, "kind-load-#{img.gsub(/[\/:]/, '_')}.tar")
      if run_cmd("#{CONTAINER_ENGINE} save -o #{tar} #{img}", fail: false)
        run_cmd("kind load image-archive #{tar} --name #{CLUSTER}", fail: false)
        FileUtils.rm_f(tar)
      else
        puts "WARN: could not save image #{img}, skipping"
      end
    end
  end

  desc 'Forward api-gateway to localhost:8080'
  task :portforward do
    puts "Forwarding api-gateway:8080 -> localhost:8080 (Ctrl+C to stop)"
    run_cmd("kubectl port-forward svc/api-gateway-external 8080:8080 -n #{NAMESPACE}", fail: false)
  end

  desc 'Smoke test: create + fetch a payment through port-forward'
  task :smoke do
    pf = IO.popen("kubectl port-forward svc/api-gateway-external 8080:8080 -n #{NAMESPACE}")
    sleep 6
    begin
      key = SecureRandom.uuid
      payload = { amount: 42.50, currency: "EUR", customerId: "qa-smoke",
                  paymentMethod: "CARD", country: "ES" }
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
# Build Tasks
# ──────────────────────────────────────────────────────────────────────────────
namespace :build do
  desc 'Build without tests'
  task :compile do
    mvn("clean package -DskipTests")
  end

  desc 'Build and test'
  task :full do
    mvn("clean verify")
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Top-level shortcuts (so raw kubectl/kind are never needed)
# ──────────────────────────────────────────────────────────────────────────────
desc 'Full deploy into Kind (shortcut for k8s:up)'
task :up => 'k8s:up'

desc 'Undeploy everything (shortcut for k8s:down)'
task :down => 'k8s:down'

desc 'Show pods + services (shortcut for k8s:status)'
task :status => 'k8s:status'

desc 'Follow app logs (shortcut for k8s:logs, APP=gateway|processor|all)'
task :logs => 'k8s:logs'

desc 'Smoke test a payment (shortcut for k8s:smoke)'
task :smoke => 'k8s:smoke'

# ──────────────────────────────────────────────────────────────────────────────
# Help
# ──────────────────────────────────────────────────────────────────────────────
desc 'List all available tasks'
task :default do
  puts <<~HELP
    Payment Orchestration Layer (poc-camel) — everything runs through Rake,
    no raw kubectl/kind/podman commands needed.
    ==========================================================
    Shortcuts:
      rake up                Full deploy into Kind (OVERLAY=dev|prod)
      rake status            Pods + services
      rake smoke             E2E payment check (create + fetch)
      rake logs [APP=gateway|processor|all]
      rake down              Undeploy everything (keep cluster)

    Tests / build:
      rake test:run          All tests + coverage
      rake test:quick        Tests without coverage
      rake test:payment      payment-processor tests only
      rake test:verify       Coverage gate (98%)
      rake test:coverage     Show last coverage table
      rake build:compile     Package without tests
      rake build:full        clean verify

    Local infra (no K8s):
      rake infra:up          podman-compose up
      rake infra:down        podman-compose down -v
      rake infra:ps          Container status
      rake infra:logs [SVC=] Follow compose logs

    Kind lifecycle + deploy:
      rake k8s:cluster       Create cluster (idempotent)
      rake k8s:cluster_delete Delete cluster
      rake k8s:up            namespace+infra+build+load+apps
      rake k8s:reset         down + up from scratch
      rake k8s:down          Undeploy everything
      rake k8s:undeploy      Apps only (keep infra)
      rake k8s:deploy        Apps via Kustomize (OVERLAY=#{OVERLAY})
      rake k8s:restart [APP=] Rollout restart + wait
      rake k8s:rebuild [APP=] Rebuild image + load + restart
      rake k8s:check [APP=]  Verify pods + assert log patterns
      rake k8s:status        Pods + services
      rake k8s:logs [APP=]   Follow logs
      rake k8s:portforward   Gateway -> localhost:8080
      rake k8s:smoke         E2E payment check

    Env knobs: OVERLAY=dev|prod CLUSTER=#{CLUSTER} NAMESPACE=#{NAMESPACE} CONTAINER_ENGINE=#{CONTAINER_ENGINE}
  HELP
end
