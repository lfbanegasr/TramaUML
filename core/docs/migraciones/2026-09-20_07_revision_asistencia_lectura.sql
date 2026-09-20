BEGIN;

-- Un lector del proyecto puede auditar una propuesta, pero no corregirla ni aplicarla.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r
JOIN uml_colaborativo.permiso p ON p.codigo = 'REVISAR_PROPUESTA_IA'
WHERE r.codigo IN ('LECTOR_PROYECTO', 'LECTOR')
ON CONFLICT DO NOTHING;

COMMIT;
