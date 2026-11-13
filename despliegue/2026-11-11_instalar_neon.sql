-- Solo para una base NUEVA y vacía, conectado como propietario de Neon.
-- No carga usuarios, proyectos, conversaciones ni credenciales locales.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='uml_colaborativo') THEN
    RAISE EXCEPTION 'La base ya contiene uml_colaborativo; no se sobrescribe. Use otra base vacía.';
  END IF;
END $$;
-- Fuente: ../database/2026-09-12_00_cuenta_aplicacion.sql
-- Cuenta tecnica del backend; los usuarios humanos se guardan en usuario.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='uml_app') THEN
        CREATE ROLE uml_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    ELSIF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='uml_app'
        AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) THEN
        RAISE EXCEPTION 'uml_app existe con privilegios administrativos; use un servidor dedicado o revise el rol';
    END IF;
END;
$$;
ALTER ROLE uml_app SET search_path = uml_colaborativo, public;
-- La clave se asigna mediante el inicializador Docker o psql interactivo.

-- Fuente: ../database/2026-09-12_16_esquema_espanol.sql
--
-- PostgreSQL database dump
--

-- Esquema en espanol exportado del contenedor verificado. Compatible con pgAdmin.

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: uml_colaborativo; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA uml_colaborativo;


--
-- Name: SCHEMA uml_colaborativo; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA uml_colaborativo IS 'Plataforma UML colaborativa: nombres y codigos en espanol';


--
-- Name: citext; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA public;


