-- Incremento 08: permisos para interoperabilidad XMI (CU28) y generación de backend (CU29-CU32).
-- Reejecutable: ignora duplicados con ON CONFLICT DO NOTHING.
-- Ejecutar como propietario del esquema con la aplicación detenida.
-- Reversión:
--   DELETE FROM uml_colaborativo.rol_permiso
--     WHERE id_permiso IN (
--       SELECT id FROM uml_colaborativo.permiso
--       WHERE codigo IN ('EXPORTAR_XMI','IMPORTAR_XMI','CONFIGURAR_BACKEND',
--                        'GENERAR_BACKEND','DESCARGAR_ARTEFACTO'));
--   DELETE FROM uml_colaborativo.permiso
--     WHERE codigo IN ('EXPORTAR_XMI','IMPORTAR_XMI','CONFIGURAR_BACKEND',
--                      'GENERAR_BACKEND','DESCARGAR_ARTEFACTO');
BEGIN;

-- 1. Permisos de dominio (reejecutable con ON CONFLICT DO NOTHING)
INSERT INTO uml_colaborativo.permiso (codigo, modulo, accion, descripcion, ambito)
VALUES
    ('EXPORTAR_XMI',       'INTEROPERABILIDAD', 'EXPORTAR_XMI',
     'Exportar el diagrama del modelo como archivo XMI 2.1 compatible con Enterprise Architect',
     'PROYECTO'),
    ('IMPORTAR_XMI',       'INTEROPERABILIDAD', 'IMPORTAR_XMI',
     'Importar un archivo XMI 2.1 desde Enterprise Architect al modelo',
     'PROYECTO'),
    ('CONFIGURAR_BACKEND', 'GENERACION',        'CONFIGURAR_BACKEND',
     'Configurar los parámetros de generación del proyecto Spring Boot',
     'PROYECTO'),
    ('GENERAR_BACKEND',    'GENERACION',        'GENERAR_BACKEND',
     'Ejecutar la generación del proyecto Spring Boot a partir del diagrama UML',
     'PROYECTO'),
    ('DESCARGAR_ARTEFACTO','GENERACION',        'DESCARGAR_ARTEFACTO',
     'Descargar el archivo ZIP del proyecto Spring Boot generado',
     'PROYECTO')
ON CONFLICT (codigo) DO NOTHING;

-- 2. Asignación a roles predeterminados del proyecto
-- LIDER: acceso completo a exportación XMI y generación de backend.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE r.nombre = 'LIDER'
  AND p.codigo IN ('EXPORTAR_XMI', 'IMPORTAR_XMI', 'CONFIGURAR_BACKEND',
                   'GENERAR_BACKEND', 'DESCARGAR_ARTEFACTO')
ON CONFLICT DO NOTHING;

-- COLABORADOR: solo puede exportar XMI (lectura del modelo) y descargar artefactos generados.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE r.nombre = 'COLABORADOR'
  AND p.codigo IN ('EXPORTAR_XMI', 'DESCARGAR_ARTEFACTO')
ON CONFLICT DO NOTHING;

-- 3. Persistencia binaria para artefactos generados (XMI y ZIP)
ALTER TABLE uml_colaborativo.archivo_almacenado
    ADD COLUMN IF NOT EXISTS contenido bytea;

COMMIT;
