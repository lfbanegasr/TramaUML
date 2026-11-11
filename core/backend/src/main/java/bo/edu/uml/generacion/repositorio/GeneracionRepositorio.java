package bo.edu.uml.generacion.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.generacion.dto.GeneracionDto.ActualizarConfiguracion;
import bo.edu.uml.generacion.dto.GeneracionDto.Dependencia;
import bo.edu.uml.generacion.dto.GeneracionDto.MapeoTipo;
import bo.edu.uml.generacion.dto.GeneracionDto.SeleccionClase;
import bo.edu.uml.generacion.dto.GeneracionDto.VersionGeneracion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistencia JDBC de la configuración avanzada y sus artefactos generados. */
@Repository
public class GeneracionRepositorio {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public GeneracionRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record CabeceraModelo(
            UUID id,
            UUID proyecto,
            String nombre,
            long revision,
            UUID idVersionActual,
            String estado,
            String estadoProyecto) {}

    public record VersionSnapshot(VersionGeneracion resumen, JsonNode instantanea) {}

    public record ConfiguracionPersistida(
            UUID id,
            UUID idVersionModelo,
            String estado,
            String nombre,
            String nombreProyecto,
            String idGrupo,
            String idArtefacto,
            String paqueteBase,
            String versionInicial,
            String versionJava,
            String versionSpringBoot,
            String herramientaMigracion,
            boolean generarRepositorios,
            boolean generarServicios,
            boolean generarControladores,
            boolean generarDtos,
            boolean exponerRest,
            JsonNode plantilla) {}

    public record ClasePersistida(
            UUID idClase,
            boolean esPersistente,
            boolean exponerRest,
            String nombreTabla,
            String nombreEntidad) {}

    public record MapeoPersistencia(
            UUID idAtributo,
            UUID idRelacion,
            String nombreTabla,
            String nombreColumna,
            String tipoMapeo,
            String opcionesJson) {}

