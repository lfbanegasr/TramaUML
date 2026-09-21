# Contrato del Incremento 13 — Importación y exportación XMI completas

Fecha: 2026-09-20  
Alcance: **CU26 del catálogo (ficha CU27) — Importar desde Enterprise Architect** y **CU27 del catálogo (ficha CU28) — Exportar para Enterprise Architect**.

## Decisiones de contrato

- El formato de intercambio es XMI 2.1 con UML 2.1. Se aceptan documentos XMI 2.x al importar y se normalizan al perfil 2.1.
- La exportación lee una instantánea inmutable de `version_modelo_uml`; no exporta el estado mutable a mitad de edición.
- La importación tiene dos fases: carga/análisis y aplicación explícita. Analizar nunca modifica el modelo.
- `FUSIONAR` conserva los UUID vigentes cuando encuentra paquetes, clases, atributos, operaciones o relaciones equivalentes. `REEMPLAZAR` elimina el contenido vigente y crea el contenido del documento como una sola decisión transaccional.
- Las operaciones de la vista previa reciben UUID deterministas preasignados a partir del intercambio. Las dependencias se ordenan paquete → clase → atributo/operación → relación; las eliminaciones se ordenan relación → clase → paquete.

## Persistencia real

Se reutilizan las tablas existentes, sin columnas inventadas ni cambios de esquema:

| Información | Persistencia |
|---|---|
| Archivo exportado | `archivo_almacenado.categoria = 'SALIDA_XMI'`, `contenido bytea` |
| Archivo importado | `archivo_almacenado.categoria = 'ENTRADA_XMI'`, `contenido bytea` |
| Intercambio | `intercambio_xmi`, `direccion`, `variante_xmi`, `estrategia_fusion`, `estado`, referencias de archivo y versión |
| Alcance y solicitud | `intercambio_xmi.ambito` JSONB: `idSolicitud`, paquete, subpaquetes y `revisionBase` |
| Conteos, advertencias y operaciones | `intercambio_xmi.informe` JSONB; cada operación contiene `orden`, `operacion`, `entidad`, UUID y `datos` |
| Fallo trazable | `intercambio_xmi.detalle_error` y estado `FALLIDO` |

Las justificaciones o advertencias se mantienen dentro de `informe`/`datos`; no se agrega una columna que el esquema no define.

## API

- `POST /api/modelos/{modelo}/xmi/exportar`: genera el XMI de la versión vigente o de una versión histórica, opcionalmente limitado a un paquete y sus subpaquetes. Devuelve conteos, versión, archivo y advertencias.
- `GET /api/modelos/{modelo}/xmi/{intercambio}/descargar`: descarga únicamente un intercambio de salida autorizado.
- `POST /api/modelos/{modelo}/xmi/importaciones`: multipart `archivo`, `idSolicitud` e `estrategia` (`FUSIONAR` o `REEMPLAZAR`). Persiste el archivo, analiza de forma segura y devuelve la previsualización.
- `GET /api/modelos/{modelo}/xmi/importaciones/{id}`: consulta la previsualización persistida y su vigencia.
- `POST /api/modelos/{modelo}/xmi/importaciones/{id}/aplicar`: exige confirmación, sesión de edición, `revisionBase` y `idSolicitud`; crea una sola revisión/versión mediante `EdicionTransaccional`.
- `POST /api/modelos/{modelo}/xmi/importaciones/{id}/cancelar`: cancela una previsualización todavía no aplicada.

Todas las rutas usan sesión opaca, CSRF de doble envío, `@AuthenticationPrincipal Identidad`, autorización por proyecto y aislamiento de modelo. La aplicación comprueba nuevamente la revisión, bloquea el intercambio y es idempotente para la misma solicitud.

## Análisis seguro y límites

El DOM se configura con procesamiento seguro, DTD y entidades externas deshabilitados, sin acceso a recursos externos ni resolución de esquemas. Se rechazan XML mal formados, XXE, raíces que no sean XMI 2.x y documentos vacíos. Hay límites de 5 MiB por archivo, 10 000 elementos XML y 500 elementos UML importables.

Se reconocen paquetes, clases, atributos, operaciones/parámetros, asociaciones con cardinalidades, agregación/composición, dependencias y generalizaciones. Las estructuras no soportadas o relaciones sin dos extremos se informan como advertencias u omisiones explícitas. Las coordenadas se distribuyen en una cuadrícula determinista para evitar solapamientos iniciales.

El exportador declara `xmi:version="2.1"`, namespaces OMG UML/XMI, documentación de la plataforma y asociaciones/generalizaciones/dependencias con referencias válidas. La compatibilidad se verifica con round-trip del analizador y fixtures de Enterprise Architect; no se afirma una validación GUI contra una instalación comercial que no está disponible en el entorno.

## Evidencia automatizada

- `XmiTest`: 9 pruebas unitarias, incluida ronda exportar→analizar, generalización/dependencia válidas y rechazo XXE.
- `NucleoIntegracionTest`: 75 escenarios de integración, incluidos revisión sin mutación, FUSIONAR, REEMPLAZAR, aplicación atómica, rollback, obsolescencia, doble aplicación, archivo inseguro, alcance histórico por paquete y permisos editor/lector.
- Suite Maven del núcleo: **116 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas**, sobre bases nuevas en `127.0.0.1:55433`.
- Frontend: `npm run format:check` aprobado; `tsc --noEmit` y `vite build --configLoader runner` aprobados. La compilación estándar puede requerir permisos para escribir `tsconfig.tsbuildinfo` y `node_modules/.vite-temp`.
- Playwright incluye exportación/descarga e importación con previsualización, confirmación y recarga del canvas; requiere ejecutarse con el lanzador aislado de navegador.

## Fuera de alcance declarado

No se implementa todavía un perfil de importación específico para cada variante propietaria de EA ni un proveedor OCR/multimodal externo. Los metadatos desconocidos se omiten con advertencia y el usuario debe revisar la previsualización antes de aplicar.