--
-- Name: EXTENSION citext; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION citext IS 'data type for case-insensitive character strings';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: actualizar_fecha_modificacion(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.actualizar_fecha_modificacion() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.actualizado_en := clock_timestamp();
    RETURN NEW;
END;
$$;


--
-- Name: actualizar_fecha_y_version_fila(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.actualizado_en := clock_timestamp();
    NEW.version_fila := OLD.version_fila + 1;
    RETURN NEW;
END;
$$;


--
-- Name: comprobar_firmas_operaciones(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.comprobar_firmas_operaciones() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE target_class uuid;
BEGIN
    IF TG_TABLE_NAME = 'operacion_uml' THEN
        target_class := CASE WHEN TG_OP = 'DELETE' THEN OLD.id_clase ELSE NEW.id_clase END;
    ELSE
        SELECT id_clase INTO target_class FROM operacion_uml
        WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.id_operacion ELSE NEW.id_operacion END;
    END IF;
    PERFORM 1 FROM clase_uml WHERE id = target_class FOR UPDATE;
    IF EXISTS (SELECT 1 FROM v_firma_operacion WHERE id_clase = target_class
        GROUP BY lower(nombre), firma_parametros HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Firma de operacion duplicada' USING ERRCODE = '23505';
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: comprobar_propietario_proyecto(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.comprobar_propietario_proyecto() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE project_to_check uuid;
BEGIN
    IF TG_TABLE_NAME = 'proyecto' THEN
        project_to_check := NEW.id;
    ELSIF TG_TABLE_NAME = 'miembro_proyecto' THEN
        IF TG_OP = 'DELETE' THEN project_to_check := OLD.id_proyecto;
        ELSE project_to_check := NEW.id_proyecto; END IF;
    ELSE
        SELECT id_proyecto INTO project_to_check FROM miembro_proyecto
        WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.id_miembro_proyecto ELSE NEW.id_miembro_proyecto END;
    END IF;
    PERFORM verificar_propietario_proyecto(project_to_check);
    RETURN NULL;
END;
$$;


--
-- Name: comprobar_proyectos_propietario(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.comprobar_proyectos_propietario() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE owned_project uuid;
BEGIN
    FOR owned_project IN SELECT id FROM proyecto WHERE id_usuario_propietario = NEW.id LOOP
        PERFORM verificar_propietario_proyecto(owned_project);
    END LOOP;
    RETURN NULL;
END;
$$;


--
-- Name: construir_instantanea_modelo(uuid); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.construir_instantanea_modelo(p_id_modelo uuid) RETURNS jsonb
    LANGUAGE sql STABLE
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
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
            WHERE r.id_modelo = m.id AND r.eliminado_en IS NULL), '[]'::jsonb)
    ) FROM modelo_uml m WHERE m.id = p_id_modelo;
$$;


--
-- Name: crear_proyecto_con_propietario(uuid, text, text, text, text); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.crear_proyecto_con_propietario(p_id_usuario_propietario uuid, p_codigo_proyecto text, p_nombre_proyecto text, p_descripcion_proyecto text DEFAULT NULL::text, p_nombre_modelo_inicial text DEFAULT 'Modelo principal'::text) RETURNS TABLE(id_proyecto uuid, id_modelo uuid)
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE
    new_project_id uuid;
    new_member_id uuid;
    new_model_id uuid;
    owner_role_id uuid;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM usuario
        WHERE id = p_id_usuario_propietario AND estado = 'ACTIVO'
    ) THEN
        RAISE EXCEPTION 'El propietario debe ser un usuario activo';
    END IF;

    SELECT id INTO owner_role_id
    FROM rol
    WHERE codigo = 'PROPIETARIO_PROYECTO' AND estado = 'ACTIVO';

    IF owner_role_id IS NULL THEN
        RAISE EXCEPTION 'No existe el rol activo PROJECT_OWNER';
    END IF;

    INSERT INTO proyecto (codigo, nombre, descripcion, id_usuario_propietario)
    VALUES (p_codigo_proyecto, p_nombre_proyecto, p_descripcion_proyecto, p_id_usuario_propietario)
    RETURNING id INTO new_project_id;

    INSERT INTO miembro_proyecto (id_proyecto, id_usuario, estado, incorporado_en)
    VALUES (new_project_id, p_id_usuario_propietario, 'ACTIVO', clock_timestamp())
    RETURNING id INTO new_member_id;

    INSERT INTO rol_miembro_proyecto (id_miembro_proyecto, id_rol, asignado_por)
    VALUES (new_member_id, owner_role_id, p_id_usuario_propietario);

    INSERT INTO modelo_uml (id_proyecto, nombre, creado_por)
    VALUES (new_project_id, p_nombre_modelo_inicial, p_id_usuario_propietario)
    RETURNING id INTO new_model_id;

    INSERT INTO actividad_proyecto (
        id_proyecto, id_modelo, id_usuario_actor, categoria, accion, tipo_entidad, id_entidad
    )
    VALUES (
        new_project_id, new_model_id, p_id_usuario_propietario,
        'PROYECTO', 'PROYECTO_CREADO', 'PROYECTO', new_project_id
    );

    RETURN QUERY SELECT new_project_id, new_model_id;
END;
$$;


--
-- Name: guardar_version_modelo(uuid, bigint, uuid, text); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.guardar_version_modelo(p_id_modelo uuid, p_revision_reservada bigint, p_actor uuid, p_resumen text) RETURNS uuid
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE model_row modelo_uml%ROWTYPE; version_id uuid; version_no integer;
BEGIN
    SELECT * INTO model_row FROM modelo_uml WHERE id = p_id_modelo FOR UPDATE;
    IF NOT FOUND OR model_row.revision_actual <> p_revision_reservada THEN
        RAISE EXCEPTION 'La revision reservada ya no es vigente' USING ERRCODE = '40001';
    END IF;
    IF NOT tiene_permiso_proyecto(p_actor, model_row.id_proyecto, 'EDITAR_MODELO') THEN
        RAISE EXCEPTION 'Sin permiso para guardar el modelo' USING ERRCODE = '42501';
    END IF;
    SELECT COALESCE(max(numero_version), 0) + 1 INTO version_no
    FROM version_modelo_uml WHERE id_modelo = p_id_modelo;
    INSERT INTO version_modelo_uml(id_modelo, numero_version, revision, id_version_padre,
        instantanea, creado_por, resumen_cambio)
    VALUES(p_id_modelo, version_no, p_revision_reservada, model_row.id_version_actual,
        construir_instantanea_modelo(p_id_modelo), p_actor, p_resumen) RETURNING id INTO version_id;
    UPDATE modelo_uml SET id_version_actual = version_id WHERE id = p_id_modelo;
    INSERT INTO actividad_proyecto(id_proyecto, id_modelo, id_version_modelo, id_usuario_actor, categoria, accion)
    VALUES(model_row.id_proyecto, p_id_modelo, version_id, p_actor, 'MODELO', 'VERSION_GUARDADA');
    RETURN version_id;
END;
$$;


--
-- Name: impedir_cambio_identidad(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.impedir_cambio_identidad() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE field_name text;
BEGIN
    FOREACH field_name IN ARRAY TG_ARGV LOOP
        IF to_jsonb(NEW)->field_name IS DISTINCT FROM to_jsonb(OLD)->field_name THEN
            RAISE EXCEPTION 'No se cambia la identidad ni el contenedor de un registro existente (%)', field_name
                USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$;


--
-- Name: impedir_ciclo_generalizacion(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.impedir_ciclo_generalizacion() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
BEGIN
    IF NEW.tipo_relacion <> 'GENERALIZACION' OR NEW.eliminado_en IS NOT NULL THEN RETURN NEW; END IF;
    PERFORM 1 FROM modelo_uml WHERE id = NEW.id_modelo FOR UPDATE;
    IF EXISTS (
        WITH RECURSIVE ancestors AS (
            SELECT id_clase_destino FROM relacion_uml
            WHERE id_clase_origen = NEW.id_clase_destino AND tipo_relacion = 'GENERALIZACION'
                AND eliminado_en IS NULL AND id <> NEW.id
            UNION
            SELECT r.id_clase_destino FROM relacion_uml r
            JOIN ancestors a ON a.id_clase_destino = r.id_clase_origen
            WHERE r.tipo_relacion = 'GENERALIZACION' AND r.eliminado_en IS NULL AND r.id <> NEW.id
        ) SELECT 1 FROM ancestors WHERE id_clase_destino = NEW.id_clase_origen
    ) THEN
        RAISE EXCEPTION 'La generalizacion no puede contener ciclos' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: impedir_ciclo_paquetes(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.impedir_ciclo_paquetes() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
BEGIN
    PERFORM 1 FROM modelo_uml WHERE id = NEW.id_modelo FOR UPDATE;
    IF NEW.id_paquete_padre IS NOT NULL AND EXISTS (
        WITH RECURSIVE ancestors AS (
            SELECT id, id_paquete_padre FROM paquete_uml WHERE id = NEW.id_paquete_padre
            UNION
            SELECT p.id, p.id_paquete_padre FROM paquete_uml p JOIN ancestors a ON p.id = a.id_paquete_padre
        ) SELECT 1 FROM ancestors WHERE id = NEW.id
    ) THEN
        RAISE EXCEPTION 'La jerarquia de paquetes no puede contener ciclos' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: iniciar_edicion_modelo(uuid, bigint, uuid); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.iniciar_edicion_modelo(p_id_modelo uuid, p_revision_esperada bigint, p_actor uuid) RETURNS bigint
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
BEGIN
    IF NOT tiene_permiso_proyecto(p_actor,
        (SELECT id_proyecto FROM modelo_uml WHERE id = p_id_modelo), 'EDITAR_MODELO') THEN
        RAISE EXCEPTION 'Sin permiso para editar el modelo' USING ERRCODE = '42501';
    END IF;
    RETURN reservar_revision_modelo(p_id_modelo, p_revision_esperada);
END;
$$;


--
-- Name: proteger_ambito_permiso_excepcional(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_ambito_permiso_excepcional() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM permiso WHERE id = NEW.id_permiso AND ambito = 'GLOBAL') THEN
        RAISE EXCEPTION 'Un proyecto no puede conceder permisos globales' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: proteger_ambito_rol(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_ambito_rol() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE assigned_scope text; permission_scope text; role_code text;
BEGIN
    SELECT ambito, codigo INTO assigned_scope, role_code FROM rol WHERE id = NEW.id_rol;
    IF TG_TABLE_NAME = 'usuario_rol' AND assigned_scope NOT IN ('GLOBAL', 'AMBOS') THEN
        RAISE EXCEPTION 'Un rol de proyecto no se asigna globalmente' USING ERRCODE = '23514';
    ELSIF TG_TABLE_NAME = 'rol_miembro_proyecto' AND assigned_scope NOT IN ('PROYECTO', 'AMBOS') THEN
        RAISE EXCEPTION 'Un rol global no se asigna desde un proyecto' USING ERRCODE = '23514';
    ELSIF TG_TABLE_NAME = 'rol_permiso' THEN
        SELECT ambito INTO permission_scope FROM permiso WHERE id = NEW.id_permiso;
        IF role_code <> 'SUPERADMINISTRADOR' AND assigned_scope <> 'AMBOS'
            AND permission_scope <> 'AMBOS' AND assigned_scope <> permission_scope THEN
            RAISE EXCEPTION 'El permiso esta fuera del ambito del rol' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: proteger_configuracion_utilizada(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_configuracion_utilizada() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE config_to_check uuid;
BEGIN
    IF TG_TABLE_NAME = 'configuracion_generacion_backend' THEN
        config_to_check := OLD.id;
    ELSIF TG_OP = 'INSERT' THEN config_to_check := NEW.id_configuracion;
    ELSE config_to_check := OLD.id_configuracion; END IF;
    PERFORM 1 FROM configuracion_generacion_backend WHERE id = config_to_check FOR UPDATE;
    IF EXISTS (SELECT 1 FROM generacion_backend WHERE id_configuracion = config_to_check) THEN
        RAISE EXCEPTION 'La configuracion ya utilizada es inmutable; cree una nueva configuracion'
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;


--
-- Name: proteger_detalles_propuesta(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_detalles_propuesta() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE proposal_to_check uuid; proposal_status text;
BEGIN
    IF TG_TABLE_NAME = 'dependencia_operacion_propuesta' THEN
        SELECT id_propuesta INTO proposal_to_check FROM operacion_propuesta
        WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.id_operacion ELSE NEW.id_operacion END;
    ELSE
        proposal_to_check := CASE WHEN TG_OP = 'DELETE' THEN OLD.id_propuesta ELSE NEW.id_propuesta END;
    END IF;
    SELECT estado INTO proposal_status FROM propuesta_inteligente
    WHERE id = proposal_to_check FOR UPDATE;
    IF proposal_status IN ('APLICADO', 'RECHAZADO') THEN
        RAISE EXCEPTION 'Las operaciones de una propuesta decidida son inmutables' USING ERRCODE = '23514';
    END IF;
    IF TG_TABLE_NAME = 'ambiguedad_propuesta' AND TG_OP <> 'DELETE' THEN
        IF NEW.id_operacion IS NOT NULL AND NOT EXISTS (SELECT 1 FROM operacion_propuesta
            WHERE id = NEW.id_operacion AND id_propuesta = proposal_to_check) THEN
            RAISE EXCEPTION 'La ambiguedad debe referir a una operacion de su propuesta' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;


--
-- Name: proteger_estado_propuesta(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_estado_propuesta() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE model_to_check uuid; request_revision bigint; version_revision bigint;
BEGIN
    SELECT id_modelo, revision_base INTO model_to_check, request_revision
    FROM solicitud_inteligente WHERE id = NEW.id_solicitud;
    IF TG_OP = 'UPDATE' AND OLD.estado IN ('APLICADO', 'RECHAZADO') AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Una propuesta decidida es inmutable' USING ERRCODE = '23514';
    END IF;
    IF NEW.estado = 'APLICADO' THEN
        SELECT revision INTO version_revision FROM version_modelo_uml
        WHERE id = NEW.id_version_aplicada AND id_modelo = model_to_check;
        IF version_revision IS NULL OR version_revision <> request_revision + 1
            OR NOT tiene_permiso_proyecto(NEW.aplicado_por,
                (SELECT id_proyecto FROM modelo_uml WHERE id = model_to_check), 'REVISAR_PROPUESTA') THEN
            RAISE EXCEPTION 'Version aplicada incompatible o usuario no autorizado' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: proteger_historial_cambio(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_historial_cambio() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Los cambios se conservan para trazabilidad' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.estado IN ('APLICADO', 'RECHAZADO') AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Un cambio finalizado es inmutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: proteger_registro_inmutable(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_registro_inmutable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Registro de auditoria inmutable; no se modifica ni elimina' USING ERRCODE = '23514';
END;
$$;


--
-- Name: proteger_rol_sistema(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_rol_sistema() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.es_del_sistema AND (TG_OP = 'DELETE' OR
        NEW.codigo IS DISTINCT FROM OLD.codigo OR NEW.ambito IS DISTINCT FROM OLD.ambito
        OR NEW.estado <> 'ACTIVO' OR NOT NEW.es_del_sistema) THEN
        RAISE EXCEPTION 'No se elimina, desactiva ni cambia el ambito de un rol protegido'
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;


--
-- Name: proteger_version_modelo(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.proteger_version_modelo() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE current_rev bigint; parent_rev bigint;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'El historial se conserva; archive el modelo' USING ERRCODE = '23514';
    ELSIF TG_OP = 'UPDATE' THEN
        IF (to_jsonb(NEW) - 'estado_validacion') IS DISTINCT FROM (to_jsonb(OLD) - 'estado_validacion') THEN
            RAISE EXCEPTION 'El contenido de una version es inmutable' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT revision_actual INTO current_rev FROM modelo_uml WHERE id = NEW.id_modelo FOR UPDATE;
    IF NEW.revision IS DISTINCT FROM current_rev THEN
        RAISE EXCEPTION 'La version debe capturar la revision vigente' USING ERRCODE = '23514';
    END IF;
    IF NEW.id_version_padre IS NOT NULL THEN
        SELECT revision INTO parent_rev FROM version_modelo_uml WHERE id = NEW.id_version_padre;
        IF parent_rev >= NEW.revision THEN
            RAISE EXCEPTION 'La version padre debe ser anterior' USING ERRCODE = '23514';
        END IF;
    END IF;
    NEW.hash_instantanea_sha256 := encode(digest(NEW.instantanea::text, 'sha256'), 'hex');
    RETURN NEW;
END;
$$;


--
-- Name: reservar_revision_modelo(uuid, bigint); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.reservar_revision_modelo(p_id_modelo uuid, p_revision_esperada bigint) RETURNS bigint
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE result_revision bigint;
BEGIN
    UPDATE modelo_uml m SET revision_actual = revision_actual + 1, estado = 'BORRADOR'
    WHERE m.id = p_id_modelo AND revision_actual = p_revision_esperada AND m.estado <> 'ARCHIVADO'
        AND EXISTS (SELECT 1 FROM proyecto p WHERE p.id = m.id_proyecto AND p.estado = 'ACTIVO')
    RETURNING revision_actual INTO result_revision;
    IF result_revision IS NULL THEN
        RAISE EXCEPTION 'Revision obsoleta, proyecto inactivo o modelo archivado' USING ERRCODE = '40001';
    END IF;
    RETURN result_revision;
END;
$$;


--
-- Name: tiene_permiso_proyecto(uuid, uuid, text); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.tiene_permiso_proyecto(p_id_usuario uuid, p_id_proyecto uuid, p_permiso text) RETURNS boolean
    LANGUAGE sql STABLE
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
    SELECT EXISTS (
        SELECT 1 FROM v_permiso_efectivo_miembro
        WHERE id_usuario = p_id_usuario AND id_proyecto = p_id_proyecto
            AND codigo_permiso = p_permiso AND esta_permitido
    );
$$;


--
-- Name: verificar_ambito_archivo(uuid, uuid, text); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_ambito_archivo(p_archivo uuid, p_proyecto uuid, p_categoria text) RETURNS void
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
BEGIN
    IF p_archivo IS NULL THEN RETURN; END IF;
    IF NOT EXISTS (SELECT 1 FROM archivo_almacenado WHERE id = p_archivo AND id_proyecto = p_proyecto
        AND categoria = p_categoria AND eliminado_en IS NULL) THEN
        RAISE EXCEPTION 'Archivo inexistente, eliminado, de categoria o proyecto incorrectos' USING ERRCODE = '23514';
    END IF;
END;
$$;


--
-- Name: verificar_archivos_operacion(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_archivos_operacion() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE project_to_check uuid;
BEGIN
    IF TG_TABLE_NAME IN ('solicitud_inteligente', 'intercambio_xmi') THEN
        SELECT id_proyecto INTO project_to_check FROM modelo_uml WHERE id = NEW.id_modelo;
        IF TG_TABLE_NAME = 'solicitud_inteligente' THEN
            IF NEW.tipo_entrada = 'VOZ' THEN
                PERFORM verificar_ambito_archivo(NEW.id_archivo_origen, project_to_check, 'AUDIO_VOZ');
            ELSIF NEW.tipo_entrada = 'IMAGEN' THEN
                PERFORM verificar_ambito_archivo(NEW.id_archivo_origen, project_to_check, 'IMAGEN_DIAGRAMA');
            ELSIF NEW.id_archivo_origen IS NOT NULL THEN
                RAISE EXCEPTION 'La solicitud TEXT no necesita un archivo de entrada' USING ERRCODE = '23514';
            END IF;
        ELSE
            PERFORM verificar_ambito_archivo(NEW.id_archivo_entrada, project_to_check, 'ENTRADA_XMI');
            PERFORM verificar_ambito_archivo(NEW.id_archivo_salida, project_to_check, 'SALIDA_XMI');
        END IF;
    ELSE
        SELECT m.id_proyecto INTO project_to_check FROM configuracion_generacion_backend cfg
        JOIN version_modelo_uml v ON v.id = cfg.id_version_modelo
        JOIN modelo_uml m ON m.id = v.id_modelo WHERE cfg.id = NEW.id_configuracion;
        PERFORM verificar_ambito_archivo(NEW.id_archivo_salida, project_to_check, 'BACKEND_GENERADO');
        PERFORM verificar_ambito_archivo(NEW.id_archivo_registro, project_to_check, 'REGISTRO_COMPILACION');
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_clase_configuracion_modelo(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_clase_configuracion_modelo() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE config_model uuid; class_model uuid;
BEGIN
    SELECT v.id_modelo INTO config_model FROM configuracion_generacion_backend cfg
    JOIN version_modelo_uml v ON v.id = cfg.id_version_modelo WHERE cfg.id = NEW.id_configuracion;
    SELECT id_modelo INTO class_model FROM clase_uml WHERE id = NEW.id_clase;
    IF config_model IS DISTINCT FROM class_model THEN
        RAISE EXCEPTION 'La clase seleccionada debe pertenecer al modelo de la configuracion'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_clases_relacion_modelo(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_clases_relacion_modelo() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    source_model_id uuid;
    target_model_id uuid;
BEGIN
    SELECT id_modelo INTO source_model_id FROM clase_uml WHERE id = NEW.id_clase_origen;
    SELECT id_modelo INTO target_model_id FROM clase_uml WHERE id = NEW.id_clase_destino;
    IF source_model_id IS DISTINCT FROM NEW.id_modelo
       OR target_model_id IS DISTINCT FROM NEW.id_modelo THEN
        RAISE EXCEPTION 'Los extremos de una relacion deben pertenecer al mismo modelo UML';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_configuracion_generacion(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_configuracion_generacion() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE cfg configuracion_generacion_backend%ROWTYPE; version_status text;
BEGIN
    SELECT * INTO cfg FROM configuracion_generacion_backend WHERE id = NEW.id_configuracion FOR UPDATE;
    SELECT estado_validacion INTO version_status FROM version_modelo_uml WHERE id = cfg.id_version_modelo;
    IF cfg.estado IS DISTINCT FROM 'VALIDO' OR version_status IS NULL
        OR version_status NOT IN ('VALIDO', 'VALIDO_CON_ADVERTENCIAS')
        OR NOT cfg.generar_repositorios OR NOT cfg.generar_controladores OR NOT cfg.generar_dtos
        OR NOT EXISTS (SELECT 1 FROM clase_configuracion_backend WHERE id_configuracion = cfg.id) THEN
        RAISE EXCEPTION 'La generacion requiere version validada, configuracion valida, clases y capas obligatorias'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_dependencia_propuesta(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_dependencia_propuesta() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE own_proposal uuid; dependency_proposal uuid;
BEGIN
    SELECT id_propuesta INTO own_proposal FROM operacion_propuesta WHERE id = NEW.id_operacion;
    SELECT id_propuesta INTO dependency_proposal FROM operacion_propuesta WHERE id = NEW.id_operacion_requerida;
    IF own_proposal IS DISTINCT FROM dependency_proposal THEN
        RAISE EXCEPTION 'Las dependencias deben pertenecer a la misma propuesta' USING ERRCODE = '23514';
    END IF;
    PERFORM 1 FROM propuesta_inteligente WHERE id = own_proposal FOR UPDATE;
    IF EXISTS (
        WITH RECURSIVE dependencies AS (
            SELECT id_operacion_requerida FROM dependencia_operacion_propuesta
            WHERE id_operacion = NEW.id_operacion_requerida
            UNION
            SELECT d.id_operacion_requerida FROM dependencia_operacion_propuesta d
            JOIN dependencies p ON p.id_operacion_requerida = d.id_operacion
        ) SELECT 1 FROM dependencies WHERE id_operacion_requerida = NEW.id_operacion
    ) THEN
        RAISE EXCEPTION 'Las dependencias de operaciones no pueden contener ciclos' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_descarga_artefacto(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_descarga_artefacto() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE job_row generacion_backend%ROWTYPE; project_to_check uuid; latest_validation text;
BEGIN
    SELECT * INTO job_row FROM generacion_backend WHERE id = NEW.id_generacion;
    SELECT m.id_proyecto INTO project_to_check FROM configuracion_generacion_backend cfg
    JOIN version_modelo_uml v ON v.id = cfg.id_version_modelo JOIN modelo_uml m ON m.id = v.id_modelo
    WHERE cfg.id = job_row.id_configuracion;
    SELECT estado INTO latest_validation FROM validacion_backend
    WHERE id_generacion = job_row.id ORDER BY iniciado_en DESC, id DESC LIMIT 1;
    IF job_row.estado IS DISTINCT FROM 'GENERADO' OR NEW.id_archivo IS DISTINCT FROM job_row.id_archivo_salida
        OR latest_validation IS NULL OR latest_validation NOT IN ('VALIDO', 'VALIDO_CON_ADVERTENCIAS')
        OR NOT tiene_permiso_proyecto(NEW.descargado_por, project_to_check, 'DESCARGAR_ARTEFACTO') THEN
        RAISE EXCEPTION 'Descarga no autorizada o backend sin validacion satisfactoria' USING ERRCODE = '23514';
    END IF;
    PERFORM verificar_ambito_archivo(NEW.id_archivo, project_to_check, 'BACKEND_GENERADO');
    RETURN NEW;
END;
$$;


--
-- Name: verificar_jerarquia_paquete_modelo(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_jerarquia_paquete_modelo() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    parent_model_id uuid;
BEGIN
    IF NEW.id_paquete_padre IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT id_modelo INTO parent_model_id FROM paquete_uml WHERE id = NEW.id_paquete_padre;
    IF parent_model_id IS DISTINCT FROM NEW.id_modelo THEN
        RAISE EXCEPTION 'El paquete padre debe pertenecer al mismo modelo UML';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_mapeo_instantanea(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_mapeo_instantanea() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE snapshot_value jsonb; element_id uuid; array_name text;
BEGIN
    SELECT v.instantanea INTO snapshot_value FROM configuracion_generacion_backend cfg
    JOIN version_modelo_uml v ON v.id = cfg.id_version_modelo WHERE cfg.id = NEW.id_configuracion;
    IF TG_TABLE_NAME = 'clase_configuracion_backend' THEN element_id := NEW.id_clase; array_name := 'clases';
    ELSIF NEW.id_atributo_uml IS NOT NULL THEN element_id := NEW.id_atributo_uml; array_name := 'atributos';
    ELSE element_id := NEW.id_relacion_uml; array_name := 'relaciones'; END IF;
    IF NOT EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(snapshot_value->array_name, snapshot_value->(CASE array_name WHEN 'clases' THEN 'classes' WHEN 'atributos' THEN 'attributes' WHEN 'relaciones' THEN 'relations' END))) e
        WHERE e->>'id' = element_id::text) THEN
        RAISE EXCEPTION 'El elemento no existe en la version congelada de la configuracion' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_paquete_clase_modelo(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_paquete_clase_modelo() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    package_model_id uuid;
BEGIN
    IF NEW.id_paquete IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT id_modelo INTO package_model_id FROM paquete_uml WHERE id = NEW.id_paquete;
    IF package_model_id IS DISTINCT FROM NEW.id_modelo THEN
        RAISE EXCEPTION 'La clase y su paquete deben pertenecer al mismo modelo UML';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: verificar_propietario_proyecto(uuid); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_propietario_proyecto(p_id_proyecto uuid) RETURNS void
    LANGUAGE plpgsql
    SET search_path TO 'uml_colaborativo', 'pg_catalog', 'public'
    AS $$
DECLARE owner_id uuid;
BEGIN
    SELECT id_usuario_propietario INTO owner_id FROM proyecto WHERE id = p_id_proyecto FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM miembro_proyecto pm
        JOIN usuario u ON u.id = pm.id_usuario AND u.estado = 'ACTIVO'
        JOIN rol_miembro_proyecto pmr ON pmr.id_miembro_proyecto = pm.id AND pmr.revocado_en IS NULL
        JOIN rol r ON r.id = pmr.id_rol AND r.codigo = 'PROPIETARIO_PROYECTO' AND r.estado = 'ACTIVO'
        WHERE pm.id_proyecto = p_id_proyecto AND pm.id_usuario = owner_id AND pm.estado = 'ACTIVO'
    ) THEN
        RAISE EXCEPTION 'El proyecto debe conservar un propietario activo con membresia y rol'
            USING ERRCODE = '23514';
    END IF;
END;
$$;


--
-- Name: verificar_version_actual_modelo(); Type: FUNCTION; Schema: uml_colaborativo; Owner: -
--

CREATE FUNCTION uml_colaborativo.verificar_version_actual_modelo() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    version_model_id uuid;
BEGIN
    IF NEW.id_version_actual IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT id_modelo INTO version_model_id
    FROM version_modelo_uml
    WHERE id = NEW.id_version_actual;
    IF version_model_id IS DISTINCT FROM NEW.id THEN
        RAISE EXCEPTION 'La version vigente debe pertenecer al mismo modelo UML';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: actividad_proyecto; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.actividad_proyecto (
    id bigint NOT NULL,
    id_proyecto uuid NOT NULL,
    id_modelo uuid,
    id_version_modelo uuid,
    id_usuario_actor uuid,
    categoria character varying(64) NOT NULL,
    accion character varying(80) NOT NULL,
    tipo_entidad character varying(64),
    id_entidad uuid,
    valor_anterior jsonb,
    valor_posterior jsonb,
    detalle jsonb DEFAULT '{}'::jsonb NOT NULL,
    ocurrido_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_actividad_proyecto_01 CHECK ((jsonb_typeof(detalle) = 'object'::text)),
    CONSTRAINT ck_actividad_proyecto_02 CHECK (((id_version_modelo IS NULL) OR (id_modelo IS NOT NULL)))
);


--
-- Name: actividad_proyecto_id_seq; Type: SEQUENCE; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE uml_colaborativo.actividad_proyecto ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME uml_colaborativo.actividad_proyecto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ambiguedad_propuesta; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.ambiguedad_propuesta (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_propuesta uuid NOT NULL,
    id_operacion uuid,
    descripcion text NOT NULL,
    resolucion text,
    resuelto_por uuid,
    resuelto_en timestamp with time zone,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL
);


--
-- Name: archivo_almacenado; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.archivo_almacenado (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_proyecto uuid,
    cargado_por uuid,
    categoria character varying(64) NOT NULL,
    proveedor_almacenamiento character varying(40) NOT NULL,
    clave_almacenamiento text NOT NULL,
    nombre_original character varying(255) NOT NULL,
    tipo_contenido character varying(150) NOT NULL,
    tamano_bytes bigint NOT NULL,
    sha256 character(64) NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    eliminado_en timestamp with time zone,
    CONSTRAINT ck_archivo_almacenado_01 CHECK (((categoria)::text = ANY (ARRAY[('IMAGEN_DIAGRAMA'::character varying)::text, ('AUDIO_VOZ'::character varying)::text, ('ENTRADA_XMI'::character varying)::text, ('SALIDA_XMI'::character varying)::text, ('BACKEND_GENERADO'::character varying)::text, ('INFORME_VALIDACION'::character varying)::text, ('REGISTRO_COMPILACION'::character varying)::text, ('OTRO'::character varying)::text]))),
    CONSTRAINT ck_archivo_almacenado_02 CHECK ((tamano_bytes >= 0)),
    CONSTRAINT ck_archivo_almacenado_03 CHECK ((sha256 ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: atributo_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.atributo_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_clase uuid NOT NULL,
    nombre character varying(180) NOT NULL,
    tipo_uml character varying(180) NOT NULL,
    visibilidad character varying(64) DEFAULT 'PRIVADO'::character varying NOT NULL,
    valor_predeterminado text,
    multiplicidad_minima integer DEFAULT 1 NOT NULL,
    multiplicidad_maxima integer DEFAULT 1,
    es_identificador boolean DEFAULT false NOT NULL,
    es_unico boolean DEFAULT false NOT NULL,
    es_estatico boolean DEFAULT false NOT NULL,
    restricciones_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    orden integer DEFAULT 0 NOT NULL,
    id_externo_xmi character varying(255),
    version_fila bigint DEFAULT 1 NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    eliminado_en timestamp with time zone,
    CONSTRAINT ck_atributo_uml_01 CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text]))),
    CONSTRAINT ck_atributo_uml_02 CHECK ((multiplicidad_minima >= 0)),
    CONSTRAINT ck_atributo_uml_03 CHECK (((multiplicidad_maxima IS NULL) OR (multiplicidad_maxima >= 0))),
    CONSTRAINT ck_atributo_uml_04 CHECK ((jsonb_typeof(restricciones_json) = 'object'::text)),
    CONSTRAINT ck_atributo_uml_05 CHECK ((orden >= 0)),
    CONSTRAINT ck_atributo_uml_06 CHECK (((multiplicidad_maxima IS NULL) OR (multiplicidad_maxima >= multiplicidad_minima))),
    CONSTRAINT ck_atributo_uml_07 CHECK (((nombre)::text <> ''::text)),
    CONSTRAINT ck_atributo_uml_08 CHECK (((tipo_uml)::text <> ''::text)),
    CONSTRAINT ck_atributo_uml_09 CHECK (((NOT es_identificador) OR ((multiplicidad_minima = 1) AND (multiplicidad_maxima = 1) AND (NOT es_estatico))))
);


--
-- Name: cambio_modelo; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.cambio_modelo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    id_sesion_edicion uuid,
    id_usuario_actor uuid NOT NULL,
    id_cambio_cliente uuid NOT NULL,
    revision_base bigint NOT NULL,
    revision_aplicada bigint,
    tipo_operacion character varying(64) NOT NULL,
    tipo_entidad character varying(64) NOT NULL,
    id_entidad uuid,
    parche jsonb NOT NULL,
    estado character varying(64) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    aplicado_en timestamp with time zone,
    CONSTRAINT ck_cambio_modelo_01 CHECK ((revision_base >= 0)),
    CONSTRAINT ck_cambio_modelo_02 CHECK ((revision_aplicada >= 0)),
    CONSTRAINT ck_cambio_modelo_03 CHECK (((tipo_operacion)::text = ANY (ARRAY[('CREAR'::character varying)::text, ('ACTUALIZAR'::character varying)::text, ('ELIMINAR'::character varying)::text, ('MOVER'::character varying)::text, ('RESTAURAR'::character varying)::text, ('IMPORTAR'::character varying)::text]))),
    CONSTRAINT ck_cambio_modelo_04 CHECK ((jsonb_typeof(parche) = ANY (ARRAY['object'::text, 'array'::text]))),
    CONSTRAINT ck_cambio_modelo_05 CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text, ('CONFLICTO'::character varying)::text]))),
    CONSTRAINT ck_cambio_modelo_06 CHECK (((((estado)::text = 'APLICADO'::text) = (revision_aplicada IS NOT NULL)) AND (((estado)::text <> 'APLICADO'::text) OR ((aplicado_en IS NOT NULL) AND (revision_aplicada = (revision_base + 1))))))
);


--
-- Name: clase_configuracion_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.clase_configuracion_backend (
    id_configuracion uuid NOT NULL,
    id_clase uuid NOT NULL,
    es_persistente boolean DEFAULT true NOT NULL,
    exponer_rest boolean DEFAULT true NOT NULL,
    nombre_tabla character varying(63),
    nombre_entidad character varying(180)
);


--
-- Name: clase_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.clase_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    id_paquete uuid,
    nombre character varying(180) NOT NULL,
    descripcion text,
    estereotipo character varying(100),
    visibilidad character varying(64) DEFAULT 'PUBLICO'::character varying NOT NULL,
    es_abstracto boolean DEFAULT false NOT NULL,
    es_persistente boolean DEFAULT true NOT NULL,
    id_externo_xmi character varying(255),
    version_fila bigint DEFAULT 1 NOT NULL,
    creado_por uuid,
    actualizado_por uuid,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    eliminado_en timestamp with time zone,
    CONSTRAINT ck_clase_uml_01 CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text]))),
    CONSTRAINT ck_clase_uml_02 CHECK (((nombre)::text <> ''::text))
);


--
-- Name: comprobacion_servicio; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.comprobacion_servicio (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_servicio uuid NOT NULL,
    estado character varying(64) NOT NULL,
    latencia_ms integer,
    detalle text,
    comprobado_por uuid,
    comprobado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_comprobacion_servicio_01 CHECK (((estado)::text = ANY (ARRAY[('DISPONIBLE'::character varying)::text, ('NO_DISPONIBLE'::character varying)::text, ('DEGRADADO'::character varying)::text]))),
    CONSTRAINT ck_comprobacion_servicio_02 CHECK ((latencia_ms >= 0))
);


--
-- Name: configuracion_generacion_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.configuracion_generacion_backend (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_version_modelo uuid NOT NULL,
    nombre character varying(180) NOT NULL,
    nombre_proyecto character varying(180) NOT NULL,
    id_grupo character varying(255) NOT NULL,
    id_artefacto character varying(255) NOT NULL,
    paquete_base character varying(255) NOT NULL,
    version_inicial character varying(60) DEFAULT '0.0.1-SNAPSHOT'::character varying NOT NULL,
    version_java character varying(20) NOT NULL,
    version_spring_boot character varying(30) NOT NULL,
    motor_base_datos character varying(20) DEFAULT 'POSTGRESQL'::character varying NOT NULL,
    herramienta_migracion character varying(16) DEFAULT 'FLYWAY'::character varying NOT NULL,
    generar_repositorios boolean DEFAULT true NOT NULL,
    generar_servicios boolean DEFAULT true NOT NULL,
    generar_controladores boolean DEFAULT true NOT NULL,
    generar_dtos boolean DEFAULT true NOT NULL,
    exponer_rest boolean DEFAULT true NOT NULL,
    plantilla_conexion jsonb DEFAULT '{}'::jsonb NOT NULL,
    estado character varying(64) DEFAULT 'BORRADOR'::character varying NOT NULL,
    creado_por uuid NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_configuracion_generacion_backend_01 CHECK (((id_grupo)::text ~ '^[A-Za-z_][A-Za-z0-9_.]*$'::text)),
    CONSTRAINT ck_configuracion_generacion_backend_02 CHECK (((motor_base_datos)::text = 'POSTGRESQL'::text)),
    CONSTRAINT ck_configuracion_generacion_backend_03 CHECK (((herramienta_migracion)::text = ANY (ARRAY[('FLYWAY'::character varying)::text, ('LIQUIBASE'::character varying)::text, ('NINGUNO'::character varying)::text]))),
    CONSTRAINT ck_configuracion_generacion_backend_04 CHECK ((jsonb_typeof(plantilla_conexion) = 'object'::text)),
    CONSTRAINT ck_configuracion_generacion_backend_05 CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('VALIDO'::character varying)::text, ('INVALIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text, ('ARCHIVADO'::character varying)::text]))),
    CONSTRAINT ck_configuracion_generacion_backend_06 CHECK (((id_artefacto)::text ~ '^[A-Za-z0-9_.-]+$'::text)),
    CONSTRAINT ck_configuracion_generacion_backend_07 CHECK (((paquete_base)::text ~ '^[a-z_][a-z0-9_.]*$'::text))
);


--
-- Name: conflicto_edicion; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.conflicto_edicion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    tipo_entidad character varying(64) NOT NULL,
    id_entidad uuid,
    revision_base bigint NOT NULL,
    id_cambio_almacenado uuid,
    id_cambio_entrante uuid,
    valor_almacenado jsonb NOT NULL,
    valor_entrante jsonb NOT NULL,
    estado character varying(64) DEFAULT 'ABIERTO'::character varying NOT NULL,
    tipo_resolucion character varying(64),
    valor_resolucion jsonb,
    detectado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    resuelto_por uuid,
    resuelto_en timestamp with time zone,
    CONSTRAINT ck_conflicto_edicion_01 CHECK ((revision_base >= 0)),
    CONSTRAINT ck_conflicto_edicion_02 CHECK (((estado)::text = ANY (ARRAY[('ABIERTO'::character varying)::text, ('RESUELTO'::character varying)::text, ('DESCARTADO'::character varying)::text]))),
    CONSTRAINT ck_conflicto_edicion_03 CHECK (((tipo_resolucion IS NULL) OR ((tipo_resolucion)::text = ANY (ARRAY[('CONSERVAR_ALMACENADO'::character varying)::text, ('APLICAR_ENTRANTE'::character varying)::text, ('FUSIONAR'::character varying)::text])))),
    CONSTRAINT ck_conflicto_edicion_04 CHECK ((((estado)::text = 'RESUELTO'::text) = (resuelto_en IS NOT NULL)))
);


--
-- Name: dependencia_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.dependencia_backend (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_configuracion uuid NOT NULL,
    id_grupo character varying(255) NOT NULL,
    id_artefacto character varying(255) NOT NULL,
    version character varying(80),
    ambito_dependencia character varying(64) DEFAULT 'COMPILACION'::character varying NOT NULL,
    es_obligatorio boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_dependencia_backend_01 CHECK (((ambito_dependencia)::text = ANY (ARRAY[('COMPILACION'::character varying)::text, ('EJECUCION'::character varying)::text, ('PRUEBA'::character varying)::text, ('PROVISTO'::character varying)::text])))
);


--
-- Name: dependencia_operacion_propuesta; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.dependencia_operacion_propuesta (
    id_operacion uuid NOT NULL,
    id_operacion_requerida uuid NOT NULL,
    CONSTRAINT ck_dependencia_operacion_propuesta_01 CHECK ((id_operacion <> id_operacion_requerida))
);


--
-- Name: descarga_artefacto; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.descarga_artefacto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_generacion uuid NOT NULL,
    id_archivo uuid NOT NULL,
    descargado_por uuid NOT NULL,
    descargado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    direccion_ip inet,
    agente_cliente text
);


--
-- Name: generacion_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.generacion_backend (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_configuracion uuid NOT NULL,
    solicitado_por uuid NOT NULL,
    numero_compilacion integer NOT NULL,
    estado character varying(64) DEFAULT 'EN_COLA'::character varying NOT NULL,
    id_archivo_salida uuid,
    id_archivo_registro uuid,
    informe_transformacion jsonb DEFAULT '{}'::jsonb NOT NULL,
    detalle_error text,
    solicitado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    iniciado_en timestamp with time zone,
    completado_en timestamp with time zone,
    CONSTRAINT ck_generacion_backend_01 CHECK ((numero_compilacion > 0)),
    CONSTRAINT ck_generacion_backend_02 CHECK (((estado)::text = ANY (ARRAY[('EN_COLA'::character varying)::text, ('EN_EJECUCION'::character varying)::text, ('GENERADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text]))),
    CONSTRAINT ck_generacion_backend_03 CHECK ((jsonb_typeof(informe_transformacion) = 'object'::text)),
    CONSTRAINT ck_generacion_backend_04 CHECK ((((estado)::text <> 'GENERADO'::text) OR ((id_archivo_salida IS NOT NULL) AND (completado_en IS NOT NULL))))
);


--
-- Name: hallazgo_validacion_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.hallazgo_validacion_backend (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_validacion uuid NOT NULL,
    severidad character varying(64) NOT NULL,
    tipo_comprobacion character varying(64) NOT NULL,
    componente character varying(255),
    mensaje text NOT NULL,
    detalle jsonb DEFAULT '{}'::jsonb NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_hallazgo_validacion_backend_01 CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text]))),
    CONSTRAINT ck_hallazgo_validacion_backend_02 CHECK (((tipo_comprobacion)::text = ANY (ARRAY[('ESTRUCTURA'::character varying)::text, ('MAPEO_MODELO'::character varying)::text, ('COMPILACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text, ('CONFIGURACION_POSTGRESQL'::character varying)::text, ('SEGURIDAD'::character varying)::text]))),
    CONSTRAINT ck_hallazgo_validacion_backend_03 CHECK ((jsonb_typeof(detalle) = 'object'::text))
);


--
-- Name: hallazgo_validacion_modelo; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.hallazgo_validacion_modelo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_validacion uuid NOT NULL,
    id_regla uuid,
    severidad character varying(64) NOT NULL,
    tipo_entidad character varying(64),
    id_entidad uuid,
    mensaje text NOT NULL,
    recomendacion text,
    ubicacion jsonb DEFAULT '{}'::jsonb NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_hallazgo_validacion_modelo_01 CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text]))),
    CONSTRAINT ck_hallazgo_validacion_modelo_02 CHECK ((jsonb_typeof(ubicacion) = 'object'::text))
);


--
-- Name: intercambio_xmi; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.intercambio_xmi (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    id_version_modelo uuid,
    solicitado_por uuid NOT NULL,
    direccion character varying(64) NOT NULL,
    variante_xmi character varying(60) NOT NULL,
    version_enterprise_architect character varying(60),
    estrategia_fusion character varying(64),
    ambito jsonb DEFAULT '{}'::jsonb NOT NULL,
    id_archivo_entrada uuid,
    id_archivo_salida uuid,
    estado character varying(64) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    id_version_importada uuid,
    informe jsonb DEFAULT '{}'::jsonb NOT NULL,
    detalle_error text,
    iniciado_en timestamp with time zone,
    completado_en timestamp with time zone,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_intercambio_xmi_01 CHECK (((direccion)::text = ANY (ARRAY[('IMPORTAR'::character varying)::text, ('EXPORTAR'::character varying)::text]))),
    CONSTRAINT ck_intercambio_xmi_02 CHECK (((estrategia_fusion IS NULL) OR ((estrategia_fusion)::text = ANY (ARRAY[('FUSIONAR'::character varying)::text, ('REEMPLAZAR'::character varying)::text])))),
    CONSTRAINT ck_intercambio_xmi_03 CHECK ((jsonb_typeof(ambito) = 'object'::text)),
    CONSTRAINT ck_intercambio_xmi_04 CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ANALIZANDO'::character varying)::text, ('LISTO_PARA_REVISION'::character varying)::text, ('COMPLETADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text]))),
    CONSTRAINT ck_intercambio_xmi_05 CHECK ((jsonb_typeof(informe) = 'object'::text)),
    CONSTRAINT ck_intercambio_xmi_06 CHECK (((((direccion)::text = 'IMPORTAR'::text) AND (id_archivo_entrada IS NOT NULL)) OR ((direccion)::text = 'EXPORTAR'::text)))
);


--
-- Name: mapeo_persistencia_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.mapeo_persistencia_backend (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_configuracion uuid NOT NULL,
    id_atributo_uml uuid,
    id_relacion_uml uuid,
    nombre_tabla character varying(63) NOT NULL,
    nombre_columna character varying(63),
    tipo_mapeo character varying(64) NOT NULL,
    opciones_mapeo jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT ck_mapeo_persistencia_backend_01 CHECK (((tipo_mapeo)::text = ANY (ARRAY[('COLUMNA'::character varying)::text, ('UNO_A_UNO'::character varying)::text, ('UNO_A_MUCHOS'::character varying)::text, ('MUCHOS_A_UNO'::character varying)::text, ('MUCHOS_A_MUCHOS'::character varying)::text, ('HERENCIA'::character varying)::text]))),
    CONSTRAINT ck_mapeo_persistencia_backend_02 CHECK ((jsonb_typeof(opciones_mapeo) = 'object'::text)),
    CONSTRAINT ck_mapeo_persistencia_backend_03 CHECK (((((id_atributo_uml IS NOT NULL))::integer + ((id_relacion_uml IS NOT NULL))::integer) = 1))
);


--
-- Name: mapeo_tipo_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.mapeo_tipo_backend (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_configuracion uuid NOT NULL,
    tipo_uml character varying(180) NOT NULL,
    tipo_java character varying(255) NOT NULL,
    tipo_postgresql character varying(120) NOT NULL,
    clase_conversor character varying(255),
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL
);


--
-- Name: miembro_proyecto; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.miembro_proyecto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_proyecto uuid NOT NULL,
    id_usuario uuid NOT NULL,
    estado character varying(64) DEFAULT 'ACTIVO'::character varying NOT NULL,
    incorporado_en timestamp with time zone,
    suspendido_en timestamp with time zone,
    retirado_en timestamp with time zone,
    motivo_estado text,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_miembro_proyecto_01 CHECK (((estado)::text = ANY (ARRAY[('INVITADO'::character varying)::text, ('ACTIVO'::character varying)::text, ('SUSPENDIDO'::character varying)::text, ('RETIRADO'::character varying)::text])))
);


--
-- Name: modelo_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.modelo_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_proyecto uuid NOT NULL,
    nombre character varying(180) NOT NULL,
    descripcion text,
    estado character varying(64) DEFAULT 'BORRADOR'::character varying NOT NULL,
    revision_actual bigint DEFAULT 0 NOT NULL,
    id_version_actual uuid,
    creado_por uuid,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    archivado_en timestamp with time zone,
    CONSTRAINT ck_modelo_uml_01 CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('ARCHIVADO'::character varying)::text]))),
    CONSTRAINT ck_modelo_uml_02 CHECK ((revision_actual >= 0))
);


