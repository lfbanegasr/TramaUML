BEGIN;
INSERT INTO uml_colaborativo.permiso(codigo,modulo,accion,descripcion,ambito)
VALUES('VALIDAR_BACKEND','GENERACION','VALIDAR_BACKEND','Compilar y validar un backend generado.','PROYECTO')
ON CONFLICT(codigo) DO NOTHING;
INSERT INTO uml_colaborativo.rol_permiso(id_rol,id_permiso)
SELECT r.id,p.id FROM uml_colaborativo.rol r CROSS JOIN uml_colaborativo.permiso p
WHERE r.codigo IN ('PROPIETARIO_PROYECTO','LIDER') AND p.codigo='VALIDAR_BACKEND'
ON CONFLICT DO NOTHING;
CREATE UNIQUE INDEX IF NOT EXISTS ux_validacion_backend_en_ejecucion
ON uml_colaborativo.validacion_backend(id_generacion) WHERE estado='EN_EJECUCION';
COMMIT;
