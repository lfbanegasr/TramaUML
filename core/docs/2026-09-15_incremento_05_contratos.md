# Incremento 05 — gestión de conflictos de edición

Fecha: 2026-09-15. Backend de esta plataforma: 0.5.0-SNAPSHOT; frontend: 0.5.0. Este incremento continúa el núcleo 0.4 y mantiene JDBC, sesiones opacas, CSRF, UUID idempotentes y el protocolo transaccional de edición UML.

## Fuentes y alcance

Se usaron el [seguimiento 04](2026-09-14_seguimiento_incremento_04.md), los [contratos 04](2026-09-14_incremento_04_contratos.md), la [guía de persistencia](../../docs/2026-09-13_guia_persistencia_corregida.md), el [diccionario vigente](../../docs/2026-09-13_diccionario_base_datos_vigente.md) y las especificaciones detalladas extraídas de `Documentacion.docx` en [fuentes/2026-09-13_lectura_Documentacion.txt](fuentes/2026-09-13_lectura_Documentacion.txt).

Existe una contradicción no resuelta en la fuente: el catálogo resumido llama CU17 a conflictos, CU18 a historial y CU19 a restauración, mientras que las fichas detalladas los llaman **CU18 — Gestionar conflictos de edición**, **CU19 — Consultar historial del modelo** y **CU20 — Restaurar versión del modelo**. Se usa siempre la numeración detallada solicitada para este incremento, acompañada del nombre.

El alcance funcional terminado es CU18. CU19 y CU20 no se implementan todavía; su base técnica queda documentada por separado. IA, XMI, generación y reorganización visual no forman parte de este incremento. Flutter continúa aplazado por indicación del usuario y conserva su requisito histórico de funcionamiento local sin Internet.

## Detección y conservación

Una escritura conserva el contrato `idSesionEdicion + idCambioCliente + revisionBase + acciones`. Si `iniciar_edicion_modelo` detecta SQLSTATE `40001`, la transacción de edición se revierte por completo. El servicio intenta primero reconocer un reenvío ya confirmado. Si no existe, abre una transacción nueva y vuelve a comprobar identidad, permiso `EDITAR_MODELO`, proyecto/modelo activos y participación `EDITOR/EN_LINEA`.

Esa segunda transacción persiste:

- un `cambio_modelo` con estado `CONFLICTO`, revisión base, actor, sesión y parche entrante;
- un `conflicto_edicion` `ABIERTO` que relaciona el cambio confirmado causal cuando puede identificarse, el cambio entrante y los valores confirmado/propuesto observados;
- un aviso `CONFLICTO_ACTUALIZADO` únicamente después del commit.

El UUID del cambio conserva la idempotencia. Repetir exactamente la misma solicitud devuelve el mismo conflicto; reutilizarlo con otro actor, sesión, revisión o parche devuelve 409. El índice parcial impide más de un conflicto abierto para el mismo cambio entrante.

## Consulta y autorización

Las lecturas requieren sesión autenticada y `VER_MODELO`; un modelo ajeno se oculta con 404. La respuesta incluye `puedeResolver`, calculado en el servidor con el estado actual. Resolver o descartar requiere además `EDITAR_MODELO`, `RESOLVER_CONFLICTO`, proyecto activo, modelo no archivado y una participación colaborativa editable y conectada.

| Método y ruta                                                    | Contrato                                                                                                                                                     |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| GET `/api/modelos/{modelo}/conflictos?estado=ABIERTO`            | Lista conflictos y comparación recalculada contra la revisión vigente. `estado` admite `ABIERTO`, `RESUELTO`, `DESCARTADO` o vacío.                          |
| GET `/api/modelos/{modelo}/conflictos/{conflicto}`               | Devuelve elemento, propiedades diferentes, valores base/confirmado/propuesto, autores, fechas, revisiones, estado, posibilidad de fusión y permiso efectivo. |
| POST `/api/modelos/{modelo}/conflictos/{conflicto}/resoluciones` | Cuerpo con sesión, UUID de resolución, revisión esperada, confirmación y tipo `CONSERVAR_ALMACENADO`, `APLICAR_ENTRANTE` o `FUSIONAR`.                       |
| POST `/api/modelos/{modelo}/conflictos/{conflicto}/descartes`    | Cuerpo con sesión, UUID de resolución, revisión esperada y confirmación. Marca la propuesta como descartada sin modificar el modelo.                         |

