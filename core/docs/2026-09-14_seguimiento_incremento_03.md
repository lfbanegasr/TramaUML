# Seguimiento vigente — incremento 03

Fecha: 2026-09-14. Continuación del incremento 02, commit de partida `76c77eb`. Backend: 0.3.0-SNAPSHOT; frontend: 0.3.0. Verificado en bases aisladas; despliegue personal pendiente.

## Estado recuperado y alcance

Se leyeron el seguimiento y los contratos del incremento 02, las convenciones, los AGENTS.md aplicables y los módulos/pruebas pertinentes. El árbol core estaba limpio antes de este incremento. Se conserva JDBC, Java 21, Spring Boot, React/TypeScript/Vite, React Flow, módulos de negocio, sesiones opacas, cookies, CSRF y todos los recorridos del núcleo 0.2.

Se revisaron CU06, CU07 y CU15, sus textos derivados y los diagramas del Word original. Las rutas, decisiones y contratos HTTP están en [contratos 03](2026-09-14_incremento_03_contratos.md); las fuentes PostgreSQL siguen siendo la guía y el diccionario corregidos, no los scripts antiguos.

La inspección de procesos confirmó que el backend personal ejecutaba el JAR 0.2 (PID 17744), y las consultas HTTP a 8080/acceso/csrf y React 5173 respondieron 200. Eso resuelve la incertidumbre del seguimiento 02 sobre qué JAR se había reiniciado; no equivale a probar recorridos personales autenticados ni a desplegar 0.3.

## Implementación

- CU06: filtros SQL por nombre/código, estado, rol y fecha de creación UTC, paginación/total autorizados, consulta de resumen con permisos renovados y retorno/reintento de la vista personal conservando filtros en memoria.
- CU07: actualización de código/nombre/descripción, archivo/reactivación confirmados, revisión concurrente, UUID idempotente, bloqueo de sesiones EDICION/ACTIVO y coordinación con transacciones UML. Resumen con equipo, actividad y modelo asociado según permisos.
- CU15: listado paginado/filtrado por responsable, acción, tipo, UUID de entidad y rango de fechas; orden estable para empates, detalle con valores y versión vinculada cuando existan. Consulta protegida también por VER_ACTIVIDAD.
- Actividad atómica de metadatos/estados y de acciones UML. Se conserva el evento de versión PostgreSQL y se añaden eventos de entidad con sus valores; no se modifica actividad histórica ni el esquema.
- React conserva borradores ante conflictos y distingue revisión explícita de reenvío exacto tras resultado incierto. Se protege también una respuesta JSON incompleta después de un commit.
- Versión 0.3 separada para conservar el JAR 0.2 abierto por el proceso personal de Windows. Dependencias sin cambios. El lanzador y el arnés de navegador apuntan al artefacto nuevo.

## Cobertura académica y decisiones pendientes

| CU | Estado | Cobertura de este incremento | Límite que impide sobredeclarar |
|---|---|---|---|
| CU06 | Flujo de consulta implementado; interpretación de fecha pendiente | Consulta, filtros, selección autorizada, resumen, casos vacíos, retiro de membresía y recuperación tras fallo | Fecha se interpreta explícitamente como creación UTC; el documento no especifica su significado. La política de asignaciones múltiples sigue pendiente y no se redefine |
| CU07 | Parcial | Consulta, modificación, archivo y reactivación implementados; creación del 0.2 conservada | Configuración inicial no definida y alcance de solo lectura sobre gestión de accesos pendiente. Presencia/apertura/cierre de sesiones se desarrollarán con colaboración |
| CU15 | Consulta implementada; cobertura histórica parcial | Consulta, filtros, paginación y detalle para actividad persistida; valores y versión de las acciones UML nuevas | Productores futuros y valores ausentes en registros históricos no se inventan; auditoría global es un pendiente diferente |

El pendiente antiguo “CU06 recuperación personal” no significa recuperación de contraseña: el flujo detallado habla de recuperación de datos. CU07 impide archivo por sesiones de edición; no autoriza cerrarlas por fuerza ni inventar expiración. Solo lectura congela metadatos y UML; la administración de accesos existente se conserva como interpretación técnica pendiente, explicada en la confirmación.

Se mantiene la contradicción documentada entre la lista inicial/tabla de ciclos del Word y los 32 casos detallados. No se atribuye al docente la organización corregida solicitada por el usuario. No se infiere que DTO sea la capa “Vista” del backend generado.

El análisis denomina al responsable “Jefe de proyecto”; el catálogo vigente usa PROPIETARIO_PROYECTO. Se conserva ese rol y la protección de propiedad del núcleo 0.2; no se crea otro rol por la diferencia de denominación.

## Evidencias de esta ejecución

