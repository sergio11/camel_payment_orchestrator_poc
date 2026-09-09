# ADR-002: Manejo Desacoplado de Excepciones bajo el Principio de Responsabilidad Única (SOLID)

## Estado
Aceptado

## Contexto
La clase `GlobalExceptionMapper` centralizaba la captura de todas las excepciones del sistema mediante un único método con múltiples *early returns* anidados y análisis de nombres de clases por reflexión/cadenas (`cls.contains("JsonProcessingException")`). Esto violaba el principio de responsabilidad única (SRP), era susceptible a errores en tiempo de ejecución y dificultaba el mantenimiento.

## Decisión
1. Descomponer el manejo de excepciones en mappers especializados por tipo (`@Provider` con `ExceptionMapper<T>`):
   - `PaymentNotFoundExceptionMapper` para errores 404 (recursos no encontrados).
   - `ConstraintViolationExceptionMapper` para errores 400 (fallos de Bean Validation).
   - `IllegalArgumentExceptionMapper` para errores 400 (argumentos inválidos).
   - `PersistenceConflictExceptionMapper` para errores 409 (conflictos de unicidad e idempotencia).
2. Simplificar `GlobalExceptionMapper` para actuar únicamente como red de seguridad global para excepciones genéricas e inesperadas (500 Internal Server Error), eliminando la inspección de cadenas.

## Consecuencias
- **Positivas**:
  - Cumplimiento de SRP y Open/Closed Principle (SOLID).
  - Eliminación de fragilidad debida a heurísticas basadas en nombres de clases por reflexión.
  - Mayor facilidad para testear unitariamente cada mapper de excepción.
- **Negativas**:
  - Aumento en el número de clases dedicadas a excepciones.
