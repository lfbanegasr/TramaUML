-- P04: permisos de asistencia inteligente (CU21-CU25).
-- Reejecutable y compatible con los códigos de rol vigentes.
BEGIN;

INSERT INTO uml_colaborativo.permiso (codigo, modulo, accion, descripcion, ambito)
VALUES
    ('SOLICITAR_IA', 'INTELIGENCIA', 'SOLICITAR',
     'Crear solicitudes inteligentes desde texto, voz o imagen', 'PROYECTO'),
    ('REVISAR_PROPUESTA_IA', 'INTELIGENCIA', 'REVISAR',
     'Consultar el detalle y las dependencias de propuestas inteligentes', 'PROYECTO'),
    ('APLICAR_PROPUESTA_IA', 'INTELIGENCIA', 'APLICAR',
     'Aplicar o rechazar propuestas inteligentes revisadas', 'PROYECTO')
ON CONFLICT (codigo) DO NOTHING;

-- LIDER = PROPIETARIO_PROYECTO; COLABORADOR = EDITOR_PROYECTO en el catálogo vigente.
-- Se contemplan también los alias solicitados para instalaciones que ya los utilicen.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r
JOIN uml_colaborativo.permiso p
  ON p.codigo IN (
      'SOLICITAR_IA', 'REVISAR_PROPUESTA_IA', 'APLICAR_PROPUESTA_IA',
      'REVISAR_PROPUESTA')
WHERE r.codigo IN (
    'PROPIETARIO_PROYECTO', 'EDITOR_PROYECTO', 'LIDER', 'COLABORADOR')
ON CONFLICT DO NOTHING;

COMMIT;
