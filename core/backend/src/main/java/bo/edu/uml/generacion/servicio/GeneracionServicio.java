package bo.edu.uml.generacion.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.generacion.dto.GeneracionDto.*;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.CabeceraModelo;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.ClasePersistida;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.ConfiguracionPersistida;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.ContenidoArchivo;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.MapeoPersistencia;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.VersionSnapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import bo.edu.uml.validacion.servicio.ValidacionServicio;
import bo.edu.uml.validacion.servicio.ProcesarValidacion;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

/** Orquesta la configuración avanzada sobre una versión UML congelada. */
@Service
public class GeneracionServicio {

    private static final String JAVA_DEFECTO = "21";
    private static final String SPRING_DEFECTO = "3.5.16";
    private final GeneracionRepositorio repo;
    private final MotorGeneracionSpringBoot motor;
    private final ObjectMapper json;
    private final ValidacionBackendServicio validaciones;
    private final ValidacionServicio validacionModelo;
    private final ProcesarValidacion procesadorModelo;
    private final TransactionTemplate transacciones;

    public GeneracionServicio(
            GeneracionRepositorio repo,
            MotorGeneracionSpringBoot motor,
            ObjectMapper json,
            ValidacionBackendServicio validaciones,
            ValidacionServicio validacionModelo,
            ProcesarValidacion procesadorModelo,
            PlatformTransactionManager gestorTransacciones) {
        this.repo = repo;
        this.motor = motor;
        this.json = json;
        this.validaciones = validaciones;
        this.validacionModelo = validacionModelo;
        this.procesadorModelo = procesadorModelo;
        this.transacciones = new TransactionTemplate(gestorTransacciones);
    }

