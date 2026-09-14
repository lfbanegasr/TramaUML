# Ejecución del núcleo y decisiones — 2026-09-14

Este proyecto implementa el backend de la Plataforma Colaborativa de Modelado UML. La generación de backends para otros negocios y Flutter offline son fases posteriores, distintas de este servidor.

## Requisitos locales

Java 21, Maven 3.9 o posterior, Node compatible con Vite 8 y npm. Se comprobó con Java 21, Maven 3.9.9 y Node 24.14.1. Docker aloja la base real existente; no hay que ejecutar compose, crear un volumen nuevo ni instalar nuevamente el esquema.

Base real: localhost:55432, uml_collab, esquema uml_colaborativo, cuenta uml_app. El backend verifica las 48 tablas, 507 columnas, 119 FK y las funciones de edición al arrancar. Si no coincide, falla sin modificar el esquema. No utiliza Hibernate ni ejecuta migraciones al iniciar.

Trabajar desde la carpeta Implementacion. JAVA_HOME debe señalar un JDK 21; si mvn no está en PATH, usar su ruta instalada. La caché Maven se ubica en .qa en D: para evitar llenar C:.

```powershell
Set-Location core/backend
mvn '-Dmaven.repo.local=../../.qa/maven-repository' -DskipTests package
Set-Location ../frontend
npm ci --cache ../../.qa/npm-cache
npm run build
Set-Location ../..
```

El empaquetado anterior no sustituye las pruebas aisladas que se describen abajo.

## Primer administrador y ejecución real

No hay una cuenta ni contraseña predeterminadas. La base original tenía cero usuarios al verificarse el 14 de septiembre. La inicialización es un comando local explícito, sin endpoint HTTP; bloquea la tabla usuario dentro de una transacción y rechaza la operación cuando existe cualquier usuario, incluso si está inactivo.

El script solicita la clave existente de uml_app y la nueva contraseña personal del administrador mediante entrada oculta. Solo las mantiene temporalmente en variables del proceso y restablece el entorno al finalizar. No guarda un archivo de credenciales. La contraseña de PostgreSQL y la contraseña de acceso a la plataforma son diferentes conceptos.

Antes de solicitar claves, el script verifica Java 21 y el JAR. Si JAVA_HOME apunta a una carpeta inexistente, busca Java en PATH y después en Android Studio. Puede indicarse una instalación explícita con -JavaHome 'C:/Program Files/Android/Android Studio/jbr'. No modifica JAVA_HOME del sistema. Para comprobar únicamente el entorno: ./core/docs/2026-09-14_ejecutar_backend.ps1 -VerificarEntorno. Una ruta explícita inválida o una versión distinta de 21 detiene el comando antes de pedir secretos.

```powershell
./core/docs/2026-09-14_ejecutar_backend.ps1 -InicializarAdministrador
```

Introduce tu propio usuario, correo, nombre y contraseña. El proceso se cierra al completar la inicialización. Si ya existe un usuario, no la repitas ni borres usuarios para reabrirla.

Para desarrollo HTTP local, iniciar el servidor en una terminal:

```powershell
./core/docs/2026-09-14_ejecutar_backend.ps1 -HttpLocal
```

En otra terminal:

```powershell
Set-Location core/frontend
npm run dev
```

Abrir http://127.0.0.1:5173. React usa el proxy de Vite hacia 127.0.0.1:8080, de modo que las cookies y las peticiones tienen el mismo origen. Entrar con el administrador elegido, crear un proyecto privado y abrir el editor.

La aplicación requiere UML_DB_PASSWORD. El script la solicita si falta; también puede proporcionarse mediante el entorno sin escribirla en comandos ni archivos. UML_DB_URL, UML_DB_USER, UML_BIND_ADDRESS y UML_PORT permiten configurar el servidor; la cuenta del datasource debe ser uml_app. UML_COOKIE_SECURE vale true por defecto. -HttpLocal la desactiva solo durante la ejecución local; para despliegue usar HTTPS, cookies seguras y un proxy que publique React y /api bajo el mismo origen. No se incluye un despliegue de producción.

## Recorrido implementado

Inicio y cierre de sesión, consulta de proyectos autorizados con filtros/paginación, creación de proyecto con propietario y modelo inicial, apertura de modelo, clases, atributos, operaciones y parámetros, relaciones y multiplicidades, organización de posiciones y borrado lógico.

El editor permite crear, editar y eliminar clases, atributos, operaciones y relaciones. Los parámetros se añaden, modifican y quitan dentro de la edición de una operación; conservan sus UUID y reciben orden según la lista enviada. La eliminación de una clase informa cuántos elementos serán afectados antes de pedir confirmación. Un lector recibe el modelo sin controles de escritura.

Los campos de restricciones de atributos se conservan como JSON de dominio: no se ejecutan como SQL ni se interpretan todavía como reglas semánticas. La validación completa de tipos, el informe de CU14 y las reglas de generación están pendientes. También permanecen pendientes paquetes editables y dimensiones/rutas de conectores.

## Contratos del servidor

| Petición | Comportamiento |
|---|---|
| GET /api/acceso/csrf | Obtiene protección CSRF antes de escribir |
| POST /api/acceso/entrar | Autentica identificador/contrasena |
| GET /api/acceso/sesion | Consulta identidad y permisos actuales |
| POST /api/acceso/salir | Revoca la sesión actual |
| GET /api/proyectos | Consulta autorizada, con búsqueda, estado y página |
| POST /api/proyectos | Crea proyecto privado, propietario y modelo |
| GET /api/modelos/{id} | Lee el modelo y su revisión consistentemente |
| POST /api/modelos/{id}/cambios | Confirma un lote de edición |

