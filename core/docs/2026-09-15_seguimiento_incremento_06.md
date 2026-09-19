# Seguimiento del incremento 06

Fecha: 2026-09-15. Estado: incremento completado y verificado. Versión: backend 0.6.0-SNAPSHOT y frontend 0.6.0.

## Resultado implementado

- **CU19 — Consultar historial del modelo:**
  - Consulta paginada descendente por cursor de versiones históricas (`numero_version`).
  - Filtros opcionales por autor (`idAutor`) y rango de fechas (`desde`/`hasta`).
  - Inspección del detalle de versión con instantánea JSONB validada (`formato_version: 2`).
  - Comparación de versiones en memoria (`ComparadorVersiones`) identificando elementos AGREGADOS, ELIMINADOS y MODIFICADOS con diferencias a nivel de propiedad.
  - Ocultamiento de modelos ajenos mediante 404 y verificación de permisos `VER_HISTORIAL_MODELO`.

- **CU20 — Restaurar versión del modelo:**
  - Previsualización no destructiva con detección de bloqueadores: sesiones colaborativas activas en modo EDITOR o conflictos de edición ABIERTOS impiden la restauración (409).
  - Protocolo transaccional completo en `RestauradorTransaccional`: bloqueo de revisión, verificación de estado de proyecto/modelo, lectura de snapshot origen, llamada a `iniciar_edicion_modelo`, reconstrucción en orden topológico seguro y validación semántica mediante `MotorSemantico`.
  - Creación de nueva versión con puntero `id_version_restaurada` hacia la versión origen, registro de `cambio_modelo` (tipo `RESTAURAR`) y auditoría en `actividad_proyecto`.
  - Difusión de evento `ModeloConfirmado` `AFTER_COMMIT` para que los navegadores conectados actualicen la revisión de forma consistente.
  - Idempotencia garantizada por UUID (`idSolicitudRestauracion`).

- **Base de datos y permisos:**
  - Migración [2026-09-15_03_historial_permisos.sql](migraciones/2026-09-15_03_historial_permisos.sql) con permisos `VER_HISTORIAL_MODELO` (asignado a `COLABORADOR` y `LIDER`) y `RESTAURAR_MODELO` (asignado a `LIDER`).
  - Función de base de datos `guardar_version_modelo_restaurada`.

- **Frontend (React 19 + TypeScript):**
  - Módulo `historial` estructurado con `historialApi.ts`, `HistorialPage.tsx`, `ComparacionPanel.tsx` y `RestauracionDialog.tsx`.
  - Integración fluida en `EditorPage.tsx` mediante botón "Historial" con recarga automática tras restauración exitosa.
  - Manejo de reintentos idempotentes con almacenamiento temporal en `sessionStorage`.

## Pruebas y verificación

- **Compilación de backend:** 70 archivos fuente compilados exitosamente en Java 21 con Maven 3.9.9 (`plataforma-0.6.0-SNAPSHOT.jar`).
- **Compilación y empaquetado frontend:** TypeScript y Vite build exitosos (0 errores). Formato de código verificado con Prettier (100% conforme).
- **Pruebas unitarias e integración de backend:** 67 de 67 pruebas exitosas (0 fallos, 0 errores) en `2026-09-14_probar_nucleo.ps1`:
  - `NucleoIntegracionTest`: 54 pruebas pasadas (incluye restauración completa, equivalencia de snapshot, idempotencia pre y post bloqueo, aislamiento por modelo, previsualización, permisos separados y bloqueo concurrente 409 por sesiones editoras activas).
  - `ValidadorSnapshotTest`: 5 pruebas pasadas (formato 2, cabecera de modelo, integridad referencial de 8 grupos, integridad SHA-256 canónico y snapshots malformados).
  - `ComparadorVersionesTest`: 5 pruebas pasadas (diferencias semánticas AGREGADO/ELIMINADO/MODIFICADO, diferencias visuales y ordenación determinista).
  - `MotorSemanticoTest`: 3 pruebas pasadas.
- **Pruebas automatizadas Playwright:** 17 de 17 pruebas exitosas en `2026-09-14_probar_navegador.ps1`:
  - `2026-09-15_historial.spec.ts`: consulta de historial, comparación de diferencias y restauración transaccional de versiones pasadas al 100%.
  - Suites de regresión previas (administración, colaboración, núcleo, proyectos/actividad): 15 de 15 pruebas pasadas sin regresiones.
- **Scripts de prueba actualizados:** `2026-09-14_probar_nucleo.ps1`, `2026-09-14_probar_navegador.ps1` y `2026-09-14_ejecutar_backend.ps1` sincronizados con versión 0.6.0.
