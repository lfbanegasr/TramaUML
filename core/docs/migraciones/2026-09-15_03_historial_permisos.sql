-- Incremento 06: permisos para historial/restauración (CU19/CU20) y función auxiliar.
-- Reejecutable: ignora duplicados con ON CONFLICT DO NOTHING; función usa CREATE OR REPLACE.
-- Ejecutar como propietario del esquema con la aplicación detenida.
-- Reversión:
--   DROP FUNCTION IF EXISTS uml_colaborativo.guardar_version_modelo_restaurada(uuid,bigint,uuid,text,uuid);
--   DELETE FROM uml_colaborativo.rol_permiso
--     WHERE nombre_permiso IN ('VER_HISTORIAL_MODELO','RESTAURAR_MODELO');
--   DELETE FROM uml_colaborativo.permiso_disponible
--     WHERE nombre IN ('VER_HISTORIAL_MODELO','RESTAURAR_MODELO');
BEGIN;

-- 1. Permisos de dominio
INSERT INTO uml_colaborativo.permiso (codigo, modulo, accion, descripcion, ambito)
VALUES
    ('VER_HISTORIAL_MODELO', 'MODELADO', 'VER_HISTORIAL', 'Listar y consultar versiones del historial del modelo', 'PROYECTO'),
    ('RESTAURAR_MODELO', 'MODELADO', 'RESTAURAR', 'Restaurar el modelo a una versión anterior como nueva versión', 'PROYECTO')
ON CONFLICT (codigo) DO NOTHING;

-- COLABORADOR: lectura de historial.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE r.nombre = 'COLABORADOR' AND p.codigo = 'VER_HISTORIAL_MODELO'
ON CONFLICT DO NOTHING;

-- LIDER: lectura + restauración.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE r.nombre = 'LIDER' AND p.codigo IN ('VER_HISTORIAL_MODELO', 'RESTAURAR_MODELO')
ON CONFLICT DO NOTHING;

COMMIT;

-- 2. Función auxiliar para restauración (fuera del bloque BEGIN/COMMIT porque
--    CREATE OR REPLACE FUNCTION no puede ejecutarse dentro de una transacción
--    que ya hizo DDL de permisos en algunos motores; se mantiene separada para
--    claridad y para cumplir el contrato "propietario ejecuta migraciones").
CREATE OR REPLACE FUNCTION uml_colaborativo.iniciar_edicion_modelo(p_id_modelo uuid,p_revision_esperada bigint,p_actor uuid)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE reserved bigint; contexts jsonb; project_id uuid;
BEGIN
    SELECT id_proyecto INTO project_id FROM modelo_uml WHERE id=p_id_modelo;
    IF NOT (tiene_permiso_proyecto(p_actor,project_id,'EDITAR_MODELO') OR tiene_permiso_proyecto(p_actor,project_id,'RESTAURAR_MODELO')) THEN
        RAISE EXCEPTION 'Sin permiso para editar o restaurar el modelo' USING ERRCODE='42501'; END IF;
    -- Orden: proyecto compartido -> modelo exclusivo. Archivar/suspender no
    -- puede confirmarse mientras esta edicion conserva el proyecto activo.
    PERFORM 1 FROM proyecto WHERE id=project_id AND estado='ACTIVO' FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'El proyecto no esta activo' USING ERRCODE='40001'; END IF;
    contexts:=COALESCE(NULLIF(current_setting('uml_colaborativo.ediciones',true),''),'{}')::jsonb;
    IF contexts ? p_id_modelo::text THEN
        RAISE EXCEPTION 'Finalice la edicion abierta antes de reservar otra revision del mismo modelo' USING ERRCODE='23514'; END IF;
    reserved:=reservar_revision_modelo(p_id_modelo,p_revision_esperada);
    contexts:=contexts || jsonb_build_object(p_id_modelo::text,jsonb_build_object('revision',reserved,'actor',p_actor));
    PERFORM set_config('uml_colaborativo.ediciones',contexts::text,true);
    RETURN reserved;
END;
$$;

