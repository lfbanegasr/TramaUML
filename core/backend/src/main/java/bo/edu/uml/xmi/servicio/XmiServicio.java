package bo.edu.uml.xmi.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.*;
import bo.edu.uml.modelado.servicio.EdicionTransaccional;
import bo.edu.uml.xmi.dto.XmiDto.*;
import bo.edu.uml.xmi.repositorio.XmiRepositorio;
import bo.edu.uml.xmi.repositorio.XmiRepositorio.CabeceraModelo;
import bo.edu.uml.xmi.repositorio.XmiRepositorio.IntercambioImportacion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Servicio transaccional para importación y exportación XMI 2.1. */
@Service
public class XmiServicio {

    private static final long MAX_XMI_BYTES = 5L * 1024 * 1024;
    private static final Set<String> TIPOS_PRIMITIVOS =
            Set.of(
                    "String",
                    "Integer",
                    "Long",
                    "Double",
                    "Boolean",
                    "LocalDate",
                    "LocalDateTime",
                    "BigDecimal",
                    "UUID");

    private final XmiRepositorio repo;
    private final GeneradorXmi21Service generador;
    private final GeneradorScriptEaService generadorScriptEa;
    private final AnalizadorXmi21Service analizador;
    private final EdicionTransaccional edicion;
    private final ObjectMapper json;

    public XmiServicio(
            XmiRepositorio repo,
            GeneradorXmi21Service generador,
            GeneradorScriptEaService generadorScriptEa,
            AnalizadorXmi21Service analizador,
            EdicionTransaccional edicion,
            ObjectMapper json) {
        this.repo = repo;
        this.generador = generador;
        this.generadorScriptEa = generadorScriptEa;
        this.analizador = analizador;
        this.edicion = edicion;
        this.json = json;
    }

    private record Datos(
            String nombre,
            List<Map<String, Object>> paquetes,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> posicionesClases,
            List<Map<String, Object>> posicionesRelaciones) {}

    private record Construccion(
            List<OperacionImportacion> operaciones,
            List<String> advertencias,
            List<String> bloqueadores) {}

    // ─── Exportación ─────────────────────────────────────────────────────────

