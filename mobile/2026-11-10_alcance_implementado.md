# Alcance del cliente Android — 2026-11-10

Implementación inicial de Flutter dentro de `mobile`, conectada a los contratos existentes de la plataforma UML. Destino de uso: Redmi Note 13, Android ARM64.

| Requisito | Resultado |
|---|---|
| Acceso con cuenta existente | Cookie opaca por dispositivo, CSRF, restauración y cierre remoto |
| Consulta de proyectos | Búsqueda, paginación, permisos y apertura de modelo |
| Crear proyecto | Código, nombre y descripción; apertura posterior del agente |
| Visualizar UML | Clases, relaciones, multiplicidades, atributos y operaciones |
| Interacción móvil | Zoom, desplazamiento, ajuste y búsqueda; ficha completa por clase |
| Agente por texto | API real de solicitudes y propuestas |
| Agente por voz | Dictado nativo → revisión de transcripción → solicitud por texto |
| Agente por imagen | Cámara/galería → previsualización → PNG/JPG multipart |
| Revisar propuestas | Selección de operaciones, dependencias y valores actuales/propuestos |
| Aplicar/rechazar | Revisión optimista y confirmación mediante backend existente |
| Colaboración | WebSocket con cookie, heartbeat, presencia, foco y actualización del modelo |
| Recuperación | Reconexión, borrador de texto y últimos 80 mensajes locales por cuenta/proyecto |
| Equipo y actividad | Lectura de endpoints existentes |

## Evoluciones pendientes

- Memoria conversacional en el servidor y conversaciones compartidas entre colaboradores.
- Respuestas a preguntas abiertas sin producir propuestas de modificación.
- Planificación compleja con contexto remoto completo del proyecto.
- Corrección libre de valores de cada operación desde formularios móviles: esta versión permite seleccionar o descartar; las correcciones se solicitan mediante una propuesta nueva.
- Borradores de fotografías persistentes después de cerrar el proceso: la previsualización actual se mantiene en memoria y se recupera el resultado perdido del selector Android cuando corresponde.
- Edición manual completa, administración, gestión de miembros, historial/restauración, intercambio Enterprise Architect y generación del backend desde el móvil.
- Validación física de cámara, dictado, comportamiento de batería/segundo plano y rendimiento de diagramas grandes en el Redmi.

No se declara completada la especificación futura del agente. Esta entrega implementa el cliente de las capacidades del backend disponible y no modifica su lógica.

## Criterios para comprobar en el dispositivo

1. Abrir la misma cuenta en escritorio y Android sin invalidar ninguno de los accesos.
2. Abrir un proyecto y consultar clases con zoom, búsqueda y ficha.
3. Dictar una instrucción, corregirla y enviarla de forma explícita.
4. Fotografiar un diagrama y revisar la propuesta antes de modificar el modelo.
5. Aplicar una propuesta y observar una nueva revisión en ambos clientes.
6. Modificar desde escritorio una revisión usada por una propuesta Android; comprobar rechazo por obsolescencia.
7. Pasar Android a segundo plano y volver; comprobar resincronización.
8. Salir de Android sin interrumpir el WebSocket de escritorio.

Fuentes: DTO/controladores/servicios de `core/backend/src/main/java/bo/edu/uml/`, contratos frontend existentes y requisitos acordados en la conversación.
