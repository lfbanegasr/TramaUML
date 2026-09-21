# Contrato del Incremento 15 — Validación y descarga controlada del backend

Fecha: 2026-09-21

Caso principal: CU29 (ficha CU30) — Validación y descarga del backend generado

## Decisiones del contrato

1. La validación del backend generado se ejecuta en un proceso aislado y offline, sin arrancar la aplicación Spring Boot ni conectar a bases de datos ni servicios de red.
2. Toda validación comprueba la integridad criptográfica del ZIP contra la huella SHA-256 persistida en `archivo_almacenado`. Si la huella no coincide, la validación se marca inmediatamente como `INVALIDO` por seguridad.
3. Se analizan la estructura del archivo ZIP, el archivo `pom.xml`, los archivos de configuración (`application.properties`) y la correspondencia entre las clases/atributos de la instantánea de la versión UML y el código fuente Java generado.
4. La compilación Java utiliza Maven 3.9.9 en modo offline (`-o`, `-B`, `-ntp`), con configuración de compilación acotada (memoria máxima 384 MB, límite de tiempo estricto, sin procesadores de anotaciones ni bifurcación del compilador) y con repositorio local en `.qa/maven-repository`.
5. Los resultados se registran en `validacion_backend` y `hallazgo_validacion_backend`. Se conservan el informe detallado (`informe.json`) y el log de compilación (`compilacion.log`) en `archivo_almacenado`.
6. La descarga del artefacto (`GET /api/modelos/{modelo}/generacion/{id}/descargar`) queda estrictamente condicionada a que la última validación del backend haya concluido en estado `VALIDO` o `VALIDO_CON_ADVERTENCIAS`. Ante cualquier otro estado o si el artefacto fue alterado tras validar, se responde `409 Conflict`.
7. Toda descarga efectiva se registra en `uml_colaborativo.descarga_artefacto` para trazabilidad y auditoría.
8. La solicitud de validación es asíncrona: `POST /api/modelos/{modelo}/generacion/{id}/validaciones` responde `202 Accepted` y encola el trabajo para su ejecución desacoplada mediante un worker en segundo plano, evitando retener conexiones JDBC durante la compilación.
9. Se impide la duplicación de validaciones simultáneas para la misma generación mediante control transaccional e índice único parcial en base de datos.
10. La autorización se basa en el permiso `VALIDAR_BACKEND` (asignado a `PROPIETARIO_PROYECTO` y `LIDER`), mientras que la consulta del informe y la descarga requieren `VER_MODELO` y `DESCARGAR_ARTEFACTO` respectivamente.

## API

### Listar generaciones disponibles

`GET /api/modelos/{modelo}/generacion`

Devuelve la lista de generaciones del modelo con su configuración, versión UML, fecha de creación, estado de la última validación y bandera `puedeValidar`.

### Solicitar validación

`POST /api/modelos/{modelo}/generacion/{id}/validaciones`

Responde con `202 Accepted` y el objeto `Informe` inicial (estado `EN_EJECUCION`). Si ya existe una validación en curso para esa generación, devuelve la misma en lugar de crear otra duplicada.

### Consultar informe de validación

`GET /api/modelos/{modelo}/generacion/{id}/validacion`

Devuelve el estado de la validación (`EN_EJECUCION`, `VALIDO`, `VALIDO_CON_ADVERTENCIAS`, `INVALIDO`, `INCOMPLETO`, `FALLIDO`), cantidades de comprobaciones aprobadas, advertencias y errores, registro de compilación, lista de hallazgos estructurados y la bandera `puedeDescargar`.

### Descargar artefacto validado

`GET /api/modelos/{modelo}/generacion/{id}/descargar`

- Si la validación no está aprobada o la huella no coincide: `409 Conflict`.
- Si el usuario no tiene permiso `DESCARGAR_ARTEFACTO`: `403 Forbidden`.
- Si está aprobada: `200 OK` con `application/zip`, encabezado `Content-Disposition` con el nombre de archivo original y registro de auditoría en `descarga_artefacto`.

## Invariantes

- El artefacto ZIP no puede contener rutas que escapen de la raíz del proyecto ni archivos ejecutables ajenos a la estructura estándar de un proyecto Spring Boot.
- El `pom.xml` solo puede contener dependencias y plugins autorizados y registrados en la configuración de generación.
- Las clases persistentes seleccionadas en la configuración deben existir como entidades en el código Java y conservar los tipos de atributos configurados.
- Las operaciones UML generan firmas en los servicios que lanzan `UnsupportedOperationException`, lo cual se reporta como advertencia para guiar la implementación manual posterior.
- Las peticiones mutables exigen autenticación válida, cookie de sesión y token CSRF.
