# Incremento 11 — Contrato de configuración segura de servicios de plataforma

Fecha: 2026-09-20. Alcance funcional: **CU20 del catálogo (ficha detallada CU21) — Configurar Servicios de la Plataforma**. La diferencia de numeración se conserva explícita y no se atribuye al docente una corrección no confirmada.

## Alcance

El módulo permite a una cuenta con el permiso global `CONFIGURAR_SERVICIOS` registrar, consultar, filtrar, actualizar, comprobar, activar y desactivar proveedores para interpretación de texto, transcripción de voz y análisis de imágenes. La configuración activa se integra con P04, pero este incremento no amplía las capacidades semánticas de los motores.

Los tipos admitidos son `IA_TEXTO`, `VOZ_A_TEXTO` e `IA_IMAGEN`. Los adaptadores implementados son `LOCAL`, `OPENAI`, `GEMINI` y `OLLAMA`. `LOCAL` representa el respaldo determinista sin red y no requiere URL.

## Mapeo al esquema vigente

| Información funcional | Persistencia existente |
|---|---|
| Nombre, proveedor, tipo, URL, parámetros y estado | `servicio_plataforma` |
| Credencial protegida y versión de clave | `secreto_servicio` |
| Resultado, latencia, detalle, actor y fecha de cada prueba | `comprobacion_servicio` |
| Uso en una operación inteligente en curso | `solicitud_inteligente.id_servicio` y `estado = 'PROCESANDO'` |
| Auditoría de las decisiones administrativas | bitácora estructurada `auditoria`, emitida después del commit |

No se modifica el esquema base original. La migración idempotente `2026-09-20_06_servicios_plataforma_seguridad.sql` asegura el permiso del superadministrador, la unicidad del nombre sin distinguir mayúsculas y los índices de consulta.

## API administrativa

Base: `/api/administracion/servicios`.

| Método y ruta | Resultado |
|---|---|
| `GET /` | Lista paginada; filtros opcionales `nombre`, `proveedor`, `tipo` y `estado` |
| `GET /{id}` | Detalle, parámetros no sensibles y última comprobación |
| `POST /` | Valida, prueba y registra una configuración |
| `PUT /{id}` | Prueba y actualiza con control optimista mediante `revision` |
| `POST /{id}/verificar` | Registra una nueva comprobación y actualiza disponibilidad |
| `POST /{id}/estado` | Activa o desactiva con control optimista y confirmación de impacto |

Todos los endpoints exigen la sesión opaca vigente, protección CSRF de doble envío para escrituras y `@AuthenticationPrincipal Identidad`. La autorización se vuelve a consultar en PostgreSQL para no confiar en permisos antiguos conservados dentro de una sesión.

Las respuestas nunca contienen la credencial ni el contenido cifrado. Solo exponen `tieneCredencial`. La revisión corresponde a `actualizado_en`; una escritura con revisión anterior termina en conflicto y obliga a recargar.

## Protección de credenciales

- La clave maestra se recibe mediante `UML_SERVICIOS_MASTER_KEY`, como Base64 de exactamente 32 bytes. No existe valor productivo predeterminado ni se persiste la clave.
- `UML_SERVICIOS_KEY_VERSION` identifica la versión operativa de la clave y participa como dato autenticado adicional.
- Cada secreto se cifra con AES-256-GCM, nonce aleatorio de 96 bits y etiqueta de autenticación de 128 bits.
- Una rotación retira el secreto anterior y crea uno nuevo; la restricción existente mantiene una sola credencial activa por servicio.
- La ausencia de clave maestra impide almacenar o usar una credencial y revierte la transacción completa.
- La configuración JSON rechaza claves con nombres sensibles como `token`, `password`, `secret`, `apiKey`, `authorization` o `credencial`; los secretos solo se aceptan por el campo dedicado.

La rotación de la clave maestra entre versiones requiere recifrar las credenciales antes de cambiar `UML_SERVICIOS_KEY_VERSION`. El sistema falla de forma cerrada si el algoritmo, formato, etiqueta o versión no coinciden.

## Validación de conexión y estados

Un servicio remoto requiere HTTPS, host explícito y ausencia de credenciales dentro de la URL. Se rechazan `localhost`, sufijos internos, direcciones loopback, privadas, link-local y multicast. No se siguen redirecciones y las comprobaciones tienen límites de conexión y lectura.

- Respuesta 2xx: comprobación `DISPONIBLE`; el servicio queda `ACTIVO`, salvo que estuviera desactivado deliberadamente.
- Respuesta 401/403: la credencial se considera inválida y una creación o actualización se rechaza sin persistir cambios parciales.
- Respuesta temporal 408/429/5xx: comprobación `DEGRADADO`.
- Host no resoluble, interrupción o ausencia de respuesta: comprobación `NO_DISPONIBLE`; una nueva configuración se conserva como `SIN_VERIFICAR` para permitir reintento.
- Una comprobación manual fallida coloca un servicio habilitado en `NO_DISPONIBLE`; no activa automáticamente uno que el administrador dejó `INACTIVO`.

La desactivación requiere confirmación explícita. Si una `solicitud_inteligente` vinculada continúa en `PROCESANDO`, se devuelve conflicto y no se cambia el estado. La reactivación exige una última comprobación disponible.

## Consumo por P04

`ResolverServicioActivo` selecciona la configuración `ACTIVO` más recientemente actualizada para cada tipo. `ConectorIaRemoto` la usa antes que las variables `UML_IA_*` heredadas. El secreto se descifra solo cuando el adaptador remoto va a utilizarlo; el proveedor `LOCAL` no accede a la clave ni realiza tráfico de red.

Las variables `UML_IA_*` se mantienen como compatibilidad cuando no existe un servicio activo en PostgreSQL. Las credenciales nunca se escriben en registros de aplicación, respuestas HTTP ni documentos versionados.

## Límites operativos

- La aplicación no genera ni custodia la clave maestra; el despliegue debe suministrarla mediante un gestor de secretos o variable protegida.
- La comprobación confirma conectividad y autenticación básica, no garantiza cuota futura ni calidad del modelo del proveedor.
- Solo se permite un servicio activo efectivo por tipo: si existen varios registros `ACTIVO`, se usa el actualizado más recientemente. Una política administrativa de prioridad explícita queda fuera de este incremento.
- Las pruebas automatizadas usan una clave ficticia y PostgreSQL aislado en `127.0.0.1:55433`; nunca deben apuntar al puerto Docker `55432`.
