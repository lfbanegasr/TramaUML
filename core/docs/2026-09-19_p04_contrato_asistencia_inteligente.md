# P04 — Contrato técnico de Asistencia Inteligente (CU21–CU25)

Fecha: 2026-09-19. Este contrato adapta la solicitud funcional al esquema PostgreSQL vigente sin renombrar ni duplicar columnas.

## Decisiones de persistencia

| Concepto funcional | Representación vigente |
|---|---|
| Orden de una operación | `operacion_propuesta.numero_secuencia` |
| Entidad afectada | `operacion_propuesta.id_entidad_destino` |
| Datos interpretados | `operacion_propuesta.valor_propuesto` |
| Valor previo para el diff | `operacion_propuesta.valor_actual` |
| Justificación | Entrada estructurada `JUSTIFICACION` dentro de `mensajes_validacion` |
| Selección para aplicar | `operacion_propuesta.esta_seleccionado` |
| Dependencias | `dependencia_operacion_propuesta` |

Las operaciones P04 se guardan con entidades `CLASE`, `ATRIBUTO`, `OPERACION`, `RELACION` o `PAQUETE`, iguales al contrato de `ModeladoDto.Accion`. El esquema vigente solo admite `CREAR`, `ACTUALIZAR` y `ELIMINAR` dentro de una propuesta. Las coordenadas iniciales forman parte de una operación `CREAR CLASE`; no se persiste una operación `MOVER` separada.

## Seguridad y compatibilidad

- `SOLICITAR_IA`: crear solicitudes de texto, voz o imagen.
- `REVISAR_PROPUESTA_IA`: consultar propuestas pendientes y sus operaciones.
- `APLICAR_PROPUESTA_IA`: aplicar o rechazar una propuesta.
- El trigger histórico de propuestas comprueba `REVISAR_PROPUESTA`; la migración conserva ese permiso para los roles editores además de los permisos nuevos.
- Los nombres funcionales LÍDER y COLABORADOR corresponden en el catálogo actual a `PROPIETARIO_PROYECTO` y `EDITOR_PROYECTO`.

Todos los endpoints usan la sesión opaca existente, CSRF de doble envío y `@AuthenticationPrincipal Identidad`. Un modelo ajeno se oculta con 404.

## Aplicación transaccional

Una propuesta se aplica solamente cuando su `revision_base` coincide con `modelo_uml.revision_actual`. Sus operaciones seleccionadas y válidas se transforman en un único `ModeladoDto.Cambio` y se ejecutan mediante `EdicionTransaccional` dentro de la misma transacción que marca la propuesta como `APLICADO`. Esto produce una sola revisión, una sola versión histórica y el evento WebSocket `MODELO_CONFIRMADO` después del commit.

Si la revisión cambió, la propuesta pasa a `DESACTUALIZADO` y el modelo no se modifica. Si cualquier operación falla, toda la aplicación se revierte.

## Motores y fallos trazables

El motor local de texto es determinista y funciona sin internet. El modo predeterminado es `LOCAL` y no genera tráfico de red. El puerto `ProveedorIa` permite activar, de forma opt-in, adaptadores `OPENAI`, `GEMINI` u `OLLAMA`; cualquier respuesta remota inválida vuelve al parser local.

Voz e imagen conservan siempre el binario original. Cuando no existe un proveedor de transcripción u OCR/multimodal funcional, la solicitud termina en `FALLIDO` con detalle explícito; nunca se inventa una transcripción ni contenido visual.

### Configuración externa opcional

| Variable | Uso |
|---|---|
| `UML_IA_PROVEEDOR` | `LOCAL`, `OPENAI`, `GEMINI` u `OLLAMA` |
| `UML_IA_CLAVE` | Credencial leída solo desde el entorno; nunca se persiste |
| `UML_IA_URL_TEXTO` / `UML_IA_MODELO_TEXTO` | Normalización de instrucciones de texto |
| `UML_IA_URL_VISION` / `UML_IA_MODELO_VISION` | Reconocimiento multimodal de diagramas |
| `UML_IA_URL_TRANSCRIPCION` / `UML_IA_MODELO_TRANSCRIPCION` | Transcripción de audio |

Las URL son explícitas para permitir OpenAI, Gemini, Ollama local o servicios compatibles sin fijar un destino externo en el código. Para OpenAI, texto/visión usan un endpoint compatible con Chat Completions y voz usa Transcriptions. Gemini usa `generateContent` con datos binarios en línea. Ollama usa `/api/generate` para texto y modelos visuales; este adaptador no atribuye a Ollama una transcripción de audio que su contrato no ofrezca.

Referencias del contrato remoto: [OpenAI Audio Transcriptions](https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create), [Gemini generateContent](https://ai.google.dev/api/generate-content) y [Ollama generate](https://docs.ollama.com/api/generate).
