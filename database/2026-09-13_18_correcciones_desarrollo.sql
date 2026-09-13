-- Migracion incremental sobre el esquema ESPANOL completo del 2026-09-12.
-- No elimina tablas ni registros. Ejecutar como propietario/administrador,
-- despues de respaldar, con ON_ERROR_STOP en psql o dentro de pgAdmin.
-- Es reejecutable: reemplaza funciones y comprueba los objetos nuevos.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SET LOCAL search_path = pg_catalog, uml_colaborativo, pg_temp;
SELECT pg_advisory_xact_lock(20260913, 18);

DO $$
BEGIN
    IF to_regclass('uml_colaborativo.version_modelo_uml') IS NULL
       OR to_regclass('uml_colaborativo.atributo_uml') IS NULL THEN
        RAISE EXCEPTION 'Se requiere el esquema espanol completo; no aplicar al esquema ingles';
    END IF;
    IF EXISTS(SELECT FROM pg_roles WHERE rolname='uml_app'
        AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolreplication)) THEN
        RAISE EXCEPTION 'La cuenta uml_app debe carecer de privilegios administrativos';
    END IF;
END;
$$;

-- NULL significa sin limite, pero NUNCA es un maximo valido para un ID.
ALTER TABLE uml_colaborativo.atributo_uml DROP CONSTRAINT ck_atributo_uml_09;
ALTER TABLE uml_colaborativo.atributo_uml ADD CONSTRAINT ck_atributo_uml_09 CHECK (
    NOT es_identificador OR (multiplicidad_minima = 1
        AND multiplicidad_maxima IS NOT DISTINCT FROM 1 AND NOT es_estatico)
);

-- Resultado coherente. La compilacion/validacion real sigue siendo del backend.
DO $$
DECLARE tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['validacion_backend','validacion_modelo'] LOOP
        IF NOT EXISTS(SELECT FROM pg_constraint WHERE conrelid=format('uml_colaborativo.%I',tabla)::regclass
            AND conname='ck_resultado_validacion_coherente') THEN
            EXECUTE format('ALTER TABLE uml_colaborativo.%I ADD CONSTRAINT ck_resultado_validacion_coherente CHECK (
                (estado = ''EN_EJECUCION'' OR completado_en IS NOT NULL)
                AND (completado_en IS NULL OR completado_en >= iniciado_en)
                AND (estado <> ''VALIDO'' OR (cantidad_errores = 0 AND cantidad_advertencias = 0))
                AND (estado <> ''VALIDO_CON_ADVERTENCIAS'' OR (cantidad_errores = 0 AND cantidad_advertencias > 0))
                AND (estado <> ''INVALIDO'' OR cantidad_errores > 0))',tabla);
        END IF;
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_propietario_proyecto(p_id_proyecto uuid)
RETURNS void LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE owner_id uuid;
BEGIN
    SELECT id_usuario_propietario INTO owner_id FROM proyecto WHERE id=p_id_proyecto FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    IF NOT EXISTS(SELECT FROM miembro_proyecto pm
        JOIN rol_miembro_proyecto pmr ON pmr.id_miembro_proyecto=pm.id AND pmr.revocado_en IS NULL
        JOIN rol r ON r.id=pmr.id_rol AND r.codigo='PROPIETARIO_PROYECTO' AND r.estado='ACTIVO'
        WHERE pm.id_proyecto=p_id_proyecto AND pm.id_usuario=owner_id AND pm.estado='ACTIVO') THEN
        RAISE EXCEPTION 'El proyecto debe conservar propietario, membresia y rol; transfiera la propiedad antes de retirar al miembro'
            USING ERRCODE='23514';
    END IF;
    -- El estado de la CUENTA no determina la titularidad. Los permisos efectivos
    -- ya exigen usuario ACTIVO; el backend verifica ademas bloqueo/expiracion.
