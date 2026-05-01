# Design: Phase 4 - K8s + Observability

## Architecture

### Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Kind | Latest | Local Kubernetes cluster |
| kubectl | Latest | K8s CLI |
| Docker/Podman | Latest | Container runtime |
| Kustomize | v5.x | K8s config management |
| Prometheus | 2.47.0 | Metrics collection |
| Grafana | 10.1.0 | Metrics visualization |
| Jaeger | 1.48.0 | Distributed tracing |
| Quarkus | 3.17.x | Application framework |

### Kubernetes Deployment

```
┌─────────────────────────────────────────────────────────────────────┐
│                        KIND CLUSTER                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    NAMESPACE: poc-camel                       │  │
│  ├──────────────────────────────────────────────────────────────┤  │
│  │                                                               │  │
│  │  ┌─────────────────────┐      ┌─────────────────────────┐   │  │
│  │  │   api-gateway       │      │   payment-processor    │   │  │
│  │  │   Deployment        │      │   Deployment            │   │  │
│  │  │   replicas: 1       │      │   replicas: 2-10 (HPA)  │   │  │
│  │  └─────────────────────┘      └─────────────────────────┘   │  │
│  │           │                             │                    │  │
│  │           └──────────────┬──────────────┘                    │  │
│  │                          ▼                                   │  │
│  │              ┌──────────────────────┐                       │  │
│  │              │   api-gateway Svc     │                       │  │
│  │              │   ClusterIP:8080      │                       │  │
│  │              └──────────────────────┘                       │  │
│  │                                                               │  │
│  │  ┌──────────────────────────────────────────────────────┐   │  │
│  │  │  HorizontalPodAutoscaler                              │   │  │
│  │  │  min:2 max:10 cpu:70% memory:80%                      │   │  │
│  │  └──────────────────────────────────────────────────────┘   │  │
│  │                                                               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Observability Stack

```
┌─────────────┐   scrape    ┌─────────────┐   query     ┌──────────┐
│ Application │ ───────────▶│  Prometheus  │ ──────────▶│ Grafana  │
│ /q/metrics  │   :8080     │   :9090     │   :3000    │ (dashboards)
└─────────────┘             └─────────────┘             └──────────┘
          │                       │                           │
          │   trace               │                           │
          ▼                       ▼                           │
┌─────────────────┐     ┌─────────────────┐                   │
│  OTEL Exporter  │────▶│     Jaeger      │───────────────────▶│   UI
│  :4318          │     │   :16686        │                   │ :16686
└─────────────────┘     └─────────────────┘                   │
                                                                    │
┌─────────────┐                                                   │
│ JSON Logs   │───────────────────────────────────────────────────┘
│ stdout      │
└─────────────┘
```

### Project Structure

```
kubernetes/
├── base/
│   ├── api-gateway-deployment.yaml
│   ├── payment-processor-deployment.yaml
│   ├── services.yaml
│   ├── configmaps.yaml
│   ├── secrets.yaml
│   ├── hpa.yaml
│   └── kustomization.yaml
└── overlays/
    └── dev/
        ├── kustomization.yaml
        └── patches/
            └── scale-patch.yaml

docker/
├── Dockerfile.api-gateway
├── Dockerfile.payment-processor
└── .dockerignore
```

---

## Docker Configuration

### Dockerfile.api-gateway

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar /app/
COPY --from=build /app/target/quarkus-app/app/ /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

RUN addgroup -S poc && adduser -S poc -G poc
USER poc:poc

EXPOSE 8080 8778

ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV JAVA_OPTS="-Xmx512m -Xms256m"

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/q/health/ready || exit 1

CMD ["java", "-jar", "/app/quarkus-run.jar"]
```

### Dockerfile.payment-processor

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar /app/
COPY --from=build /app/target/quarkus-app/app/ /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

RUN addgroup -S poc && adduser -S poc -G poc
USER poc:poc

EXPOSE 8080 8778

ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV JAVA_OPTS="-Xmx1g -Xms512m"

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/q/health/ready || exit 1

