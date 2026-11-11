# Trama UML: Render gratuito, Neon y Gemini

Esta variante publica React compilado y Spring Boot en un único servicio HTTPS. PostgreSQL vive en Neon y Gemini se consulta exclusivamente desde el backend. La laptop del presentador no ejecuta el servidor ni el modelo de IA.

## Archivos y procedencia

- `2026-11-11_render.Dockerfile`: construcción de web, JAR y caché del compilador de backends generados.
- `2026-11-11_render.yaml`: servicio Render con `plan: free`, sin base Render ni recursos pagos.
- `2026-11-11_instalar_neon.psql`: instalación nueva basada en el esquema español, catálogos, correcciones del 13/09 y migraciones 01 a 11 de `core/docs/migraciones/`.
- `2026-11-11_preparar_render.ps1`: copia permitida de fuentes; excluye `.env`, datos locales y materiales académicos/personales. Genera también SQL para el editor web de Neon.
- Perfil `despliegue`: puerto `PORT`, HTTPS detrás del proxy y pool de cinco conexiones.

No se importan automáticamente usuarios, proyectos ni conversaciones de la base local. Esta guía prepara una instalación independiente. Para conservar proyectos existentes, planificar una migración aparte.

## 1. Preparar las fuentes publicables

Desde `Implementacion`:

```powershell
.\despliegue\2026-11-11_preparar_render.ps1
```

El comando muestra una carpeta nueva dentro de `despliegue/.salida/`. Esa carpeta, que contiene `core/`, `database/`, `despliegue/` y `render.yaml`, es la raíz del repositorio de despliegue. No publicar el repositorio académico completo ni copiar `.env`.

Revisar el paquete antes de enviarlo a un repositorio privado autorizado. Render necesita acceso a ese repositorio. Los archivos ignorados tampoco deben enviarse mediante otros mecanismos.

## 2. Preparar Neon

Crear un proyecto en el plan Free, PostgreSQL 17 o posterior, con una base nueva vacía. Elegir una región próxima a Virginia, donde se propone alojar Render. Conservar el usuario propietario solo para instalar y administrar el esquema.

Con `psql`, establecer las variables `PGHOST`, `PGDATABASE`, `PGUSER`, `PGSSLMODE=verify-full` y conectar con solicitud interactiva de contraseña:

```powershell
psql -X -W -v ON_ERROR_STOP=1 -f .\despliegue\2026-11-11_instalar_neon.psql
psql -X -W
```

Dentro de la segunda sesión:

```text
\password uml_app
```

Asignar una contraseña nueva, exclusiva de esta base. No usar la del propietario ni la de la instalación local. No introducirla en comandos que queden en el historial.

Alternativa sin `psql`: ejecutar `despliegue/2026-11-11_instalar_neon.sql` del paquete en el editor SQL de Neon, solo en la base vacía, y asignar la contraseña del rol mediante la interfaz de roles de Neon. Si una operación no está disponible en la interfaz, usar `psql`; no conceder privilegios administrativos a `uml_app` para sortearla.

La instalación aborta si encuentra el esquema `uml_colaborativo`. No borrar una base existente para volver a intentar. Revisar el error y, si corresponde, usar otra base vacía.

## 3. Variables privadas del servidor

Configurar en Render, nunca en React ni en Git:

| Variable | Valor |
| --- | --- |
| `UML_DB_URL` | `jdbc:postgresql://HOST_DIRECTO_NEON/BASE?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&currentSchema=uml_colaborativo,public` |
| `UML_DB_USER` | `uml_app` |
| `UML_DB_PASSWORD` | Contraseña exclusiva de ese rol |
| `UML_IA_CLAVE` | API Key vigente de Gemini |
| `UML_SERVICIOS_MASTER_KEY` | 32 bytes aleatorios codificados en Base64 |
| `SPRING_PROFILES_ACTIVE` | `despliegue` |

Usar el endpoint directo de Neon: Hikari ya administra el pool y el código usa configuración de sesión. No pegar una URL `postgresql://usuario:clave@...` en `UML_DB_URL`; usuario y contraseña van por separado. La fábrica SSL indicada utiliza las autoridades de confianza del JDK y `verify-full` comprueba el nombre del servidor; no requiere copiar un `root.crt` al contenedor.

