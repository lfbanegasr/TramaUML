package bo.edu.uml.servicios.repositorio;

import static bo.edu.uml.servicios.dto.ServicioPlataformaDto.*;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class ServicioPlataformaRepositorio {
    private static final String SELECT_SERVICIO =
            """
            select s.id,s.nombre,s.proveedor,s.tipo_servicio,s.url_servicio,s.configuracion,s.estado,
                   s.creado_por,s.creado_en,s.actualizado_en,
                   exists(select 1 from uml_colaborativo.secreto_servicio ss
                          where ss.id_servicio=s.id and ss.retirado_en is null) tiene_credencial,
                   c.estado comprobacion_estado,c.latencia_ms,c.detalle comprobacion_detalle,
                   c.comprobado_en
            from uml_colaborativo.servicio_plataforma s
            left join lateral (
              select estado,latencia_ms,detalle,comprobado_en
              from uml_colaborativo.comprobacion_servicio
              where id_servicio=s.id order by comprobado_en desc,id desc limit 1
            ) c on true
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ServicioPlataformaRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void bloquearEscrituras() {
        jdbc.queryForObject("select pg_advisory_xact_lock(1920260920)", Map.of(), Object.class);
    }

    public PaginaServicios listar(
            String nombre, String proveedor, String tipo, String estado, int pagina, int tamano) {
        var p =
                new MapSqlParameterSource()
                        .addValue("nombre", nombre)
                        .addValue("proveedor", proveedor)
                        .addValue("tipo", tipo)
                        .addValue("estado", estado)
                        .addValue("limite", tamano)
                        .addValue("desplazamiento", pagina * tamano);
        String filtro =
                """
                where (:nombre='' or strpos(lower(s.nombre),lower(:nombre))>0)
                  and (:proveedor='' or s.proveedor=:proveedor)
                  and (:tipo='' or s.tipo_servicio=:tipo)
                  and (:estado='' or s.estado=:estado)
                """;
        List<Servicio> servicios =
                jdbc.query(
                        SELECT_SERVICIO
                                + filtro
                                + " order by s.nombre,s.id limit :limite offset :desplazamiento",
                        p,
                        this::servicio);
        long total =
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.servicio_plataforma s "
                                        + filtro,
                                p,
                                Long.class));
        return new PaginaServicios(servicios, pagina, tamano, total);
    }

    public Servicio detalle(UUID id) {
        List<Servicio> filas =
                jdbc.query(SELECT_SERVICIO + " where s.id=:id", Map.of("id", id), this::servicio);
        if (filas.isEmpty()) throw new ApiException(404, "Servicio no disponible.");
        return filas.getFirst();
    }

    public Optional<Servicio> activo(String tipo) {
        List<Servicio> filas =
                jdbc.query(
                        SELECT_SERVICIO
                                + " where s.tipo_servicio=:tipo and s.estado='ACTIVO' order by"
                                + " s.actualizado_en desc,s.id limit 1",
                        Map.of("tipo", tipo),
                        this::servicio);
        return filas.stream().findFirst();
    }

    public boolean existeNombre(UUID excluir, String nombre) {
        return existe(
                "select exists(select 1 from uml_colaborativo.servicio_plataforma where"
                        + " lower(nombre)=lower(:nombre) and (cast(:excluir as uuid) is null or"
                        + " id<>cast(:excluir as uuid)))",
                new MapSqlParameterSource()
                        .addValue("nombre", nombre)
                        .addValue("excluir", excluir));
    }

    public boolean existeEquivalente(
            UUID excluir, String proveedor, String tipo, String url, JsonNode configuracion) {
        return existe(
                """
                select exists(select 1 from uml_colaborativo.servicio_plataforma
                where proveedor=:proveedor and tipo_servicio=:tipo
                  and coalesce(url_servicio,'')=coalesce(:url,'')
                  and configuracion=cast(:configuracion as jsonb)
                  and (cast(:excluir as uuid) is null or id<>cast(:excluir as uuid)))
                """,
                new MapSqlParameterSource()
                        .addValue("proveedor", proveedor)
                        .addValue("tipo", tipo)
                        .addValue("url", url)
                        .addValue("configuracion", configuracion.toString())
                        .addValue("excluir", excluir));
    }

    public void insertar(
            UUID id,
            UUID actor,
            String nombre,
            String proveedor,
            String tipo,
            String url,
            JsonNode configuracion,
            String estado) {
        jdbc.update(
                """
                insert into uml_colaborativo.servicio_plataforma
                  (id,nombre,proveedor,tipo_servicio,url_servicio,configuracion,estado,creado_por)
                values(:id,:nombre,:proveedor,:tipo,:url,cast(:configuracion as jsonb),:estado,:actor)
                """,
                parametros(id, nombre, proveedor, tipo, url, configuracion, estado)
                        .addValue("actor", actor));
    }

    public void actualizar(
            UUID id,
            String nombre,
            String proveedor,
            String tipo,
            String url,
            JsonNode configuracion,
            String estado,
            String revision) {
        int filas =
                jdbc.update(
                        """
                        update uml_colaborativo.servicio_plataforma
                        set nombre=:nombre,proveedor=:proveedor,tipo_servicio=:tipo,
                            url_servicio=:url,configuracion=cast(:configuracion as jsonb),estado=:estado
                        where id=:id and actualizado_en=cast(:revision as timestamptz)
                        """,
                        parametros(id, nombre, proveedor, tipo, url, configuracion, estado)
                                .addValue("revision", revision));
        exigirUna(filas);
    }

    public void cambiarEstado(UUID id, String estado, String revision) {
        int filas =
                jdbc.update(
                        "update uml_colaborativo.servicio_plataforma set estado=:estado where"
                                + " id=:id and actualizado_en=cast(:revision as timestamptz)",
                        Map.of("id", id, "estado", estado, "revision", revision));
        exigirUna(filas);
    }

    public void actualizarEstadoComprobado(UUID id, String estado) {
        jdbc.update(
                "update uml_colaborativo.servicio_plataforma set estado=:estado where id=:id",
                Map.of("id", id, "estado", estado));
    }

    public void guardarComprobacion(
            UUID servicio, UUID actor, String estado, Integer latencia, String detalle) {
        jdbc.update(
                """
                insert into uml_colaborativo.comprobacion_servicio
                  (id_servicio,estado,latencia_ms,detalle,comprobado_por)
                values(:servicio,:estado,:latencia,:detalle,:actor)
                """,
                new MapSqlParameterSource()
                        .addValue("servicio", servicio)
                        .addValue("estado", estado)
                        .addValue("latencia", latencia)
                        .addValue("detalle", detalle)
                        .addValue("actor", actor));
    }

    public Optional<Secreto> secretoActivo(UUID servicio) {
        List<Secreto> filas =
                jdbc.query(
                        """
                        select id,contenido_cifrado,algoritmo_cifrado,version_clave
                        from uml_colaborativo.secreto_servicio
                        where id_servicio=:servicio and retirado_en is null
                        """,
                        Map.of("servicio", servicio),
                        (r, n) ->
                                new Secreto(
                                        r.getObject("id", UUID.class),
                                        r.getBytes("contenido_cifrado"),
                                        r.getString("algoritmo_cifrado"),
                                        r.getString("version_clave")));
        return filas.stream().findFirst();
    }

    public void reemplazarSecreto(
            UUID servicio, byte[] contenido, String algoritmo, String versionClave) {
        retirarSecreto(servicio, true);
        jdbc.update(
                """
                insert into uml_colaborativo.secreto_servicio
                  (id_servicio,contenido_cifrado,algoritmo_cifrado,version_clave)
                values(:servicio,:contenido,:algoritmo,:version)
                """,
                Map.of(
                        "servicio",
                        servicio,
                        "contenido",
                        contenido,
                        "algoritmo",
                        algoritmo,
                        "version",
                        versionClave));
    }

    public void retirarSecreto(UUID servicio, boolean rotacion) {
        jdbc.update(
                """
                update uml_colaborativo.secreto_servicio
                set retirado_en=clock_timestamp(),
                    rotado_en=case when :rotacion then clock_timestamp() else rotado_en end
                where id_servicio=:servicio and retirado_en is null
                """,
                Map.of("servicio", servicio, "rotacion", rotacion));
    }

    public boolean tieneSolicitudEnCurso(UUID servicio) {
        return existe(
                "select exists(select 1 from uml_colaborativo.solicitud_inteligente where"
                        + " id_servicio=:servicio and estado='PROCESANDO')",
                new MapSqlParameterSource("servicio", servicio));
    }

    public record Secreto(UUID id, byte[] contenido, String algoritmo, String versionClave) {}

    private Servicio servicio(ResultSet r, int numero) throws SQLException {
        Instant comprobado = instante(r, "comprobado_en");
        Comprobacion ultima =
                comprobado == null
                        ? null
                        : new Comprobacion(
                                r.getString("comprobacion_estado"),
                                (Integer) r.getObject("latencia_ms"),
                                r.getString("comprobacion_detalle"),
                                comprobado);
        Instant actualizado = instante(r, "actualizado_en");
        return new Servicio(
                r.getObject("id", UUID.class),
                r.getString("nombre"),
                r.getString("proveedor"),
                r.getString("tipo_servicio"),
                r.getString("url_servicio"),
                leerJson(r.getString("configuracion")),
                r.getString("estado"),
                r.getBoolean("tiene_credencial"),
                r.getObject("creado_por", UUID.class),
                instante(r, "creado_en"),
                actualizado,
                actualizado.toString(),
                ultima);
    }

    private JsonNode leerJson(String valor) {
        try {
            return json.readTree(valor);
        } catch (Exception e) {
            throw new IllegalStateException("Configuración JSON persistida inválida.", e);
        }
    }

    private Instant instante(ResultSet r, String columna) throws SQLException {
        var tiempo = r.getTimestamp(columna);
        return tiempo == null ? null : tiempo.toInstant();
    }

    private MapSqlParameterSource parametros(
            UUID id,
            String nombre,
            String proveedor,
            String tipo,
            String url,
            JsonNode configuracion,
            String estado) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", nombre)
                .addValue("proveedor", proveedor)
                .addValue("tipo", tipo)
                .addValue("url", url)
                .addValue("configuracion", configuracion.toString())
                .addValue("estado", estado);
    }

    private boolean existe(String sql, MapSqlParameterSource parametros) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, parametros, Boolean.class));
    }

    private void exigirUna(int filas) {
        if (filas != 1)
            throw new ApiException(
                    409, "El servicio cambió. Recarga sus datos antes de confirmar.");
    }
}
