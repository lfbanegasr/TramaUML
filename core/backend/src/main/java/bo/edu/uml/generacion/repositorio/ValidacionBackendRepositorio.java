package bo.edu.uml.generacion.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.generacion.dto.ValidacionBackendDto.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Repository
public class ValidacionBackendRepositorio {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ValidacionBackendRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Artefacto(
            UUID id,
            UUID configuracion,
            UUID modelo,
            UUID proyecto,
            UUID archivo,
            String nombre,
            String sha256,
            byte[] contenido) {}

    public Artefacto artefacto(UUID modelo, UUID id, boolean bloquear) {
        return jdbc
                .query(
                        """
                        select g.id,g.id_configuracion,v.id_modelo,m.id_proyecto,a.id archivo,
                               a.nombre_original,a.sha256,a.contenido
                        from uml_colaborativo.generacion_backend g
                        join uml_colaborativo.configuracion_generacion_backend c on c.id=g.id_configuracion
                        join uml_colaborativo.version_modelo_uml v on v.id=c.id_version_modelo
                        join uml_colaborativo.modelo_uml m on m.id=v.id_modelo
                        join uml_colaborativo.archivo_almacenado a on a.id=g.id_archivo_salida
                        where g.id=:id and v.id_modelo=:modelo and g.estado='GENERADO'
                        """
                                + (bloquear ? " for update of g" : ""),
                        Map.of("id", id, "modelo", modelo),
                        (r, n) ->
                                new Artefacto(
                                        id,
                                        r.getObject("id_configuracion", UUID.class),
                                        modelo,
                                        r.getObject("id_proyecto", UUID.class),
                                        r.getObject("archivo", UUID.class),
                                        r.getString("nombre_original"),
                                        r.getString("sha256"),
                                        r.getBytes("contenido")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Generación no disponible."));
    }

    public List<GeneracionDisponible> listar(UUID modelo, boolean validar) {
        return jdbc.query(
                """
                select g.id,g.id_configuracion,v.id version,v.numero_version,a.nombre_original,g.completado_en
                from uml_colaborativo.generacion_backend g
                join uml_colaborativo.configuracion_generacion_backend c on c.id=g.id_configuracion
                join uml_colaborativo.version_modelo_uml v on v.id=c.id_version_modelo
                join uml_colaborativo.archivo_almacenado a on a.id=g.id_archivo_salida
                where v.id_modelo=:modelo and g.estado='GENERADO'
                order by g.completado_en desc,g.id desc limit 30
                """,
                Map.of("modelo", modelo),
                (r, n) -> {
                    UUID id = r.getObject("id", UUID.class);
                    return new GeneracionDisponible(
                            id,
                            r.getObject("id_configuracion", UUID.class),
                            r.getObject("version", UUID.class),
                            r.getInt("numero_version"),
                            r.getString("nombre_original"),
                            r.getTimestamp("completado_en").toInstant(),
                            ultimo(id),
                            validar);
                });
    }

    public Informe ultimo(UUID generacion) {
        var ids =
                jdbc.queryForList(
                        "select id from uml_colaborativo.validacion_backend where id_generacion=:id"
                            + " order by iniciado_en desc,id desc limit 1",
                        Map.of("id", generacion),
                        UUID.class);
        return ids.isEmpty() ? null : informe(ids.getFirst());
    }

    public Informe informe(UUID id) {
        return jdbc.queryForObject(
                """
                select v.*,a.contenido informe from uml_colaborativo.validacion_backend v
                left join uml_colaborativo.archivo_almacenado a on a.id=v.id_archivo_informe where v.id=:id
                """,
                Map.of("id", id),
                (r, n) -> {
                    String sha = null;
                    String log = "";
                    try {
                        byte[] contenido = r.getBytes("informe");
                        if (contenido != null) {
                            var datos = json.readTree(contenido);
                            sha = datos.path("sha256").asText(null);
                            log = datos.path("registro").asText("");
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Informe ilegible.", e);
                    }
                    String estado = r.getString("estado");
                    return new Informe(
                            id,
                            r.getObject("id_generacion", UUID.class),
                            estado,
                            r.getInt("comprobaciones_aprobadas"),
                            r.getInt("cantidad_advertencias"),
                            r.getInt("cantidad_errores"),
                            r.getTimestamp("iniciado_en").toInstant(),
                            r.getTimestamp("completado_en") == null
                                    ? null
                                    : r.getTimestamp("completado_en").toInstant(),
                            sha,
                            log,
                            hallazgos(id),
                            Set.of("VALIDO", "VALIDO_CON_ADVERTENCIAS").contains(estado));
                });
    }

    private List<Hallazgo> hallazgos(UUID id) {
        return jdbc.query(
                "select * from uml_colaborativo.hallazgo_validacion_backend where id_validacion=:id"
                    + " and componente is distinct from 'TRABAJO_TOMADO' order by creado_en,id",
                Map.of("id", id),
                (r, n) -> {
                    try {
                        return new Hallazgo(
                                r.getString("severidad"),
                                r.getString("tipo_comprobacion"),
                                r.getString("componente"),
                                r.getString("mensaje"),
                                json.readValue(
                                        r.getString("detalle"),
                                        new TypeReference<Map<String, Object>>() {}));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    public UUID crear(UUID generacion, UUID actor) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.validacion_backend(id,id_generacion,solicitado_por)"
                    + " values(:id,:g,:a)",
                Map.of("id", id, "g", generacion, "a", actor));
        return id;
    }

    public List<UUID> pendientes() {
        return jdbc.queryForList(
                """
                select v.id from uml_colaborativo.validacion_backend v where v.estado='EN_EJECUCION'
                and not exists(select 1 from uml_colaborativo.hallazgo_validacion_backend h
                    where h.id_validacion=v.id and h.componente='TRABAJO_TOMADO')
                order by v.iniciado_en,v.id limit 1
                """,
                Map.of(),
                UUID.class);
    }

    public record Trabajo(UUID id, UUID actor, Artefacto artefacto) {}

    public Trabajo tomar(UUID id) {
        var filas =
                jdbc.queryForList(
                        "select id_generacion,solicitado_por,estado from"
                            + " uml_colaborativo.validacion_backend where id=:id for update",
                        Map.of("id", id));
        if (filas.isEmpty() || !"EN_EJECUCION".equals(filas.getFirst().get("estado"))) return null;
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists(select 1 from uml_colaborativo.hallazgo_validacion_backend "
                                + "where id_validacion=:id and componente='TRABAJO_TOMADO')",
                        Map.of("id", id),
                        Boolean.class))) return null;
        hallazgo(
                id,
                new Hallazgo(
                        "INFORMACION",
                        "COMPILACION",
                        "TRABAJO_TOMADO",
                        "Trabajo reservado.",
                        Map.of()));
        UUID g = (UUID) filas.getFirst().get("id_generacion");
        UUID modelo =
                jdbc.queryForObject(
                        """
                        select v.id_modelo from uml_colaborativo.generacion_backend g
                        join uml_colaborativo.configuracion_generacion_backend c on c.id=g.id_configuracion
                        join uml_colaborativo.version_modelo_uml v on v.id=c.id_version_modelo where g.id=:id
                        """,
                        Map.of("id", g),
                        UUID.class);
        return new Trabajo(
                id, (UUID) filas.getFirst().get("solicitado_por"), artefacto(modelo, g, false));
    }

    public void expirar() {
        jdbc.update(
                "update uml_colaborativo.validacion_backend set"
                    + " estado='INCOMPLETO',completado_en=clock_timestamp() where"
                    + " estado='EN_EJECUCION' and iniciado_en < clock_timestamp()-interval '10"
                    + " minutes'",
                Map.of());
    }

    public void completar(Trabajo trabajo, Resultado resultado) {
        String estado =
                jdbc.queryForObject(
                        "select estado from uml_colaborativo.validacion_backend where id=:id for"
                            + " update",
                        Map.of("id", trabajo.id()),
                        String.class);
        if (!"EN_EJECUCION".equals(estado)) return;
        UUID informe =
                guardarArchivo(
                        trabajo,
                        "INFORME_VALIDACION",
                        "informe.json",
                        serializar(resultado).getBytes(StandardCharsets.UTF_8),
                        "application/json");
        UUID registro =
                guardarArchivo(
                        trabajo,
                        "REGISTRO_COMPILACION",
                        "compilacion.log",
                        resultado.registro().getBytes(StandardCharsets.UTF_8),
                        "text/plain");
        for (Hallazgo h : resultado.hallazgos()) hallazgo(trabajo.id(), h);
        jdbc.update(
                """
                update uml_colaborativo.validacion_backend set estado=:estado,comprobaciones_aprobadas=:aprobadas,
                cantidad_advertencias=:advertencias,cantidad_errores=:errores,id_archivo_informe=:informe,
                completado_en=clock_timestamp() where id=:id
                """,
                Map.of(
                        "estado",
                        resultado.estado(),
                        "aprobadas",
                        resultado.aprobadas(),
                        "advertencias",
                        resultado.hallazgos().stream()
                                .filter(h -> "ADVERTENCIA".equals(h.severidad()))
                                .count(),
                        "errores",
                        resultado.hallazgos().stream()
                                .filter(h -> "ERROR".equals(h.severidad()))
                                .count(),
                        "informe",
                        informe,
                        "id",
                        trabajo.id()));
        jdbc.update(
                "update uml_colaborativo.generacion_backend set id_archivo_registro=:registro where"
                    + " id=:id",
                Map.of("registro", registro, "id", trabajo.artefacto().id()));
    }

    private UUID guardarArchivo(
            Trabajo t, String categoria, String nombre, byte[] contenido, String tipo) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into uml_colaborativo.archivo_almacenado(id,id_proyecto,cargado_por,categoria,
                proveedor_almacenamiento,clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,sha256,contenido)
                values(:id,:proyecto,:actor,:categoria,'BASE_DATOS',:clave,:nombre,:tipo,:tam,:sha,:contenido)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("proyecto", t.artefacto().proyecto())
                        .addValue("actor", t.actor())
                        .addValue("categoria", categoria)
                        .addValue("clave", "validacion-backend/" + id)
                        .addValue("nombre", nombre)
                        .addValue("tipo", tipo)
                        .addValue("tam", (long) contenido.length)
                        .addValue("sha", sha256(contenido))
                        .addValue("contenido", contenido));
        return id;
    }

    private void hallazgo(UUID id, Hallazgo h) {
        jdbc.update(
                """
                insert into uml_colaborativo.hallazgo_validacion_backend(id_validacion,severidad,tipo_comprobacion,componente,mensaje,detalle)
                values(:id,:s,:t,:c,:m,cast(:d as jsonb))
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("s", h.severidad())
                        .addValue("t", h.tipoComprobacion())
                        .addValue("c", h.componente())
                        .addValue("m", h.mensaje())
                        .addValue("d", serializar(h.detalle())));
    }

    public void registrarDescarga(Artefacto a, UUID actor) {
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.descarga_artefacto(id_generacion,id_archivo,descargado_por)"
                    + " values(:g,:f,:a)",
                Map.of("g", a.id(), "f", a.archivo(), "a", actor));
    }

    private String serializar(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
