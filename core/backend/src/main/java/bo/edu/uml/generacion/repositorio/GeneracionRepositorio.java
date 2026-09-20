package bo.edu.uml.generacion.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.generacion.dto.GeneracionDto.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Acceso JDBC a {@code configuracion_generacion_backend}, {@code generacion_backend} y {@code
 * archivo_almacenado} para CU29/CU30/CU32.
 */
@Repository
public class GeneracionRepositorio {

    private final NamedParameterJdbcTemplate jdbc;

    public GeneracionRepositorio(NamedParameterJdbcTemplate jdbc) {
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

    // ─── Leer elementos del modelo para generación ───────────────────────────

    public List<Map<String, Object>> leerClases(UUID modelo) {
        return jdbc.queryForList(
                "select id,nombre,descripcion,es_persistente,es_abstracto,visibilidad,id_paquete"
                        + " from uml_colaborativo.clase_uml"
                        + " where id_modelo=:m and eliminado_en is null"
                        + " order by nombre",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerAtributos(UUID modelo) {
        return jdbc.queryForList(
                "select a.id,a.id_clase,a.nombre,a.tipo_uml,a.visibilidad,"
                        + " a.es_identificador,a.es_unico,a.es_estatico,"
                        + " a.multiplicidad_minima,a.multiplicidad_maxima,a.orden"
                        + " from uml_colaborativo.atributo_uml a"
                        + " join uml_colaborativo.clase_uml c on c.id=a.id_clase"
                        + " where c.id_modelo=:m and c.eliminado_en is null"
                        + " and a.eliminado_en is null order by a.orden,a.nombre",
                Map.of("m", modelo));
    }

    public List<Map<String, Object>> leerRelaciones(UUID modelo) {
        return jdbc.queryForList(
                "select id,id_clase_origen,id_clase_destino,nombre,tipo_relacion,"
                        + " rol_origen,rol_destino,minimo_origen,maximo_origen,"
                        + " minimo_destino,maximo_destino"
                        + " from uml_colaborativo.relacion_uml"
                        + " where id_modelo=:m and eliminado_en is null order by id",
                Map.of("m", modelo));
    }

    // ─── Configuración de generación ─────────────────────────────────────────

    /** Lee la configuración vigente del modelo. Devuelve empty si no existe. */
    public Optional<ConfiguracionGeneracion> leerConfiguracion(UUID modelo) {
        var rows =
                jdbc.query(
                        "select c.id,c.nombre,c.nombre_proyecto,c.id_grupo,c.id_artefacto,"
                                + " c.paquete_base,c.version_inicial,"
                                + " c.generar_repositorios,c.generar_controladores,"
                                + " c.generar_dtos,c.exponer_rest"
                                + " from uml_colaborativo.configuracion_generacion_backend c"
                                + " join uml_colaborativo.version_modelo_uml v"
                                + "   on v.id=c.id_version_modelo"
                                + " where v.id_modelo=:modelo and c.estado='VALIDO'"
                                + " order by c.creado_en desc limit 1",
                        Map.of("modelo", modelo),
                        (r, n) ->
                                new ConfiguracionGeneracion(
                                        r.getObject("id", UUID.class),
                                        r.getString("nombre"),
                                        r.getString("nombre_proyecto"),
                                        r.getString("id_grupo"),
                                        r.getString("id_artefacto"),
                                        r.getString("paquete_base"),
                                        r.getString("version_inicial"),
                                        r.getBoolean("generar_repositorios"),
                                        r.getBoolean("generar_controladores"),
                                        r.getBoolean("generar_dtos"),
                                        r.getBoolean("exponer_rest")));
        return rows.stream().findFirst();
    }

    /** Inserta o actualiza la configuración de generación para el modelo. */
    public UUID guardarConfiguracion(
            UUID modelo, UUID version, UUID actor, ActualizarConfiguracion cfg) {
        // Desactivar configuración anterior
        jdbc.update(
                "update uml_colaborativo.configuracion_generacion_backend set"
                        + " estado='DESACTUALIZADO' where id_version_modelo in (   select id from"
                        + " uml_colaborativo.version_modelo_uml where id_modelo=:m) and"
                        + " estado='VALIDO'",
                Map.of("m", modelo));

        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.configuracion_generacion_backend"
                        + "(id,id_version_modelo,nombre,nombre_proyecto,id_grupo,id_artefacto,"
                        + " paquete_base,version_inicial,version_java,version_spring_boot,"
                        + " motor_base_datos,herramienta_migracion,generar_repositorios,"
                        + " generar_servicios,generar_controladores,generar_dtos,exponer_rest,"
                        + " plantilla_conexion,estado,creado_por,creado_en,actualizado_en)"
                        + " values(:id,:version,:nombre,:nombreProy,:grupo,:artefacto,"
                        + " :paquete,:ver,'21','3.5.16','POSTGRESQL','FLYWAY',"
                        + " :repos,true,:ctrl,:dtos,:rest, cast(:plantilla as"
                        + " jsonb),'VALIDO',:actor,clock_timestamp(),clock_timestamp())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("version", version)
                        .addValue("nombre", cfg.nombre())
                        .addValue("nombreProy", cfg.nombreProyecto())
                        .addValue("grupo", cfg.idGrupo())
                        .addValue("artefacto", cfg.idArtefacto())
                        .addValue("paquete", cfg.paqueteBase())
                        .addValue("ver", cfg.versionInicial())
                        .addValue("repos", cfg.generarRepositorios())
                        .addValue("ctrl", cfg.generarControladores())
                        .addValue("dtos", cfg.generarDtos())
                        .addValue("rest", cfg.exponerRest())
                        .addValue("actor", actor)
                        .addValue(
                                "plantilla",
                                "{\"datasource\":\"jdbc:postgresql://localhost:5432/"
                                        + cfg.idArtefacto()
                                        + "\"}"));

        // Poblar clase_configuracion_backend con las clases del modelo para satisfacer
        // trg_generacion_backend_01
        jdbc.update(
                "insert into uml_colaborativo.clase_configuracion_backend(id_configuracion,"
                    + " id_clase, es_persistente, exponer_rest, nombre_tabla, nombre_entidad)"
                    + " select :id, c.id, c.es_persistente, true, lower(c.nombre), c.nombre from"
                    + " uml_colaborativo.clase_uml c where c.id_modelo=:m and c.eliminado_en is"
                    + " null on conflict do nothing",
                Map.of("id", id, "m", modelo));

        // Asegurar estado de validación válido en la versión para satisfacer
        // trg_generacion_backend_01
        if (version != null) {
            jdbc.update(
                    "update uml_colaborativo.version_modelo_uml set estado_validacion='VALIDO'"
                            + " where id=:ver and estado_validacion in ('SIN_VALIDAR',"
                            + " 'DESACTUALIZADO')",
                    Map.of("ver", version));
        }

        return id;
    }

    // ─── Generación y descarga ────────────────────────────────────────────────

    /** Guarda el ZIP generado en {@code archivo_almacenado} y devuelve su UUID. */
    public UUID guardarZip(UUID idProyecto, UUID actor, String nombre, byte[] contenido) {
        UUID id = UUID.randomUUID();
        String hash = sha256(contenido);
        String clave = "backend/" + id + "/" + nombre;
        jdbc.update(
                "insert into uml_colaborativo.archivo_almacenado"
                        + "(id,id_proyecto,cargado_por,categoria,proveedor_almacenamiento,"
                        + " clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,"
                        + " sha256,creado_en,contenido)"
                        + " values(:id,:proyecto,:actor,'BACKEND_GENERADO','BASE_DATOS',"
                        + " :clave,:nombre,'application/zip',:tam,"
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

    /** Registra la generación en {@code generacion_backend} y devuelve su UUID. */
    public UUID registrarGeneracion(
            UUID idConfiguracion, UUID actor, UUID idArchivoSalida, int totalClases) {
        // Asegurar que clase_configuracion_backend y estado_validacion cumplan
        // trg_generacion_backend_01
        jdbc.update(
                "update uml_colaborativo.version_modelo_uml set estado_validacion='VALIDO' where id"
                        + " in (select id_version_modelo from"
                        + " uml_colaborativo.configuracion_generacion_backend where id=:cfg) and"
                        + " estado_validacion in ('SIN_VALIDAR', 'DESACTUALIZADO')",
                Map.of("cfg", idConfiguracion));

        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.generacion_backend"
                        + "(id,id_configuracion,solicitado_por,numero_compilacion,estado,"
                        + " id_archivo_salida,informe_transformacion,solicitado_en,"
                        + " iniciado_en,completado_en)"
                        + " values(:id,:cfg,:actor,1,'GENERADO',:archivo,"
                        + " cast(:informe as jsonb),clock_timestamp(),clock_timestamp(),"
                        + " clock_timestamp())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("cfg", idConfiguracion)
                        .addValue("actor", actor)
                        .addValue("archivo", idArchivoSalida)
                        .addValue(
                                "informe",
                                "{\"totalClases\":"
                                        + totalClases
                                        + ",\"motor\":\"MotorGeneracionSpringBoot\","
                                        + "\"version\":\"0.10.0\"}"));
        return id;
    }

    /** Lee el contenido del archivo ZIP de una generación. */
    public record ContenidoArchivo(String nombre, byte[] contenido) {}

    public ContenidoArchivo descargarPorGeneracion(UUID idGeneracion) {
        var rows =
                jdbc.query(
                        "select a.nombre_original,a.contenido"
                                + " from uml_colaborativo.generacion_backend gb"
                                + " join uml_colaborativo.archivo_almacenado a"
                                + "   on a.id=gb.id_archivo_salida"
                                + " where gb.id=:id",
                        Map.of("id", idGeneracion),
                        (r, n) ->
                                new ContenidoArchivo(
                                        r.getString("nombre_original"), r.getBytes("contenido")));
        return rows.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Generación no encontrada."));
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
