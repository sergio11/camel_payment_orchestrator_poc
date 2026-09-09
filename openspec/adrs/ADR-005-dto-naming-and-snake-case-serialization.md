# ADR-005: Convención de Nomenclatura DTO y Serialización JSON snake_case

## Estado
Aceptado

## Contexto
Los objetos de transferencia de datos (DTOs) en el módulo `shared` no presentaban una nomenclatura homogénea con sufijo `DTO` y sus campos se serializaban por defecto en `camelCase`, apartándose del estándar habitual en APIs REST empresariales y pasarelas de pago (`snake_case`).

## Decisión
1. Estandarizar todos los DTOs con el sufijo `DTO`: `PaymentRequestDTO`, `PaymentResponseDTO`, `PaymentStatusResponseDTO`, `PaymentPageResponseDTO`, `ErrorResponseDTO`.
2. Anotar explícitamente cada campo con `@JsonProperty("...")` indicando su nombre canónico en formato `snake_case` (e.g. `customer_id`, `payment_method`, `failure_reason`).
3. Mantener clases alias para preservar la compatibilidad hacia atrás en componentes que aún requieran los nombres originales.

## Consecuencias
- **Positivas**:
  - Contrato de API REST homogéneo, estándar y legible en formato `snake_case`.
  - Claridad semántica distinguiendo inequívocamente modelos de transferencia (DTOs) de modelos de dominio.
- **Negativas**:
  - Los clientes HTTP deben enviar y recibir atributos en formato `snake_case`.