--
-- Name: operacion_propuesta; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.operacion_propuesta (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_propuesta uuid NOT NULL,
    numero_secuencia integer NOT NULL,
    tipo_operacion character varying(64) NOT NULL,
    tipo_entidad character varying(64) NOT NULL,
    id_entidad_destino uuid,
    valor_actual jsonb,
    valor_propuesto jsonb,
    confianza numeric(5,4),
    esta_seleccionado boolean DEFAULT true NOT NULL,
    estado character varying(64) DEFAULT 'PROPUESTO'::character varying NOT NULL,
    mensajes_validacion jsonb DEFAULT '[]'::jsonb NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_operacion_propuesta_01 CHECK ((numero_secuencia > 0)),
    CONSTRAINT ck_operacion_propuesta_02 CHECK (((tipo_operacion)::text = ANY (ARRAY[('CREAR'::character varying)::text, ('ACTUALIZAR'::character varying)::text, ('ELIMINAR'::character varying)::text]))),
    CONSTRAINT ck_operacion_propuesta_03 CHECK (((confianza IS NULL) OR ((confianza >= (0)::numeric) AND (confianza <= (1)::numeric)))),
    CONSTRAINT ck_operacion_propuesta_04 CHECK (((estado)::text = ANY (ARRAY[('PROPUESTO'::character varying)::text, ('VALIDO'::character varying)::text, ('INVALIDO'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text]))),
    CONSTRAINT ck_operacion_propuesta_05 CHECK ((jsonb_typeof(mensajes_validacion) = 'array'::text))
);


--
-- Name: operacion_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.operacion_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_clase uuid NOT NULL,
    nombre character varying(180) NOT NULL,
    tipo_retorno character varying(180),
    visibilidad character varying(64) DEFAULT 'PUBLICO'::character varying NOT NULL,
    es_estatico boolean DEFAULT false NOT NULL,
    es_abstracto boolean DEFAULT false NOT NULL,
    orden integer DEFAULT 0 NOT NULL,
    id_externo_xmi character varying(255),
    version_fila bigint DEFAULT 1 NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    eliminado_en timestamp with time zone,
    CONSTRAINT ck_operacion_uml_01 CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text]))),
    CONSTRAINT ck_operacion_uml_02 CHECK ((orden >= 0)),
    CONSTRAINT ck_operacion_uml_03 CHECK (((nombre)::text <> ''::text))
);


--
-- Name: paquete_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.paquete_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    id_paquete_padre uuid,
    nombre character varying(180) NOT NULL,
    descripcion text,
    id_externo_xmi character varying(255),
    version_fila bigint DEFAULT 1 NOT NULL,
    creado_por uuid,
    actualizado_por uuid,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    eliminado_en timestamp with time zone,
    CONSTRAINT ck_paquete_uml_01 CHECK (((nombre)::text <> ''::text)),
    CONSTRAINT ck_paquete_uml_02 CHECK ((id_paquete_padre IS DISTINCT FROM id))
);


--
-- Name: parametro_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.parametro_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_operacion uuid NOT NULL,
    nombre character varying(180) NOT NULL,
    tipo_uml character varying(180) NOT NULL,
    direccion character varying(64) DEFAULT 'ENTRADA'::character varying NOT NULL,
    es_obligatorio boolean DEFAULT true NOT NULL,
    valor_predeterminado text,
    orden integer NOT NULL,
    id_externo_xmi character varying(255),
    version_fila bigint DEFAULT 1 NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    eliminado_en timestamp with time zone,
    CONSTRAINT ck_parametro_uml_01 CHECK (((direccion)::text = ANY (ARRAY[('ENTRADA'::character varying)::text, ('SALIDA'::character varying)::text, ('ENTRADA_SALIDA'::character varying)::text, ('RETORNO'::character varying)::text]))),
    CONSTRAINT ck_parametro_uml_02 CHECK ((orden >= 0)),
    CONSTRAINT ck_parametro_uml_03 CHECK (((nombre)::text <> ''::text)),
    CONSTRAINT ck_parametro_uml_04 CHECK (((tipo_uml)::text <> ''::text))
);


--
-- Name: participante_sesion_modelo; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.participante_sesion_modelo (
    id_sesion_edicion uuid NOT NULL,
    id_usuario uuid NOT NULL,
    modo_participante character varying(64) NOT NULL,
    estado_presencia character varying(64) DEFAULT 'EN_LINEA'::character varying NOT NULL,
    tipo_entidad_enfocada character varying(40),
    id_entidad_enfocada uuid,
    incorporado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    ultima_presencia_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    salida_en timestamp with time zone,
    CONSTRAINT ck_participante_sesion_modelo_01 CHECK (((modo_participante)::text = ANY (ARRAY[('EDITOR'::character varying)::text, ('LECTOR'::character varying)::text]))),
    CONSTRAINT ck_participante_sesion_modelo_02 CHECK (((estado_presencia)::text = ANY (ARRAY[('EN_LINEA'::character varying)::text, ('AUSENTE'::character varying)::text, ('DESCONECTADO'::character varying)::text])))
);


--
-- Name: permiso; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.permiso (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    codigo public.citext NOT NULL,
    modulo character varying(64) NOT NULL,
    accion character varying(64) NOT NULL,
    descripcion text,
    ambito character varying(64) NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_permiso_01 CHECK (((ambito)::text = ANY (ARRAY[('GLOBAL'::character varying)::text, ('PROYECTO'::character varying)::text, ('AMBOS'::character varying)::text])))
);


--
-- Name: permiso_miembro_proyecto; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.permiso_miembro_proyecto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_miembro_proyecto uuid NOT NULL,
    id_permiso uuid NOT NULL,
    decision character varying(64) NOT NULL,
    asignado_por uuid,
    asignado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    revocado_en timestamp with time zone,
    CONSTRAINT ck_permiso_miembro_proyecto_01 CHECK (((decision)::text = ANY (ARRAY[('PERMITIR'::character varying)::text, ('DENEGAR'::character varying)::text]))),
    CONSTRAINT ck_permiso_miembro_proyecto_02 CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))
);


--
-- Name: posicion_clase_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.posicion_clase_uml (
    id_clase uuid NOT NULL,
    x numeric(12,3) NOT NULL,
    y numeric(12,3) NOT NULL,
    ancho numeric(12,3) NOT NULL,
    alto numeric(12,3) NOT NULL,
    orden_capa integer DEFAULT 0 NOT NULL,
    estilo jsonb DEFAULT '{}'::jsonb NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_posicion_clase_uml_01 CHECK ((ancho > (0)::numeric)),
    CONSTRAINT ck_posicion_clase_uml_02 CHECK ((alto > (0)::numeric)),
    CONSTRAINT ck_posicion_clase_uml_03 CHECK ((jsonb_typeof(estilo) = 'object'::text))
);


--
-- Name: posicion_relacion_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.posicion_relacion_uml (
    id_relacion uuid NOT NULL,
    puntos_intermedios jsonb DEFAULT '[]'::jsonb NOT NULL,
    etiqueta_x numeric(12,3),
    etiqueta_y numeric(12,3),
    estilo jsonb DEFAULT '{}'::jsonb NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_posicion_relacion_uml_01 CHECK ((jsonb_typeof(puntos_intermedios) = 'array'::text)),
    CONSTRAINT ck_posicion_relacion_uml_02 CHECK ((jsonb_typeof(estilo) = 'object'::text))
);


--
-- Name: preferencia_diagrama; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.preferencia_diagrama (
    id_modelo uuid NOT NULL,
    id_usuario uuid NOT NULL,
    escala numeric(8,4) DEFAULT 1 NOT NULL,
    desplazamiento_x numeric(12,3) DEFAULT 0 NOT NULL,
    desplazamiento_y numeric(12,3) DEFAULT 0 NOT NULL,
    mostrar_cuadricula boolean DEFAULT true NOT NULL,
    ajustar_cuadricula boolean DEFAULT true NOT NULL,
    preferencias jsonb DEFAULT '{}'::jsonb NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_preferencia_diagrama_01 CHECK ((escala > (0)::numeric)),
    CONSTRAINT ck_preferencia_diagrama_02 CHECK ((jsonb_typeof(preferencias) = 'object'::text))
);


--
-- Name: propuesta_inteligente; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.propuesta_inteligente (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_solicitud uuid NOT NULL,
    estado character varying(64) DEFAULT 'BORRADOR'::character varying NOT NULL,
    resumen text,
    confianza_general numeric(5,4),
    revisado_por uuid,
    revisado_en timestamp with time zone,
    aplicado_por uuid,
    id_version_aplicada uuid,
    aplicado_en timestamp with time zone,
    rechazado_por uuid,
    rechazado_en timestamp with time zone,
    motivo_rechazo text,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_propuesta_inteligente_01 CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('LISTO'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text, ('FALLIDO'::character varying)::text]))),
    CONSTRAINT ck_propuesta_inteligente_02 CHECK (((confianza_general IS NULL) OR ((confianza_general >= (0)::numeric) AND (confianza_general <= (1)::numeric)))),
    CONSTRAINT ck_propuesta_inteligente_03 CHECK (((((estado)::text <> 'APLICADO'::text) OR ((aplicado_en IS NOT NULL) AND (aplicado_por IS NOT NULL) AND (id_version_aplicada IS NOT NULL))) AND (((estado)::text <> 'RECHAZADO'::text) OR ((rechazado_en IS NOT NULL) AND (rechazado_por IS NOT NULL))) AND (NOT ((aplicado_en IS NOT NULL) AND (rechazado_en IS NOT NULL)))))
);


--
-- Name: proyecto; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.proyecto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    codigo public.citext NOT NULL,
    nombre character varying(180) NOT NULL,
    descripcion text,
    id_usuario_propietario uuid NOT NULL,
    estado character varying(64) DEFAULT 'ACTIVO'::character varying NOT NULL,
    visibilidad character varying(64) DEFAULT 'PRIVADO'::character varying NOT NULL,
    opciones jsonb DEFAULT '{}'::jsonb NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    archivado_en timestamp with time zone,
    CONSTRAINT ck_proyecto_01 CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('ARCHIVADO'::character varying)::text, ('SUSPENDIDO'::character varying)::text]))),
    CONSTRAINT ck_proyecto_02 CHECK (((visibilidad)::text = ANY (ARRAY[('PRIVADO'::character varying)::text, ('INTERNO'::character varying)::text]))),
    CONSTRAINT ck_proyecto_03 CHECK ((jsonb_typeof(opciones) = 'object'::text)),
    CONSTRAINT ck_proyecto_04 CHECK (((nombre)::text <> ''::text)),
    CONSTRAINT ck_proyecto_05 CHECK (((btrim((codigo)::text) <> ''::text) AND (btrim((nombre)::text) <> ''::text)))
);


--
-- Name: regla_validacion; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.regla_validacion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    codigo public.citext NOT NULL,
    nombre character varying(180) NOT NULL,
    descripcion text NOT NULL,
    severidad character varying(64) NOT NULL,
    aplica_a character varying(64) NOT NULL,
    esta_activo boolean DEFAULT true NOT NULL,
    clave_implementacion character varying(200) NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_regla_validacion_01 CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text])))
);


--
-- Name: relacion_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.relacion_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    id_clase_origen uuid NOT NULL,
    id_clase_destino uuid NOT NULL,
    nombre character varying(180),
    tipo_relacion character varying(64) NOT NULL,
    direccion character varying(64) DEFAULT 'SIN_DIRECCION'::character varying NOT NULL,
    rol_origen character varying(180),
    rol_destino character varying(180),
    minimo_origen integer,
    maximo_origen integer,
    minimo_destino integer,
    maximo_destino integer,
    origen_navegable boolean,
    destino_navegable boolean,
    id_externo_xmi character varying(255),
    version_fila bigint DEFAULT 1 NOT NULL,
    creado_por uuid,
    actualizado_por uuid,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    eliminado_en timestamp with time zone,
    CONSTRAINT ck_relacion_uml_01 CHECK (((tipo_relacion)::text = ANY (ARRAY[('ASOCIACION'::character varying)::text, ('AGREGACION'::character varying)::text, ('COMPOSICION'::character varying)::text, ('GENERALIZACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text]))),
    CONSTRAINT ck_relacion_uml_02 CHECK (((direccion)::text = ANY (ARRAY[('SIN_DIRECCION'::character varying)::text, ('ORIGEN_A_DESTINO'::character varying)::text, ('DESTINO_A_ORIGEN'::character varying)::text, ('BIDIRECCIONAL'::character varying)::text]))),
    CONSTRAINT ck_relacion_uml_03 CHECK (((minimo_origen IS NULL) OR (minimo_origen >= 0))),
    CONSTRAINT ck_relacion_uml_04 CHECK (((maximo_origen IS NULL) OR (maximo_origen >= 0))),
    CONSTRAINT ck_relacion_uml_05 CHECK (((minimo_destino IS NULL) OR (minimo_destino >= 0))),
    CONSTRAINT ck_relacion_uml_06 CHECK (((maximo_destino IS NULL) OR (maximo_destino >= 0))),
    CONSTRAINT ck_relacion_uml_07 CHECK (((maximo_origen IS NULL) OR (minimo_origen IS NULL) OR (maximo_origen >= minimo_origen))),
    CONSTRAINT ck_relacion_uml_08 CHECK (((maximo_destino IS NULL) OR (minimo_destino IS NULL) OR (maximo_destino >= minimo_destino))),
    CONSTRAINT ck_relacion_uml_09 CHECK ((((tipo_relacion)::text <> 'GENERALIZACION'::text) OR (id_clase_origen <> id_clase_destino))),
    CONSTRAINT ck_relacion_uml_10 CHECK (((((tipo_relacion)::text = ANY (ARRAY[('ASOCIACION'::character varying)::text, ('AGREGACION'::character varying)::text, ('COMPOSICION'::character varying)::text])) AND (minimo_origen IS NOT NULL) AND (minimo_destino IS NOT NULL)) OR (((tipo_relacion)::text = ANY (ARRAY[('GENERALIZACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text])) AND (minimo_origen IS NULL) AND (maximo_origen IS NULL) AND (minimo_destino IS NULL) AND (maximo_destino IS NULL))))
);


--
-- Name: rol; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.rol (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    codigo public.citext NOT NULL,
    nombre character varying(120) NOT NULL,
    descripcion text,
    ambito character varying(64) NOT NULL,
    es_del_sistema boolean DEFAULT false NOT NULL,
    estado character varying(64) DEFAULT 'ACTIVO'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_rol_01 CHECK (((ambito)::text = ANY (ARRAY[('GLOBAL'::character varying)::text, ('PROYECTO'::character varying)::text, ('AMBOS'::character varying)::text]))),
    CONSTRAINT ck_rol_02 CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('INACTIVO'::character varying)::text])))
);


--
-- Name: rol_miembro_proyecto; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.rol_miembro_proyecto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_miembro_proyecto uuid NOT NULL,
    id_rol uuid NOT NULL,
    asignado_por uuid,
    asignado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    revocado_en timestamp with time zone,
    CONSTRAINT ck_rol_miembro_proyecto_01 CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))
);


--
-- Name: rol_permiso; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.rol_permiso (
    id_rol uuid NOT NULL,
    id_permiso uuid NOT NULL,
    concedido_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL
);


--
-- Name: secreto_servicio; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.secreto_servicio (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_servicio uuid NOT NULL,
    contenido_cifrado bytea NOT NULL,
    algoritmo_cifrado character varying(40) NOT NULL,
    version_clave character varying(40) NOT NULL,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    rotado_en timestamp with time zone,
    retirado_en timestamp with time zone
);


--
-- Name: servicio_plataforma; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.servicio_plataforma (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(140) NOT NULL,
    proveedor character varying(100) NOT NULL,
    tipo_servicio character varying(64) NOT NULL,
    url_servicio text,
    configuracion jsonb DEFAULT '{}'::jsonb NOT NULL,
    estado character varying(64) DEFAULT 'SIN_VERIFICAR'::character varying NOT NULL,
    creado_por uuid,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_servicio_plataforma_01 CHECK (((tipo_servicio)::text = ANY (ARRAY[('IA_TEXTO'::character varying)::text, ('VOZ_A_TEXTO'::character varying)::text, ('IA_IMAGEN'::character varying)::text]))),
    CONSTRAINT ck_servicio_plataforma_02 CHECK ((jsonb_typeof(configuracion) = 'object'::text)),
    CONSTRAINT ck_servicio_plataforma_03 CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('INACTIVO'::character varying)::text, ('SIN_VERIFICAR'::character varying)::text, ('NO_DISPONIBLE'::character varying)::text])))
);


--
-- Name: sesion_autenticacion; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.sesion_autenticacion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_usuario uuid NOT NULL,
    hash_token bytea NOT NULL,
    hash_token_renovacion bytea,
    direccion_ip inet,
    agente_cliente text,
    emitido_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    revocado_en timestamp with time zone,
    motivo_revocacion character varying(200),
    CONSTRAINT ck_sesion_autenticacion_01 CHECK ((expira_en > emitido_en)),
    CONSTRAINT ck_sesion_autenticacion_02 CHECK (((octet_length(hash_token) = 32) AND ((hash_token_renovacion IS NULL) OR (octet_length(hash_token_renovacion) = 32)))),
    CONSTRAINT ck_sesion_autenticacion_03 CHECK (((revocado_en IS NULL) OR (revocado_en >= emitido_en)))
);


--
-- Name: sesion_edicion_modelo; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.sesion_edicion_modelo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    abierto_por uuid NOT NULL,
    modo character varying(64) NOT NULL,
    estado character varying(64) DEFAULT 'ACTIVO'::character varying NOT NULL,
    revision_base bigint NOT NULL,
    abierto_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    ultima_actividad_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    cerrado_en timestamp with time zone,
    CONSTRAINT ck_sesion_edicion_modelo_01 CHECK ((revision_base >= 0)),
    CONSTRAINT ck_sesion_edicion_modelo_02 CHECK (((modo)::text = ANY (ARRAY[('EDICION'::character varying)::text, ('SOLO_LECTURA'::character varying)::text]))),
    CONSTRAINT ck_sesion_edicion_modelo_03 CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('CERRADO'::character varying)::text, ('INTERRUMPIDO'::character varying)::text])))
);


