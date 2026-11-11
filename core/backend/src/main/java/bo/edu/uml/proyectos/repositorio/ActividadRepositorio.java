package bo.edu.uml.proyectos.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.proyectos.dto.ActividadDto.*;

import com.fasterxml.jackson.databind.*;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.time.*;
import java.util.*;

@Repository
public class ActividadRepositorio {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ActividadRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static final String DESDE =
            """
            from uml_colaborativo.actividad_proyecto a
            left join uml_colaborativo.usuario u on u.id=a.id_usuario_actor
            left join uml_colaborativo.version_modelo_uml v on v.id=a.id_version_modelo
            where a.id_proyecto=:proyecto
            """;
    private static final String FILTROS =
            """
            and (cast(:actor as uuid) is null or a.id_usuario_actor=cast(:actor as uuid))
            and (:accion='' or a.accion=:accion)
            and (:tipo='' or a.tipo_entidad=:tipo)
            and (cast(:entidad as uuid) is null or a.id_entidad=cast(:entidad as uuid))
            and (cast(:desde as timestamptz) is null or a.ocurrido_en>=cast(:desde as timestamptz))
            and (cast(:hasta as timestamptz) is null or a.ocurrido_en<cast(:hasta as timestamptz))
            """;
    private static final String SELECT =
            "select a.id::text,a.id_usuario_actor,coalesce(u.nombre_completo,'Actor no disponible')"
                + " responsable,a.categoria,a.accion,a.tipo_entidad,a.id_entidad,a.ocurrido_en,a.id_modelo,a.id_version_modelo,v.revision"
                + " revision_modelo ";

    public Pagina listar(
            UUID proyecto,
            UUID actor,
            String accion,
            String tipo,
            UUID entidad,
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamano) {
        var p =
                new MapSqlParameterSource()
                        .addValue("proyecto", proyecto)
                        .addValue("actor", actor == null ? null : actor.toString())
                        .addValue("accion", accion)
                        .addValue("tipo", tipo)
                        .addValue("entidad", entidad == null ? null : entidad.toString())
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
                                                .toString())
                        .addValue("limite", tamano)
                        .addValue("offset", pagina * tamano);
        long total = jdbc.queryForObject("select count(*) " + DESDE + FILTROS, p, Long.class);
        return new Pagina(
                jdbc.query(
                        SELECT
                                + DESDE
                                + FILTROS
                                + " order by a.ocurrido_en desc,a.id desc limit :limite offset"
                                + " :offset",
                        p,
                        this::evento),
                pagina,
                tamano,
                total);
    }

    private Evento evento(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new Evento(
                r.getString("id"),
                r.getObject("id_usuario_actor", UUID.class),
                r.getString("responsable"),
                r.getString("categoria"),
                r.getString("accion"),
                r.getString("tipo_entidad"),
                r.getObject("id_entidad", UUID.class),
                r.getObject("ocurrido_en", OffsetDateTime.class).toString(),
                r.getObject("id_modelo", UUID.class),
                r.getObject("id_version_modelo", UUID.class),
                r.getObject("revision_modelo", Long.class));
    }

    public Detalle detalle(UUID proyecto, long id) {
        return jdbc
                .query(
                        SELECT
                                + ",a.valor_anterior::text,a.valor_posterior::text,a.detalle::text "
                                + DESDE
                                + " and a.id=:id",
                        Map.of("proyecto", proyecto, "id", id),
                        (r, n) ->
                                new Detalle(
                                        evento(r, n),
                                        leer(r.getString("valor_anterior")),
                                        leer(r.getString("valor_posterior")),
                                        leer(r.getString("detalle"))))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Actividad no disponible."));
    }

    public Map<String, Object> filtros(UUID proyecto) {
        return Map.of(
                "actores",
                jdbc.queryForList(
                        "select distinct a.id_usuario_actor id,u.nombre_completo nombre from"
                            + " uml_colaborativo.actividad_proyecto a join uml_colaborativo.usuario"
                            + " u on u.id=a.id_usuario_actor where a.id_proyecto=:id order by"
                            + " nombre,id",
                        Map.of("id", proyecto)),
                "acciones",
                jdbc.queryForList(
                        "select distinct accion from uml_colaborativo.actividad_proyecto where"
                                + " id_proyecto=:id order by accion",
                        Map.of("id", proyecto),
                        String.class),
                "tipos",
                jdbc.queryForList(
                        "select distinct tipo_entidad from uml_colaborativo.actividad_proyecto"
                                + " where id_proyecto=:id and tipo_entidad is not null order by"
                                + " tipo_entidad",
                        Map.of("id", proyecto),
                        String.class));
    }

    public boolean repeticion(
            UUID proyecto, UUID actor, UUID solicitud, String accion, String peticion) {
        var filas =
                jdbc.queryForList(
                        "select id_usuario_actor,accion,detalle->'peticion'=cast(:peticion as"
                                + " jsonb) coincide from uml_colaborativo.actividad_proyecto where"
                                + " id_proyecto=:proyecto and categoria='PROYECTO' and"
                                + " detalle->>'idSolicitudCliente'=:solicitud",
                        Map.of(
                                "proyecto",
                                proyecto,
                                "solicitud",
                                solicitud.toString(),
                                "peticion",
                                peticion));
        if (filas.isEmpty()) return false;
        var f = filas.getFirst();
        if (!actor.equals(f.get("id_usuario_actor"))
                || !accion.equals(f.get("accion"))
                || !Boolean.TRUE.equals(f.get("coincide")))
            throw new ApiException(
                    409,
                    "El UUID ya identifica otra solicitud. Conserva el borrador y revisa la"
                            + " operación.");
        return true;
    }

    public void registrar(
            UUID proyecto,
            UUID actor,
            String categoria,
            String accion,
            String tipo,
            UUID entidad,
            UUID modelo,
            UUID version,
            String anterior,
            String posterior,
            String detalle) {
        jdbc.update(
                """
                insert into uml_colaborativo.actividad_proyecto(id_proyecto,id_usuario_actor,categoria,accion,tipo_entidad,id_entidad,id_modelo,id_version_modelo,valor_anterior,valor_posterior,detalle)
                values(:proyecto,:actor,:categoria,:accion,:tipo,:entidad,:modelo,:version,cast(:anterior as jsonb),cast(:posterior as jsonb),cast(:detalle as jsonb))
                """,
                new MapSqlParameterSource()
                        .addValue("proyecto", proyecto)
                        .addValue("actor", actor)
                        .addValue("categoria", categoria)
                        .addValue("accion", accion)
                        .addValue("tipo", tipo)
                        .addValue("entidad", entidad)
                        .addValue("modelo", modelo)
                        .addValue("version", version)
                        .addValue("anterior", anterior)
                        .addValue("posterior", posterior)
                        .addValue("detalle", detalle));
    }

    public String escribir(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar la actividad.", e);
        }
    }

    private JsonNode leer(String valor) {
        try {
            return valor == null ? null : json.readTree(valor);
        } catch (Exception e) {
            throw new IllegalStateException("Actividad JSON inválida.", e);
        }
    }
}
