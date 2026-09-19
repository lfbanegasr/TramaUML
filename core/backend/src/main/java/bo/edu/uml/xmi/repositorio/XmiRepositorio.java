package bo.edu.uml.xmi.repositorio;

import bo.edu.uml.compartido.ApiException;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Acceso JDBC puro a {@code intercambio_xmi} y {@code archivo_almacenado} para CU28. No importa
 * clases de otros módulos; cabecera duplicada intencionalmente para evitar dependencia circular con
 * el módulo modelado.
 */
@Repository
public class XmiRepositorio {

    private final NamedParameterJdbcTemplate jdbc;

    public XmiRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─── Cabecera del modelo ─────────────────────────────────────────────────

    public record CabeceraModelo(
            UUID id,
            UUID proyecto,
            String nombre,
            UUID idVersionActual,
            String estado,
            String estadoProyecto) {}

    public CabeceraModelo cabecera(UUID modelo) {
        return jdbc
                .query(
                        "select m.id,m.id_proyecto,m.nombre,m.id_version_actual,m.estado,"
                                + " p.estado estado_proyecto"
                                + " from uml_colaborativo.modelo_uml m"
                                + " join uml_colaborativo.proyecto p on p.id=m.id_proyecto"
                                + " where m.id=:id",
                        Map.of("id", modelo),
                        (r, n) ->
                                new CabeceraModelo(
                                        r.getObject("id", UUID.class),
                                        r.getObject("id_proyecto", UUID.class),
                                        r.getString("nombre"),
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

    // ─── Leer elementos del modelo ───────────────────────────────────────────

    public List<Map<String, Object>> leerClases(UUID modelo) {
        return jdbc.queryForList(
                "select c.id,c.nombre,c.descripcion,c.estereotipo,c.visibilidad,"
                        + " c.es_abstracto,c.es_persistente,c.id_paquete"
                        + " from uml_colaborativo.clase_uml c"
                        + " where c.id_modelo=:m and c.eliminado_en is null"
                        + " order by c.nombre",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerAtributos(UUID modelo) {
        return jdbc.queryForList(
                "select a.id,a.id_clase,a.nombre,a.tipo_uml,a.visibilidad,"
                        + " a.multiplicidad_minima,a.multiplicidad_maxima,"
                        + " a.es_identificador,a.es_estatico,a.es_unico,a.orden"
                        + " from uml_colaborativo.atributo_uml a"
                        + " join uml_colaborativo.clase_uml c on c.id=a.id_clase"
                        + " where c.id_modelo=:m and c.eliminado_en is null"
                        + " and a.eliminado_en is null"
                        + " order by a.orden,a.nombre",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerOperaciones(UUID modelo) {
        return jdbc.queryForList(
                "select o.id,o.id_clase,o.nombre,o.visibilidad,o.tipo_retorno,"
                        + " o.es_estatico,o.es_abstracto,o.orden"
                        + " from uml_colaborativo.operacion_uml o"
                        + " join uml_colaborativo.clase_uml c on c.id=o.id_clase"
                        + " where c.id_modelo=:m and c.eliminado_en is null"
                        + " and o.eliminado_en is null"
                        + " order by o.orden,o.nombre",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerParametros(UUID modelo) {
        return jdbc.queryForList(
                "select a.id,a.id_operacion,a.nombre,a.tipo_uml,a.direccion,"
                        + " a.es_obligatorio,a.orden"
                        + " from uml_colaborativo.parametro_uml a"
                        + " join uml_colaborativo.operacion_uml o on o.id=a.id_operacion"
                        + " join uml_colaborativo.clase_uml c on c.id=o.id_clase"
                        + " where c.id_modelo=:m and c.eliminado_en is null"
                        + " and o.eliminado_en is null and a.eliminado_en is null"
                        + " order by a.orden",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerRelaciones(UUID modelo) {
        return jdbc.queryForList(
                "select id,id_clase_origen,id_clase_destino,nombre,tipo_relacion,direccion,"
                        + " rol_origen,rol_destino,minimo_origen,maximo_origen,"
                        + " minimo_destino,maximo_destino"
                        + " from uml_colaborativo.relacion_uml"
                        + " where id_modelo=:m and eliminado_en is null"
                        + " order by id",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerPaquetes(UUID modelo) {
        return jdbc.queryForList(
                "select id,id_paquete_padre,nombre,descripcion"
                        + " from uml_colaborativo.paquete_uml"
                        + " where id_modelo=:m and eliminado_en is null"
                        + " order by nombre",
                Map.of("m", modelo));
    }

    // ─── Persistencia XMI ────────────────────────────────────────────────────

    /** Guarda el archivo XMI en {@code archivo_almacenado} y devuelve su UUID. */
    public UUID guardarArchivo(UUID idProyecto, UUID actor, String nombre, byte[] contenido) {
        UUID id = UUID.randomUUID();
        String hash = sha256(contenido);
        String clave = "xmi/" + id + "/" + nombre;
        jdbc.update(
                "insert into uml_colaborativo.archivo_almacenado"
                        + "(id,id_proyecto,cargado_por,categoria,proveedor_almacenamiento,"
                        + " clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,"
                        + " sha256,creado_en,contenido)"
                        + " values(:id,:proyecto,:actor,'SALIDA_XMI','BASE_DATOS',"
                        + " :clave,:nombre,'application/xml',:tam,"
                        + " :sha256,clock_timestamp(),:contenido)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("proyecto", idProyecto)
                        .addValue("actor", actor)
                        .addValue("clave", clave)
                        .addValue("nombre", nombre)
                        .addValue("tam", (long) contenido.length)
                        .addValue("sha256", hash)
                        .addValue("contenido", contenido));
        return id;
    }

    /** Registra el intercambio en {@code intercambio_xmi} y devuelve su UUID. */
    public UUID registrarIntercambio(
            UUID modelo, UUID version, UUID actor, UUID idArchivoSalida, int totalClases) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.intercambio_xmi"
                    + "(id,id_modelo,id_version_modelo,solicitado_por,direccion,variante_xmi,"
                    + " id_archivo_salida,estado,ambito,informe,iniciado_en,completado_en,creado_en)"
                    + " values(:id,:modelo,:version,:actor,'EXPORTAR','XMI_2.1',"
                    + " :archivo,'COMPLETADO', cast(:ambito as jsonb), cast(:informe as jsonb),"
                    + " clock_timestamp(),clock_timestamp(),clock_timestamp())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("modelo", modelo)
                        .addValue("version", version)
                        .addValue("actor", actor)
                        .addValue("archivo", idArchivoSalida)
                        .addValue("ambito", "{\"alcance\":\"MODELO_COMPLETO\"}")
                        .addValue(
                                "informe",
                                "{\"totalClases\":" + totalClases + ",\"variante\":\"XMI_2.1\"}"));
        return id;
    }

    /** Lee el contenido del archivo de salida de un intercambio dado. */
    public record ContenidoArchivo(String nombre, byte[] contenido, long tamanio) {}

    public ContenidoArchivo descargarArchivoPorIntercambio(UUID modelo, UUID idIntercambio) {
        var rows =
                jdbc.query(
                        "select a.nombre_original,a.contenido,a.tamano_bytes"
                                + " from uml_colaborativo.intercambio_xmi ix"
                                + " join uml_colaborativo.archivo_almacenado a"
                                + "   on a.id=ix.id_archivo_salida"
                                + " where ix.id=:ix and ix.id_modelo=:modelo",
                        Map.of("ix", idIntercambio, "modelo", modelo),
                        (r, n) ->
                                new ContenidoArchivo(
                                        r.getString("nombre_original"),
                                        r.getBytes("contenido"),
                                        r.getLong("tamano_bytes")));
        return rows.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Intercambio XMI no encontrado."));
    }

    private String sha256(byte[] data) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            var sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