--
-- Name: solicitud_inteligente; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.solicitud_inteligente (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    solicitado_por uuid NOT NULL,
    id_servicio uuid,
    tipo_entrada character varying(64) NOT NULL,
    id_archivo_origen uuid,
    texto_original text,
    texto_transcrito text,
    texto_corregido text,
    codigo_idioma character varying(16),
    revision_base bigint NOT NULL,
    estado character varying(64) DEFAULT 'RECIBIDO'::character varying NOT NULL,
    respuesta_servicio jsonb,
    detalle_error text,
    recibido_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    completado_en timestamp with time zone,
    CONSTRAINT ck_solicitud_inteligente_01 CHECK (((tipo_entrada)::text = ANY (ARRAY[('TEXTO'::character varying)::text, ('VOZ'::character varying)::text, ('IMAGEN'::character varying)::text]))),
    CONSTRAINT ck_solicitud_inteligente_02 CHECK ((revision_base >= 0)),
    CONSTRAINT ck_solicitud_inteligente_03 CHECK (((estado)::text = ANY (ARRAY[('RECIBIDO'::character varying)::text, ('PROCESANDO'::character varying)::text, ('COMPLETADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text]))),
    CONSTRAINT ck_solicitud_inteligente_04 CHECK (((((tipo_entrada)::text = 'TEXTO'::text) AND (texto_original IS NOT NULL)) OR (((tipo_entrada)::text = ANY (ARRAY[('VOZ'::character varying)::text, ('IMAGEN'::character varying)::text])) AND (id_archivo_origen IS NOT NULL)))),
    CONSTRAINT ck_solicitud_inteligente_05 CHECK ((((tipo_entrada)::text <> 'TEXTO'::text) OR (btrim(texto_original) <> ''::text)))
);


--
-- Name: usuario; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.usuario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre_usuario public.citext NOT NULL,
    correo_electronico public.citext NOT NULL,
    nombre_completo character varying(160) NOT NULL,
    hash_contrasena text NOT NULL,
    estado character varying(64) DEFAULT 'ACTIVO'::character varying NOT NULL,
    intentos_fallidos_acceso smallint DEFAULT 0 NOT NULL,
    bloqueado_hasta timestamp with time zone,
    ultimo_acceso_en timestamp with time zone,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    desactivado_en timestamp with time zone,
    CONSTRAINT ck_usuario_01 CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ACTIVO'::character varying)::text, ('BLOQUEADO'::character varying)::text, ('INACTIVO'::character varying)::text]))),
    CONSTRAINT ck_usuario_02 CHECK ((intentos_fallidos_acceso >= 0)),
    CONSTRAINT ck_usuario_03 CHECK ((nombre_usuario OPERATOR(public.<>) ''::public.citext)),
    CONSTRAINT ck_usuario_04 CHECK ((correo_electronico OPERATOR(public.<>) ''::public.citext)),
    CONSTRAINT ck_usuario_05 CHECK (((btrim((nombre_completo)::text) <> ''::text) AND (btrim((nombre_usuario)::text) <> ''::text) AND (btrim((correo_electronico)::text) <> ''::text)))
);


--
-- Name: usuario_rol; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.usuario_rol (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_usuario uuid NOT NULL,
    id_rol uuid NOT NULL,
    asignado_por uuid,
    asignado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    revocado_en timestamp with time zone,
    CONSTRAINT ck_usuario_rol_01 CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))
);


--
-- Name: v_contexto_propuesta_inteligente; Type: VIEW; Schema: uml_colaborativo; Owner: -
--

CREATE VIEW uml_colaborativo.v_contexto_propuesta_inteligente AS
 SELECT p.id,
    p.id_solicitud,
    p.estado,
    p.resumen,
    p.confianza_general,
    p.revisado_por,
    p.revisado_en,
    p.aplicado_por,
    p.id_version_aplicada,
    p.aplicado_en,
    p.rechazado_por,
    p.rechazado_en,
    p.motivo_rechazo,
    p.creado_en,
    p.actualizado_en,
    r.id_modelo,
    m.id_proyecto,
    r.revision_base,
    r.tipo_entrada
   FROM ((uml_colaborativo.propuesta_inteligente p
     JOIN uml_colaborativo.solicitud_inteligente r ON ((r.id = p.id_solicitud)))
     JOIN uml_colaborativo.modelo_uml m ON ((m.id = r.id_modelo)));


--
-- Name: v_firma_operacion; Type: VIEW; Schema: uml_colaborativo; Owner: -
--

CREATE VIEW uml_colaborativo.v_firma_operacion AS
 SELECT id AS id_operacion,
    id_clase,
    nombre,
    COALESCE(( SELECT jsonb_agg(jsonb_build_array(p.direccion, p.tipo_uml) ORDER BY p.orden) AS jsonb_agg
           FROM uml_colaborativo.parametro_uml p
          WHERE ((p.id_operacion = o.id) AND (p.eliminado_en IS NULL))), '[]'::jsonb) AS firma_parametros
   FROM uml_colaborativo.operacion_uml o
  WHERE (eliminado_en IS NULL);


--
-- Name: v_miembro_proyecto_activo; Type: VIEW; Schema: uml_colaborativo; Owner: -
--

CREATE VIEW uml_colaborativo.v_miembro_proyecto_activo AS
 SELECT pm.id AS id_miembro_proyecto,
    pm.id_proyecto,
    pm.id_usuario,
    u.nombre_usuario,
    u.correo_electronico,
    u.nombre_completo,
    pm.estado,
    pm.incorporado_en
   FROM (uml_colaborativo.miembro_proyecto pm
     JOIN uml_colaborativo.usuario u ON ((u.id = pm.id_usuario)))
  WHERE (((pm.estado)::text = 'ACTIVO'::text) AND ((u.estado)::text = 'ACTIVO'::text));


--
-- Name: v_permiso_efectivo_miembro; Type: VIEW; Schema: uml_colaborativo; Owner: -
--

CREATE VIEW uml_colaborativo.v_permiso_efectivo_miembro AS
 WITH decisions AS (
         SELECT pmr.id_miembro_proyecto,
            rp.id_permiso,
            true AS allowed,
            1 AS precedence
           FROM ((uml_colaborativo.rol_miembro_proyecto pmr
             JOIN uml_colaborativo.rol r ON (((r.id = pmr.id_rol) AND ((r.estado)::text = 'ACTIVO'::text))))
             JOIN uml_colaborativo.rol_permiso rp ON ((rp.id_rol = r.id)))
          WHERE (pmr.revocado_en IS NULL)
        UNION ALL
         SELECT permiso_miembro_proyecto.id_miembro_proyecto,
            permiso_miembro_proyecto.id_permiso,
            ((permiso_miembro_proyecto.decision)::text = 'PERMITIR'::text),
            2
           FROM uml_colaborativo.permiso_miembro_proyecto
          WHERE (permiso_miembro_proyecto.revocado_en IS NULL)
        )
 SELECT d.id_miembro_proyecto,
    pm.id_proyecto,
    pm.id_usuario,
    d.id_permiso,
    p.codigo AS codigo_permiso,
        CASE
            WHEN bool_or((NOT d.allowed)) FILTER (WHERE (d.precedence = 2)) THEN false
            WHEN bool_or(d.allowed) FILTER (WHERE (d.precedence = 2)) THEN true
            ELSE bool_or(d.allowed)
        END AS esta_permitido
   FROM (((decisions d
     JOIN uml_colaborativo.miembro_proyecto pm ON (((pm.id = d.id_miembro_proyecto) AND ((pm.estado)::text = 'ACTIVO'::text))))
     JOIN uml_colaborativo.usuario u ON (((u.id = pm.id_usuario) AND ((u.estado)::text = 'ACTIVO'::text))))
     JOIN uml_colaborativo.permiso p ON (((p.id = d.id_permiso) AND ((p.ambito)::text = ANY (ARRAY[('PROYECTO'::character varying)::text, ('AMBOS'::character varying)::text])))))
  GROUP BY d.id_miembro_proyecto, pm.id_proyecto, pm.id_usuario, d.id_permiso, p.codigo;


--
-- Name: validacion_backend; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.validacion_backend (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_generacion uuid NOT NULL,
    solicitado_por uuid,
    estado character varying(64) DEFAULT 'EN_EJECUCION'::character varying NOT NULL,
    comprobaciones_aprobadas integer DEFAULT 0 NOT NULL,
    cantidad_advertencias integer DEFAULT 0 NOT NULL,
    cantidad_errores integer DEFAULT 0 NOT NULL,
    id_archivo_informe uuid,
    iniciado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    completado_en timestamp with time zone,
    CONSTRAINT ck_validacion_backend_01 CHECK (((estado)::text = ANY (ARRAY[('EN_EJECUCION'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('FALLIDO'::character varying)::text, ('INCOMPLETO'::character varying)::text]))),
    CONSTRAINT ck_validacion_backend_02 CHECK ((comprobaciones_aprobadas >= 0)),
    CONSTRAINT ck_validacion_backend_03 CHECK ((cantidad_advertencias >= 0)),
    CONSTRAINT ck_validacion_backend_04 CHECK ((cantidad_errores >= 0))
);


--
-- Name: version_modelo_uml; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.version_modelo_uml (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    numero_version integer NOT NULL,
    revision bigint NOT NULL,
    id_version_padre uuid,
    id_version_restaurada uuid,
    instantanea jsonb NOT NULL,
    hash_instantanea_sha256 character(64) NOT NULL,
    estado_validacion character varying(64) DEFAULT 'SIN_VALIDAR'::character varying NOT NULL,
    resumen_cambio text,
    creado_por uuid,
    creado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ck_version_modelo_uml_01 CHECK ((numero_version > 0)),
    CONSTRAINT ck_version_modelo_uml_02 CHECK ((revision >= 0)),
    CONSTRAINT ck_version_modelo_uml_03 CHECK ((jsonb_typeof(instantanea) = 'object'::text)),
    CONSTRAINT ck_version_modelo_uml_04 CHECK ((hash_instantanea_sha256 ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_version_modelo_uml_05 CHECK (((estado_validacion)::text = ANY (ARRAY[('SIN_VALIDAR'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text])))
);


--
-- Name: v_resumen_generacion_backend; Type: VIEW; Schema: uml_colaborativo; Owner: -
--

CREATE VIEW uml_colaborativo.v_resumen_generacion_backend AS
 SELECT job.id AS id_generacion,
    m.id_proyecto,
    v.id_modelo,
    cfg.nombre AS nombre_configuracion,
    job.numero_compilacion,
    job.estado AS estado_generacion,
    job.solicitado_en,
    job.completado_en,
    validation.estado AS estado_validacion,
    validation.cantidad_errores,
    validation.cantidad_advertencias,
    job.id_archivo_salida
   FROM ((((uml_colaborativo.generacion_backend job
     JOIN uml_colaborativo.configuracion_generacion_backend cfg ON ((cfg.id = job.id_configuracion)))
     JOIN uml_colaborativo.version_modelo_uml v ON ((v.id = cfg.id_version_modelo)))
     JOIN uml_colaborativo.modelo_uml m ON ((m.id = v.id_modelo)))
     LEFT JOIN LATERAL ( SELECT validacion_backend.estado,
            validacion_backend.cantidad_errores,
            validacion_backend.cantidad_advertencias
           FROM uml_colaborativo.validacion_backend
          WHERE (validacion_backend.id_generacion = job.id)
          ORDER BY validacion_backend.iniciado_en DESC, validacion_backend.id DESC
         LIMIT 1) validation ON (true));


--
-- Name: v_resumen_modelo; Type: VIEW; Schema: uml_colaborativo; Owner: -
--

CREATE VIEW uml_colaborativo.v_resumen_modelo AS
 SELECT m.id AS id_modelo,
    m.id_proyecto,
    m.nombre,
    m.estado,
    m.revision_actual,
    m.id_version_actual,
    c.total AS cantidad_clases_activas,
    r.total AS cantidad_relaciones_activas,
    v.numero_version AS numero_ultima_version,
    v.creado_en AS ultima_version_en,
    m.actualizado_en
   FROM (((uml_colaborativo.modelo_uml m
     LEFT JOIN LATERAL ( SELECT count(*) AS total
           FROM uml_colaborativo.clase_uml
          WHERE ((clase_uml.id_modelo = m.id) AND (clase_uml.eliminado_en IS NULL))) c ON (true))
     LEFT JOIN LATERAL ( SELECT count(*) AS total
           FROM uml_colaborativo.relacion_uml
          WHERE ((relacion_uml.id_modelo = m.id) AND (relacion_uml.eliminado_en IS NULL))) r ON (true))
     LEFT JOIN LATERAL ( SELECT version_modelo_uml.numero_version,
            version_modelo_uml.creado_en
           FROM uml_colaborativo.version_modelo_uml
          WHERE (version_modelo_uml.id_modelo = m.id)
          ORDER BY version_modelo_uml.numero_version DESC
         LIMIT 1) v ON (true));


--
-- Name: v_semantica_atributo; Type: VIEW; Schema: uml_colaborativo; Owner: -
--

CREATE VIEW uml_colaborativo.v_semantica_atributo AS
 SELECT id,
    id_clase,
    nombre,
    tipo_uml,
    visibilidad,
    valor_predeterminado,
    multiplicidad_minima,
    multiplicidad_maxima,
    es_identificador,
    es_unico,
    es_estatico,
    restricciones_json,
    orden,
    id_externo_xmi,
    version_fila,
    creado_en,
    actualizado_en,
    eliminado_en,
    (multiplicidad_minima = 0) AS admite_nulo
   FROM uml_colaborativo.atributo_uml a;


--
-- Name: validacion_modelo; Type: TABLE; Schema: uml_colaborativo; Owner: -
--

CREATE TABLE uml_colaborativo.validacion_modelo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    id_modelo uuid NOT NULL,
    id_version_modelo uuid,
    revision_validada bigint NOT NULL,
    solicitado_por uuid,
    estado character varying(64) NOT NULL,
    cantidad_errores integer DEFAULT 0 NOT NULL,
    cantidad_advertencias integer DEFAULT 0 NOT NULL,
    iniciado_en timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    completado_en timestamp with time zone,
    resumen jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT ck_validacion_modelo_01 CHECK ((revision_validada >= 0)),
    CONSTRAINT ck_validacion_modelo_02 CHECK (((estado)::text = ANY (ARRAY[('EN_EJECUCION'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('FALLIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text]))),
    CONSTRAINT ck_validacion_modelo_03 CHECK ((cantidad_errores >= 0)),
    CONSTRAINT ck_validacion_modelo_04 CHECK ((cantidad_advertencias >= 0)),
    CONSTRAINT ck_validacion_modelo_05 CHECK ((jsonb_typeof(resumen) = 'object'::text))
);


--
-- Name: actividad_proyecto pk_actividad_proyecto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.actividad_proyecto
    ADD CONSTRAINT pk_actividad_proyecto_01 PRIMARY KEY (id);


--
-- Name: ambiguedad_propuesta pk_ambiguedad_propuesta_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.ambiguedad_propuesta
    ADD CONSTRAINT pk_ambiguedad_propuesta_01 PRIMARY KEY (id);


--
-- Name: archivo_almacenado pk_archivo_almacenado_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.archivo_almacenado
    ADD CONSTRAINT pk_archivo_almacenado_01 PRIMARY KEY (id);


--
-- Name: atributo_uml pk_atributo_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.atributo_uml
    ADD CONSTRAINT pk_atributo_uml_01 PRIMARY KEY (id);


--
-- Name: cambio_modelo pk_cambio_modelo_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT pk_cambio_modelo_01 PRIMARY KEY (id);


--
-- Name: clase_configuracion_backend pk_clase_configuracion_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_configuracion_backend
    ADD CONSTRAINT pk_clase_configuracion_backend_01 PRIMARY KEY (id_configuracion, id_clase);


--
-- Name: clase_uml pk_clase_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_uml
    ADD CONSTRAINT pk_clase_uml_01 PRIMARY KEY (id);


--
-- Name: comprobacion_servicio pk_comprobacion_servicio_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.comprobacion_servicio
    ADD CONSTRAINT pk_comprobacion_servicio_01 PRIMARY KEY (id);


--
-- Name: configuracion_generacion_backend pk_configuracion_generacion_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.configuracion_generacion_backend
    ADD CONSTRAINT pk_configuracion_generacion_backend_01 PRIMARY KEY (id);


--
-- Name: conflicto_edicion pk_conflicto_edicion_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT pk_conflicto_edicion_01 PRIMARY KEY (id);


--
-- Name: dependencia_backend pk_dependencia_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.dependencia_backend
    ADD CONSTRAINT pk_dependencia_backend_01 PRIMARY KEY (id);


--
-- Name: dependencia_operacion_propuesta pk_dependencia_operacion_propuesta_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.dependencia_operacion_propuesta
    ADD CONSTRAINT pk_dependencia_operacion_propuesta_01 PRIMARY KEY (id_operacion, id_operacion_requerida);


--
-- Name: descarga_artefacto pk_descarga_artefacto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.descarga_artefacto
    ADD CONSTRAINT pk_descarga_artefacto_01 PRIMARY KEY (id);


--
-- Name: generacion_backend pk_generacion_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.generacion_backend
    ADD CONSTRAINT pk_generacion_backend_01 PRIMARY KEY (id);


--
-- Name: hallazgo_validacion_backend pk_hallazgo_validacion_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.hallazgo_validacion_backend
    ADD CONSTRAINT pk_hallazgo_validacion_backend_01 PRIMARY KEY (id);


--
-- Name: hallazgo_validacion_modelo pk_hallazgo_validacion_modelo_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.hallazgo_validacion_modelo
    ADD CONSTRAINT pk_hallazgo_validacion_modelo_01 PRIMARY KEY (id);


--
-- Name: intercambio_xmi pk_intercambio_xmi_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT pk_intercambio_xmi_01 PRIMARY KEY (id);


--
-- Name: mapeo_persistencia_backend pk_mapeo_persistencia_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.mapeo_persistencia_backend
    ADD CONSTRAINT pk_mapeo_persistencia_backend_01 PRIMARY KEY (id);


--
-- Name: mapeo_tipo_backend pk_mapeo_tipo_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.mapeo_tipo_backend
    ADD CONSTRAINT pk_mapeo_tipo_backend_01 PRIMARY KEY (id);


--
-- Name: miembro_proyecto pk_miembro_proyecto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.miembro_proyecto
    ADD CONSTRAINT pk_miembro_proyecto_01 PRIMARY KEY (id);


--
-- Name: modelo_uml pk_modelo_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.modelo_uml
    ADD CONSTRAINT pk_modelo_uml_01 PRIMARY KEY (id);


--
-- Name: operacion_propuesta pk_operacion_propuesta_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.operacion_propuesta
    ADD CONSTRAINT pk_operacion_propuesta_01 PRIMARY KEY (id);


--
-- Name: operacion_uml pk_operacion_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.operacion_uml
    ADD CONSTRAINT pk_operacion_uml_01 PRIMARY KEY (id);


--
-- Name: paquete_uml pk_paquete_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.paquete_uml
    ADD CONSTRAINT pk_paquete_uml_01 PRIMARY KEY (id);


--
-- Name: parametro_uml pk_parametro_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.parametro_uml
    ADD CONSTRAINT pk_parametro_uml_01 PRIMARY KEY (id);


--
-- Name: participante_sesion_modelo pk_participante_sesion_modelo_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.participante_sesion_modelo
    ADD CONSTRAINT pk_participante_sesion_modelo_01 PRIMARY KEY (id_sesion_edicion, id_usuario);


--
-- Name: permiso pk_permiso_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.permiso
    ADD CONSTRAINT pk_permiso_01 PRIMARY KEY (id);


--
-- Name: permiso_miembro_proyecto pk_permiso_miembro_proyecto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.permiso_miembro_proyecto
    ADD CONSTRAINT pk_permiso_miembro_proyecto_01 PRIMARY KEY (id);


--
-- Name: posicion_clase_uml pk_posicion_clase_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.posicion_clase_uml
    ADD CONSTRAINT pk_posicion_clase_uml_01 PRIMARY KEY (id_clase);


--
-- Name: posicion_relacion_uml pk_posicion_relacion_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.posicion_relacion_uml
    ADD CONSTRAINT pk_posicion_relacion_uml_01 PRIMARY KEY (id_relacion);


--
-- Name: preferencia_diagrama pk_preferencia_diagrama_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.preferencia_diagrama
    ADD CONSTRAINT pk_preferencia_diagrama_01 PRIMARY KEY (id_modelo, id_usuario);


--
-- Name: propuesta_inteligente pk_propuesta_inteligente_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.propuesta_inteligente
    ADD CONSTRAINT pk_propuesta_inteligente_01 PRIMARY KEY (id);


--
-- Name: proyecto pk_proyecto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.proyecto
    ADD CONSTRAINT pk_proyecto_01 PRIMARY KEY (id);


--
-- Name: regla_validacion pk_regla_validacion_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.regla_validacion
    ADD CONSTRAINT pk_regla_validacion_01 PRIMARY KEY (id);


--
-- Name: relacion_uml pk_relacion_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT pk_relacion_uml_01 PRIMARY KEY (id);


--
-- Name: rol pk_rol_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol
    ADD CONSTRAINT pk_rol_01 PRIMARY KEY (id);


--
-- Name: rol_miembro_proyecto pk_rol_miembro_proyecto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol_miembro_proyecto
    ADD CONSTRAINT pk_rol_miembro_proyecto_01 PRIMARY KEY (id);


--
-- Name: rol_permiso pk_rol_permiso_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol_permiso
    ADD CONSTRAINT pk_rol_permiso_01 PRIMARY KEY (id_rol, id_permiso);


--
-- Name: secreto_servicio pk_secreto_servicio_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.secreto_servicio
    ADD CONSTRAINT pk_secreto_servicio_01 PRIMARY KEY (id);


--
-- Name: servicio_plataforma pk_servicio_plataforma_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.servicio_plataforma
    ADD CONSTRAINT pk_servicio_plataforma_01 PRIMARY KEY (id);


--
-- Name: sesion_autenticacion pk_sesion_autenticacion_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_autenticacion
    ADD CONSTRAINT pk_sesion_autenticacion_01 PRIMARY KEY (id);


--
-- Name: sesion_edicion_modelo pk_sesion_edicion_modelo_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_edicion_modelo
    ADD CONSTRAINT pk_sesion_edicion_modelo_01 PRIMARY KEY (id);


--
-- Name: solicitud_inteligente pk_solicitud_inteligente_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.solicitud_inteligente
    ADD CONSTRAINT pk_solicitud_inteligente_01 PRIMARY KEY (id);


--
-- Name: usuario pk_usuario_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.usuario
    ADD CONSTRAINT pk_usuario_01 PRIMARY KEY (id);


--
-- Name: usuario_rol pk_usuario_rol_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.usuario_rol
    ADD CONSTRAINT pk_usuario_rol_01 PRIMARY KEY (id);


--
-- Name: validacion_backend pk_validacion_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_backend
    ADD CONSTRAINT pk_validacion_backend_01 PRIMARY KEY (id);


--
-- Name: validacion_modelo pk_validacion_modelo_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_modelo
    ADD CONSTRAINT pk_validacion_modelo_01 PRIMARY KEY (id);


--
-- Name: version_modelo_uml pk_version_modelo_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT pk_version_modelo_uml_01 PRIMARY KEY (id);


--
-- Name: archivo_almacenado uq_archivo_almacenado_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.archivo_almacenado
    ADD CONSTRAINT uq_archivo_almacenado_01 UNIQUE (clave_almacenamiento);


--
-- Name: cambio_modelo uq_cambio_modelo_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT uq_cambio_modelo_01 UNIQUE (id_modelo, id_cambio_cliente);


--
-- Name: cambio_modelo uq_cambio_modelo_02; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT uq_cambio_modelo_02 UNIQUE (id_modelo, revision_aplicada);


--
-- Name: cambio_modelo uq_cambio_modelo_03; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT uq_cambio_modelo_03 UNIQUE (id, id_modelo);


--
-- Name: clase_uml uq_clase_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_uml
    ADD CONSTRAINT uq_clase_uml_01 UNIQUE (id, id_modelo);


--
-- Name: configuracion_generacion_backend uq_configuracion_generacion_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.configuracion_generacion_backend
    ADD CONSTRAINT uq_configuracion_generacion_backend_01 UNIQUE (id_version_modelo, nombre);


--
-- Name: dependencia_backend uq_dependencia_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.dependencia_backend
    ADD CONSTRAINT uq_dependencia_backend_01 UNIQUE (id_configuracion, id_grupo, id_artefacto);


--
-- Name: generacion_backend uq_generacion_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.generacion_backend
    ADD CONSTRAINT uq_generacion_backend_01 UNIQUE (id_configuracion, numero_compilacion);


--
-- Name: mapeo_tipo_backend uq_mapeo_tipo_backend_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.mapeo_tipo_backend
    ADD CONSTRAINT uq_mapeo_tipo_backend_01 UNIQUE (id_configuracion, tipo_uml);


--
-- Name: miembro_proyecto uq_miembro_proyecto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.miembro_proyecto
    ADD CONSTRAINT uq_miembro_proyecto_01 UNIQUE (id_proyecto, id_usuario);


--
-- Name: modelo_uml uq_modelo_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.modelo_uml
    ADD CONSTRAINT uq_modelo_uml_01 UNIQUE (id_proyecto, nombre);


--
-- Name: modelo_uml uq_modelo_uml_02; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.modelo_uml
    ADD CONSTRAINT uq_modelo_uml_02 UNIQUE (id, id_proyecto);


--
-- Name: operacion_propuesta uq_operacion_propuesta_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.operacion_propuesta
    ADD CONSTRAINT uq_operacion_propuesta_01 UNIQUE (id_propuesta, numero_secuencia);


--
-- Name: paquete_uml uq_paquete_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.paquete_uml
    ADD CONSTRAINT uq_paquete_uml_01 UNIQUE (id, id_modelo);


--
-- Name: permiso uq_permiso_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.permiso
    ADD CONSTRAINT uq_permiso_01 UNIQUE (codigo);


--
-- Name: permiso uq_permiso_02; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.permiso
    ADD CONSTRAINT uq_permiso_02 UNIQUE (modulo, accion, ambito);


--
-- Name: propuesta_inteligente uq_propuesta_inteligente_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.propuesta_inteligente
    ADD CONSTRAINT uq_propuesta_inteligente_01 UNIQUE (id_solicitud);


--
-- Name: proyecto uq_proyecto_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.proyecto
    ADD CONSTRAINT uq_proyecto_01 UNIQUE (codigo);


--
-- Name: regla_validacion uq_regla_validacion_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.regla_validacion
    ADD CONSTRAINT uq_regla_validacion_01 UNIQUE (codigo);


--
-- Name: rol uq_rol_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol
    ADD CONSTRAINT uq_rol_01 UNIQUE (codigo);


--
-- Name: servicio_plataforma uq_servicio_plataforma_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.servicio_plataforma
    ADD CONSTRAINT uq_servicio_plataforma_01 UNIQUE (nombre);


--
-- Name: sesion_autenticacion uq_sesion_autenticacion_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_autenticacion
    ADD CONSTRAINT uq_sesion_autenticacion_01 UNIQUE (hash_token);


--
-- Name: sesion_autenticacion uq_sesion_autenticacion_02; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_autenticacion
    ADD CONSTRAINT uq_sesion_autenticacion_02 UNIQUE (hash_token_renovacion);


--
-- Name: sesion_edicion_modelo uq_sesion_edicion_modelo_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_edicion_modelo
    ADD CONSTRAINT uq_sesion_edicion_modelo_01 UNIQUE (id, id_modelo);


--
-- Name: usuario uq_usuario_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.usuario
    ADD CONSTRAINT uq_usuario_01 UNIQUE (nombre_usuario);


--
-- Name: usuario uq_usuario_02; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.usuario
    ADD CONSTRAINT uq_usuario_02 UNIQUE (correo_electronico);


--
-- Name: version_modelo_uml uq_version_modelo_uml_01; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT uq_version_modelo_uml_01 UNIQUE (id_modelo, numero_version);


--
-- Name: version_modelo_uml uq_version_modelo_uml_02; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT uq_version_modelo_uml_02 UNIQUE (id_modelo, revision);


--
-- Name: version_modelo_uml uq_version_modelo_uml_03; Type: CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT uq_version_modelo_uml_03 UNIQUE (id, id_modelo);


--
-- Name: ix_actividad_proyecto_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_actividad_proyecto_01 ON uml_colaborativo.actividad_proyecto USING btree (id_proyecto, ocurrido_en DESC);


--
-- Name: ix_actividad_proyecto_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_actividad_proyecto_02 ON uml_colaborativo.actividad_proyecto USING btree (id_modelo, ocurrido_en DESC);


--
-- Name: ix_archivo_almacenado_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_archivo_almacenado_01 ON uml_colaborativo.archivo_almacenado USING btree (id_proyecto, categoria, creado_en DESC);


--
-- Name: ix_atributo_uml_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_atributo_uml_01 ON uml_colaborativo.atributo_uml USING btree (id_clase, lower((nombre)::text)) WHERE (eliminado_en IS NULL);


--
-- Name: ix_atributo_uml_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_atributo_uml_02 ON uml_colaborativo.atributo_uml USING btree (id_clase, orden) WHERE (eliminado_en IS NULL);


--
-- Name: ix_cambio_modelo_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_cambio_modelo_01 ON uml_colaborativo.cambio_modelo USING btree (id_modelo, revision_base, creado_en);


--
-- Name: ix_clase_configuracion_backend_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_clase_configuracion_backend_01 ON uml_colaborativo.clase_configuracion_backend USING btree (id_clase);


--
-- Name: ix_clase_uml_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_clase_uml_01 ON uml_colaborativo.clase_uml USING btree (id_modelo, COALESCE(id_paquete, '00000000-0000-0000-0000-000000000000'::uuid), lower((nombre)::text)) WHERE (eliminado_en IS NULL);


--
-- Name: ix_clase_uml_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_clase_uml_02 ON uml_colaborativo.clase_uml USING btree (id_modelo) WHERE (eliminado_en IS NULL);


--
-- Name: ix_clase_uml_03; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_clase_uml_03 ON uml_colaborativo.clase_uml USING btree (id_paquete);


--
-- Name: ix_comprobacion_servicio_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_comprobacion_servicio_01 ON uml_colaborativo.comprobacion_servicio USING btree (id_servicio, comprobado_en DESC);


--
-- Name: ix_conflicto_edicion_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_conflicto_edicion_01 ON uml_colaborativo.conflicto_edicion USING btree (id_modelo, detectado_en DESC) WHERE ((estado)::text = 'ABIERTO'::text);


--
-- Name: ix_dependencia_operacion_propuesta_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_dependencia_operacion_propuesta_01 ON uml_colaborativo.dependencia_operacion_propuesta USING btree (id_operacion_requerida);


--
-- Name: ix_descarga_artefacto_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_descarga_artefacto_01 ON uml_colaborativo.descarga_artefacto USING btree (id_generacion, descargado_en DESC);


--
-- Name: ix_generacion_backend_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_generacion_backend_01 ON uml_colaborativo.generacion_backend USING btree (estado, solicitado_en);


--
-- Name: ix_hallazgo_validacion_backend_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_hallazgo_validacion_backend_01 ON uml_colaborativo.hallazgo_validacion_backend USING btree (id_validacion);


--
-- Name: ix_hallazgo_validacion_modelo_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_hallazgo_validacion_modelo_01 ON uml_colaborativo.hallazgo_validacion_modelo USING btree (id_validacion);


--
-- Name: ix_intercambio_xmi_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_intercambio_xmi_01 ON uml_colaborativo.intercambio_xmi USING btree (id_modelo, creado_en DESC);


--
-- Name: ix_mapeo_persistencia_backend_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_mapeo_persistencia_backend_01 ON uml_colaborativo.mapeo_persistencia_backend USING btree (id_configuracion, id_atributo_uml) WHERE (id_atributo_uml IS NOT NULL);


--
-- Name: ix_mapeo_persistencia_backend_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_mapeo_persistencia_backend_02 ON uml_colaborativo.mapeo_persistencia_backend USING btree (id_configuracion, id_relacion_uml) WHERE (id_relacion_uml IS NOT NULL);


--
-- Name: ix_miembro_proyecto_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_miembro_proyecto_01 ON uml_colaborativo.miembro_proyecto USING btree (id_usuario, estado);


--
-- Name: ix_miembro_proyecto_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_miembro_proyecto_02 ON uml_colaborativo.miembro_proyecto USING btree (id_proyecto, estado);


--
-- Name: ix_modelo_uml_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_modelo_uml_01 ON uml_colaborativo.modelo_uml USING btree (id_proyecto, estado);


--
-- Name: ix_operacion_uml_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_operacion_uml_01 ON uml_colaborativo.operacion_uml USING btree (id_clase, orden) WHERE (eliminado_en IS NULL);


--
-- Name: ix_paquete_uml_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_paquete_uml_01 ON uml_colaborativo.paquete_uml USING btree (id_modelo, COALESCE(id_paquete_padre, '00000000-0000-0000-0000-000000000000'::uuid), lower((nombre)::text)) WHERE (eliminado_en IS NULL);


--
-- Name: ix_paquete_uml_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_paquete_uml_02 ON uml_colaborativo.paquete_uml USING btree (id_paquete_padre);


--
-- Name: ix_parametro_uml_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_parametro_uml_01 ON uml_colaborativo.parametro_uml USING btree (id_operacion, orden) WHERE (eliminado_en IS NULL);


--
-- Name: ix_parametro_uml_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_parametro_uml_02 ON uml_colaborativo.parametro_uml USING btree (id_operacion, lower((nombre)::text)) WHERE (eliminado_en IS NULL);


--
-- Name: ix_permiso_miembro_proyecto_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_permiso_miembro_proyecto_01 ON uml_colaborativo.permiso_miembro_proyecto USING btree (id_miembro_proyecto, id_permiso) WHERE (revocado_en IS NULL);


--
-- Name: ix_permiso_miembro_proyecto_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_permiso_miembro_proyecto_02 ON uml_colaborativo.permiso_miembro_proyecto USING btree (id_miembro_proyecto);


--
-- Name: ix_propuesta_inteligente_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_propuesta_inteligente_01 ON uml_colaborativo.propuesta_inteligente USING btree (estado, creado_en);


--
-- Name: ix_proyecto_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_proyecto_01 ON uml_colaborativo.proyecto USING btree (id_usuario_propietario, estado);


--
-- Name: ix_relacion_uml_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_relacion_uml_01 ON uml_colaborativo.relacion_uml USING btree (id_modelo, id_externo_xmi) WHERE ((eliminado_en IS NULL) AND (id_externo_xmi IS NOT NULL));


--
-- Name: ix_relacion_uml_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_relacion_uml_02 ON uml_colaborativo.relacion_uml USING btree (id_modelo) WHERE (eliminado_en IS NULL);


--
-- Name: ix_relacion_uml_03; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_relacion_uml_03 ON uml_colaborativo.relacion_uml USING btree (id_clase_origen);


--
-- Name: ix_relacion_uml_04; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_relacion_uml_04 ON uml_colaborativo.relacion_uml USING btree (id_clase_destino);


--
-- Name: ix_rol_miembro_proyecto_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_rol_miembro_proyecto_01 ON uml_colaborativo.rol_miembro_proyecto USING btree (id_miembro_proyecto, id_rol) WHERE (revocado_en IS NULL);


--
-- Name: ix_rol_miembro_proyecto_02; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_rol_miembro_proyecto_02 ON uml_colaborativo.rol_miembro_proyecto USING btree (id_miembro_proyecto);


--
-- Name: ix_rol_permiso_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_rol_permiso_01 ON uml_colaborativo.rol_permiso USING btree (id_permiso);


--
-- Name: ix_secreto_servicio_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_secreto_servicio_01 ON uml_colaborativo.secreto_servicio USING btree (id_servicio) WHERE (retirado_en IS NULL);


--
-- Name: ix_sesion_autenticacion_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_sesion_autenticacion_01 ON uml_colaborativo.sesion_autenticacion USING btree (id_usuario, expira_en DESC) WHERE (revocado_en IS NULL);


--
-- Name: ix_sesion_edicion_modelo_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_sesion_edicion_modelo_01 ON uml_colaborativo.sesion_edicion_modelo USING btree (id_modelo, ultima_actividad_en DESC) WHERE ((estado)::text = 'ACTIVO'::text);


--
-- Name: ix_solicitud_inteligente_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_solicitud_inteligente_01 ON uml_colaborativo.solicitud_inteligente USING btree (id_modelo, recibido_en DESC);


--
-- Name: ix_usuario_rol_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE UNIQUE INDEX ix_usuario_rol_01 ON uml_colaborativo.usuario_rol USING btree (id_usuario, id_rol) WHERE (revocado_en IS NULL);


--
-- Name: ix_validacion_backend_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_validacion_backend_01 ON uml_colaborativo.validacion_backend USING btree (id_generacion, iniciado_en DESC, id DESC);


--
-- Name: ix_validacion_modelo_01; Type: INDEX; Schema: uml_colaborativo; Owner: -
--

CREATE INDEX ix_validacion_modelo_01 ON uml_colaborativo.validacion_modelo USING btree (id_modelo, iniciado_en DESC);


--
-- Name: actividad_proyecto trg_actividad_proyecto_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_actividad_proyecto_01 BEFORE DELETE OR UPDATE ON uml_colaborativo.actividad_proyecto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_registro_inmutable();


--
-- Name: ambiguedad_propuesta trg_ambiguedad_propuesta_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_ambiguedad_propuesta_01 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.ambiguedad_propuesta FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_detalles_propuesta();


--
-- Name: archivo_almacenado trg_archivo_almacenado_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_archivo_almacenado_01 BEFORE UPDATE ON uml_colaborativo.archivo_almacenado FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_proyecto', 'categoria', 'clave_almacenamiento', 'sha256', 'tamano_bytes');


--
-- Name: atributo_uml trg_atributo_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_atributo_uml_01 BEFORE UPDATE ON uml_colaborativo.atributo_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila();


--
-- Name: atributo_uml trg_atributo_uml_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_atributo_uml_02 BEFORE UPDATE ON uml_colaborativo.atributo_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_clase');


--
-- Name: cambio_modelo trg_cambio_modelo_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_cambio_modelo_01 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.cambio_modelo FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_historial_cambio();


--
-- Name: cambio_modelo trg_cambio_modelo_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_cambio_modelo_02 BEFORE UPDATE ON uml_colaborativo.cambio_modelo FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_modelo', 'id_cambio_cliente', 'revision_base', 'id_usuario_actor');


--
-- Name: clase_configuracion_backend trg_clase_configuracion_backend_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_clase_configuracion_backend_01 BEFORE INSERT OR UPDATE OF id_configuracion, id_clase ON uml_colaborativo.clase_configuracion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_clase_configuracion_modelo();


--
-- Name: clase_configuracion_backend trg_clase_configuracion_backend_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_clase_configuracion_backend_02 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.clase_configuracion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_configuracion_utilizada();


--
-- Name: clase_configuracion_backend trg_clase_configuracion_backend_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_clase_configuracion_backend_03 BEFORE INSERT OR UPDATE ON uml_colaborativo.clase_configuracion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_mapeo_instantanea();


--
-- Name: clase_configuracion_backend trg_clase_configuracion_backend_04; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_clase_configuracion_backend_04 BEFORE UPDATE ON uml_colaborativo.clase_configuracion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id_configuracion');


--
-- Name: clase_uml trg_clase_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_clase_uml_01 BEFORE UPDATE ON uml_colaborativo.clase_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila();


--
-- Name: clase_uml trg_clase_uml_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_clase_uml_02 BEFORE UPDATE ON uml_colaborativo.clase_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_modelo');


--
-- Name: configuracion_generacion_backend trg_configuracion_generacion_backend_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_configuracion_generacion_backend_01 BEFORE UPDATE ON uml_colaborativo.configuracion_generacion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: configuracion_generacion_backend trg_configuracion_generacion_backend_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_configuracion_generacion_backend_02 BEFORE DELETE OR UPDATE ON uml_colaborativo.configuracion_generacion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_configuracion_utilizada();


--
-- Name: configuracion_generacion_backend trg_configuracion_generacion_backend_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_configuracion_generacion_backend_03 BEFORE UPDATE ON uml_colaborativo.configuracion_generacion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_version_modelo');


--
-- Name: dependencia_backend trg_dependencia_backend_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_dependencia_backend_01 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.dependencia_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_configuracion_utilizada();


--
-- Name: dependencia_backend trg_dependencia_backend_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_dependencia_backend_02 BEFORE UPDATE ON uml_colaborativo.dependencia_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id_configuracion');


--
-- Name: dependencia_operacion_propuesta trg_dependencia_operacion_propuesta_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_dependencia_operacion_propuesta_01 BEFORE INSERT OR UPDATE ON uml_colaborativo.dependencia_operacion_propuesta FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_dependencia_propuesta();


--
-- Name: dependencia_operacion_propuesta trg_dependencia_operacion_propuesta_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_dependencia_operacion_propuesta_02 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.dependencia_operacion_propuesta FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_detalles_propuesta();


--
-- Name: descarga_artefacto trg_descarga_artefacto_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_descarga_artefacto_01 BEFORE DELETE OR UPDATE ON uml_colaborativo.descarga_artefacto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_registro_inmutable();


--
-- Name: descarga_artefacto trg_descarga_artefacto_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_descarga_artefacto_02 BEFORE INSERT ON uml_colaborativo.descarga_artefacto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_descarga_artefacto();


--
-- Name: generacion_backend trg_generacion_backend_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_generacion_backend_01 BEFORE INSERT ON uml_colaborativo.generacion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_configuracion_generacion();


--
-- Name: generacion_backend trg_generacion_backend_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_generacion_backend_02 BEFORE UPDATE ON uml_colaborativo.generacion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_configuracion');


--
-- Name: generacion_backend trg_generacion_backend_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_generacion_backend_03 BEFORE INSERT OR UPDATE ON uml_colaborativo.generacion_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_archivos_operacion();


--
-- Name: intercambio_xmi trg_intercambio_xmi_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_intercambio_xmi_01 BEFORE INSERT OR UPDATE ON uml_colaborativo.intercambio_xmi FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_archivos_operacion();


--
-- Name: mapeo_persistencia_backend trg_mapeo_persistencia_backend_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_mapeo_persistencia_backend_01 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.mapeo_persistencia_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_configuracion_utilizada();


--
-- Name: mapeo_persistencia_backend trg_mapeo_persistencia_backend_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_mapeo_persistencia_backend_02 BEFORE INSERT OR UPDATE ON uml_colaborativo.mapeo_persistencia_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_mapeo_instantanea();


--
-- Name: mapeo_persistencia_backend trg_mapeo_persistencia_backend_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_mapeo_persistencia_backend_03 BEFORE UPDATE ON uml_colaborativo.mapeo_persistencia_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id_configuracion');


--
-- Name: mapeo_tipo_backend trg_mapeo_tipo_backend_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_mapeo_tipo_backend_01 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.mapeo_tipo_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_configuracion_utilizada();


--
-- Name: mapeo_tipo_backend trg_mapeo_tipo_backend_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_mapeo_tipo_backend_02 BEFORE UPDATE ON uml_colaborativo.mapeo_tipo_backend FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id_configuracion');


--
-- Name: miembro_proyecto trg_miembro_proyecto_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_miembro_proyecto_01 BEFORE UPDATE ON uml_colaborativo.miembro_proyecto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: miembro_proyecto trg_miembro_proyecto_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_miembro_proyecto_02 AFTER INSERT OR DELETE OR UPDATE ON uml_colaborativo.miembro_proyecto DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.comprobar_propietario_proyecto();


--
-- Name: miembro_proyecto trg_miembro_proyecto_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_miembro_proyecto_03 BEFORE UPDATE ON uml_colaborativo.miembro_proyecto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_proyecto', 'id_usuario');


--
-- Name: modelo_uml trg_modelo_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_modelo_uml_01 BEFORE UPDATE ON uml_colaborativo.modelo_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: modelo_uml trg_modelo_uml_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_modelo_uml_02 BEFORE UPDATE ON uml_colaborativo.modelo_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_proyecto');


--
-- Name: operacion_propuesta trg_operacion_propuesta_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_operacion_propuesta_01 BEFORE UPDATE ON uml_colaborativo.operacion_propuesta FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_propuesta');


--
-- Name: operacion_propuesta trg_operacion_propuesta_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_operacion_propuesta_02 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.operacion_propuesta FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_detalles_propuesta();


--
-- Name: operacion_uml trg_operacion_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_operacion_uml_01 BEFORE UPDATE ON uml_colaborativo.operacion_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila();


--
-- Name: operacion_uml trg_operacion_uml_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_operacion_uml_02 BEFORE UPDATE ON uml_colaborativo.operacion_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_clase');


--
-- Name: operacion_uml trg_operacion_uml_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_operacion_uml_03 AFTER INSERT OR DELETE OR UPDATE ON uml_colaborativo.operacion_uml DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.comprobar_firmas_operaciones();


--
-- Name: paquete_uml trg_paquete_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_paquete_uml_01 BEFORE UPDATE ON uml_colaborativo.paquete_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila();


--
-- Name: paquete_uml trg_paquete_uml_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_paquete_uml_02 BEFORE UPDATE ON uml_colaborativo.paquete_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_modelo');


--
-- Name: paquete_uml trg_paquete_uml_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_paquete_uml_03 BEFORE INSERT OR UPDATE OF id_paquete_padre ON uml_colaborativo.paquete_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_ciclo_paquetes();


--
-- Name: parametro_uml trg_parametro_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_parametro_uml_01 BEFORE UPDATE ON uml_colaborativo.parametro_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila();


--
-- Name: parametro_uml trg_parametro_uml_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_parametro_uml_02 BEFORE UPDATE ON uml_colaborativo.parametro_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_operacion');


--
-- Name: parametro_uml trg_parametro_uml_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_parametro_uml_03 AFTER INSERT OR DELETE OR UPDATE ON uml_colaborativo.parametro_uml DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.comprobar_firmas_operaciones();


--
-- Name: permiso_miembro_proyecto trg_permiso_miembro_proyecto_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_permiso_miembro_proyecto_01 BEFORE INSERT OR UPDATE ON uml_colaborativo.permiso_miembro_proyecto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_ambito_permiso_excepcional();


--
-- Name: propuesta_inteligente trg_propuesta_inteligente_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_propuesta_inteligente_01 BEFORE UPDATE ON uml_colaborativo.propuesta_inteligente FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: propuesta_inteligente trg_propuesta_inteligente_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_propuesta_inteligente_02 BEFORE UPDATE ON uml_colaborativo.propuesta_inteligente FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_solicitud');


--
-- Name: propuesta_inteligente trg_propuesta_inteligente_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_propuesta_inteligente_03 BEFORE INSERT OR UPDATE ON uml_colaborativo.propuesta_inteligente FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_estado_propuesta();


--
-- Name: proyecto trg_proyecto_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_proyecto_01 BEFORE UPDATE ON uml_colaborativo.proyecto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: proyecto trg_proyecto_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_proyecto_02 AFTER INSERT OR UPDATE ON uml_colaborativo.proyecto DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.comprobar_propietario_proyecto();


--
-- Name: relacion_uml trg_relacion_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_relacion_uml_01 BEFORE UPDATE ON uml_colaborativo.relacion_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila();


--
-- Name: relacion_uml trg_relacion_uml_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_relacion_uml_02 BEFORE INSERT OR UPDATE ON uml_colaborativo.relacion_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_ciclo_generalizacion();


--
-- Name: relacion_uml trg_relacion_uml_03; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_relacion_uml_03 BEFORE UPDATE ON uml_colaborativo.relacion_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_modelo');


--
-- Name: rol trg_rol_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_rol_01 BEFORE UPDATE ON uml_colaborativo.rol FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: rol trg_rol_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_rol_02 BEFORE DELETE OR UPDATE ON uml_colaborativo.rol FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_rol_sistema();


--
-- Name: rol_miembro_proyecto trg_rol_miembro_proyecto_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_rol_miembro_proyecto_01 AFTER INSERT OR DELETE OR UPDATE ON uml_colaborativo.rol_miembro_proyecto DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.comprobar_propietario_proyecto();


--
-- Name: rol_miembro_proyecto trg_rol_miembro_proyecto_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_rol_miembro_proyecto_02 BEFORE INSERT OR UPDATE ON uml_colaborativo.rol_miembro_proyecto FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_ambito_rol();


--
-- Name: rol_permiso trg_rol_permiso_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_rol_permiso_01 BEFORE INSERT OR UPDATE ON uml_colaborativo.rol_permiso FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_ambito_rol();


--
-- Name: servicio_plataforma trg_servicio_plataforma_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_servicio_plataforma_01 BEFORE UPDATE ON uml_colaborativo.servicio_plataforma FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: solicitud_inteligente trg_solicitud_inteligente_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_solicitud_inteligente_01 BEFORE UPDATE ON uml_colaborativo.solicitud_inteligente FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.impedir_cambio_identidad('id', 'id_modelo', 'revision_base');


--
-- Name: solicitud_inteligente trg_solicitud_inteligente_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_solicitud_inteligente_02 BEFORE INSERT OR UPDATE ON uml_colaborativo.solicitud_inteligente FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.verificar_archivos_operacion();


--
-- Name: usuario trg_usuario_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_usuario_01 BEFORE UPDATE ON uml_colaborativo.usuario FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.actualizar_fecha_modificacion();


--
-- Name: usuario trg_usuario_02; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_usuario_02 AFTER UPDATE OF estado ON uml_colaborativo.usuario DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.comprobar_proyectos_propietario();


--
-- Name: usuario_rol trg_usuario_rol_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_usuario_rol_01 BEFORE INSERT OR UPDATE ON uml_colaborativo.usuario_rol FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_ambito_rol();


--
-- Name: version_modelo_uml trg_version_modelo_uml_01; Type: TRIGGER; Schema: uml_colaborativo; Owner: -
--

CREATE TRIGGER trg_version_modelo_uml_01 BEFORE INSERT OR DELETE OR UPDATE ON uml_colaborativo.version_modelo_uml FOR EACH ROW EXECUTE FUNCTION uml_colaborativo.proteger_version_modelo();


--
-- Name: actividad_proyecto fk_actividad_proyecto_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.actividad_proyecto
    ADD CONSTRAINT fk_actividad_proyecto_01 FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE;


--
-- Name: actividad_proyecto fk_actividad_proyecto_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.actividad_proyecto
    ADD CONSTRAINT fk_actividad_proyecto_02 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE SET NULL;


--
-- Name: actividad_proyecto fk_actividad_proyecto_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.actividad_proyecto
    ADD CONSTRAINT fk_actividad_proyecto_03 FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL;


--
-- Name: actividad_proyecto fk_actividad_proyecto_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.actividad_proyecto
    ADD CONSTRAINT fk_actividad_proyecto_04 FOREIGN KEY (id_usuario_actor) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: actividad_proyecto fk_actividad_proyecto_05; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.actividad_proyecto
    ADD CONSTRAINT fk_actividad_proyecto_05 FOREIGN KEY (id_modelo, id_proyecto) REFERENCES uml_colaborativo.modelo_uml(id, id_proyecto);


--
-- Name: actividad_proyecto fk_actividad_proyecto_06; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.actividad_proyecto
    ADD CONSTRAINT fk_actividad_proyecto_06 FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo);


--
-- Name: ambiguedad_propuesta fk_ambiguedad_propuesta_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.ambiguedad_propuesta
    ADD CONSTRAINT fk_ambiguedad_propuesta_01 FOREIGN KEY (id_propuesta) REFERENCES uml_colaborativo.propuesta_inteligente(id) ON DELETE CASCADE;


--
-- Name: ambiguedad_propuesta fk_ambiguedad_propuesta_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.ambiguedad_propuesta
    ADD CONSTRAINT fk_ambiguedad_propuesta_02 FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE;


--
-- Name: ambiguedad_propuesta fk_ambiguedad_propuesta_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.ambiguedad_propuesta
    ADD CONSTRAINT fk_ambiguedad_propuesta_03 FOREIGN KEY (resuelto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: archivo_almacenado fk_archivo_almacenado_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.archivo_almacenado
    ADD CONSTRAINT fk_archivo_almacenado_01 FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE;


--
-- Name: archivo_almacenado fk_archivo_almacenado_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.archivo_almacenado
    ADD CONSTRAINT fk_archivo_almacenado_02 FOREIGN KEY (cargado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: atributo_uml fk_atributo_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.atributo_uml
    ADD CONSTRAINT fk_atributo_uml_01 FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE;


--
-- Name: cambio_modelo fk_cambio_modelo_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT fk_cambio_modelo_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: cambio_modelo fk_cambio_modelo_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT fk_cambio_modelo_02 FOREIGN KEY (id_sesion_edicion) REFERENCES uml_colaborativo.sesion_edicion_modelo(id) ON DELETE SET NULL;


--
-- Name: cambio_modelo fk_cambio_modelo_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT fk_cambio_modelo_03 FOREIGN KEY (id_usuario_actor) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: cambio_modelo fk_cambio_modelo_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.cambio_modelo
    ADD CONSTRAINT fk_cambio_modelo_04 FOREIGN KEY (id_sesion_edicion, id_modelo) REFERENCES uml_colaborativo.sesion_edicion_modelo(id, id_modelo);


--
-- Name: clase_configuracion_backend fk_clase_configuracion_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_configuracion_backend
    ADD CONSTRAINT fk_clase_configuracion_backend_01 FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE;


--
-- Name: clase_configuracion_backend fk_clase_configuracion_backend_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_configuracion_backend
    ADD CONSTRAINT fk_clase_configuracion_backend_02 FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT;


--
-- Name: clase_uml fk_clase_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_uml
    ADD CONSTRAINT fk_clase_uml_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: clase_uml fk_clase_uml_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_uml
    ADD CONSTRAINT fk_clase_uml_02 FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: clase_uml fk_clase_uml_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_uml
    ADD CONSTRAINT fk_clase_uml_03 FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: clase_uml fk_clase_uml_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.clase_uml
    ADD CONSTRAINT fk_clase_uml_04 FOREIGN KEY (id_paquete, id_modelo) REFERENCES uml_colaborativo.paquete_uml(id, id_modelo) ON DELETE SET NULL (id_paquete) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: comprobacion_servicio fk_comprobacion_servicio_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.comprobacion_servicio
    ADD CONSTRAINT fk_comprobacion_servicio_01 FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE CASCADE;


--
-- Name: comprobacion_servicio fk_comprobacion_servicio_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.comprobacion_servicio
    ADD CONSTRAINT fk_comprobacion_servicio_02 FOREIGN KEY (comprobado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: configuracion_generacion_backend fk_configuracion_generacion_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.configuracion_generacion_backend
    ADD CONSTRAINT fk_configuracion_generacion_backend_01 FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE RESTRICT;


--
-- Name: configuracion_generacion_backend fk_configuracion_generacion_backend_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.configuracion_generacion_backend
    ADD CONSTRAINT fk_configuracion_generacion_backend_02 FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: conflicto_edicion fk_conflicto_edicion_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT fk_conflicto_edicion_01 FOREIGN KEY (id_cambio_almacenado) REFERENCES uml_colaborativo.cambio_modelo(id) ON DELETE SET NULL;


--
-- Name: conflicto_edicion fk_conflicto_edicion_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT fk_conflicto_edicion_02 FOREIGN KEY (id_cambio_entrante) REFERENCES uml_colaborativo.cambio_modelo(id) ON DELETE SET NULL;


--
-- Name: conflicto_edicion fk_conflicto_edicion_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT fk_conflicto_edicion_03 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: conflicto_edicion fk_conflicto_edicion_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT fk_conflicto_edicion_04 FOREIGN KEY (resuelto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: conflicto_edicion fk_conflicto_edicion_05; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT fk_conflicto_edicion_05 FOREIGN KEY (id_cambio_entrante, id_modelo) REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo);


--
-- Name: conflicto_edicion fk_conflicto_edicion_06; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT fk_conflicto_edicion_06 FOREIGN KEY (id_cambio_almacenado, id_modelo) REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo);


--
-- Name: dependencia_backend fk_dependencia_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.dependencia_backend
    ADD CONSTRAINT fk_dependencia_backend_01 FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE;


--
-- Name: dependencia_operacion_propuesta fk_dependencia_operacion_propuesta_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.dependencia_operacion_propuesta
    ADD CONSTRAINT fk_dependencia_operacion_propuesta_01 FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE;


--
-- Name: dependencia_operacion_propuesta fk_dependencia_operacion_propuesta_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.dependencia_operacion_propuesta
    ADD CONSTRAINT fk_dependencia_operacion_propuesta_02 FOREIGN KEY (id_operacion_requerida) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE;


--
-- Name: descarga_artefacto fk_descarga_artefacto_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.descarga_artefacto
    ADD CONSTRAINT fk_descarga_artefacto_01 FOREIGN KEY (id_generacion) REFERENCES uml_colaborativo.generacion_backend(id) ON DELETE RESTRICT;


--
-- Name: descarga_artefacto fk_descarga_artefacto_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.descarga_artefacto
    ADD CONSTRAINT fk_descarga_artefacto_02 FOREIGN KEY (id_archivo) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE RESTRICT;


--
-- Name: descarga_artefacto fk_descarga_artefacto_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.descarga_artefacto
    ADD CONSTRAINT fk_descarga_artefacto_03 FOREIGN KEY (descargado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: generacion_backend fk_generacion_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.generacion_backend
    ADD CONSTRAINT fk_generacion_backend_01 FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE RESTRICT;


--
-- Name: generacion_backend fk_generacion_backend_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.generacion_backend
    ADD CONSTRAINT fk_generacion_backend_02 FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: generacion_backend fk_generacion_backend_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.generacion_backend
    ADD CONSTRAINT fk_generacion_backend_03 FOREIGN KEY (id_archivo_salida) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL;


--
-- Name: generacion_backend fk_generacion_backend_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.generacion_backend
    ADD CONSTRAINT fk_generacion_backend_04 FOREIGN KEY (id_archivo_registro) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL;


--
-- Name: hallazgo_validacion_backend fk_hallazgo_validacion_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.hallazgo_validacion_backend
    ADD CONSTRAINT fk_hallazgo_validacion_backend_01 FOREIGN KEY (id_validacion) REFERENCES uml_colaborativo.validacion_backend(id) ON DELETE CASCADE;


--
-- Name: hallazgo_validacion_modelo fk_hallazgo_validacion_modelo_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.hallazgo_validacion_modelo
    ADD CONSTRAINT fk_hallazgo_validacion_modelo_01 FOREIGN KEY (id_validacion) REFERENCES uml_colaborativo.validacion_modelo(id) ON DELETE CASCADE;


--
-- Name: hallazgo_validacion_modelo fk_hallazgo_validacion_modelo_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.hallazgo_validacion_modelo
    ADD CONSTRAINT fk_hallazgo_validacion_modelo_02 FOREIGN KEY (id_regla) REFERENCES uml_colaborativo.regla_validacion(id) ON DELETE SET NULL;


--
-- Name: intercambio_xmi fk_intercambio_xmi_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: intercambio_xmi fk_intercambio_xmi_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_02 FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL;


--
-- Name: intercambio_xmi fk_intercambio_xmi_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_03 FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: intercambio_xmi fk_intercambio_xmi_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_04 FOREIGN KEY (id_archivo_entrada) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL;


--
-- Name: intercambio_xmi fk_intercambio_xmi_05; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_05 FOREIGN KEY (id_archivo_salida) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL;


--
-- Name: intercambio_xmi fk_intercambio_xmi_06; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_06 FOREIGN KEY (id_version_importada) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL;


--
-- Name: intercambio_xmi fk_intercambio_xmi_07; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_07 FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo);


--
-- Name: intercambio_xmi fk_intercambio_xmi_08; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.intercambio_xmi
    ADD CONSTRAINT fk_intercambio_xmi_08 FOREIGN KEY (id_version_importada, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo);


--
-- Name: mapeo_persistencia_backend fk_mapeo_persistencia_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.mapeo_persistencia_backend
    ADD CONSTRAINT fk_mapeo_persistencia_backend_01 FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE;


--
-- Name: mapeo_persistencia_backend fk_mapeo_persistencia_backend_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.mapeo_persistencia_backend
    ADD CONSTRAINT fk_mapeo_persistencia_backend_02 FOREIGN KEY (id_atributo_uml) REFERENCES uml_colaborativo.atributo_uml(id) ON DELETE RESTRICT;


--
-- Name: mapeo_persistencia_backend fk_mapeo_persistencia_backend_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.mapeo_persistencia_backend
    ADD CONSTRAINT fk_mapeo_persistencia_backend_03 FOREIGN KEY (id_relacion_uml) REFERENCES uml_colaborativo.relacion_uml(id) ON DELETE RESTRICT;


--
-- Name: mapeo_tipo_backend fk_mapeo_tipo_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.mapeo_tipo_backend
    ADD CONSTRAINT fk_mapeo_tipo_backend_01 FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE;


--
-- Name: miembro_proyecto fk_miembro_proyecto_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.miembro_proyecto
    ADD CONSTRAINT fk_miembro_proyecto_01 FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE;


--
-- Name: miembro_proyecto fk_miembro_proyecto_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.miembro_proyecto
    ADD CONSTRAINT fk_miembro_proyecto_02 FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: modelo_uml fk_modelo_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.modelo_uml
    ADD CONSTRAINT fk_modelo_uml_01 FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE;


--
-- Name: modelo_uml fk_modelo_uml_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.modelo_uml
    ADD CONSTRAINT fk_modelo_uml_02 FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: modelo_uml fk_modelo_uml_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.modelo_uml
    ADD CONSTRAINT fk_modelo_uml_03 FOREIGN KEY (id_version_actual, id) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: operacion_propuesta fk_operacion_propuesta_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.operacion_propuesta
    ADD CONSTRAINT fk_operacion_propuesta_01 FOREIGN KEY (id_propuesta) REFERENCES uml_colaborativo.propuesta_inteligente(id) ON DELETE CASCADE;


--
-- Name: operacion_uml fk_operacion_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.operacion_uml
    ADD CONSTRAINT fk_operacion_uml_01 FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE;


--
-- Name: paquete_uml fk_paquete_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.paquete_uml
    ADD CONSTRAINT fk_paquete_uml_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: paquete_uml fk_paquete_uml_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.paquete_uml
    ADD CONSTRAINT fk_paquete_uml_02 FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: paquete_uml fk_paquete_uml_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.paquete_uml
    ADD CONSTRAINT fk_paquete_uml_03 FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: paquete_uml fk_paquete_uml_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.paquete_uml
    ADD CONSTRAINT fk_paquete_uml_04 FOREIGN KEY (id_paquete_padre, id_modelo) REFERENCES uml_colaborativo.paquete_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: parametro_uml fk_parametro_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.parametro_uml
    ADD CONSTRAINT fk_parametro_uml_01 FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_uml(id) ON DELETE CASCADE;


--
-- Name: participante_sesion_modelo fk_participante_sesion_modelo_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.participante_sesion_modelo
    ADD CONSTRAINT fk_participante_sesion_modelo_01 FOREIGN KEY (id_sesion_edicion) REFERENCES uml_colaborativo.sesion_edicion_modelo(id) ON DELETE CASCADE;


--
-- Name: participante_sesion_modelo fk_participante_sesion_modelo_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.participante_sesion_modelo
    ADD CONSTRAINT fk_participante_sesion_modelo_02 FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE;


--
-- Name: permiso_miembro_proyecto fk_permiso_miembro_proyecto_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.permiso_miembro_proyecto
    ADD CONSTRAINT fk_permiso_miembro_proyecto_01 FOREIGN KEY (id_miembro_proyecto) REFERENCES uml_colaborativo.miembro_proyecto(id) ON DELETE CASCADE;


--
-- Name: permiso_miembro_proyecto fk_permiso_miembro_proyecto_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.permiso_miembro_proyecto
    ADD CONSTRAINT fk_permiso_miembro_proyecto_02 FOREIGN KEY (id_permiso) REFERENCES uml_colaborativo.permiso(id) ON DELETE CASCADE;


--
-- Name: permiso_miembro_proyecto fk_permiso_miembro_proyecto_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.permiso_miembro_proyecto
    ADD CONSTRAINT fk_permiso_miembro_proyecto_03 FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: posicion_clase_uml fk_posicion_clase_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.posicion_clase_uml
    ADD CONSTRAINT fk_posicion_clase_uml_01 FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE;


--
-- Name: posicion_relacion_uml fk_posicion_relacion_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.posicion_relacion_uml
    ADD CONSTRAINT fk_posicion_relacion_uml_01 FOREIGN KEY (id_relacion) REFERENCES uml_colaborativo.relacion_uml(id) ON DELETE CASCADE;


--
-- Name: preferencia_diagrama fk_preferencia_diagrama_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.preferencia_diagrama
    ADD CONSTRAINT fk_preferencia_diagrama_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: preferencia_diagrama fk_preferencia_diagrama_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.preferencia_diagrama
    ADD CONSTRAINT fk_preferencia_diagrama_02 FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE;


--
-- Name: propuesta_inteligente fk_propuesta_inteligente_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.propuesta_inteligente
    ADD CONSTRAINT fk_propuesta_inteligente_01 FOREIGN KEY (id_solicitud) REFERENCES uml_colaborativo.solicitud_inteligente(id) ON DELETE CASCADE;


--
-- Name: propuesta_inteligente fk_propuesta_inteligente_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.propuesta_inteligente
    ADD CONSTRAINT fk_propuesta_inteligente_02 FOREIGN KEY (revisado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: propuesta_inteligente fk_propuesta_inteligente_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.propuesta_inteligente
    ADD CONSTRAINT fk_propuesta_inteligente_03 FOREIGN KEY (aplicado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: propuesta_inteligente fk_propuesta_inteligente_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.propuesta_inteligente
    ADD CONSTRAINT fk_propuesta_inteligente_04 FOREIGN KEY (id_version_aplicada) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL;


--
-- Name: propuesta_inteligente fk_propuesta_inteligente_05; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.propuesta_inteligente
    ADD CONSTRAINT fk_propuesta_inteligente_05 FOREIGN KEY (rechazado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: proyecto fk_proyecto_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.proyecto
    ADD CONSTRAINT fk_proyecto_01 FOREIGN KEY (id_usuario_propietario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: relacion_uml fk_relacion_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT fk_relacion_uml_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: relacion_uml fk_relacion_uml_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT fk_relacion_uml_02 FOREIGN KEY (id_clase_origen) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT;


--
-- Name: relacion_uml fk_relacion_uml_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT fk_relacion_uml_03 FOREIGN KEY (id_clase_destino) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT;


--
-- Name: relacion_uml fk_relacion_uml_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT fk_relacion_uml_04 FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: relacion_uml fk_relacion_uml_05; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT fk_relacion_uml_05 FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: relacion_uml fk_relacion_uml_06; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT fk_relacion_uml_06 FOREIGN KEY (id_clase_origen, id_modelo) REFERENCES uml_colaborativo.clase_uml(id, id_modelo);


--
-- Name: relacion_uml fk_relacion_uml_07; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.relacion_uml
    ADD CONSTRAINT fk_relacion_uml_07 FOREIGN KEY (id_clase_destino, id_modelo) REFERENCES uml_colaborativo.clase_uml(id, id_modelo);


--
-- Name: rol_miembro_proyecto fk_rol_miembro_proyecto_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol_miembro_proyecto
    ADD CONSTRAINT fk_rol_miembro_proyecto_01 FOREIGN KEY (id_miembro_proyecto) REFERENCES uml_colaborativo.miembro_proyecto(id) ON DELETE CASCADE;


--
-- Name: rol_miembro_proyecto fk_rol_miembro_proyecto_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol_miembro_proyecto
    ADD CONSTRAINT fk_rol_miembro_proyecto_02 FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE RESTRICT;


--
-- Name: rol_miembro_proyecto fk_rol_miembro_proyecto_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol_miembro_proyecto
    ADD CONSTRAINT fk_rol_miembro_proyecto_03 FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: rol_permiso fk_rol_permiso_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol_permiso
    ADD CONSTRAINT fk_rol_permiso_01 FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE CASCADE;


--
-- Name: rol_permiso fk_rol_permiso_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.rol_permiso
    ADD CONSTRAINT fk_rol_permiso_02 FOREIGN KEY (id_permiso) REFERENCES uml_colaborativo.permiso(id) ON DELETE CASCADE;


--
-- Name: secreto_servicio fk_secreto_servicio_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.secreto_servicio
    ADD CONSTRAINT fk_secreto_servicio_01 FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE CASCADE;


--
-- Name: servicio_plataforma fk_servicio_plataforma_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.servicio_plataforma
    ADD CONSTRAINT fk_servicio_plataforma_01 FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: sesion_autenticacion fk_sesion_autenticacion_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_autenticacion
    ADD CONSTRAINT fk_sesion_autenticacion_01 FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE;


--
-- Name: sesion_edicion_modelo fk_sesion_edicion_modelo_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_edicion_modelo
    ADD CONSTRAINT fk_sesion_edicion_modelo_01 FOREIGN KEY (abierto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: sesion_edicion_modelo fk_sesion_edicion_modelo_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.sesion_edicion_modelo
    ADD CONSTRAINT fk_sesion_edicion_modelo_02 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: solicitud_inteligente fk_solicitud_inteligente_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.solicitud_inteligente
    ADD CONSTRAINT fk_solicitud_inteligente_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: solicitud_inteligente fk_solicitud_inteligente_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.solicitud_inteligente
    ADD CONSTRAINT fk_solicitud_inteligente_02 FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: solicitud_inteligente fk_solicitud_inteligente_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.solicitud_inteligente
    ADD CONSTRAINT fk_solicitud_inteligente_03 FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE SET NULL;


--
-- Name: solicitud_inteligente fk_solicitud_inteligente_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.solicitud_inteligente
    ADD CONSTRAINT fk_solicitud_inteligente_04 FOREIGN KEY (id_archivo_origen) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL;


--
-- Name: usuario_rol fk_usuario_rol_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.usuario_rol
    ADD CONSTRAINT fk_usuario_rol_01 FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT;


--
-- Name: usuario_rol fk_usuario_rol_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.usuario_rol
    ADD CONSTRAINT fk_usuario_rol_02 FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE RESTRICT;


--
-- Name: usuario_rol fk_usuario_rol_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.usuario_rol
    ADD CONSTRAINT fk_usuario_rol_03 FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: validacion_backend fk_validacion_backend_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_backend
    ADD CONSTRAINT fk_validacion_backend_01 FOREIGN KEY (id_generacion) REFERENCES uml_colaborativo.generacion_backend(id) ON DELETE CASCADE;


--
-- Name: validacion_backend fk_validacion_backend_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_backend
    ADD CONSTRAINT fk_validacion_backend_02 FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: validacion_backend fk_validacion_backend_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_backend
    ADD CONSTRAINT fk_validacion_backend_03 FOREIGN KEY (id_archivo_informe) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL;


--
-- Name: validacion_modelo fk_validacion_modelo_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_modelo
    ADD CONSTRAINT fk_validacion_modelo_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: validacion_modelo fk_validacion_modelo_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_modelo
    ADD CONSTRAINT fk_validacion_modelo_02 FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL;


--
-- Name: validacion_modelo fk_validacion_modelo_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_modelo
    ADD CONSTRAINT fk_validacion_modelo_03 FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: validacion_modelo fk_validacion_modelo_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.validacion_modelo
    ADD CONSTRAINT fk_validacion_modelo_04 FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo);


--
-- Name: version_modelo_uml fk_version_modelo_uml_01; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT fk_version_modelo_uml_01 FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE;


--
-- Name: version_modelo_uml fk_version_modelo_uml_02; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT fk_version_modelo_uml_02 FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL;


--
-- Name: version_modelo_uml fk_version_modelo_uml_03; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT fk_version_modelo_uml_03 FOREIGN KEY (id_version_padre, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: version_modelo_uml fk_version_modelo_uml_04; Type: FK CONSTRAINT; Schema: uml_colaborativo; Owner: -
--

ALTER TABLE ONLY uml_colaborativo.version_modelo_uml
    ADD CONSTRAINT fk_version_modelo_uml_04 FOREIGN KEY (id_version_restaurada, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: SCHEMA uml_colaborativo; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA uml_colaborativo TO uml_app;


--
-- Name: FUNCTION actualizar_fecha_modificacion(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.actualizar_fecha_modificacion() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.actualizar_fecha_modificacion() TO uml_app;


--
-- Name: FUNCTION actualizar_fecha_y_version_fila(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.actualizar_fecha_y_version_fila() TO uml_app;


--
-- Name: FUNCTION comprobar_firmas_operaciones(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.comprobar_firmas_operaciones() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.comprobar_firmas_operaciones() TO uml_app;


--
-- Name: FUNCTION comprobar_propietario_proyecto(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.comprobar_propietario_proyecto() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.comprobar_propietario_proyecto() TO uml_app;


--
-- Name: FUNCTION comprobar_proyectos_propietario(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.comprobar_proyectos_propietario() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.comprobar_proyectos_propietario() TO uml_app;


--
-- Name: FUNCTION construir_instantanea_modelo(p_id_modelo uuid); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.construir_instantanea_modelo(p_id_modelo uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.construir_instantanea_modelo(p_id_modelo uuid) TO uml_app;


--
-- Name: FUNCTION crear_proyecto_con_propietario(p_id_usuario_propietario uuid, p_codigo_proyecto text, p_nombre_proyecto text, p_descripcion_proyecto text, p_nombre_modelo_inicial text); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.crear_proyecto_con_propietario(p_id_usuario_propietario uuid, p_codigo_proyecto text, p_nombre_proyecto text, p_descripcion_proyecto text, p_nombre_modelo_inicial text) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.crear_proyecto_con_propietario(p_id_usuario_propietario uuid, p_codigo_proyecto text, p_nombre_proyecto text, p_descripcion_proyecto text, p_nombre_modelo_inicial text) TO uml_app;


--
-- Name: FUNCTION guardar_version_modelo(p_id_modelo uuid, p_revision_reservada bigint, p_actor uuid, p_resumen text); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.guardar_version_modelo(p_id_modelo uuid, p_revision_reservada bigint, p_actor uuid, p_resumen text) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.guardar_version_modelo(p_id_modelo uuid, p_revision_reservada bigint, p_actor uuid, p_resumen text) TO uml_app;


--
-- Name: FUNCTION impedir_cambio_identidad(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.impedir_cambio_identidad() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.impedir_cambio_identidad() TO uml_app;


--
-- Name: FUNCTION impedir_ciclo_generalizacion(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.impedir_ciclo_generalizacion() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.impedir_ciclo_generalizacion() TO uml_app;


--
-- Name: FUNCTION impedir_ciclo_paquetes(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.impedir_ciclo_paquetes() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.impedir_ciclo_paquetes() TO uml_app;


--
-- Name: FUNCTION iniciar_edicion_modelo(p_id_modelo uuid, p_revision_esperada bigint, p_actor uuid); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.iniciar_edicion_modelo(p_id_modelo uuid, p_revision_esperada bigint, p_actor uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.iniciar_edicion_modelo(p_id_modelo uuid, p_revision_esperada bigint, p_actor uuid) TO uml_app;


--
-- Name: FUNCTION proteger_ambito_permiso_excepcional(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_ambito_permiso_excepcional() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_ambito_permiso_excepcional() TO uml_app;


--
-- Name: FUNCTION proteger_ambito_rol(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_ambito_rol() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_ambito_rol() TO uml_app;


--
-- Name: FUNCTION proteger_configuracion_utilizada(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_configuracion_utilizada() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_configuracion_utilizada() TO uml_app;


--
-- Name: FUNCTION proteger_detalles_propuesta(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_detalles_propuesta() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_detalles_propuesta() TO uml_app;


--
-- Name: FUNCTION proteger_estado_propuesta(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_estado_propuesta() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_estado_propuesta() TO uml_app;


--
-- Name: FUNCTION proteger_historial_cambio(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_historial_cambio() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_historial_cambio() TO uml_app;


--
-- Name: FUNCTION proteger_registro_inmutable(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_registro_inmutable() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_registro_inmutable() TO uml_app;


--
-- Name: FUNCTION proteger_rol_sistema(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_rol_sistema() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_rol_sistema() TO uml_app;


--
-- Name: FUNCTION proteger_version_modelo(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.proteger_version_modelo() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.proteger_version_modelo() TO uml_app;


--
-- Name: FUNCTION reservar_revision_modelo(p_id_modelo uuid, p_revision_esperada bigint); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.reservar_revision_modelo(p_id_modelo uuid, p_revision_esperada bigint) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.reservar_revision_modelo(p_id_modelo uuid, p_revision_esperada bigint) TO uml_app;


--
-- Name: FUNCTION tiene_permiso_proyecto(p_id_usuario uuid, p_id_proyecto uuid, p_permiso text); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.tiene_permiso_proyecto(p_id_usuario uuid, p_id_proyecto uuid, p_permiso text) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.tiene_permiso_proyecto(p_id_usuario uuid, p_id_proyecto uuid, p_permiso text) TO uml_app;


--
-- Name: FUNCTION verificar_ambito_archivo(p_archivo uuid, p_proyecto uuid, p_categoria text); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_ambito_archivo(p_archivo uuid, p_proyecto uuid, p_categoria text) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_ambito_archivo(p_archivo uuid, p_proyecto uuid, p_categoria text) TO uml_app;


--
-- Name: FUNCTION verificar_archivos_operacion(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_archivos_operacion() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_archivos_operacion() TO uml_app;


--
-- Name: FUNCTION verificar_clase_configuracion_modelo(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_clase_configuracion_modelo() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_clase_configuracion_modelo() TO uml_app;


--
-- Name: FUNCTION verificar_clases_relacion_modelo(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_clases_relacion_modelo() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_clases_relacion_modelo() TO uml_app;


--
-- Name: FUNCTION verificar_configuracion_generacion(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_configuracion_generacion() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_configuracion_generacion() TO uml_app;


--
-- Name: FUNCTION verificar_dependencia_propuesta(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_dependencia_propuesta() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_dependencia_propuesta() TO uml_app;


--
-- Name: FUNCTION verificar_descarga_artefacto(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_descarga_artefacto() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_descarga_artefacto() TO uml_app;


--
-- Name: FUNCTION verificar_jerarquia_paquete_modelo(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_jerarquia_paquete_modelo() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_jerarquia_paquete_modelo() TO uml_app;


--
-- Name: FUNCTION verificar_mapeo_instantanea(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_mapeo_instantanea() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_mapeo_instantanea() TO uml_app;


--
-- Name: FUNCTION verificar_paquete_clase_modelo(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_paquete_clase_modelo() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_paquete_clase_modelo() TO uml_app;


--
-- Name: FUNCTION verificar_propietario_proyecto(p_id_proyecto uuid); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_propietario_proyecto(p_id_proyecto uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_propietario_proyecto(p_id_proyecto uuid) TO uml_app;


--
-- Name: FUNCTION verificar_version_actual_modelo(); Type: ACL; Schema: uml_colaborativo; Owner: -
--

REVOKE ALL ON FUNCTION uml_colaborativo.verificar_version_actual_modelo() FROM PUBLIC;
GRANT ALL ON FUNCTION uml_colaborativo.verificar_version_actual_modelo() TO uml_app;


--
-- Name: TABLE actividad_proyecto; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT ON TABLE uml_colaborativo.actividad_proyecto TO uml_app;


--
-- Name: SEQUENCE actividad_proyecto_id_seq; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE uml_colaborativo.actividad_proyecto_id_seq TO uml_app;


--
-- Name: TABLE ambiguedad_propuesta; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.ambiguedad_propuesta TO uml_app;


--
-- Name: TABLE archivo_almacenado; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.archivo_almacenado TO uml_app;


--
-- Name: TABLE atributo_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.atributo_uml TO uml_app;


--
-- Name: TABLE cambio_modelo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.cambio_modelo TO uml_app;


--
-- Name: TABLE clase_configuracion_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE uml_colaborativo.clase_configuracion_backend TO uml_app;


--
-- Name: TABLE clase_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.clase_uml TO uml_app;


--
-- Name: TABLE comprobacion_servicio; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.comprobacion_servicio TO uml_app;


--
-- Name: TABLE configuracion_generacion_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.configuracion_generacion_backend TO uml_app;


--
-- Name: TABLE conflicto_edicion; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.conflicto_edicion TO uml_app;


--
-- Name: TABLE dependencia_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE uml_colaborativo.dependencia_backend TO uml_app;


--
-- Name: TABLE dependencia_operacion_propuesta; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.dependencia_operacion_propuesta TO uml_app;


--
-- Name: TABLE descarga_artefacto; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT ON TABLE uml_colaborativo.descarga_artefacto TO uml_app;


--
-- Name: TABLE generacion_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.generacion_backend TO uml_app;


--
-- Name: TABLE hallazgo_validacion_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.hallazgo_validacion_backend TO uml_app;


--
-- Name: TABLE hallazgo_validacion_modelo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.hallazgo_validacion_modelo TO uml_app;


--
-- Name: TABLE intercambio_xmi; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.intercambio_xmi TO uml_app;


--
-- Name: TABLE mapeo_persistencia_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE uml_colaborativo.mapeo_persistencia_backend TO uml_app;


--
-- Name: TABLE mapeo_tipo_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE uml_colaborativo.mapeo_tipo_backend TO uml_app;


--
-- Name: TABLE miembro_proyecto; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.miembro_proyecto TO uml_app;


--
-- Name: TABLE modelo_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.modelo_uml TO uml_app;


--
-- Name: TABLE operacion_propuesta; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.operacion_propuesta TO uml_app;


--
-- Name: TABLE operacion_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.operacion_uml TO uml_app;


--
-- Name: TABLE paquete_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.paquete_uml TO uml_app;


--
-- Name: TABLE parametro_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.parametro_uml TO uml_app;


--
-- Name: TABLE participante_sesion_modelo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE uml_colaborativo.participante_sesion_modelo TO uml_app;


--
-- Name: TABLE permiso; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.permiso TO uml_app;


--
-- Name: TABLE permiso_miembro_proyecto; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.permiso_miembro_proyecto TO uml_app;


--
-- Name: TABLE posicion_clase_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.posicion_clase_uml TO uml_app;


--
-- Name: TABLE posicion_relacion_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.posicion_relacion_uml TO uml_app;


--
-- Name: TABLE preferencia_diagrama; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE uml_colaborativo.preferencia_diagrama TO uml_app;


--
-- Name: TABLE propuesta_inteligente; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.propuesta_inteligente TO uml_app;


--
-- Name: TABLE proyecto; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.proyecto TO uml_app;


--
-- Name: TABLE regla_validacion; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.regla_validacion TO uml_app;


--
-- Name: TABLE relacion_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.relacion_uml TO uml_app;


--
-- Name: TABLE rol; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.rol TO uml_app;


--
-- Name: TABLE rol_miembro_proyecto; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.rol_miembro_proyecto TO uml_app;


--
-- Name: TABLE rol_permiso; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.rol_permiso TO uml_app;


--
-- Name: TABLE secreto_servicio; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.secreto_servicio TO uml_app;


--
-- Name: TABLE servicio_plataforma; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.servicio_plataforma TO uml_app;


--
-- Name: TABLE sesion_autenticacion; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE uml_colaborativo.sesion_autenticacion TO uml_app;


--
-- Name: TABLE sesion_edicion_modelo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.sesion_edicion_modelo TO uml_app;


--
-- Name: TABLE solicitud_inteligente; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.solicitud_inteligente TO uml_app;


--
-- Name: TABLE usuario; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.usuario TO uml_app;


--
-- Name: TABLE usuario_rol; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.usuario_rol TO uml_app;


--
-- Name: TABLE v_contexto_propuesta_inteligente; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT ON TABLE uml_colaborativo.v_contexto_propuesta_inteligente TO uml_app;


--
-- Name: TABLE v_firma_operacion; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT ON TABLE uml_colaborativo.v_firma_operacion TO uml_app;


--
-- Name: TABLE v_miembro_proyecto_activo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT ON TABLE uml_colaborativo.v_miembro_proyecto_activo TO uml_app;


--
-- Name: TABLE v_permiso_efectivo_miembro; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT ON TABLE uml_colaborativo.v_permiso_efectivo_miembro TO uml_app;


--
-- Name: TABLE validacion_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.validacion_backend TO uml_app;


--
-- Name: TABLE version_modelo_uml; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT ON TABLE uml_colaborativo.version_modelo_uml TO uml_app;


--
-- Name: COLUMN version_modelo_uml.estado_validacion; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT UPDATE(estado_validacion) ON TABLE uml_colaborativo.version_modelo_uml TO uml_app;


--
-- Name: TABLE v_resumen_generacion_backend; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT ON TABLE uml_colaborativo.v_resumen_generacion_backend TO uml_app;


--
-- Name: TABLE v_resumen_modelo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT ON TABLE uml_colaborativo.v_resumen_modelo TO uml_app;


--
-- Name: TABLE v_semantica_atributo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT ON TABLE uml_colaborativo.v_semantica_atributo TO uml_app;


--
-- Name: TABLE validacion_modelo; Type: ACL; Schema: uml_colaborativo; Owner: -
--

GRANT SELECT,INSERT,UPDATE ON TABLE uml_colaborativo.validacion_modelo TO uml_app;


--
-- PostgreSQL database dump complete
--

SET check_function_bodies = true;
SET search_path TO uml_colaborativo, public;

-- Fuente: ../database/2026-09-12_17_catalogos_espanol.sql
--
-- PostgreSQL database dump
--

-- Catalogos en espanol: sin usuarios humanos ni credenciales.

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: permiso; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('33f4a471-f304-4815-bd0c-c37a6d688023', 'CREAR_PROYECTOS', 'PROYECTO', 'CREAR', 'Crear proyectos.', 'GLOBAL', '2026-09-13 02:57:33.226147+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('8eabf7ab-ecfb-4a74-8481-f281d0c0a2c5', 'GESTIONAR_USUARIOS', 'ACCESO', 'GESTIONAR_USUARIOS', 'Gestionar cuentas y estados de usuario.', 'GLOBAL', '2026-09-13 02:57:33.225693+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('6390272c-3f19-4574-b2e4-714b4c5584e3', 'GESTIONAR_ROLES', 'ACCESO', 'GESTIONAR_ROLES', 'Gestionar roles y permisos.', 'GLOBAL', '2026-09-13 02:57:33.226015+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('f36f27be-9fe8-49c3-9173-7e1883f3d86a', 'CONFIGURAR_SERVICIOS', 'ACCESO', 'CONFIGURAR_SERVICIOS', 'Configurar proveedores de IA, voz e imagen.', 'GLOBAL', '2026-09-13 02:57:33.226094+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('f4bd3641-0a95-40d8-b843-356bebf92f87', 'DESCARGAR_ARTEFACTO', 'GENERACION', 'DESCARGAR_ARTEFACTO', 'Descargar artefactos generados y validados.', 'PROYECTO', '2026-09-13 02:57:33.227019+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('1c1dde76-9f39-4ecf-b58c-aa731d344ba7', 'VALIDAR_BACKEND', 'GENERACION', 'VALIDAR_BACKEND', 'Compilar y validar un backend generado.', 'PROYECTO', '2026-09-13 02:57:33.226982+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('bda3882b-5898-4764-8cc3-09dd6eac8c35', 'GENERAR_BACKEND', 'GENERACION', 'GENERAR_BACKEND', 'Generar un backend Spring Boot.', 'PROYECTO', '2026-09-13 02:57:33.226945+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('6e92e7cf-c52a-4062-a9df-0deca27b5c10', 'CONFIGURAR_BACKEND', 'GENERACION', 'CONFIGURAR_BACKEND', 'Configurar la transformacion a Spring Boot.', 'PROYECTO', '2026-09-13 02:57:33.226906+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('86892fc9-0a22-49e5-bbf9-77162be1d727', 'EXPORTAR_XMI', 'INTEROPERABILIDAD', 'EXPORTAR_XMI', 'Exportar modelos XMI.', 'PROYECTO', '2026-09-13 02:57:33.226867+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('1440d453-1d12-4c5f-b4c3-9c55647b2c98', 'IMPORTAR_XMI', 'INTEROPERABILIDAD', 'IMPORTAR_XMI', 'Importar modelos XMI.', 'PROYECTO', '2026-09-13 02:57:33.226831+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', 'REVISAR_PROPUESTA', 'INTELIGENCIA', 'REVISAR_PROPUESTA', 'Revisar, corregir, aceptar o rechazar propuestas.', 'PROYECTO', '2026-09-13 02:57:33.226792+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('719827db-36f1-4ce4-bfae-60d8d53b642a', 'ASISTIR_IMAGEN', 'INTELIGENCIA', 'USAR_IMAGEN', 'Crear propuestas desde imagen.', 'PROYECTO', '2026-09-13 02:57:33.226749+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('2c693e04-04b3-45f0-8c7c-132c43b28b6d', 'ASISTIR_VOZ', 'INTELIGENCIA', 'USAR_VOZ', 'Crear propuestas desde voz.', 'PROYECTO', '2026-09-13 02:57:33.226712+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('93210a03-db2d-48f8-ac6b-132a6a47ba57', 'ASISTIR_TEXTO', 'INTELIGENCIA', 'USAR_TEXTO', 'Crear propuestas desde texto.', 'PROYECTO', '2026-09-13 02:57:33.226675+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', 'RESOLVER_CONFLICTO', 'COLABORACION', 'RESOLVER_CONFLICTO', 'Resolver conflictos de edicion.', 'PROYECTO', '2026-09-13 02:57:33.226638+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('56f5ee6a-5bc5-4077-af6a-3432406c62cd', 'COLABORAR', 'COLABORACION', 'EDITAR_EN_VIVO', 'Participar en edicion simultanea.', 'PROYECTO', '2026-09-13 02:57:33.2266+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('fa4009bb-f5df-44b2-ac92-28d99041592e', 'RESTAURAR_MODELO', 'CALIDAD', 'RESTAURAR_VERSION', 'Restaurar una version como una version nueva.', 'PROYECTO', '2026-09-13 02:57:33.226563+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('09f257c4-d7eb-457c-a48a-70432dc88d2b', 'VER_HISTORIAL_MODELO', 'CALIDAD', 'VER_HISTORIAL', 'Consultar y comparar versiones.', 'PROYECTO', '2026-09-13 02:57:33.226525+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('76c10f64-773c-476e-8dc7-fa2440e57de9', 'VALIDAR_MODELO', 'CALIDAD', 'VALIDAR_MODELO', 'Ejecutar reglas de validacion UML.', 'PROYECTO', '2026-09-13 02:57:33.226486+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('524d9497-9375-4b49-b37b-af8493d0a41a', 'ORGANIZAR_MODELO', 'MODELO', 'ORGANIZAR', 'Organizar visualmente el diagrama.', 'PROYECTO', '2026-09-13 02:57:33.226449+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('f08ba837-62dd-4ccd-b867-9d15b8f2f37b', 'VER_ACTIVIDAD', 'COLABORACION', 'VER_ACTIVIDAD', 'Consultar la actividad del proyecto.', 'PROYECTO', '2026-09-13 02:57:33.226337+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('fa394fae-0b5b-4ce6-90cd-9b622d8ef52d', 'ASIGNAR_ROLES_PROYECTO', 'PROYECTO', 'ASIGNAR_ROLES', 'Asignar roles y permisos en un proyecto.', 'PROYECTO', '2026-09-13 02:57:33.226299+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('3c716b10-92c8-4e20-9351-713963e06fa8', 'GESTIONAR_MIEMBROS', 'PROYECTO', 'GESTIONAR_MIEMBROS', 'Incorporar, suspender y retirar miembros.', 'PROYECTO', '2026-09-13 02:57:33.226261+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('e201365e-0455-444a-ad76-7b58f663781a', 'GESTIONAR_PROYECTOS', 'PROYECTO', 'GESTIONAR', 'Modificar, archivar y reactivar un proyecto.', 'PROYECTO', '2026-09-13 02:57:33.226224+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', 'VER_MODELO', 'MODELO', 'VER', 'Abrir y consultar el modelo UML.', 'PROYECTO', '2026-09-13 02:57:33.226374+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', 'EDITAR_MODELO', 'MODELO', 'EDICION', 'Crear y modificar elementos UML.', 'PROYECTO', '2026-09-13 02:57:33.226414+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('0e1c5568-4f6a-428d-9825-1fa764dc9c0e', 'VER_PROYECTOS', 'PROYECTO', 'VER', 'Consultar proyectos autorizados.', 'AMBOS', '2026-09-13 02:57:33.226187+00');


--
-- Data for Name: regla_validacion; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('0611caf1-668b-461f-bffc-6571926478fa', 'UML_FIRMA_OPERACION_UNICA', 'Firma de operacion unica', 'No se admiten firmas duplicadas dentro de una clase.', 'ERROR', 'UML_OPERATION', true, 'operacion.firma.unica', '2026-09-13 02:57:33.241395+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('7e861453-d8a1-4781-810e-c6b8360ae9c6', 'UML_IDENTIFICADOR_PERSISTENTE_OBLIGATORIO', 'Identificador persistente obligatorio', 'Toda clase persistente debe declarar al menos un atributo identificador.', 'ERROR', 'CLASE_UML', true, 'persistente.id.obligatorio', '2026-09-13 02:57:33.24127+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('b8a2875a-ac3b-4052-b734-2d8202d315fa', 'UML_NOMBRE_CLASE_UNICO', 'Nombre de clase unico', 'No puede haber dos clases activas con el mismo nombre dentro del paquete.', 'ERROR', 'CLASE_UML', true, 'clase.nombre.unico', '2026-09-13 02:57:33.2412+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('b01619c3-e252-4681-92f2-806ea0af6c96', 'UML_NOMBRE_CLASE_OBLIGATORIO', 'Nombre de clase obligatorio', 'Toda clase activa debe tener un nombre valido.', 'ERROR', 'CLASE_UML', true, 'clase.nombre.obligatorio', '2026-09-13 02:57:33.24098+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('b17ce393-9e48-4645-ac90-31df06059f7d', 'UML_TIPO_ATRIBUTO_OBLIGATORIO', 'Tipo de atributo obligatorio', 'Todo atributo debe declarar un tipo UML.', 'ERROR', 'ATRIBUTO_UML', true, 'atributo.tipo.obligatorio', '2026-09-13 02:57:33.241238+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('824f137d-0bbd-4996-a103-fb7788949a1d', 'UML_CARACTERISTICA_XMI_NO_COMPATIBLE', 'Caracteristica XMI no compatible', 'Una propiedad importada o exportada no se representa completamente.', 'ADVERTENCIA', 'MODELO_UML', true, 'xmi.caracteristica.no_compatible', '2026-09-13 02:57:33.241426+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('50e08559-1760-4e87-8815-f93c3411f346', 'UML_GENERALIZACION_SIN_CICLOS', 'Generalizacion sin ciclos', 'La jerarquia de generalizacion no puede contener ciclos.', 'ERROR', 'RELACION_UML', true, 'generalizacion.sin_ciclos', '2026-09-13 02:57:33.241364+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('c9dd0a6c-c7f1-4e46-b065-72ac35275b35', 'UML_CARDINALIDAD_VALIDA', 'Cardinalidad valida', 'Los minimos y maximos de una relacion deben ser coherentes.', 'ERROR', 'RELACION_UML', true, 'relacion.cardinalidad.valida', '2026-09-13 02:57:33.241333+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('06900322-d006-4bb7-a101-64945f432564', 'UML_EXTREMOS_RELACION_VALIDOS', 'Extremos de relacion validos', 'Los extremos deben existir y pertenecer al mismo modelo.', 'ERROR', 'RELACION_UML', true, 'relacion.extremos.validos', '2026-09-13 02:57:33.2413+00');


--
-- Data for Name: rol; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'PROPIETARIO_PROYECTO', 'Propietario de proyecto', 'Administra el proyecto, sus miembros y sus artefactos.', 'PROYECTO', true, 'ACTIVO', '2026-09-13 02:57:33.223019+00', '2026-09-13 02:57:33.223019+00');
INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', 'EDITOR_PROYECTO', 'Ingeniero editor', 'Edita, valida e intercambia modelos autorizados.', 'PROYECTO', true, 'ACTIVO', '2026-09-13 02:57:33.223076+00', '2026-09-13 02:57:33.223076+00');
INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', 'LECTOR_PROYECTO', 'Ingeniero visualizador', 'Consulta el proyecto y su historial sin modificarlo.', 'PROYECTO', true, 'ACTIVO', '2026-09-13 02:57:33.223125+00', '2026-09-13 02:57:33.223125+00');
INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'SUPERADMINISTRADOR', 'Superadministrador', 'Administracion global de la plataforma.', 'GLOBAL', true, 'ACTIVO', '2026-09-13 02:57:33.222131+00', '2026-09-13 02:57:33.222131+00');


--
-- Data for Name: rol_permiso; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '8eabf7ab-ecfb-4a74-8481-f281d0c0a2c5', '2026-09-13 02:57:33.228935+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '6390272c-3f19-4574-b2e4-714b4c5584e3', '2026-09-13 02:57:33.229209+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'f36f27be-9fe8-49c3-9173-7e1883f3d86a', '2026-09-13 02:57:33.229244+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '33f4a471-f304-4815-bd0c-c37a6d688023', '2026-09-13 02:57:33.229269+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.229292+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'e201365e-0455-444a-ad76-7b58f663781a', '2026-09-13 02:57:33.229315+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '3c716b10-92c8-4e20-9351-713963e06fa8', '2026-09-13 02:57:33.229337+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'fa394fae-0b5b-4ce6-90cd-9b622d8ef52d', '2026-09-13 02:57:33.229362+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.229383+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.229404+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', '2026-09-13 02:57:33.229426+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '524d9497-9375-4b49-b37b-af8493d0a41a', '2026-09-13 02:57:33.229448+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '76c10f64-773c-476e-8dc7-fa2440e57de9', '2026-09-13 02:57:33.229469+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.229551+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'fa4009bb-f5df-44b2-ac92-28d99041592e', '2026-09-13 02:57:33.229582+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '56f5ee6a-5bc5-4077-af6a-3432406c62cd', '2026-09-13 02:57:33.229606+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', '2026-09-13 02:57:33.229629+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '93210a03-db2d-48f8-ac6b-132a6a47ba57', '2026-09-13 02:57:33.229652+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '2c693e04-04b3-45f0-8c7c-132c43b28b6d', '2026-09-13 02:57:33.229673+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '719827db-36f1-4ce4-bfae-60d8d53b642a', '2026-09-13 02:57:33.229696+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', '2026-09-13 02:57:33.229721+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '1440d453-1d12-4c5f-b4c3-9c55647b2c98', '2026-09-13 02:57:33.229743+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '86892fc9-0a22-49e5-bbf9-77162be1d727', '2026-09-13 02:57:33.229765+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '6e92e7cf-c52a-4062-a9df-0deca27b5c10', '2026-09-13 02:57:33.229789+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'bda3882b-5898-4764-8cc3-09dd6eac8c35', '2026-09-13 02:57:33.22981+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '1c1dde76-9f39-4ecf-b58c-aa731d344ba7', '2026-09-13 02:57:33.229832+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'f4bd3641-0a95-40d8-b843-356bebf92f87', '2026-09-13 02:57:33.229856+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.233869+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'e201365e-0455-444a-ad76-7b58f663781a', '2026-09-13 02:57:33.233917+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '3c716b10-92c8-4e20-9351-713963e06fa8', '2026-09-13 02:57:33.233945+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'fa394fae-0b5b-4ce6-90cd-9b622d8ef52d', '2026-09-13 02:57:33.233977+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.234004+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.234028+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', '2026-09-13 02:57:33.234053+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '524d9497-9375-4b49-b37b-af8493d0a41a', '2026-09-13 02:57:33.234078+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '76c10f64-773c-476e-8dc7-fa2440e57de9', '2026-09-13 02:57:33.234105+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.234131+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'fa4009bb-f5df-44b2-ac92-28d99041592e', '2026-09-13 02:57:33.234155+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '56f5ee6a-5bc5-4077-af6a-3432406c62cd', '2026-09-13 02:57:33.234179+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', '2026-09-13 02:57:33.234203+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '93210a03-db2d-48f8-ac6b-132a6a47ba57', '2026-09-13 02:57:33.234227+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '2c693e04-04b3-45f0-8c7c-132c43b28b6d', '2026-09-13 02:57:33.234251+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '719827db-36f1-4ce4-bfae-60d8d53b642a', '2026-09-13 02:57:33.234276+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', '2026-09-13 02:57:33.2343+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '1440d453-1d12-4c5f-b4c3-9c55647b2c98', '2026-09-13 02:57:33.234325+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '86892fc9-0a22-49e5-bbf9-77162be1d727', '2026-09-13 02:57:33.234351+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '6e92e7cf-c52a-4062-a9df-0deca27b5c10', '2026-09-13 02:57:33.234376+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'bda3882b-5898-4764-8cc3-09dd6eac8c35', '2026-09-13 02:57:33.2344+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '1c1dde76-9f39-4ecf-b58c-aa731d344ba7', '2026-09-13 02:57:33.234428+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'f4bd3641-0a95-40d8-b843-356bebf92f87', '2026-09-13 02:57:33.234454+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.236634+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.236682+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.236708+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', '2026-09-13 02:57:33.236734+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '524d9497-9375-4b49-b37b-af8493d0a41a', '2026-09-13 02:57:33.236756+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '76c10f64-773c-476e-8dc7-fa2440e57de9', '2026-09-13 02:57:33.236779+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.236802+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '56f5ee6a-5bc5-4077-af6a-3432406c62cd', '2026-09-13 02:57:33.236826+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', '2026-09-13 02:57:33.236852+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '93210a03-db2d-48f8-ac6b-132a6a47ba57', '2026-09-13 02:57:33.236874+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '2c693e04-04b3-45f0-8c7c-132c43b28b6d', '2026-09-13 02:57:33.236896+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '719827db-36f1-4ce4-bfae-60d8d53b642a', '2026-09-13 02:57:33.236919+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', '2026-09-13 02:57:33.236941+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '1440d453-1d12-4c5f-b4c3-9c55647b2c98', '2026-09-13 02:57:33.236963+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '86892fc9-0a22-49e5-bbf9-77162be1d727', '2026-09-13 02:57:33.236985+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.238932+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.238982+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.239007+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.239037+00');


--
-- PostgreSQL database dump complete
--

SET search_path TO uml_colaborativo, public;

-- Fuente: ../database/2026-09-13_18_correcciones_desarrollo.sql
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

-- Fuente: ../core/docs/migraciones/2026-09-14_01_revocacion_permisos_rol.sql
-- Incremento de administración: la matriz rol_permiso no tiene borrado lógico.
-- Permite revocar asociaciones exclusivamente en esa tabla, después de que
-- el servicio autentique al actor, proteja al superadministrador y audite.
-- No cambia tablas, columnas, FK ni datos de dominio. Ejecutar como propietario.
-- Reversión del privilegio: REVOKE DELETE ON uml_colaborativo.rol_permiso FROM uml_app;
BEGIN;
GRANT DELETE ON TABLE uml_colaborativo.rol_permiso TO uml_app;
COMMIT;

-- Fuente: ../core/docs/migraciones/2026-09-15_02_resolucion_conflictos.sql
-- Incremento 05: trazabilidad e idempotencia de la decisión humana sobre conflictos.
-- La tabla ya conserva ambos valores y los cambios causales. Estas columnas enlazan
-- la solicitud REST y, cuando existe, el cambio confirmado por la resolución.
-- Ejecutar como propietario del esquema. No contiene datos ni modifica revisiones.
BEGIN;

ALTER TABLE uml_colaborativo.conflicto_edicion
    ADD COLUMN IF NOT EXISTS id_solicitud_resolucion uuid,
    ADD COLUMN IF NOT EXISTS id_cambio_resolucion uuid;

ALTER TABLE uml_colaborativo.conflicto_edicion
    DROP CONSTRAINT IF EXISTS ck_conflicto_edicion_04,
    DROP CONSTRAINT IF EXISTS ck_conflicto_edicion_05,
    DROP CONSTRAINT IF EXISTS ck_conflicto_edicion_06;

ALTER TABLE uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT ck_conflicto_edicion_04 CHECK (
        (estado = 'ABIERTO' AND resuelto_en IS NULL)
        OR (estado IN ('RESUELTO', 'DESCARTADO') AND resuelto_en IS NOT NULL)
    ),
    ADD CONSTRAINT ck_conflicto_edicion_05 CHECK (
        id_solicitud_resolucion IS NULL
        OR (estado IN ('RESUELTO', 'DESCARTADO') AND resuelto_por IS NOT NULL)
    ),
    ADD CONSTRAINT ck_conflicto_edicion_06 CHECK (
        id_cambio_resolucion IS NULL
        OR (estado = 'RESUELTO' AND tipo_resolucion IN ('APLICAR_ENTRANTE', 'FUSIONAR'))
    );

DO $migracion$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_conflicto_edicion_07'
          AND conrelid = 'uml_colaborativo.conflicto_edicion'::regclass
    ) THEN
        ALTER TABLE uml_colaborativo.conflicto_edicion
            ADD CONSTRAINT fk_conflicto_edicion_07
            FOREIGN KEY (id_cambio_resolucion, id_modelo)
            REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo);
    END IF;
END
$migracion$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_conflicto_edicion_01
    ON uml_colaborativo.conflicto_edicion(id_solicitud_resolucion)
    WHERE id_solicitud_resolucion IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_conflicto_edicion_02
    ON uml_colaborativo.conflicto_edicion(id_cambio_entrante)
    WHERE id_cambio_entrante IS NOT NULL AND estado = 'ABIERTO';

COMMIT;

-- Fuente: ../core/docs/migraciones/2026-09-15_03_historial_permisos.sql
-- Incremento 06: permisos para historial/restauración (CU19/CU20) y función auxiliar.
-- Reejecutable: ignora duplicados con ON CONFLICT DO NOTHING; función usa CREATE OR REPLACE.
-- Ejecutar como propietario del esquema con la aplicación detenida.
-- Reversión:
--   DROP FUNCTION IF EXISTS uml_colaborativo.guardar_version_modelo_restaurada(uuid,bigint,uuid,text,uuid);
--   DELETE FROM uml_colaborativo.rol_permiso
--     WHERE id_permiso IN (
--       SELECT id FROM uml_colaborativo.permiso
--       WHERE codigo IN ('VER_HISTORIAL_MODELO','RESTAURAR_MODELO'));
--   DELETE FROM uml_colaborativo.permiso
--     WHERE codigo IN ('VER_HISTORIAL_MODELO','RESTAURAR_MODELO');
BEGIN;

-- 1. Permisos de dominio
INSERT INTO uml_colaborativo.permiso (codigo, modulo, accion, descripcion, ambito)
VALUES
    ('VER_HISTORIAL_MODELO', 'MODELADO', 'VER_HISTORIAL', 'Listar y consultar versiones del historial del modelo', 'PROYECTO'),
    ('RESTAURAR_MODELO', 'MODELADO', 'RESTAURAR', 'Restaurar el modelo a una versión anterior como nueva versión', 'PROYECTO')
ON CONFLICT (codigo) DO NOTHING;

-- Los códigos vigentes son PROPIETARIO_PROYECTO, EDITOR_PROYECTO y
-- LECTOR_PROYECTO. LIDER/COLABORADOR se conservan como alias compatibles.
-- Todos los roles del proyecto pueden consultar el historial.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE (
        r.codigo IN (
            'PROPIETARIO_PROYECTO', 'EDITOR_PROYECTO', 'LECTOR_PROYECTO',
            'LIDER', 'COLABORADOR')
        OR upper(r.nombre) IN ('LIDER', 'COLABORADOR'))
  AND p.codigo = 'VER_HISTORIAL_MODELO'
ON CONFLICT DO NOTHING;

-- Solo el propietario/líder recibe restauración por defecto.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE (
        r.codigo IN ('PROPIETARIO_PROYECTO', 'LIDER')
        OR upper(r.nombre) = 'LIDER')
  AND p.codigo = 'RESTAURAR_MODELO'
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

-- Fuente: ../core/docs/migraciones/2026-09-18_04_xmi_generacion_permisos.sql
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

-- 2. Asignación a roles predeterminados del proyecto. Los códigos vigentes
-- son PROPIETARIO_PROYECTO y EDITOR_PROYECTO; LIDER/COLABORADOR se aceptan
-- como alias para instalaciones anteriores.
-- Propietario/LIDER: acceso completo a interoperabilidad y generación.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE (
        r.codigo IN ('PROPIETARIO_PROYECTO', 'LIDER')
        OR upper(r.nombre) = 'LIDER')
  AND p.codigo IN ('EXPORTAR_XMI', 'IMPORTAR_XMI', 'CONFIGURAR_BACKEND',
                   'GENERAR_BACKEND', 'DESCARGAR_ARTEFACTO')
ON CONFLICT DO NOTHING;

-- Editor/COLABORADOR: intercambio XMI y descarga. Los permisos de configuración
-- y generación no se amplían sin una decisión académica explícita.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r, uml_colaborativo.permiso p
WHERE (
        r.codigo IN ('EDITOR_PROYECTO', 'COLABORADOR')
        OR upper(r.nombre) = 'COLABORADOR')
  AND p.codigo IN ('EXPORTAR_XMI', 'IMPORTAR_XMI', 'DESCARGAR_ARTEFACTO')
ON CONFLICT DO NOTHING;

-- 3. Persistencia binaria para artefactos generados (XMI y ZIP)
ALTER TABLE uml_colaborativo.archivo_almacenado
    ADD COLUMN IF NOT EXISTS contenido bytea;

COMMIT;

-- Fuente: ../core/docs/migraciones/2026-09-19_05_asistencia_inteligente_permisos.sql
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

-- Fuente: ../core/docs/migraciones/2026-09-20_06_servicios_plataforma_seguridad.sql
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

-- Fuente: ../core/docs/migraciones/2026-09-20_07_revision_asistencia_lectura.sql
BEGIN;

-- Un lector del proyecto puede auditar una propuesta, pero no corregirla ni aplicarla.
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso)
SELECT r.id, p.id
FROM uml_colaborativo.rol r
JOIN uml_colaborativo.permiso p ON p.codigo = 'REVISAR_PROPUESTA_IA'
WHERE r.codigo IN ('LECTOR_PROYECTO', 'LECTOR')
ON CONFLICT DO NOTHING;

COMMIT;

-- Fuente: ../core/docs/migraciones/2026-09-21_08_generacion_backend_avanzada.sql
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

-- Fuente: ../core/docs/migraciones/2026-09-21_09_validacion_backend.sql
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

-- Fuente: ../core/docs/migraciones/2026-11-10_10_subconjunto_uml_251.sql
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

-- Fuente: ../core/docs/migraciones/2026-11-10_11_agente_colaborativo.sql
-- Incremental, después de 01..10. No modifica conversaciones ni versiones anteriores.
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(20261110, 11);
CREATE TABLE IF NOT EXISTS uml_colaborativo.conversacion_agente (
 id uuid PRIMARY KEY, id_modelo uuid NOT NULL UNIQUE REFERENCES uml_colaborativo.modelo_uml(id),
 creado_por uuid NOT NULL REFERENCES uml_colaborativo.usuario(id),
 creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE IF NOT EXISTS uml_colaborativo.mensaje_agente (
 id uuid PRIMARY KEY, id_conversacion uuid NOT NULL REFERENCES uml_colaborativo.conversacion_agente(id),
 secuencia bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
 autor uuid REFERENCES uml_colaborativo.usuario(id),
 emisor varchar(12) NOT NULL CHECK(emisor IN ('USUARIO','ASISTENTE')),
 texto text NOT NULL CHECK(length(texto)<=32000),
 modalidad varchar(10) NOT NULL CHECK(modalidad IN ('TEXTO','VOZ','IMAGEN')),
 clave uuid, respuesta_a uuid REFERENCES uml_colaborativo.mensaje_agente(id),
 id_archivo uuid REFERENCES uml_colaborativo.archivo_almacenado(id),
 id_propuesta uuid REFERENCES uml_colaborativo.propuesta_inteligente(id),
 tipo_resultado varchar(12) CHECK(tipo_resultado IN ('RESPUESTA','ACLARACION','PROPUESTA','ERROR')),
 fuentes jsonb NOT NULL DEFAULT '[]', supuestos jsonb NOT NULL DEFAULT '[]',
 creado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(id_conversacion,autor,clave), UNIQUE(respuesta_a)
);
CREATE INDEX IF NOT EXISTS ix_mensaje_agente_hilo ON uml_colaborativo.mensaje_agente(id_conversacion,secuencia);
ALTER TABLE uml_colaborativo.mensaje_agente ADD COLUMN IF NOT EXISTS extraccion text CHECK(extraccion IS NULL OR length(extraccion)<=60000);
CREATE TABLE IF NOT EXISTS uml_colaborativo.ejecucion_agente (
 id uuid PRIMARY KEY, id_mensaje uuid NOT NULL UNIQUE REFERENCES uml_colaborativo.mensaje_agente(id),
 estado varchar(12) NOT NULL CHECK(estado IN ('PENDIENTE','PROCESANDO','COMPLETADO','FALLIDO','CANCELADO')),
 intencion varchar(10) NOT NULL CHECK(intencion IN ('AUTO','CONSULTAR','MODIFICAR')),
 revision_base bigint, corte_contexto bigint, contexto jsonb, resultado jsonb,
 token_trabajo uuid, intentos int NOT NULL DEFAULT 0, iniciado_en timestamptz,
 completado_en timestamptz, creado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 duracion_ms bigint, modo varchar(20), error varchar(1000)
);
CREATE INDEX IF NOT EXISTS ix_ejecucion_agente_pendiente ON uml_colaborativo.ejecucion_agente(estado,creado_en);
CREATE TABLE IF NOT EXISTS uml_colaborativo.acuerdo_agente (
 id uuid PRIMARY KEY, id_conversacion uuid NOT NULL REFERENCES uml_colaborativo.conversacion_agente(id),
 id_mensaje uuid NOT NULL REFERENCES uml_colaborativo.mensaje_agente(id),
 contenido varchar(2000) NOT NULL CHECK(length(btrim(contenido))>0),
 confirmado_por uuid NOT NULL REFERENCES uml_colaborativo.usuario(id),
 sustituye uuid UNIQUE REFERENCES uml_colaborativo.acuerdo_agente(id),
 confirmado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
GRANT SELECT,INSERT,UPDATE ON uml_colaborativo.conversacion_agente,uml_colaborativo.mensaje_agente,
 uml_colaborativo.ejecucion_agente,uml_colaborativo.acuerdo_agente TO uml_app;
GRANT USAGE,SELECT ON SEQUENCE uml_colaborativo.mensaje_agente_secuencia_seq TO uml_app;
COMMIT;

-- Fuente: ../core/docs/migraciones/2026-11-13_12_generacion_independiente.sql
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

ALTER ROLE uml_app LOGIN;
