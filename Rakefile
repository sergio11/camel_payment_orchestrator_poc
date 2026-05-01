# Rakefile - Payment Orchestration Layer SDD Workflow
# Requires: Ruby 3.x, Bundler, Podman, Kind, kubectl

require "rake"
require "fileutils"

def run_cmd(cmd, chdir: nil)
  puts ">> #{cmd}"
  ok = if chdir
         Dir.chdir(chdir) { system(cmd) }
       else
         system(cmd)
       end
  raise "Command failed: #{cmd}" unless ok
end

def run_openspec(cmd)
  run_cmd("npm.cmd run openspec -- #{cmd}")
end

ROOT = File.expand_path(__dir__)
OPEN_SPEC_CHANGES = File.join(ROOT, "specs", "changes")
OPEN_SPEC_TEMPLATES = File.join(ROOT, "config", "templates", "change")

def resolve_change_id(args)
  change_id = args[:change] || ENV["CHANGE"]
  raise "Missing change id. Use CHANGE=<id> or rake sdd:<task>[<id>]" if change_id.nil? || change_id.strip.empty?
  change_id.strip
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
  # Fallback to local DB if we are likely on a dev machine and DB_URL points to docker service 'db'
  if ENV["DATABASE_URL"] && ENV["DATABASE_URL"].include?("@db:") && ENV["DATABASE_URL_LOCAL"]
    puts ">> Using DATABASE_URL_LOCAL as fallback for local execution"
    ENV["DATABASE_URL"] = ENV["DATABASE_URL_LOCAL"]
  end
end

def change_dir(change_id)
  File.join(OPEN_SPEC_CHANGES, change_id)
end

def ensure_change_exists!(change_id)
  path = change_dir(change_id)
  raise "Change not found: #{path}" unless Dir.exist?(path)
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

  {
    total: total,
    done: done,
    pending: total - done,
    pending_items: pending_items
  }
end

def print_task_summary(change_id)
  tasks_file = File.join(change_dir(change_id), "tasks.md")
  stats = parse_task_stats(tasks_file)

  puts "Change: #{change_id}"
  puts "Tasks: #{stats[:done]}/#{stats[:total]} completed"
  puts "Pending: #{stats[:pending]}"

  unless stats[:pending_items].empty?
    puts "Next pending tasks:"
    stats[:pending_items].first(5).each { |item| puts "- #{item}" }
  end

  stats
end

def scaffold_change!(change_id)
  target_dir = change_dir(change_id)
  specs_dir = File.join(target_dir, "specs")

  FileUtils.mkdir_p(target_dir)
  FileUtils.mkdir_p(specs_dir)

  {
    "proposal.md" => File.join(OPEN_SPEC_TEMPLATES, "proposal.md"),
    "design.md" => File.join(OPEN_SPEC_TEMPLATES, "design.md"),
    "tasks.md" => File.join(OPEN_SPEC_TEMPLATES, "tasks.md")
  }.each do |name, template|
    target = File.join(target_dir, name)
    if File.exist?(target)
      puts "skip #{target} (already exists)"
    else
      FileUtils.cp(template, target)
      puts "created #{target}"
    end
  end

  delta_template = File.join(OPEN_SPEC_TEMPLATES, "spec-delta.md")
  sample_delta = File.join(specs_dir, "platform-foundation", "spec.md")
  unless File.exist?(sample_delta)
    FileUtils.mkdir_p(File.dirname(sample_delta))
    FileUtils.cp(delta_template, sample_delta)
    puts "created #{sample_delta}"
  end
end

# ============================================================================
# Namespace: sdd (Spec-Driven Development)
# ============================================================================