La clave maestra cifra secretos de proveedores guardados por la aplicación: conservarla en el gestor de secretos; cambiarla sin migrar esos secretos impide descifrarlos. La API Key de Gemini del entorno no se escribe en la base de datos.

El Blueprint propone `gemini-2.5-flash` en las tres variables de modelo. Verificar que la clave tenga acceso y cuota para ese modelo; ajustar al modelo que se haya probado. Un servicio activo configurado en la aplicación tiene prioridad sobre el entorno.

## 4. Crear el primer administrador antes de publicar

Compilar el backend local y conectar temporalmente ese JAR a la base nueva de Neon. Usar el helper `2026-11-11_inicializar_admin_neon.ps1`: solicita las contraseñas con entrada oculta, crea el administrador y termina sin abrir un servidor web. No modifica `.env` ni deja habilitado el bootstrap en Render.

```powershell
.\despliegue\2026-11-11_inicializar_admin_neon.ps1
```

No ejecutar sobre una instalación con usuarios: el backend lo rechaza para protegerlos.

Si se trabaja únicamente desde el paquete aislado, empaquetar primero con `mvn -f core/backend/pom.xml package -DskipTests` usando Java 21. El helper selecciona la versión de JAR declarada en el POM, aunque queden JAR antiguos en `target/`.

## 5. Crear el servicio en Render

Crear un Blueprint con el repositorio aislado y `render.yaml`. Comprobar **Free** antes de crear. Completar las variables privadas. Si se usa creación manual de Web Service, seleccionar Docker, contexto raíz y `despliegue/2026-11-11_render.Dockerfile`; configurar `/salud` como health check.

La imagen descarga dependencias en la construcción y no necesita claves para compilar. Ejecuta el servidor con un usuario sin privilegios y un heap máximo inicial de 256 MB. No definir `UML_BOOTSTRAP_ENABLED=true` en Render: el administrador se crea con el helper anterior.

El nuevo perfil permite GET/HEAD de `/`, `/index.html`, `/assets/**`, `/favicon.ico` y `/salud`. La cadena existente continúa protegiendo `/api/**` y `/ws/**`. Se conservan cookies Secure, HttpOnly, SameSite=Strict y protección CSRF.

## 6. Verificar la URL real

1. `/salud` debe responder 200 y `{"estado":"DISPONIBLE"}` después de completar el arranque y consultar PostgreSQL.
2. Abrir `/`, iniciar sesión y comprobar que no aparecen errores CSP ni recursos 401.
3. Crear proyecto/modelo ficticio, guardar y volver a abrirlo.
4. Abrir dos sesiones y comprobar edición colaborativa mediante WSS.
5. Consultar al asistente y solicitar una propuesta pequeña; revisar antes de aplicar.
6. Probar exportación/importación XMI y generación de ZIP. Probar la compilación del backend generado separadamente y observar memoria.
7. Redeplegar y confirmar que usuarios, modelos, archivos y conversaciones almacenados en PostgreSQL se conservan.

## Límites reales

- Render Free suspende el servicio por inactividad; abrirlo con antelación a la defensa. No garantiza disponibilidad continua.
- El disco local del contenedor es efímero. La caché de compilación está en la imagen; los directorios de trabajo son temporales. Los datos duraderos deben seguir en PostgreSQL.
- La compilación de backends generados arranca otra JVM con su propio límite de memoria y puede superar el presupuesto del plan gratuito. No se deshabilita silenciosamente: medirla en Render y comunicar cualquier función no verificada.
- Neon y Gemini también tienen cuotas. Una API Key no garantiza uso gratuito ni disponibilidad de todos los modelos.
- La defensa desplegada necesita Internet en el aula, aunque el servidor y Gemini funcionen fuera de la laptop.

Fuentes: [Render Free](https://render.com/docs/free), [Blueprints](https://render.com/docs/blueprint-spec), [Neon](https://neon.com/pricing), [conexiones Java/Neon](https://neon.com/docs/guides/java).
