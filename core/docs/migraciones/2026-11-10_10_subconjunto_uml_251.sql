-- UML 2.5.1, subconjunto de clases. Ejecutar DESPUÉS de las migraciones 01..09.
-- No aplicar automáticamente a la base existente. Respaldar antes; no reescribe versiones.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(20261110, 10);
ALTER TABLE uml_colaborativo.atributo_uml ADD COLUMN IF NOT EXISTS es_ordenado boolean NOT NULL DEFAULT false;
ALTER TABLE uml_colaborativo.atributo_uml ADD COLUMN IF NOT EXISTS coleccion_unica boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN uml_colaborativo.atributo_uml.es_unico IS 'Opción de unicidad SQL/JPA; NO es MultiplicityElement::isUnique';
COMMENT ON COLUMN uml_colaborativo.atributo_uml.coleccion_unica IS 'UML MultiplicityElement::isUnique; predeterminado true';
COMMENT ON COLUMN uml_colaborativo.atributo_uml.es_ordenado IS 'UML MultiplicityElement::isOrdered; predeterminado false';
-- El identificador es una pista de generación; sus condiciones se diagnostican en el generador.
ALTER TABLE uml_colaborativo.atributo_uml DROP CONSTRAINT IF EXISTS ck_atributo_uml_09;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='uml_colaborativo.relacion_uml'::regclass AND conname='ck_composicion_conjunto_uml251') THEN
  ALTER TABLE uml_colaborativo.relacion_uml ADD CONSTRAINT ck_composicion_conjunto_uml251
   CHECK (eliminado_en IS NOT NULL OR tipo_relacion <> 'COMPOSICION' OR (maximo_origen IS NOT NULL AND maximo_origen <= 1)) NOT VALID;
 END IF;
END $$;
-- NOT VALID conserva datos anteriores incoherentes para leer/diagnosticar; exige corregirlos al editarlos.
CREATE OR REPLACE FUNCTION uml_colaborativo.construir_instantanea_modelo(p_id_modelo uuid) RETURNS jsonb
    LANGUAGE sql STABLE
    SET search_path TO 'pg_catalog', 'uml_colaborativo', 'public', 'pg_temp'
    AS $$
    SELECT jsonb_build_object(
        'formato_version', 3,
        'subconjunto_uml', 'clases-2026-11-10',
        'modelo', jsonb_build_object('id', m.id, 'nombre', m.nombre, 'descripcion', m.descripcion),
        'paquetes', COALESCE((SELECT jsonb_agg(to_jsonb(p) ORDER BY p.id)
            FROM paquete_uml p WHERE p.id_modelo = m.id AND p.eliminado_en IS NULL), '[]'::jsonb),
        'clases', COALESCE((SELECT jsonb_agg(to_jsonb(c) ORDER BY c.id)
            FROM clase_uml c WHERE c.id_modelo = m.id AND c.eliminado_en IS NULL), '[]'::jsonb),
        'atributos', COALESCE((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.id_clase, a.orden, a.id)
            FROM atributo_uml a JOIN clase_uml c ON c.id = a.id_clase
            WHERE c.id_modelo = m.id AND c.eliminado_en IS NULL AND a.eliminado_en IS NULL), '[]'::jsonb),
        'operaciones', COALESCE((SELECT jsonb_agg(to_jsonb(o) ORDER BY o.id_clase, o.orden, o.id)
            FROM operacion_uml o JOIN clase_uml c ON c.id = o.id_clase
            WHERE c.id_modelo = m.id AND c.eliminado_en IS NULL AND o.eliminado_en IS NULL), '[]'::jsonb),
        'parametros', COALESCE((SELECT jsonb_agg(to_jsonb(p) ORDER BY p.id_operacion, p.orden, p.id)
            FROM parametro_uml p JOIN operacion_uml o ON o.id = p.id_operacion
            JOIN clase_uml c ON c.id = o.id_clase
            WHERE c.id_modelo = m.id AND c.eliminado_en IS NULL AND o.eliminado_en IS NULL AND p.eliminado_en IS NULL), '[]'::jsonb),
        'relaciones', COALESCE((SELECT jsonb_agg(to_jsonb(r) ORDER BY r.id)
            FROM relacion_uml r JOIN clase_uml s ON s.id = r.id_clase_origen
            JOIN clase_uml t ON t.id = r.id_clase_destino
            WHERE r.id_modelo = m.id AND r.eliminado_en IS NULL AND s.eliminado_en IS NULL AND t.eliminado_en IS NULL), '[]'::jsonb),
        'posiciones_clases', COALESCE((SELECT jsonb_agg(to_jsonb(l) ORDER BY l.id_clase)
            FROM posicion_clase_uml l JOIN clase_uml c ON c.id = l.id_clase
            WHERE c.id_modelo = m.id AND c.eliminado_en IS NULL), '[]'::jsonb),
        'posiciones_relaciones', COALESCE((SELECT jsonb_agg(to_jsonb(l) ORDER BY l.id_relacion)
            FROM posicion_relacion_uml l JOIN relacion_uml r ON r.id = l.id_relacion
            JOIN clase_uml s ON s.id = r.id_clase_origen
            JOIN clase_uml t ON t.id = r.id_clase_destino
            WHERE r.id_modelo = m.id AND r.eliminado_en IS NULL
              AND s.eliminado_en IS NULL AND t.eliminado_en IS NULL), '[]'::jsonb)
    ) FROM modelo_uml m WHERE m.id = p_id_modelo;
$$;

COMMIT;
