# Seguimiento vigente — 2026-09-14

Estado: desarrollo retomado. Primer incremento ejecutable comprobado con Spring Boot, React, Chrome y PostgreSQL aislado. Posteriormente el usuario resolvió la credencial de uml_app y ejecutó correctamente el bootstrap y el backend contra la instalación real; véase la confirmación de arranque al final. El asistente no restableció contraseñas.

## Incremento técnico cerrado

- Estructura por funcionalidades en core/backend y core/frontend, con instrucciones y contratos en core/docs.
- Autenticación real mediante sesiones PostgreSQL, hash bcrypt, token opaco con hash almacenado, revocación, bloqueo por intentos y autorización del servidor.
- Consulta SQL de proyectos autorizados, búsqueda por nombre/código, estado y paginación; proyecto privado con propietario y modelo inicial persistente.
- Editor visual con creación, consulta, modificación y borrado lógico de clases, atributos, operaciones y relaciones. Parámetros de una operación se guardan juntos, mantienen UUID y orden y admiten dirección, obligatoriedad y valor predeterminado.
- Clases con estereotipo opcional; atributos con multiplicidad, identificador, valor predeterminado y restricciones JSON; relaciones con tipo, dirección, roles y cardinalidades. Posiciones de clases persistentes.
- Confirmación completa por iniciar_edicion_modelo/cambio_modelo/guardar_version_modelo dentro de una transacción/conexión. El listener es AFTER_COMMIT; WebSocket todavía no se implementó.
- Reenvíos con el mismo UUID y contenido, rollback por revisión obsoleta y conservación del borrador ante conflictos o fallos de red. Navegar/cerrar sesión con un borrador exige una decisión explícita; se impide salir durante un guardado.
- Comando local para inicializar el primer administrador: entrada oculta, entorno temporal, sin credenciales fijas, rechazado si ya existe cualquier usuario.

## Correspondencia con los casos de uso

No se declara completo un CU con pasos pendientes de su especificación. El incremento cierra recorridos técnicos concretos:

| CU detallado | Parte comprobada | Pendiente para completar el CU |
|---|---|---|
| CU01 | Inicio real, identidad/permisos, bloqueo y panel React | Auditoría completa de acceso en bitácora |
| CU02 | Revocación, cierre y protección del borrador | Auditoría completa de cierre |
| CU06 | Listado autorizado, nombre/estado, paginación y apertura | Filtros de fecha/rol y resumen ampliado |
| CU07 | Crear proyecto privado, propietario, modelo inicial y actividad | Actualizar/archivar/reactivar y comprobación de sesiones activas |
| CU10 | Carga consistente del editor, revisión y solo lectura | Presencia de sesión y estado/informe de validación |
| CU11 | CRUD, unicidad por restricciones existentes, estereotipo, impacto y versiones | Incorporar avisos de validación semántica |
| CU12 | CRUD atributos/operaciones y parámetros, firma y versiones | Validación semántica completa de tipos/restricciones |
| CU13 | CRUD, tipos, cardinalidades, roles, dirección y reglas existentes | Completar revisión semántica y representación de navegabilidad |
| CU14 | Validaciones de entrada/integridad al guardar | Worker de validación, informe persistente y localización de hallazgos |
| CU16 | Arrastre de clases con posición persistente | Paquetes editables, dimensiones, rutas y preferencias |

CU03–05 y CU08–09 siguen pendientes. El manejo transaccional de conflictos es una base técnica; CU17–20 todavía requieren colaboración, conflicto registrado, historial navegable y restauración. CU21–32 permanecen pendientes.

## Pruebas ejecutadas y resultados

