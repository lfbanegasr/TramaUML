# Seguimiento del incremento 02 — 2026-09-14

Estado: código implementado, servidor verificado y migración aplicada a la base real. Versión 0.2.0-SNAPSHOT. Continúa el núcleo ya existente; no se recreó la base ni se modificaron los documentos originales.

## Decisiones
- Administración, miembros, paquetes y validación mantienen módulos separados, DTO de entrada, servicios de negocio y repositorios JDBC.
- Sesiones opacas y CSRF continúan vigentes; los permisos se consultan en el servidor. Los cambios de cuenta revocan sesiones; cambios de permisos se reflejan en la autorización de peticiones posteriores.
- Alta/restablecimiento usan credencial aleatoria de una sola activación, hash bcrypt marcado y contraseña personal posterior. El marcador impide eludir la activación mediante cambios de estado.
- Protección de cuenta propia administrativa, último administrador activo y rol Superadministrador; verificación de ámbito y límites del administrador delegado.
- Las membresías y asignaciones revocadas conservan historial. DENEGAR prevalece. El propietario exige transferencia explícita antes de ser restringido o retirado.
- Las revisiones administrativas detectan sobrescrituras concurrentes. El bloqueo de catálogo precede al bloqueo del proyecto al modificar miembros.
- Paquetes, padres y ubicación de clases usan la transacción y funciones de versionado vigentes, con ORGANIZAR_MODELO.
- Worker de validación de capacidad limitada sobre instantáneas inmutables; informes persistentes y localización, regla desconocida = FALLIDO, revisión anterior = DESACTUALIZADO.
- El estado de validación es metadato permitido; no se escriben instantáneas/punteros/revisiones arbitrarios.
- Convenciones verificables: Java con google-java-format AOSP, TypeScript/CSS con Prettier, UTF-8/LF y BOM en PowerShell.
- Flutter aplazado por indicación actual del usuario; requisito histórico offline conservado.
- No se equipara DTO a “Vista” del backend generado. Generación y backend de la plataforma siguen separados.

## Recorridos realizados y cobertura académica
No se marca un CU completo si faltan obligaciones de su flujo.

| CU | Recorrido ejecutable incorporado | Pendiente del caso completo |
|---|---|---|
| CU03 | Alta, filtros, edición, estado, roles globales, activación, restablecimiento y protección administrativa | Notificaciones, expiración temporal y auditoría global consultable |
| CU04 | Alta/edición de roles, nombre único, filtros, ámbito, permisos, protección y dependencia antes de desactivar | Retiro lógico explícito: se usa INACTIVO de forma conservadora; revisar política del estado RETIRADO |
| CU05 | Matriz por ámbito, revocación, confirmación del impacto y autorización vigente | Vista detallada de cuentas afectadas y notificación del cambio al cliente/presencia |
| CU08 | Incorporación, filtros, suspensión/retiro con motivo, reactivación y cierre de registros de sesión existentes | Presencia WebSocket, transferencia de otras responsabilidades y notificaciones |
| CU09 | Rol principal, excepciones, control de ámbitos/escalamiento, revisión concurrente y transferencia de propiedad | Política de múltiples roles y actualización inmediata de la interfaz por eventos |
| CU14 | Worker, nueve reglas del catálogo, informe persistente, errores/advertencias, localización, versión consistente y obsolescencia | Ampliar catálogo, acordar vocabulario de restricciones y reglas completas para generación/XMI |
| CU16 | Paquetes jerárquicos, ubicación de clases y borrado lógico seguro; arrastre del núcleo previo | Dimensiones, trazado de conectores, preferencias y organización visual completa |

Los recorridos del incremento 01 siguen vigentes. CU06 recuperación personal y CU07 actualización/archivo/reactivación de proyectos siguen incompletos. CU15 y CU17–20 requieren actividad consultable, colaboración, conflictos registrados, historial y restauración. CU21–32 siguen pendientes. El resumen inicial del Word y su tabla de ciclos difieren de los 32 CU detallados; se conserva la organización corregida solicitada por el usuario, sin atribuirla al docente.

