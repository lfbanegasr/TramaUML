# PostgreSQL corregido para el desarrollo — guía vigente

Fecha: 2026-09-13. Estado: migración aplicada y comprobada en `uml-collab-postgresql-1`. Este es un derivado técnico de desarrollo, no una sustitución del enunciado ni de los documentos originales.

## Resultado y conexión

Se conservaron las 48 tablas, 507 columnas y 119 claves foráneas, con nombres de dominio en español. No se recreó el contenedor, no se eliminó el volumen y las cantidades exactas de registros de las 48 tablas coinciden antes/después. Continúan los 109 registros iniciales de catálogo; no se dejaron usuarios/proyectos ficticios en la instalación real.

| Parámetro | Valor vigente |
|---|---|
| Servidor | localhost |
| Puerto | 55432 |
| Base | uml_collab |
| Esquema | uml_colaborativo |
| Administración | uml_admin |
| Cuenta del backend | uml_app |
| Contraseñas | Las existentes, no modificadas ni incluidas en archivos |

El catálogo final tiene 127 índices, 37 funciones y 88 triggers personalizados. Se eliminaron cinco funciones obsoletas: cuatro verificadores sin disparadores, sustituidos por FK compuestas, y el verificador de cuenta activa del propietario, cuya regla se cambió. No se eliminaron tablas, atributos o relaciones. Los nuevos triggers aseguran integridad/edición; no ejecutan IA ni generan/compilan código.

## Correcciones justificadas

1. Un atributo identificador exige multiplicidad `1..1`, incluso cuando el máximo enviado es NULL. Las colecciones no identificadoras siguen permitidas.
2. Validaciones UML/backend terminadas requieren fecha de finalización y fechas ordenadas. `VALIDO` requiere cero errores/advertencias; `VALIDO_CON_ADVERTENCIAS` requiere cero errores y al menos una advertencia; `INVALIDO` requiere al menos un error. El worker debe actualizar estado, contadores y finalización juntos.
3. Bloquear/desactivar la cuenta de un propietario no elimina ni invalida su titularidad. Los permisos efectivos siguen excluyendo usuarios no activos. Retirar su membresía/rol todavía requiere transferir la propiedad en una transacción.
4. La configuración usada conserva inmutables sus parámetros, dependencias y mapeos. Su `estado` sí puede cambiar a archivado/desactualizado u otro valor permitido. Una nueva configuración es necesaria para cambiar el contenido técnico.
5. PostgreSQL ya no obliga a generar DTO/repositorios/controladores mediante flags: la política del generador verifica las capas y compatibilidades del producto seleccionado. La base sí exige versión validada, configuración válida y clases seleccionadas.
6. La descarga registrada exige una validación final satisfactoria, cero errores, archivo correcto y permiso. El controlador debe comprobar autorización/disponibilidad antes de transmitir el archivo: el registro por sí solo no protege un endpoint.
7. Cambiar el ámbito de roles/permisos verifica asignaciones vigentes. La modificación del catálogo se coordina mediante bloqueo transaccional no bloqueante para evitar combinaciones incompatibles en carreras concurrentes. Una colisión administrativa devuelve `40001` y requiere releer/reintentar la transacción.
8. Una clase eliminada lógicamente propaga el borrado a atributos, operaciones, parámetros y relaciones activas. No destruye registros físicos ni snapshots históricos. Se rechazan elementos vigentes sobre padres eliminados. Un paquete debe vaciarse/reubicarse antes de eliminarlo.
9. Las posiciones de conectores y las relaciones usan el mismo criterio de clases activas en la instantánea, evitando posiciones sin una relación correspondiente.
10. La cuenta `uml_app` requiere contexto de edición transaccional para escribir las ocho tablas UML compartidas y para cambiar nombre/descripción de la cabecera del modelo. El proyecto se mantiene activo mediante bloqueo compartido durante la edición; archivar/suspender impide nuevas ediciones y no puede intercalarse silenciosamente con la confirmación. Ya no modifica revisiones/versiones actuales directamente ni inserta snapshots arbitrarios. La confirmación comprueba que la revisión fue guardada.
11. Se añadieron cuatro índices enfocados: ambigüedades por propuesta/operación y asignaciones activas por rol. No se añadieron índices indiscriminadamente a todas las FK.
12. Las funciones privilegiadas tienen búsqueda de esquemas explícita y `pg_temp` al final; no se confía en tablas temporales del llamante ni se concede ejecución a PUBLIC. `uml_app` no puede crear objetos en los esquemas utilizados.

## Contrato que debe seguir Spring Boot

**Cambio importante de integración:** no escribir UML mediante `save()` aislados/autocommit ni invocar `reservar_revision_modelo` desde `uml_app`. Usar `iniciar_edicion_modelo` y `guardar_version_modelo` dentro de UNA transacción y conexión. El actor siempre lo obtiene el servidor de la identidad autenticada, nunca de un usuario elegido por el frontend.