END;
$$;
DROP TRIGGER IF EXISTS trg_usuario_02 ON uml_colaborativo.usuario;
DROP FUNCTION IF EXISTS uml_colaborativo.comprobar_proyectos_propietario();

CREATE OR REPLACE FUNCTION uml_colaborativo.proteger_configuracion_utilizada()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE config_to_check uuid;
BEGIN
    IF TG_TABLE_NAME='configuracion_generacion_backend' THEN config_to_check:=OLD.id;
    ELSIF TG_OP='INSERT' THEN config_to_check:=NEW.id_configuracion;
    ELSE config_to_check:=OLD.id_configuracion; END IF;
    PERFORM 1 FROM configuracion_generacion_backend WHERE id=config_to_check FOR UPDATE;
    IF EXISTS(SELECT FROM generacion_backend WHERE id_configuracion=config_to_check) THEN
        IF TG_TABLE_NAME='configuracion_generacion_backend' AND TG_OP='UPDATE' THEN
            IF (to_jsonb(NEW)-ARRAY['estado','actualizado_en'])
                IS NOT DISTINCT FROM (to_jsonb(OLD)-ARRAY['estado','actualizado_en']) THEN
                RETURN NEW;
            END IF;
        END IF;
        RAISE EXCEPTION 'Los parametros usados son inmutables; el estado puede cambiar, los parametros requieren nueva configuracion'
            USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_configuracion_generacion()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE cfg configuracion_generacion_backend%ROWTYPE; version_status text;
BEGIN
    SELECT * INTO cfg FROM configuracion_generacion_backend WHERE id=NEW.id_configuracion FOR UPDATE;
    SELECT estado_validacion INTO version_status FROM version_modelo_uml WHERE id=cfg.id_version_modelo;
    IF cfg.estado IS DISTINCT FROM 'VALIDO' OR version_status IS NULL
        OR version_status NOT IN('VALIDO','VALIDO_CON_ADVERTENCIAS')
        OR NOT EXISTS(SELECT FROM clase_configuracion_backend WHERE id_configuracion=cfg.id) THEN
        RAISE EXCEPTION 'La generacion requiere version validada, configuracion valida y clases seleccionadas'
            USING ERRCODE='23514';
    END IF;
    -- Las capas, DTO, versiones y dependencias se comprueban en el generador.
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_descarga_artefacto()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE job_row generacion_backend%ROWTYPE; project_to_check uuid; validation_row validacion_backend%ROWTYPE;
BEGIN
    SELECT * INTO job_row FROM generacion_backend WHERE id=NEW.id_generacion;
    SELECT m.id_proyecto INTO project_to_check FROM configuracion_generacion_backend cfg
        JOIN version_modelo_uml v ON v.id=cfg.id_version_modelo JOIN modelo_uml m ON m.id=v.id_modelo
        WHERE cfg.id=job_row.id_configuracion;
    SELECT * INTO validation_row FROM validacion_backend WHERE id_generacion=job_row.id
        ORDER BY iniciado_en DESC,id DESC LIMIT 1;
    IF job_row.estado IS DISTINCT FROM 'GENERADO' OR NEW.id_archivo IS DISTINCT FROM job_row.id_archivo_salida
        OR validation_row.estado IS NULL OR validation_row.estado NOT IN('VALIDO','VALIDO_CON_ADVERTENCIAS')
        OR validation_row.cantidad_errores <> 0 OR validation_row.completado_en IS NULL
        OR NOT tiene_permiso_proyecto(NEW.descargado_por,project_to_check,'DESCARGAR_ARTEFACTO') THEN
        RAISE EXCEPTION 'Descarga no autorizada o backend sin validacion final satisfactoria' USING ERRCODE='23514';
    END IF;
    PERFORM verificar_ambito_archivo(NEW.id_archivo,project_to_check,'BACKEND_GENERADO');
    RETURN NEW;
END;
$$;

