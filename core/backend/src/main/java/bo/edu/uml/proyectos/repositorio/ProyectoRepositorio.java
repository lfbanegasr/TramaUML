package bo.edu.uml.proyectos.repositorio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.proyectos.dto.ProyectoDto.*;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
            (:admin or uml_colaborativo.tiene_permiso_proyecto(:actor,p.id,'GESTIONAR_MIEMBROS')) puede_miembros
            """;

    private Map<String, Object> parametros(Identidad actor, String filtro, String estado) {
        return new HashMap<>(
                Map.of(
                        "actor",
                        actor.id(),
                        "admin",
                        actor.esAdministrador(),
                        "filtro",
                        filtro,
                        "estado",
                        estado));
    }

    public Pagina listar(Identidad actor, String filtro, String estado, int pagina, int tamano) {
        var params = parametros(actor, filtro, estado);
        params.put("limite", tamano);
        params.put("offset", pagina * tamano);
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
        var p = parametros(actor, "", "");
        p.put("id", id);
        return jdbc.query(SELECT + AUTORIZADOS + " and p.id=:id", p, this::resumen).stream()
                .findFirst();
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
                r.getBoolean("puede_miembros"));
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
                    + " uml_colaborativo.actividad_proyecto(id_proyecto,id_usuario_actor,categoria,accion)"
                    + " values(:proyecto,:actor,'PROYECTO','CREAR')",
                Map.of("proyecto", id, "actor", actor.id()));
    }
}
