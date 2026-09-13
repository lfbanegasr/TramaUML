-- Derivado de la suite 15. El original se conserva.
-- Ajustes: bloqueo del propietario permitido; marcas temporales reales coherentes.
\set ON_ERROR_STOP on

BEGIN;
SET search_path TO uml_colaborativo, public;

DO $$
DECLARE
    test_user_id uuid;
    project_one_id uuid;
    model_one_id uuid;
    project_two_id uuid;
    model_two_id uuid;
    class_one_id uuid;
    class_two_id uuid;
    foreign_class_id uuid;
    member_id uuid;
    revision_value bigint;
    duplicate_was_blocked boolean := false;
    cross_model_was_blocked boolean := false;
    stale_revision_was_blocked boolean := false;
BEGIN
    IF to_regclass('uml_colaborativo.modelo_uml') IS NULL
       OR to_regclass('uml_colaborativo.generacion_backend') IS NULL
       OR to_regclass('uml_colaborativo.propuesta_inteligente') IS NULL THEN
        RAISE EXCEPTION 'Faltan tablas obligatorias';
    END IF;

    IF (SELECT count(*) FROM rol WHERE es_del_sistema) <> 4 THEN
        RAISE EXCEPTION 'El catalogo debe contener cuatro roles del sistema';
    END IF;

    INSERT INTO usuario (nombre_usuario, correo_electronico, nombre_completo, hash_contrasena)
    VALUES ('db_test_user', 'db-test@example.invalid', 'Usuario de prueba', 'TEST_ONLY_NOT_A_PASSWORD')
    RETURNING id INTO test_user_id;

    SELECT created.id_proyecto, created.id_modelo
    INTO project_one_id, model_one_id
    FROM crear_proyecto_con_propietario(
        test_user_id, 'DB-TEST-ONE', 'Proyecto de prueba uno', NULL, 'Modelo principal'
    ) AS created;

    SELECT id INTO member_id
    FROM miembro_proyecto pm
    WHERE pm.id_proyecto = project_one_id AND pm.id_usuario = test_user_id;

    IF member_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM rol_miembro_proyecto pmr
        JOIN rol r ON r.id = pmr.id_rol
        WHERE pmr.id_miembro_proyecto = member_id
          AND pmr.revocado_en IS NULL
          AND r.codigo = 'PROPIETARIO_PROYECTO'
    ) THEN
        RAISE EXCEPTION 'La creacion del proyecto no asigno al propietario';
    END IF;

    INSERT INTO clase_uml (id_modelo, nombre, creado_por)
    VALUES (model_one_id, 'Persona', test_user_id)
    RETURNING id INTO class_one_id;

    INSERT INTO clase_uml (id_modelo, nombre, creado_por)
    VALUES (model_one_id, 'Cita', test_user_id)
    RETURNING id INTO class_two_id;

    BEGIN
        INSERT INTO clase_uml (id_modelo, nombre, creado_por)
        VALUES (model_one_id, 'persona', test_user_id);
    EXCEPTION WHEN unique_violation THEN
        duplicate_was_blocked := true;
    END;

    IF NOT duplicate_was_blocked THEN
        RAISE EXCEPTION 'No se bloqueo una clase duplicada sin distinguir mayusculas';
    END IF;

    SELECT created.id_proyecto, created.id_modelo
    INTO project_two_id, model_two_id
    FROM crear_proyecto_con_propietario(
        test_user_id, 'DB-TEST-TWO', 'Proyecto de prueba dos', NULL, 'Otro modelo'
    ) AS created;

    INSERT INTO clase_uml (id_modelo, nombre, creado_por)
    VALUES (model_two_id, 'ClaseExterna', test_user_id)
    RETURNING id INTO foreign_class_id;

    BEGIN
        INSERT INTO relacion_uml (
            id_modelo, id_clase_origen, id_clase_destino, tipo_relacion,
            minimo_origen, maximo_origen, minimo_destino, maximo_destino, creado_por
        )
        VALUES (
            model_one_id, class_one_id, foreign_class_id, 'ASOCIACION',
            0, NULL, 1, 1, test_user_id
        );
    EXCEPTION WHEN OTHERS THEN
        cross_model_was_blocked := true;
    END;

    IF NOT cross_model_was_blocked THEN
        RAISE EXCEPTION 'No se bloqueo una relacion entre modelos diferentes';
    END IF;

    INSERT INTO relacion_uml (
        id_modelo, id_clase_origen, id_clase_destino, tipo_relacion,
        minimo_origen, maximo_origen, minimo_destino, maximo_destino, creado_por
    )
    VALUES (
        model_one_id, class_one_id, class_two_id, 'ASOCIACION',
        0, NULL, 1, 1, test_user_id
    );

    revision_value := reservar_revision_modelo(model_one_id, 0);
    IF revision_value <> 1 THEN
        RAISE EXCEPTION 'La primera revision reservada debia ser 1';
    END IF;

    BEGIN
        PERFORM reservar_revision_modelo(model_one_id, 0);
    EXCEPTION WHEN serialization_failure THEN
        stale_revision_was_blocked := true;
    END;

    IF NOT stale_revision_was_blocked THEN
        RAISE EXCEPTION 'No se detecto una escritura basada en una revision obsoleta';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM v_permiso_efectivo_miembro
        WHERE id_miembro_proyecto = member_id
          AND codigo_permiso = 'EDITAR_MODELO'
          AND esta_permitido
    ) THEN
        RAISE EXCEPTION 'La vista de permisos no reconoce MODEL_EDIT para el propietario';
    END IF;

    RAISE NOTICE 'Pruebas de integridad completadas correctamente';
