# ADR-006: Modelado Relacional 1:1 para Metadatos de Pago

## Estado
Aceptado

## Contexto
En el modelo relacional previo, los metadatos de pago se almacenaban como una cadena desnormalizada `metadataJson TEXT` en la tabla `payments`. En el procesador de fraude (`FraudEvaluationProcessor`), se accedía a claves arbitrarias en mapas sin control de tipos ni validación de existencia, exponiendo al sistema a errores en tiempo de ejecución.

## Decisión
1. Sustituir la columna desnormalizada `metadataJson` por una relación relacional 1:1 entre `PaymentEntity` y una nueva entidad `PaymentMetadataEntity` vinculada a la tabla `payment_metadata`.
2. Crear la migración SQL `V3__payment_metadata.sql` con clave primaria foránea a `payments(id)` y columnas estructuradas para los campos evaluados por fraude (`order_id`, `attempts`, `is_new_payment_method`, `payment_method_age_days`, `customer_risk_tier`).
3. Modelar en dominio el record `PaymentMetadata` con acceso tipado seguro.
4. Robustecer `FraudEvaluationProcessor` con métodos de acceso seguro con valores por defecto.

## Consecuencias
- **Positivas**:
  - Estructura relacional normalizada y tipada en base de datos.
  - Eliminación de la dependencia de `ObjectMapper` para la persistencia de pagos.
  - Acceso seguro a campos de evaluación de fraude sin riesgo de `NullPointerException` o `ClassCastException`.
- **Negativas**:
  - Inserción y consulta de una tabla adicional (`payment_metadata`) para operaciones de pago con metadatos.
