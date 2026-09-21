BEGIN;

-- Incremento 14: las capas son configurables. La coherencia entre capas se valida en el
-- servicio antes de marcar la configuración como VALIDO; el disparador conserva las
-- invariantes duras de reproducibilidad de la generación.
CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_configuracion_generacion()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO 'pg_catalog', 'uml_colaborativo', 'public', 'pg_temp'
AS $$
DECLARE
    cfg uml_colaborativo.configuracion_generacion_backend%ROWTYPE;
    version_status text;
BEGIN
    SELECT * INTO cfg
      FROM uml_colaborativo.configuracion_generacion_backend
     WHERE id = NEW.id_configuracion
     FOR UPDATE;

    SELECT estado_validacion INTO version_status
      FROM uml_colaborativo.version_modelo_uml
     WHERE id = cfg.id_version_modelo;

    IF cfg.estado IS DISTINCT FROM 'VALIDO'
       OR version_status IS NULL
       OR version_status NOT IN ('VALIDO', 'VALIDO_CON_ADVERTENCIAS')
       OR NOT EXISTS (
            SELECT 1
              FROM uml_colaborativo.clase_configuracion_backend
             WHERE id_configuracion = cfg.id
       ) THEN
        RAISE EXCEPTION
            'La generacion requiere una version validada, configuracion valida y clases seleccionadas'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_configuracion_generacion() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION uml_colaborativo.verificar_configuracion_generacion() TO uml_app;

COMMENT ON FUNCTION uml_colaborativo.verificar_configuracion_generacion() IS
    'I14: protege la generacion reproducible sin imponer capas opcionales.';

COMMIT;
