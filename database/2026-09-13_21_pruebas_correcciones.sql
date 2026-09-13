\set ON_ERROR_STOP on
BEGIN;
SET search_path=pg_catalog,uml_colaborativo,public,pg_temp;
CREATE TEMP TABLE test_result_desarrollo(label text PRIMARY KEY,passed boolean NOT NULL) ON COMMIT DROP;
GRANT SELECT,INSERT ON test_result_desarrollo TO uml_app;
CREATE FUNCTION pg_temp.comprobar(p_label text,p_value boolean) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF p_value IS DISTINCT FROM true THEN RAISE EXCEPTION 'FALLO: %',p_label; END IF;
    INSERT INTO pg_temp.test_result_desarrollo VALUES(p_label,true);
END;
$$;
CREATE FUNCTION pg_temp.esperar_error(p_label text,p_sql text,p_expected text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual_state text:='00000';
BEGIN
    BEGIN
        EXECUTE p_sql;
        SET CONSTRAINTS ALL IMMEDIATE;
    EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS actual_state=RETURNED_SQLSTATE;
    END;
    SET CONSTRAINTS ALL DEFERRED;
    IF actual_state<>p_expected THEN RAISE EXCEPTION 'FALLO: %, esperado %, obtenido %',p_label,p_expected,actual_state; END IF;
    PERFORM pg_temp.comprobar(p_label,true);
END;
$$;
DO $$
DECLARE actor uuid; reader_id uuid; project_a uuid; project_b uuid; model_a uuid; model_b uuid; class_a uuid; class_b uuid;
    class_other uuid; attr_a uuid; method_a uuid; param_a uuid; rel_a uuid; pkg_a uuid; version_a uuid;
    cfg_a uuid; job_a uuid; role_a uuid; permiso_a uuid; owner_member uuid; reserved bigint; snap jsonb;
BEGIN
    INSERT INTO usuario(nombre_usuario,correo_electronico,nombre_completo,hash_contrasena)
        VALUES('correcciones_actor','correcciones-actor@example.invalid','Prueba correcciones','TEST_ONLY') RETURNING id INTO actor;
    INSERT INTO usuario(nombre_usuario,correo_electronico,nombre_completo,hash_contrasena)
        VALUES('correcciones_lector','correcciones-lector@example.invalid','Prueba lector','TEST_ONLY') RETURNING id INTO reader_id;
    SELECT c.id_proyecto,c.id_modelo INTO project_a,model_a FROM crear_proyecto_con_propietario(actor,'CORRECCIONES-A','Prueba correcciones') c;
    SELECT c.id_proyecto,c.id_modelo INTO project_b,model_b FROM crear_proyecto_con_propietario(actor,'CORRECCIONES-B','Otro modelo') c;
    INSERT INTO clase_uml(id_modelo,nombre) VALUES(model_a,'Persona') RETURNING id INTO class_a;
    INSERT INTO clase_uml(id_modelo,nombre) VALUES(model_a,'Cita') RETURNING id INTO class_b;
    INSERT INTO clase_uml(id_modelo,nombre) VALUES(model_b,'Externa') RETURNING id INTO class_other;
    INSERT INTO atributo_uml(id_clase,nombre,tipo_uml,es_identificador) VALUES(class_a,'id','UUID',true) RETURNING id INTO attr_a;
    PERFORM pg_temp.esperar_error('ID ilimitado rechazado',format('UPDATE atributo_uml SET multiplicidad_maxima=NULL WHERE id=%L',attr_a),'23514');
    PERFORM pg_temp.esperar_error('ID multiple rechazado',format('UPDATE atributo_uml SET multiplicidad_maxima=2 WHERE id=%L',attr_a),'23514');
    INSERT INTO atributo_uml(id_clase,nombre,tipo_uml,multiplicidad_minima,multiplicidad_maxima)
        VALUES(class_b,'etiquetas','String',0,NULL);
    PERFORM pg_temp.comprobar('Coleccion no identificadora permitida',EXISTS(SELECT FROM atributo_uml WHERE id_clase=class_b AND multiplicidad_maxima IS NULL));
    UPDATE usuario SET estado='BLOQUEADO' WHERE id=actor;
    PERFORM pg_temp.comprobar('Bloquear propietario no elimina propiedad',EXISTS(SELECT FROM proyecto WHERE id=project_a AND id_usuario_propietario=actor));
    PERFORM pg_temp.comprobar('Propietario bloqueado pierde permisos',NOT tiene_permiso_proyecto(actor,project_a,'EDITAR_MODELO'));
    UPDATE usuario SET estado='INACTIVO' WHERE id=actor;
    SET CONSTRAINTS ALL IMMEDIATE;
    SET CONSTRAINTS ALL DEFERRED;
    PERFORM pg_temp.comprobar('Desactivar propietario permitido',EXISTS(SELECT FROM usuario WHERE id=actor AND estado='INACTIVO'));
    UPDATE usuario SET estado='ACTIVO' WHERE id=actor;
    SELECT id INTO owner_member FROM miembro_proyecto WHERE id_proyecto=project_a AND id_usuario=actor;
    PERFORM pg_temp.esperar_error('Retirar propietario sigue requiriendo transferencia',
        format('UPDATE miembro_proyecto SET estado=''RETIRADO'' WHERE id=%L',owner_member),'23514');

    INSERT INTO permiso(codigo,modulo,accion,descripcion,ambito)
        VALUES('PRUEBA_SCOPE','PRUEBA','SCOPE','Prueba','PROYECTO') RETURNING id INTO permiso_a;
    INSERT INTO rol(codigo,nombre,ambito) VALUES('PRUEBA_SCOPE','Prueba ambito','PROYECTO') RETURNING id INTO role_a;
    INSERT INTO rol_permiso(id_rol,id_permiso) VALUES(role_a,permiso_a);
    INSERT INTO rol_miembro_proyecto(id_miembro_proyecto,id_rol) VALUES(owner_member,role_a);
    PERFORM pg_temp.esperar_error('Ambito de rol asignado protegido',format('UPDATE rol SET ambito=''GLOBAL'' WHERE id=%L',role_a),'23514');
    PERFORM pg_temp.esperar_error('Ambito de permiso asignado protegido',format('UPDATE permiso SET ambito=''GLOBAL'' WHERE id=%L',permiso_a),'23514');
    INSERT INTO permiso_miembro_proyecto(id_miembro_proyecto,id_permiso,decision) VALUES(owner_member,permiso_a,'PERMITIR');
    UPDATE rol SET ambito='AMBOS' WHERE id=role_a;
    PERFORM pg_temp.esperar_error('Permiso excepcional impide pasar a global',format('UPDATE permiso SET ambito=''GLOBAL'' WHERE id=%L',permiso_a),'23514');
    UPDATE permiso SET ambito='AMBOS' WHERE id=permiso_a;
    PERFORM pg_temp.comprobar('Ampliar ambitos compatibles permitido',EXISTS(SELECT FROM permiso WHERE id=permiso_a AND ambito='AMBOS'));

    INSERT INTO operacion_uml(id_clase,nombre) VALUES(class_a,'buscar') RETURNING id INTO method_a;
    INSERT INTO parametro_uml(id_operacion,nombre,tipo_uml,orden) VALUES(method_a,'codigo','String',0) RETURNING id INTO param_a;
    INSERT INTO relacion_uml(id_modelo,id_clase_origen,id_clase_destino,tipo_relacion,minimo_origen,maximo_origen,minimo_destino,maximo_destino)
        VALUES(model_a,class_a,class_b,'ASOCIACION',1,1,0,NULL) RETURNING id INTO rel_a;
    INSERT INTO posicion_relacion_uml(id_relacion) VALUES(rel_a);
    INSERT INTO posicion_clase_uml(id_clase,x,y,ancho,alto) VALUES(class_a,0,0,150,100);
    reserved:=iniciar_edicion_modelo(model_a,0,actor);
    version_a:=guardar_version_modelo(model_a,reserved,actor,'Version para configuracion');
    UPDATE version_modelo_uml SET estado_validacion='VALIDO' WHERE id=version_a;
    INSERT INTO configuracion_generacion_backend(id_version_modelo,nombre,nombre_proyecto,id_grupo,id_artefacto,paquete_base,
        version_java,version_spring_boot,estado,creado_por,generar_dtos)
        VALUES(version_a,'Prueba','Prueba','com.example','prueba','com.example.prueba','21','TEST_ONLY','VALIDO',actor,false) RETURNING id INTO cfg_a;
    INSERT INTO clase_configuracion_backend(id_configuracion,id_clase) VALUES(cfg_a,class_a);
    INSERT INTO generacion_backend(id_configuracion,solicitado_por,numero_compilacion) VALUES(cfg_a,actor,1) RETURNING id INTO job_a;
    PERFORM pg_temp.comprobar('DTO opcional no bloquea persistencia',job_a IS NOT NULL);
    UPDATE configuracion_generacion_backend SET estado='ARCHIVADO' WHERE id=cfg_a;
    PERFORM pg_temp.comprobar('Archivar configuracion congelada permitido',EXISTS(SELECT FROM configuracion_generacion_backend WHERE id=cfg_a AND estado='ARCHIVADO'));
    UPDATE configuracion_generacion_backend SET estado='DESACTUALIZADO' WHERE id=cfg_a;
    PERFORM pg_temp.comprobar('Marcar configuracion desactualizada permitido',EXISTS(SELECT FROM configuracion_generacion_backend WHERE id=cfg_a AND estado='DESACTUALIZADO'));
    PERFORM pg_temp.esperar_error('Parametros congelados siguen protegidos',format('UPDATE configuracion_generacion_backend SET paquete_base=''com.otro'' WHERE id=%L',cfg_a),'23514');
    PERFORM pg_temp.esperar_error('Validacion valida con errores rechazada',format(
        'INSERT INTO validacion_backend(id_generacion,estado,cantidad_errores,completado_en) VALUES(%L,''VALIDO'',1,clock_timestamp())',job_a),'23514');
    PERFORM pg_temp.esperar_error('Validacion terminal sin finalizar rechazada',format(
        'INSERT INTO validacion_backend(id_generacion,estado) VALUES(%L,''VALIDO'')',job_a),'23514');
    PERFORM pg_temp.esperar_error('Advertencias requieren resultado correspondiente',format(
        'INSERT INTO validacion_backend(id_generacion,estado,cantidad_advertencias,completado_en) VALUES(%L,''VALIDO'',1,clock_timestamp())',job_a),'23514');
    PERFORM pg_temp.esperar_error('Resultado con advertencias sin advertencias rechazado',format(
        'INSERT INTO validacion_backend(id_generacion,estado,completado_en) VALUES(%L,''VALIDO_CON_ADVERTENCIAS'',clock_timestamp())',job_a),'23514');
    PERFORM pg_temp.esperar_error('Fechas invertidas rechazadas',format(
        'INSERT INTO validacion_backend(id_generacion,estado,iniciado_en,completado_en) VALUES(%L,''VALIDO'',clock_timestamp(),clock_timestamp()-interval ''1 hour'')',job_a),'23514');
    INSERT INTO validacion_backend(id_generacion,estado,cantidad_advertencias,completado_en)
        VALUES(job_a,'VALIDO_CON_ADVERTENCIAS',1,clock_timestamp());
    PERFORM pg_temp.comprobar('Resultado coherente con advertencias permitido',EXISTS(SELECT FROM validacion_backend WHERE id_generacion=job_a));
    PERFORM pg_temp.esperar_error('Validacion UML incoherente rechazada',format(
        'INSERT INTO validacion_modelo(id_modelo,revision_validada,estado,cantidad_errores,completado_en) VALUES(%L,1,''VALIDO'',1,clock_timestamp())',model_a),'23514');

    INSERT INTO paquete_uml(id_modelo,nombre) VALUES(model_a,'Dominio') RETURNING id INTO pkg_a;
    UPDATE clase_uml SET id_paquete=pkg_a WHERE id=class_b;
    PERFORM pg_temp.esperar_error('Paquete no vacio protegido',format('UPDATE paquete_uml SET eliminado_en=clock_timestamp() WHERE id=%L',pkg_a),'23514');
    UPDATE clase_uml SET eliminado_en=clock_timestamp() WHERE id=class_a;
    PERFORM pg_temp.comprobar('Borrado de clase propaga a atributos',EXISTS(SELECT FROM atributo_uml WHERE id=attr_a AND eliminado_en IS NOT NULL));
    PERFORM pg_temp.comprobar('Borrado de clase propaga a operaciones',EXISTS(SELECT FROM operacion_uml WHERE id=method_a AND eliminado_en IS NOT NULL));
    PERFORM pg_temp.comprobar('Borrado propaga a parametros',EXISTS(SELECT FROM parametro_uml WHERE id=param_a AND eliminado_en IS NOT NULL));
    PERFORM pg_temp.comprobar('Borrado propaga a relaciones',EXISTS(SELECT FROM relacion_uml WHERE id=rel_a AND eliminado_en IS NOT NULL));
    snap:=construir_instantanea_modelo(model_a);
    PERFORM pg_temp.comprobar('Instantanea sin conectores visuales huerfanos',jsonb_array_length(snap->'relaciones')=0 AND jsonb_array_length(snap->'posiciones_relaciones')=0);
    PERFORM pg_temp.comprobar('Instantanea anterior conservada',(SELECT jsonb_array_length(instantanea->'clases')=2 FROM version_modelo_uml WHERE id=version_a));
    PERFORM pg_temp.esperar_error('Atributo sobre clase eliminada rechazado',format('INSERT INTO atributo_uml(id_clase,nombre,tipo_uml) VALUES(%L,''otro'',''String'')',class_a),'23514');
    PERFORM pg_temp.esperar_error('Operacion sobre clase eliminada rechazada',format('INSERT INTO operacion_uml(id_clase,nombre) VALUES(%L,''otra'')',class_a),'23514');
    PERFORM pg_temp.esperar_error('Parametro sobre operacion eliminada rechazado',format('INSERT INTO parametro_uml(id_operacion,nombre,tipo_uml,orden) VALUES(%L,''otro'',''String'',1)',method_a),'23514');
    PERFORM pg_temp.esperar_error('Relacion hacia clase eliminada rechazada',format(
        'INSERT INTO relacion_uml(id_modelo,id_clase_origen,id_clase_destino,tipo_relacion,minimo_origen,minimo_destino) VALUES(%L,%L,%L,''ASOCIACION'',1,0)',model_a,class_b,class_a),'23514');
    UPDATE clase_uml SET id_paquete=NULL WHERE id=class_b;
    UPDATE paquete_uml SET eliminado_en=clock_timestamp() WHERE id=pkg_a;
    PERFORM pg_temp.esperar_error('Clase en paquete eliminado rechazada',format('UPDATE clase_uml SET id_paquete=%L WHERE id=%L',pkg_a,class_b),'23514');
    PERFORM pg_temp.esperar_error('Paquete en padre eliminado rechazado',format('INSERT INTO paquete_uml(id_modelo,nombre,id_paquete_padre) VALUES(%L,''Hijo'',%L)',model_a,pkg_a),'23514');

    SET LOCAL ROLE uml_app;
    PERFORM pg_temp.esperar_error('Escritura directa sin reserva rechazada',format('INSERT INTO clase_uml(id_modelo,nombre) VALUES(%L,''Directa'')',model_b),'42501');
    PERFORM pg_temp.esperar_error('Nombre de modelo tambien requiere revision',format('UPDATE modelo_uml SET nombre=''SinRevision'' WHERE id=%L',model_b),'42501');
    PERFORM pg_temp.esperar_error('Proyecto archivado no admite edicion',format(
        'UPDATE proyecto SET estado=''ARCHIVADO'' WHERE id=%L; SELECT iniciar_edicion_modelo(%L,0,%L)',project_b,model_b,actor),'40001');
    PERFORM pg_temp.esperar_error('Revision directa protegida',format('UPDATE modelo_uml SET revision_actual=8 WHERE id=%L',model_b),'42501');
    PERFORM pg_temp.esperar_error('Version fabricada protegida',format(
        'INSERT INTO version_modelo_uml(id_modelo,numero_version,revision,instantanea,hash_instantanea_sha256) VALUES(%L,1,0,''{}'',repeat(''a'',64))',model_b),'42501');
    PERFORM pg_temp.esperar_error('Reserva sin actor no accesible al backend',format('SELECT reservar_revision_modelo(%L,0)',model_b),'42501');
    PERFORM pg_temp.esperar_error('Reserva sin guardar no confirma',format(
        'SELECT iniciar_edicion_modelo(%L,0,%L); INSERT INTO clase_uml(id_modelo,nombre) VALUES(%L,''NoConfirmada'')',model_b,actor,model_b),'23514');
    reserved:=iniciar_edicion_modelo(model_b,0,actor);
    INSERT INTO clase_uml(id_modelo,nombre) VALUES(model_b,'Transaccional');
    PERFORM pg_temp.esperar_error('Archivar durante edicion impide guardar',format(
        'UPDATE proyecto SET estado=''ARCHIVADO'' WHERE id=%L; SELECT guardar_version_modelo(%L,1,%L,''Archivado'')',project_b,model_b,actor),'40001');
    UPDATE modelo_uml SET nombre='Nombre versionado' WHERE id=model_b;
    PERFORM pg_temp.esperar_error('Guardar con actor distinto rechazado',format('SELECT guardar_version_modelo(%L,1,%L,''Actor incorrecto'')',model_b,reader_id),'42501');
    PERFORM pg_temp.esperar_error('Escritura en otro modelo rechazada',format('UPDATE clase_uml SET nombre=''NoAutorizada'' WHERE id=%L',class_b),'42501');
    version_a:=guardar_version_modelo(model_b,reserved,actor,'Cambio confirmado');
    SET CONSTRAINTS ALL IMMEDIATE;
    SET CONSTRAINTS ALL DEFERRED;
    PERFORM pg_temp.comprobar('Edicion del backend confirmada',(SELECT id_version_actual=version_a AND revision_actual=1 FROM modelo_uml WHERE id=model_b));
    PERFORM pg_temp.esperar_error('Escritura posterior al guardado rechazada',format('UPDATE clase_uml SET nombre=''FueraDeVersion'' WHERE id=%L',class_other),'42501');
    PERFORM pg_temp.esperar_error('Revision obsoleta conserva conflicto',format('SELECT iniciar_edicion_modelo(%L,0,%L)',model_b,actor),'40001');
    -- Una tabla temporal del llamante no puede sombrear la tabla de dominio.
    CREATE TEMP TABLE clase_uml(id uuid,nombre text);
    reserved:=iniciar_edicion_modelo(model_b,1,actor);
    INSERT INTO uml_colaborativo.clase_uml(id_modelo,nombre) VALUES(model_b,'SinSombreado');
    version_a:=guardar_version_modelo(model_b,reserved,actor,'Esquemas confiables');
    PERFORM pg_temp.comprobar('Definer ignora tablas temporales',(SELECT jsonb_array_length(instantanea->'clases')=3
        FROM uml_colaborativo.version_modelo_uml WHERE id=version_a));
    RESET ROLE;
    PERFORM pg_temp.comprobar('Funciones nuevas no son publicas',NOT EXISTS(
        SELECT FROM pg_proc p CROSS JOIN LATERAL aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) a
        WHERE p.oid='uml_colaborativo.guardar_version_modelo(uuid,bigint,uuid,text)'::regprocedure
        AND a.grantee=0 AND a.privilege_type='EXECUTE'));
    SET CONSTRAINTS ALL IMMEDIATE;
END;
$$;
SELECT label,passed FROM pg_temp.test_result_desarrollo ORDER BY label;
SELECT count(*) AS correcciones_aprobadas FROM pg_temp.test_result_desarrollo;
ROLLBACK;