-- Validar tambien al CAMBIAR el ambito, no solo al asignar una relacion.
-- Bloqueos compartidos en asignaciones impiden carreras con cambios del catalogo.
CREATE OR REPLACE FUNCTION uml_colaborativo.proteger_ambito_rol()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE assigned_scope text; permission_scope text; role_code text;
BEGIN
    IF NOT pg_try_advisory_xact_lock(20260913,19) THEN
        RAISE EXCEPTION 'Catalogo de autorizacion modificado concurrentemente; reintente la transaccion'
            USING ERRCODE='40001'; END IF;
    SELECT ambito,codigo INTO assigned_scope,role_code FROM rol WHERE id=NEW.id_rol FOR SHARE;
    IF TG_TABLE_NAME='usuario_rol' AND assigned_scope NOT IN('GLOBAL','AMBOS') THEN
        RAISE EXCEPTION 'Un rol de proyecto no se asigna globalmente' USING ERRCODE='23514';
    ELSIF TG_TABLE_NAME='rol_miembro_proyecto' AND assigned_scope NOT IN('PROYECTO','AMBOS') THEN
        RAISE EXCEPTION 'Un rol global no se asigna desde un proyecto' USING ERRCODE='23514';
    ELSIF TG_TABLE_NAME='rol_permiso' THEN
        SELECT ambito INTO permission_scope FROM permiso WHERE id=NEW.id_permiso FOR SHARE;
        IF role_code <> 'SUPERADMINISTRADOR' AND assigned_scope <> 'AMBOS'
            AND permission_scope <> 'AMBOS' AND assigned_scope <> permission_scope THEN
            RAISE EXCEPTION 'El permiso esta fuera del ambito del rol' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION uml_colaborativo.proteger_ambito_permiso_excepcional()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE permission_scope text;
BEGIN
    IF NOT pg_try_advisory_xact_lock(20260913,19) THEN
        RAISE EXCEPTION 'Catalogo de autorizacion modificado concurrentemente; reintente la transaccion'
            USING ERRCODE='40001'; END IF;
    SELECT ambito INTO permission_scope FROM permiso WHERE id=NEW.id_permiso FOR SHARE;
    IF permission_scope='GLOBAL' THEN
        RAISE EXCEPTION 'Un permiso global no se asigna desde un proyecto' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_cambio_ambito_catalogo()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
BEGIN
    IF NEW.ambito IS NOT DISTINCT FROM OLD.ambito THEN RETURN NEW; END IF;
    IF NOT pg_try_advisory_xact_lock(20260913,19) THEN
        RAISE EXCEPTION 'Catalogo de autorizacion modificado concurrentemente; reintente la transaccion'
            USING ERRCODE='40001'; END IF;
    IF TG_TABLE_NAME='rol' THEN
        IF (NEW.ambito NOT IN('GLOBAL','AMBOS') AND EXISTS(
                SELECT FROM usuario_rol WHERE id_rol=NEW.id AND revocado_en IS NULL))
            OR (NEW.ambito NOT IN('PROYECTO','AMBOS') AND EXISTS(
                SELECT FROM rol_miembro_proyecto WHERE id_rol=NEW.id AND revocado_en IS NULL))
            OR (NEW.codigo <> 'SUPERADMINISTRADOR' AND NEW.ambito <> 'AMBOS' AND EXISTS(
                SELECT FROM rol_permiso rp JOIN permiso p ON p.id=rp.id_permiso
                WHERE rp.id_rol=NEW.id AND p.ambito <> 'AMBOS' AND p.ambito <> NEW.ambito)) THEN
            RAISE EXCEPTION 'El nuevo ambito contradice asignaciones vigentes del rol' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.ambito='GLOBAL' AND EXISTS(SELECT FROM permiso_miembro_proyecto
                WHERE id_permiso=NEW.id AND revocado_en IS NULL))
            OR (NEW.ambito <> 'AMBOS' AND EXISTS(SELECT FROM rol_permiso rp JOIN rol r ON r.id=rp.id_rol
                WHERE rp.id_permiso=NEW.id AND r.codigo <> 'SUPERADMINISTRADOR'
                AND r.ambito <> 'AMBOS' AND r.ambito <> NEW.ambito)) THEN
            RAISE EXCEPTION 'El nuevo ambito contradice asignaciones vigentes del permiso' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_rol_ambito_catalogo ON uml_colaborativo.rol;
