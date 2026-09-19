# Incremento 06 — Historial del modelo y restauración de versión (CU19 + CU20)

Fecha: 2026-09-15. Backend de esta plataforma: 0.6.0-SNAPSHOT; frontend: 0.6.0. Este incremento continúa el núcleo 0.5 y añade los módulos completos de consulta de historial (CU19) y restauración transaccional de versiones (CU20).

## Fuentes y alcance

Se utilizaron el [seguimiento 05](2026-09-15_seguimiento_incremento_05.md), los [contratos 05](2026-09-15_incremento_05_contratos.md), la [base técnica de historial/restauración](2026-09-15_base_tecnica_historial_restauracion.md) y las especificaciones detalladas extraídas de `Documentacion.docx`.

Se conserva la numeración de las fichas detalladas: **CU19 — Consultar historial del modelo** y **CU20 — Restaurar versión del modelo**.

## Contratos de API REST

Todos los endpoints exigen autenticación por sesión opaca. Las operaciones de lectura requieren `VER_HISTORIAL_MODELO` (o administrador). Las operaciones de restauración requieren `RESTAURAR_MODELO` (asignado al rol LÍDER o administrador). Los modelos ajenos responden `404 Not Found`.

| Método y ruta | Contrato y parámetros | Descripción |
|---|---|---|
| GET `/api/modelos/{modelo}/versiones` | `limite` (int, default 20), `cursor` (int, número_versión), `autor` (UUID), `desde` (LocalDate), `hasta` (LocalDate) | Lista paginada descendente por cursor de versiones con metadatos resumidos y total. |
| GET `/api/modelos/{modelo}/versiones/{version}` | Ruta con UUID de la versión | Devuelve metadatos y la instantánea JSONB validada (`formato_version: 2`). 404 si la versión no pertenece al modelo. |
| GET `/api/modelos/{modelo}/comparaciones` | `desde` (UUID), `hasta` (UUID) | Compara dos versiones del mismo modelo y devuelve lista de elementos AGREGADOS, ELIMINADOS y MODIFICADOS con detalle de propiedades. |
| POST `/api/modelos/{modelo}/restauraciones/previsualizaciones` | Cuerpo JSON: `{ "idVersionOrigen": "uuid" }` | Previsualiza la restauración sin escribir en BD. Informa bloqueadores (sesiones editoras activas, conflictos abiertos) y total estimado de elementos. |
| POST `/api/modelos/{modelo}/restauraciones` | Header CSRF + Cuerpo JSON: `{ "idSolicitudRestauracion": "uuid", "idVersionOrigen": "uuid", "revisionEsperada": n, "motivo": "...", "confirmar": true }` | Ejecuta el protocolo transaccional de restauración. Devuelve la nueva revisión, nueva versión y bandera de repetición idempotente. |

## Protocolo transaccional de restauración (CU20)

La restauración opera dentro de una única transacción y conexión de base de datos bajo aislamiento estricto:

```
1. Autorizar actor (VER_HISTORIAL_MODELO + RESTAURAR_MODELO, proyecto ACTIVO, modelo no ARCHIVADO).
2. Bloquear revisión actual del modelo (SELECT FOR UPDATE).
3. Verificar idempotencia por idSolicitudRestauracion.
4. Validar revisionEsperada contra revisión vigente (409 si hubo cambios concurrentes).
5. Comprobar bloqueadores: 0 sesiones colaborativas activas en modo EDITOR y 0 conflictos ABIERTOS (409 con detalle si existen).
6. Leer y validar snapshot de la versión origen.
7. Iniciar edición transaccional: invocar uml_colaborativo.iniciar_edicion_modelo().
8. Reconstruir estado en orden seguro:
   a. Eliminación lógica en orden inverso de dependencias (relaciones → parámetros → operaciones → atributos → clases → paquetes).
   b. Restauración/upsert en orden topológico (paquetes raíz primero → clases → atributos → operaciones → parámetros → relaciones → posiciones).
9. Validación semántica: evaluar MotorSemantico; verificar que no introduce errores nuevos.
10. Guardar nueva versión invocando guardar_version_modelo_restaurada() con id_version_restaurada = idVersionOrigen.
11. Registrar cambio_modelo con tipo RESTAURAR y estado APLICADO.
12. Registrar evento en auditoría/actividad con tipo RESTAURAR_MODELO.
13. Commit.
14. Publicar evento AFTER_COMMIT ModeloConfirmado (difusión WebSocket a clientes conectados).
```

## Persistencia y migración

La migración [2026-09-15_03_historial_permisos.sql](migraciones/2026-09-15_03_historial_permisos.sql):
- Inserta los permisos `VER_HISTORIAL_MODELO` y `RESTAURAR_MODELO` en `permiso_disponible`.
- Asigna `VER_HISTORIAL_MODELO` al rol predeterminado `COLABORADOR`.
- Asigna `VER_HISTORIAL_MODELO` y `RESTAURAR_MODELO` al rol predeterminado `LIDER`.
- Define la función auxiliar con privilegios de seguridad `uml_colaborativo.guardar_version_modelo_restaurada(p_id_modelo, p_revision_reservada, p_actor, p_resumen, p_id_version_origen)`.

## Interfaz de usuario (React)

- **Acceso:** Botón "Historial" en la barra de herramientas del editor UML.
- **HistorialPage:** Lista paginada con cursor, filtros por fecha y autor, estado de validación y badges para versiones originadas por restauración.
- **ComparacionPanel:** Vista comparativa con badges de operaciones (AGREGADO en verde, ELIMINADO en rojo, MODIFICADO en ámbar) y tabla de propiedades con valores anterior/posterior.
- **RestauracionDialog:** Diálogo modal accesible para usuarios con permisos, que previsualiza bloqueadores, solicita motivo obligatorio, exige confirmación explícita y conserva idempotencia en `sessionStorage`.
