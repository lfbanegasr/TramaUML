# Contrato del Incremento 14 — Configuración avanzada de generación

Fecha: 2026-09-21

Caso principal: CU28 (ficha CU29) — Configurar generación del backend

## Decisiones del contrato

1. Toda configuración referencia una `version_modelo_uml` y se alimenta exclusivamente de su `instantanea`; nunca lee el modelo vivo durante la generación.
2. Guardar una configuración no cambia `estado_validacion` de la versión. La generación solo se habilita para `VALIDO` o `VALIDO_CON_ADVERTENCIAS`.
3. Cada guardado crea una configuración nueva. Una configuración que ya produjo una generación permanece inmutable y reproducible.
4. Las clases seleccionadas se almacenan en `clase_configuracion_backend`; los mapeos de tipos y persistencia, en sus tablas específicas; y las dependencias, en `dependencia_backend`.
5. La selección de operaciones se conserva dentro de `plantilla_conexion` como JSON estructurado porque el esquema no define una tabla de selección de operaciones. No se inventa una columna.
6. La configuración puede apuntar deliberadamente a una versión histórica. La respuesta la identifica como histórica, pero no la considera obsoleta: genera desde la instantánea elegida.
7. La compilación aislada, el informe de `validacion_backend` y la descarga condicionada pertenecen al Incremento 15.

## API

### Consultar

`GET /api/modelos/{modelo}/generacion/configuracion?version={uuid}`

`version` es opcional; sin ella se usa la versión vigente. La respuesta incluye:

- metadatos y estado de validación de la versión;
- versiones disponibles;
- configuración Maven, Java, Spring Boot y migraciones;
- capas seleccionadas;
- clases, operaciones, nombres de entidad/tabla y exposición REST;
- mapeos UML → Java → PostgreSQL;
- dependencias obligatorias y adicionales;
- mensajes de error, advertencia o información;
- `puedeGenerar`, calculado por el servidor.

### Guardar

`PUT /api/modelos/{modelo}/generacion/configuracion`

El cuerpo contiene la versión elegida y todos los elementos configurables. Si las listas avanzadas se omiten, el servidor selecciona las clases de la instantánea y aplica defaults seguros para mantener compatibilidad con clientes anteriores.

### Generar

`POST /api/modelos/{modelo}/generacion/generar`

```json
{ "idConfiguracion": "uuid" }
```

Si se omite el cuerpo se utiliza la última configuración válida de la versión actual. No se crea ni se valida una configuración de manera implícita.

## Invariantes

- La versión y cada UUID seleccionado deben pertenecer al modelo indicado.
- Debe existir al menos una clase seleccionada.
- Servicios y controladores CRUD requieren repositorios; REST/OpenAPI requiere controladores.
- Los nombres de tabla y entidad deben ser identificadores seguros.
- Las relaciones salientes incorporan su clase destino para evitar referencias rotas.
- Las dependencias exigidas por las capas, PostgreSQL y la herramienta de migración no pueden eliminarse mediante el cuerpo del cliente.
- La descarga busca la generación dentro del modelo solicitado y no admite UUID de otro modelo.
- Las peticiones mutables conservan sesión opaca, CSRF de doble envío y permisos `CONFIGURAR_BACKEND`/`GENERAR_BACKEND`.

## Salida del motor

El motor respeta las capas y elementos seleccionados, versiones Java/Spring, dependencias Maven, mapeos de tipos, nombres de tabla/entidad, exposición REST y herramienta de migración. Produce Flyway, Liquibase o `schema.sql`, y genera servicios con las firmas UML seleccionadas como puntos explícitos de implementación.
