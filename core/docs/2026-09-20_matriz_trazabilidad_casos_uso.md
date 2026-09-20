# Matriz consolidada de trazabilidad de casos de uso

Fecha de corte: 2026-09-20. Fuente funcional: [lectura de Documentacion](fuentes/2026-09-13_lectura_Documentacion.txt). Estado técnico de partida: commit `f600be6`.

## Regla de numeración

La fuente contiene dos enumeraciones diferentes. El catálogo resumido no separa **Gestionar permisos** como caso independiente; las fichas detalladas sí lo hacen como CU05. Por ello, desde ese punto cada ficha detallada tiene un número mayor en una unidad respecto del catálogo. Esta matriz conserva ambos identificadores y no atribuye al docente una corrección todavía no confirmada.

En documentación nueva se escribirá primero el identificador del catálogo, utilizado por la planificación del proyecto, y entre paréntesis el identificador de la ficha detallada cuando difiera. Ejemplo: **CU21 (ficha CU22) — Modificar mediante texto**.

## Estados

- **Completo comprobado:** el flujo principal y sus controles materiales tienen pruebas automatizadas.
- **Avanzado parcial:** existe un recorrido utilizable, pero faltan obligaciones explícitas de la ficha.
- **Pendiente:** no existe todavía un recorrido funcional completo.

## Matriz

| Catálogo | Ficha | Caso de uso | Estado vigente | Evidencia y brecha principal |
|---|---:|---|---|---|
| CU01 | CU01 | Iniciar sesión | Completo comprobado | Sesión opaca, bloqueo, CSRF, activación e historial propio en `acceso` |
| CU02 | CU02 | Cerrar sesión | Completo comprobado | Revocación persistente, eliminación de cookie y sesiones históricas |
| CU03 | CU03 | Gestionar usuarios | Completo comprobado | Alta, consulta, actualización, activación, restablecimiento y revocación |
| CU04 | CU04 | Gestionar roles globales | Completo comprobado | Matriz de permisos, ámbitos, protección de roles del sistema y revisión optimista |
| Integrado en CU04 | CU05 | Gestionar permisos | Avanzado parcial | Asignación y revocación mediante roles; falta confirmación docente sobre si requiere pantalla independiente |
| CU05 | CU06 | Consultar proyectos asignados | Completo comprobado | Filtros, paginación, autorización y recuperación ante retiro de membresía |
| CU06 | CU07 | Gestionar proyecto | Avanzado parcial | Crear, actualizar, archivar y reactivar; el alcance de solo lectura sobre administración continúa documentado como interpretación |
| CU07 | CU08 | Gestionar miembros | Completo comprobado | Incorporación, consulta, suspensión, retiro y transferencia de propiedad |
| CU08 | CU09 | Asignar roles del proyecto | Completo comprobado | Roles y excepciones de permiso por membresía, sin escalamiento silencioso |
| CU09 | CU10 | Abrir sesión de modelado | Avanzado parcial | Carga, presencia y modo lector/editor; falta confirmar si debe mostrarse siempre un resumen de validación |
| CU10 | CU11 | Gestionar clases | Completo comprobado | Crear, actualizar, eliminar y versionar clases |
| CU11 | CU12 | Gestionar atributos y operaciones | Completo comprobado | Atributos, operaciones, parámetros, tipos, multiplicidad e identificadores |
| CU12 | CU13 | Gestionar relaciones y cardinalidades | Completo comprobado | Asociaciones, agregación, composición, generalización, dirección y cardinalidad |
| CU13 | CU14 | Validar el modelo UML | Completo comprobado | Informe persistente, procesamiento recuperable y reglas semánticas |
| CU14 | CU15 | Consultar actividad del proyecto | Completo comprobado | Filtros, paginación, detalle, valores y versión vinculada |
| CU15 | CU16 | Organizar visualmente | Avanzado parcial | Movimiento individual persistente; faltan tamaño, multiselección, alineación, distribución, viewport y autoorganización |
| CU16 | CU17 | Editar colaborativamente | Completo comprobado | Presencia, foco, cambios confirmados, reconexión y pérdida de permisos |
| CU17 | CU18 | Gestionar conflictos | Completo comprobado | Comparación, conservar, descartar, fusionar, idempotencia y trazabilidad |
| CU18 | CU19 | Consultar historial | Completo comprobado | Listado, filtros, detalle y comparación semántica de versiones |
| CU19 | CU20 | Restaurar versión | Completo comprobado | Previsualización, bloqueadores, restauración atómica como nueva versión y WebSocket |
| CU20 | CU21 | Configurar servicios de plataforma | Pendiente | Existen `servicio_plataforma` y `secreto_servicio`, pero no API ni interfaz administrativa |
| CU21 | CU22 | Modificar mediante texto | Avanzado parcial | Solicitud y propuesta seguras; el motor local solo crea clases, atributos y relaciones |
| CU22 | CU23 | Modificar mediante voz | Avanzado parcial | Web Speech y MediaRecorder; faltan reproducción, límites de duración y proveedor validado |
| CU23 | CU24 | Reconstruir desde imagen | Avanzado parcial | Carga segura, persistencia y adaptador; falta OCR/visión funcional validada y edición de dudosos |
| CU24 | CU25 | Revisar cambios interpretados | Avanzado parcial | Resumen, confianza, operaciones y dependencias; falta selección, corrección y revalidación |
| CU25 | CU26 | Aplicar o rechazar cambios | Completo comprobado para propuestas vigentes | Aplicación atómica, rechazo, obsolescencia, rollback y prevención de doble decisión |
| CU26 | CU27 | Importar desde Enterprise Architect | Pendiente | El permiso existe, pero no hay carga, análisis, previsualización ni aplicación XMI |
| CU27 | CU28 | Exportar para Enterprise Architect | Avanzado parcial | XMI 2.1 y descarga; faltan alcance por paquete/versión, advertencias y prueba con Enterprise Architect |
| CU28 | CU29 | Configurar generación del backend | Avanzado parcial | Configuración base y capas; faltan selección de clases, operaciones, dependencias, versiones y mapeos |
| CU29 | CU30 | Generar backend Spring Boot | Avanzado parcial | ZIP con entidades, repositorios, DTOs, controladores, OpenAPI y esquema; falta enlazarlo con validación automática |
| CU30 | CU31 | Validar backend generado | Pendiente | No existe compilación aislada, comparación con el modelo ni informe persistente |
| CU31 | CU32 | Descargar proyecto generado | Avanzado parcial | Descarga registrada; todavía no exige una validación aprobada ni incluye su informe |

