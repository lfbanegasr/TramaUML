# Incremento 03 — proyectos y actividad

Fecha: 2026-09-14. Backend de esta plataforma: 0.3.0-SNAPSHOT; frontend: 0.3.0. Se continúa desde 0.2, sin cambiar versiones de dependencias.

## Fuentes y decisiones

Fuentes: [seguimiento 02](2026-09-14_seguimiento_incremento_02.md), [contratos 02](2026-09-14_incremento_02_contratos.md), [convenciones](2026-09-14_convenciones_desarrollo.md), [guía de persistencia](../../docs/2026-09-13_guia_persistencia_corregida.md) y [diccionario vigente](../../docs/2026-09-13_diccionario_base_datos_vigente.md).

Se revisaron los flujos detallados CU06, CU07 y CU15 en `core/docs/fuentes/2026-09-13_lectura_Documentacion.txt`, derivado de `../Documentacion/Documentacion.docx`. Se inspeccionaron sus diagramas originales extraídos, respectivamente `.qa/fuentes/2026-09-13_diagrama_82.png`, `81.png` y `73.png`. Estos derivados de lectura están excluidos de Git; los Word originales se conservaron. El enunciado sigue identificado en `../Documentacion/Contexto del primer parcial.docx`; no se añaden criterios del docente.

El seguimiento 02 llamaba al pendiente de CU06 “recuperación personal”. El flujo detallado describe recuperación de datos ante errores y retorno a los proyectos autorizados; no describe recuperación de contraseña. Aquí se conserva la consulta en memoria durante la navegación y se permite reintentar. Al cerrar o perder la sesión se descarta ese estado; no se almacena información privada en localStorage.

Decisiones técnicas explícitas, sujetas a aclaración de requisitos:

- “Fecha” del filtro CU06 se interpreta como fecha de creación. La interfaz lo identifica expresamente. Los rangos de proyectos y actividad son días UTC inclusivos: límite inferior a medianoche y superior exclusivo al inicio del día siguiente. No se interpreta la fecha como último cambio.
- Los metadatos editables son código, nombre y descripción. Se mantiene la unicidad de código en PostgreSQL. Propiedad y accesos conservan los mecanismos del incremento 02; no se aceptan campos arbitrarios en lugar de esos mecanismos.
- “Solo lectura” se aplica a metadatos y edición UML. La gestión de accesos existente se conserva para poder revocar permisos. CU07 no define claramente ese alcance sobre administración de miembros: esta interpretación queda pendiente de confirmación académica y se explica al confirmar el archivo.
- La configuración inicial no tiene un vocabulario documentado suficiente. Se conserva `opciones` y su valor predeterminado; no se inventan opciones. Por este punto y la ambigüedad anterior no se marca CU07 completo.
- Se bloquea el archivo cuando existen registros ACTIVO/EDICION en `sesion_edicion_modelo`. Un registro de solo lectura no es una sesión de edición. No se inventa una caducidad, no se cierran sesiones automáticamente y no se ofrece archivo forzado.
- La consulta muestra todos los roles vigentes del actor y filtra por cualquiera de sus asignaciones activas. No modifica la política de roles múltiples pendiente del incremento 02. Se conserva el acceso global del SUPERADMINISTRADOR existente, sin concederlo a nuevos roles por su nombre.

## Contrato HTTP

Todos los endpoints requieren sesión opaca. Las escrituras requieren CSRF. El actor procede de la sesión; el servidor vuelve a consultar identidad y permisos vigentes. DENEGAR mantiene su precedencia en la autorización de proyecto existente.

