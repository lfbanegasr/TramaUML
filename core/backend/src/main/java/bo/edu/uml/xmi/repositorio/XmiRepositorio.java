package bo.edu.uml.xmi.repositorio;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper json;

    public XmiRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ─── Cabecera del modelo ─────────────────────────────────────────────────

    public record CabeceraModelo(
            UUID id,
            UUID proyecto,
            String nombre,
            long revision,
            UUID idVersionActual,
            String estado,
            String estadoProyecto) {}

    public CabeceraModelo cabecera(UUID modelo) {
        return jdbc
                .query(
                        "select"
                            + " m.id,m.id_proyecto,m.nombre,m.revision_actual,m.id_version_actual,m.estado,"
                            + " p.estado estado_proyecto from uml_colaborativo.modelo_uml m join"
                            + " uml_colaborativo.proyecto p on p.id=m.id_proyecto where m.id=:id",
                        Map.of("id", modelo),
                        (r, n) ->
                                new CabeceraModelo(
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
                    + " a.es_identificador,a.es_estatico,a.es_unico,a.es_ordenado,a.coleccion_unica,a.valor_predeterminado,a.restricciones_json,a.orden"
                    + " from uml_colaborativo.atributo_uml a join uml_colaborativo.clase_uml c on"
                    + " c.id=a.id_clase where c.id_modelo=:m and c.eliminado_en is null and"
                    + " a.eliminado_en is null order by a.orden,a.nombre",
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
                        + " a.es_obligatorio,a.valor_predeterminado,a.orden"
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

    public List<Map<String, Object>> leerPosicionesClases(UUID modelo) {
        return jdbc.queryForList(
                "select l.id_clase, l.x, l.y, l.ancho, l.alto, l.orden_capa, l.estilo"
                        + " from uml_colaborativo.posicion_clase_uml l"
                        + " join uml_colaborativo.clase_uml c on c.id = l.id_clase"
                        + " where c.id_modelo = :m and c.eliminado_en is null"
                        + " order by l.id_clase",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerPosicionesRelaciones(UUID modelo) {
        return jdbc.queryForList(
                "select l.id_relacion, l.puntos_intermedios, l.etiqueta_x, l.etiqueta_y, l.estilo"
                        + " from uml_colaborativo.posicion_relacion_uml l"
                        + " join uml_colaborativo.relacion_uml r on r.id = l.id_relacion"
                        + " where r.id_modelo = :m and r.eliminado_en is null"
                        + " order by l.id_relacion",
                Map.of("m", modelo));
    }

    /** Lee una instantánea inmutable y verifica que la versión pertenezca al modelo. */
    public JsonNode leerInstantanea(UUID modelo, UUID version) {
        String contenido =
                jdbc
                        .query(
                                "select instantanea::text from uml_colaborativo.version_modelo_uml"
                                        + " where id=:version and id_modelo=:modelo",
                                Map.of("version", version, "modelo", modelo),
                                (r, n) -> r.getString(1))
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new ApiException(404, "Versión no disponible."));
        try {
            return json.readTree(contenido);
        } catch (Exception e) {
            throw new ApiException(422, "La instantánea seleccionada no puede interpretarse.");
        }
    }

    public long revisionVersion(UUID modelo, UUID version) {
        return jdbc
                .query(
                        "select revision from uml_colaborativo.version_modelo_uml"
                                + " where id=:version and id_modelo=:modelo",
                        Map.of("version", version, "modelo", modelo),
                        (r, n) -> r.getLong("revision"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Versión no disponible."));
    }

    // ─── Persistencia XMI ────────────────────────────────────────────────────

    /** Guarda el archivo XMI en {@code archivo_almacenado} y devuelve su UUID. */
    public UUID guardarArchivo(UUID idProyecto, UUID actor, String nombre, byte[] contenido) {
        return guardarArchivo(
                idProyecto, actor, "SALIDA_XMI", "application/xml", nombre, contenido);
    }

    /** Guarda un archivo exportado en {@code archivo_almacenado} con tipo MIME explícito. */
    public UUID guardarArchivo(
            UUID idProyecto, UUID actor, String nombre, String tipoContenido, byte[] contenido) {
        return guardarArchivo(idProyecto, actor, "SALIDA_XMI", tipoContenido, nombre, contenido);
    }

    /** Guarda el archivo XMI original de una importación. */
    public UUID guardarArchivoEntrada(
            UUID idProyecto, UUID actor, String nombre, String tipoContenido, byte[] contenido) {
        return guardarArchivo(idProyecto, actor, "ENTRADA_XMI", tipoContenido, nombre, contenido);
    }

    private UUID guardarArchivo(
            UUID idProyecto,
            UUID actor,
            String categoria,
            String tipoContenido,
            String nombre,
            byte[] contenido) {
        UUID id = UUID.randomUUID();
        String hash = sha256(contenido);
        String clave = "xmi/" + categoria.toLowerCase(Locale.ROOT) + "/" + id + "/" + nombre;
        jdbc.update(
                "insert into uml_colaborativo.archivo_almacenado"
                        + "(id,id_proyecto,cargado_por,categoria,proveedor_almacenamiento,"
                        + " clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,"
                        + " sha256,creado_en,contenido)"
                        + " values(:id,:proyecto,:actor,:categoria,'BASE_DATOS',"
                        + " :clave,:nombre,:tipo,:tam,"
                        + " :sha256,clock_timestamp(),:contenido)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("proyecto", idProyecto)
                        .addValue("actor", actor)
                        .addValue("categoria", categoria)
                        .addValue("clave", clave)
                        .addValue("nombre", nombre)
                        .addValue("tipo", tipoContenido)
                        .addValue("tam", (long) contenido.length)
                        .addValue("sha256", hash)
                        .addValue("contenido", contenido));
        return id;
    }

    /** Registra el intercambio en {@code intercambio_xmi} y devuelve su UUID. */
    public UUID registrarIntercambio(
            UUID modelo,
            UUID version,
            UUID actor,
            UUID idArchivoSalida,
            String ambito,
            String informe) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.intercambio_xmi"
                    + "(id,id_modelo,id_version_modelo,solicitado_por,direccion,variante_xmi,"
                    + " id_archivo_salida,estado,ambito,informe,iniciado_en,completado_en,creado_en)"
                    + " values(:id,:modelo,:version,:actor,'EXPORTAR',cast(:informe as"
                    + " jsonb)->>'variante', :archivo,'COMPLETADO', cast(:ambito as jsonb),"
                    + " cast(:informe as jsonb),"
                    + " clock_timestamp(),clock_timestamp(),clock_timestamp())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("modelo", modelo)
                        .addValue("version", version)
                        .addValue("actor", actor)
                        .addValue("archivo", idArchivoSalida)
                        .addValue("ambito", ambito)
                        .addValue("informe", informe));
        return id;
    }

    public record IntercambioImportacion(
            UUID id,
            UUID modelo,
            UUID archivoEntrada,
            String nombreArchivo,
            String estado,
            String estrategia,
            String variante,
            String versionEnterpriseArchitect,
            String ambito,
            String informe,
            String detalleError,
            UUID versionImportada) {}

    /** Registra la carga antes de analizarla para conservar un fallo trazable. */
    public UUID registrarImportacion(
            UUID modelo,
            UUID version,
            UUID actor,
            UUID archivoEntrada,
            UUID idSolicitud,
            long revisionBase,
            String estrategia) {
        UUID id = UUID.randomUUID();
        String ambito =
                escribir(
                        Map.of(
                                "alcance",
                                "MODELO_COMPLETO",
                                "idSolicitud",
                                idSolicitud,
                                "revisionBase",
                                revisionBase));
        jdbc.update(
                "insert into uml_colaborativo.intercambio_xmi"
                    + "(id,id_modelo,id_version_modelo,solicitado_por,direccion,variante_xmi,"
                    + " estrategia_fusion,ambito,id_archivo_entrada,estado,informe,iniciado_en,creado_en)"
                    + " values(:id,:modelo,:version,:actor,'IMPORTAR','XMI_2.1',:estrategia,"
                    + " cast(:ambito as jsonb),:archivo,'ANALIZANDO','{}'::jsonb,"
                    + " clock_timestamp(),clock_timestamp())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("modelo", modelo)
                        .addValue("version", version)
                        .addValue("actor", actor)
                        .addValue("estrategia", estrategia)
                        .addValue("ambito", ambito)
                        .addValue("archivo", archivoEntrada));
        return id;
    }

    public Optional<UUID> buscarImportacion(
            UUID modelo, UUID actor, UUID idSolicitud, String estrategia) {
        return jdbc
                .query(
                        "select id from uml_colaborativo.intercambio_xmi where id_modelo=:modelo"
                            + " and solicitado_por=:actor and direccion='IMPORTAR' and"
                            + " estrategia_fusion=:estrategia and ambito->>'idSolicitud'=:solicitud"
                            + " order by creado_en desc limit 1",
                        Map.of(
                                "modelo",
                                modelo,
                                "actor",
                                actor,
                                "estrategia",
                                estrategia,
                                "solicitud",
                                idSolicitud.toString()),
                        (r, n) -> r.getObject("id", UUID.class))
                .stream()
                .findFirst();
    }

    public void registrarAnalisis(
            UUID intercambio, String versionEnterpriseArchitect, String informe) {
        int actualizadas =
                jdbc.update(
                        "update uml_colaborativo.intercambio_xmi set"
                            + " version_enterprise_architect=:ea,estado='LISTO_PARA_REVISION',variante_xmi=coalesce(cast(:informe"
                            + " as jsonb)->>'varianteXmi',variante_xmi), informe=cast(:informe as"
                            + " jsonb),detalle_error=null where id=:id and direccion='IMPORTAR' and"
                            + " estado='ANALIZANDO'",
                        new MapSqlParameterSource()
                                .addValue("id", intercambio)
                                .addValue("ea", versionEnterpriseArchitect)
                                .addValue("informe", informe));
        if (actualizadas != 1)
            throw new ApiException(409, "La importación ya no admite el resultado del análisis.");
    }

    public void registrarFallo(UUID intercambio, String detalle) {
        jdbc.update(
                "update uml_colaborativo.intercambio_xmi set"
                    + " estado='FALLIDO',detalle_error=:detalle, completado_en=clock_timestamp()"
                    + " where id=:id and estado='ANALIZANDO'",
                Map.of("id", intercambio, "detalle", detalle));
    }

    public IntercambioImportacion importacion(UUID modelo, UUID intercambio) {
        return consultarImportacion(modelo, intercambio, false);
    }

    public IntercambioImportacion bloquearImportacion(UUID modelo, UUID intercambio) {
        return consultarImportacion(modelo, intercambio, true);
    }

    private IntercambioImportacion consultarImportacion(
            UUID modelo, UUID intercambio, boolean bloquear) {
        String sql =
                "select ix.id,ix.id_modelo,ix.id_archivo_entrada,a.nombre_original,ix.estado,"
                    + " ix.estrategia_fusion,ix.variante_xmi,ix.version_enterprise_architect,"
                    + " ix.ambito::text,ix.informe::text,ix.detalle_error,ix.id_version_importada"
                    + " from uml_colaborativo.intercambio_xmi ix join"
                    + " uml_colaborativo.archivo_almacenado a on a.id=ix.id_archivo_entrada where"
                    + " ix.id=:id and ix.id_modelo=:modelo and ix.direccion='IMPORTAR'"
                        + (bloquear ? " for update of ix" : "");
        return jdbc
                .query(sql, Map.of("id", intercambio, "modelo", modelo), this::importacion)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Importación XMI no encontrada."));
    }

    private IntercambioImportacion importacion(java.sql.ResultSet r, int n)
            throws java.sql.SQLException {
        return new IntercambioImportacion(
                r.getObject("id", UUID.class),
                r.getObject("id_modelo", UUID.class),
                r.getObject("id_archivo_entrada", UUID.class),
                r.getString("nombre_original"),
                r.getString("estado"),
                r.getString("estrategia_fusion"),
                r.getString("variante_xmi"),
                r.getString("version_enterprise_architect"),
                r.getString("ambito"),
                r.getString("informe"),
                r.getString("detalle_error"),
                r.getObject("id_version_importada", UUID.class));
    }

    public void completarImportacion(
            UUID intercambio, UUID versionImportada, UUID idSolicitudAplicacion) {
        int actualizadas =
                jdbc.update(
                        "update uml_colaborativo.intercambio_xmi set estado='COMPLETADO',"
                            + " id_version_importada=:version,completado_en=clock_timestamp(),"
                            + " informe=jsonb_set(informe,'{idSolicitudAplicacion}',to_jsonb(:solicitud::text),true)"
                            + " where id=:id and estado='LISTO_PARA_REVISION'",
                        Map.of(
                                "id",
                                intercambio,
                                "version",
                                versionImportada,
                                "solicitud",
                                idSolicitudAplicacion.toString()));
        if (actualizadas != 1) throw new ApiException(409, "La importación ya fue procesada.");
    }

    public void cancelarImportacion(UUID intercambio, String motivo) {
        int actualizadas =
                jdbc.update(
                        "update uml_colaborativo.intercambio_xmi set estado='CANCELADO',"
                                + " detalle_error=:motivo,completado_en=clock_timestamp()"
                                + " where id=:id and estado='LISTO_PARA_REVISION'",
                        new MapSqlParameterSource()
                                .addValue("id", intercambio)
                                .addValue("motivo", motivo));
        if (actualizadas != 1) throw new ApiException(409, "La importación ya fue procesada.");
    }

    public Optional<UUID> sesionEditable(UUID modelo, UUID actor) {
        return jdbc
                .query(
                        "select s.id from uml_colaborativo.sesion_edicion_modelo s join"
                            + " uml_colaborativo.participante_sesion_modelo p on"
                            + " p.id_sesion_edicion=s.id where s.id_modelo=:modelo and"
                            + " s.estado='ACTIVO' and p.id_usuario=:actor and"
                            + " p.modo_participante='EDITOR' and p.salida_en is null and"
                            + " p.estado_presencia='EN_LINEA' order by p.entrada_en desc limit 1",
                        Map.of("modelo", modelo, "actor", actor),
                        (r, n) -> r.getObject("id", UUID.class))
                .stream()
                .findFirst();
    }

    private String escribir(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron serializar los metadatos XMI.", e);
        }
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
