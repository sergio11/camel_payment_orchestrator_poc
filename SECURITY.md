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