Un cambio incluye idCambioCliente UUID, revisionBase y hasta 50 acciones. Cada acción declara operacion, entidad, idEntidad y datos. Entidades actuales: CLASE, ATRIBUTO, OPERACION y RELACION; acciones: CREAR, ACTUALIZAR, ELIMINAR y MOVER, donde MOVER corresponde a una clase. Los datos se comprueban mediante listas de campos permitidos, tipos y valores; el servidor obtiene el actor de la sesión y nunca acepta SQL libre.

Dentro de una transacción/conexión: autorizar, reconocer reenvío, iniciar_edicion_modelo, aplicar acciones JDBC, registrar cambio_modelo, guardar_version_modelo y commit. JDBC ejecuta inmediatamente y no necesita flush JPA. PostgreSQL genera la instantánea; no se modifica revision_actual ni se insertan snapshots desde Java. El listener AFTER_COMMIT está preparado, pero todavía no existe transporte WebSocket.

Ante SQLSTATE 40001 se revierte el lote completo. El servidor reconoce un UUID previamente confirmado después del rollback; de otro modo devuelve 409. No cambia automáticamente la revisión base del usuario. React conserva el formulario para que el usuario recargue, revise y decida guardar; un reenvío por respuesta perdida reutiliza el UUID y el contenido originales.

Las sesiones contienen un token aleatorio y opaco en cookie HttpOnly/SameSite Strict; la base conserva únicamente su hash. La identidad y los permisos se consultan de nuevo en el servidor. CSRF se renueva al entrar/salir; la autenticación de cada petición no vuelve a rotarlo. Esta configuración evitó un rechazo real detectado por las pruebas de navegador. Respuestas de seguridad usan UTF-8.

Políticas iniciales del núcleo, no reglas atribuidas al docente: bcrypt con coste 12; administrador con mínimo 12 caracteres y máximo 72 bytes UTF-8; sesiones de dos horas; bloqueo de quince minutos tras cinco intentos fallidos. Se documentan para que puedan revisarse.

## Pruebas con datos ficticios

Se requiere PostgreSQL nativo local para pruebas; el equipo dispone de PostgreSQL 18. La base real utiliza PostgreSQL 17 en Docker. Las pruebas de la aplicación se ejecutaron en 18; se verificó el catálogo vigente en 17, pero queda pendiente repetir la suite de aplicación en 17.

Desde Implementacion:

```powershell
./core/docs/pruebas/2026-09-14_probar_nucleo.ps1
./core/docs/pruebas/2026-09-14_probar_navegador.ps1
```

Los parámetros -JavaHome, -Maven y -PostgresBin permiten usar rutas instaladas sin copiarlas al proyecto. El primer script comprueba el directorio del servidor aislado antes de crear una base nueva uml_collab_test_*, siempre en puerto distinto de 55432. Reutiliza .qa/postgres, instalado exclusivamente para pruebas; no borra bases ni volúmenes. Las cuatro fuentes SQL revisadas 00/16/17/18 se cargan únicamente en la base nueva ficticia.

La autenticación trust del clúster de pruebas se limita al servidor vinculado a 127.0.0.1 y a datos ficticios; nunca se modifica pg_hba de la instalación real. Los tests del servidor además rechazan bases cuyo nombre no comienza por uml_collab_test_ y bases con usuarios existentes.

El segundo script inicia y detiene únicamente sus procesos de prueba (backend 8181, React 5174), genera un administrador ficticio con contraseña aleatoria en el entorno y usa Chrome instalado en modo sin ventanas. Las pruebas realizan peticiones reales: no hay API simulada. Una prueba intercepta deliberadamente una respuesta después del commit para verificar idempotencia; es inyección de un fallo de red en el test, no una funcionalidad simulada del producto.

Informes: core/backend/target/surefire-reports, core/frontend/test-results y registros .qa. Todo ese contenido está excluido de Git. Para detener el servidor nativo después de inspeccionar los datos:

```powershell
& 'C:/Program Files/PostgreSQL/18/bin/pg_ctl.exe' -D .qa/postgres/data -m fast stop
```

## Fuentes y organización siguiente

Se revisaron los flujos CU01, CU02, CU06, CU07 y CU10–14 de Documentacion/Documentacion.docx, incluidos diagramas de realización/secuencia para proyecto, clases, atributos/operaciones y relaciones. El contrato de persistencia corrigió el orden operativo simplificado de algunos diagramas: la lógica se concentra en servicios y la revisión/instantánea se confirman por las funciones existentes, sin alterar los originales.

Las especificaciones detalladas contienen 32 CU; la lista inicial y la tabla de ciclos no coinciden completamente. Se usa la secuencia solicitada por el usuario: CU01–14, CU15–20, CU21–26, CU27–32. No se atribuye esa corrección al docente.

Seguir dentro del ciclo inicial: administración de usuarios/roles/permisos, miembros, ciclo de vida del proyecto, paquetes y validación semántica. Después colaboración e historial, revisión humana de propuestas de IA, XMI y generación/compilación/descarga. Flutter requiere voz, IA y persistencia locales sin Internet para negocios diversos. La interpretación docente de Vista en el backend generado continúa pendiente; DTO no se considera automáticamente esa capa.

Fuentes vigentes: Documentacion/Documentacion.docx y Documentacion/Contexto del primer parcial.docx (relativas a Primer parcial), docs/2026-09-13_guia_persistencia_corregida.md y docs/2026-09-13_diccionario_base_datos_vigente.md (relativas a Implementacion). Ninguna se modificó.