Secuencia de un servicio `@Transactional`:

1. Autenticar, autorizar pertenencia/permiso y consultar `id_cambio_cliente` para reconocer reenvíos ya confirmados.
2. Ejecutar `iniciar_edicion_modelo(id_modelo, revision_base, id_usuario_autenticado)` y conservar la revisión devuelta.
3. Crear/modificar/eliminar lógicamente clases, atributos, operaciones, parámetros, paquetes, relaciones y posiciones mediante repositorios. Agrupar las operaciones de una misma acción lógica.
4. Registrar `cambio_modelo` con el UUID del cambio cliente, actor autenticado, revisión base/aplicada, parche y estado `APLICADO`. La unicidad evita duplicar el mismo cambio o revisión aplicada.
5. Si se usa JPA, ejecutar `EntityManager.flush()` antes de guardar la instantánea, evitando modificaciones pendientes que se intenten aplicar después de cerrar el contexto. Usar consultas nativas con el mismo EntityManager/conexión; no abrir otra conexión independiente.
6. Ejecutar `guardar_version_modelo(id_modelo, revision_reservada, id_usuario_autenticado, resumen)` y confirmar. El contexto se cierra al guardar; cualquier escritura UML posterior requiere otra edición.
7. Publicar por WebSocket únicamente el cambio confirmado. Invalidar/refrescar el contexto JPA cuando los triggers hayan cambiado dependientes o cabeceras que estén en memoria.

Ejemplo de contrato para consultas con parámetros de Spring (los nombres `:parametro` no son valores SQL literales ni comandos listos para pgAdmin):

```sql
-- Toda esta secuencia comparte la transacción del servicio.
SELECT uml_colaborativo.iniciar_edicion_modelo(
    :id_modelo, :revision_base, :id_usuario_autenticado
);

-- Repositorios: operaciones UML solicitadas por el usuario.
-- Registrar cambio_modelo y vaciar cambios JPA pendientes.

SELECT uml_colaborativo.guardar_version_modelo(
    :id_modelo, :revision_reservada, :id_usuario_autenticado, :resumen
);
```

El contexto SQL es LOCAL a la transacción: no permanece al reutilizar una conexión del pool. Es defensa contra errores de integración, **no autenticación ni aislamiento RLS por ingeniero**. El servidor PostgreSQL no se expone a React ni a Flutter; los clientes nunca reciben la clave de `uml_app`. El administrador puede realizar mantenimiento fuera del protocolo, por lo que no debe utilizarse su cuenta como datasource normal.

Los flags `estado_validacion`/`estado=VALIDO` son resultados declarados por workers confiables, no prueba de que PostgreSQL ejecutó un compilador. Mantener Spring Security en REST, WebSocket y descargas. Aplicar la política de último administrador, revocación de sesiones, permisos globales y acceso a proyectos privados en servicios autorizados. No aceptar SQL libre, contextos SQL, claves del proveedor o decisiones de permisos del cliente.

`version_fila` se incrementa mediante trigger. Si se usa JPA `@Version`, alinear el valor y verificar sus actualizaciones condicionadas; evitar escrituras masivas que sobrescriban versiones o entidades almacenadas en memoria después de una cascada.

## Conflictos, borrado y restauración

- `40001` por revisión obsoleta: revertir TODO el cambio y devolver conflicto al cliente (habitualmente HTTP 409). No sustituir su base por la revisión actual para sobrescribir automáticamente. Si era un reenvío, comprobar el UUID ya confirmado tras revertir.
- `40001` por colisión administrativa del catálogo: releer y revalidar antes de reintentar la transacción. `40P01`/timeouts también requieren rollback; no ignorarlos.
- `42501`: falta de permiso o escritura fuera del protocolo. `23514`: dato/regla incoherente o edición sin guardado. `23505`: unicidad/idempotencia. Mapear errores al API sin filtrar detalles internos sensibles.
- No llamar IA, compilar ni transmitir archivos mientras se mantiene la transacción de edición abierta. Preparar propuestas y ejecutar trabajos largos fuera del bloqueo; aplicar resultados después mediante una acción corta y controlada.
- Restaurar solo `clase_uml.eliminado_en=NULL` NO restaura automáticamente sus dependientes. Para deshacer/restaurar una versión, el servicio rehidrata explícitamente el snapshot: paquetes en orden de padres, clases, miembros, parámetros, relaciones y posiciones. Así no resucita datos eliminados independientemente.
- Una propuesta IA desactualizada conserva su solicitud original. La política actual exige aplicar sobre su revisión base inmediata; el backend debe crear una propuesta/solicitud actualizada y revisada si decide rebasarla. No falsear una revisión antigua para forzar su aplicación.
- Las instantáneas siguen usando `formato_version=2`; un restaurador/generador debe conservar la compatibilidad con el formato histórico en inglés si importa una copia anterior.

