package bo.edu.uml.historial.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.historial.dto.HistorialDto.*;
import bo.edu.uml.historial.servicio.ValidadorSnapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;

/**
 * Acceso a {@code version_modelo_uml} y tablas relacionadas para CU19/CU20. No importa clases de
 * otros módulos; la redundancia de {@code cabecera} es controlada y explícita para evitar
 * dependencia circular con el módulo {@code modelado}.
 */
@Repository
public class HistorialRepositorio {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ValidadorSnapshot validador;

    public HistorialRepositorio(
            NamedParameterJdbcTemplate jdbc, ObjectMapper json, ValidadorSnapshot validador) {
        this.jdbc = jdbc;
        this.json = json;
        this.validador = validador;
    }

    /** Cabecera del modelo con estado del proyecto; independiente de ModeloRepositorio. */
    public record Cabecera(
            UUID id,
            UUID proyecto,
            String nombre,
            long revision,
            UUID version,
            String estado,
            String estadoProyecto) {}

    public Cabecera cabecera(UUID modelo) {
        return jdbc
                .query(
                        "select m.id,m.id_proyecto,m.nombre,m.revision_actual,m.id_version_actual,"
                                + " m.estado,p.estado estado_proyecto"
                                + " from uml_colaborativo.modelo_uml m"
                                + " join uml_colaborativo.proyecto p on p.id=m.id_proyecto"
                                + " where m.id=:id",
                        Map.of("id", modelo),
                        (r, n) ->
                                new Cabecera(
                                        r.getObject("id", UUID.class),
                                        r.getObject("id_proyecto", UUID.class),
                                        r.getString("nombre"),
                                        r.getLong("revision_actual"),
                                        r.getObject("id_version_actual", UUID.class),
                                        r.getString("estado"),
                                        r.getString("estado_proyecto")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Modelo no disponible."));
    }

    public boolean permiso(UUID actor, UUID proyecto, String permiso) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select uml_colaborativo.tiene_permiso_proyecto(:actor,:proyecto,:permiso)",
                        Map.of("actor", actor, "proyecto", proyecto, "permiso", permiso),
                        Boolean.class));
    }

    /**
     * Paginación descendente por número de versión usando cursor. Sin cursor devuelve desde la
     * versión más reciente.
     */
    public PaginaVersiones listar(
            UUID modelo,
            Integer cursorDesde,
            int limite,
            UUID idAutor,
            LocalDate desde,
            LocalDate hasta) {
        var p =
                new MapSqlParameterSource()
                        .addValue("modelo", modelo)
                        .addValue("limite", limite + 1)
                        .addValue("cursor", cursorDesde)
                        .addValue("autor", idAutor)
                        .addValue("desde", desde == null ? null : desde.toString())
                        .addValue("hasta", hasta == null ? null : hasta.toString());
        List<ResumenVersion> filas =
                jdbc.query(
                        "select v.id,v.numero_version,v.revision,v.id_version_padre,"
                            + " v.id_version_restaurada,v.estado_validacion,v.resumen_cambio,"
                            + " v.creado_por id_autor,u.nombre_completo"
                            + " nombre_autor,v.creado_en::text, v.hash_instantanea_sha256 sha256"
                            + " from uml_colaborativo.version_modelo_uml v join"
                            + " uml_colaborativo.usuario u on u.id=v.creado_por where"
                            + " v.id_modelo=:modelo and (:cursor::int is null or v.numero_version <"
                            + " :cursor) and (:autor::uuid is null or v.creado_por=:autor) and"
                            + " (:desde::text is null or v.creado_en::date >= :desde::date) and"
                            + " (:hasta::text is null or v.creado_en::date <= :hasta::date) order"
                            + " by v.numero_version desc limit :limite",
                        p,
                        this::resumen);
        Integer siguiente = null;
        if (filas.size() > limite) {
            siguiente = filas.get(limite - 1).numeroVersion();
            filas = filas.subList(0, limite);
        }
        long total =
                Optional.ofNullable(
                                jdbc.queryForObject(
                                        "select count(*) from uml_colaborativo.version_modelo_uml v"
                                            + " where v.id_modelo=:modelo and (:autor::uuid is null"
                                            + " or v.creado_por=:autor) and (:desde::text is null"
                                            + " or v.creado_en::date >= :desde::date) and"
                                            + " (:hasta::text is null or v.creado_en::date <="
                                            + " :hasta::date)",
                                        p,
                                        Long.class))
                        .orElse(0L);
        return new PaginaVersiones(filas, total, siguiente);
    }

    /** Devuelve metadatos de una versión concreta o lanza 404 si no pertenece al modelo. */
    public ResumenVersion leerResumen(UUID modelo, UUID version) {
        return jdbc
                .query(
                        "select v.id,v.numero_version,v.revision,v.id_version_padre,"
                            + " v.id_version_restaurada,v.estado_validacion,v.resumen_cambio,"
                            + " v.creado_por id_autor,u.nombre_completo"
                            + " nombre_autor,v.creado_en::text, v.hash_instantanea_sha256 sha256"
                            + " from uml_colaborativo.version_modelo_uml v join"
                            + " uml_colaborativo.usuario u on u.id=v.creado_por where v.id=:version"
                            + " and v.id_modelo=:modelo",
                        Map.of("version", version, "modelo", modelo),
                        this::resumen)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Versión no disponible."));
    }

    /** Devuelve metadatos + snapshot JSONB ya parseado o lanza 422 si no es interpretable. */
    public DetalleVersion leerDetalle(UUID modelo, UUID version) {
        ResumenVersion resumen = leerResumen(modelo, version);
        String texto =
                jdbc.queryForObject(
                        "select instantanea::text from uml_colaborativo.version_modelo_uml"
                                + " where id=:version and id_modelo=:modelo",
                        Map.of("version", version, "modelo", modelo),
                        String.class);
        if (texto == null) throw new ApiException(422, "El snapshot de esta versión está vacío.");
        JsonNode nodo;
        try {
            nodo = json.readTree(texto);
        } catch (Exception e) {
            throw new ApiException(422, "El snapshot de esta versión no puede interpretarse.");
        }
        validador.validar(nodo, modelo, resumen.sha256(), texto);
        return new DetalleVersion(resumen, nodo);
    }

    /**
     * Devuelve el snapshot como JsonNode para comparación interna. Lanza 422 si no es interpretable
     * o válido.
     */
    public JsonNode snapshot(UUID modelo, UUID version) {
        ResumenVersion resumen = leerResumen(modelo, version);
        String texto =
                jdbc
                        .query(
                                "select instantanea::text from uml_colaborativo.version_modelo_uml"
                                        + " where id=:version and id_modelo=:modelo",
                                Map.of("version", version, "modelo", modelo),
                                (r, n) -> r.getString(1))
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new ApiException(404, "Versión no disponible."));
        JsonNode nodo;
        try {
            nodo = json.readTree(texto);
        } catch (Exception e) {
            throw new ApiException(422, "El snapshot de esta versión no puede interpretarse.");
        }
        validador.validar(nodo, modelo, resumen.sha256(), texto);
        return nodo;
    }

    /** Verifica que la versión pertenezca al modelo indicado. */
    public void verificarPertenencia(UUID modelo, UUID version) {
        boolean existe =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "select exists(select 1 from uml_colaborativo.version_modelo_uml"
                                        + " where id=:version and id_modelo=:modelo)",
                                Map.of("version", version, "modelo", modelo),
                                Boolean.class));
        if (!existe) throw new ApiException(400, "Las versiones deben pertenecer al mismo modelo.");
    }

    /** Cantidad de sesiones colaborativas activas con rol EDITOR. */
    public int sesionesEditoras(UUID modelo) {
        return sesionesEditoras(modelo, null);
    }

    /** Cantidad de sesiones colaborativas activas con rol EDITOR ajenas al actor especificado. */
    public int sesionesEditoras(UUID modelo, UUID actor) {
        String sql =
                "select count(*) from uml_colaborativo.sesion_edicion_modelo s"
                        + " join uml_colaborativo.participante_sesion_modelo ps"
                        + " on ps.id_sesion_edicion=s.id"
                        + " where s.id_modelo=:modelo and s.estado='ACTIVO'"
                        + " and ps.modo_participante='EDITOR' and ps.salida_en is null"
                        + " and ps.estado_presencia='EN_LINEA'"
                        + (actor != null ? " and ps.id_usuario != :actor" : "");
        Map<String, Object> params =
                actor != null ? Map.of("modelo", modelo, "actor", actor) : Map.of("modelo", modelo);
        return Optional.ofNullable(jdbc.queryForObject(sql, params, Integer.class)).orElse(0);
    }

    /** Cantidad de conflictos de edición abiertos. */
    public int conflictosAbiertos(UUID modelo) {
        return Optional.ofNullable(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.conflicto_edicion"
                                        + " where id_modelo=:modelo and estado='ABIERTO'",
                                Map.of("modelo", modelo),
                                Integer.class))
                .orElse(0);
    }

    /** Bloquea la fila del modelo para actualización, devuelve la revisión actual. */
    public long bloquearRevision(UUID modelo) {
        return Optional.ofNullable(
                        jdbc.queryForObject(
                                "select revision_actual from uml_colaborativo.modelo_uml"
                                        + " where id=:modelo for update",
                                Map.of("modelo", modelo),
                                Long.class))
                .orElseThrow(() -> new ApiException(404, "Modelo no disponible."));
    }

    /** Detecta una restauración ya confirmada con el mismo UUID idempotente dentro del modelo. */
    public Optional<ResultadoRestauracion> repeticion(UUID modelo, UUID idSolicitud) {
        return jdbc
                .query(
                        "select v.revision,v.id from uml_colaborativo.cambio_modelo c join"
                            + " uml_colaborativo.version_modelo_uml v   on v.id_modelo=c.id_modelo"
                            + " and v.revision=c.revision_aplicada where c.id_modelo=:modelo and"
                            + " c.id_cambio_cliente=:solicitud and c.tipo_operacion='RESTAURAR'  "
                            + " and c.estado='APLICADO'",
                        Map.of("modelo", modelo, "solicitud", idSolicitud),
                        (r, n) ->
                                new ResultadoRestauracion(
                                        r.getLong("revision"), r.getObject("id", UUID.class), true))
                .stream()
                .findFirst();
    }

    private ResumenVersion resumen(ResultSet r, int n) throws java.sql.SQLException {
        return new ResumenVersion(
                r.getObject("id", UUID.class),
                r.getInt("numero_version"),
                r.getLong("revision"),
                r.getObject("id_version_padre", UUID.class),
                r.getObject("id_version_restaurada", UUID.class),
                r.getString("estado_validacion"),
                r.getString("resumen_cambio"),
                r.getObject("id_autor", UUID.class),
                r.getString("nombre_autor"),
                r.getString("creado_en"),
                r.getString("sha256"));
    }
}
