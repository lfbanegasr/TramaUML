# Trama UML Android 1.0.2

Actualización para la presentación del proyecto, basada en `mobile/lib/` y el backend existente.

- Conexión fija HTTPS a `https://trama-uml.onrender.com`. El inicio de sesión no expone configuración de servidor y descarta direcciones locales guardadas por versiones anteriores.
- El historial compartido conserva la posición de lectura. Ningún sondeo ni finalización de envío desplaza la lista. El usuario puede bajar con «Ver últimos mensajes».
- Las consultas conservan nombre e identificador del autor recibidos del servidor.
- La selección de fotos no rechaza el original por superar 10 MB. Cámara y galería preparan una copia; el transporte adapta imágenes grandes a un máximo de 4 MiB, con liberación de recursos de decodificación. Esto no constituye almacenamiento ni transferencia de archivos ilimitados.
- El servidor acepta archivos multipart de hasta 16 MB y solicitudes de 18 MB. Es un margen para las fotos optimizadas y el audio existente, necesario para la memoria disponible en Render Free.

## Modelo del servidor

Texto y visión se prepararon para `gemini-3.8-flash`, con respaldo en modelos que Google lista con capa gratuita. La transcripción permanece en `gemini-2.5-flash`. La disponibilidad efectiva y los cupos dependen del proyecto y la API key de Google; no se verificó la cuota de la cuenta ni se habilitó facturación. Las configuraciones de servicios activas en la base de datos conservan prioridad sobre variables de entorno.

Referencias consultadas el 2026-11-11:

- https://aistudio.google.com/docs/pricing#gemini-3.8-flash
- https://aistudio.google.com/docs/models/gemini-3.8-flash

## Verificación

- `flutter analyze`: sin incidencias.
- `flutter test --no-pub`: 18 pruebas aprobadas, incluyendo lectura durante mensajes entrantes, lectura cerca del final, migración desde una IP local y optimización de un original de 12 MiB.
- Chrome/Playwright: dos regresiones aprobadas con API ficticia, sin datos de producción: lectura estable durante múltiples sondeos y carga de un original de 12 MiB con envío reducido.
- Backend: 25 pruebas aprobadas de IA y visión, incluida respuesta Gemini dividida en varias partes con exclusión de partes de razonamiento.
- APK release: versión `1.0.2`, código `3`, Android 7.0 o superior, firma verificada para instalación directa.
- No se realizó una prueba física con la cámara del teléfono ni una consulta con la cuota real de Gemini.

## Entrega

APK: `despliegue/.salida/2026-11-11_trama_uml_android_v1.0.2.apk` desde la raíz del proyecto.

SHA-256: `583F92328C761D4ADBC4A2BB1E84A7B9540AEE439736C82E288CB565B839C3E2`.

El APK puede instalarse como actualización sobre los anteriores generados en esta laptop. Para compilar: `flutter pub get` y `flutter build apk --release`, desde `mobile/`.

La actualización del servidor requiere sincronizar el Blueprint vinculado a la rama `render-deploy` del repositorio TramaUML. Usar **Manual Sync** en la página del Blueprint para aplicar también los modelos definidos en `render.yaml`; un despliegue de código por sí solo no cambia variables antiguas. Antes de esa sincronización, el servicio público seguía mostrando el JavaScript anterior `index-C_uPw8os.js`.