    @Transactional
    public ResultadoExportacion exportar(
            Identidad actor, UUID modelo, SolicitudExportacion solicitud) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "EXPORTAR_XMI", "exportar XMI");

        Datos datos;
        UUID version = solicitud.idVersion();
        if (Boolean.TRUE.equals(solicitud.estadoActual())) {
            version = null;
            datos = datosActuales(modelo, cabecera.nombre());
        } else if (version != null) {
            datos = datosVersion(modelo, version);
        } else if (cabecera.idVersionActual() != null) {
            version = cabecera.idVersionActual();
            datos = datosVersion(modelo, version);
        } else {
            version = null;
            datos = datosActuales(modelo, cabecera.nombre());
        }
        List<String> advertencias = new ArrayList<>();
        Datos alcance =
                filtrarAlcance(
                        datos,
                        solicitud.idPaquete(),
                        !Boolean.FALSE.equals(solicitud.incluirSubpaquetes()),
                        advertencias);

        if (alcance.clases().isEmpty())
            advertencias.add("El alcance seleccionado no contiene clases UML.");

        String formato =
                solicitud.formato() == null || solicitud.formato().isBlank()
                        ? "XMI_251_EA"
                        : solicitud.formato().toUpperCase(Locale.ROOT);

        if (!Set.of("XMI_EA", "XMI_OMG", "XMI_251_EA", "XMI_251_OMG", "SCRIPT_VBS_EA")
                .contains(formato))
            throw new ApiException(422, "Perfil de intercambio desconocido.");
        byte[] contenido;
        String nombreArchivo;
        String tipoMime;
        String varianteInforme;

        if ("SCRIPT_VBS_EA".equals(formato)) {
            contenido =
                    generadorScriptEa.generar(
                            alcance.nombre(),
                            alcance.paquetes(),
                            alcance.clases(),
                            alcance.atributos(),
                            alcance.operaciones(),
                            alcance.parametros(),
                            alcance.relaciones(),
                            alcance.posicionesClases());
            nombreArchivo = nombreArchivoVbs(cabecera.nombre(), solicitud.idPaquete());
            tipoMime = "text/plain";
            varianteInforme = "SCRIPT_VBS_EA";
        } else if (formato.startsWith("XMI_251")) {
            boolean ea = formato.equals("XMI_251_EA");
            contenido =
                    new GeneradorXmi251Service()
                            .generar(
                                    alcance.nombre(),
                                    alcance.paquetes(),
                                    alcance.clases(),
                                    alcance.atributos(),
                                    alcance.operaciones(),
                                    alcance.parametros(),
                                    alcance.relaciones(),
                                    alcance.posicionesClases(),
                                    alcance.posicionesRelaciones(),
                                    ea);
            nombreArchivo =
                    nombreArchivo(cabecera.nombre(), solicitud.idPaquete())
                            .replace("_xmi21", "_uml251_xmi251");
            tipoMime = "application/xml";
            varianteInforme = ea ? "UML_2.5.1_XMI_2.5.1_EA" : "UML_2.5.1_XMI_2.5.1_OMG";
            if (!ea && !alcance.posicionesClases().isEmpty())
                advertencias.add(
                        "Perfil semántico sin layout: se omiten posiciones de clases; no se exporta"
                            + " UMLDI.");
        } else {
            boolean incluirExtensionEa = !"XMI_OMG".equals(formato);
            contenido =
                    generador.generar(
                            alcance.nombre(),
                            alcance.paquetes(),
                            alcance.clases(),
                            alcance.atributos(),
                            alcance.operaciones(),
                            alcance.parametros(),
                            alcance.relaciones(),
                            alcance.posicionesClases(),
                            alcance.posicionesRelaciones(),
                            incluirExtensionEa);
            nombreArchivo = nombreArchivo(cabecera.nombre(), solicitud.idPaquete());
            tipoMime = "application/xml";
            varianteInforme = incluirExtensionEa ? "XMI_2.1_EA" : "XMI_2.1_OMG";
        }

        if (!formato.startsWith("XMI_251")) agregarAdvertenciasTipos(alcance, advertencias);
        if (!formato.startsWith("XMI_251"))
            advertencias.add(
                    "Exportación de compatibilidad anterior: puede perder orden/unicidad UML,"
                        + " valores predeterminados, restricciones y metadatos del producto. No"
                        + " acredita UML 2.5.1 ni compatibilidad visual EA.");
        if (!alcance.posicionesRelaciones().isEmpty())
            advertencias.add("No se exportan puntos intermedios ni estilos de relaciones.");
        advertencias.add(
                "XML y round-trip del subconjunto; compatibilidad visual con una instalación real"
                    + " de Enterprise Architect pendiente de verificación.");
        UUID idArchivo =
                repo.guardarArchivo(cabecera.proyecto(), actor.id(), nombreArchivo, tipoMime, contenido);

        ObjectNode ambito = json.createObjectNode();
        ambito.put("alcance", solicitud.idPaquete() == null ? "MODELO_COMPLETO" : "PAQUETE");
        ambito.put("idSolicitud", solicitud.idSolicitud().toString());
        ambito.put("formato", formato);
        if (solicitud.idPaquete() != null)
            ambito.put("idPaquete", solicitud.idPaquete().toString());
        ambito.put("incluirSubpaquetes", !Boolean.FALSE.equals(solicitud.incluirSubpaquetes()));
        if (solicitud.descripcion() != null && !solicitud.descripcion().isBlank())
            ambito.put("descripcion", solicitud.descripcion().trim());

        ObjectNode informe = json.createObjectNode();
        informe.put("variante", varianteInforme);
        informe.put("formato", formato);
        informe.put("totalPaquetes", alcance.paquetes().size());
        informe.put("totalClases", alcance.clases().size());
        informe.put("totalRelaciones", alcance.relaciones().size());
        informe.set("advertencias", json.valueToTree(advertencias));
        UUID idIntercambio =
                repo.registrarIntercambio(
                        modelo,
                        version,
                        actor.id(),
                        idArchivo,
                        escribir(ambito),
                        escribir(informe));

        return new ResultadoExportacion(
                idIntercambio,
                idArchivo,
                nombreArchivo,
                version,
                solicitud.idPaquete(),
                alcance.paquetes().size(),
                alcance.clases().size(),
                alcance.relaciones().size(),
                List.copyOf(advertencias));
    }

    @Transactional(readOnly = true)
    public XmiRepositorio.ContenidoArchivo descargar(
            Identidad actor, UUID modelo, UUID idIntercambio) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "EXPORTAR_XMI", "exportar XMI");
        return repo.descargarArchivoPorIntercambio(modelo, idIntercambio);
    }

    // ─── Importación ─────────────────────────────────────────────────────────

    @Transactional
    public Importacion importar(
            Identidad actor,
            UUID modelo,
            UUID idSolicitud,
            String estrategia,
            MultipartFile archivo) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "IMPORTAR_XMI", "importar XMI");
        String estrategiaNormalizada = estrategia(estrategia);
        Optional<UUID> repetida =
                repo.buscarImportacion(modelo, actor.id(), idSolicitud, estrategiaNormalizada);
        if (repetida.isPresent()) return detalleAutorizado(actor, cabecera, repetida.get());

        byte[] contenido = leerArchivo(archivo);
        String nombre = nombreEntrada(archivo.getOriginalFilename());
        String tipo = tipoContenido(archivo.getContentType());
        UUID idArchivo =
                repo.guardarArchivoEntrada(
                        cabecera.proyecto(), actor.id(), nombre, tipo, contenido);
        UUID intercambio =
                repo.registrarImportacion(
                        modelo,
                        cabecera.idVersionActual(),
                        actor.id(),
                        idArchivo,
                        idSolicitud,
                        cabecera.revision(),
                        estrategiaNormalizada);
        try {
            var analisis = analizador.analizar(contenido, intercambio);
            Construccion construccion = construir(modelo, analisis, estrategiaNormalizada);
            ObjectNode informe = informe(analisis, cabecera.revision(), construccion);
            repo.registrarAnalisis(
                    intercambio, analisis.versionEnterpriseArchitect(), escribir(informe));
        } catch (ApiException e) {
            repo.registrarFallo(intercambio, e.getMessage());
        } catch (RuntimeException e) {
            repo.registrarFallo(intercambio, "No se pudo analizar el documento XMI.");
        }
        return detalleAutorizado(actor, cabecera, intercambio);
    }

    @Transactional(readOnly = true)
    public Importacion consultar(Identidad actor, UUID modelo, UUID intercambio) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "IMPORTAR_XMI", "revisar importaciones XMI");
        return convertir(repo.importacion(modelo, intercambio), cabecera.revision());
    }

    @Transactional
    public ResultadoAplicacionImportacion aplicar(
            Identidad actor,
            UUID modelo,
            UUID intercambio,
            SolicitudAplicacionImportacion solicitud) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "IMPORTAR_XMI", "aplicar importaciones XMI");
        IntercambioImportacion fila = repo.bloquearImportacion(modelo, intercambio);
        JsonNode informe = leerJson(fila.informe());

        if ("COMPLETADO".equals(fila.estado())
                && solicitud
                        .idSolicitud()
                        .toString()
                        .equals(informe.path("idSolicitudAplicacion").asText())
                && fila.versionImportada() != null) {
            return new ResultadoAplicacionImportacion(
                    intercambio,
                    fila.estado(),
                    repo.revisionVersion(modelo, fila.versionImportada()),
                    fila.versionImportada(),
                    true);
        }
        if (!"LISTO_PARA_REVISION".equals(fila.estado()))
            throw new ApiException(409, "La importación ya fue procesada o no está lista.");

        long revisionBase = informe.path("revisionBase").asLong(-1);
        if (revisionBase != solicitud.revisionBase() || revisionBase != cabecera.revision())
            throw new ApiException(
                    409, "El modelo cambió; analiza nuevamente el XMI antes de aplicarlo.");
        if (informe.path("bloqueadores").size() > 0)
            throw new ApiException(
                    422, "La importación contiene bloqueadores que impiden aplicarla.");

        List<OperacionImportacion> previsualizacion = operaciones(informe.path("operaciones"));
        if (previsualizacion.isEmpty())
            throw new ApiException(422, "La importación no contiene operaciones aplicables.");
        List<Accion> acciones =
                previsualizacion.stream()
                        .map(o -> new Accion(o.operacion(), o.entidad(), o.idEntidad(), o.datos()))
                        .toList();
        Cambio cambio =
                new Cambio(
                        solicitud.idSesionEdicion(),
                        solicitud.idSolicitud(),
                        revisionBase,
                        acciones);
        Confirmacion confirmacion = edicion.aplicar(actor, modelo, cambio, escribir(acciones));
        repo.completarImportacion(intercambio, confirmacion.idVersion(), solicitud.idSolicitud());
        return new ResultadoAplicacionImportacion(
                intercambio,
                "COMPLETADO",
                confirmacion.revision(),
                confirmacion.idVersion(),
                confirmacion.repetido());
    }

    @Transactional
    public Importacion cancelar(
            Identidad actor, UUID modelo, UUID intercambio, SolicitudCancelacion solicitud) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        autorizar(actor, cabecera, "IMPORTAR_XMI", "cancelar importaciones XMI");
        IntercambioImportacion fila = repo.bloquearImportacion(modelo, intercambio);
        if (!"LISTO_PARA_REVISION".equals(fila.estado()))
            throw new ApiException(409, "La importación ya fue procesada o no está lista.");
        String motivo =
                solicitud == null || solicitud.motivo() == null || solicitud.motivo().isBlank()
                        ? "Cancelada por el usuario."
                        : solicitud.motivo().trim();
        repo.cancelarImportacion(intercambio, motivo);
        return convertir(repo.importacion(modelo, intercambio), cabecera.revision());
    }

    // ─── Construcción de la previsualización ────────────────────────────────

    private Construccion construir(
            UUID modelo, AnalizadorXmi21Service.Analisis analisis, String estrategia) {
        List<Map<String, Object>> paquetesActuales = repo.leerPaquetes(modelo);
        List<Map<String, Object>> clasesActuales = repo.leerClases(modelo);
        List<Map<String, Object>> atributosActuales = repo.leerAtributos(modelo);
        List<Map<String, Object>> operacionesActuales = repo.leerOperaciones(modelo);
        List<Map<String, Object>> relacionesActuales = repo.leerRelaciones(modelo);
        List<OperacionImportacion> resultado = new ArrayList<>();
        LinkedHashSet<String> advertencias = new LinkedHashSet<>(analisis.advertencias());
        LinkedHashSet<String> bloqueadores = new LinkedHashSet<>();
        validarDuplicados(analisis, bloqueadores);
        int orden = 1;

        if ("REEMPLAZAR".equals(estrategia)) {
            for (Map<String, Object> r : relacionesActuales)
                resultado.add(eliminar(orden++, "RELACION", uuid(r.get("id")), nombreMapa(r)));
            for (Map<String, Object> c : clasesActuales)
                resultado.add(eliminar(orden++, "CLASE", uuid(c.get("id")), nombreMapa(c)));
            List<Map<String, Object>> paquetesOrdenados = new ArrayList<>(paquetesActuales);
            paquetesOrdenados.sort(
                    Comparator.comparingInt(
                                    (Map<String, Object> p) -> profundidad(p, paquetesActuales))
                            .reversed());
            for (Map<String, Object> p : paquetesOrdenados)
                resultado.add(eliminar(orden++, "PAQUETE", uuid(p.get("id")), nombreMapa(p)));
            if (!clasesActuales.isEmpty())
                advertencias.add(
                        "REEMPLAZAR eliminará los elementos vigentes antes de crear el contenido"
                                + " importado.");
        }

        boolean fusionar = "FUSIONAR".equals(estrategia);
        Map<String, Map<String, Object>> paquetesPorNombre =
                porPadreYNombre(paquetesActuales, "id_paquete_padre");
        Map<String, Map<String, Object>> clasesPorNombre =
                porPadreYNombre(clasesActuales, "id_paquete");
        Map<UUID, UUID> idsPaquete = new LinkedHashMap<>(), idsClase = new LinkedHashMap<>();

        for (var p : analisis.paquetes()) {
            Map<String, Object> existente =
                    fusionar
                            ? paquetesPorNombre.get(
                                    Objects.toString(
                                                    p.padre() == null
                                                            ? null
                                                            : idsPaquete.get(p.padre()),
                                                    "")
                                            + "|"
                                            + clave(p.nombre()))
                            : null;
            UUID id = existente == null ? p.id() : uuid(existente.get("id"));
            idsPaquete.put(p.id(), id);
            ObjectNode datos = json.createObjectNode().put("nombre", p.nombre());
            ponerTexto(datos, "descripcion", p.descripcion());
            ponerUuid(datos, "padre", p.padre() == null ? null : idsPaquete.get(p.padre()));
            resultado.add(
                    operacion(
                            orden++,
                            existente == null ? "CREAR" : "ACTUALIZAR",
                            "PAQUETE",
                            id,
                            p.nombre(),
                            datos));
        }

        for (var c : analisis.clases()) {
            Map<String, Object> existente =
                    fusionar
                            ? clasesPorNombre.get(
                                    Objects.toString(
                                                    c.paquete() == null
                                                            ? null
                                                            : idsPaquete.get(c.paquete()),
                                                    "")
                                            + "|"
                                            + clave(c.nombre()))
                            : null;
            UUID id = existente == null ? c.id() : uuid(existente.get("id"));
            idsClase.put(c.id(), id);
            ObjectNode datos =
                    json.createObjectNode()
                            .put("nombre", c.nombre())
                            .put("visibilidad", c.visibilidad())
                            .put("esAbstracto", c.abstracta())
                            .put("esPersistente", c.persistente())
                            .put("estereotipo", c.estereotipo())
                            .put("descripcion", c.descripcion())
                            .put("x", c.x())
                            .put("y", c.y())
                            .put("ancho", c.ancho())
                            .put("alto", c.alto());
            ponerUuid(datos, "idPaquete", c.paquete() == null ? null : idsPaquete.get(c.paquete()));
            resultado.add(
                    operacion(
                            orden++,
                            existente == null ? "CREAR" : "ACTUALIZAR",
                            "CLASE",
                            id,
                            c.nombre(),
                            datos));
        }

        Map<String, Map<String, Object>> atributosActualesPorClave =
                porPadreYNombre(atributosActuales, "id_clase");
        for (var a : analisis.atributos()) {
            UUID clase = idsClase.get(a.clase());
            Map<String, Object> existente =
                    fusionar
                            ? atributosActualesPorClave.get(clase + "|" + clave(a.nombre()))
                            : null;
            UUID id = existente == null ? a.id() : uuid(existente.get("id"));
            ObjectNode datos =
                    json.createObjectNode()
                            .put("idClase", clase.toString())
                            .put("nombre", a.nombre())
                            .put("tipoUml", a.tipo())
                            .put("visibilidad", a.visibilidad())
                            .put("minimo", a.minimo())
                            .put("esIdentificador", a.identificador())
                            .put("esEstatico", a.estatico())
                            .put("esUnico", a.unicoSql())
                            .put("esOrdenado", a.ordenado())
                            .put("coleccionUnica", a.coleccionUnica())
                            .put("valorPredeterminado", a.predeterminado());
            if (a.restricciones() != null) datos.set("restricciones", leerJson(a.restricciones()));
            if (a.maximo() == null) datos.putNull("maximo");
            else datos.put("maximo", a.maximo());
            resultado.add(
                    operacion(
                            orden++,
                            existente == null ? "CREAR" : "ACTUALIZAR",
                            "ATRIBUTO",
                            id,
                            a.nombre(),
                            datos));
        }

        Map<String, Map<String, Object>> operacionesActualesPorClave = new LinkedHashMap<>();
        var parametrosActuales = repo.leerParametros(modelo);
        for (var o : operacionesActuales) {
            var firma =
                    parametrosActuales.stream()
                            .filter(p -> Objects.equals(p.get("id_operacion"), o.get("id")))
                            .sorted(
                                    Comparator.comparingInt(
                                            p -> ((Number) p.getOrDefault("orden", 0)).intValue()))
                            .map(p -> p.get("tipo_uml") + ":" + p.get("direccion"))
                            .toList();
            operacionesActualesPorClave.put(
                    o.get("id_clase") + "|" + clave(nombreMapa(o)) + "|" + firma, o);
        }
        for (var o : analisis.operaciones()) {
            UUID clase = idsClase.get(o.clase());
            Map<String, Object> existente =
                    fusionar
                            ? operacionesActualesPorClave.get(
                                    clase
                                            + "|"
                                            + clave(o.nombre())
                                            + "|"
                                            + o.parametros().stream()
                                                    .map(p -> p.tipo() + ":" + p.direccion())
                                                    .toList())
                            : null;
            UUID id = existente == null ? o.id() : uuid(existente.get("id"));
            ObjectNode datos =
                    json.createObjectNode()
                            .put("idClase", clase.toString())
                            .put("nombre", o.nombre())
                            .put("visibilidad", o.visibilidad())
                            .put("esEstatico", o.estatica())
                            .put("esAbstracto", o.abstracta());
            ponerTexto(datos, "tipoRetorno", o.retorno());
            ArrayNode parametros = datos.putArray("parametros");
            for (var p : o.parametros()) {
                ObjectNode parametro =
                        parametros
                                .addObject()
                                .put("id", p.id().toString())
                                .put("nombre", p.nombre())
                                .put("tipoUml", p.tipo())
                                .put("direccion", p.direccion())
                                .put("esObligatorio", p.obligatorio());
                parametro.put("valorPredeterminado", p.predeterminado());
            }
            resultado.add(
                    operacion(
                            orden++,
                            existente == null ? "CREAR" : "ACTUALIZAR",
                            "OPERACION",
                            id,
                            o.nombre(),
                            datos));
        }

        Map<String, Map<String, Object>> relacionesPorClave = new LinkedHashMap<>();
        for (Map<String, Object> r : relacionesActuales)
            relacionesPorClave.put(claveRelacion(r), r);
        for (var r : analisis.relaciones()) {
            UUID origen = idsClase.get(r.origen()), destino = idsClase.get(r.destino());
            String llave = claveRelacion(origen, destino, r.tipo(), r.nombre());
            Map<String, Object> existente = fusionar ? relacionesPorClave.get(llave) : null;
            UUID id = existente == null ? r.id() : uuid(existente.get("id"));
            ObjectNode datos =
                    json.createObjectNode()
                            .put("origen", origen.toString())
                            .put("destino", destino.toString())
                            .put("tipo", r.tipo())
                            .put("direccion", r.direccion());
            ponerTexto(datos, "nombre", r.nombre());
            ponerTexto(datos, "rolOrigen", r.rolOrigen());
            ponerTexto(datos, "rolDestino", r.rolDestino());
            if (Set.of("ASOCIACION", "AGREGACION", "COMPOSICION").contains(r.tipo())) {
                ponerEntero(datos, "minimoOrigen", r.minimoOrigen(), 1);
                ponerMaximo(datos, "maximoOrigen", r.maximoOrigen());
                ponerEntero(datos, "minimoDestino", r.minimoDestino(), 1);
                ponerMaximo(datos, "maximoDestino", r.maximoDestino());
            }
            resultado.add(
                    operacion(
                            orden++,
                            existente == null ? "CREAR" : "ACTUALIZAR",
                            "RELACION",
                            id,
                            r.nombre() == null ? r.tipo() : r.nombre(),
                            datos));
        }
        return new Construccion(
                List.copyOf(resultado), List.copyOf(advertencias), List.copyOf(bloqueadores));
    }

    private ObjectNode informe(
            AnalizadorXmi21Service.Analisis analisis,
            long revisionBase,
            Construccion construccion) {
        ObjectNode informe = json.createObjectNode();
        informe.put("revisionBase", revisionBase);
        informe.put("varianteXmi", analisis.varianteXmi());
        informe.put("totalPaquetes", analisis.paquetes().size());
        informe.put("totalClases", analisis.clases().size());
        informe.put("totalAtributos", analisis.atributos().size());
        informe.put("totalOperaciones", analisis.operaciones().size());
        informe.put("totalRelaciones", analisis.relaciones().size());
        informe.set("advertencias", json.valueToTree(construccion.advertencias()));
        informe.set("bloqueadores", json.valueToTree(construccion.bloqueadores()));
        informe.set("operaciones", json.valueToTree(construccion.operaciones()));
        return informe;
    }

    private Importacion detalleAutorizado(
            Identidad actor, CabeceraModelo cabecera, UUID intercambio) {
        autorizar(actor, cabecera, "IMPORTAR_XMI", "revisar importaciones XMI");
        return convertir(repo.importacion(cabecera.id(), intercambio), cabecera.revision());
    }

    private Importacion convertir(IntercambioImportacion fila, long revisionActual) {
        JsonNode ambito = leerJson(fila.ambito());
        JsonNode informe = leerJson(fila.informe());
        long revisionBase = ambito.path("revisionBase").asLong(-1);
        List<String> advertencias = textos(informe.path("advertencias"));
        List<String> bloqueadores = textos(informe.path("bloqueadores"));
        List<OperacionImportacion> operaciones = operaciones(informe.path("operaciones"));
        boolean puedeAplicar =
                "LISTO_PARA_REVISION".equals(fila.estado())
                        && revisionBase == revisionActual
                        && bloqueadores.isEmpty()
                        && !operaciones.isEmpty();
        return new Importacion(
                fila.id(),
                fila.archivoEntrada(),
                fila.nombreArchivo(),
                fila.estado(),
                fila.estrategia(),
                fila.variante(),
                fila.versionEnterpriseArchitect(),
                revisionBase,
                revisionActual,
                informe.path("totalPaquetes").asInt(),
                informe.path("totalClases").asInt(),
                informe.path("totalAtributos").asInt(),
                informe.path("totalOperaciones").asInt(),
                informe.path("totalRelaciones").asInt(),
                advertencias,
                bloqueadores,
                operaciones,
                puedeAplicar,
                fila.detalleError());
    }

    // ─── Datos y alcance de exportación ─────────────────────────────────────

    private Datos datosActuales(UUID modelo, String nombreModelo) {
        return new Datos(
                nombreModelo,
                repo.leerPaquetes(modelo),
                repo.leerClases(modelo),
                repo.leerAtributos(modelo),
                repo.leerOperaciones(modelo),
                repo.leerParametros(modelo),
                repo.leerRelaciones(modelo),
                repo.leerPosicionesClases(modelo),
                repo.leerPosicionesRelaciones(modelo));
    }

    private Datos datosVersion(UUID modelo, UUID version) {
        JsonNode instantanea = repo.leerInstantanea(modelo, version);
        String nombre = instantanea.path("modelo").path("nombre").asText("Modelo");
        return new Datos(
                nombre,
                mapas(instantanea.path("paquetes")),
                mapas(instantanea.path("clases")),
                mapas(instantanea.path("atributos")),
                mapas(instantanea.path("operaciones")),
                mapas(instantanea.path("parametros")),
                mapas(instantanea.path("relaciones")),
                mapas(instantanea.path("posiciones_clases")),
                mapas(instantanea.path("posiciones_relaciones")));
    }

    private Datos filtrarAlcance(
            Datos datos, UUID paquete, boolean incluirSubpaquetes, List<String> advertencias) {
        if (paquete == null) return datos;
        Map<UUID, Map<String, Object>> indice = new LinkedHashMap<>();
        for (Map<String, Object> p : datos.paquetes()) indice.put(uuid(p.get("id")), p);
        if (!indice.containsKey(paquete))
            throw new ApiException(404, "Paquete no disponible en la versión seleccionada.");
        Set<UUID> incluidos = new LinkedHashSet<>();
        incluidos.add(paquete);
        if (incluirSubpaquetes) {
            boolean cambio;
            do {
                cambio = false;
                for (Map<String, Object> p : datos.paquetes()) {
                    UUID padre = uuidNullable(p.get("id_paquete_padre"));
                    if (padre != null && incluidos.contains(padre))
                        cambio |= incluidos.add(uuid(p.get("id")));
                }
            } while (cambio);
        }
        List<Map<String, Object>> paquetes =
                datos.paquetes().stream()
                        .filter(p -> incluidos.contains(uuid(p.get("id"))))
                        .map(LinkedHashMap::new)
                        .map(
                                p -> {
                                    UUID padre = uuidNullable(p.get("id_paquete_padre"));
                                    if (padre == null || !incluidos.contains(padre))
                                        p.put("id_paquete_padre", null);
                                    return (Map<String, Object>) p;
                                })
                        .toList();
        List<Map<String, Object>> clases =
                datos.clases().stream()
                        .filter(c -> incluidos.contains(uuidNullable(c.get("id_paquete"))))
                        .toList();
        Set<UUID> idsClase = new HashSet<>();
        clases.forEach(c -> idsClase.add(uuid(c.get("id"))));
        List<Map<String, Object>> atributos =
                datos.atributos().stream()
                        .filter(a -> idsClase.contains(uuid(a.get("id_clase"))))
                        .toList();
        List<Map<String, Object>> operaciones =
                datos.operaciones().stream()
                        .filter(o -> idsClase.contains(uuid(o.get("id_clase"))))
                        .toList();
        Set<UUID> idsOperacion = new HashSet<>();
        operaciones.forEach(o -> idsOperacion.add(uuid(o.get("id"))));
        List<Map<String, Object>> parametros =
                datos.parametros().stream()
                        .filter(p -> idsOperacion.contains(uuid(p.get("id_operacion"))))
                        .toList();
        List<Map<String, Object>> relaciones =
                datos.relaciones().stream()
                        .filter(
                                r ->
                                        idsClase.contains(uuid(r.get("id_clase_origen")))
                                                && idsClase.contains(
                                                        uuid(r.get("id_clase_destino"))))
                        .toList();
        long omitidas = datos.relaciones().size() - relaciones.size();
        if (omitidas > 0)
            advertencias.add(
                    "Se omitieron "
                            + omitidas
                            + " relaciones con extremos fuera del alcance seleccionado.");

        List<Map<String, Object>> posicionesClases =
                datos.posicionesClases().stream()
                        .filter(p -> idsClase.contains(uuidNullable(p.get("id_clase"))))
                        .toList();
        Set<UUID> idsRelacion = new HashSet<>();
        relaciones.forEach(r -> idsRelacion.add(uuid(r.get("id"))));
        List<Map<String, Object>> posicionesRelaciones =
                datos.posicionesRelaciones().stream()
                        .filter(p -> idsRelacion.contains(uuidNullable(p.get("id_relacion"))))
                        .toList();

        return new Datos(
                datos.nombre(),
                paquetes,
                clases,
                atributos,
                operaciones,
                parametros,
                relaciones,
                posicionesClases,
                posicionesRelaciones);
    }

    private void agregarAdvertenciasTipos(Datos datos, List<String> advertencias) {
        LinkedHashSet<String> personalizados = new LinkedHashSet<>();
        for (Map<String, Object> a : datos.atributos()) {
            String tipo = Objects.toString(a.get("tipo_uml"), "String");
            if (!TIPOS_PRIMITIVOS.contains(tipo)) personalizados.add(tipo);
        }
        for (Map<String, Object> o : datos.operaciones()) {
            String tipo = Objects.toString(o.get("tipo_retorno"), "");
            if (!tipo.isBlank() && !TIPOS_PRIMITIVOS.contains(tipo)) personalizados.add(tipo);
        }
        if (!personalizados.isEmpty())
            advertencias.add(
                    "Los tipos personalizados se exportan por nombre: "
                            + String.join(", ", personalizados)
                            + ".");
    }

    // ─── Validación, conversión y utilidades ─────────────────────────────────

    private void validarDuplicados(
            AnalizadorXmi21Service.Analisis analisis, Set<String> bloqueadores) {
        Set<String> clases = new HashSet<>(), paquetes = new HashSet<>(), hijos = new HashSet<>();
        for (var p : analisis.paquetes())
            if (!paquetes.add(p.padre() + "|" + clave(p.nombre())))
                bloqueadores.add("El XMI contiene paquetes duplicados: " + p.nombre() + ".");
        for (var c : analisis.clases())
            if (!clases.add(c.paquete() + "|" + clave(c.nombre())))
                bloqueadores.add("El XMI contiene clases duplicadas: " + c.nombre() + ".");
        for (var a : analisis.atributos())
            if (!hijos.add(a.clase() + "|A|" + clave(a.nombre())))
                bloqueadores.add("Una clase contiene el atributo duplicado " + a.nombre() + ".");
        for (var o : analisis.operaciones())
            if (!hijos.add(
                    o.clase()
                            + "|O|"
                            + clave(o.nombre())
                            + "|"
                            + o.parametros().stream()
                                    .map(p -> p.tipo() + ":" + p.direccion())
                                    .toList()))
                bloqueadores.add("Una clase contiene la operación duplicada " + o.nombre() + ".");
    }

    private OperacionImportacion operacion(
            int orden, String tipo, String entidad, UUID id, String nombre, JsonNode datos) {
        return new OperacionImportacion(orden, tipo, entidad, id, nombre, datos);
    }

    private OperacionImportacion eliminar(int orden, String entidad, UUID id, String nombre) {
        return operacion(orden, "ELIMINAR", entidad, id, nombre, NullNode.instance);
    }

    private Map<String, Map<String, Object>> porNombre(List<Map<String, Object>> elementos) {
        Map<String, Map<String, Object>> resultado = new LinkedHashMap<>();
        for (Map<String, Object> e : elementos) resultado.putIfAbsent(clave(nombreMapa(e)), e);
        return resultado;
    }

    private Map<String, Map<String, Object>> porPadreYNombre(
            List<Map<String, Object>> elementos, String campoPadre) {
        Map<String, Map<String, Object>> resultado = new LinkedHashMap<>();
        for (Map<String, Object> e : elementos)
            resultado.put(Objects.toString(e.get(campoPadre), "") + "|" + clave(nombreMapa(e)), e);
        return resultado;
    }

    private String claveRelacion(Map<String, Object> r) {
        return claveRelacion(
                uuid(r.get("id_clase_origen")),
                uuid(r.get("id_clase_destino")),
                Objects.toString(r.get("tipo_relacion"), "ASOCIACION"),
                Objects.toString(r.get("nombre"), null));
    }

    private String claveRelacion(UUID origen, UUID destino, String tipo, String nombre) {
        return origen + "|" + destino + "|" + tipo + "|" + clave(nombre);
    }

    private int profundidad(Map<String, Object> paquete, List<Map<String, Object>> todos) {
        Map<UUID, UUID> padres = new HashMap<>();
        for (Map<String, Object> p : todos)
            padres.put(uuid(p.get("id")), uuidNullable(p.get("id_paquete_padre")));
        int profundidad = 0;
        UUID actual = uuidNullable(paquete.get("id_paquete_padre"));
        Set<UUID> visitados = new HashSet<>();
        while (actual != null && visitados.add(actual)) {
            profundidad++;
            actual = padres.get(actual);
        }
        return profundidad;
    }

    private List<Map<String, Object>> mapas(JsonNode nodo) {
        if (!nodo.isArray()) return List.of();
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (JsonNode item : nodo)
            resultado.add(
                    json.convertValue(item, new TypeReference<LinkedHashMap<String, Object>>() {}));
        return List.copyOf(resultado);
    }

    private List<OperacionImportacion> operaciones(JsonNode nodo) {
        if (!nodo.isArray()) return List.of();
        try {
            return json.convertValue(nodo, new TypeReference<List<OperacionImportacion>>() {});
        } catch (IllegalArgumentException e) {
            throw new ApiException(422, "El informe de importación está dañado.");
        }
    }

    private List<String> textos(JsonNode nodo) {
        if (!nodo.isArray()) return List.of();
        List<String> resultado = new ArrayList<>();
        nodo.forEach(v -> resultado.add(v.asText()));
        return List.copyOf(resultado);
    }

    private byte[] leerArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty())
            throw new ApiException(400, "Selecciona un archivo XMI.");
        if (archivo.getSize() > MAX_XMI_BYTES)
            throw new ApiException(413, "El archivo XMI supera el límite de 5 MB.");
        String tipo = tipoContenido(archivo.getContentType());
        if (!Set.of(
                        "application/xml",
                        "text/xml",
                        "application/xmi+xml",
                        "application/octet-stream")
                .contains(tipo))
            throw new ApiException(415, "El tipo de archivo XMI no es válido.");
        try {
            byte[] contenido = archivo.getBytes();
            String inicio =
                    new String(
                                    contenido,
                                    0,
                                    Math.min(contenido.length, 256),
                                    StandardCharsets.UTF_8)
                            .replace("\uFEFF", "")
                            .stripLeading();
            if (!inicio.startsWith("<"))
                throw new ApiException(415, "La firma del archivo no corresponde a XML.");
            return contenido;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "No se pudo leer el archivo XMI.");
        }
    }

    private void autorizar(Identidad actor, CabeceraModelo c, String permiso, String accion) {
        if (!"ACTIVO".equals(c.estadoProyecto()) || "ARCHIVADO".equals(c.estado()))
            throw new ApiException(404, "Modelo no disponible.");
        if (actor.esAdministrador()) return;
        if (!repo.permiso(actor.id(), c.proyecto(), permiso))
            throw new ApiException(403, "No tienes permiso para " + accion + " en este modelo.");
    }

    private String estrategia(String valor) {
        String normalizada = valor == null ? "FUSIONAR" : valor.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("FUSIONAR", "REEMPLAZAR").contains(normalizada))
            throw new ApiException(400, "La estrategia debe ser FUSIONAR o REEMPLAZAR.");
        return normalizada;
    }

    private JsonNode leerJson(String valor) {
        try {
            return valor == null ? json.createObjectNode() : json.readTree(valor);
        } catch (Exception e) {
            throw new ApiException(422, "Los metadatos del intercambio XMI están dañados.");
        }
    }

    private String escribir(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el intercambio XMI.", e);
        }
    }

    private UUID uuid(Object valor) {
        if (valor instanceof UUID id) return id;
        return UUID.fromString(Objects.toString(valor));
    }

    private UUID uuidNullable(Object valor) {
        return valor == null ? null : uuid(valor);
    }

    private String clave(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT);
    }

    private String nombreMapa(Map<String, Object> valor) {
        return Objects.toString(valor.get("nombre"), "Sin nombre");
    }

    private void ponerTexto(ObjectNode nodo, String campo, String valor) {
        if (valor == null || valor.isBlank()) nodo.putNull(campo);
        else nodo.put(campo, valor);
    }

    private void ponerUuid(ObjectNode nodo, String campo, UUID valor) {
        if (valor == null) nodo.putNull(campo);
        else nodo.put(campo, valor.toString());
    }

    private void ponerEntero(ObjectNode nodo, String campo, Integer valor, int defecto) {
        nodo.put(campo, valor == null ? defecto : valor);
    }

    private void ponerMaximo(ObjectNode nodo, String campo, Integer valor) {
        if (valor == null) nodo.putNull(campo);
        else nodo.put(campo, valor);
    }

    private String tipoContenido(String valor) {
        if (valor == null || valor.isBlank()) return "application/octet-stream";
        return valor.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String nombreEntrada(String original) {
        String nombre =
                original == null ? "modelo.xmi" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (nombre.length() > 255) nombre = nombre.substring(nombre.length() - 255);
        return nombre.isBlank() ? "modelo.xmi" : nombre;
    }

    private String nombreArchivo(String nombreModelo, UUID paquete) {
        String seguro = nombreModelo.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return seguro + (paquete == null ? "" : "_paquete") + "_xmi21.xmi";
    }

    private String nombreArchivoVbs(String nombreModelo, UUID paquete) {
        String seguro = nombreModelo.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return seguro + (paquete == null ? "" : "_paquete") + "_ea.vbs";
    }
}