END;
$$;

ROLLBACK;
\set ON_ERROR_STOP on
BEGIN;
SET search_path TO uml_colaborativo, public;
CREATE TEMP TABLE test_result(label text PRIMARY KEY, passed boolean NOT NULL) ON COMMIT DROP;
CREATE FUNCTION pg_temp.assert_true(p_label text, p_value boolean)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF p_value IS DISTINCT FROM true THEN RAISE EXCEPTION 'FALLO: %', p_label; END IF;
    INSERT INTO test_result VALUES(p_label, true);
END;
$$;
CREATE FUNCTION pg_temp.expect_error(p_label text, p_sql text, p_expected text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual_state text := '00000';
BEGIN
    BEGIN
        EXECUTE p_sql;
        SET CONSTRAINTS ALL IMMEDIATE;
    EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
    END;
    SET CONSTRAINTS ALL DEFERRED;
    IF actual_state <> p_expected THEN
        RAISE EXCEPTION 'FALLO: %, SQLSTATE esperado %, obtenido %', p_label, p_expected, actual_state;
    END IF;
    PERFORM pg_temp.assert_true(p_label, true);
END;
$$;
DO $$
DECLARE
    owner_id uuid; reader_id uuid; project_a uuid; model_a uuid; project_b uuid; model_b uuid;
    class_a uuid; class_b uuid; class_foreign uuid; owner_member uuid; reader_member uuid;
    package_a uuid; package_b uuid; version_a uuid; version_b uuid; attribute_a uuid;
    request_a uuid; request_b uuid; proposal_a uuid; proposal_b uuid; op_a uuid; op_b uuid; op_foreign uuid;
    method_a uuid; method_b uuid; config_a uuid; config_draft uuid; job_a uuid;
    asset_zip uuid; asset_wrong uuid; asset_image_foreign uuid; audit_id bigint; revision_a bigint;
BEGIN
    INSERT INTO usuario(nombre_usuario, correo_electronico, nombre_completo, hash_contrasena)
    VALUES('functional_owner', 'functional-owner@example.invalid', 'Propietario prueba', 'TEST_HASH_NOT_REAL') RETURNING id INTO owner_id;
    INSERT INTO usuario(nombre_usuario, correo_electronico, nombre_completo, hash_contrasena)
    VALUES('functional_reader', 'functional-reader@example.invalid', 'Lector prueba', 'TEST_HASH_NOT_REAL') RETURNING id INTO reader_id;
    SELECT id_proyecto, id_modelo INTO project_a, model_a
    FROM crear_proyecto_con_propietario(owner_id, 'FUNCTIONAL-A', 'Proyecto funcional A');
    SELECT id_proyecto, id_modelo INTO project_b, model_b
    FROM crear_proyecto_con_propietario(owner_id, 'FUNCTIONAL-B', 'Proyecto funcional B');
    SELECT id INTO owner_member FROM miembro_proyecto WHERE id_proyecto = project_a AND id_usuario = owner_id;
    INSERT INTO miembro_proyecto(id_proyecto, id_usuario) VALUES(project_a, reader_id) RETURNING id INTO reader_member;
    INSERT INTO rol_miembro_proyecto(id_miembro_proyecto, id_rol, asignado_por)
    SELECT reader_member, id, owner_id FROM rol WHERE codigo = 'LECTOR_PROYECTO';
    PERFORM pg_temp.assert_true('Propietario creado con permisos', tiene_permiso_proyecto(owner_id, project_a, 'EDITAR_MODELO'));
    PERFORM pg_temp.assert_true('Lector consulta pero no edita', tiene_permiso_proyecto(reader_id, project_a, 'VER_MODELO')
        AND NOT tiene_permiso_proyecto(reader_id, project_a, 'EDITAR_MODELO'));
    PERFORM pg_temp.expect_error('Usuario duplicado sin distinguir mayusculas',
        'INSERT INTO usuario(nombre_usuario,correo_electronico,nombre_completo,hash_contrasena) VALUES(''FUNCTIONAL_OWNER'',''another@example.invalid'',''Otro'',''PRUEBA'')', '23505');
    INSERT INTO clase_uml(id_modelo, nombre) VALUES(model_a, 'Persona') RETURNING id INTO class_a;
    INSERT INTO clase_uml(id_modelo, nombre) VALUES(model_a, 'Cita') RETURNING id INTO class_b;
    INSERT INTO clase_uml(id_modelo, nombre) VALUES(model_b, 'Externa') RETURNING id INTO class_foreign;
    PERFORM pg_temp.expect_error('Clase duplicada', format('INSERT INTO clase_uml(id_modelo,nombre) VALUES(%L,''persona'')', model_a), '23505');
    PERFORM pg_temp.expect_error('Movimiento de clase entre modelos', format('UPDATE clase_uml SET id_modelo=%L WHERE id=%L', model_b, class_a), '23514');
    PERFORM pg_temp.expect_error('Relacion entre modelos diferentes', format(
        'INSERT INTO relacion_uml(id_modelo,id_clase_origen,id_clase_destino,tipo_relacion,minimo_origen,minimo_destino) VALUES(%L,%L,%L,''ASOCIACION'',0,1)',
        model_a, class_a, class_foreign), '23503');
    PERFORM pg_temp.expect_error('Cardinalidad incoherente', format(
        'INSERT INTO relacion_uml(id_modelo,id_clase_origen,id_clase_destino,tipo_relacion,minimo_origen,maximo_origen,minimo_destino) VALUES(%L,%L,%L,''ASOCIACION'',2,1,1)',
        model_a, class_a, class_b), '23514');
    INSERT INTO relacion_uml(id_modelo, id_clase_origen, id_clase_destino, tipo_relacion, minimo_origen, maximo_origen, minimo_destino, maximo_destino)
    VALUES(model_a, class_a, class_b, 'ASOCIACION', 1, 1, 0, NULL);
    INSERT INTO paquete_uml(id_modelo,nombre) VALUES(model_a, 'Dominio') RETURNING id INTO package_a;
    INSERT INTO paquete_uml(id_modelo,nombre,id_paquete_padre) VALUES(model_a, 'Subdominio', package_a) RETURNING id INTO package_b;
    PERFORM pg_temp.expect_error('Ciclo de paquetes', format('UPDATE paquete_uml SET id_paquete_padre=%L WHERE id=%L', package_b, package_a), '23514');
    INSERT INTO relacion_uml(id_modelo, id_clase_origen, id_clase_destino, tipo_relacion)
    VALUES(model_a, class_a, class_b, 'GENERALIZACION');
    PERFORM pg_temp.expect_error('Ciclo de herencia', format(
        'INSERT INTO relacion_uml(id_modelo,id_clase_origen,id_clase_destino,tipo_relacion) VALUES(%L,%L,%L,''GENERALIZACION'')', model_a, class_b, class_a), '23514');
    INSERT INTO atributo_uml(id_clase,nombre,tipo_uml,es_identificador) VALUES(class_a,'id','UUID',true) RETURNING id INTO attribute_a;
    PERFORM pg_temp.assert_true('Nulabilidad derivada de multiplicidad',
        (SELECT NOT admite_nulo FROM v_semantica_atributo WHERE id = attribute_a));
    PERFORM pg_temp.expect_error('Identificador opcional rechazado', format('UPDATE atributo_uml SET multiplicidad_minima=0 WHERE id=%L', attribute_a), '23514');
    PERFORM pg_temp.expect_error('Token de longitud incorrecta', format(
        'INSERT INTO sesion_autenticacion(id_usuario,hash_token,expira_en) VALUES(%L,decode(''ff'',''hex''),clock_timestamp()+interval ''1 hour'')', owner_id), '23514');
    PERFORM pg_temp.expect_error('Desactivar rol protegido', 'UPDATE rol SET estado=''INACTIVO'' WHERE codigo=''PROPIETARIO_PROYECTO''', '23514');
    PERFORM pg_temp.expect_error('Retirar membresia del propietario', format('UPDATE miembro_proyecto SET estado=''RETIRADO'' WHERE id=%L', owner_member), '23514');
    PERFORM pg_temp.expect_error('Revocar rol del propietario', format('UPDATE rol_miembro_proyecto SET revocado_en=clock_timestamp() WHERE id_miembro_proyecto=%L', owner_member), '23514');
    UPDATE usuario SET estado='INACTIVO' WHERE id=owner_id;
    PERFORM pg_temp.assert_true('Propietario inactivo conserva titularidad y pierde acceso',
        NOT tiene_permiso_proyecto(owner_id,project_a,'EDITAR_MODELO')
        AND EXISTS(SELECT 1 FROM proyecto WHERE id=project_a AND id_usuario_propietario=owner_id));
    UPDATE usuario SET estado='ACTIVO' WHERE id=owner_id;
    PERFORM pg_temp.expect_error('Rol global asignado desde proyecto', format(
        'INSERT INTO rol_miembro_proyecto(id_miembro_proyecto,id_rol) SELECT %L,id FROM rol WHERE codigo=''SUPERADMINISTRADOR''', reader_member), '23514');
    PERFORM pg_temp.expect_error('Permiso global concedido desde proyecto', format(
        'INSERT INTO permiso_miembro_proyecto(id_miembro_proyecto,id_permiso,decision) SELECT %L,id,''PERMITIR'' FROM permiso WHERE codigo=''GESTIONAR_USUARIOS''', reader_member), '23514');
    PERFORM pg_temp.expect_error('Lector no reserva revision', format('SELECT iniciar_edicion_modelo(%L,0,%L)', model_a, reader_id), '42501');
    revision_a := iniciar_edicion_modelo(model_a, 0, owner_id);
    version_a := guardar_version_modelo(model_a, revision_a, owner_id, 'Primera version');
    PERFORM pg_temp.assert_true('Instantanea completa y hash calculado', (SELECT
        jsonb_array_length(instantanea->'clases') = 2 AND jsonb_array_length(instantanea->'atributos') = 1
        AND hash_instantanea_sha256 = encode(digest(instantanea::text, 'sha256'), 'hex')
        FROM version_modelo_uml WHERE id = version_a));
    PERFORM pg_temp.assert_true('Version vigente enlazada', (SELECT id_version_actual = version_a FROM modelo_uml WHERE id = model_a));
    PERFORM pg_temp.expect_error('Revision obsoleta', format('SELECT iniciar_edicion_modelo(%L,0,%L)', model_a, owner_id), '40001');
    INSERT INTO cambio_modelo(id_modelo,id_usuario_actor,id_cambio_cliente,revision_base,revision_aplicada,
        tipo_operacion,tipo_entidad,parche,estado,aplicado_en)
    VALUES(model_a,owner_id,'22222222-2222-4222-8222-222222222222',0,1,'CREAR','CLASE_UML','{}','APLICADO',clock_timestamp());
    PERFORM pg_temp.expect_error('Cambio finalizado inmutable',format(
        'UPDATE cambio_modelo SET parche=''[]'' WHERE id_modelo=%L',model_a),'23514');
    PERFORM pg_temp.expect_error('Idempotencia de cambio cliente',format(
        'INSERT INTO cambio_modelo(id_modelo,id_usuario_actor,id_cambio_cliente,revision_base,tipo_operacion,tipo_entidad,parche) VALUES(%L,%L,''22222222-2222-4222-8222-222222222222'',1,''ACTUALIZAR'',''CLASE_UML'',''{}'')',model_a,owner_id),'23505');
    PERFORM pg_temp.expect_error('Cambio aplicado sin revision',format(
        'INSERT INTO cambio_modelo(id_modelo,id_usuario_actor,id_cambio_cliente,revision_base,tipo_operacion,tipo_entidad,parche,estado) VALUES(%L,%L,gen_random_uuid(),1,''ACTUALIZAR'',''CLASE_UML'',''{}'',''APLICADO'')',model_a,owner_id),'23514');
    PERFORM pg_temp.expect_error('Historial inmutable', format('UPDATE version_modelo_uml SET instantanea=''{}'' WHERE id=%L', version_a), '23514');
    PERFORM pg_temp.expect_error('Historial no eliminable', format('DELETE FROM version_modelo_uml WHERE id=%L', version_a), '23514');
    PERFORM iniciar_edicion_modelo(model_b,0,owner_id);
    version_b := guardar_version_modelo(model_b,1,owner_id,'Version externa');
    PERFORM pg_temp.expect_error('Version vigente de otro modelo', format('UPDATE modelo_uml SET id_version_actual=%L WHERE id=%L', version_b, model_a), '23503');
    PERFORM pg_temp.assert_true('Resumen sin multiplicar conteos', (SELECT cantidad_clases_activas=2 AND cantidad_relaciones_activas=2
        AND numero_ultima_version=1 FROM v_resumen_modelo WHERE id_modelo=model_a));
    SELECT id INTO audit_id FROM actividad_proyecto WHERE id_proyecto=project_a LIMIT 1;
    PERFORM pg_temp.expect_error('Auditoria no modificable', format('UPDATE actividad_proyecto SET accion=''ALTERADA'' WHERE id=%s', audit_id), '23514');
    PERFORM pg_temp.expect_error('Texto vacio para IA', format(
        'INSERT INTO solicitud_inteligente(id_modelo,solicitado_por,tipo_entrada,texto_original,revision_base) VALUES(%L,%L,''TEXTO'',''  '',1)', model_a, owner_id), '23514');
    INSERT INTO archivo_almacenado(id_proyecto,categoria,proveedor_almacenamiento,clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,sha256)
    VALUES(project_b,'IMAGEN_DIAGRAMA','PRUEBA','functional-image','pizarra.png','image/png',42,repeat('a',64)) RETURNING id INTO asset_image_foreign;
    PERFORM pg_temp.expect_error('Imagen de otro proyecto', format(
        'INSERT INTO solicitud_inteligente(id_modelo,solicitado_por,tipo_entrada,id_archivo_origen,revision_base) VALUES(%L,%L,''IMAGEN'',%L,1)', model_a, owner_id, asset_image_foreign), '23514');
    INSERT INTO solicitud_inteligente(id_modelo,solicitado_por,tipo_entrada,texto_original,revision_base)
    VALUES(model_a,owner_id,'TEXTO','Crea clase Producto',1) RETURNING id INTO request_a;
    INSERT INTO solicitud_inteligente(id_modelo,solicitado_por,tipo_entrada,texto_original,revision_base)
    VALUES(model_b,owner_id,'TEXTO','Crea clase Servicio',1) RETURNING id INTO request_b;
    INSERT INTO propuesta_inteligente(id_solicitud) VALUES(request_a) RETURNING id INTO proposal_a;
    INSERT INTO propuesta_inteligente(id_solicitud) VALUES(request_b) RETURNING id INTO proposal_b;
    INSERT INTO operacion_propuesta(id_propuesta,numero_secuencia,tipo_operacion,tipo_entidad) VALUES(proposal_a,1,'CREAR','CLASE_UML') RETURNING id INTO op_a;
    INSERT INTO operacion_propuesta(id_propuesta,numero_secuencia,tipo_operacion,tipo_entidad) VALUES(proposal_a,2,'CREAR','ATRIBUTO_UML') RETURNING id INTO op_b;
    INSERT INTO operacion_propuesta(id_propuesta,numero_secuencia,tipo_operacion,tipo_entidad) VALUES(proposal_b,1,'CREAR','CLASE_UML') RETURNING id INTO op_foreign;
    PERFORM pg_temp.expect_error('Dependencias entre propuestas diferentes', format(
        'INSERT INTO dependencia_operacion_propuesta VALUES(%L,%L)', op_a, op_foreign), '23514');
    INSERT INTO dependencia_operacion_propuesta VALUES(op_b,op_a);
    PERFORM pg_temp.expect_error('Ciclo en operaciones IA', format('INSERT INTO dependencia_operacion_propuesta VALUES(%L,%L)', op_a, op_b), '23514');
    PERFORM pg_temp.expect_error('Aplicar propuesta a version ajena', format(
        'UPDATE propuesta_inteligente SET estado=''APLICADO'',aplicado_por=%L,aplicado_en=clock_timestamp(),id_version_aplicada=%L WHERE id=%L', owner_id, version_b, proposal_a), '23514');
    INSERT INTO operacion_uml(id_clase,nombre) VALUES(class_a,'buscar') RETURNING id INTO method_a;
    INSERT INTO parametro_uml(id_operacion,nombre,tipo_uml,orden) VALUES(method_a,'id','UUID',0);
    INSERT INTO operacion_uml(id_clase,nombre) VALUES(class_a,'buscar') RETURNING id INTO method_b;
    INSERT INTO parametro_uml(id_operacion,nombre,tipo_uml,orden) VALUES(method_b,'nombre','String',0);
    SET CONSTRAINTS ALL IMMEDIATE;
    SET CONSTRAINTS ALL DEFERRED;
    PERFORM pg_temp.assert_true('Sobrecarga de operaciones valida', (SELECT count(*)=2 FROM v_firma_operacion WHERE id_clase=class_a));
    PERFORM pg_temp.expect_error('Firma duplicada al cambiar parametro', format('UPDATE parametro_uml SET tipo_uml=''UUID'' WHERE id_operacion=%L', method_b), '23505');
    INSERT INTO configuracion_generacion_backend(id_version_modelo,nombre,nombre_proyecto,id_grupo,id_artefacto,paquete_base,version_java,version_spring_boot,estado,creado_por)
    VALUES(version_a,'Configuracion inicial','Barberia','com.example','barberia','com.example.barberia','21','TEST_VERSION','VALIDO',owner_id) RETURNING id INTO config_a;
    INSERT INTO clase_configuracion_backend(id_configuracion,id_clase) VALUES(config_a,class_a);
    PERFORM pg_temp.expect_error('Clase ajena a version configurada', format('INSERT INTO clase_configuracion_backend(id_configuracion,id_clase) VALUES(%L,%L)',config_a,class_foreign),'23514');
    INSERT INTO configuracion_generacion_backend(id_version_modelo,nombre,nombre_proyecto,id_grupo,id_artefacto,paquete_base,version_java,version_spring_boot,creado_por)
    VALUES(version_a,'Borrador','Barberia','com.example','barberia','com.example.barberia','21','TEST_VERSION',owner_id) RETURNING id INTO config_draft;
    PERFORM pg_temp.expect_error('No generar configuracion borrador', format('INSERT INTO generacion_backend(id_configuracion,solicitado_por,numero_compilacion) VALUES(%L,%L,1)',config_draft,owner_id),'23514');
    PERFORM pg_temp.expect_error('No generar version UML sin validar',format('INSERT INTO generacion_backend(id_configuracion,solicitado_por,numero_compilacion) VALUES(%L,%L,1)',config_a,owner_id),'23514');
    UPDATE version_modelo_uml SET estado_validacion='VALIDO' WHERE id=version_a;
    INSERT INTO generacion_backend(id_configuracion,solicitado_por,numero_compilacion) VALUES(config_a,owner_id,1) RETURNING id INTO job_a;
    PERFORM pg_temp.expect_error('Configuracion utilizada inmutable',format('UPDATE configuracion_generacion_backend SET id_artefacto=''otro'' WHERE id=%L',config_a),'23514');
    PERFORM pg_temp.expect_error('Clases de configuracion utilizada inmutables',format('UPDATE clase_configuracion_backend SET nombre_tabla=''otra'' WHERE id_configuracion=%L',config_a),'23514');
    INSERT INTO archivo_almacenado(id_proyecto,categoria,proveedor_almacenamiento,clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,sha256)
    VALUES(project_a,'BACKEND_GENERADO','PRUEBA','functional-backend','backend.zip','application/zip',42,repeat('b',64)) RETURNING id INTO asset_zip;
    INSERT INTO archivo_almacenado(id_proyecto,categoria,proveedor_almacenamiento,clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,sha256)
    VALUES(project_a,'BACKEND_GENERADO','PRUEBA','functional-other-backend','other.zip','application/zip',42,repeat('c',64)) RETURNING id INTO asset_wrong;
    UPDATE generacion_backend SET estado='GENERADO',id_archivo_salida=asset_zip,completado_en=clock_timestamp() WHERE id=job_a;
    PERFORM pg_temp.expect_error('Descarga sin validar backend',format('INSERT INTO descarga_artefacto(id_generacion,id_archivo,descargado_por) VALUES(%L,%L,%L)',job_a,asset_zip,owner_id),'23514');
    INSERT INTO validacion_backend(id_generacion,estado,completado_en) VALUES(job_a,'VALIDO',clock_timestamp());
    PERFORM pg_temp.expect_error('Descarga por lector sin autorizacion',format('INSERT INTO descarga_artefacto(id_generacion,id_archivo,descargado_por) VALUES(%L,%L,%L)',job_a,asset_zip,reader_id),'23514');
    PERFORM pg_temp.expect_error('Descarga de archivo distinto al generado',format('INSERT INTO descarga_artefacto(id_generacion,id_archivo,descargado_por) VALUES(%L,%L,%L)',job_a,asset_wrong,owner_id),'23514');
    INSERT INTO descarga_artefacto(id_generacion,id_archivo,descargado_por) VALUES(job_a,asset_zip,owner_id);
    PERFORM pg_temp.assert_true('Descarga autorizada y validada',EXISTS(SELECT 1 FROM descarga_artefacto WHERE id_generacion=job_a));
    INSERT INTO permiso_miembro_proyecto(id_miembro_proyecto,id_permiso,decision)
    SELECT reader_member,id,'PERMITIR' FROM permiso WHERE codigo='EDITAR_MODELO';
    PERFORM pg_temp.assert_true('Permiso excepcional ALLOW',tiene_permiso_proyecto(reader_id,project_a,'EDITAR_MODELO'));
    UPDATE permiso_miembro_proyecto SET decision='DENEGAR' WHERE id_miembro_proyecto=reader_member;
    PERFORM pg_temp.assert_true('DENY prevalece',NOT tiene_permiso_proyecto(reader_id,project_a,'EDITAR_MODELO'));
    UPDATE permiso_miembro_proyecto SET decision='PERMITIR' WHERE id_miembro_proyecto=reader_member;
    UPDATE usuario SET estado='INACTIVO' WHERE id=reader_id;
    PERFORM pg_temp.assert_true('Usuario inactivo pierde excepciones',NOT tiene_permiso_proyecto(reader_id,project_a,'EDITAR_MODELO'));
    PERFORM pg_temp.assert_true('Cuenta runtime no es administradora',EXISTS(SELECT 1 FROM pg_roles WHERE rolname='uml_app'
        AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole));
    PERFORM pg_temp.assert_true('Runtime no puede modificar instantaneas',NOT has_column_privilege('uml_app','uml_colaborativo.version_modelo_uml','instantanea','UPDATE'));
    SET CONSTRAINTS ALL IMMEDIATE;
END;
$$;
SELECT label, passed FROM test_result ORDER BY label;
SELECT count(*) AS pruebas_aprobadas FROM test_result;
ROLLBACK;
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE uml_app;
SET search_path TO uml_colaborativo, public;
DO $$
DECLARE actor uuid; id_proyecto uuid; id_modelo uuid; reserved bigint; saved uuid;
BEGIN
    INSERT INTO usuario(nombre_usuario,correo_electronico,nombre_completo,hash_contrasena)
    VALUES('runtime_test','runtime-test@example.invalid','Prueba runtime','TEST_NOT_REAL') RETURNING id INTO actor;
    SELECT c.id_proyecto,c.id_modelo INTO id_proyecto,id_modelo
    FROM crear_proyecto_con_propietario(actor,'RUNTIME-TEST','Prueba Spring Boot') c;
    reserved := iniciar_edicion_modelo(id_modelo,0,actor);
    INSERT INTO clase_uml(id_modelo,nombre,creado_por) VALUES(id_modelo,'Cliente',actor);
    saved := guardar_version_modelo(id_modelo,reserved,actor,'Cambio transaccional');
    IF saved IS NULL THEN RAISE EXCEPTION 'No se guardo la version usando uml_app'; END IF;
    BEGIN
        UPDATE version_modelo_uml SET instantanea='{}' WHERE id=saved;
        RAISE EXCEPTION 'uml_app pudo alterar instantanea';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE NOTICE 'Runtime uml_app: transaccion completa y privilegios verificados';
END;
$$;
ROLLBACK;