    @Transactional(readOnly = true)
    public ConfiguracionGeneracion leerConfiguracion(Identidad actor, UUID modelo, UUID idVersion) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "CONFIGURAR_BACKEND", "configurar la generación de backend");
        UUID version = idVersion == null ? cabecera.idVersionActual() : idVersion;
        VersionSnapshot datos = repo.version(modelo, version, cabecera.idVersionActual());
        return vista(
                cabecera,
                datos,
                repo.leerConfiguracion(modelo, version).orElse(null),
                repo.listarVersiones(modelo, cabecera.idVersionActual()));
    }

    @Transactional
    public ConfiguracionGeneracion guardarConfiguracion(
            Identidad actor, UUID modelo, ActualizarConfiguracion entrada) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "CONFIGURAR_BACKEND", "configurar la generación de backend");
        UUID version =
                entrada.idVersionModelo() == null
                        ? cabecera.idVersionActual()
                        : entrada.idVersionModelo();
        VersionSnapshot datos = repo.version(modelo, version, cabecera.idVersionActual());
        ActualizarConfiguracion cfg = normalizar(entrada);
        Preparacion preparacion = preparar(datos.instantanea(), cfg);
        List<MensajeConfiguracion> mensajes =
                diagnosticar(
                        datos.instantanea(),
                        datos.resumen(),
                        preparacion.clases(),
                        preparacion.mapeos(),
                        cfg);
        String estado = tieneErrores(mensajes) ? "INVALIDO" : "VALIDO";
        UUID id =
                repo.guardarConfiguracion(
                        version,
                        actor.id(),
                        cfg,
                        preparacion.clases(),
                        preparacion.mapeos(),
                        preparacion.dependencias(),
                        preparacion.persistencia(),
                        plantilla(preparacion.clases(), cfg.nombre()),
                        estado);
        ConfiguracionPersistida guardada = repo.leerConfiguracion(modelo, id, false);
        return vista(
                cabecera,
                datos,
                guardada,
                repo.listarVersiones(modelo, cabecera.idVersionActual()));
    }

    public ResultadoGeneracion generar(
            Identidad actor, UUID modelo, SolicitudGeneracion solicitud) {
        // La comprobación de compatibilidad se confirma antes de abrir la transacción del ZIP.
        // Nunca se marca una versión como válida sin evaluarla con el motor semántico real.
        var preparada = transacciones.execute(s -> {
            CabeceraModelo c = repo.cabecera(modelo);
            autorizar(actor, c, "GENERAR_BACKEND", "generar el backend");
            ConfiguracionPersistida configuracion = solicitud == null || solicitud.idConfiguracion() == null
                    ? repo.leerConfiguracion(modelo, c.idVersionActual())
                        .orElseThrow(() -> new ApiException(422, "Guarda una configuración antes de generar."))
                    : repo.leerConfiguracion(modelo, solicitud.idConfiguracion(), false);
            if (!"VALIDO".equals(configuracion.estado()))
                throw new ApiException(422, "Revisa la configuración del backend antes de generar.");
            var version = repo.version(modelo, configuracion.idVersionModelo(), c.idVersionActual());
            UUID tarea = null;
            if (repo.requiereValidacionLegada()
                    && !Set.of("VALIDO", "VALIDO_CON_ADVERTENCIAS").contains(version.resumen().estadoValidacion())) {
                var aceptada = (Map<?, ?>) validacionModelo.solicitar(actor, modelo, configuracion.idVersionModelo());
                tarea = (UUID) aceptada.get("id");
            }
            return new PreparacionLegada(configuracion.id(), tarea);
        });
        if (preparada.validacion() != null) procesadorModelo.procesar(preparada.validacion());
        return transacciones.execute(s -> generarConfirmado(actor, modelo,
                new SolicitudGeneracion(preparada.configuracion())));
    }

    private record PreparacionLegada(UUID configuracion, UUID validacion) {}

    private ResultadoGeneracion generarConfirmado(
            Identidad actor, UUID modelo, SolicitudGeneracion solicitud) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "GENERAR_BACKEND", "generar el backend");
        UUID idConfiguracion = solicitud == null ? null : solicitud.idConfiguracion();
        ConfiguracionPersistida cfg;
        if (idConfiguracion == null) {
            cfg =
                    repo.leerConfiguracion(modelo, cabecera.idVersionActual())
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    422,
                                                    "Guarda una configuración válida antes de"
                                                            + " generar."));
        } else {
            cfg = repo.leerConfiguracion(modelo, idConfiguracion, true);
        }
        if (!"VALIDO".equals(cfg.estado()))
            throw new ApiException(422, "La configuración seleccionada no es válida.");

        VersionSnapshot version =
                repo.version(modelo, cfg.idVersionModelo(), cabecera.idVersionActual());
        if (repo.requiereValidacionLegada()
                && !Set.of("VALIDO", "VALIDO_CON_ADVERTENCIAS").contains(version.resumen().estadoValidacion()))
            throw new ApiException(422,
                    "La comprobación automática detectó errores UML en esta versión."
                    + " Abre el informe de validación para ver los errores concretos.");

        List<ClasePersistida> seleccion = repo.clasesConfiguracion(cfg.id());
        if (seleccion.isEmpty())
            throw new ApiException(422, "La configuración no contiene clases seleccionadas.");
        DatosMotor datos = datosMotor(version.instantanea(), seleccion, cfg.plantilla());
        if (datos.clases().isEmpty())
            throw new ApiException(422, "La selección no contiene clases generables.");

        var resultadoMotor =
                motor.generar(
                        cfg.idGrupo(),
                        cfg.idArtefacto(),
                        cfg.versionInicial(),
                        cfg.paqueteBase(),
                        new MotorGeneracionSpringBoot.OpcionesGeneracion(
                                cfg.versionJava(),
                                cfg.versionSpringBoot(),
                                cfg.herramientaMigracion(),
                                cfg.generarRepositorios(),
                                cfg.generarServicios(),
                                cfg.generarControladores(),
                                cfg.generarDtos(),
                                cfg.exponerRest(),
                                repo.dependencias(cfg.id()),
                                repo.mapeosTipo(cfg.id())),
                        datos.clases(),
                        datos.atributos(),
                        datos.relaciones(),
                        datos.operaciones(),
                        datos.parametros());
        byte[] zip = resultadoMotor.contenido();
        int totalArchivos = resultadoMotor.totalArchivos();
        String nombreZip = cfg.idArtefacto() + "-v" + version.resumen().numeroVersion() + ".zip";
        UUID archivo = repo.guardarZip(cabecera.proyecto(), actor.id(), nombreZip, zip);
        UUID generacion =
                repo.registrarGeneracion(
                        cfg.id(),
                        actor.id(),
                        archivo,
                        datos.clases().size(),
                        datos.operaciones().size(),
                        totalArchivos);
        return new ResultadoGeneracion(
                generacion,
                cfg.id(),
                cfg.idVersionModelo(),
                archivo,
                nombreZip,
                datos.clases().size(),
                datos.operaciones().size(),
                totalArchivos);
    }

    @Transactional
    public ContenidoArchivo descargar(Identidad actor, UUID modelo, UUID generacion) {
        return validaciones.descargar(actor, modelo, generacion);
    }

    private ConfiguracionGeneracion vista(
            CabeceraModelo cabecera,
            VersionSnapshot version,
            ConfiguracionPersistida cfg,
            List<VersionGeneracion> versiones) {
        List<Map<String, Object>> clasesSnapshot = mapas(version.instantanea(), "clases");
        List<Map<String, Object>> operacionesSnapshot = mapas(version.instantanea(), "operaciones");
        Map<UUID, ClasePersistida> guardadas =
                cfg == null
                        ? Map.of()
                        : repo.clasesConfiguracion(cfg.id()).stream()
                                .collect(
                                        Collectors.toMap(
                                                ClasePersistida::idClase, Function.identity()));
        Set<UUID> operacionesGuardadas =
                operacionesSeleccionadas(cfg == null ? null : cfg.plantilla());
        Map<UUID, List<Map<String, Object>>> operacionesPorClase =
                operacionesSnapshot.stream()
                        .collect(
                                Collectors.groupingBy(
                                        o -> uuid(o.get("id_clase")),
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        Map<UUID, List<UUID>> dependientes = new LinkedHashMap<>();
        for (Map<String, Object> relacion : mapas(version.instantanea(), "relaciones")) {
            if ("DEPENDENCIA".equals(relacion.get("tipo_relacion"))) continue;
            UUID origen = uuid(relacion.get("id_clase_origen"));
            UUID destino = uuid(relacion.get("id_clase_destino"));
            if ((cfg == null || guardadas.containsKey(origen)) && !origen.equals(destino))
                dependientes.computeIfAbsent(destino, k -> new ArrayList<>()).add(origen);
        }
        List<ClaseGeneracion> clases = new ArrayList<>();
        for (Map<String, Object> clase : clasesSnapshot) {
            UUID id = uuid(clase.get("id"));
            ClasePersistida guardada = guardadas.get(id);
            boolean seleccionada = cfg == null || guardada != null;
            List<OperacionGeneracion> operaciones =
                    operacionesPorClase.getOrDefault(id, List.of()).stream()
                            .map(
                                    o ->
                                            new OperacionGeneracion(
                                                    uuid(o.get("id")),
                                                    texto(o.get("nombre")),
                                                    textoNullable(o.get("tipo_retorno")),
                                                    cfg == null
                                                            || !seleccionOperacionesExplicita(
                                                                    cfg.plantilla())
                                                            || operacionesGuardadas.contains(
                                                                    uuid(o.get("id")))))
                            .toList();
            String nombre = texto(clase.get("nombre"));
            clases.add(
                    new ClaseGeneracion(
                            id,
                            nombre,
                            seleccionada,
                            !dependientes.getOrDefault(id, List.of()).isEmpty(),
                            List.copyOf(dependientes.getOrDefault(id, List.of())),
                            guardada == null
                                    ? bool(clase.get("es_persistente"))
                                    : guardada.esPersistente(),
                            guardada == null || guardada.exponerRest(),
                            guardada == null ? snake(nombre) : guardada.nombreTabla(),
                            guardada == null ? nombreClase(nombre) : guardada.nombreEntidad(),
                            operaciones));
        }

        List<MapeoTipo> mapeos = cfg == null ? mapeosDefecto() : repo.mapeosTipo(cfg.id());
        List<Dependencia> dependencias =
                cfg == null
                        ? dependenciasDefecto(true, true, true, "FLYWAY")
                        : repo.dependencias(cfg.id());
        ActualizarConfiguracion actual =
                cfg == null ? defaults(cabecera, version.resumen().id()) : desdePersistida(cfg);
        List<MensajeConfiguracion> mensajes =
                diagnosticar(
                        version.instantanea(),
                        version.resumen(),
                        seleccionDesdeVista(clases),
                        mapeos,
                        actual);
        return new ConfiguracionGeneracion(
                cfg == null ? null : cfg.id(),
                version.resumen().id(),
                version.resumen().numeroVersion(),
                version.resumen().revision(),
                version.resumen().versionActual(),
                version.resumen().estadoValidacion(),
                cfg == null ? "BORRADOR" : cfg.estado(),
                actual.nombre(),
                actual.nombreProyecto(),
                actual.idGrupo(),
                actual.idArtefacto(),
                actual.paqueteBase(),
                actual.versionInicial(),
                actual.versionJava(),
                actual.versionSpringBoot(),
                actual.herramientaMigracion(),
                actual.generarRepositorios(),
                actual.generarServicios(),
                actual.generarControladores(),
                actual.generarDtos(),
                actual.exponerRest(),
                versiones,
                clases,
                mapeos,
                dependencias,
                mensajes,
                !tieneErrores(mensajes) && (cfg == null || !"ARCHIVADO".equals(cfg.estado())));
    }

    private Preparacion preparar(JsonNode snapshot, ActualizarConfiguracion cfg) {
        List<Map<String, Object>> clases = mapas(snapshot, "clases");
        List<Map<String, Object>> operaciones = mapas(snapshot, "operaciones");
        List<Map<String, Object>> atributos = mapas(snapshot, "atributos");
        List<Map<String, Object>> relaciones = mapas(snapshot, "relaciones");
        Set<UUID> idsClases =
                clases.stream().map(c -> uuid(c.get("id"))).collect(Collectors.toSet());
        Set<UUID> idsOperaciones =
                operaciones.stream().map(o -> uuid(o.get("id"))).collect(Collectors.toSet());
        Map<UUID, Map<String, Object>> clasesPorId =
                clases.stream()
                        .collect(Collectors.toMap(c -> uuid(c.get("id")), Function.identity()));
        Map<UUID, UUID> dueñoOperacion =
                operaciones.stream()
                        .collect(
                                Collectors.toMap(
                                        o -> uuid(o.get("id")), o -> uuid(o.get("id_clase"))));

        List<SeleccionClase> solicitadas =
                cfg.clases() == null
                        ? clases.stream()
                                .map(
                                        c ->
                                                new SeleccionClase(
                                                        uuid(c.get("id")),
                                                        true,
                                                        bool(c.get("es_persistente")),
                                                        true,
                                                        snake(texto(c.get("nombre"))),
                                                        nombreClase(texto(c.get("nombre"))),
                                                        null))
                                .toList()
                        : cfg.clases();
        Map<UUID, SeleccionClase> porId = new LinkedHashMap<>();
        for (SeleccionClase clase : solicitadas) {
            if (porId.containsKey(clase.idClase()))
                throw new ApiException(400, "No se admite una clase duplicada en la selección.");
            if (!idsClases.contains(clase.idClase()))
                throw new ApiException(400, "Una clase seleccionada no pertenece a la versión.");
            if (clase.operaciones() != null) {
                Set<UUID> revisadas = new LinkedHashSet<>();
                clase.operaciones()
                        .forEach(
                                o -> {
                                    if (!idsOperaciones.contains(o.idOperacion()))
                                        throw new ApiException(
                                                400,
                                                "Una operación seleccionada no pertenece a la"
                                                        + " versión.");
                                    if (!clase.idClase()
                                            .equals(dueñoOperacion.get(o.idOperacion())))
                                        throw new ApiException(
                                                400,
                                                "La operación seleccionada no pertenece a su"
                                                        + " clase.");
                                    if (!revisadas.add(o.idOperacion()))
                                        throw new ApiException(
                                                400, "No se admite una operación duplicada.");
                                });
            }
            Map<String, Object> original = clasesPorId.get(clase.idClase());
            String nombre = texto(original.get("nombre"));
            porId.put(
                    clase.idClase(),
                    new SeleccionClase(
                            clase.idClase(),
                            clase.seleccionada(),
                            clase.esPersistente(),
                            clase.exponerRest(),
                            vacio(clase.nombreTabla()) ? snake(nombre) : clase.nombreTabla(),
                            vacio(clase.nombreEntidad())
                                    ? nombreClase(nombre)
                                    : clase.nombreEntidad(),
                            clase.operaciones()));
        }
        for (Map<String, Object> original : clases) {
            UUID id = uuid(original.get("id"));
            porId.putIfAbsent(
                    id,
                    new SeleccionClase(
                            id,
                            false,
                            bool(original.get("es_persistente")),
                            true,
                            snake(texto(original.get("nombre"))),
                            nombreClase(texto(original.get("nombre"))),
                            null));
        }

        // Una asociación seleccionada necesita su destino; una dependencia no impone entidades.
        boolean cambio;
        do {
            cambio = false;
            for (Map<String, Object> relacion : relaciones) {
                if ("DEPENDENCIA".equals(relacion.get("tipo_relacion"))) continue;
                UUID origen = uuid(relacion.get("id_clase_origen"));
                UUID destino = uuid(relacion.get("id_clase_destino"));
                SeleccionClase o = porId.get(origen);
                SeleccionClase d = porId.get(destino);
                if (o != null && o.seleccionada() && d != null && !d.seleccionada()) {
                    porId.put(
                            destino,
                            new SeleccionClase(
                                    d.idClase(),
                                    true,
                                    d.esPersistente(),
                                    d.exponerRest(),
                                    d.nombreTabla(),
                                    d.nombreEntidad(),
                                    d.operaciones()));
                    cambio = true;
                }
            }
        } while (cambio);

        List<MapeoTipo> mapeos = combinarMapeos(cfg.mapeosTipo());
        List<Dependencia> dependencias =
                combinarDependencias(
                        cfg.dependencias(),
                        cfg.generarControladores(),
                        cfg.generarDtos(),
                        cfg.exponerRest(),
                        cfg.herramientaMigracion());
        List<SeleccionClase> seleccion = List.copyOf(porId.values());
        return new Preparacion(
                seleccion,
                mapeos,
                dependencias,
                mapeosPersistencia(seleccion, atributos, relaciones));
    }

    private List<MapeoPersistencia> mapeosPersistencia(
            List<SeleccionClase> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> relaciones) {
        Map<UUID, SeleccionClase> seleccionadas =
                clases.stream()
                        .filter(SeleccionClase::seleccionada)
                        .collect(Collectors.toMap(SeleccionClase::idClase, Function.identity()));
        List<MapeoPersistencia> resultado = new ArrayList<>();
        for (Map<String, Object> atributo : atributos) {
            SeleccionClase clase = seleccionadas.get(uuid(atributo.get("id_clase")));
            if (clase != null && clase.esPersistente())
                resultado.add(
                        new MapeoPersistencia(
                                uuid(atributo.get("id")),
                                null,
                                clase.nombreTabla(),
                                snake(texto(atributo.get("nombre"))),
                                "COLUMNA",
                                "{}"));
        }
        for (Map<String, Object> relacion : relaciones) {
            SeleccionClase origen = seleccionadas.get(uuid(relacion.get("id_clase_origen")));
            SeleccionClase destino = seleccionadas.get(uuid(relacion.get("id_clase_destino")));
            if (origen != null
                    && destino != null
                    && origen.esPersistente()
                    && destino.esPersistente()
                    && !Set.of("GENERALIZACION", "DEPENDENCIA")
                            .contains(texto(relacion.get("tipo_relacion"))))
                resultado.add(
                        new MapeoPersistencia(
                                null,
                                uuid(relacion.get("id")),
                                origen.nombreTabla(),
                                snake(destino.nombreEntidad()) + "_id",
                                tipoRelacion(relacion),
                                "{}"));
        }
        return resultado;
    }

    private DatosMotor datosMotor(
            JsonNode snapshot, List<ClasePersistida> seleccion, JsonNode plantilla) {
        Map<UUID, ClasePersistida> clasesSeleccionadas =
                seleccion.stream()
                        .collect(Collectors.toMap(ClasePersistida::idClase, Function.identity()));
        Set<UUID> operacionesSeleccionadas = operacionesSeleccionadas(plantilla);
        boolean seleccionExplicita = seleccionOperacionesExplicita(plantilla);
        List<Map<String, Object>> clases =
                mapas(snapshot, "clases").stream()
                        .filter(c -> clasesSeleccionadas.containsKey(uuid(c.get("id"))))
                        .map(
                                c -> {
                                    Map<String, Object> copia = new LinkedHashMap<>(c);
                                    ClasePersistida cfg =
                                            clasesSeleccionadas.get(uuid(c.get("id")));
                                    copia.put("nombre", cfg.nombreEntidad());
                                    copia.put("nombre_tabla", cfg.nombreTabla());
                                    copia.put("es_persistente", cfg.esPersistente());
                                    copia.put("exponer_rest", cfg.exponerRest());
                                    return copia;
                                })
                        .toList();
        Set<UUID> ids = clases.stream().map(c -> uuid(c.get("id"))).collect(Collectors.toSet());
        List<Map<String, Object>> atributos =
                mapas(snapshot, "atributos").stream()
                        .filter(a -> ids.contains(uuid(a.get("id_clase"))))
                        .toList();
        List<Map<String, Object>> relaciones =
                mapas(snapshot, "relaciones").stream()
                        .filter(
                                r ->
                                        ids.contains(uuid(r.get("id_clase_origen")))
                                                && ids.contains(uuid(r.get("id_clase_destino"))))
                        .toList();
        List<Map<String, Object>> operaciones =
                mapas(snapshot, "operaciones").stream()
                        .filter(o -> ids.contains(uuid(o.get("id_clase"))))
                        .filter(
                                o ->
                                        !seleccionExplicita
                                                || operacionesSeleccionadas.contains(
                                                        uuid(o.get("id"))))
                        .toList();
        Set<UUID> idsOperaciones =
                operaciones.stream().map(o -> uuid(o.get("id"))).collect(Collectors.toSet());
        List<Map<String, Object>> parametros =
                mapas(snapshot, "parametros").stream()
                        .filter(p -> idsOperaciones.contains(uuid(p.get("id_operacion"))))
                        .toList();
        return new DatosMotor(clases, atributos, relaciones, operaciones, parametros);
    }

    private List<MensajeConfiguracion> diagnosticar(
            JsonNode snapshot,
            VersionGeneracion version,
            List<SeleccionClase> clases,
            List<MapeoTipo> mapeos,
            ActualizarConfiguracion cfg) {
        List<MensajeConfiguracion> mensajes = new ArrayList<>();
        if (clases.stream().noneMatch(SeleccionClase::seleccionada))
            mensajes.add(error("SIN_CLASES", "Selecciona al menos una clase."));
        else if (clases.stream().noneMatch(c -> c.seleccionada() && c.esPersistente()))
            mensajes.add(
                    error(
                            "SIN_ENTIDADES_PERSISTENTES",
                            "Selecciona al menos una clase persistente para generar código."));
        List<SeleccionClase> persistentes =
                clases.stream().filter(c -> c.seleccionada() && c.esPersistente()).toList();
        long nombresEntidad =
                persistentes.stream()
                        .map(SeleccionClase::nombreEntidad)
                        .map(String::toLowerCase)
                        .distinct()
                        .count();
        long nombresTabla =
                persistentes.stream()
                        .map(SeleccionClase::nombreTabla)
                        .map(String::toLowerCase)
                        .distinct()
                        .count();
        if (nombresEntidad != persistentes.size() || nombresTabla != persistentes.size())
            mensajes.add(
                    error(
                            "NOMBRES_DUPLICADOS",
                            "Cada clase persistente necesita un nombre de entidad y tabla"
                                    + " únicos."));
        if (cfg.generarServicios() && !cfg.generarRepositorios())
            mensajes.add(
                    error(
                            "CAPAS_INCOMPATIBLES",
                            "La capa de servicios requiere repositorios en este generador."));
        if (cfg.generarControladores() && !cfg.generarRepositorios())
            mensajes.add(
                    error("CAPAS_INCOMPATIBLES", "Los controladores CRUD requieren repositorios."));
        if (cfg.exponerRest() && !cfg.generarControladores())
            mensajes.add(
                    error(
                            "REST_SIN_CONTROLADORES",
                            "OpenAPI/REST requiere la capa de controladores."));
        if (mapeos.isEmpty())
            mensajes.add(error("SIN_MAPEOS", "Debe existir al menos un mapeo de tipos."));

        if (snapshot != null) {
            Map<UUID, SeleccionClase> seleccionadas =
                    clases.stream()
                            .filter(SeleccionClase::seleccionada)
                            .collect(
                                    Collectors.toMap(SeleccionClase::idClase, Function.identity()));
            List<Map<String, Object>> atributos = mapas(snapshot, "atributos");
            Set<UUID> clasesConId = new HashSet<>();
            for (Map<String, Object> a : atributos) {
                UUID idClase = uuid(a.get("id_clase"));
                SeleccionClase clase = seleccionadas.get(idClase);
                if (clase != null) {
                    if (bool(a.get("es_estatico"))
                            || a.get("multiplicidad_maxima") == null
                            || ((Number) a.get("multiplicidad_maxima")).longValue() != 1
                            || bool(a.get("es_ordenado"))
                            || Boolean.FALSE.equals(a.get("coleccion_unica")))
                        mensajes.add(
                                error(
                                        "COLECCION_ATRIBUTO_NO_GENERABLE",
                                        "El atributo '"
                                                + texto(a.get("nombre"))
                                                + "' es UML válido pero este generador CRUD solo"
                                                + " admite atributos escalares sin orden"
                                                + " explícito."));
                    if (bool(a.get("es_identificador"))
                            && (bool(a.get("es_estatico"))
                                    || !Integer.valueOf(1).equals(a.get("multiplicidad_minima"))
                                    || !Integer.valueOf(1).equals(a.get("multiplicidad_maxima"))))
                        mensajes.add(
                                error(
                                        "ID_PERSISTENCIA_INVALIDO",
                                        "Un @Id JPA requiere 1..1 y no ser estático; esto no"
                                            + " invalida UML."));
                    String tipo = textoNullable(a.get("tipo_uml"));
                    if (tipo == null
                            || tipo.isBlank()
                            || tipo.startsWith("uml:")
                            || tipo.contains(":")) {
                        mensajes.add(
                                error(
                                        "ATRIBUTO_SIN_TIPO",
                                        "En la clase '"
                                                + clase.nombreEntidad()
                                                + "', el atributo '"
                                                + texto(a.get("nombre"))
                                                + "' no tiene un tipo de dato válido ('"
                                                + (tipo == null || tipo.isBlank()
                                                        ? "sin tipo"
                                                        : tipo)
                                                + "')."));
                    }
                    if (bool(a.get("es_identificador"))) {
                        clasesConId.add(idClase);
                    }
                }
            }
            Set<UUID> opSeleccionadas = new HashSet<>();
            for (var c : clases)
                if (c.seleccionada() && c.esPersistente()) {
                    if (c.operaciones() == null)
                        for (var o : mapas(snapshot, "operaciones")) {
                            if (c.idClase().equals(uuid(o.get("id_clase"))))
                                opSeleccionadas.add(uuid(o.get("id")));
                        }
                    else
                        for (var o : c.operaciones())
                            if (o.seleccionada()) opSeleccionadas.add(o.idOperacion());
                }
            for (var p : mapas(snapshot, "parametros"))
                if (opSeleccionadas.contains(uuid(p.get("id_operacion")))
                        && Set.of("SALIDA", "ENTRADA_SALIDA").contains(texto(p.get("direccion"))))
                    mensajes.add(
                            error(
                                    "PARAMETRO_SALIDA_NO_GENERABLE",
                                    "Parámetro out/inout UML válido; falta mapeo de respuesta para"
                                        + " este generador CRUD."));
            if (!mapas(snapshot, "relaciones").isEmpty())
                mensajes.add(
                        new MensajeConfiguracion(
                                "ADVERTENCIA",
                                "MULTIPLICIDADES_NEGOCIO_PENDIENTES",
                                "El CRUD no garantiza todos los límites de tamaño de colecciones ni"
                                    + " la exclusividad dinámica de composición; requieren reglas"
                                    + " de negocio y pruebas de instancias."));
            Set<UUID> idsPersistentes =
                    persistentes.stream().map(SeleccionClase::idClase).collect(Collectors.toSet());
            for (var c : mapas(snapshot, "clases"))
                if (idsPersistentes.contains(uuid(c.get("id"))) && bool(c.get("es_abstracto")))
                    mensajes.add(
                            error(
                                    "CLASE_ABSTRACTA_NO_GENERABLE",
                                    "Clase abstracta UML válida; este generador CRUD no admite"
                                        + " herencia JPA."));
            for (var r : mapas(snapshot, "relaciones"))
                if ("GENERALIZACION".equals(r.get("tipo_relacion"))
                        && (idsPersistentes.contains(uuid(r.get("id_clase_origen")))
                                || idsPersistentes.contains(uuid(r.get("id_clase_destino")))))
                    mensajes.add(
                            error(
                                    "HERENCIA_NO_GENERABLE",
                                    "Generalización UML válida; mapeo de herencia JPA no"
                                        + " implementado."));
            for (var o : mapas(snapshot, "operaciones"))
                if (idsPersistentes.contains(uuid(o.get("id_clase"))))
                    mensajes.add(
                            new MensajeConfiguracion(
                                    "ADVERTENCIA",
                                    "NEGOCIO_PENDIENTE",
                                    "Las operaciones de negocio son contratos; requieren"
                                        + " implementación y pruebas propias."));
            for (SeleccionClase c : persistentes) {
                if (!clasesConId.contains(c.idClase())) {
                    mensajes.add(
                            new MensajeConfiguracion("ADVERTENCIA",
                                    "CLASE_SIN_ID",
                                    "En la clase persistente '"
                                            + c.nombreEntidad()
                                            + "' se generará un identificador técnico Long en el backend."
                                            + " La versión UML guardada no se modifica."));
                }
            }
        }

        if (!Set.of("VALIDO", "VALIDO_CON_ADVERTENCIAS").contains(version.estadoValidacion()))
            mensajes.add(
                    new MensajeConfiguracion(
                            "ADVERTENCIA",
                            "VERSION_SIN_VALIDAR",
                            "Puedes generar sin validar esta versión UML. La revisión semántica"
                                    + " es opcional e independiente de la compilación del backend."));
        if (!version.versionActual())
            mensajes.add(
                    new MensajeConfiguracion(
                            "INFORMACION",
                            "VERSION_HISTORICA",
                            "Se generará de forma reproducible desde una versión histórica"
                                    + " congelada."));
        return List.copyOf(mensajes);
    }

    private ActualizarConfiguracion defaults(CabeceraModelo cabecera, UUID version) {
        String artefacto = nombreSeguro(cabecera.nombre());
        return new ActualizarConfiguracion(
                version,
                "Backend " + cabecera.nombre(),
                artefacto,
                "com.empresa",
                artefacto,
                "com.empresa." + segmentoPaqueteSeguro(cabecera.nombre()),
                "1.0.0-SNAPSHOT",
                JAVA_DEFECTO,
                SPRING_DEFECTO,
                "FLYWAY",
                true,
                true,
                true,
                true,
                true,
                null,
                null,
                null);
    }

    private ActualizarConfiguracion desdePersistida(ConfiguracionPersistida cfg) {
        return new ActualizarConfiguracion(
                cfg.idVersionModelo(),
                cfg.nombre(),
                cfg.nombreProyecto(),
                cfg.idGrupo(),
                cfg.idArtefacto(),
                cfg.paqueteBase(),
                cfg.versionInicial(),
                cfg.versionJava(),
                cfg.versionSpringBoot(),
                cfg.herramientaMigracion(),
                cfg.generarRepositorios(),
                cfg.generarServicios(),
                cfg.generarControladores(),
                cfg.generarDtos(),
                cfg.exponerRest(),
                null,
                null,
                null);
    }

    private ActualizarConfiguracion normalizar(ActualizarConfiguracion cfg) {
        return new ActualizarConfiguracion(
                cfg.idVersionModelo(),
                cfg.nombre().trim(),
                cfg.nombreProyecto().trim(),
                cfg.idGrupo().trim(),
                cfg.idArtefacto().trim(),
                cfg.paqueteBase().trim(),
                cfg.versionInicial().trim(),
                vacio(cfg.versionJava()) ? JAVA_DEFECTO : cfg.versionJava(),
                vacio(cfg.versionSpringBoot()) ? SPRING_DEFECTO : cfg.versionSpringBoot(),
                vacio(cfg.herramientaMigracion()) ? "FLYWAY" : cfg.herramientaMigracion(),
                cfg.generarRepositorios(),
                cfg.generarServicios(),
                cfg.generarControladores(),
                cfg.generarDtos(),
                cfg.exponerRest(),
                cfg.clases(),
                cfg.mapeosTipo(),
                cfg.dependencias());
    }

    private List<MapeoTipo> combinarMapeos(List<MapeoTipo> personalizados) {
        Map<String, MapeoTipo> resultado =
                mapeosDefecto().stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.tipoUml().toLowerCase(Locale.ROOT),
                                        Function.identity(),
                                        (a, b) -> b,
                                        LinkedHashMap::new));
        if (personalizados != null)
            personalizados.forEach(m -> resultado.put(m.tipoUml().toLowerCase(Locale.ROOT), m));
        return List.copyOf(resultado.values());
    }

    private List<MapeoTipo> mapeosDefecto() {
        return List.of(
                new MapeoTipo("String", "String", "VARCHAR(255)", null),
                new MapeoTipo("Texto", "String", "TEXT", null),
                new MapeoTipo("Integer", "Integer", "INTEGER", null),
                new MapeoTipo("int", "Integer", "INTEGER", null),
                new MapeoTipo("Long", "Long", "BIGINT", null),
                new MapeoTipo("Double", "Double", "DOUBLE PRECISION", null),
                new MapeoTipo("Boolean", "Boolean", "BOOLEAN", null),
                new MapeoTipo("LocalDate", "LocalDate", "DATE", null),
                new MapeoTipo("Date", "LocalDate", "DATE", null),
                new MapeoTipo("LocalDateTime", "LocalDateTime", "TIMESTAMP", null),
                new MapeoTipo("UUID", "UUID", "UUID", null),
                new MapeoTipo("BigDecimal", "BigDecimal", "NUMERIC", null));
    }

    private List<Dependencia> combinarDependencias(
            List<Dependencia> extras,
            boolean controladores,
            boolean dtos,
            boolean rest,
            String migracion) {
        Map<String, Dependencia> resultado = new LinkedHashMap<>();
        for (Dependencia d : dependenciasDefecto(controladores, dtos, rest, migracion))
            resultado.put(d.idGrupo() + ":" + d.idArtefacto(), d);
        if (extras != null)
            for (Dependencia d : extras) {
                if (d.obligatoria()) continue;
                Dependencia normalizada =
                        new Dependencia(
                                d.idGrupo().trim(),
                                d.idArtefacto().trim(),
                                vacio(d.version()) ? null : d.version().trim(),
                                vacio(d.ambito()) ? "COMPILACION" : d.ambito(),
                                d.obligatoria());
                resultado.putIfAbsent(
                        normalizada.idGrupo() + ":" + normalizada.idArtefacto(), normalizada);
            }
        return List.copyOf(resultado.values());
    }

    private List<Dependencia> dependenciasDefecto(
            boolean controladores, boolean dtos, boolean rest, String migracion) {
        List<Dependencia> ds = new ArrayList<>();
        if (controladores || rest)
            ds.add(obligatoria("org.springframework.boot", "spring-boot-starter-web", null));
        ds.add(obligatoria("org.springframework.boot", "spring-boot-starter-data-jpa", null));
        if (dtos || controladores)
            ds.add(obligatoria("org.springframework.boot", "spring-boot-starter-validation", null));
        ds.add(new Dependencia("org.postgresql", "postgresql", null, "EJECUCION", true));
        if (rest)
            ds.add(obligatoria("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "2.8.9"));
        if ("FLYWAY".equals(migracion)) {
            ds.add(obligatoria("org.flywaydb", "flyway-core", null));
            ds.add(obligatoria("org.flywaydb", "flyway-database-postgresql", null));
        }
        if ("LIQUIBASE".equals(migracion))
            ds.add(obligatoria("org.liquibase", "liquibase-core", null));
        ds.add(
                new Dependencia(
                        "org.springframework.boot",
                        "spring-boot-starter-test",
                        null,
                        "PRUEBA",
                        true));
        return ds;
    }

    private Dependencia obligatoria(String grupo, String artefacto, String version) {
        return new Dependencia(grupo, artefacto, version, "COMPILACION", true);
    }

    private String plantilla(List<SeleccionClase> clases, String nombre) {
        boolean seleccionExplicita = clases.stream().anyMatch(c -> c.operaciones() != null);
        List<String> operaciones =
                clases.stream()
                        .filter(SeleccionClase::seleccionada)
                        .filter(c -> c.operaciones() != null)
                        .flatMap(c -> c.operaciones().stream())
                        .filter(SeleccionOperacion::seleccionada)
                        .map(o -> o.idOperacion().toString())
                        .toList();
        try {
            return json.writeValueAsString(
                    Map.of(
                            "nombreConfiguracion", nombre,
                            "operacionesSeleccionadas",
                            operaciones,
                            "seleccionOperacionesExplicita",
                            seleccionExplicita));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo serializar la selección de operaciones.", e);
        }
    }

    private Set<UUID> operacionesSeleccionadas(JsonNode plantilla) {
        Set<UUID> resultado = new LinkedHashSet<>();
        if (plantilla != null)
            plantilla
                    .path("operacionesSeleccionadas")
                    .forEach(n -> resultado.add(UUID.fromString(n.asText())));
        return resultado;
    }

    private boolean seleccionOperacionesExplicita(JsonNode plantilla) {
        return plantilla != null
                && plantilla.path("seleccionOperacionesExplicita").asBoolean(false);
    }

    private List<SeleccionClase> seleccionDesdeVista(List<ClaseGeneracion> clases) {
        return clases.stream()
                .map(
                        c ->
                                new SeleccionClase(
                                        c.idClase(),
                                        c.seleccionada(),
                                        c.esPersistente(),
                                        c.exponerRest(),
                                        c.nombreTabla(),
                                        c.nombreEntidad(),
                                        c.operaciones().stream()
                                                .map(
                                                        o ->
                                                                new SeleccionOperacion(
                                                                        o.id(), o.seleccionada()))
                                                .toList()))
                .toList();
    }

    private List<Map<String, Object>> mapas(JsonNode snapshot, String campo) {
        return json.convertValue(
                snapshot.path(campo), new TypeReference<List<Map<String, Object>>>() {});
    }

    private String tipoRelacion(Map<String, Object> relacion) {
        if ("GENERALIZACION".equals(texto(relacion.get("tipo_relacion")))) return "HERENCIA";
        boolean muchosOrigen = maxMuchos(relacion.get("maximo_origen"));
        boolean muchosDestino = maxMuchos(relacion.get("maximo_destino"));
        if (muchosOrigen && muchosDestino) return "MUCHOS_A_MUCHOS";
        if (muchosDestino) return "UNO_A_MUCHOS";
        if (muchosOrigen) return "MUCHOS_A_UNO";
        return "UNO_A_UNO";
    }

    private boolean maxMuchos(Object valor) {
        if (valor == null) return true;
        if (valor instanceof Number n) return n.intValue() != 1;
        return !"1".equals(valor.toString());
    }

    private int contarArchivos(byte[] zip) {
        int total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            while (in.getNextEntry() != null) total++;
            return total;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo verificar el ZIP generado.", e);
        }
    }

    private void autorizar(Identidad actor, CabeceraModelo c, String permiso, String accion) {
        if (!"ACTIVO".equals(c.estadoProyecto()))
            throw new ApiException(404, "Modelo no disponible.");
        if (!actor.esAdministrador() && !repo.permiso(actor.id(), c.proyecto(), permiso))
            throw new ApiException(403, "No tienes permiso para " + accion + ".");
    }

    private MensajeConfiguracion error(String codigo, String mensaje) {
        return new MensajeConfiguracion("ERROR", codigo, mensaje);
    }

    private boolean tieneErrores(List<MensajeConfiguracion> mensajes) {
        return mensajes.stream().anyMatch(m -> "ERROR".equals(m.nivel()));
    }

    private String nombreSeguro(String valor) {
        String resultado =
                Normalizer.normalize(valor == null ? "proyecto" : valor, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9-]", "-")
                        .replaceAll("-+", "-")
                        .replaceAll("^-|-$", "");
        return resultado.isBlank() ? "proyecto" : resultado;
    }

    private String segmentoPaqueteSeguro(String valor) {
        String resultado = nombreSeguro(valor).replace("-", "");
        return Character.isDigit(resultado.charAt(0)) ? "p" + resultado : resultado;
    }

    private String nombreClase(String valor) {
        String limpio =
                Normalizer.normalize(valor, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .replaceAll("[^A-Za-z0-9]", " ");
        StringBuilder resultado = new StringBuilder();
        for (String parte : limpio.trim().split("\\s+"))
            if (!parte.isEmpty())
                resultado.append(Character.toUpperCase(parte.charAt(0))).append(parte.substring(1));
        if (resultado.isEmpty()) resultado.append("Entidad");
        if (Character.isDigit(resultado.charAt(0))) resultado.insert(0, 'E');
        return resultado.toString();
    }

    private String snake(String valor) {
        return nombreClase(valor).replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private UUID uuid(Object valor) {
        return valor instanceof UUID id ? id : UUID.fromString(valor.toString());
    }

    private String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private String textoNullable(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private boolean bool(Object valor) {
        return valor instanceof Boolean b ? b : Boolean.parseBoolean(texto(valor));
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private record Preparacion(
            List<SeleccionClase> clases,
            List<MapeoTipo> mapeos,
            List<Dependencia> dependencias,
            List<MapeoPersistencia> persistencia) {}

    private record DatosMotor(
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros) {}
}
