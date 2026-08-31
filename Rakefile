# Rakefile - Payment Orchestration Layer (poc-camel)
# Requires: Ruby 3.x, Podman, Maven

require "rake"
require "fileutils"

CONTAINER_ENGINE = ENV['CONTAINER_ENGINE'] || 'podman'
PODMAN_DOCKER_HOST = "npipe:////./pipe/podman-machine-default"

ROOT = File.expand_path(__dir__)

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
    setup_podman_env
    cmd = File.exist?('mvnw') ? './mvnw' : 'mvn'
    begin
      sh "#{cmd} clean test -Dsurefire.useFile=false"
    ensure
      print_coverage_table
    end
  end

  desc 'Run all tests without coverage (faster)'
  task :quick do
    setup_podman_env
    cmd = File.exist?('mvnw') ? './mvnw' : 'mvn'
    sh "#{cmd} clean test -Dsurefire.useFile=false -Djacoco.skip=true"
  end

  desc 'Run only payment-processor tests'
  task :payment do
    setup_podman_env
    cmd = File.exist?('mvnw') ? './mvnw' : 'mvn'
    begin
      sh "#{cmd} test -pl payment-processor -am -Dsurefire.useFile=false"
    ensure
      print_coverage_table
    end
  end

  desc 'Show coverage report'
  task :coverage do
    print_coverage_table
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Infrastructure Tasks
# ──────────────────────────────────────────────────────────────────────────────
namespace :infra do
  desc 'Start infrastructure services (Kafka, Monitoring, Jaeger)'
  task :up do
    infra_dir = File.join(ROOT, "kubernetes", "infrastructure")
    if Dir.exist?(infra_dir)
      Dir.glob("#{infra_dir}/*.yaml").each do |manifest|
        run_cmd("podman kube play #{manifest}", fail: false)
      end
    else
      run_cmd("podman-compose up -d", fail: false)
    end
  end

  desc 'Stop infrastructure services'
  task :down do
    run_cmd("podman pod rm -f kafka-stack", fail: false)
    run_cmd("podman pod rm -f monitoring-stack", fail: false)
    run_cmd("podman pod rm -f jaeger", fail: false)
  end

  desc 'Show infrastructure status'
  task :ps do
    run_cmd("podman pod ps")
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Kubernetes Tasks
# ──────────────────────────────────────────────────────────────────────────────
namespace :k8s do
  desc 'Deploy to Kind cluster'
  task :deploy do
    run_cmd("kubectl apply -k #{File.join(ROOT, 'kubernetes', 'overlays', 'dev')}")
  end

  desc 'Undeploy from Kind cluster'
  task :undeploy do
    run_cmd("kubectl delete -k #{File.join(ROOT, 'kubernetes', 'overlays', 'dev')}", fail: false)
  end

  desc 'Show all pods'
  task :pods do
    run_cmd("kubectl get pods -A")
  end

  desc 'Show all services'
  task :svc do
    run_cmd("kubectl get svc -A")
  end

  desc 'Build container images'
  task :build do
    docker_dir = File.join(ROOT, "docker")
    run_cmd("podman build -t poc-camel/api-gateway:dev -f #{File.join(docker_dir, 'Dockerfile.api-gateway')} .") if File.exist?(File.join(docker_dir, "Dockerfile.api-gateway"))
    run_cmd("podman build -t poc-camel/payment-processor:dev -f #{File.join(docker_dir, 'Dockerfile.payment-processor')} .") if File.exist?(File.join(docker_dir, "Dockerfile.payment-processor"))
  end

  desc 'Load images into Kind cluster'
  task :load do
    run_cmd("kind load docker-image poc-camel/api-gateway:dev --name poc-camel", fail: false)
    run_cmd("kind load docker-image poc-camel/payment-processor:dev --name poc-camel", fail: false)
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Build Tasks
# ──────────────────────────────────────────────────────────────────────────────
namespace :build do
  desc 'Build without tests'
  task :compile do
    cmd = File.exist?('mvnw') ? './mvnw' : 'mvn'
    sh "#{cmd} clean package -DskipTests"
  end

  desc 'Build and test'
  task :full do
    cmd = File.exist?('mvnw') ? './mvnw' : 'mvn'
    sh "#{cmd} clean verify"
  end
end

# ──────────────────────────────────────────────────────────────────────────────
# Help
# ──────────────────────────────────────────────────────────────────────────────
desc 'List all available tasks'
task :default do
  puts <<~HELP
    Payment Orchestration Layer (poc-camel) - Available Tasks
    ==========================================================
      rake test:run          Run all tests + JaCoCo coverage
      rake test:quick        Run tests without coverage (faster)
      rake test:payment      Run payment-processor tests only
      rake test:coverage     Show coverage table from last run

      rake build:compile     Build without tests
      rake build:full        Build + test + verify

      rake infra:up          Start infrastructure
      rake infra:down        Stop infrastructure
      rake infra:ps          Show pod status

      rake k8s:deploy        Deploy to Kind cluster
      rake k8s:undeploy      Undeploy from Kind
      rake k8s:pods          Show pods
      rake k8s:build         Build container images
      rake k8s:load          Load images into Kind
  HELP
end
