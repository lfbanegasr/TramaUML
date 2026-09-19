# Seguimiento del incremento 09 — P04 Asistencia Inteligente

Fecha: 2026-09-19
Casos de uso: CU21, CU22, CU23, CU24 y CU25
Rama: `codex/p04-asistencia-inteligente`
Línea base protegida: commit `4a50bcf` (incrementos 06–08)

## Resultado

P04 queda integrado verticalmente en el núcleo y el editor. La IA nunca modifica el modelo al recibir una entrada: primero registra una solicitud, genera una propuesta persistida y muestra sus operaciones para decisión humana.

| Caso | Resultado implementado |
|---|---|
| CU21 Texto | Registro de solicitud, motor híbrido con fallback local determinista, UUID preasignados, operaciones y dependencias |
| CU22 Voz | Web Speech API, fallback MediaRecorder, almacenamiento `AUDIO_VOZ`, transcriptor configurable y fallo explícito si falta |
| CU23 Imagen | PNG/JPG, límites y firma real, almacenamiento `IMAGEN_DIAGRAMA`, adaptador multimodal configurable y coordenadas sin solapamiento |
| CU24 Revisión | Propuesta vigente/detalle, resumen, confianza, operaciones expandibles, justificaciones y dependencias |
| CU25 Decisión | Rechazo trazable o aplicación en una sola revisión/versión mediante `EdicionTransaccional` |

## Decisiones verificadas

- JDBC puro con `NamedParameterJdbcTemplate`; no se añadió JPA.
- DTOs Java inmutables mediante `record`.
- Sesión opaca, principal autenticado y CSRF de doble envío en todas las mutaciones.
- Permisos `SOLICITAR_IA`, `REVISAR_PROPUESTA_IA` y `APLICAR_PROPUESTA_IA` asignados de manera idempotente a los roles editores reales.
- La justificación reside como mensaje estructurado `JUSTIFICACION`; no se añadió una columna inexistente.
- Atributos dependen de su clase y las relaciones de ambas clases.
- Una revisión divergente deja la propuesta `DESACTUALIZADO`.
- Un fallo intermedio revierte elementos, revisión, versión y notificación; una propuesta decidida no puede aplicarse otra vez.
- La notificación WebSocket ocurre por el mecanismo existente después del commit.

## Proveedores

`LOCAL` es el modo predeterminado y funciona sin conexión. `OPENAI`, `GEMINI` y `OLLAMA` se habilitan únicamente con variables `UML_IA_*`; no hay URL, modelo ni credencial externa incorporada al repositorio. Si el proveedor no está configurado o falla, texto conserva el respaldo local y voz/imagen registran un fallo trazable.

No se probaron servicios pagos o cuentas externas porque no se proporcionaron credenciales. Sí se probaron el contrato local, la selección del resultado remoto mediante un doble de prueba y todos los caminos de ausencia de proveedor.

## Evidencia de cierre

| Verificación | Resultado |
|---|---|
| Google Java Format AOSP | Código 0 |
| Núcleo aislado PostgreSQL `55433` | 95 pruebas, 0 fallos |
| Reinicio autónomo del clúster de pruebas | Verificado desde servidor detenido |
| Prettier `format:check` | Código 0 |
| TypeScript + Vite `build` | Código 0 |
| Playwright en base aislada | 19 pruebas, 0 fallos |

El script aislado valida que el servidor conectado pertenece a `.qa/postgres/data`. El puerto Docker `55432` no se usa ni se modifica.

## Límites conscientes

- El parser local crea clases, atributos y relaciones con cardinalidad; intenciones más amplias requieren un proveedor remoto o ampliar la gramática en otro incremento.
- El reconocimiento visual local no finge OCR. Sin adaptador funcional, conserva el archivo y responde `FALLIDO`.
- Ollama se usa para texto/visión según su endpoint de generación; la transcripción requiere un proveedor con contrato de audio compatible.
- El esquema vigente no admite `MOVER` en `operacion_propuesta`; las coordenadas se incluyen en `CREAR CLASE`.