namespace :sdd do
  desc "One-time setup for SDD workflow (root + backend + frontend dependencies)"
  task :setup do
    load_env
    run_cmd("npm.cmd install")
    run_cmd("npm.cmd install", chdir: File.join(ROOT, "backend")) if Dir.exist?(File.join(ROOT, "backend"))
    run_cmd("npm.cmd install", chdir: File.join(ROOT, "frontend")) if Dir.exist?(File.join(ROOT, "frontend"))
    Rake::Task["db:push"].invoke if Rake::Task.task_defined?("db:push")
    run_cmd("npm.cmd run openspec:list")
  end

  desc "Initialize/scaffold a new change (usage: rake sdd:init[my-change-id])"
  task :init, [:change] do |_, args|
    change_id = resolve_change_id(args)
    scaffold_change!(change_id)
    puts
    puts "Next:"
    puts "- Fill proposal/design/tasks in openspec/changes/#{change_id}/"
    puts "- Run: rake sdd:check[#{change_id}]"
  end

  desc "List OpenSpec items through SDD entrypoint"
  task :list do
    run_cmd("npm.cmd run openspec:list")
  end

  desc "Show a change or spec (usage: rake sdd:show[phase-1-bootstrap])"
  task :show, [:target] do |_, args|
    target = args[:target] || ENV["TARGET"]
    if target.nil? || target.strip.empty?
      raise "Missing target. Use: rake sdd:show[phase-1-bootstrap] or TARGET=phase-1-bootstrap rake sdd:show"
    end
    run_openspec("show #{target}")
  end

  desc "Validate all changes and specs"
  task :validate do
    run_cmd("npm.cmd run openspec:validate")
  end

  desc "Validate changes only"
  task :validate_changes do
    run_cmd("npm.cmd run openspec:validate:changes")
  end

  desc "Validate specs only"
  task :validate_specs do
    run_cmd("npm.cmd run openspec:validate:specs")
  end

  desc "Show current progress and next pending tasks (usage: rake sdd:status[change-id])"
  task :status, [:change] do |_, args|
    change_id = resolve_change_id(args)
    ensure_change_exists!(change_id)
    print_task_summary(change_id)
  end

  desc "Validate a change workflow (tasks + OpenSpec change validation)"
  task :check, [:change] do |_, args|
    change_id = resolve_change_id(args)
    ensure_change_exists!(change_id)
    print_task_summary(change_id)
    Rake::Task["sdd:validate_changes"].invoke
    run_openspec("validate #{change_id}")
  end

  desc "Gate for merge: requires zero pending tasks and passing validation"
  task :ship, [:change] do |_, args|
    change_id = resolve_change_id(args)
    ensure_change_exists!(change_id)
    stats = print_task_summary(change_id)
    raise "Cannot ship: #{stats[:pending]} pending tasks in #{change_id}" if stats[:pending] > 0

    Rake::Task["sdd:validate_changes"].invoke
    run_openspec("validate #{change_id}")
    puts "Ship gate passed for #{change_id}"
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

namespace :docker do
  desc "Start containers in detached mode"
  task :up do
    run_cmd("docker compose up -d")
  end

  desc "Start containers with forced rebuild"
  task :up_build do
    run_cmd("docker compose up -d --build")
  end

  desc "Stop and remove containers"
  task :down do
    run_cmd("docker compose down")
  end

  desc "Rebuild stack (down + up --build)"
  task :rebuild do
    Rake::Task["docker:down"].invoke
    Rake::Task["docker:up_build"].invoke
  end

  desc "Rebuild and start frontend service only"
  task :rebuild_frontend do
    run_cmd("docker compose up -d --build frontend")
  end

  desc "Rebuild and start backend service only"
  task :rebuild_backend do
    run_cmd("docker compose up -d --build backend")
  end

  desc "Restart running services"
  task :restart do
    run_cmd("docker compose restart")
  end

  desc "Show service status"
  task :ps do
    run_cmd("docker compose ps")
  end

  desc "Follow logs (usage: rake docker:logs or rake docker:logs[backend])"
  task :logs, [:service] do |_, args|
    service = args[:service] || ENV["SERVICE"]
    if service && !service.strip.empty?
      run_cmd("docker compose logs -f #{service.strip}")
    else
      run_cmd("docker compose logs -f")
    end
  end
end

namespace :db do
  desc "Push schema changes to the database (prisma db push)"
  task :push do
    load_env
    run_cmd("npx.cmd prisma db push --schema=prisma/schema.prisma", chdir: File.join(ROOT, "backend")) if Dir.exist?(File.join(ROOT, "backend"))
  end

  desc "Open Prisma Studio"
  task :studio do
    load_env
    run_cmd("npx.cmd prisma studio --schema=prisma/schema.prisma", chdir: File.join(ROOT, "backend")) if Dir.exist?(File.join(ROOT, "backend"))
  end
end

desc "List available Rake tasks"
task default: ["-T"]