CMD ["java", "-jar", "/app/quarkus-run.jar"]
```

### .dockerignore

```
target/
*.class
.git/
.gitignore
*.md
kubernetes/
docs/
```

---

## Kubernetes Manifests

### api-gateway-deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: poc-camel
  labels:
    app: api-gateway
    component: gateway
    version: v1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
        component: gateway
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/q/metrics"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
      containers:
      - name: api-gateway
        image: poc-camel/api-gateway:latest
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: metrics
          containerPort: 8778
          protocol: TCP
        env:
        - name: QUARKUS_PROFILE
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: profile
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: kafka.servers
        - name: JAEGER_ENDPOINT
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: jaeger.endpoint
        envFrom:
        - configMapRef:
            name: app-config
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        volumeMounts:
        - name: tmp
          mountPath: /tmp
      volumes:
      - name: tmp
        emptyDir: {}
```

### payment-processor-deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-processor
  namespace: poc-camel
  labels:
    app: payment-processor
    component: processor
    version: v1
spec:
  replicas: 2
  selector:
    matchLabels:
      app: payment-processor
  template:
    metadata:
      labels:
        app: payment-processor
        component: processor
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/q/metrics"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
      containers:
      - name: payment-processor
        image: poc-camel/payment-processor:latest
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: metrics
          containerPort: 8778
          protocol: TCP
        env:
        - name: QUARKUS_PROFILE
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: profile
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: kafka.servers
        - name: JAEGER_ENDPOINT
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: jaeger.endpoint
        envFrom:
        - configMapRef:
            name: app-config
        - secretRef:
            name: provider-secrets
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        volumeMounts:
        - name: tmp
          mountPath: /tmp
      volumes:
      - name: tmp
        emptyDir: {}
```

### services.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: poc-camel
  labels:
    app: api-gateway
spec:
  type: ClusterIP
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  - name: metrics
    port: 8778
    targetPort: 8778
    protocol: TCP
  selector:
    app: api-gateway
---
apiVersion: v1
kind: Service
metadata:
  name: payment-processor
  namespace: poc-camel
  labels:
    app: payment-processor
spec:
  type: ClusterIP
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  - name: metrics
    port: 8778
    targetPort: 8778
    protocol: TCP
  selector:
    app: payment-processor
```

### configmaps.yaml

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: poc-camel
data:
  profile: "dev"
  kafka.servers: "kafka:9092"
  kafka.topic.payments.received: "payments.events.received"
  kafka.topic.payments.processed: "payments.events.processed"
  kafka.topic.payments.failed: "payments.events.failed"
  kafka.topic.payments.dead-letter: "payments.events.dead-letter"
  kafka.topic.fraud.detected: "fraud.events.detected"
  jaeger.endpoint: "http://jaeger:14268/api/traces"
  jaeger.service.name: "payment-processor"
  log.level: "INFO"
  log.format.json: "true"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: fraud-rules-config
  namespace: poc-camel
data:
  fraud.high-amount-threshold: "15000"
  fraud.high-risk-countries: "XX,YY,ZZ"
  fraud.max-rapid-retries: "3"
  fraud.new-customer-age-days: "30"
  fraud.risk-score-threshold-high: "80"
  fraud.risk-score-threshold-medium: "50"
```

### secrets.yaml

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: provider-secrets
  namespace: poc-camel
type: Opaque
stringData:
  provider-a-url: "http://provider-a:8081"
  provider-b-url: "http://provider-b:8082"
data:
  # Base64 encoded - generated via: echo -n "value" | base64
  # provider-a-url: aHR0cDovL3Byb3ZpZGVyLWE6ODA4MQ==
  # provider-b-url: aHR0cDovL3Byb3ZpZGVyLWI6ODA4Mg==
```

### hpa.yaml

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-processor-hpa
  namespace: poc-camel
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-processor
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
      - type: Pods
        value: 1
        periodSeconds: 60
      selectPolicy: Min
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
      - type: Pods
        value: 2
        periodSeconds: 15
      selectPolicy: Max
```

---

## Kustomization

### base/kustomization.yaml

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: poc-camel

resources:
- api-gateway-deployment.yaml
- payment-processor-deployment.yaml
- services.yaml
- configmaps.yaml
- hpa.yaml
- secrets.yaml

commonLabels:
  app: poc-camel
  environment: dev

images:
- name: poc-camel/api-gateway
  newTag: latest
- name: poc-camel/payment-processor
  newTag: latest
```

### overlays/dev/kustomization.yaml

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
- ../../base

namespace: poc-camel

namePrefix: dev-