CREATE TRIGGER trg_rol_ambito_catalogo BEFORE UPDATE OF ambito ON uml_colaborativo.rol
    FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_cambio_ambito_catalogo();
DROP TRIGGER IF EXISTS trg_permiso_ambito_catalogo ON uml_colaborativo.permiso;
CREATE TRIGGER trg_permiso_ambito_catalogo BEFORE UPDATE OF ambito ON uml_colaborativo.permiso
    FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_cambio_ambito_catalogo();

-- Padre activo para elementos vigentes. Al borrar una clase/operacion, los
-- dependientes se borran logicamente; nunca se borra su registro historico.
CREATE OR REPLACE FUNCTION uml_colaborativo.verificar_padres_uml_activos()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE parent_deleted timestamptz; invalid_parent boolean; parent_row record;
BEGIN
    IF NEW.eliminado_en IS NOT NULL THEN RETURN NEW; END IF;
    CASE TG_TABLE_NAME
        WHEN 'clase_uml' THEN
            IF NEW.id_paquete IS NOT NULL THEN
                SELECT eliminado_en INTO parent_deleted FROM paquete_uml WHERE id=NEW.id_paquete FOR SHARE;
                IF parent_deleted IS NOT NULL THEN
                    RAISE EXCEPTION 'La clase requiere un paquete activo' USING ERRCODE='23514'; END IF;
            END IF;
        WHEN 'atributo_uml','operacion_uml' THEN
            SELECT eliminado_en INTO parent_deleted FROM clase_uml WHERE id=NEW.id_clase FOR SHARE;
            IF parent_deleted IS NOT NULL THEN
                RAISE EXCEPTION 'El miembro UML requiere una clase activa' USING ERRCODE='23514'; END IF;
        WHEN 'parametro_uml' THEN
            SELECT (o.eliminado_en IS NOT NULL OR c.eliminado_en IS NOT NULL) INTO invalid_parent
                FROM operacion_uml o JOIN clase_uml c ON c.id=o.id_clase
                WHERE o.id=NEW.id_operacion FOR SHARE OF o,c;
            IF invalid_parent THEN
                RAISE EXCEPTION 'El parametro requiere operacion y clase activas' USING ERRCODE='23514'; END IF;
        WHEN 'relacion_uml' THEN
            FOR parent_row IN SELECT eliminado_en FROM clase_uml
                WHERE id IN(NEW.id_clase_origen,NEW.id_clase_destino) ORDER BY id FOR SHARE LOOP
                IF parent_row.eliminado_en IS NOT NULL THEN
                    RAISE EXCEPTION 'La relacion requiere clases activas' USING ERRCODE='23514'; END IF;
            END LOOP;
        WHEN 'paquete_uml' THEN
            IF NEW.id_paquete_padre IS NOT NULL THEN
                SELECT eliminado_en INTO parent_deleted FROM paquete_uml WHERE id=NEW.id_paquete_padre FOR SHARE;
                IF parent_deleted IS NOT NULL THEN
                    RAISE EXCEPTION 'El paquete padre debe estar activo' USING ERRCODE='23514'; END IF;
            END IF;
    END CASE;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION uml_colaborativo.propagar_borrado_logico_uml()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