Las respuestas de resolución incluyen el conflicto final, revisión/versión nuevas cuando hubo una escritura y `repetido` para reconocer un reenvío idempotente. Los errores usan el contrato estructurado general del backend.

## Decisiones de resolución

- **Conservar almacenado:** cierra el conflicto sin crear una revisión, porque el estado confirmado no cambia.
- **Aplicar entrante:** reaplica la propuesta sobre el estado actual. Si el elemento fue eliminado, solo lo restaura cuando sus padres o extremos siguen activos.
- **Fusionar:** solo se habilita para propiedades disjuntas. Si ambos cambios tocaron la misma propiedad con valores diferentes, hubo creación/eliminación incompatible o la situación es ambigua, responde 422 y mantiene el conflicto abierto. No existe fusión automática genérica.
- **Descartar:** registra una decisión explícita distinta de conservar. Cerrar el panel o cancelar la confirmación deja el conflicto pendiente; esta elección concreta la alternativa “descartada o pendiente, según su estado” de la fuente sin perder trazabilidad.

Antes de confirmar, el servicio bloquea la revisión del modelo, bloquea el conflicto y vuelve a leer proyecto/modelo. Revalida permisos, conexión de la sesión y `revisionEsperada`. Si apareció otra revisión, devuelve 409; la siguiente consulta reconstruye las diferencias contra el estado nuevo y evita decidir sobre información obsoleta.

Aplicar o fusionar usa una sola transacción y conexión:

`autorizar y bloquear → iniciar_edicion_modelo → restaurar/aplicar acciones JDBC → validar instantánea candidata → cambio_modelo → guardar_version_modelo → finalizar conflicto → actividad → commit`.

La validación semántica compara errores antes/después y rechaza errores UML nuevos. Un fallo revierte elementos, revisión, versión, resolución y actividad. La decisión queda vinculada por `id_cambio_resolucion` cuando produce una versión y siempre conserva `id_solicitud_resolucion`, resolutor, fecha, tipo y valor final.

## Persistencia incremental

La migración [2026-09-15_02_resolucion_conflictos.sql](migraciones/2026-09-15_02_resolucion_conflictos.sql) reutiliza `conflicto_edicion`; no añade tablas. Incorpora dos columnas, una FK compuesta, dos CHECK y dos índices únicos parciales. El esquema resultante mantiene 48 tablas y pasa de 507 a 509 columnas y de 119 a 120 FK.

La migración fue preparada para ejecutarse como propietario del esquema y no contiene datos. Los arneses la aplican únicamente a bases nuevas `uml_collab_test_*`. No se ha aplicado a la base personal del puerto 55432; el backend 0.5 requiere aplicarla antes de su reinicio personal.

## WebSocket e interfaz

WebSocket transporta avisos de estado, nunca resoluciones. `CONFLICTO_ACTUALIZADO` contiene modelo, conflicto, estado y revisión. El manejador vuelve a autorizar a cada destinatario antes de enviar. Tanto ese aviso como `MODELO_CONFIRMADO` usan listeners `AFTER_COMMIT`, por lo que un rollback no se anuncia.

React muestra un contador, lista, autores, fechas, revisiones y una tabla por propiedad. Las incompatibles se resaltan. Quien solo puede consultar ve modo de lectura. La decisión seleccionada se conserva en `sessionStorage` durante una recarga o desconexión; el UUID se reutiliza en el reintento de esa misma operación. La resolución requiere una segunda confirmación y se envía por REST con CSRF.

## Límites conscientes

Los avisos siguen siendo locales a una instancia de Spring Boot. La comparación trabaja con el formato de instantánea 2 y los tipos actualmente editables: paquete, clase, atributo, operación y relación; los parámetros se incluyen dentro de la operación. La consulta histórica y la restauración completa permanecen fuera de este incremento. No se decidió la contradicción de numeración en nombre del docente.