| Método y ruta bajo `/api` | Contrato |
|---|---|
| GET `/proyectos` | `filtro` por nombre/código, `estado`, `rol` por código, `desde`/`hasta` YYYY-MM-DD, `pagina` desde 0 y `tamano` 1–100, predeterminado 20; total y filas autorizadas calculados en SQL |
| GET `/proyectos/roles-consulta` | Códigos de texto y nombres de roles de proyecto activos; no permisos ni cuentas |
| GET `/proyectos/{id}` | Resumen, autorizaciones actuales, revisión, fechas, modelo asociado, conteo de miembros y sesiones de edición registradas |
| GET `/proyectos/{id}/equipo` | Nombres, roles y estados para quien puede consultar el proyecto; sin correos ni credenciales; no concede gestión de miembros |
| PUT `/proyectos/{id}` | `codigo`, `nombre`, `descripcion`, `revision` ISO con zona y `idSolicitudCliente` UUID |
| POST `/proyectos/{id}/archivar` | `revision`, `idSolicitudCliente`, `confirmar: true`; transición ACTIVO → ARCHIVADO |
| POST `/proyectos/{id}/reactivar` | Los mismos campos; transición ARCHIVADO → ACTIVO; no reactiva SUSPENDIDO |
| GET `/proyectos/{id}/actividad` | `responsable` UUID, `accion`, `tipo`, `entidad` UUID, `desde`/`hasta` UTC, `pagina`, `tamano`; orden `ocurrido_en DESC, id DESC` |
| GET `/proyectos/{id}/actividad/filtros` | Actores, acciones y tipos existentes exclusivamente en ese proyecto |
| GET `/proyectos/{id}/actividad/{evento}` | Valores anterior/posterior, detalle y versión/revisión vinculadas, cuando estén registrados |

Un proyecto no autorizado se devuelve como 404. Cuando el proyecto está disponible pero falta GESTIONAR_PROYECTOS o VER_ACTIVIDAD se responde 403. Las entradas inválidas reciben 400/422; revisión, estado, sesiones, unicidad o bloqueo concurrente reciben 409. Se mantienen los errores sanitizados de persistencia del núcleo, incluido SQLSTATE 40001.

`actividad_proyecto.id` es BIGINT y se expone como cadena decimal para conservar precisión en JavaScript. El detalle verifica también `id_proyecto`; un ID conocido de otro proyecto no concede acceso. Los códigos CITEXT del catálogo se convierten explícitamente a texto.

## Transacciones e idempotencia

Las actualizaciones de proyecto toman primero el bloqueo compartido de catálogo administrativo vigente y después `proyecto FOR UPDATE NOWAIT`. Vuelven a autorizar bajo ese bloqueo. La revisión es `actualizado_en`, comparada como instante; Java no fabrica ni actualiza ese valor directamente. El trigger existente mantiene la marca.

Se reconoce la solicitud ya confirmada antes de comprobar revisión o transición, pero después de autorizar. Su UUID, actor, operación y petición canónica quedan en `actividad_proyecto.detalle`. Un UUID reutilizado con otra petición recibe 409. El bloqueo de proyecto serializa este reconocimiento y su inserción, sin una tabla o migración nuevas. Reenviar una solicitud confirmada devuelve el resumen vigente y no deshace cambios posteriores.

La transacción contiene modificación y actividad con valores anteriores/posteriores. Un error revierte ambos. El archivo no modifica revisiones ni versiones UML. El bloqueo exclusivo del proyecto impide intercalarlo con una transacción UML que ya mantiene el bloqueo compartido del contrato PostgreSQL. No se elimina contenido al archivar; la reactivación vuelve a habilitar el estado, conservando permisos y versiones.

La edición UML conserva UNA transacción/conexión:

`autorizar → reconocer UUID → iniciar_edicion_modelo → cambios JDBC → cambio_modelo → guardar_version_modelo → actividad por acción → commit`.

Se capturan los valores de la entidad antes y después de cada acción en esa misma transacción. Las operaciones incluyen sus parámetros y las clases su posición. El borrado lógico conserva su marca en el valor posterior. No se fabrican snapshots de versión: son valores parciales de actividad. El evento VERSION_GUARDADA que produce PostgreSQL se conserva como confirmación de versión, separado de los eventos de entidad. Los registros de actividad existentes son inmutables; no se enriquecen retroactivamente.

El listener de cambios del núcleo sigue siendo AFTER_COMMIT. No hay transporte WebSocket implementado. Las futuras publicaciones de proyecto también deberán producirse después del commit.