REVOKE ALL ON FUNCTION uml_colaborativo.iniciar_edicion_modelo(uuid,bigint,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION uml_colaborativo.iniciar_edicion_modelo(uuid,bigint,uuid) TO uml_app;

CREATE OR REPLACE FUNCTION uml_colaborativo.guardar_version_modelo_restaurada(
    p_id_modelo           uuid,
    p_revision_reservada  bigint,
    p_actor               uuid,
    p_resumen             text,
    p_id_version_origen   uuid
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, uml_colaborativo, pg_temp
AS $$
DECLARE
    model_row modelo_uml%ROWTYPE;
    version_id uuid;
    version_no integer;
    contexts jsonb;
    context_value jsonb;
BEGIN
    -- Validar que la versión origen exista y pertenezca al mismo modelo
    IF NOT EXISTS (
        SELECT 1 FROM uml_colaborativo.version_modelo_uml
        WHERE id = p_id_version_origen AND id_modelo = p_id_modelo
    ) THEN
        RAISE EXCEPTION 'La version origen no pertenece al modelo o no existe' USING ERRCODE = '23503';
    END IF;

    -- Validar contexto de edición de la transacción
    contexts := COALESCE(NULLIF(current_setting('uml_colaborativo.ediciones', true), ''), '{}')::jsonb;
    context_value := contexts->p_id_modelo::text;
    IF context_value IS NULL
        OR (context_value->>'revision')::bigint IS DISTINCT FROM p_revision_reservada
        OR (context_value->>'actor')::uuid IS DISTINCT FROM p_actor THEN
        RAISE EXCEPTION 'Reserva y actor deben pertenecer a la misma transaccion' USING ERRCODE = '42501';
    END IF;

    -- Bloquear y validar modelo
    SELECT * INTO model_row FROM modelo_uml WHERE id = p_id_modelo FOR UPDATE;
    IF NOT FOUND OR model_row.revision_actual <> p_revision_reservada THEN
        RAISE EXCEPTION 'La revision reservada ya no es vigente' USING ERRCODE = '40001';
    END IF;

    IF model_row.estado = 'ARCHIVADO' OR NOT EXISTS (
        SELECT FROM proyecto WHERE id = model_row.id_proyecto AND estado = 'ACTIVO'
    ) THEN
        RAISE EXCEPTION 'No se guarda una edicion de modelo/proyecto archivado o suspendido' USING ERRCODE = '40001';
    END IF;

    -- Validar permiso RESTAURAR_MODELO sin requerir EDITAR_MODELO
    IF NOT tiene_permiso_proyecto(p_actor, model_row.id_proyecto, 'RESTAURAR_MODELO') THEN
        RAISE EXCEPTION 'Sin permiso para restaurar el modelo' USING ERRCODE = '42501';
    END IF;

    -- Calcular siguiente número de versión
    SELECT COALESCE(max(numero_version), 0) + 1 INTO version_no
    FROM version_modelo_uml WHERE id_modelo = p_id_modelo;

    -- Insertar directamente con id_version_restaurada desde su creación (evita UPDATE rechazado por inmutabilidad)
    INSERT INTO version_modelo_uml(
        id_modelo,
        numero_version,
        revision,
        id_version_padre,
        id_version_restaurada,
        instantanea,
        creado_por,
        resumen_cambio
    ) VALUES (
        p_id_modelo,
        version_no,
        p_revision_reservada,
        model_row.id_version_actual,
        p_id_version_origen,
        construir_instantanea_modelo(p_id_modelo),
        p_actor,
        p_resumen
    ) RETURNING id INTO version_id;

    -- Actualizar id_version_actual en el modelo
    UPDATE modelo_uml SET id_version_actual = version_id WHERE id = p_id_modelo;

    -- Registrar auditoría de actividad
    INSERT INTO actividad_proyecto(
        id_proyecto, id_modelo, id_version_modelo, id_usuario_actor, categoria, accion
    ) VALUES (
        model_row.id_proyecto, p_id_modelo, version_id, p_actor, 'MODELO', 'VERSION_RESTAURADA'
    );

    -- Limpiar contexto transaccional
    PERFORM set_config('uml_colaborativo.ediciones', (contexts - p_id_modelo::text)::text, true);

    RETURN version_id;
END;
$$;

REVOKE ALL ON FUNCTION uml_colaborativo.guardar_version_modelo_restaurada(uuid,bigint,uuid,text,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION uml_colaborativo.guardar_version_modelo_restaurada(uuid,bigint,uuid,text,uuid) TO uml_app;

