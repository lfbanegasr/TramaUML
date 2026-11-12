# Activación verificada de imágenes en Gemini

El usuario informó que al importar una imagen el servicio aparecía como no habilitado. Se confirmó que Render ya servía la actualización anterior (`index-BYu_t-fc.js`). No se accedió a las credenciales ni a la configuración privada de la cuenta productiva durante el diagnóstico.

## Defectos corregidos

- La comprobación administrativa aplicaba GET sobre `:generateContent`, un recurso que requiere POST. Para comprobar la conexión ahora consulta el recurso del modelo con GET.
- El conector usaba una pausa compartida cuando texto agotaba su cuota; esa pausa podía bloquear visión sin realizar ninguna solicitud. Ahora las pausas se distinguen por capacidad.
- Un fallo visual devolvía ausencia genérica de proveedor y podía confundirse con falta de habilitación. La importación ahora diferencia falta de configuración, cuota, acceso rechazado y análisis sin resultado; no presenta una descripción manual como lectura real cuando el conector falla.

## Activación

Administración → Servicios inteligentes → **Habilitar imágenes con Gemini**.

La operación exige una identidad vigente con permiso de configurar servicios y protección CSRF. Reutiliza la clave del servicio Gemini de texto, del servicio Gemini de imagen activo o de `UML_IA_CLAVE`; no la devuelve al navegador. Prueba una imagen sintética de 16×16 sin datos del usuario y exige una respuesta de análisis. Solo tras una prueba exitosa abre la transacción, cifra la credencial, crea o actualiza `IA_IMAGEN` como `ACTIVO` y registra la comprobación y auditoría. Un fallo no crea ni activa servicios. La desactivación previa se cambia únicamente mediante esta acción explícita del administrador.

Se prueban modelos con capa gratuita, en orden de preferencia configurada y con respaldo: `gemini-3.8-flash`, `gemini-3.6-flash`, `gemini-2.5-flash`, `gemini-3.1-flash-lite`. La disponibilidad efectiva depende de la cuota de la API key. La prueba consume una pequeña solicitud de visión; no modifica facturación.

Si no existe ninguna clave, debe completarse `UML_IA_CLAVE` en Render o el servicio Gemini de texto. Nunca se debe pegar la clave en el chat ni guardarla en Git.

## Verificación local

- 24 pruebas backend aprobadas: activación solo tras análisis, secreto cifrado, registro previo inactivo, respaldo de modelo, cuota agotada sin escrituras, permisos, método HTTP correcto, independencia entre cuotas de texto y visión, y seguridad web existente.
- Dos pruebas Chrome aprobadas: activación desde Administración sin introducir otra clave y error de cuota sin confirmación falsa de servicio activo.
- Frontend compilado para producción; nuevo recurso `index-bI4k439p.js`.
- Pruebas externas simuladas; la activación con la clave real se realiza desde la sesión administrativa de la aplicación desplegada. No se afirma que esté activo en producción hasta que esa comprobación termine correctamente.
- El APK 1.0.2 usa el mismo servidor y no requiere reinstalación para recibir este cambio.

Fuentes oficiales consultadas el 2026-11-11:

- https://aistudio.google.com/api-reference/models
- https://aistudio.google.com/api-reference/generate-content