commonLabels:
  environment: dev
  version: v1

replicas:
- name: api-gateway
  count: 1
- name: payment-processor
  count: 2

configMapGenerator:
- name: app-config
  behavior: merge
  literals:
  - QUARKUS_PROFILE=dev
  - LOG_LEVEL=DEBUG
```

---

## Observability Configuration

### Prometheus scrape configuration

```yaml
# prometheus-config.yaml (ConfigMap)
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: monitoring
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s

    scrape_configs:
    - job_name: 'kubernetes-apiservers'
      kubernetes_sd_configs:
      - role: endpoints
      scheme: https
      tls_config:
        ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
      bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token

    - job_name: 'payment-processor'
      kubernetes_sd_configs:
      - role: pod
      namespaces:
        names:
        - poc-camel
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?(\d+)
        replacement: $1:$2
        target_label: __address__
      - action: labelmap
        regex: __meta_kubernetes_pod_label_(.+)
        replacement: $1

    - job_name: 'api-gateway'
      kubernetes_sd_configs:
      - role: pod
        namespaces:
        - poc-camel
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: api-gateway
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        target_label: port
      - source_labels: [__address__]
        action: replace
        regex: ([^:]+):(\d+)
        replacement: $1:8080
        target_label: __address__

    - job_name: 'kafka'
      static_configs:
      - targets: ['kafka:9092']
        labels:
          app: kafka
```

### Grafana Dashboard

```json
{
  "dashboard": {
    "title": "Payment Orchestration - Overview",
    "tags": ["payment", "camel", "quarkus", "poc-camel"],
    "timezone": "browser",
    "refresh": "30s",
    "panels": [
      {
        "id": 1,
        "title": "Payment Request Rate",
        "type": "graph",
        "gridPos": {"x": 0, "y": 0, "w": 12, "h": 8},
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count{service=\"payment-gateway\"}[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ],
        "yaxes": [{"format": "short"}, {"format": "short"}]
      },
      {
        "id": 2,
        "title": "Payment Success Rate",
        "type": "gauge",
        "gridPos": {"x": 12, "y": 0, "w": 6, "h": 8},
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count{service=\"payment-gateway\",status=~\"2..\"}[5m])) / sum(rate(http_server_requests_seconds_count{service=\"payment-gateway\"}[5m])) * 100"
          }
        ],
        "fieldConfig": {"defaults": {"unit": "percent", "min": 0, "max": 100}}
      },
      {
        "id": 3,
        "title": "Payment Processing Latency (p50)",
        "type": "graph",
        "gridPos": {"x": 0, "y": 8, "w": 12, "h": 8},
        "targets": [
          {
            "expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{service=\"payment-gateway\"}[5m])) by (le))",
            "legendFormat": "p50"
          },
          {
            "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{service=\"payment-gateway\"}[5m])) by (le))",
            "legendFormat": "p95"
          }
        ]
      },
      {
        "id": 4,
        "title": "Fraud Detection Rate",
        "type": "graph",
        "gridPos": {"x": 12, "y": 8, "w": 12, "h": 8},
        "targets": [
          {
            "expr": "rate(fraud_detected_total[5m])",
            "legendFormat": "Fraud detected"
          }
        ]
      },
      {
        "id": 5,
        "title": "Provider Fallback Count",
        "type": "graph",
        "gridPos": {"x": 0, "y": 16, "w": 12, "h": 8},
        "targets": [
          {
            "expr": "rate(provider_fallback_total[5m])",
            "legendFormat": "Fallbacks"
          }
        ]
      },
      {
        "id": 6,
        "title": "Pod Memory Usage",
        "type": "graph",
        "gridPos": {"x": 12, "y": 16, "w": 12, "h": 8},
        "targets": [
          {
            "expr": "container_memory_usage_bytes{pod=~\"payment-processor.*\"}",
            "legendFormat": "{{pod}}"
          }
        ]
      }
    ]
  }
}
```

---

## Logging Configuration

### application.properties additions

```properties
# JSON Logging
quarkus.log.console.json=true
quarkus.log.console.json.pretty-print=false

# Log Format with tracing
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p traceId=%X{traceId} spanId=%X{spanId} [%c{2.}] (%t) %s%e%n

