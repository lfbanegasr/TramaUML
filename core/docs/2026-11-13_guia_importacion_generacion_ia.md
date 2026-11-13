# Importación, versiones, generación e IA — 2026-11-13

## Cómo generar y descargar

1. Abre **Interoperabilidad → Generar backend**.
2. Elige la versión guardada que quieres usar. Puedes elegir una versión histórica sin restaurarla sobre el lienzo.
3. Ajusta los nombres del proyecto si lo necesitas. Las capas, clases, operaciones y tipos están en **Opciones avanzadas**.
4. Pulsa **Guardar y generar**. La generación no exige ni solicita validación UML: funciona con versiones sin validar, ya validadas o que luego se hayan revisado. Se conservan los controles de configuración necesarios para producir código.
5. Pulsa **Validar y descargar ZIP**. El servidor compila el código y descarga el archivo al aprobar. Si prefieres consultar primero el informe, usa **Validar backend** y luego **Descargar ZIP validado**.

Después de generar sigue disponible **Volver a generar esta versión**. Las generaciones anteriores se conservan y se recuperan en **Generaciones guardadas**, incluso al reabrir el diálogo. Al seleccionar una generación guardada también se carga su versión UML para volver a generar desde ella. La configuración cargada es la última guardada para esa versión; cada ZIP anterior conserva su configuración original.

La validación del diagrama y la compilación del proyecto son comprobaciones distintas. Una versión UML válida todavía puede contener características que el generador no implementa; el diagnóstico de configuración identifica esos bloqueos. Un ZIP requiere una compilación aprobada para su contenido e integridad SHA-256 para descargarse. Una nueva comprobación pendiente o fallida no revoca la aprobación anterior del mismo contenido. Cambiar el contenido del ZIP sí exige otra aprobación. Los propietarios y líderes tienen el permiso de validación de backend; los demás participantes necesitan que alguien con ese permiso lo valide.

Se pueden guardar varias configuraciones con el mismo nombre y versión: cada guardado conserva una instantánea independiente y no invalida las anteriores. El historial permite recuperar las generaciones anteriores, incluidas las aprobadas más antiguas.

Si aparece **SIN_ENTIDADES_PERSISTENTES**, pulsa **Preparar clases seleccionadas como entidades**. Esto modifica la configuración del backend, sin cambiar el diagrama UML. Si falta un identificador, el generador añade un identificador técnico Long en el código; utiliza otro nombre si ya existe un atributo `id`.

El compilador aislado preparado en este proyecto admite **Spring Boot 3.5.16**. La web ofrece esa versión y señala las configuraciones antiguas que requieren ajustarla. Las dependencias adicionales necesitan estar disponibles en la caché del servidor. El generador produce estructura y contratos de operaciones; las reglas de negocio todavía requieren implementación y pruebas propias.

## Cómo importar

Selecciona un XMI/XML de hasta 5 MB, elige fusionar o reemplazar, analiza el archivo y revisa los cambios antes de aplicarlos. **Cambiar archivo o estrategia** permite salir de una previsualización fallida o revisar otra opción. Cada análisis diferente usa una solicitud nueva; los reintentos de la misma aplicación conservan su identificador.

La aplicación crea una versión del modelo. Si se guardó la importación pero no se pudo refrescar el lienzo, el mensaje lo distingue y pide reabrir el modelo. Cerrar una importación ya aplicada no intenta cancelarla.

## IA de texto e imagen

- El menú **Servicios de IA** del chat muestra por separado la configuración de texto y de imágenes. **CONFIGURADO** significa que hay una configuración utilizable, no que se haya realizado una inferencia real con esa credencial.
- El texto conserva el respaldo local. Una pausa por cuota del servicio remoto se identifica en el chat.
- La visión informa por separado falta de configuración, credenciales rechazadas, modelo inexistente, cuota agotada y problemas de conexión. Los errores no se atribuyen a un proveedor diferente del configurado.
- Una cuota agotada pausa las llamadas de esa capacidad y configuración durante un minuto. No se prueban consecutivamente otros modelos por un error 429. Texto e imagen tienen pausas independientes.
- Un reintento de imagen después de perder la conexión conserva la clave de envío, para recuperar la solicitud existente. Si la ejecución termina fallida, una nueva tentativa usa otra clave. La foto y los ajustes del diálogo se conservan.
- Las fotos admiten PNG/JPEG de hasta 16 MB. Se revisan formato real y dimensiones antes de decodificarlas en el servidor: máximo 12.000 píxeles por lado y 40 millones de píxeles. La web prepara una copia reducida de hasta 2.560 píxeles por lado, con fondo blanco al convertir transparencias a JPEG.
- Los avances de imagen describen la preparación y el envío reales; no se simulan fases internas del proveedor. Las propuestas se revisan antes de aplicar cambios.
- El formulario de servicios Gemini permite completar URL y modelo; las credenciales siguen cifradas en el servidor y no se devuelven en la consulta de capacidades.