- PostgreSQL 18.3 nativo, base ficticia nueva en 127.0.0.1:55433: **35 aprobadas** (32 integración + 3 motor), 0 fallos/errores/omitidas. Suite Maven final 39,091 s; integración 28,42 s.
- PostgreSQL 17.11 nativo portátil, otra base ficticia nueva en 127.0.0.1:55435: las mismas **35 aprobadas**, 0 fallos/errores/omitidas. Suite 45,468 s; integración 40,89 s. Se reutilizaron sus binarios existentes; no se creó un contenedor.
- Chrome/Playwright con backend 0.3 real, worker semántico asíncrono real y React en 8181/5174: **13 aprobadas**, 0 fallos/omitidas/inestables; informe final 60,036 s. Incluye las ocho regresiones existentes y cinco recorridos nuevos.
- Recorridos nuevos: filtros/retorno, actualización/archivo/lectura/reactivación; conflicto conservado y respuesta JSON incompleta tras commit con reenvío del mismo UUID; paginación/filtros/detalle UML/versión y recuperación de actividad tras fallo; recuperación de lista; membresía retirada entre listado y selección.
- Servidor: acceso denegado, catálogo de roles como texto, fecha UTC y paginación, revisión/idempotencia, concurrencia, sesiones de edición frente a lectura, bloqueo de edición en curso, unicidad/rollback, detalle limitado al proyecto, antes/después y versión UML, ausencia de eventos al revertir y orden de eventos con tiempos iguales.
- Java 21/Maven: JAR ejecutable plataforma-0.3.0-SNAPSHOT.jar empaquetado, sin sobrescribir el 0.2 abierto. TypeScript/Vite: build aprobado, 196 módulos. Dependencias conservadas.
- Formato Java AOSP y Prettier: comprobados. Se reaplicó la única diferencia de formato Java restante antes del empaquetado final; sin cambio de comportamiento. Sintaxis de los cuatro scripts PowerShell y lanzador -VerificarEntorno: aprobados, sin solicitar secretos ni iniciar el backend personal.
- Se inspeccionaron las capturas incremento3-resumen-verificado.png e incremento3-proyecto-actividad.png. El resumen muestra descripción completa y ancho de escritorio comprobado; el detalle conserva sus valores en columnas y permite desplazar JSON extenso.
- Primera suite de servidor: dos errores de fixtures por roles inexistentes, corregidos usando el catálogo vigente. Las suites posteriores pasaron. El navegador detectó CITEXT serializado como objeto: conversión a texto corregida y comprobación de API añadida. Los fallos posteriores de etiqueta de descripción/selector de acción se corrigieron mediante asociación explícita de label y selector por rol accesible, respectivamente. La regresión final completa pasó; no se omitieron casos.

Los resultados históricos del incremento 02 (24 servidor y ocho navegador) son antecedentes; las cifras de este apartado corresponden a ejecuciones de este incremento.

Registros excluidos de Git: `.qa/2026-09-14_incremento3_backend.log`, `.qa/2026-09-14_incremento3_postgres17.log`, `.qa/2026-09-14_incremento3_empaquetado.log`, `.qa/2026-09-14_incremento3_navegador.log` y `.qa/2026-09-14_incremento3_formato_web.log`. Informes: `core/backend/target/surefire-reports`, `core/frontend/test-results/resultado.json` y capturas en test-results. Artefactos generados y bases ficticias permanecen excluidos de Git.

Al finalizar las pruebas no se obtuvo autorización automática para detener los servidores PostgreSQL ficticios de los puertos 55433 y 55435 porque se alcanzó el límite temporal del revisor. No se intentó eludir la restricción: sus directorios de datos aislados se conservaron y los procesos pueden continuar activos hasta realizar el cierre seguro. Esto no afecta ni modifica el contenedor o la base personal.

## Base personal y despliegue

La consulta de solo lectura usando uml_app confirmó PostgreSQL 17.10, base uml_collab, 48 tablas, 507 columnas y 119 FK. La cuenta dispone de INSERT en actividad_proyecto y UPDATE en proyecto: no se prepara ni se aplica una migración nueva. No se repite la migración de DELETE rol_permiso en la base personal. No se inicializa/restablece al administrador, se insertan datos ficticios personales ni se recrean contenedores/volúmenes.

El proceso personal 0.2 se conserva. Para usar 0.3 se requiere terminar ese backend en su terminal y ejecutar el lanzador con -HttpLocal, sin -InicializarAdministrador, utilizando la clave vigente por entrada oculta. El frontend Vite personal lee los archivos nuevos durante el desarrollo; los endpoints nuevos requieren el reinicio del backend. Instrucciones completas: [ejecución 03](2026-09-14_incremento_03_contratos.md#ejecución-personal).

## Pendientes vigentes y próximo paso

1. Reinicio personal con el JAR 0.3 y comprobación humana de filtros, resumen, archivo/reactivación y actividad; no confundirlo con las pruebas aisladas.
2. Confirmar vocabulario de configuración inicial, significado de fecha CU06 y alcance de archivo sobre administración de accesos. Mantener las interpretaciones explícitas mientras se aclaran.
3. CU10/CU17: sesiones persistidas y presencia/WebSocket autenticado; escritores de sesiones bajo bloqueo compartido del proyecto y eventos confirmados AFTER_COMMIT.
4. CU18–20: conflictos registrados, historial y restauración por el contrato existente, con borradores conservados y sin escrituras arbitrarias de revisión/snapshot.
5. Auditoría global transaccional y consultable; notificaciones, expiración de credenciales, política de roles múltiples y retención/performance de bitácoras.
6. IA revisable, XMI y backend generado/compilación/descarga en incrementos posteriores. Flutter aplazado; requisito histórico offline preservado.

Para continuar en otro chat: leer primero este seguimiento y contratos 03; luego las fuentes y módulos del siguiente CU. No reinterpretar pendientes históricos resueltos como pendientes actuales. Ejecutar los arneses nativos ficticios; cualquier futura migración debe quedar separada, justificada y probada y requiere aprobación antes de aplicarse a la instalación personal.

## Defensa breve

- La autorización se renueva en el servidor porque un botón visible puede quedar obsoleto tras un retiro de acceso.
- Revisión concurrente detecta que otro usuario modificó el proyecto; UUID idempotente reconoce que el mismo usuario reenvió una operación ya confirmada. Resuelven problemas distintos.
- Actividad y cambio comparten transacción para evitar que una bitácora anuncie una edición revertida o que un cambio carezca de su registro.
- Archivo coordina su bloqueo con edición UML para impedir que una edición confirme después de archivar. Las sesiones activas no se cierran sin una regla documentada.