## PostgreSQL sí filtra datos; el backend ejecuta los procesos

Mantener WHERE, JOIN, ORDER BY, COUNT y paginación en consultas SQL. La autorización decide qué proyecto/usuario se consulta. No descargar las 48 tablas a Java para filtrarlas.

Reconocimiento de voz/fotos, validación semántica, tratamiento de XMI, transformación UML→Java/PostgreSQL, selección de plantillas y compilación pertenecen al backend/workers. PostgreSQL conserva su estructura, estados, trazabilidad y restricciones. Las preferencias personales y presencia efímera no necesitan una revisión global por cada movimiento; reducir su frecuencia de escritura y conservar en memoria lo momentáneo, usando coordinación compartida si hay varias instancias.

Se conserva el snapshot completo por acción confirmada. No se introdujo CRDT ni versionado incremental: la revisión global serializa cambios del mismo modelo de forma segura, pero puede generar conflictos aun cuando afecten clases distintas. La política de frecuencia/checkpoints y una estrategia más granular deben decidirse con medidas de modelos grandes.

## Archivos e instalación

- [Migración 18 aplicada](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/database/2026-09-13_18_correcciones_desarrollo.sql>): SQL puro para el esquema español completo; transacción atómica y reejecutable. Ante datos incompatibles, se detiene sin borrar/arreglar silenciosamente registros.
- [Aplicación con respaldo y copia aislada](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/database/2026-09-13_20_aplicar_correcciones.ps1>): sin `-Aplicar` solo valida y respalda; con `-Aplicar` prueba antes de migrar el original.
- [Instalador nuevo vigente](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/database/2026-09-13_instalar_espanol.psql>): únicamente para una base VACÍA, carga 00→16→17→18→19→21.
- [Docker para instalaciones futuras](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/database/2026-09-13_docker_compose_postgresql.yml>): inicializa solo un volumen nuevo. No es necesario ejecutarlo sobre tu contenedor actual ni eliminar volúmenes.
- [Diccionario vigente extraído de PostgreSQL](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/docs/2026-09-13_diccionario_base_datos_vigente.md>).
- [Evidencia de validación actual](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/docs/2026-09-13_validacion_correcciones.md>).

Los scripts 16/17 del 12 de septiembre se conservan como línea base del instalador; el resultado vigente es esa línea base MÁS la migración 18. No aplicar la traducción 14 ni los instaladores viejos sobre esta base. Las suites nuevas son 19/21; la suite 15 se conserva como antecedente y contiene la antigua regla del propietario.

Los respaldos previos están en `database/.privado/`, excluidos de Git. Se restauraron en otra base para comprobarlos. Son dumps de la base, no respaldos de contraseñas/roles globales ni de archivos de almacenamiento externo. La ventana de migración debe estar sin escrituras del negocio: comparar conteos no detecta actualizaciones que mantengan el mismo número de filas. No restaurar encima de la base actual como primer paso.

El diagrama EA existente conserva sus 48 clases, 507 atributos y las mismas FK/multiplicidades físicas. No necesita reordenarse por esta migración. Las notas técnicas de CHECK/triggers del script antiguo son antecedentes; para documentar sus nuevas reglas utilizar el diccionario vigente. No se afirma haber ejecutado o remozado el diagrama dentro de EA en esta corrección.

## Alcance y fuentes

Se revisaron el [enunciado actual](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Documentacion/Contexto del primer parcial.docx>) y la [documentación del estudiante](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Documentacion/Documentacion.docx>), sin modificarlos. No se dispuso de una rúbrica adicional; no se inventan criterios de aprobación.

La base de la plataforma guarda diagramas/proyectos. La base PostgreSQL generada para una barbería u otro negocio es independiente. Flutter sin Internet necesita persistencia e IA locales y una estrategia de sincronización si se añade acceso remoto; esta migración no implementa ese requisito. Sigue pendiente confirmar con el docente cómo representar la capa Vista en el backend REST generado y la variante XMI de evaluación.

Referencias técnicas: [CHECK y NULL](https://www.postgresql.org/docs/17/ddl-constraints.html#DDL-CONSTRAINTS-CHECK-CONSTRAINTS), [bloqueos de filas](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS), [funciones SECURITY DEFINER seguras](https://www.postgresql.org/docs/17/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY), [eventos Spring después de commit](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html).

Antes de implementar el servicio, comprobar con un caso propio: ¿qué debe ocurrir si falla una relación después de crear una clase?, ¿cuándo se publica por WebSocket?, ¿qué se rehidrata al restaurar una versión? Las tres respuestas deben seguir este contrato transaccional.