React conserva campos ante 409. Recargar la revisión es una acción explícita que muestra los datos actuales y conserva el borrador; no envía ni mezcla cambios automáticamente. En errores de red, respuesta JSON incompleta o errores 5xx conserva la petición y el UUID para reintentar exactamente el mismo envío. La salida con borrador solicita una decisión y se bloquea durante el guardado.

## Ejecución personal

El JAR 0.3 se genera separado porque Windows mantiene abierto el JAR 0.2 personal. No se reinicia ni se detiene ese proceso automáticamente. En la terminal donde corre el backend, termina su ejecución con Ctrl+C y, desde Implementacion, ejecuta:

```powershell
./core/docs/2026-09-14_ejecutar_backend.ps1 -HttpLocal
```

El lanzador busca Java 21 y solicita la clave vigente de `uml_app` mediante entrada oculta si la variable UML_DB_PASSWORD no está disponible. **No uses -InicializarAdministrador**: tu administrador ya existe. No envíes contraseñas al chat. Backend local: `http://127.0.0.1:8080`; React: `http://127.0.0.1:5173`. Usa una sola dirección para mantener las cookies del navegador.

Para iniciar React, si no está ejecutándose:

```powershell
cd core/frontend
npm.cmd run dev
```

La conexión personal conserva `localhost:55432`, `uml_collab`, esquema `uml_colaborativo` y datasource `uml_app`. No se necesita una nueva migración; la revocación de rol_permiso ya aplicada no se repite.

## Pruebas reproducibles

Se reutilizan los arneses [servidor nativo](pruebas/2026-09-14_probar_nucleo.ps1) y [navegador](pruebas/2026-09-14_probar_navegador.ps1). Requieren Java 21 y Maven disponibles; se puede indicar -JavaHome y -Maven. Crean una base ficticia nueva, validan el clúster y prohíben el puerto personal 55432. La inicialización del administrador en el arnés se aplica exclusivamente a esa base aislada.

```powershell
./core/docs/pruebas/2026-09-14_probar_nucleo.ps1 -JavaHome 'C:/Program Files/Android/Android Studio/jbr' -Maven '<ruta existente de mvn.cmd>'
# Después de empaquetar core/backend con Maven:
./core/docs/pruebas/2026-09-14_probar_navegador.ps1 -JavaHome 'C:/Program Files/Android/Android Studio/jbr'
./core/docs/2026-09-14_formato_java.ps1 -JavaHome 'C:/Program Files/Android/Android Studio/jbr'
cd core/frontend
npm.cmd run format:check
npm.cmd run build
```

Para PostgreSQL 17 portátil usa el arnés de servidor con -PostgresBin apuntando a sus binarios existentes, -Puerto 55435 y -NombreCluster postgres17. No requiere Docker ni un contenedor adicional. Los datos ficticios se conservan en `.qa` para inspección.

## Preparación del siguiente incremento

1. CU10/CU17: persistir apertura/cierre y presencia reales. Todo escritor de sesiones debe tomar el bloqueo compartido del proyecto antes de comprobar ACTIVO y registrar la sesión, compatible con el archivo exclusivo. La interfaz actual no crea presencia por abrir una pestaña: el contador muestra registros existentes.
2. WebSocket autenticado y autorizado, presencia y comunicación de revocaciones/archivo; cambios confirmados exclusivamente AFTER_COMMIT. Diferenciar mensajes de presencia de cambios persistidos.
3. CU18: registrar conflictos conservando los borradores. El registro necesita una transacción separada de la edición revertida; no convertir un 409 en sobrescritura ni restauración automática.
4. CU19–20: historial paginado y restauración mediante las funciones vigentes y una nueva versión confirmada; no escribir punteros o instantáneas arbitrarios.

Quedan visibles auditoría global transaccional/consultable, notificaciones, expiración de credenciales, política de roles múltiples y alcance completo del archivo. IA, XMI y generación/compilación/descarga se desarrollarán después. “Vista” del backend generado sigue pendiente; DTO no se declara equivalente. Flutter está aplazado por el usuario y conserva su requisito histórico de voz, IA y persistencia locales offline para negocios diversos.
