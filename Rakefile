# Rakefile - Payment Orchestration Layer SDD Workflow
# Requires: Ruby 3.x, Bundler, Podman, Kind, kubectl

require "rake"
require "fileutils"
require "yaml"

ROOT = File.expand_path(__dir__)

# ============================================================================
# Helper Functions
# ============================================================================

def run_cmd(cmd, chdir: nil, fail: true)
  puts ">> #{cmd}"
  ok = if chdir
         Dir.chdir(chdir) { system(cmd) }
       else
         system(cmd)
       end
  raise "Command failed: #{cmd}" if fail && !ok
  ok
end

def load_env
  env_file = File.join(ROOT, ".env")
  return unless File.exist?(env_file)
  puts ">> Loading .env from #{ROOT}"
  File.readlines(env_file).each do |line|
    line = line.strip
    next if line.empty? || line.start_with?("#") || !line.include?("=")
    key, val = line.split("=", 2)
    ENV[key] ||= val
  end
end

def change_dir(change_id)
  File.join(ROOT, "specs", "changes", change_id)
end

def ensure_change_exists!(change_id)
  path = change_dir(change_id)
  raise "Change not found: #{path}" unless Dir.exist?(path)
end

# ============================================================================
# Constants
# ============================================================================

OPENSPEC_CHANGES = File.join(ROOT, "specs", "changes")
TEMPLATES_DIR = File.join(ROOT, "config", "templates", "change")

# ============================================================================
# Namespace: sdd (Spec-Driven Development)
# ============================================================================

namespace :sdd do
  desc "One-time setup: install deps, create cluster, start services"
  task :setup do
    puts "=== SDD Setup ==="
    
    if File.exist?(File.join(ROOT, "Gemfile"))
      run_cmd("bundle install")
    end
    
    if File.exist?(File.join(ROOT, "package.json"))
      run_cmd("npm install")
    end
    
    puts "\n=== Setting up Kind Cluster ==="
    run_cmd("kind get clusters 2>nul | findstr /C:poc-camel >nul 2>&1 || kind create cluster --name poc-camel", fail: false)
    
    puts "\n=== Starting Podman Services ==="
    Rake::Task["podman:up"].invoke
    
    puts "\n=== Setup Complete ==="
    puts "Run: rake sdd:list"
  end

  desc "Initialize new change (usage: rake sdd:init[phase-1-foundation])"
  task :init, [:change] do |_, args|
    change_id = args[:change] || ENV["CHANGE"]
    raise "Missing CHANGE id. Usage: rake sdd:init[phase-1-foundation]" if change_id.nil? || change_id.strip.empty?
    
    change_id = change_id.strip
    target_dir = change_dir(change_id)
    specs_dir = File.join(target_dir, "specs")
    
    FileUtils.mkdir_p(target_dir)
    FileUtils.mkdir_p(specs_dir)
    
    templates = {
      "proposal.md" => File.join(TEMPLATES_DIR, "proposal.md"),
      "design.md" => File.join(TEMPLATES_DIR, "design.md"),
      "tasks.md" => File.join(TEMPLATES_DIR, "tasks.md"),
      "risks.md" => File.join(TEMPLATES_DIR, "risks.md"),
      "api-changes.md" => File.join(TEMPLATES_DIR, "api-changes.md")
    }
    
    templates.each do |name, template|
      target = File.join(target_dir, name)
      if File.exist?(target)
        puts "SKIP #{target} (already exists)"
      elsif File.exist?(template)
        FileUtils.cp(template, target)
        puts "CREATED #{target}"
      else
        File.write(target, "# #{name.gsub("-", " ").titleize}\n\n")
        puts "CREATED #{target} (empty template)"
      end
    end
    
    puts "\nNext steps:"
    puts "- Edit specs/changes/#{change_id}/proposal.md"
    puts "- Edit specs/changes/#{change_id}/design.md"
    puts "- Edit specs/changes/#{change_id}/tasks.md"
    puts "- Run: rake sdd:check[#{change_id}]"
  end

  desc "List all changes"
  task :list do
    puts "=== Changes ==="
    if Dir.exist?(OPENSPEC_CHANGES)
      Dir.glob("#{OPENSPEC_CHANGES}/*").sort.each do |dir|
        next unless Dir.exist?(dir)
        id = File.basename(dir)
        tasks_file = File.join(dir, "tasks.md")
        stats = parse_task_stats(tasks_file)
        puts "#{id}: #{stats[:done]}/#{stats[:total]} tasks"
      end
    else
      puts "No changes found. Run: rake sdd:init[phase-1-foundation]"
    end
  end

  desc "Show change details (usage: rake sdd:show[phase-1-foundation])"
  task :show, [:target] do |_, args|
    target = args[:target] || ENV["TARGET"]
    if target.nil? || target.strip.empty?
      raise "Missing TARGET. Usage: rake sdd:show[phase-1-foundation]"
    end
    
    path = change_dir(target)
    raise "Change not found: #{path}" unless Dir.exist?(path)
    
    puts "=== Change: #{target} ==="
    
    %w[proposal design tasks risks api-changes].each do |doc|
      file = File.join(path, "#{doc}.md")
      if File.exist?(file)
        puts "\n--- #{doc.upcase} ---"
        puts File.read(file)[0..500]
      end
    end
  end

  desc "Validate all changes and specs"
  task :validate do
    puts "=== Validating Specs ==="
    Rake::Task["spec:validate"].invoke
    
    puts "\n=== Validating Changes ==="
    Rake::Task["sdd:validate_changes"].invoke
  end

  desc "Show progress (usage: rake sdd:status[phase-1-foundation])"
  task :status, [:change] do |_, args|
    change_id = resolve_change_id(args)
    ensure_change_exists!(change_id)
    print_task_summary(change_id)
  end

  desc "Validate change workflow (usage: rake sdd:check[phase-1-foundation])"
  task :check, [:change] do |_, args|
    change_id = resolve_change_id(args)
    ensure_change_exists!(change_id)
    
    print_task_summary(change_id)
    Rake::Task["spec:validate"].invoke
  end

  desc "Ship gate: requires zero pending tasks and passing validation"
  task :ship, [:change] do |_, args|
    change_id = resolve_change_id(args)
    ensure_change_exists!(change_id)
    
    stats = print_task_summary(change_id)
    raise "Cannot ship: #{stats[:pending]} pending tasks in #{change_id}" if stats[:pending] > 0
    
    Rake::Task["spec:validate"].invoke
    puts "Ship gate PASSED for #{change_id}"
  end