- Compilación TypeScript y build Vite: aprobados. La caché npm y Maven se ubicó en D:.
- Maven: compilación, 12 pruebas de integración y empaquetado aprobados. Última suite: 12/0 fallos/0 errores/0 omitidas, 12,99 s. Registro .qa/2026-09-14_pruebas_backend.log; surefire en core/backend/target/surefire-reports.
- Chrome/Playwright: 4 pruebas aprobadas, 14,3 s. Registro .qa/2026-09-14_pruebas_navegador.log; resultado JSON y captura en core/frontend/test-results.
- Recorridos navegador: sesión/cierre; clases/atributos/operaciones/parámetros/relaciones y recarga; arrastre persistente; respuesta perdida después del commit y reenvío idéntico sin duplicación; conflicto entre dos revisiones con borrador conservado y revisión explícita.
- Pruebas del servidor adicionales: parámetros ajenos no se reasignan; firma duplicada revierte operación y versión completas; orden de parámetros y borrado lógico. Se mantienen los casos de CSRF, membresía retirada, lector, idempotencia, concurrencia y protocolo obligatorio.
- Las pruebas del navegador detectaron que SessionManagementFilter borraba CSRF al volver a autenticar cada petición. Se desactivó esa gestión automática y se configuró NullSecurityContextRepository; la sesión opaca se consulta en PostgreSQL y la rotación ocurre en los controladores de entrada/salida. Se añadió una comprobación de que crear proyectos no elimina CSRF.
- Se corrigieron la selección del elemento tras un reenvío confirmado y los nombres accesibles de selectores; la suite volvió a pasar después de esos cambios.
- Scripts PowerShell: sintaxis comprobada; preparación nativa, bootstrap ficticio y arranque/parada de procesos de navegador ejecutados correctamente.
- PostgreSQL real 17 en Docker: disponible y healthy. Consultas READ ONLY con uml_app dentro del contenedor confirmaron 48 tablas, 507 columnas, 119 FK y cero usuarios. No se dejó ningún dato ficticio en esa base.
- Aplicación probada contra PostgreSQL nativo 18 en bases nuevas uml_collab_test_* en 55433. La suite de aplicación contra 17 y el arranque externo contra la instalación real todavía no se verificaron.
- Los hashes SHA-256 de ambos documentos Word coinciden con la lectura inicial; originales conservados.

## Decisiones para explicar el proyecto

La transacción conserva una acción UML completa: si falla un parámetro o una relación, la clase, sus cambios y la versión se revierten juntos. El actor proviene de la sesión del servidor. La revisión base detecta una edición concurrente; el UUID permite reconocer una respuesta perdida sin crear dos veces el mismo elemento.

Las funciones existentes generan las instantáneas y preservan el historial. JDBC permite controlar el orden de escritura y versionado sin escrituras JPA pendientes. Los eventos se procesan después del commit para que una transacción fallida no se anuncie como confirmada.

Se mantiene la organización actual solicitada CU01–14 / CU15–20 / CU21–26 / CU27–32; las discrepancias de la lista y tabla de ciclos del Word siguen registradas. No se inventaron reglas del docente. Flutter offline es una fase adicional obligatoria para negocios diversos, con voz, IA y persistencia locales. Vista del backend generado permanece pendiente de interpretación; DTO no se equipara automáticamente a esa capa.

## Ejecución y siguiente paso

Corrección de arranque del 14 de septiembre: JAVA_HOME señalaba un JDK Eclipse Adoptium inexistente. El lanzador ahora valida Java 21 antes de pedir secretos, detecta instalaciones locales y permite -VerificarEntorno. Se comprobó este modo con Windows PowerShell y el JBR 21 de Android Studio, sin iniciar el backend ni modificar PostgreSQL. El script usa UTF-8 con BOM para que Windows PowerShell muestre correctamente los acentos. Esta comprobación no confirma todavía la autenticación con la base real.

Consultar 2026-09-14_ejecucion_y_contratos.md para compilar, iniciar el administrador propio, ejecutar React y repetir pruebas. 2026-09-14_ejecutar_backend.ps1 solicita secretos con entrada oculta; no exige publicarlos en el chat.

Siguiente incremento dentro del ciclo inicial: auditoría de acceso, administración de usuarios/roles/permisos y miembros, completar filtros/resumen/ciclo de vida del proyecto, paquetes y el informe de validación de CU14. Confirmar el recorrido de inicio de sesión y edición desde el navegador en la instalación real con el administrador elegido por el usuario.

Los módulos de IA, intercambio XMI y generación todavía no se desarrollaron. La IA deberá producir propuestas revisables; los servicios ejecutarán validación, generación y compilación. Flutter debe ejecutar reconocimiento, IA y persistencia locales sin Internet. El backend de esta plataforma y el backend de negocios que se generará siguen separados.

## Confirmación posterior del arranque real

La salida compartida por el usuario registra el 14 de septiembre a las 01:11: conexión psql como uml_app a uml_collab; mensaje de primer administrador inicializado; cierre normal del proceso de bootstrap; arranque posterior HTTP en 8080 con conexión Hikari completada. El cierre del bootstrap es intencional. El asistente comprobó después HTTP 200 en http://127.0.0.1:8080/api/acceso/csrf y http://127.0.0.1:5173/. Esto confirma el arranque y la disponibilidad HTTP; todavía no demuestra el recorrido autenticado del usuario ni sustituye la suite pendiente de aplicación en PostgreSQL 17. No se copiaron secretos ni la salida privada al repositorio.
