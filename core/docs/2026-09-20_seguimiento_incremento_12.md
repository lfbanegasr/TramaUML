# Seguimiento consolidado — Incremento 12

Fecha de cierre: 2026-09-20

Rama de trabajo: `codex/incremento-12-cierre-asistencia-inteligente`

Versión consolidada: backend `0.12.0-SNAPSHOT`, frontend `0.12.0`

Casos cubiertos: **CU21 a CU25 del catálogo (fichas detalladas CU22 a CU26) — Asistencia Inteligente**.

## Resultado

Se cerró el recorrido texto/voz/imagen → propuesta → revisión editable → aplicación o rechazo. La IA permanece separada del modelo: solo la decisión explícita de un actor autorizado llega al motor transaccional de edición.

El contrato detallado se encuentra en [2026-09-20_incremento_12_contrato_cierre_asistencia.md](2026-09-20_incremento_12_contrato_cierre_asistencia.md) y la matriz consolidada fue actualizada con el estado comprobado.

## Backend

- El motor semántico local ahora usa el contexto persistido y propone creación, actualización y eliminación sobre clases, atributos, operaciones y relaciones.
- Las actualizaciones y eliminaciones incluyen `valor_actual`; las referencias dudosas quedan inválidas y no seleccionadas.
- Se incorporó revisión optimista editable de todas las operaciones mediante `PUT /api/modelos/{modelo}/ia/propuestas/{id}`.
- La revalidación comprueba estructura, campos, tipos, multiplicidades, parámetros, referencias del modelo y dependencias.
- La aplicación rechaza cualquier operación seleccionada inválida y ya no puede omitir silenciosamente un dependiente inválido.
- La aplicación definitiva continúa siendo atómica, con control de revisión, rollback, versión única y evento WebSocket poscommit.
- Voz valida tamaño, MIME, firma y duración declarada. Imagen mantiene validación de formato, firma y dimensiones.
- La ausencia de transcriptor u OCR/multimodal produce un fallo persistido y explícito.

## Frontend

- El diálogo de revisión permite seleccionar operaciones, ver el diff, corregir JSON y revalidar antes de aplicar.
- La aplicación queda deshabilitada cuando hay cambios locales sin guardar, una propuesta obsoleta o errores seleccionados.
- Los lectores autorizados pueden consultar una propuesta pendiente sin controles de decisión.
- MediaRecorder muestra el tiempo, se detiene a los 120 segundos y permite escuchar la grabación antes de enviarla.
- La vista previa de imagen y la corrección del texto detectado se conservan.

## Persistencia, permisos y versión

- No se modificó el esquema original ni se añadió JPA.
- La migración `2026-09-20_07_revision_asistencia_lectura.sql` es idempotente y habilita revisión de solo lectura para `LECTOR_PROYECTO`.
- Los arneses aislados aplican la nueva migración.
- Backend, frontend, metadatos XMI, metadatos de generación y lanzadores quedaron alineados en `0.12.0`.

## Verificación final

Todas las pruebas con persistencia usaron bases ficticias nuevas en PostgreSQL `127.0.0.1:55433`. No se conectó ni modificó el PostgreSQL del puerto `55432`.

| Control | Resultado |
|---|---:|
| Google Java Format AOSP | Aprobado |
| Pruebas Maven del núcleo | 108 aprobadas, 0 fallos, 0 errores, 0 omitidas |
| Prettier `format:check` | Aprobado |
| Compilación TypeScript/Vite | Aprobada |
| Pruebas Playwright/Chrome | 23 aprobadas |
| Empaquetado Spring Boot | Aprobado |

La cobertura nueva verifica interpretación contextual, valores actual/propuesto, corrección y revisión optimista, dependencias rotas, bloqueo de aplicación inválida, permisos, duración de audio y un recorrido de navegador que corrige una clase antes de aplicar la propuesta. La regresión conserva CSRF, aislamiento entre modelos, propuesta obsoleta, rollback y prevención de doble decisión.

## Límites declarados

- El motor local usa una gramática española explícita y determinista; no pretende resolver cualquier frase ambigua.
- Las pruebas no consumen credenciales ni servicios externos reales. Validan el adaptador, la configuración segura, los caminos locales/manuales y el fallo cerrado; la disponibilidad productiva depende del proveedor configurado.
- La edición avanzada expone JSON estructurado. Formularios específicos por tipo de operación serían una mejora de experiencia, no una brecha del contrato transaccional.
- La duración recibida por el backend proviene del navegador; tamaño, MIME y firma sí se validan sobre el binario.

## Próximo avance del plan

El siguiente incremento es el **Incremento 13: importación y exportación XMI completas**, empezando por el análisis seguro de XMI de Enterprise Architect, su previsualización y aplicación transaccional.
