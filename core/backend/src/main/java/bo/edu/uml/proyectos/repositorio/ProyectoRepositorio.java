package bo.edu.uml.proyectos.repositorio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.proyectos.dto.ProyectoDto.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.*;
import java.util.*;

@Repository
public class ProyectoRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public ProyectoRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String AUTORIZADOS =
            """
            from uml_colaborativo.proyecto p join uml_colaborativo.usuario propietario on propietario.id=p.id_usuario_propietario
            where (:admin or uml_colaborativo.tiene_permiso_proyecto(:actor,p.id,'VER_PROYECTOS'))
            and (:filtro='' or strpos(lower(p.nombre),lower(:filtro))>0 or strpos(lower(p.codigo::text),lower(:filtro))>0)
            and (:estado='' or p.estado=:estado)
            and (cast(:desde as timestamptz) is null or p.creado_en>=cast(:desde as timestamptz))
            and (cast(:hasta as timestamptz) is null or p.creado_en<cast(:hasta as timestamptz))
            and (:rol='' or (:rol='SUPERADMINISTRADOR' and :admin) or exists(
              select 1 from uml_colaborativo.miembro_proyecto fm join uml_colaborativo.rol_miembro_proyecto fr on fr.id_miembro_proyecto=fm.id
              join uml_colaborativo.rol rr on rr.id=fr.id_rol where fm.id_proyecto=p.id and fm.id_usuario=:actor
              and fm.estado='ACTIVO' and fr.revocado_en is null and rr.estado='ACTIVO' and rr.codigo=:rol))
            """;
    private static final String SELECT =
            """
            select p.id,p.codigo,p.nombre,p.descripcion,p.estado,propietario.nombre_completo propietario,
            coalesce((select string_agg(r.nombre,', ' order by r.nombre) from uml_colaborativo.miembro_proyecto mp
              join uml_colaborativo.rol_miembro_proyecto mr on mr.id_miembro_proyecto=mp.id and mr.revocado_en is null
              join uml_colaborativo.rol r on r.id=mr.id_rol and r.estado='ACTIVO'
              where mp.id_usuario=:actor and mp.id_proyecto=p.id and mp.estado='ACTIVO'),case when :admin then 'Administrador' else 'Sin rol' end) rol,
            (select id from uml_colaborativo.modelo_uml m where m.id_proyecto=p.id order by m.creado_en,m.id limit 1) id_modelo,
            (:admin or uml_colaborativo.tiene_permiso_proyecto(:actor,p.id,'VER_MODELO')) puede_ver,
            (p.estado='ACTIVO' and uml_colaborativo.tiene_permiso_proyecto(:actor,p.id,'EDITAR_MODELO')) puede_editar,
            (:admin or uml_colaborativo.tiene_permiso_proyecto(:actor,p.id,'GESTIONAR_MIEMBROS')) puede_miembros,
            (:admin or uml_colaborativo.tiene_permiso_proyecto(:actor,p.id,'GESTIONAR_PROYECTOS')) puede_gestionar,
            (:admin or uml_colaborativo.tiene_permiso_proyecto(:actor,p.id,'VER_ACTIVIDAD')) puede_actividad,
            p.actualizado_en,p.creado_en,p.archivado_en,
            (select nombre from uml_colaborativo.modelo_uml m where m.id_proyecto=p.id order by m.creado_en,m.id limit 1) nombre_modelo,
            coalesce((select revision_actual from uml_colaborativo.modelo_uml m where m.id_proyecto=p.id order by m.creado_en,m.id limit 1),0) revision_modelo,
            (select count(*) from uml_colaborativo.miembro_proyecto m where m.id_proyecto=p.id and m.estado='ACTIVO') miembros_activos,
            (select count(*) from uml_colaborativo.sesion_edicion_modelo s join uml_colaborativo.modelo_uml m on m.id=s.id_modelo
              where m.id_proyecto=p.id and s.estado='ACTIVO' and s.modo='EDICION') sesiones_edicion,
            coalesce((select count(*) from uml_colaborativo.version_modelo_uml v join uml_colaborativo.modelo_uml m on m.id=v.id_modelo
              where m.id_proyecto=p.id),0) total_versiones
            """;

    private MapSqlParameterSource parametros(
            Identidad actor,
            String filtro,
            String estado,
            String rol,
            LocalDate desde,
            LocalDate hasta) {
        return new MapSqlParameterSource()
                .addValue("actor", actor.id())
                .addValue("admin", actor.esAdministrador())
                .addValue("filtro", filtro)
                .addValue("estado", estado)
                .addValue("rol", rol)
                .addValue(
                        "desde",
                        desde == null
                                ? null
                                : desde.atStartOfDay().atOffset(ZoneOffset.UTC).toString())
                .addValue(
                        "hasta",
                        hasta == null
                                ? null
                                : hasta.plusDays(1)
                                        .atStartOfDay()
                                        .atOffset(ZoneOffset.UTC)
                                        .toString());
    }

    public Pagina listar(
            Identidad actor,
            String filtro,
            String estado,
            String rol,
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamano) {
        var params = parametros(actor, filtro, estado, rol, desde, hasta);
        params.addValue("limite", tamano).addValue("offset", pagina * tamano);
        long total = jdbc.queryForObject("select count(*) " + AUTORIZADOS, params, Long.class);
        var list =
                jdbc.query(
                        SELECT
                                + AUTORIZADOS
                                + " order by p.actualizado_en desc,p.id limit :limite offset"
                                + " :offset",
                        params,
                        this::resumen);
        return new Pagina(list, pagina, tamano, total);
    }

    public Optional<Resumen> obtener(Identidad actor, UUID id) {
        var p = parametros(actor, "", "", "", null, null);
        p.addValue("id", id);
        return jdbc.query(SELECT + AUTORIZADOS + " and p.id=:id", p, this::resumen).stream()
                .findFirst();
    }

    public void bloquear(UUID id) {
        jdbc.query(
                "select id from uml_colaborativo.proyecto where id=:id for update nowait",
                Map.of("id", id),
                (r, n) -> r.getObject(1, UUID.class));
    }

    public List<Map<String, Object>> rolesConsulta() {
        return jdbc.queryForList(
                "select codigo::text codigo,nombre from uml_colaborativo.rol where estado='ACTIVO'"
                        + " and ambito in ('PROYECTO','AMBOS') order by nombre,codigo",
                Map.of());
    }

    public List<Map<String, Object>> equipo(UUID id) {
        return jdbc.queryForList(
                """
                select m.id,u.nombre_completo nombre,m.estado,
                  coalesce(string_agg(r.nombre,', ' order by r.nombre) filter(where r.estado='ACTIVO'),'Sin rol') roles
                from uml_colaborativo.miembro_proyecto m join uml_colaborativo.usuario u on u.id=m.id_usuario
                left join uml_colaborativo.rol_miembro_proyecto mr on mr.id_miembro_proyecto=m.id and mr.revocado_en is null
                left join uml_colaborativo.rol r on r.id=mr.id_rol where m.id_proyecto=:id
                group by m.id,u.nombre_completo order by u.nombre_completo,m.id
                """,
                Map.of("id", id));
    }

    public String valores(UUID id) {
        return jdbc.queryForObject(
                "select"
                    + " jsonb_build_object('codigo',codigo,'nombre',nombre,'descripcion',descripcion,'estado',estado,'archivadoEn',archivado_en)::text"
                    + " from uml_colaborativo.proyecto where id=:id",
                Map.of("id", id),
                String.class);
    }

    public void actualizar(UUID id, Actualizar datos) {
        jdbc.update(
                "update uml_colaborativo.proyecto set"
                        + " codigo=:codigo,nombre=:nombre,descripcion=:descripcion where id=:id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("codigo", datos.codigo())
                        .addValue("nombre", datos.nombre().trim())
                        .addValue("descripcion", datos.descripcion()));
    }

    public void estado(UUID id, boolean archivar) {
        jdbc.update(
                "update uml_colaborativo.proyecto set estado=:estado,archivado_en="
                        + (archivar ? "clock_timestamp()" : "null")
                        + " where id=:id",
                Map.of("id", id, "estado", archivar ? "ARCHIVADO" : "ACTIVO"));
    }

    private Resumen resumen(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Resumen(
                r.getObject("id", UUID.class),
                r.getString("codigo"),
                r.getString("nombre"),
                r.getString("descripcion"),
                r.getString("estado"),
                r.getString("propietario"),
                r.getString("rol"),
                r.getObject("id_modelo", UUID.class),
                r.getBoolean("puede_ver"),
                r.getBoolean("puede_editar"),
                r.getBoolean("puede_miembros"),
                r.getBoolean("puede_gestionar"),
                r.getBoolean("puede_actividad"),
                r.getObject("actualizado_en", OffsetDateTime.class).toString(),
                r.getObject("creado_en", OffsetDateTime.class).toString(),
                r.getObject("archivado_en", OffsetDateTime.class) == null
                        ? null
                        : r.getObject("archivado_en", OffsetDateTime.class).toString(),
                r.getString("nombre_modelo"),
                r.getLong("revision_modelo"),
                r.getInt("miembros_activos"),
                r.getInt("sesiones_edicion"),
                r.getLong("total_versiones"));
    }

    public void crear(UUID id, Identidad actor, Crear datos, UUID modelo) {
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.proyecto(id,codigo,nombre,descripcion,id_usuario_propietario)"
                    + " values(:id,:codigo,:nombre,:descripcion,:actor)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("codigo", datos.codigo())
                        .addValue("nombre", datos.nombre().trim())
                        .addValue("descripcion", datos.descripcion())
                        .addValue("actor", actor.id()));
        UUID miembro = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.miembro_proyecto(id,id_proyecto,id_usuario,incorporado_en)"
                    + " values(:id,:proyecto,:actor,clock_timestamp())",
                Map.of("id", miembro, "proyecto", id, "actor", actor.id()));
        int roles =
                jdbc.update(
                        "insert into"
                            + " uml_colaborativo.rol_miembro_proyecto(id_miembro_proyecto,id_rol,asignado_por)"
                            + " select :miembro,id,:actor from uml_colaborativo.rol where"
                            + " codigo='PROPIETARIO_PROYECTO' and estado='ACTIVO'",
                        Map.of("miembro", miembro, "actor", actor.id()));
        if (roles != 1) throw new IllegalStateException("Falta el rol de propietario.");
        jdbc.update(
                "insert into uml_colaborativo.modelo_uml(id,id_proyecto,nombre,creado_por)"
                        + " values(:id,:proyecto,'Modelo de clases',:actor)",
                Map.of("id", modelo, "proyecto", id, "actor", actor.id()));
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.actividad_proyecto(id_proyecto,id_usuario_actor,categoria,accion,tipo_entidad,id_entidad,valor_posterior)"
                    + " select"
                    + " :proyecto,:actor,'PROYECTO','CREAR','PROYECTO',:proyecto,jsonb_build_object('codigo',codigo,'nombre',nombre,'descripcion',descripcion,'estado',estado)"
                    + " from uml_colaborativo.proyecto where id=:proyecto",
                Map.of("proyecto", id, "actor", actor.id()));
    }
}