    public CabeceraModelo cabecera(UUID modelo) {
        return jdbc
                .query(
                        "select"
                            + " m.id,m.id_proyecto,m.nombre,m.revision_actual,m.id_version_actual,m.estado,p.estado"
                            + " estado_proyecto from uml_colaborativo.modelo_uml m join"
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

    public List<VersionGeneracion> listarVersiones(UUID modelo, UUID actual) {
        return jdbc.query(
                "select id,numero_version,revision,estado_validacion from "
                        + "uml_colaborativo.version_modelo_uml where id_modelo=:modelo "
                        + "order by numero_version desc",
                Map.of("modelo", modelo),
                (r, n) ->
                        new VersionGeneracion(
                                r.getObject("id", UUID.class),
                                r.getInt("numero_version"),
                                r.getLong("revision"),
                                r.getString("estado_validacion"),
                                r.getObject("id", UUID.class).equals(actual)));
    }

    public VersionSnapshot version(UUID modelo, UUID version, UUID actual) {
        return jdbc
                .query(
                        "select id,numero_version,revision,estado_validacion,instantanea::text "
                                + "from uml_colaborativo.version_modelo_uml "
                                + "where id=:version and id_modelo=:modelo",
                        Map.of("version", version, "modelo", modelo),
                        (r, n) -> {
                            try {
                                UUID id = r.getObject("id", UUID.class);
                                return new VersionSnapshot(
                                        new VersionGeneracion(
                                                id,
                                                r.getInt("numero_version"),
                                                r.getLong("revision"),
                                                r.getString("estado_validacion"),
                                                id.equals(actual)),
                                        json.readTree(r.getString("instantanea")));
                            } catch (Exception e) {
                                throw new ApiException(
                                        422,
                                        "La instantánea de la versión no puede interpretarse.");
                            }
                        })
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Versión no disponible."));
    }

    public Optional<ConfiguracionPersistida> leerConfiguracion(UUID modelo, UUID version) {
        return jdbc
                .query(
                        "select c.* from uml_colaborativo.configuracion_generacion_backend c join"
                            + " uml_colaborativo.version_modelo_uml v on v.id=c.id_version_modelo"
                            + " where v.id_modelo=:modelo and c.id_version_modelo=:version and"
                            + " c.estado not in ('ARCHIVADO','DESACTUALIZADO') order by c.creado_en"
                            + " desc limit 1",
                        Map.of("modelo", modelo, "version", version),
                        (r, n) -> configurar(r))
                .stream()
                .findFirst();
    }

    public ConfiguracionPersistida leerConfiguracion(UUID modelo, UUID id, boolean bloquear) {
        String sufijo = bloquear ? " for update of c" : "";
        return jdbc
                .query(
                        "select c.* from uml_colaborativo.configuracion_generacion_backend c join"
                            + " uml_colaborativo.version_modelo_uml v on v.id=c.id_version_modelo"
                            + " where v.id_modelo=:modelo and c.id=:id"
                                + sufijo,
                        Map.of("modelo", modelo, "id", id),
                        (r, n) -> configurar(r))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Configuración no disponible."));
    }

    public List<ClasePersistida> clasesConfiguracion(UUID id) {
        return jdbc.query(
                "select id_clase,es_persistente,exponer_rest,nombre_tabla,nombre_entidad from"
                    + " uml_colaborativo.clase_configuracion_backend where id_configuracion=:id",
                Map.of("id", id),
                (r, n) ->
                        new ClasePersistida(
                                r.getObject("id_clase", UUID.class),
                                r.getBoolean("es_persistente"),
                                r.getBoolean("exponer_rest"),
                                r.getString("nombre_tabla"),
                                r.getString("nombre_entidad")));
    }

    public List<MapeoTipo> mapeosTipo(UUID id) {
        return jdbc.query(
                "select tipo_uml,tipo_java,tipo_postgresql,clase_conversor from"
                        + " uml_colaborativo.mapeo_tipo_backend where id_configuracion=:id order by"
                        + " tipo_uml",
                Map.of("id", id),
                (r, n) ->
                        new MapeoTipo(
                                r.getString("tipo_uml"),
                                r.getString("tipo_java"),
                                r.getString("tipo_postgresql"),
                                r.getString("clase_conversor")));
    }

    public List<Dependencia> dependencias(UUID id) {
        return jdbc.query(
                "select id_grupo,id_artefacto,version,ambito_dependencia,es_obligatorio from "
                        + "uml_colaborativo.dependencia_backend where id_configuracion=:id "
                        + "order by es_obligatorio desc,id_grupo,id_artefacto",
                Map.of("id", id),
                (r, n) ->
                        new Dependencia(
                                r.getString("id_grupo"),
                                r.getString("id_artefacto"),
                                r.getString("version"),
                                r.getString("ambito_dependencia"),
                                r.getBoolean("es_obligatorio")));
    }

    public UUID guardarConfiguracion(
            UUID version,
            UUID actor,
            ActualizarConfiguracion cfg,
            List<SeleccionClase> clases,
            List<MapeoTipo> mapeos,
            List<Dependencia> dependencias,
            List<MapeoPersistencia> persistencia,
            String plantillaJson,
            String estado) {
        jdbc.update(
                "update uml_colaborativo.configuracion_generacion_backend set"
                    + " estado='DESACTUALIZADO',actualizado_en=clock_timestamp() where"
                    + " id_version_modelo=:version and estado in ('BORRADOR','VALIDO','INVALIDO')"
                    + " and not exists(select 1 from uml_colaborativo.generacion_backend g where"
                    + " g.id_configuracion=configuracion_generacion_backend.id)",
                Map.of("version", version));

        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.configuracion_generacion_backend"
                    + "(id,id_version_modelo,nombre,nombre_proyecto,id_grupo,id_artefacto,"
                    + "paquete_base,version_inicial,version_java,version_spring_boot,"
                    + "motor_base_datos,herramienta_migracion,generar_repositorios,"
                    + "generar_servicios,generar_controladores,generar_dtos,exponer_rest,plantilla_conexion,estado,creado_por)"
                    + " values(:id,:version,:nombre,"
                    + ":proyecto,:grupo,:artefacto,:paquete,:inicial,:java,:spring,'POSTGRESQL',:migracion,:repos,:servicios,:controladores,:dtos,:rest,cast(:plantilla"
                    + " as jsonb),:estado,:actor)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("version", version)
                        .addValue("nombre", cfg.nombre())
                        .addValue("proyecto", cfg.nombreProyecto())
                        .addValue("grupo", cfg.idGrupo())
                        .addValue("artefacto", cfg.idArtefacto())
                        .addValue("paquete", cfg.paqueteBase())
                        .addValue("inicial", cfg.versionInicial())
                        .addValue("java", cfg.versionJava())
                        .addValue("spring", cfg.versionSpringBoot())
                        .addValue("migracion", cfg.herramientaMigracion())
                        .addValue("repos", cfg.generarRepositorios())
                        .addValue("servicios", cfg.generarServicios())
                        .addValue("controladores", cfg.generarControladores())
                        .addValue("dtos", cfg.generarDtos())
                        .addValue("rest", cfg.exponerRest())
                        .addValue("plantilla", plantillaJson)
                        .addValue("estado", estado)
                        .addValue("actor", actor));

        for (SeleccionClase clase : clases) {
            if (!clase.seleccionada()) continue;
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.clase_configuracion_backend(id_configuracion,id_clase,es_persistente,exponer_rest,nombre_tabla,nombre_entidad)"
                        + " values(:cfg,:clase,:persistente,:rest,:tabla,:entidad)",
                    new MapSqlParameterSource()
                            .addValue("cfg", id)
                            .addValue("clase", clase.idClase())
                            .addValue("persistente", clase.esPersistente())
                            .addValue("rest", clase.exponerRest())
                            .addValue("tabla", clase.nombreTabla())
                            .addValue("entidad", clase.nombreEntidad()));
        }
        for (MapeoTipo mapeo : mapeos) {
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.mapeo_tipo_backend(id,id_configuracion,tipo_uml,tipo_java,tipo_postgresql,clase_conversor)"
                        + " values(gen_random_uuid(),:cfg,:uml,:java,:sql,:conversor)",
                    new MapSqlParameterSource()
                            .addValue("cfg", id)
                            .addValue("uml", mapeo.tipoUml())
                            .addValue("java", mapeo.tipoJava())
                            .addValue("sql", mapeo.tipoPostgresql())
                            .addValue("conversor", mapeo.claseConversor()));
        }
        for (Dependencia dependencia : dependencias) {
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.dependencia_backend(id,id_configuracion,id_grupo,id_artefacto,version,ambito_dependencia,es_obligatorio)"
                        + " values(gen_random_uuid(),:cfg,:grupo,:artefacto,:version,:ambito,:obligatoria)",
                    new MapSqlParameterSource()
                            .addValue("cfg", id)
                            .addValue("grupo", dependencia.idGrupo())
                            .addValue("artefacto", dependencia.idArtefacto())
                            .addValue("version", dependencia.version())
                            .addValue("ambito", dependencia.ambito())
                            .addValue("obligatoria", dependencia.obligatoria()));
        }
        for (MapeoPersistencia mapeo : persistencia) {
            jdbc.update(
                    "insert into uml_colaborativo.mapeo_persistencia_backend"
                        + "(id,id_configuracion,id_atributo_uml,id_relacion_uml,nombre_tabla,nombre_columna,tipo_mapeo,opciones_mapeo)"
                        + " values(gen_random_uuid(),:cfg,:atributo,:relacion,:tabla,:columna,:tipo,cast(:opciones"
                        + " as jsonb))",
                    new MapSqlParameterSource()
                            .addValue("cfg", id)
                            .addValue("atributo", mapeo.idAtributo())
                            .addValue("relacion", mapeo.idRelacion())
                            .addValue("tabla", mapeo.nombreTabla())
                            .addValue("columna", mapeo.nombreColumna())
                            .addValue("tipo", mapeo.tipoMapeo())
                            .addValue("opciones", mapeo.opcionesJson()));
        }
        return id;
    }

    public UUID guardarZip(UUID proyecto, UUID actor, String nombre, byte[] contenido) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.archivo_almacenado"
                        + "(id,id_proyecto,cargado_por,categoria,proveedor_almacenamiento,"
                        + "clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,"
                        + "sha256,contenido) values(:id,:proyecto,:actor,'BACKEND_GENERADO',"
                        + "'BASE_DATOS',:clave,:nombre,'application/zip',:tam,:sha,:contenido)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("proyecto", proyecto)
                        .addValue("actor", actor)
                        .addValue("clave", "backend/" + id + "/" + nombre)
                        .addValue("nombre", nombre)
                        .addValue("tam", (long) contenido.length)
                        .addValue("sha", sha256(contenido))
                        .addValue("contenido", contenido));
        return id;
    }

    public UUID registrarGeneracion(
            UUID configuracion,
            UUID actor,
            UUID archivo,
            int totalClases,
            int totalOperaciones,
            int totalArchivos) {
        UUID id = UUID.randomUUID();
        String informe;
        try {
            informe =
                    json.writeValueAsString(
                            Map.of(
                                    "totalClases", totalClases,
                                    "totalOperaciones", totalOperaciones,
                                    "totalArchivos", totalArchivos,
                                    "motor", "MotorGeneracionSpringBoot",
                                    "version", "0.14.0"));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el informe de generación.", e);
        }
        Integer numero =
                jdbc.queryForObject(
                        "select coalesce(max(numero_compilacion),0)+1 from "
                                + "uml_colaborativo.generacion_backend where id_configuracion=:cfg",
                        Map.of("cfg", configuracion),
                        Integer.class);
        jdbc.update(
                "insert into uml_colaborativo.generacion_backend"
                    + "(id,id_configuracion,solicitado_por,numero_compilacion,estado,id_archivo_salida,informe_transformacion,iniciado_en,completado_en)"
                    + " values(:id,:cfg,:actor,:numero,'GENERADO',:archivo,cast(:informe as jsonb),"
                    + "clock_timestamp(),clock_timestamp())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("cfg", configuracion)
                        .addValue("actor", actor)
                        .addValue("numero", numero)
                        .addValue("archivo", archivo)
                        .addValue("informe", informe));
        return id;
    }

    public record ContenidoArchivo(String nombre, byte[] contenido) {}

    public ContenidoArchivo descargarPorGeneracion(UUID modelo, UUID generacion) {
        return jdbc
                .query(
                        "select a.nombre_original,a.contenido from"
                            + " uml_colaborativo.generacion_backend g join"
                            + " uml_colaborativo.configuracion_generacion_backend c on"
                            + " c.id=g.id_configuracion join uml_colaborativo.version_modelo_uml v"
                            + " on v.id=c.id_version_modelo join"
                            + " uml_colaborativo.archivo_almacenado a on a.id=g.id_archivo_salida"
                            + " where g.id=:id and v.id_modelo=:modelo",
                        Map.of("id", generacion, "modelo", modelo),
                        (r, n) ->
                                new ContenidoArchivo(
                                        r.getString("nombre_original"), r.getBytes("contenido")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Generación no encontrada."));
    }

    private ConfiguracionPersistida configurar(java.sql.ResultSet r) throws java.sql.SQLException {
        try {
            return new ConfiguracionPersistida(
                    r.getObject("id", UUID.class),
                    r.getObject("id_version_modelo", UUID.class),
                    r.getString("estado"),
                    r.getString("nombre"),
                    r.getString("nombre_proyecto"),
                    r.getString("id_grupo"),
                    r.getString("id_artefacto"),
                    r.getString("paquete_base"),
                    r.getString("version_inicial"),
                    r.getString("version_java"),
                    r.getString("version_spring_boot"),
                    r.getString("herramienta_migracion"),
                    r.getBoolean("generar_repositorios"),
                    r.getBoolean("generar_servicios"),
                    r.getBoolean("generar_controladores"),
                    r.getBoolean("generar_dtos"),
                    r.getBoolean("exponer_rest"),
                    json.readTree(r.getString("plantilla_conexion")));
        } catch (JsonProcessingException e) {
            throw new ApiException(422, "La configuración guardada no puede interpretarse.");
        }
    }

    private String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no está disponible.", e);
        }
    }
}
