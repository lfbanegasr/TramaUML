# Seguimiento del Incremento 15 — Validación y descarga controlada del backend

Fecha de cierre técnico: 2026-09-21
Rama: `codex/incremento-15-validacion-backend`
Versión: backend `0.14.0-SNAPSHOT`, frontend `0.14.0`

## Resultado

Se implementó el ciclo completo de validación aislada y descarga controlada del backend generado (CU29 / ficha CU30). El sistema compila de forma offline y hermética el código fuente Java generado dentro de un sandbox seguro, verifica la estructura del archivo ZIP, comprueba la integridad del POM y de las entidades respecto a la versión UML congelada, y condiciona la descarga del artefacto a que la validación haya resultado aprobada.

## Backend

- **CompiladorBackendAislado**:
  - Extracción y análisis estricto del archivo ZIP (límite de tamaño, prevención de path traversal y de archivos no permitidos).
  - Validación del archivo `pom.xml` contra el perfil Spring Boot y las dependencias declaradas.
  - Verificación de correspondencia entre clases/atributos de la instantánea UML y el código fuente generado.
  - Compilación Java aislada mediante Maven 3.9.9 (`-o`, `-B`, `-ntp`) con classpath y repositorio local acotado a `.qa/maven-repository`, sin procesadores de anotaciones ni conexión a red ni a bases de datos.
  - Generación de informe estructurado (`Informe`, `Hallazgo`, `Resultado`) y registro detallado de compilación.
- **ValidacionBackendRepositorio**:
  - Consultas y persistencia en `uml_colaborativo.validacion_backend`, `hallazgo_validacion_backend` y `descarga_artefacto`.
  - Almacenamiento de `informe.json` y `compilacion.log` en `uml_colaborativo.archivo_almacenado`.
  - Mecanismo de reserva de trabajos con bloqueo pesimista y marca de toma de trabajo para evitar condiciones de carrera.
  - Caducidad automática de trabajos huérfanos o con tiempo excedido (`expirar()`).
- **ValidacionBackendServicio** y **ProcesarValidacionBackend**:
  - Control de permisos con roles reales (`VALIDAR_BACKEND`, `VER_MODELO`, `DESCARGAR_ARTEFACTO`).
  - Ejecución de la compilación desacoplada fuera de transacciones JDBC para no retener conexiones del pool.
  - Verificación de huella SHA-256 antes de permitir la descarga (`descargar()`), devolviendo `409 Conflict` si la validación no está aprobada o si el artefacto fue modificado.
- **WorkerValidacionBackend**:
  - Tarea programada (`@Scheduled`) y pool dedicado de un hilo para procesar trabajos de validación en segundo plano.
- **Migración `2026-09-21_09_validacion_backend.sql`**:
  - Registro del permiso `VALIDAR_BACKEND` y asignación a roles `PROPIETARIO_PROYECTO` y `LIDER`.
  - Creación de índice único condicional `ux_validacion_backend_en_ejecucion` para garantizar una única ejecución activa por generación.

## Frontend

- **ValidacionBackendPanel**:
  - Componente integrado en `GenerarBackendDialog` para visualizar el estado de validación de cada generación disponible.
  - Sondeo asíncrono con temporizador mientras la validación está en curso (`EN_EJECUCION`).
  - Presentación de métricas (aprobadas, advertencias, errores), lista de hallazgos y visor expandible del registro de compilación (`compilacion.log`).
  - Botón "Validar backend" / "Volver a validar backend" habilitado según permisos.
  - Botón "Descargar ZIP validado" deshabilitado hasta que la validación esté aprobada (`VALIDO` o `VALIDO_CON_ADVERTENCIAS`).
- **generacionApi.ts**:
  - Tipos e interfaces `InformeBackend`, `GeneracionDisponible` y métodos `listarGeneraciones`, `validarBackend`, `leerInformeBackend`.
  - Manejo de respuestas de error JSON estructuradas en la descarga.

## Pruebas y verificación

- **Pruebas unitarias**:
  - `CompiladorBackendAisladoTest`: verificación de rechazo de rutas con escape/extensiones no permitidas, y ejecución de procesos en entorno limpio con timeout (2 pruebas aprobadas).
- **Pruebas de integración del núcleo**:
  - `NucleoIntegracionTest`: verificación completa de permisos, CSRF, integridad de artefactos en BD, revalidación, bloqueo de descarga ante código roto o validación fallida, y auditoría en `descarga_artefacto` (122 pruebas aprobadas, 0 fallas, en base aislada `55433`).
- **Pruebas de extremo a extremo (E2E)**:
  - Playwright: 24 de 24 pruebas aprobadas en navegador Chromium, incluyendo el flujo completo de configuración, generación, validación y descarga de backend validado.
- **Estilo y formato**:
  - Frontend: `prettier --check` aprobado.
  - Frontend build: `tsc -b && vite build` aprobado.
  - Backend: compilación y empaquetado Maven aprobados sin advertencias ni errores.
