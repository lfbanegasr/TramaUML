-- Incremento 11: endurecimiento idempotente de la configuración de servicios.
-- No almacena claves maestras ni credenciales en texto claro.
-- Reversión: eliminar solo los índices de esta migración. El permiso pertenece al
-- catálogo base y no debe eliminarse si ya es utilizado por una instalación.

BEGIN;

INSERT INTO uml_colaborativo.permiso
    (codigo, modulo, accion, descripcion, ambito)
VALUES
    ('CONFIGURAR_SERVICIOS', 'ACCESO', 'CONFIGURAR_SERVICIOS',
     'Configurar proveedores de IA, voz e imagen.', 'GLOBAL')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE r.codigo = 'SUPERADMINISTRADOR'
  AND p.codigo = 'CONFIGURAR_SERVICIOS'
ON CONFLICT DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_servicio_plataforma_nombre_normalizado
    ON uml_colaborativo.servicio_plataforma (lower(nombre));

CREATE INDEX IF NOT EXISTS ix_servicio_plataforma_filtros
    ON uml_colaborativo.servicio_plataforma
       (tipo_servicio, proveedor, estado, actualizado_en DESC);

COMMIT;