Referencias externas consultadas: [modelos de Gemini](https://aistudio.google.com/docs/models) y [tratamiento de errores y reintentos](https://aistudio.google.com/docs/troubleshooting). La lista de modelos de respaldo solo se usa ante un modelo no encontrado; no garantiza que una cuenta tenga acceso o cuota.

## Espacio de lectura del chat

La entrada usa un selector de intención compacto, un área de texto y una fila de herramientas. Los errores anteriores se agrupan en un bloque desplegable. **↓** lleva a los últimos mensajes. **Ampliar chat** abre una vista amplia; **Reducir chat** o Escape vuelven al panel. Se conservan el borrador y la posición de lectura. En las pruebas de escritorio y móvil, el área de mensajes ocupa más de la mitad del panel lateral.

## Procedencia y verificación

Cambios sobre el código existente de [generación web](../frontend/src/features/interoperabilidad/components/GenerarBackendDialog.tsx), [importación web](../frontend/src/features/interoperabilidad/components/ImportarXmiDialog.tsx), [validación](../backend/src/main/java/bo/edu/uml/validacion/servicio/ValidacionServicio.java), [conector IA](../backend/src/main/java/bo/edu/uml/ia/servicio/ConectorIaRemoto.java) y sus interfaces asociadas. Se conservaron los cambios previos en un respaldo local bajo `.qa/2026-11-13_respaldo_flujos/` y `.qa/2026-11-13_estado_previo.patch`.

Pruebas reproducibles:

```powershell
# Backend: PostgreSQL ficticio exclusivo, sin tocar la base de uso diario.
.\core\docs\pruebas\2026-11-13_probar_flujos.ps1

# Web: con Vite disponible en la URL indicada; API simulada en estas regresiones.
cd core\frontend
$env:UML_E2E_URL = 'http://127.0.0.1:5189'
npm run test:e2e -- tests/2026-11-13_flujos_importacion_generacion_ia.spec.ts tests/2026-11-11_chat_fotos_regresion.spec.ts
npm run build
```

La integración comprueba validación histórica, regeneración desde la misma configuración, descarga del ZIP anterior, pertenencia de versiones y conservación del modelo actual. Los ensayos de IA usan proveedores HTTP simulados y credenciales ficticias; falta comprobar disponibilidad y cuota de las credenciales reales en cada entorno.

Resultado local actualizado: **58 pruebas de backend aprobadas**, **nueve recorridos de navegador aprobados**, compilación de producción de la web y empaquetado del backend correctos. Se verificó también que repetir la migración conserva las cantidades de configuraciones, versiones, archivos y validaciones. Evidencia local: `.qa/2026-11-13_pruebas_generacion_independiente.log`, `core/backend/target/surefire-reports/` y `core/frontend/test-results/resultado.json`. Capturas: [panel del chat](../../.qa/2026-11-13_chat_panel.png), [chat ampliado](../../.qa/2026-11-13_chat_ampliado_escritorio.png) y [chat en móvil](../../.qa/2026-11-13_chat_movil.png).

Esta revisión requiere aplicar [la migración 12](migraciones/2026-11-13_12_generacion_independiente.sql) con la cuenta propietaria del esquema. La transacción elimina la restricción de nombre duplicado y actualiza las reglas de generación y descarga; conserva las configuraciones, versiones, archivos y validaciones existentes. Las instalaciones nuevas y las pruebas incluyen esta migración.

Después de aplicarla, reinicia el backend con el JAR actualizado y usa la web actualizada. Una sesión que siga usando el servidor anterior seguirá teniendo el comportamiento anterior. La aplicación de esta migración a una base de uso real depende de identificar el entorno y de disponer de una conexión con permisos de propietario; no se ha cambiado un despliegue remoto desde esta tarea.
