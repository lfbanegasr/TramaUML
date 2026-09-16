# Base técnica propuesta para CU19 historial y CU20 restauración

Fecha: 2026-09-15. Estado: análisis y contratos propuestos; no constituyen implementación de CU19 ni CU20.

## Fuente y numeración

Las fichas detalladas de `core/docs/fuentes/2026-09-13_lectura_Documentacion.txt` denominan **CU19 — Consultar historial del modelo** y **CU20 — Restaurar versión del modelo**. El catálogo resumido los desplaza a CU18/CU19. La contradicción permanece pendiente de confirmación docente.

## Formato vigente de versión

`version_modelo_uml` guarda por modelo un `numero_version`, `revision`, versión padre, posible versión restaurada, `instantanea` JSONB, SHA-256, estado de validación, resumen, autor y fecha. Las restricciones garantizan identidad dentro del mismo modelo, revisión única e instantánea tipo objeto.

`guardar_version_modelo` construye internamente una instantánea `formato_version: 2`. Sus grupos son:

1. `modelo` con identidad, nombre y descripción;
2. `paquetes`;
3. `clases`;
4. `atributos`;
5. `operaciones`;
6. `parametros`;
7. `relaciones`;
8. `posiciones_clases`;
9. `posiciones_relaciones`.

Los arreglos usan UUID estables y orden determinista. Solo contienen filas activas; el historial de borrado permanece en las tablas vivas y cada snapshot representa el estado visible de su revisión.

## Carga y validación requerida

Un lector de snapshots deberá exigir objeto JSON, `formato_version = 2`, modelo coincidente y arreglos con tamaño/límites definidos. Debe verificar UUID, campos obligatorios, enums y tipos antes de mostrar o comparar. Además debe comprobar referencias: paquete padre, clase-paquete, atributo/operación-clase, parámetro-operación, extremos de relaciones y posiciones. El hash almacenado debe contrastarse con la representación canónica que usa PostgreSQL; no basta confiar en el JSON recibido.

Para restaurar, el orden de reconstrucción seguro es:

1. paquetes en orden de padres;
2. clases;
3. atributos y operaciones;
4. parámetros;
5. relaciones;
6. posiciones de clases y relaciones.

Las eliminaciones de elementos ausentes deben ocurrir en orden inverso o mediante las reglas de borrado lógico existentes. Todo debe ejecutarse entre `iniciar_edicion_modelo` y `guardar_version_modelo`, registrar un único cambio lógico y producir una **nueva** versión con `id_version_restaurada` apuntando al origen. No se debe mover directamente `revision_actual`, `id_version_actual` ni insertar snapshots desde Spring.

## Comparación de versiones

La comparación debe aceptar únicamente dos versiones del mismo modelo/proyecto. Se debe indexar cada grupo por UUID y clasificar agregado, eliminado o modificado; para operaciones también se comparan parámetros. Las posiciones se presentan como cambio visual independiente. El orden de arreglos no constituye una diferencia por sí mismo. Campos técnicos como fechas de creación, borrado o número de fila requieren una lista explícita de exclusión antes de declarar equivalencia semántica.

Las restricciones y preguntas aún abiertas son:

- comportamiento ante un futuro `formato_version` desconocido;
- normalización exacta usada para verificar el SHA-256 desde Java;
- filtros de “tipo de modificación” y “elemento afectado” cuando una versión contiene varias acciones;
- política exacta para cambios pendientes de sesiones activas: la fuente exige guardarlos o descartarlos, pero no define quién puede forzar esa decisión;
- forma de mostrar una versión cuyo snapshot esté dañado, además de sus metadatos.

## Contratos REST propuestos

Los siguientes contratos son propuesta para el incremento siguiente y pueden ajustarse al implementar:

| Método y ruta                                                  | Propósito                                                                                               |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| GET `/api/modelos/{modelo}/versiones`                          | Página descendente con filtros por actor, fechas, tipo y entidad. Requiere `VER_HISTORIAL_MODELO`.      |
| GET `/api/modelos/{modelo}/versiones/{version}`                | Snapshot validado en modo lectura y metadatos; 422 si el contenido no puede interpretarse.              |
| GET `/api/modelos/{modelo}/comparaciones?desde={a}&hasta={b}`  | Diferencias por UUID y propiedad, solo entre versiones del mismo modelo.                                |
| POST `/api/modelos/{modelo}/restauraciones/previsualizaciones` | Verifica versión, revisión vigente, sesiones/cambios pendientes y devuelve resumen sin escribir.        |
| POST `/api/modelos/{modelo}/restauraciones`                    | Confirma con UUID idempotente, revisión esperada, versión origen y motivo. Requiere `RESTAURAR_MODELO`. |

La restauración deberá reautorizar, bloquear revisión/modelo, revalidar snapshot, comprobar sesiones activas, reconstruir, ejecutar validación semántica completa, guardar como nueva versión, registrar actividad y publicar `MODELO_CONFIRMADO` después del commit. Una carrera o inconsistencia debe revertir toda la operación y conservar la última versión estable.