# Category Levels
quarkus.log.category."org.apache.camel".level=INFO
quarkus.log.category."org.apache.kafka".level=WARN
quarkus.log.category."io.quarkus".level=INFO
quarkus.log.category."com.poc".level=DEBUG

# File logging (optional)
# quarkus.log.file.enable=true
# quarkus.log.file.path=/var/log/poc-camel/application.log
```

### Log Entry Example

```json
{
  "timestamp": "2026-05-01T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.poc.processor.PaymentProcessorRoute",
  "message": "Processing payment",
  "traceId": "abc123def456",
  "spanId": "span789",
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 99.99,
  "currency": "USD"
}
```

---

## Rake Tasks Additions

```ruby
namespace :k8s do
  desc "Build container images"
  task :build do
    run_cmd("podman build -t poc-camel/api-gateway:latest -f #{File.join(ROOT, 'docker', 'Dockerfile.api-gateway')} .")
    run_cmd("podman build -t poc-camel/payment-processor:latest -f #{File.join(ROOT, 'docker', 'Dockerfile.payment-processor')} .")
  end

  desc "Load images into Kind cluster"
  task :load do
    run_cmd("kind load docker-image poc-camel/api-gateway:latest --name poc-camel")
    run_cmd("kind load docker-image poc-camel/payment-processor:latest --name poc-camel")
  end

  desc "Show pod logs (usage: rake k8s:logs[payment-processor-xxxxx])"
  task :logs, [:pod] do |_, args|
    pod = args[:pod] || ENV["POD"]
    raise "Missing POD name. Usage: rake k8s:logs[pod-name]" unless pod
    run_cmd("kubectl logs #{pod} -n poc-camel -f")
  end

  desc "Port forward service (usage: rake k8s:port[service,8080])"
  task :port, [:service, :port] do |_, args|
    service = args[:service] || "api-gateway"
    port = args[:port] || 8080
    run_cmd("kubectl port-forward svc/#{service} #{port}:#{port} -n poc-camel")
  end

  desc "Scale deployment (usage: rake k8s:scale[payment-processor,5])"
  task :scale, [:deployment, :replicas] do |_, args|
    deployment = args[:deployment] || "payment-processor"
    replicas = args[:replicas] || 3
    run_cmd("kubectl scale deployment/#{deployment} --replicas=#{replicas} -n poc-camel")
  end

  desc "Restart deployment (usage: rake k8s:restart[payment-processor])"
  task :restart, [:deployment] do |_, args|
    deployment = args[:deployment] || "payment-processor"
    run_cmd("kubectl rollout restart deployment/#{deployment} -n poc-camel")
  end

  desc "Describe resource (usage: rake k8s:describe[deployment/payment-processor])"
  task :describe, [:resource] do |_, args|
    resource = args[:resource] || "deployment/payment-processor"
    run_cmd("kubectl describe #{resource} -n poc-camel")
  end

  desc "Show all resources"
  task :all do
    run_cmd("kubectl get all -n poc-camel")
    run_cmd("kubectl get configmaps,secrets -n poc-camel")
  end
end
```

---

## Data Flow

1. **Build**: `rake k8s:build` → creates Docker images
2. **Load**: `rake k8s:load` → loads images into Kind
3. **Deploy**: `rake k8s:deploy` → applies K8s manifests
4. **Access**: `rake k8s:port[api-gateway,8080]` → port forward
5. **Observe**:
   - Metrics: `curl http://localhost:9090` → Prometheus
   - Traces: `curl http://localhost:16686` → Jaeger
   - Dashboards: `curl http://localhost:3000` → Grafana
   - Logs: `kubectl logs -n poc-camel` → JSON structured logs

---

## Security Considerations

- Secrets: Base64 encoded (not encrypted at rest)
- Non-root containers: runAsNonRoot=true, runAsUser=1000
- No network policies (dev mode)
- No TLS termination at ingress (dev mode)
- ServiceAccount: default (read-only)
- Resource limits: enforced via requests/limits

## HPA Behavior

| Metric | Threshold | Action |
|--------|-----------|--------|
| CPU | 70% | Scale up (max 10 replicas) |
| Memory | 80% | Scale up (max 10 replicas) |
| CPU | <30% | Scale down (min 2 replicas) |
| Memory | <60% | Scale down (min 2 replicas) |

Scale-up: Immediate (0s stabilization window)
Scale-down: 5 minutes (300s stabilization window)