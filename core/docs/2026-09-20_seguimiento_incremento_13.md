# Seguimiento del Incremento 13 — Importación y exportación XMI completas

Fecha de cierre técnico: 2026-09-20  
Rama: `codex/incremento-13-xmi-completo`  
Versión: backend `0.13.0-SNAPSHOT`, frontend `0.13.0`

## Resultado

Se cerró el flujo de interoperabilidad XMI de extremo a extremo. La plataforma puede exportar una versión histórica o el modelo vigente con alcance por paquete y subpaquetes, y puede cargar, analizar, revisar, fusionar o reemplazar un documento XMI antes de modificar el modelo.

El modelo no cambia durante la carga. La aplicación requiere una confirmación explícita y ejecuta todas las operaciones en una sola revisión/versión mediante el motor transaccional existente. Tras el commit se conserva la notificación WebSocket y la idempotencia de solicitud.

## Backend implementado

- `bo.edu.uml.xmi.dto.XmiDto`: contratos inmutables para exportación, carga, previsualización, aplicación y cancelación.
- `AnalizadorXmi21Service`: parser DOM seguro con límites, rechazo de DTD/XXE, UUID deterministas, cardinalidades, operaciones/parámetros, asociaciones, dependencias y generalizaciones.
- `XmiRepositorio`: instantáneas históricas, almacenamiento binario `ENTRADA_XMI`/`SALIDA_XMI`, intercambio XMI, informe JSONB, consulta/bloqueo/idempotencia y estados de análisis/aplicación.
- `XmiServicio`: exportación por versión/paquete, análisis sin efectos, estrategias `FUSIONAR`/`REEMPLAZAR`, bloqueadores, revisión de `revisionBase`, aplicación atómica y cancelación.
- `XmiControlador`: endpoints REST protegidos por permisos, sesión y CSRF.
- `GeneradorXmi21Service`: XMI 2.1 con documentación del exportador 0.13.0, relaciones OMG válidas y generalizaciones/dependencias referenciadas.

## Frontend implementado

- Cliente HTTP tipado para carga, consulta, aplicación, cancelación y exportación.
- `ImportarXmiDialog`: selección/arrastre de XML, límite de 5 MiB, estrategia, advertencias, bloqueadores, operaciones y confirmación.
- `ExportarXmiDialog`: selección de versión histórica, paquete/subpaquetes, descripción, advertencias, conteos y descarga.
- Integración de ambos flujos en la barra del editor, recarga del canvas después de aplicar y estilos de revisión.

## Verificación

Todas las pruebas con persistencia usaron bases nuevas del clúster aislado `127.0.0.1:55433`; no se utilizó PostgreSQL `55432`.

| Control | Resultado |
|---|---:|
| Google Java Format AOSP | Aprobado |
| Maven núcleo | 116 aprobadas, 0 fallos, 0 errores, 0 omitidas |
| Prettier `format:check` | Aprobado |
| TypeScript (`tsc --noEmit`) | Aprobado |
| Vite (`--configLoader runner`, salida temporal) | Aprobado |
| Playwright | Preparado; debe correrse con `2026-09-14_probar_navegador.ps1` |
| Jar Spring Boot | `plataforma-0.13.0-SNAPSHOT.jar` empaquetado |

## Límites conocidos

- No se dispone de Enterprise Architect comercial para una prueba GUI; la compatibilidad se cubre con el perfil XMI 2.1, fixtures, round-trip y referencias válidas.
- EA puede emitir metadatos propietarios o asociaciones con estructuras distintas; se omiten de forma explícita y quedan en advertencias, nunca se aplican silenciosamente.
- `FUSIONAR` identifica clases por nombre dentro del modelo y relaciones por sus extremos/tipo/nombre; una futura mejora podrá exigir claves externas más estrictas para modelos que permitan nombres repetidos.

## Próximo incremento

El siguiente trabajo es el **Incremento 14: configuración avanzada de generación**, centrado en selección de elementos, dependencias, mapeos y versiones del backend generado. La validación/compilación controlada y la descarga condicionada seguirán en el Incremento 15.

