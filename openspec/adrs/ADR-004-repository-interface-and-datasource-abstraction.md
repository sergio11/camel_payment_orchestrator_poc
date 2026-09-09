# ADR-004: Abstracción de Repositorios mediante Interfaces y Desacoplamiento de Estructuras en Memoria

## Estado
Aceptado

## Contexto
`PaymentRepository` carecía de una interfaz de abstracción, impidiendo la aplicación del principio de inversión de dependencias (IoC / DIP) y el principio de sustitución de Liskov (LSP).
Además, mantenía estructuras de almacenamiento en memoria concurrentes (`ConcurrentHashMap`) mezcladas con lógica JPA, dependía directamente de `ObjectMapper` para serializar metadatos y realizaba comprobaciones de tipo `em == null`.
Dado que Quarkus soporta DataSource dual mediante perfiles (H2 en dev/test y PostgreSQL en prod), las estructuras en memoria resultaban innecesarias y generaban deuda técnica.

## Decisión
1. Definir una interfaz `PaymentRepository` en la capa de puertos/repositorios.
2. Implementar `PaymentRepositoryJpa` como proveedor `@ApplicationScoped`, encapsulando `EntityManager`.
3. Eliminar los diccionarios `ConcurrentHashMap` del repositorio: toda la persistencia se delega en la base de datos configurada por perfil (H2 o PostgreSQL).
4. Extraer la lógica de transformación entre dominio y persistencia a `PaymentPersistenceMapper`, eliminando la dependencia de `ObjectMapper` del repositorio.

## Consecuencias
- **Positivas**:
  - Inversión de dependencias (IoC) y cumplimiento estricto de LSP.
  - Comportamiento consistente y reproducible entre pruebas locales y entornos reales apoyándose en H2/PostgreSQL.
  - Repositorio limpio y enfocado exclusivamente en operaciones de persistencia.
- **Negativas**:
  - Requiere un DataSource activo (H2 o PostgreSQL) para la ejecución de la capa de persistencia.