## Pruebas y resultados
- PostgreSQL 18.3 nativo, base nueva ficticia en 127.0.0.1:55433: 21 pruebas de integración + 3 del motor = 24 aprobadas, 0 fallos/errores/omitidas. Última suite: 32,628 s Maven; integración 24,03 s.
- PostgreSQL 17.11 nativo portátil, clúster separado .qa/postgres17 y base nueva ficticia en 127.0.0.1:55435: las mismas 24 aprobadas, 0 fallos/errores/omitidas. Suite 29,003 s; integración 24,67 s.
- Compilación TypeScript y Vite: aprobada; 192 módulos, sin errores de tipos.
- Maven: empaquetado ejecutable versión 0.2.0-SNAPSHOT aprobado después de las pruebas. La versión anterior presentó un bloqueo de renombrado de Windows; el artefacto nuevo queda separado.
- Chrome/Playwright: ocho pruebas aprobadas, 0 fallos, 39,3 s en la ejecución final, incluida la comprobación del ancho corregido del diálogo de miembros. Se corrigió una navegación del test que intentaba pulsar detrás del diálogo abierto.
- El servidor se prueba sin simular persistencia: sesiones, CSRF, permisos, borrado lógico, rollback, revisión/idempotencia y funciones de versionado reales.
- Pruebas nuevas: credencial temporal/activación/estado/restablecimiento; delegado sin escalamiento; matriz y ámbitos; miembros con denegación/suspensión/transferencia; paquetes con ciclos/contenido/ubicación preservada; validación persistida, corrección, obsolescencia y regla no implementada; tipo ambiguo cualificado, rango numérico y generalización cíclica.
- El worker se desactiva solo en las pruebas de integración deterministas; las pruebas de navegador ejecutan el worker real asíncrono con el JAR empaquetado.
- Se revisaron capturas de administración, miembros y validación. Detectaron un max-width heredado que estrechaba el diálogo de miembros; se corrigió y se incorporó comprobación de anchura de escritorio.
- Sintaxis de los cuatro scripts PowerShell, formato Java y Prettier: comprobados. El lanzador -VerificarEntorno detectó Java 21 y el JAR 0.2 en Windows PowerShell 5.1, mostrando correctamente acentos y sin pedir secretos. La política de ejecución se habilitó solo en ese proceso de comprobación, sin modificar la política del sistema.
- Prueba inicial del motor falló por una expectativa y un objeto JSON incompleto del fixture. Corregidos los datos, pasaron las tres pruebas; no se eliminó el caso.
- La revisión automática rechazó crear un contenedor adicional de prueba por la restricción del usuario. No se creó. Se completó la comprobación de PostgreSQL 17 con binarios oficiales portátiles y un clúster nativo aislado en D:.
- Los Word permanecieron en su ubicación y no se escribieron. Las fuentes Markdown vigentes y scripts de antecedentes se conservaron.

Registros excluidos de Git: .qa/2026-09-14_incremento2_backend.log, .qa/2026-09-14_incremento2_postgres17.log, .qa/2026-09-14_incremento2_empaquetado.log y .qa/2026-09-14_incremento2_navegador.log. Surefire: core/backend/target/surefire-reports. Navegador/capturas: core/frontend/test-results.

Los binarios PostgreSQL 17.11-3 utilizados proceden de la [página oficial de EDB](https://www.enterprisedb.com/download-postgresql-binaries), archivo 1260491. SHA-256 local del ZIP utilizado: 4B8DB0930C38F6EF845DB919551DEDDA3B6B845AEB0927B3D79A6E8E9E4537CF. Este hash identifica el archivo usado; no se presenta como checksum publicado por EDB. Solo bin/lib/share se extrajeron en .qa/tools/postgres17; no se instaló un servicio Windows.
Al terminar se cerraron los dos servidores nativos ficticios tras comprobar su data_directory. pg_ctl confirmó que no están en ejecución y ambos registros muestran cierre completado; sus bases se conservaron. El arnés cerró solamente sus procesos de backend/web de prueba.

## Base real
Se verificó PostgreSQL 17.10 en el contenedor existente uml-collab-postgresql-1. Antes de la migración, uml_app carecía de DELETE en rol_permiso. La tabla pertenece a uml_admin.

Se aplicó únicamente core/docs/migraciones/2026-09-14_01_revocacion_permisos_rol.sql como propietario, tras probarla en bases aisladas. La comprobación posterior con uml_app confirmó el privilegio y conservó 48 tablas, 507 columnas y 119 FK; permaneció el usuario personal existente. No se insertaron datos ficticios, cambiaron contraseñas, recrearon contenedores ni eliminaron volúmenes.

El recuento de columnas excluye vistas: contar information_schema.columns sin filtrar BASE TABLE también incluye sus columnas y puede devolver 586. Esto no representa un cambio de esquema.

El código del incremento se comprobó contra bases aisladas. El nuevo JAR en la instalación personal requiere reiniciar la terminal del backend con la clave existente mediante entrada oculta; no se considera realizado ese reinicio por el usuario.

## Pendientes materiales y siguiente paso
1. Reiniciar el backend personal con -HttpLocal y revisar Administración, Miembros, Nuevo paquete e Informe de validación. No volver a inicializar al administrador.
2. Completar recuperación personal y ciclo de vida del proyecto con sus requisitos específicos.
3. Desarrollar consulta de actividad, sesiones/presencia, transporte WebSocket AFTER_COMMIT e historial navegable/restauración por el contrato existente.
4. Decidir la persistencia de auditoría global: el registro técnico posterior al commit en .qa no garantiza una auditoría global transaccional. La actividad de miembros sí se guarda en actividad_proyecto.
5. Acordar vocabulario completo de restricciones, política de roles múltiples, expiración/notificación de credenciales y retención de bitácoras.
6. Continuar después IA revisable, XMI y generación/compilación/descarga. Un informe semántico VALIDO no acredita compilación ni compatibilidad Enterprise Architect.
7. Flutter permanece aplazado; no se sustituye su requisito histórico offline por llamadas remotas.

Instrucciones: [contratos y ejecución](2026-09-14_incremento_02_contratos.md), [convenciones](2026-09-14_convenciones_desarrollo.md), [ejecución general](2026-09-14_ejecucion_y_contratos.md).

## Preguntas para defender las decisiones
- ¿Por qué un botón oculto no asegura que un lector no pueda editar?
- ¿Qué sucede si cambia una clase entre solicitar la validación y terminar el worker?
- ¿Por qué el mismo UUID permite recuperar una respuesta perdida sin duplicar una clase?
- ¿Qué diferencia existe entre registrar actividad dentro de la transacción y escribir una bitácora después del commit?
- ¿Por qué el backend de esta plataforma y el backend que generará para un negocio necesitan contratos distintos?
