package bo.edu.uml.proyectos.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.proyectos.dto.MiembroDto.*;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class MiembroRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public MiembroRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID propietario(UUID proyecto, boolean bloquear) {
        return jdbc
                .query(
                        "select id_usuario_propietario from uml_colaborativo.proyecto where"
                                + " id=:proyecto"
                                + (bloquear ? " for update" : ""),
                        Map.of("proyecto", proyecto),
                        (r, n) -> r.getObject(1, UUID.class))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Proyecto no disponible."));
    }

    public List<Map<String, Object>> listar(UUID proyecto) {
        return jdbc.queryForList(
                """
                select m.id,m.id_usuario usuario,u.nombre_usuario::text identificador,u.nombre_completo nombre,u.correo_electronico::text correo,
                m.estado,m.motivo_estado motivo,m.actualizado_en::text revision,
                coalesce((select jsonb_agg(r.id) from uml_colaborativo.rol_miembro_proyecto mr join uml_colaborativo.rol r on r.id=mr.id_rol
                  where mr.id_miembro_proyecto=m.id and mr.revocado_en is null),'[]')::text roles_json,
                coalesce((select jsonb_agg(jsonb_build_object('permiso',id_permiso,'decision',decision)) from uml_colaborativo.permiso_miembro_proyecto
                  where id_miembro_proyecto=m.id and revocado_en is null),'[]')::text permisos_json,
                (select count(*) from uml_colaborativo.participante_sesion_modelo ps join uml_colaborativo.sesion_edicion_modelo s on s.id=ps.id_sesion_edicion
                  join uml_colaborativo.modelo_uml mo on mo.id=s.id_modelo where mo.id_proyecto=m.id_proyecto and ps.id_usuario=m.id_usuario and ps.salida_en is null and s.estado='ACTIVO') sesiones_activas
                from uml_colaborativo.miembro_proyecto m join uml_colaborativo.usuario u on u.id=m.id_usuario
                where m.id_proyecto=:proyecto order by u.nombre_completo,m.id
                """,
                Map.of("proyecto", proyecto));
    }

    public Map<String, Object> obtener(UUID proyecto, UUID id) {
        return listar(proyecto).stream()
                .filter(m -> id.equals(m.get("id")))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Miembro no disponible."));
    }

    public List<Map<String, Object>> buscar(UUID proyecto, String filtro) {
        if (filtro.trim().length() < 2) return List.of();
        return jdbc.queryForList(
                """
                select u.id,u.nombre_usuario::text usuario,u.nombre_completo nombre,u.correo_electronico::text correo
                from uml_colaborativo.usuario u where u.estado='ACTIVO'
                and (strpos(lower(u.nombre_completo),lower(:filtro))>0 or strpos(lower(u.correo_electronico::text),lower(:filtro))>0 or strpos(lower(u.nombre_usuario::text),lower(:filtro))>0)
                and not exists(select 1 from uml_colaborativo.miembro_proyecto m where m.id_proyecto=:proyecto and m.id_usuario=u.id)
                order by u.nombre_completo,u.id limit 20
                """,
                Map.of("proyecto", proyecto, "filtro", filtro.trim()));
    }

    public boolean activa(UUID usuario) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists(select 1 from uml_colaborativo.usuario where id=:usuario and"
                                + " estado='ACTIVO')",
                        Map.of("usuario", usuario),
                        Boolean.class));
    }

    public void guardar(UUID proyecto, UUID id, UUID actor, Guardar d, boolean nuevo) {
        var p =
                new MapSqlParameterSource()
                        .addValue("proyecto", proyecto)
                        .addValue("id", id)
                        .addValue("actor", actor)
                        .addValue("usuario", d.usuario())
                        .addValue("estado", d.estado())
                        .addValue("motivo", d.motivo())
                        .addValue("revision", d.revision())
                        .addValue("rol", d.rol());
        if (nuevo)
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.miembro_proyecto(id,id_proyecto,id_usuario,estado,incorporado_en)"
                        + " values(:id,:proyecto,:usuario,'ACTIVO',clock_timestamp())",
                    p);
        else {
            int n =
                    jdbc.update(
                            """
                            update uml_colaborativo.miembro_proyecto set estado=:estado,motivo_estado=:motivo,
                            suspendido_en=case when :estado='SUSPENDIDO' then clock_timestamp() else null end,
                            retirado_en=case when :estado='RETIRADO' then clock_timestamp() else null end
                            where id=:id and id_proyecto=:proyecto and id_usuario=:usuario and actualizado_en=cast(:revision as timestamptz)
                            """,
                            p);
            if (n != 1) throw new ApiException(409, "La membresía cambió. Recarga y revisa.");
        }
        jdbc.update(
                "update uml_colaborativo.rol_miembro_proyecto set revocado_en=clock_timestamp()"
                        + " where id_miembro_proyecto=:id and revocado_en is null",
                p);
        jdbc.update(
                "update uml_colaborativo.permiso_miembro_proyecto set revocado_en=clock_timestamp()"
                        + " where id_miembro_proyecto=:id and revocado_en is null",
                p);
        if (!d.estado().equals("RETIRADO")) {
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.rol_miembro_proyecto(id_miembro_proyecto,id_rol,asignado_por)"
                        + " values(:id,:rol,:actor)",
                    p);
            for (Decision decision : d.permisos())
                jdbc.update(
                        "insert into"
                            + " uml_colaborativo.permiso_miembro_proyecto(id_miembro_proyecto,id_permiso,decision,asignado_por)"
                            + " values(:id,:permiso,:decision,:actor)",
                        p.addValue("permiso", decision.permiso())
                                .addValue("decision", decision.decision()));
        }
        sincronizarAcceso(proyecto, d.usuario());
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.actividad_proyecto(id_proyecto,id_usuario_actor,categoria,accion,tipo_entidad,id_entidad)"
                    + " values(:proyecto,:actor,'MIEMBROS',:accion,'MIEMBRO_PROYECTO',:id)",
                p.addValue("accion", nuevo ? "INCORPORAR" : "ACTUALIZAR_" + d.estado()));
    }

    public void sincronizarAcceso(UUID proyecto, UUID usuario) {
        var p = Map.of("proyecto", proyecto, "usuario", usuario);
        jdbc.update(
                """
                update uml_colaborativo.participante_sesion_modelo ps set
                  modo_participante=case when uml_colaborativo.tiene_permiso_proyecto(:usuario,:proyecto,'EDITAR_MODELO')
                    and pr.estado='ACTIVO' and mo.estado<>'ARCHIVADO' then 'EDITOR' else 'LECTOR' end,
                  estado_presencia=case when uml_colaborativo.tiene_permiso_proyecto(:usuario,:proyecto,'VER_MODELO')
                    then ps.estado_presencia else 'DESCONECTADO' end,
                  salida_en=case when uml_colaborativo.tiene_permiso_proyecto(:usuario,:proyecto,'VER_MODELO')
                    then ps.salida_en else clock_timestamp() end,
                  tipo_entidad_enfocada=case when uml_colaborativo.tiene_permiso_proyecto(:usuario,:proyecto,'VER_MODELO')
                    then ps.tipo_entidad_enfocada else null end,
                  id_entidad_enfocada=case when uml_colaborativo.tiene_permiso_proyecto(:usuario,:proyecto,'VER_MODELO')
                    then ps.id_entidad_enfocada else null end,
                  ultima_presencia_en=clock_timestamp()
                from uml_colaborativo.sesion_edicion_modelo s,uml_colaborativo.modelo_uml mo,
                  uml_colaborativo.proyecto pr
                where ps.id_usuario=:usuario and ps.salida_en is null and ps.id_sesion_edicion=s.id
                  and s.id_modelo=mo.id and mo.id_proyecto=:proyecto and pr.id=mo.id_proyecto
                """,
                p);
        jdbc.update(
                """
                update uml_colaborativo.sesion_edicion_modelo s set
                  estado=case when exists(select 1 from uml_colaborativo.participante_sesion_modelo ps
                    where ps.id_sesion_edicion=s.id and ps.salida_en is null
                      and ps.estado_presencia in ('EN_LINEA','AUSENTE')) then 'ACTIVO' else 'CERRADO' end,
                  modo=case when exists(select 1 from uml_colaborativo.participante_sesion_modelo ps
                    where ps.id_sesion_edicion=s.id and ps.salida_en is null
                      and ps.modo_participante='EDITOR' and ps.estado_presencia in ('EN_LINEA','AUSENTE'))
                    then 'EDICION' else 'SOLO_LECTURA' end,
                  cerrado_en=case when exists(select 1 from uml_colaborativo.participante_sesion_modelo ps
                    where ps.id_sesion_edicion=s.id and ps.salida_en is null
                      and ps.estado_presencia in ('EN_LINEA','AUSENTE')) then null else clock_timestamp() end,
                  ultima_actividad_en=clock_timestamp()
                from uml_colaborativo.modelo_uml mo where s.estado='ACTIVO' and s.id_modelo=mo.id
                  and mo.id_proyecto=:proyecto
                """,
                p);
    }

    public void transferir(UUID proyecto, UUID miembro, UUID actor) {
        var m = obtener(proyecto, miembro);
        if (!"ACTIVO".equals(m.get("estado")) || !activa((UUID) m.get("usuario")))
            throw new ApiException(422, "El nuevo propietario debe ser un miembro activo.");
        var p =
                Map.of(
                        "proyecto",
                        proyecto,
                        "miembro",
                        miembro,
                        "actor",
                        actor,
                        "usuario",
                        m.get("usuario"));
        jdbc.update(
                """
                insert into uml_colaborativo.rol_miembro_proyecto(id_miembro_proyecto,id_rol,asignado_por)
                select :miembro,r.id,:actor from uml_colaborativo.rol r where r.codigo='PROPIETARIO_PROYECTO' and r.estado='ACTIVO'
                and not exists(select 1 from uml_colaborativo.rol_miembro_proyecto mr where mr.id_miembro_proyecto=:miembro and mr.id_rol=r.id and mr.revocado_en is null)
                """,
                p);
        jdbc.update(
                "update uml_colaborativo.permiso_miembro_proyecto set revocado_en=clock_timestamp()"
                    + " where id_miembro_proyecto=:miembro and decision='DENEGAR' and revocado_en"
                    + " is null",
                p);
        jdbc.update(
                "update uml_colaborativo.proyecto set id_usuario_propietario=:usuario where"
                        + " id=:proyecto",
                p);
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.actividad_proyecto(id_proyecto,id_usuario_actor,categoria,accion,tipo_entidad,id_entidad)"
                    + " values(:proyecto,:actor,'MIEMBROS','TRANSFERIR_PROPIEDAD','MIEMBRO_PROYECTO',:miembro)",
                p);
    }
}
