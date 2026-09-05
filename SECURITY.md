# Security Policy (PoC demo)
Secretos demo rotados a valores `poc-demo-*` no reales.
El historial git aun contiene valores viejos: no reescribir por decision.
No usar estos valores en produccion bajo ningun concepto.
Usar ExternalSecrets / SealedSecrets / Vault para inyeccion real.
Grafana demo usa `poc-admin` solo local; rotar antes de exponer.
Reportar exposicion accidental y rotar inmediatamente.
`.env` y `secrets.yaml` fuera del indice; `.gitignore` los cubre.
Imagenes `:latest` con `Always`; kafka-ui y openspec pineados.
Sin commit realizado; revisar `git diff` antes de publicar.
Grafana `poc-demo-pass-2026` es solo local (podman-compose + grafana.ini demo); ante exposicion rotar y usar `monitoring/grafana.env` (gitignored) o Vault; ver `grafana.env.example`.
K8s `secrets.yaml` es plantilla local; existe `secrets.yaml.example` con placeholders; `secrets.yaml` gitignored; prod via ExternalSecrets/SealedSecrets/Vault.
CORS restringido via `${CORS_ORIGINS:https://localhost:3000}`; swagger-ui off en %prod, on solo %dev/%test.
K8s base endurecido piloto: readOnlyRootFilesystem + /tmp emptyDir, drop ALL, no privesc, seccomp RuntimeDefault, runAsNonRoot 1000, IfNotPresent en base (Always solo prod overlay); NetworkPolicy deny-all + allow gateway->processor, ->kafka:29092, scrape prometheus.