end

# ============================================================================
# Namespace: podman (Local Services)
# ============================================================================

namespace :podman do
  desc "Start services (Kafka, Prometheus, Jaeger) in detached mode"
  task :up do
    compose_file = File.join(ROOT, "podman-compose.yaml")
    if File.exist?(compose_file)
      run_cmd("podman compose -f #{compose_file} up -d")
      Rake::Task["podman:ps"].invoke
    else
      puts "WARN: podman-compose.yaml not found"
    end
  end

  desc "Start services with forced rebuild"
  task :up_build do
    compose_file = File.join(ROOT, "podman-compose.yaml")
    run_cmd("podman compose -f #{compose_file} up -d --build")
  end

  desc "Stop and remove containers"
  task :down do
    compose_file = File.join(ROOT, "podman-compose.yaml")
    run_cmd("podman compose -f #{compose_file} down", fail: false)
  end

  desc "Rebuild stack (down + up --build)"
  task :rebuild do
    Rake::Task["podman:down"].invoke
    Rake::Task["podman:up_build"].invoke
  end

  desc "Show service status"
  task :ps do
    compose_file = File.join(ROOT, "podman-compose.yaml")
    run_cmd("podman compose -f #{compose_file} ps")
  end

  desc "Follow logs (usage: rake podman:logs or rake podman:logs[service])"
  task :logs, [:service] do |_, args|
    service = args[:service] || ENV["SERVICE"]
    compose_file = File.join(ROOT, "podman-compose.yaml")
    if service && !service.strip.empty?
      run_cmd("podman compose -f #{compose_file} logs -f #{service.strip}")
    else
      run_cmd("podman compose -f #{compose_file} logs -f")
    end
  end

  desc "Clean volumes"
  task :clean do
    run_cmd("podman volume rm -f poc-camel-kafka poc-camel-prometheus poc-camel-jaeger 2>nul", fail: false)
  end
end

# ============================================================================
# Namespace: k8s (Kubernetes)
# ============================================================================

namespace :k8s do
  desc "Deploy to Kind cluster"
  task :deploy do
    puts "=== Deploying to Kind ==="
    run_cmd("kubectl apply -k #{File.join(ROOT, 'kubernetes', 'overlays', 'dev')}")
  end

  desc "Delete deployment"
  task :undeploy do
    run_cmd("kubectl delete -k #{File.join(ROOT, 'kubernetes', 'overlays', 'dev')}", fail: false)
  end

  desc "Show all pods"
  task :pods do
    run_cmd("kubectl get pods -A")
  end

  desc "Show all services"
  task :svc do
    run_cmd("kubectl get svc -A")
  end

  desc "Show pod logs (usage: rake k8s:logs[payment-processor-xxxxx])"
  task :logs, [:pod] do |_, args|
    pod = args[:pod] || ENV["POD"]
    raise "Missing POD name" unless pod
    run_cmd("kubectl logs #{pod} -f")
  end

  desc "Port forward service (usage: rake k8s:port[service,8080])"
  task :port, [:service, :port] do |_, args|
    service = args[:service] || "payment-processor"
    port = args[:port] || 8080
    run_cmd("kubectl port-forward svc/#{service} #{port}:#{port}")
  end
end

# ============================================================================
# Namespace: spec (Specs Validation & Generation)
# ============================================================================