## Evidencia técnica principal

- Acceso, administración, proyectos y modelado: paquetes `bo.edu.uml.acceso`, `bo.edu.uml.proyectos` y `bo.edu.uml.modelado`.
- Colaboración y conflictos: paquete `bo.edu.uml.colaboracion`.
- Historial y restauración: paquete `bo.edu.uml.historial` y [contrato del incremento 06](2026-09-15_incremento_06_contratos.md).
- XMI y generación: paquetes `bo.edu.uml.xmi` y `bo.edu.uml.generacion` y [seguimiento del incremento 08](2026-09-18_seguimiento_incremento_08.md).
- Asistencia inteligente: paquete `bo.edu.uml.ia`, [contrato P04](2026-09-19_p04_contrato_asistencia_inteligente.md) y [seguimiento del incremento 09](2026-09-19_seguimiento_incremento_09_p04.md).

## Orden de continuidad aprobado

1. Incremento 10: estabilización, permisos, trazabilidad y cobertura faltante.
2. Incremento 11: configuración de servicios de la plataforma.
3. Incremento 12: cierre funcional de asistencia inteligente.
4. Incremento 13: importación y exportación XMI completas.
5. Incremento 14: configuración avanzada de generación.
6. Incremento 15: validación y descarga controlada del backend.
7. Incremento 16: organización visual.
8. Incremento 17: auditoría, notificaciones y seguridad administrativa.
9. Incremento 18: preparación de entrega y operación.
