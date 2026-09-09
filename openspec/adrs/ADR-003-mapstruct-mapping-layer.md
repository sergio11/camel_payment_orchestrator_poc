# ADR-003: Adopción de MapStruct para el Mapeo de DTOs y Entidades

## Estado
Aceptado

## Contexto
Los mapeos entre DTOs (`PaymentRequest`, `PaymentResponse`) y entidades se realizaban mediante métodos estáticos manuales en `PaymentMapper`, lo que generaba código repetitivo propenso a errores humanos ante cambios en los esquemas de datos.

## Decisión
1. Incorporar MapStruct (`org.mapstruct:mapstruct` y `mapstruct-processor`) en el proyecto Maven.
2. Definir `PaymentMapper` como una interfaz anotada con `@Mapper(componentModel = "cdi")`.
3. Proporcionar soporte tanto para inyección CDI como para métodos delegados estáticos para facilitar la interoperabilidad con código existente.

## Consecuencias
- **Positivas**:
  - Generación de código de mapeo en tiempo de compilación, de alto rendimiento y tipado seguro.
  - Reducción de código *boilerplate*.
  - Detección temprana en compilación de campos no mapeados o incompatibles.
- **Negativas**:
  - Incorporación de un procesador de anotaciones adicional en el ciclo de construcción de Maven.