namespace :spec do
  desc "Validate OpenAPI/AsyncAPI specs"
  task :validate do
    puts "=== Validating OpenAPI ==="
    openapi_dir = File.join(ROOT, "specs", "openapi")
    if Dir.exist?(openapi_dir)
      Dir.glob("#{openapi_dir}/*.yaml").each do |file|
        puts "Checking #{File.basename(file)}..."
        run_cmd("npx.cmd spectral lint #{file}", fail: false)
      end
    end
    
    puts "\n=== Validating AsyncAPI ==="
    asyncapi_dir = File.join(ROOT, "specs", "asyncapi")
    if Dir.exist?(asyncapi_dir)
      Dir.glob("#{asyncapi_dir}/*.yaml").each do |file|
        puts "Checking #{File.basename(file)}..."
        run_cmd("npx.cmd spectral lint #{file}", fail: false)
      end
    end
  end

  desc "Generate server stubs from OpenAPI"
  task :codegen do
    openapi_file = File.join(ROOT, "specs", "openapi", "payment-api.yaml")
    output_dir = File.join(ROOT, "generated")
    
    if File.exist?(openapi_file)
      FileUtils.mkdir_p(output_dir)
      puts "Generating Quarkus server stubs..."
      run_cmd("npx.cmd @openapitools/openapi-generator-cli generate -i #{openapi_file} -g quarkus -o #{output_dir}/quarkus")
    else
      raise "OpenAPI spec not found: #{openapi_file}"
    end
  end

  desc "Serve OpenAPI docs"
  task :docs do
    openapi_file = File.join(ROOT, "specs", "openapi", "payment-api.yaml")
    if File.exist?(openapi_file)
      run_cmd("npx.cmd @stoplight/spectral serve #{openapi_file}")
    end
  end
end

# ============================================================================
# Namespace: build (Build & Test)
# ============================================================================

namespace :build do
  desc "Build Maven project"
  task :maven do
    Dir.glob("#{ROOT}/**/pom.xml").each do |pom|
      project_dir = File.dirname(pom)
      puts "Building #{project_dir}..."
      run_cmd("mvn clean package -DskipTests", chdir: project_dir)
    end
  end

  desc "Build container images"
  task :images do
    puts "=== Building Container Images ==="
    docker_dir = File.join(ROOT, "docker")
    run_cmd("podman build -t poc-camel/api-gateway:latest -f #{File.join(docker_dir, 'Dockerfile.api-gateway')} .") if File.exist?(File.join(docker_dir, "Dockerfile.api-gateway"))
    run_cmd("podman build -t poc-camel/payment-processor:latest -f #{File.join(docker_dir, 'Dockerfile.processor')} .") if File.exist?(File.join(docker_dir, "Dockerfile.processor"))
  end
end

# ============================================================================
# Namespace: dev (Development Helpers)
# ============================================================================

namespace :dev do
  desc "Start development mode (services + deploy)"
  task :up do
    Rake::Task["podman:up"].invoke
    Rake::Task["k8s:deploy"].invoke
    puts "\nServices available:"
    puts "- API Gateway: http://localhost:8080"
    puts "- Kafka: localhost:9092"
    puts "- Prometheus: http://localhost:9090"
    puts "- Jaeger: http://localhost:16686"
  end

  desc "Clean everything"
  task :clean do
    Rake::Task["podman:down"].invoke
    Rake::Task["k8s:undeploy"].invoke
    Rake::Task["podman:clean"].invoke
    puts "All cleaned"
  end

  desc "Show status dashboard"
  task :status do
    puts "=== Podman Services ==="
    Rake::Task["podman:ps"].invoke
    
    puts "\n=== Kubernetes Pods ==="
    Rake::Task["k8s:pods"].invoke
  end
end

# ============================================================================
# Helper Methods
# ============================================================================

def resolve_change_id(args)
  change_id = args[:change] || ENV["CHANGE"]
  raise "Missing change id. Use CHANGE=<id> or rake sdd:<task>[<id>]" if change_id.nil? || change_id.strip.empty?
  change_id.strip
end

def parse_task_stats(tasks_file)
  return { total: 0, done: 0, pending: 0, pending_items: [] } unless File.exist?(tasks_file)

  total = 0
  done = 0
  pending_items = []

  File.readlines(tasks_file, chomp: true).each do |line|
    case line
    when /^\s*-\s*\[[xX]\]\s+(.+)$/
      total += 1
      done += 1
    when /^\s*-\s*\[\s\]\s+(.+)$/
      total += 1
      pending_items << Regexp.last_match(1).strip
    end
  end

  { total: total, done: done, pending: total - done, pending_items: pending_items }
end

def print_task_summary(change_id)
  tasks_file = File.join(change_dir(change_id), "tasks.md")
  stats = parse_task_stats(tasks_file)

  puts "Change: #{change_id}"
  puts "Tasks: #{stats[:done]}/#{stats[:total]} completed"
  puts "Pending: #{stats[:pending]}"

  unless stats[:pending_items].empty?
    puts "\nNext pending tasks:"
    stats[:pending_items].first(5).each { |item| puts "- #{item}" }
  end

  stats
end

task default: ["sdd:list"]