BEGIN
    IF OLD.eliminado_en IS NOT NULL OR NEW.eliminado_en IS NULL THEN RETURN NEW; END IF;
    IF TG_TABLE_NAME='clase_uml' THEN
        UPDATE atributo_uml SET eliminado_en=NEW.eliminado_en WHERE id_clase=NEW.id AND eliminado_en IS NULL;
        UPDATE operacion_uml SET eliminado_en=NEW.eliminado_en WHERE id_clase=NEW.id AND eliminado_en IS NULL;
        UPDATE relacion_uml SET eliminado_en=NEW.eliminado_en
            WHERE (id_clase_origen=NEW.id OR id_clase_destino=NEW.id) AND eliminado_en IS NULL;
    ELSE
        UPDATE parametro_uml SET eliminado_en=NEW.eliminado_en WHERE id_operacion=NEW.id AND eliminado_en IS NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION uml_colaborativo.proteger_borrado_paquete_no_vacio()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
BEGIN
    IF OLD.eliminado_en IS NULL AND NEW.eliminado_en IS NOT NULL AND (
        EXISTS(SELECT FROM paquete_uml WHERE id_paquete_padre=NEW.id AND eliminado_en IS NULL)
        OR EXISTS(SELECT FROM clase_uml WHERE id_paquete=NEW.id AND eliminado_en IS NULL)) THEN
        RAISE EXCEPTION 'Reubique o elimine logicamente el contenido antes de borrar el paquete' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
