# Preparación de despliegue de Trama UML en Render + Neon

## Estado

Preparación local terminada. No se creó un servicio Render, una base Neon ni una URL pública: las conexiones de Render, Neon y GitHub no estaban habilitadas durante esta preparación. Tampoco se envió el repositorio académico ni datos locales a esos proveedores.

## Cambios

- Nuevos archivos `despliegue/2026-11-11_*`, separados de los originales de AWS.
- Perfil `core/backend/src/main/resources/application-despliegue.yml` para puerto del proveedor, proxy HTTPS y pool reducido.
- `core/backend/src/main/java/bo/edu/uml/despliegue/`: recursos públicos limitados a la web y health check; API y WebSockets conservan su cadena de seguridad.
- Paquete publicable aislado con fuentes permitidas, instalador de base vacía y sin `.env`, material académico, CV ni datos de la base local.
- Inicialización administrativa independiente, con contraseñas ocultas y sin bootstrap permanente en el servicio.

## Verificaciones realizadas

| Verificación | Resultado |
| --- | --- |
| `npm run build` en `core/frontend` | Correcto; aviso de tamaño de un bundle, no bloqueante |
| Suite del backend con PostgreSQL aislado | 219 pruebas, cero fallos/errores, una omitida |
| Pruebas nuevas `DespliegueWebTest` | Cuatro correctas, repetidas tras el ajuste final de CSP |
| SQL generado para el editor de Neon | Instalado en otra base ficticia PostgreSQL 17; 52 tablas |
| JAR de la versión actual | Empaquetado correctamente en la copia aislada |
| Creación del administrador sin servidor web | Correcta en la base ficticia |
| Chrome, perfil `despliegue`, frontend compilado | Login, proyecto, clase, API anónima 401, cookies Secure/HttpOnly/Strict y cuatro eventos WebSocket; sin errores CSP |
| Memoria del servidor después del recorrido básico | Aproximadamente 220 MB de working set en Windows; no es una medición en Render |
| Revisión de patrones de claves conocidas en el paquete | Sin coincidencias en la revisión realizada |

La prueba omitida fue `GeneracionRuntimeTest.zipGeneradoArrancaYPersisteCrudEnPostgresql`: no se verificó aquí la ejecución del backend generado. Los registros y datos sintéticos de prueba permanecen en `.qa/`, ignorada por Git y excluida del paquete.

La prueba de navegador utilizó el JAR con recursos compilados servidos desde una ubicación local configurada; el Dockerfile los incorpora al classpath durante el build. No se construyó la imagen Docker ni se verificaron TLS público o acceso multiusuario en Render. El servidor temporal propio se detuvo al terminar; no se detuvo el servidor de desarrollo del usuario.

## Pendientes para publicar

1. Habilitar las conexiones de Render, Neon y GitHub o proporcionar acceso mediante los paneles de los proveedores.
2. Crear una base Neon Free independiente, ejecutar el instalador y asignar una contraseña exclusiva al rol restringido `uml_app`.
3. Configurar los secretos del servidor. La comprobación directa de `UML_IA_CLAVE` del `.env` local devolvió HTTP 400 y su valor no coincide con el formato habitual de una clave Google; no se validó la credencial que pudiera estar cifrada en un servicio activo de la base local. No se imprimió la clave.
4. Publicar únicamente las fuentes aisladas en un repositorio privado autorizado y desplegar el servicio Free del Blueprint.
5. Repetir las comprobaciones funcionales contra la URL real, probar Gemini con la clave definitiva y medir compilación de backends generados bajo los límites del plan.

Guía operativa: `despliegue/2026-11-11_guia_render_neon.md`. No se propone IA local en esta entrega.
