BEGIN;

-- Un nombre describe la configuración; no identifica una instantánea de configuración.
-- Se conservan todas las filas, incluidas las utilizadas por ZIPs ya aprobados.
ALTER TABLE uml_colaborativo.configuracion_generacion_backend
    DROP CONSTRAINT IF EXISTS uq_configuracion_generacion_backend_01;
CREATE INDEX IF NOT EXISTS ix_configuracion_backend_version_nombre
    ON uml_colaborativo.configuracion_generacion_backend(id_version_modelo,nombre);

CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_configuracion_generacion()
RETURNS trigger LANGUAGE plpgsql
SET search_path TO 'pg_catalog','uml_colaborativo','public','pg_temp'
AS $$
DECLARE cfg uml_colaborativo.configuracion_generacion_backend%ROWTYPE;
BEGIN
    SELECT * INTO cfg FROM uml_colaborativo.configuracion_generacion_backend
    WHERE id=NEW.id_configuracion FOR UPDATE;
    IF cfg.estado IS DISTINCT FROM 'VALIDO' OR NOT EXISTS (
        SELECT 1 FROM uml_colaborativo.clase_configuracion_backend WHERE id_configuracion=cfg.id
    ) THEN
        RAISE EXCEPTION 'La generacion requiere una configuracion valida y clases seleccionadas'
            USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_descarga_artefacto()
RETURNS trigger LANGUAGE plpgsql
SET search_path TO 'pg_catalog','uml_colaborativo','public','pg_temp'
AS $$
DECLARE trabajo uml_colaborativo.generacion_backend%ROWTYPE;
        proyecto uuid; huella text;
BEGIN
    SELECT * INTO trabajo FROM uml_colaborativo.generacion_backend WHERE id=NEW.id_generacion;
    SELECT m.id_proyecto INTO proyecto
    FROM uml_colaborativo.configuracion_generacion_backend c
    JOIN uml_colaborativo.version_modelo_uml v ON v.id=c.id_version_modelo
    JOIN uml_colaborativo.modelo_uml m ON m.id=v.id_modelo
    WHERE c.id=trabajo.id_configuracion;
    SELECT sha256 INTO huella FROM uml_colaborativo.archivo_almacenado WHERE id=NEW.id_archivo;
    IF trabajo.estado IS DISTINCT FROM 'GENERADO'
        OR NEW.id_archivo IS DISTINCT FROM trabajo.id_archivo_salida
        OR NOT uml_colaborativo.tiene_permiso_proyecto(NEW.descargado_por,proyecto,'DESCARGAR_ARTEFACTO')
        OR NOT EXISTS (
            SELECT 1 FROM uml_colaborativo.validacion_backend v
            JOIN uml_colaborativo.archivo_almacenado informe ON informe.id=v.id_archivo_informe
            WHERE v.id_generacion=trabajo.id AND v.estado IN ('VALIDO','VALIDO_CON_ADVERTENCIAS')
              AND CASE WHEN informe.categoria='INFORME_VALIDACION' AND informe.tipo_contenido='application/json'
                       THEN convert_from(informe.contenido,'UTF8')::jsonb->>'sha256'
                       ELSE NULL END=huella
        ) THEN
        RAISE EXCEPTION 'Descarga no autorizada o ZIP sin aprobacion para su contenido'
            USING ERRCODE='23514';
    END IF;
    PERFORM uml_colaborativo.verificar_ambito_archivo(NEW.id_archivo,proyecto,'BACKEND_GENERADO');
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_configuracion_generacion() FROM PUBLIC;
REVOKE ALL ON FUNCTION uml_colaborativo.verificar_descarga_artefacto() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION uml_colaborativo.verificar_configuracion_generacion() TO uml_app;
GRANT EXECUTE ON FUNCTION uml_colaborativo.verificar_descarga_artefacto() TO uml_app;
COMMIT;
