# ADR-001: Separación de Modelos de Dominio y Entidades de Persistencia JPA

## Estado
Aceptado

## Contexto
En la implementación inicial del backend (`api-gateway`), el record inmutable `Payment` residía dentro del paquete `com.poc.gateway.entity`, mezclando conceptos de dominio con entidades JPA como `PaymentEntity` y `OutboxEventEntity`.
Esto violaba los principios de arquitectura limpia y diseño guiado por el dominio (DDD), acoplando el modelo de negocio a detalles de infraestructura de persistencia.

## Decisión
1. Trasladar el record inmutable `Payment` a la capa de dominio en el paquete `com.poc.gateway.domain`.
2. Mantener en el paquete `com.poc.gateway.entity` exclusivamente las entidades de persistencia JPA (`PaymentEntity`, `PaymentMetadataEntity`, `OutboxEventEntity`).
3. Mantener temporalmente en `com.poc.gateway.entity.Payment` un adaptador marcado como `@Deprecated` para compatibilidad binaria durante la transición.

## Consecuencias
- **Positivas**:
  - Modelo de dominio desacoplado de dependencias del framework ORM y de la base de datos.
  - Mayor facilidad para testear reglas de negocio de forma aislada.
  - Claridad estructural alineada con DDD y Clean Architecture.
- **Negativas**:
  - Requiere una capa de mapeo explícita entre dominio y persistencia (`PaymentPersistenceMapper`).