DO $$
DECLARE tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['paquete_uml','clase_uml','atributo_uml','operacion_uml','parametro_uml','relacion_uml'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_padres_activos ON uml_colaborativo.%I',tabla);
        EXECUTE format('CREATE TRIGGER trg_padres_activos BEFORE INSERT OR UPDATE ON uml_colaborativo.%I
            FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_padres_uml_activos()',tabla);
    END LOOP;
END;
$$;
DROP TRIGGER IF EXISTS trg_borrado_dependientes ON uml_colaborativo.clase_uml;
CREATE TRIGGER trg_borrado_dependientes AFTER UPDATE OF eliminado_en ON uml_colaborativo.clase_uml
    FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.propagar_borrado_logico_uml();
DROP TRIGGER IF EXISTS trg_borrado_dependientes ON uml_colaborativo.operacion_uml;
CREATE TRIGGER trg_borrado_dependientes AFTER UPDATE OF eliminado_en ON uml_colaborativo.operacion_uml
    FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.propagar_borrado_logico_uml();
DROP TRIGGER IF EXISTS trg_borrado_paquete ON uml_colaborativo.paquete_uml;
CREATE TRIGGER trg_borrado_paquete BEFORE UPDATE OF eliminado_en ON uml_colaborativo.paquete_uml
    FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_borrado_paquete_no_vacio();

-- Contexto LOCAL de la transaccion, establecido por iniciar_edicion_modelo.
-- Es una defensa frente a errores de integracion, no una identidad autenticada
-- ni sustituto de Spring Security/RLS. No aceptar SQL/contextos del cliente.
CREATE OR REPLACE FUNCTION uml_colaborativo.id_modelo_fila_uml(p_tabla text,p_fila jsonb)
RETURNS uuid LANGUAGE plpgsql STABLE SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE result_id uuid;
BEGIN
    CASE p_tabla
        WHEN 'modelo_uml' THEN result_id:=(p_fila->>'id')::uuid;
        WHEN 'paquete_uml','clase_uml','relacion_uml' THEN result_id:=(p_fila->>'id_modelo')::uuid;
        WHEN 'atributo_uml','operacion_uml','posicion_clase_uml' THEN
            SELECT id_modelo INTO result_id FROM clase_uml WHERE id=(p_fila->>'id_clase')::uuid;
        WHEN 'parametro_uml' THEN
            SELECT c.id_modelo INTO result_id FROM operacion_uml o JOIN clase_uml c ON c.id=o.id_clase
                WHERE o.id=(p_fila->>'id_operacion')::uuid;
        WHEN 'posicion_relacion_uml' THEN
            SELECT id_modelo INTO result_id FROM relacion_uml WHERE id=(p_fila->>'id_relacion')::uuid;
        ELSE RAISE EXCEPTION 'Entidad no admitida por el protocolo UML';
    END CASE;
    RETURN result_id;
END;
$$;
CREATE OR REPLACE FUNCTION uml_colaborativo.exigir_contexto_edicion_uml()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE model_id uuid; contexts jsonb; context_value jsonb; current_rev bigint; saved_rev bigint;
BEGIN
    -- El administrador conserva acceso para migraciones/recuperacion controladas.
    IF current_user <> 'uml_app' THEN RETURN NEW; END IF;
    model_id:=id_modelo_fila_uml(TG_TABLE_NAME,to_jsonb(NEW));
    contexts:=COALESCE(NULLIF(current_setting('uml_colaborativo.ediciones',true),''),'{}')::jsonb;
    context_value:=contexts->model_id::text;
    SELECT m.revision_actual,v.revision INTO current_rev,saved_rev FROM modelo_uml m
        LEFT JOIN version_modelo_uml v ON v.id=m.id_version_actual WHERE m.id=model_id;
    IF model_id IS NULL OR context_value IS NULL
        OR (context_value->>'revision')::bigint IS DISTINCT FROM current_rev
        OR current_rev <= COALESCE(saved_rev,0) THEN
        RAISE EXCEPTION 'La escritura UML requiere iniciar_edicion_modelo en ESTA transaccion y guardar al finalizar'
            USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION uml_colaborativo.exigir_edicion_confirmada()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE current_rev bigint; saved_rev bigint;
BEGIN
    IF current_user <> 'uml_app' AND
        NOT (COALESCE(NULLIF(current_setting('uml_colaborativo.ediciones',true),''),'{}')::jsonb ? NEW.id::text)
        THEN RETURN NULL; END IF;
    SELECT m.revision_actual,v.revision INTO current_rev,saved_rev FROM modelo_uml m
        LEFT JOIN version_modelo_uml v ON v.id=m.id_version_actual WHERE m.id=NEW.id;
    IF saved_rev IS DISTINCT FROM current_rev THEN
        RAISE EXCEPTION 'No se puede confirmar la transaccion sin guardar su version UML' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE OR REPLACE FUNCTION uml_colaborativo.iniciar_edicion_modelo(p_id_modelo uuid,p_revision_esperada bigint,p_actor uuid)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE reserved bigint; contexts jsonb; project_id uuid;
BEGIN
    SELECT id_proyecto INTO project_id FROM modelo_uml WHERE id=p_id_modelo;
    IF NOT tiene_permiso_proyecto(p_actor,project_id,'EDITAR_MODELO') THEN
        RAISE EXCEPTION 'Sin permiso para editar el modelo' USING ERRCODE='42501'; END IF;
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
CREATE OR REPLACE FUNCTION uml_colaborativo.guardar_version_modelo(p_id_modelo uuid,p_revision_reservada bigint,p_actor uuid,p_resumen text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, uml_colaborativo, pg_temp AS $$
DECLARE model_row modelo_uml%ROWTYPE; version_id uuid; version_no integer; contexts jsonb; context_value jsonb;
BEGIN
    contexts:=COALESCE(NULLIF(current_setting('uml_colaborativo.ediciones',true),''),'{}')::jsonb;
    context_value:=contexts->p_id_modelo::text;
    IF context_value IS NULL OR (context_value->>'revision')::bigint IS DISTINCT FROM p_revision_reservada
        OR (context_value->>'actor')::uuid IS DISTINCT FROM p_actor THEN
        RAISE EXCEPTION 'Reserva y actor deben pertenecer a la misma transaccion' USING ERRCODE='42501'; END IF;
    SELECT * INTO model_row FROM modelo_uml WHERE id=p_id_modelo FOR UPDATE;
    IF NOT FOUND OR model_row.revision_actual <> p_revision_reservada THEN
        RAISE EXCEPTION 'La revision reservada ya no es vigente' USING ERRCODE='40001'; END IF;
    IF model_row.estado='ARCHIVADO' OR NOT EXISTS(SELECT FROM proyecto WHERE id=model_row.id_proyecto AND estado='ACTIVO') THEN
        RAISE EXCEPTION 'No se guarda una edicion de modelo/proyecto archivado o suspendido' USING ERRCODE='40001'; END IF;
    IF NOT tiene_permiso_proyecto(p_actor,model_row.id_proyecto,'EDITAR_MODELO') THEN
        RAISE EXCEPTION 'Sin permiso para guardar el modelo' USING ERRCODE='42501'; END IF;
    SELECT COALESCE(max(numero_version),0)+1 INTO version_no FROM version_modelo_uml WHERE id_modelo=p_id_modelo;
    INSERT INTO version_modelo_uml(id_modelo,numero_version,revision,id_version_padre,instantanea,creado_por,resumen_cambio)
        VALUES(p_id_modelo,version_no,p_revision_reservada,model_row.id_version_actual,
            construir_instantanea_modelo(p_id_modelo),p_actor,p_resumen) RETURNING id INTO version_id;
    UPDATE modelo_uml SET id_version_actual=version_id WHERE id=p_id_modelo;
    INSERT INTO actividad_proyecto(id_proyecto,id_modelo,id_version_modelo,id_usuario_actor,categoria,accion)
        VALUES(model_row.id_proyecto,p_id_modelo,version_id,p_actor,'MODELO','VERSION_GUARDADA');
    PERFORM set_config('uml_colaborativo.ediciones',(contexts-p_id_modelo::text)::text,true);
    RETURN version_id;
END;
$$;
DO $$
DECLARE tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['paquete_uml','clase_uml','atributo_uml','operacion_uml','parametro_uml',
        'relacion_uml','posicion_clase_uml','posicion_relacion_uml'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_exigir_edicion ON uml_colaborativo.%I',tabla);
        EXECUTE format('CREATE TRIGGER trg_exigir_edicion BEFORE INSERT OR UPDATE ON uml_colaborativo.%I
            FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.exigir_contexto_edicion_uml()',tabla);
    END LOOP;
END;
$$;
DROP TRIGGER IF EXISTS trg_exigir_edicion_cabecera ON uml_colaborativo.modelo_uml;
CREATE TRIGGER trg_exigir_edicion_cabecera BEFORE UPDATE OF nombre,descripcion ON uml_colaborativo.modelo_uml
    FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.exigir_contexto_edicion_uml();
DROP TRIGGER IF EXISTS trg_modelo_edicion_confirmada ON uml_colaborativo.modelo_uml;
CREATE CONSTRAINT TRIGGER trg_modelo_edicion_confirmada AFTER UPDATE ON uml_colaborativo.modelo_uml
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    WHEN (OLD.revision_actual IS DISTINCT FROM NEW.revision_actual)
    EXECUTE FUNCTION uml_colaborativo.exigir_edicion_confirmada();

-- uml_app no puede saltarse la reserva reseteando revisiones o fabricando snapshots.
REVOKE UPDATE ON uml_colaborativo.modelo_uml FROM uml_app;
GRANT UPDATE(nombre,descripcion,estado,archivado_en) ON uml_colaborativo.modelo_uml TO uml_app;
REVOKE INSERT ON uml_colaborativo.modelo_uml FROM uml_app;
GRANT INSERT(id,id_proyecto,nombre,descripcion,estado,creado_por) ON uml_colaborativo.modelo_uml TO uml_app;
REVOKE INSERT ON uml_colaborativo.version_modelo_uml FROM uml_app;
REVOKE EXECUTE ON FUNCTION uml_colaborativo.reservar_revision_modelo(uuid,bigint) FROM uml_app,PUBLIC;
REVOKE ALL ON FUNCTION uml_colaborativo.iniciar_edicion_modelo(uuid,bigint,uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION uml_colaborativo.guardar_version_modelo(uuid,bigint,uuid,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION uml_colaborativo.iniciar_edicion_modelo(uuid,bigint,uuid),
    uml_colaborativo.guardar_version_modelo(uuid,bigint,uuid,text) TO uml_app;

-- Indices enfocados en consultas de propuesta, administracion y pertenencia.
CREATE INDEX IF NOT EXISTS ix_ambiguedad_propuesta_revision ON uml_colaborativo.ambiguedad_propuesta(id_propuesta);
CREATE INDEX IF NOT EXISTS ix_ambiguedad_operacion ON uml_colaborativo.ambiguedad_propuesta(id_operacion) WHERE id_operacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_rol_miembro_por_rol ON uml_colaborativo.rol_miembro_proyecto(id_rol) WHERE revocado_en IS NULL;
CREATE INDEX IF NOT EXISTS ix_usuario_rol_por_rol ON uml_colaborativo.usuario_rol(id_rol) WHERE revocado_en IS NULL;

-- Solo funciones antiguas sin disparadores, sustituidas por FK compuestas.
-- RESTRICT implicito: si existe una dependencia real, la migracion se detiene.
DROP FUNCTION IF EXISTS uml_colaborativo.verificar_clases_relacion_modelo();
DROP FUNCTION IF EXISTS uml_colaborativo.verificar_jerarquia_paquete_modelo();
DROP FUNCTION IF EXISTS uml_colaborativo.verificar_paquete_clase_modelo();
DROP FUNCTION IF EXISTS uml_colaborativo.verificar_version_actual_modelo();

DO $$
DECLARE funcion text;
BEGIN
    FOREACH funcion IN ARRAY ARRAY['verificar_cambio_ambito_catalogo','verificar_padres_uml_activos',
        'propagar_borrado_logico_uml','proteger_borrado_paquete_no_vacio','exigir_contexto_edicion_uml','exigir_edicion_confirmada'] LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION uml_colaborativo.%I() FROM PUBLIC',funcion);
        EXECUTE format('GRANT EXECUTE ON FUNCTION uml_colaborativo.%I() TO uml_app',funcion);
    END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION uml_colaborativo.id_modelo_fila_uml(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION uml_colaborativo.id_modelo_fila_uml(text,jsonb) TO uml_app;
-- Los auxiliares invocados desde funciones DEFINER tampoco deben resolver
-- tablas temporales del llamante. pg_temp siempre se busca al final.
DO $$
DECLARE funcion record;
BEGIN
    IF has_schema_privilege('uml_app','uml_colaborativo','CREATE')
        OR has_schema_privilege('uml_app','public','CREATE') THEN
        RAISE EXCEPTION 'uml_app no debe poder crear objetos en esquemas usados por funciones privilegiadas';
    END IF;
    FOR funcion IN SELECT oid::regprocedure AS firma FROM pg_proc
        WHERE pronamespace='uml_colaborativo'::regnamespace LOOP
        EXECUTE format('ALTER FUNCTION %s SET search_path = pg_catalog, uml_colaborativo, public, pg_temp',funcion.firma);
    END LOOP;
END;
$$;
COMMENT ON SCHEMA uml_colaborativo IS 'Plataforma UML colaborativa en espanol; correcciones de desarrollo 2026-09-13 (18)';
CREATE OR REPLACE FUNCTION uml_colaborativo.construir_instantanea_modelo(p_id_modelo uuid) RETURNS jsonb
    LANGUAGE sql STABLE
    SET search_path TO 'pg_catalog', 'uml_colaborativo', 'public', 'pg_temp'
    AS $$
    SELECT jsonb_build_object(
        'formato_version', 2,
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
