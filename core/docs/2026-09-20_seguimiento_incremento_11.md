# Seguimiento consolidado — Incremento 11

Fecha de cierre: 2026-09-20

Rama de trabajo: `codex/incremento-11-servicios-plataforma`

Versión consolidada: backend `0.11.0-SNAPSHOT`, frontend `0.11.0`

Caso cubierto: **CU20 del catálogo (ficha detallada CU21) — Configurar Servicios de la Plataforma**.

## Objetivo cumplido

Se implementó el recorrido administrativo completo para registrar, consultar, filtrar, actualizar, comprobar, activar y desactivar los servicios usados por asistencia inteligente. Las credenciales quedan fuera de las respuestas y de la configuración general, se cifran antes de persistirse y solo se descifran cuando un adaptador remoto activo debe utilizarlas.

## Backend

- Se creó el paquete `bo.edu.uml.servicios` con DTOs inmutables, controlador REST, servicio transaccional, repositorio JDBC, comprobador de conectividad, cifrador y resolvedor de configuración activa.
- Todas las consultas utilizan `NamedParameterJdbcTemplate`; no se añadió JPA.
- Se incorporaron filtros por nombre, proveedor, tipo y estado, paginación y detalle con última verificación.
- Creación y actualización realizan una comprobación previa. Un proveedor no resoluble se conserva como `SIN_VERIFICAR`; una URL interna o insegura se rechaza.
- El control optimista usa `actualizado_en` como revisión y devuelve conflicto frente a una edición obsoleta.
- La desactivación requiere confirmación y se bloquea mientras exista una solicitud inteligente vinculada en estado `PROCESANDO`.
- Cada comprobación conserva resultado, latencia, detalle, actor y fecha. Crear, actualizar, comprobar, activar y desactivar producen auditoría después del commit.
- P04 prefiere la configuración activa en PostgreSQL y mantiene las variables `UML_IA_*` como compatibilidad cuando no existe un servicio activo.

## Seguridad de secretos

- Las credenciales se cifran con AES-256-GCM, nonce aleatorio y etiqueta autenticada.
- La clave maestra se recibe exclusivamente mediante `UML_SERVICIOS_MASTER_KEY`, en Base64 de 32 bytes; no existe una clave productiva predeterminada.
- `UML_SERVICIOS_KEY_VERSION` se valida durante el descifrado y se usa como dato autenticado adicional.
- Las respuestas solo exponen `tieneCredencial`; nunca devuelven el secreto ni su contenido cifrado.
- La rotación retira la credencial anterior y conserva una sola activa. El retiro explícito queda disponible al editar.
- Se rechazan secretos introducidos dentro del JSON general y URLs con credenciales, HTTP, loopback o destinos privados/locales.
- Una clave ausente, una etiqueta alterada o una versión distinta falla de forma cerrada y revierte la operación.

## Persistencia y permisos

- Se reutilizaron `servicio_plataforma`, `secreto_servicio`, `comprobacion_servicio` y la relación existente `solicitud_inteligente.id_servicio`.
- La migración `2026-09-20_06_servicios_plataforma_seguridad.sql` es idempotente, asegura `CONFIGURAR_SERVICIOS` para `SUPERADMINISTRADOR`, impone unicidad normalizada del nombre y agrega el índice de filtros.
- La autorización global se vuelve a leer desde PostgreSQL y todas las escrituras conservan sesión opaca y CSRF de doble envío.
- No se modificó el esquema base original.

## Frontend

- Administración muestra la sección **Servicios** cuando la cuenta posee `CONFIGURAR_SERVICIOS`.
- El panel permite registrar y editar, filtrar, comprobar conexiones y activar o desactivar con confirmación.
- El formulario separa la credencial de los parámetros JSON, usa entrada protegida y advierte que el valor no volverá a mostrarse.
- La lista muestra estado, última comprobación, latencia y solo el indicador `Protegida` o `No configurada`.
- Las respuestas de comprobación y cambio de estado actualizan inmediatamente la revisión local para evitar acciones consecutivas con datos obsoletos.

## Versionado y operación

- Maven y el artefacto ejecutable quedaron en `0.11.0-SNAPSHOT`.
- El paquete frontend y su archivo de bloqueo quedaron en `0.11.0`.
- Los metadatos de XMI y generación se alinearon con `0.11.0`.
- Los lanzadores apuntan al artefacto vigente.
- El lanzador de navegador suministra una clave AES ficticia solo durante la prueba y restaura el entorno al finalizar.

## Verificación final

Todas las verificaciones con datos persistentes utilizaron bases ficticias nuevas en PostgreSQL `127.0.0.1:55433`. No se estableció conexión con el PostgreSQL de Docker del puerto `55432`.

| Control | Resultado |
|---|---:|
| Google Java Format AOSP | Aprobado |
| Pruebas Maven del núcleo | 104 aprobadas, 0 fallos, 0 errores, 0 omitidas |
| Prettier `format:check` | Aprobado |
| Compilación TypeScript/Vite | Aprobada |
| Pruebas Playwright | 23 aprobadas |
| Empaquetado Spring Boot | Aprobado |

La cobertura nueva comprueba cifrado y manipulación, ausencia de clave, rotación, no exposición, duplicidad, filtros, permisos, CSRF, revisión obsoleta, URL privada, proveedor no disponible, bloqueo por solicitud en curso y el recorrido completo de interfaz. Vite mantiene la advertencia no bloqueante ya conocida porque el paquete principal minificado supera 500 kB.

## Límites conservados

- Los adaptadores remotos no se probaron con credenciales reales ni consumieron proveedores externos; las pruebas usan `LOCAL` o el dominio reservado `.invalid`.
- La clave maestra debe ser administrada por el entorno de despliegue. La rotación entre versiones requiere recifrar secretos antes de retirar la clave anterior.
- Si hay varios servicios `ACTIVO` del mismo tipo, P04 selecciona el actualizado más recientemente; una prioridad administrativa explícita queda como posible mejora.
- El cierre funcional de texto, voz, imagen y revisión editable pertenece al Incremento 12.

## Próximo avance autorizado por el plan

El Incremento 12 debe cerrar las brechas restantes de asistencia inteligente: operaciones más amplias del motor de texto, reproducción y límites de voz, proveedor de transcripción validado, OCR/visión funcional, edición y selección de operaciones propuestas y revalidación antes de aplicar. Su desarrollo comenzará únicamente con autorización expresa.
