package bo.edu.uml.xmi.servicio;

import bo.edu.uml.compartido.ApiException;

import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Analizador XMI 2.x sin resolución de entidades, DTD ni recursos externos. */
@Service
public class AnalizadorXmi21Service {

    private static final int MAX_ELEMENTOS_XML = 10_000;
    private static final int MAX_ELEMENTOS_MODELO = 500;

    public record Paquete(UUID id, UUID padre, String nombre, String descripcion) {}

    public record Clase(
            UUID id,
            UUID paquete,
            String nombre,
            String visibilidad,
            boolean abstracta,
            double x,
            double y,
            double ancho,
            double alto,
            boolean persistente,
            String estereotipo,
            String descripcion) {}

    public record Atributo(
            UUID id,
            UUID clase,
            String nombre,
            String tipo,
            String visibilidad,
            int minimo,
            Integer maximo,
            boolean identificador,
            boolean estatico,
            boolean ordenado,
            boolean coleccionUnica,
            boolean unicoSql,
            String predeterminado,
            String restricciones) {}

    public record Parametro(
            UUID id,
            String nombre,
            String tipo,
            String direccion,
            boolean obligatorio,
            int orden,
            String predeterminado) {}

    public record Operacion(
            UUID id,
            UUID clase,
            String nombre,
            String retorno,
            String visibilidad,
            boolean estatica,
            boolean abstracta,
            List<Parametro> parametros) {}

    public record Relacion(
            UUID id,
            UUID origen,
            UUID destino,
            String nombre,
            String tipo,
            String rolOrigen,
            String rolDestino,
            Integer minimoOrigen,
            Integer maximoOrigen,
            Integer minimoDestino,
            Integer maximoDestino,
            String direccion) {}

    public record Analisis(
            String nombreModelo,
            String varianteXmi,
            String versionEnterpriseArchitect,
            List<Paquete> paquetes,
            List<Clase> clases,
            List<Atributo> atributos,
            List<Operacion> operaciones,
            List<Relacion> relaciones,
            List<String> advertencias) {}

    private record RelacionPendiente(
            UUID id,
            String origen,
            String destino,
            String nombre,
            String tipo,
            String rolOrigen,
            String rolDestino,
            Integer minimoOrigen,
            Integer maximoOrigen,
            Integer minimoDestino,
            Integer maximoDestino) {}

    private static final class Estado {
        private final UUID semilla;
        private final Map<String, UUID> ids = new LinkedHashMap<>();
        private final List<Paquete> paquetes = new ArrayList<>();
        private final List<Clase> clases = new ArrayList<>();
        private final List<Atributo> atributos = new ArrayList<>();
        private final List<Operacion> operaciones = new ArrayList<>();
        private final List<RelacionPendiente> relaciones = new ArrayList<>();
        private final List<Element> elementosAsociacion = new ArrayList<>();
        private final Map<String, Element> propiedadesPorId = new HashMap<>();
        private final Map<String, String> claseDePropiedad = new HashMap<>();
        private final Set<String> extremosAsociacion = new HashSet<>();
        private final LinkedHashSet<String> advertencias = new LinkedHashSet<>();
        private final Map<String, String> tiposPorId = new HashMap<>();
        private final Map<String, Element> metadata = new HashMap<>();
        private final Map<UUID, String> direcciones = new HashMap<>();
        private boolean preferirNucleo;
        private int anonimos;

        private Estado(UUID semilla) {
            this.semilla = semilla;
        }

        private UUID id(String referencia) {
            String llave =
                    referencia == null || referencia.isBlank()
                            ? "anonimo-" + anonimos++
                            : referencia;
            return ids.computeIfAbsent(
                    limpiarReferencia(llave),
                    valor ->
                            UUID.nameUUIDFromBytes(
                                    (semilla + ":" + valor).getBytes(StandardCharsets.UTF_8)));
        }
    }

    public Analisis analizar(byte[] contenido, UUID semilla) {
        if (contenido == null || contenido.length == 0)
            throw new ApiException(422, "El archivo XMI está vacío.");
        Document documento = documentoSeguro(contenido);
        if (documento.getElementsByTagName("*").getLength() > MAX_ELEMENTOS_XML)
            throw new ApiException(422, "El XMI supera el límite de elementos XML.");

        Element raiz = documento.getDocumentElement();
        if (raiz == null || !"XMI".equalsIgnoreCase(local(raiz)))
            throw new ApiException(422, "El archivo no contiene una raíz XMI.");
        String version = atributoLocal(raiz, "version");
        if (version == null || !version.startsWith("2."))
            throw new ApiException(422, "Solo se admiten documentos XMI 2.x.");

        Element modelo = buscarModelo(raiz);
        if (modelo == null) throw new ApiException(422, "El XMI no contiene un modelo UML.");

        Estado estado = new Estado(semilla);
        estado.preferirNucleo = !"2.1".equals(version);
        preindexar(raiz, estado);
        if (!Set.of("2.1", "2.4.1", "2.4.2", "2.5", "2.5.1").contains(version))
            estado.advertencias.add(
                    "Variante XMI "
                            + version
                            + " no verificada; solo se interpreta el subconjunto de clases.");
        if ("2.1".equals(version))
            estado.advertencias.add(
                    "Archivo antiguo: no se infiere conformidad UML a partir de XMI 2.1.");
        recorrer(modelo, null, estado);
        for (Element e : estado.elementosAsociacion) {
            leerRelacion(e, estado);
        }
        recuperarConectoresEa(raiz, estado);
        int total =
                estado.paquetes.size()
                        + estado.clases.size()
                        + estado.atributos.size()
                        + estado.operaciones.size()
                        + estado.relaciones.size();
        if (total == 0)
            throw new ApiException(422, "El XMI no contiene elementos UML importables.");
        if (total > MAX_ELEMENTOS_MODELO)
            throw new ApiException(422, "El XMI supera el límite de 500 elementos importables.");

        recuperarDisposicionVisualEa(raiz, estado);
        for (int i = 0; i < estado.clases.size(); i++) {
            Clase c = estado.clases.get(i);
            String key = referenciaOriginal(c.id(), estado);
            if (meta(estado, key, "x") != null && meta(estado, key, "y") != null) {
                double x = Double.parseDouble(meta(estado, key, "x")),
                        y = Double.parseDouble(meta(estado, key, "y"));
                double
                        ancho =
                                Double.parseDouble(
                                        Objects.toString(meta(estado, key, "ancho"), "240")),
                        alto =
                                Double.parseDouble(
                                        Objects.toString(meta(estado, key, "alto"), "160"));
                if (!Double.isFinite(x)
                        || !Double.isFinite(y)
                        || !Double.isFinite(ancho)
                        || !Double.isFinite(alto)
                        || ancho <= 0
                        || alto <= 0) throw new ApiException(422, "Layout de producto inválido.");
                estado.clases.set(
                        i,
                        new Clase(
                                c.id(),
                                c.paquete(),
                                c.nombre(),
                                c.visibilidad(),
                                c.abstracta(),
                                x,
                                y,
                                ancho,
                                alto,
                                c.persistente(),
                                c.estereotipo(),
                                c.descripcion()));
            }
        }
        List<Relacion> relaciones = resolverRelaciones(estado);
        String exportador = versionEnterpriseArchitect(raiz);
        String nombreModelo = nombre(modelo, "Modelo importado");
        if ("EA_Model".equalsIgnoreCase(nombreModelo) && !estado.paquetes.isEmpty()) {
            nombreModelo = estado.paquetes.get(0).nombre();
        }
        return new Analisis(
                nombreModelo,
                "XMI_" + version,
                exportador,
                List.copyOf(estado.paquetes),
                List.copyOf(estado.clases),
                List.copyOf(estado.atributos),
                List.copyOf(estado.operaciones),
                List.copyOf(relaciones),
                List.copyOf(estado.advertencias));
    }

    private void recuperarDisposicionVisualEa(Element raiz, Estado estado) {
        NodeList nodos = raiz.getElementsByTagName("*");
        Map<String, double[]> coordenadasPorReferencia = new HashMap<>();

        for (int i = 0; i < nodos.getLength(); i++) {
            if (nodos.item(i) instanceof Element elem) {
                if ("element".equalsIgnoreCase(local(elem)) && elem.hasAttribute("geometry")) {
                    String subject = atributo(elem, "subject");
                    if (subject == null || subject.isBlank()) subject = atributo(elem, "xmi:idref");
                    String geometry = atributo(elem, "geometry");
                    if (subject != null && geometry != null) {
                        double[] xy = extraerCoordenadasGeometry(geometry, !estado.preferirNucleo);
                        if (xy != null) {
                            coordenadasPorReferencia.put(limpiarReferencia(subject), xy);
                        }
                    }
                }
            }
        }

        if (coordenadasPorReferencia.isEmpty()) return;

        int actualizadas = 0;
        for (int i = 0; i < estado.clases.size(); i++) {
            Clase c = estado.clases.get(i);
            for (Map.Entry<String, UUID> entry : estado.ids.entrySet()) {
                if (entry.getValue().equals(c.id())
                        && coordenadasPorReferencia.containsKey(entry.getKey())) {
                    double[] xy = coordenadasPorReferencia.get(entry.getKey());
                    estado.clases.set(
                            i,
                            new Clase(
                                    c.id(),
                                    c.paquete(),
                                    c.nombre(),
                                    c.visibilidad(),
                                    c.abstracta(),
                                    xy[0],
                                    xy[1],
                                    xy[2],
                                    xy[3],
                                    c.persistente(),
                                    c.estereotipo(),
                                    c.descripcion()));
                    actualizadas++;
                    break;
                }
            }
        }

        if (actualizadas > 0) {
            estado.advertencias.add(
                    "Se recuperó la disposición visual del diagrama de clases de Enterprise"
                        + " Architect ("
                            + actualizadas
                            + " elementos posicionados).");
        }
    }

    private double[] extraerCoordenadasGeometry(String geometry, boolean legado) {
        try {
            double left = 60, top = 60, right = 300, bottom = 220;
            boolean tieneLeft = false, tieneTop = false;
            for (String parte : geometry.split(";")) {
                String[] kv = parte.split("=", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().toLowerCase(Locale.ROOT);
                    String v = kv[1].trim();
                    if ("left".equals(k)) {
                        left = Double.parseDouble(v);
                        tieneLeft = true;
                    } else if ("top".equals(k)) {
                        double val = Double.parseDouble(v);
                        top = legado ? Math.abs(val) : -val;
                        tieneTop = true;
                    } else if ("right".equals(k)) right = Double.parseDouble(v);
                    else if ("bottom".equals(k))
                        bottom = legado ? Math.abs(Double.parseDouble(v)) : -Double.parseDouble(v);
                }
            }
            if (tieneLeft || tieneTop)
                return new double[] {
                    left, top, Math.max(1, right - left), Math.max(1, bottom - top)
                };
        } catch (Exception ignored) {
        }
        return null;
    }

    private Document documentoSeguro(byte[] contenido) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var constructor = f.newDocumentBuilder();
            constructor.setErrorHandler(
                    new ErrorHandler() {
                        @Override
                        public void warning(SAXParseException e) throws SAXException {
                            throw e;
                        }

                        @Override
                        public void error(SAXParseException e) throws SAXException {
                            throw e;
                        }

                        @Override
                        public void fatalError(SAXParseException e) throws SAXException {
                            throw e;
                        }
                    });
            constructor.setEntityResolver(
                    (publicId, systemId) -> {
                        throw new SAXException("Las entidades externas no están permitidas.");
                    });
            return constructor.parse(new ByteArrayInputStream(contenido));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No se pudo configurar el analizador XML seguro.", e);
        } catch (SAXException | IOException e) {
            throw new ApiException(422, "El archivo XMI no es XML seguro y bien formado.");
        }
    }

    private Element buscarModelo(Element raiz) {
        if (esTipo(raiz, "Model") || "Model".equals(local(raiz))) return raiz;
        NodeList elementos = raiz.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elementos.getLength(); i++) {
            if (elementos.item(i) instanceof Element e
                    && ("Model".equals(local(e)) || esTipo(e, "Model"))) return e;
        }
        return null;
    }

    private void recorrer(Element contenedor, UUID padre, Estado estado) {
        for (Element e : hijos(contenedor)) {
            String tipo = tipo(e);
            if (es(tipo, "Package") || "Package".equals(local(e))) {
                String referencia = identificador(e);
                UUID id = estado.id(referencia);
                estado.paquetes.add(
                        new Paquete(id, padre, nombreObligatorio(e, "paquete"), comentario(e)));
                recorrer(e, id, estado);
            } else if (es(tipo, "Class") || "Class".equals(local(e))) {
                leerClase(e, padre, estado);
            } else if (es(tipo, "Association") || "Association".equals(local(e))) {
                estado.elementosAsociacion.add(e);
            } else if (es(tipo, "Dependency") || "Dependency".equals(local(e))) {
                leerRelacion(e, estado);
            } else if (es(tipo, "PrimitiveType")) {
                // Declaración de tokens de tipos; no se convierte en una clase.
            } else if (esElementoEmpaquetado(e) && tipo != null && !tipo.isBlank()) {
                estado.advertencias.add("Se omitió el elemento UML no soportado " + tipo + ".");
            }
        }
    }

    private void leerClase(Element e, UUID paquete, Estado estado) {
        String referencia = identificador(e);
        UUID id = estado.id(referencia);
        int indice = estado.clases.size();
        estado.clases.add(
                new Clase(
                        id,
                        paquete,
                        nombreObligatorio(e, "clase"),
                        visibilidad(e),
                        bool(e, "isAbstract"),
                        60 + (indice % 4) * 300,
                        60 + (indice / 4) * 220,
                        240,
                        160,
                        metaBool(estado, referencia, "es_persistente", !estado.preferirNucleo),
                        meta(estado, referencia, "estereotipo"),
                        comentario(e)));
        for (Element hijo : hijos(e)) {
            switch (local(hijo)) {
                case "ownedAttribute" -> {
                    String propId = identificador(hijo);
                    if (propId != null) {
                        estado.propiedadesPorId.put(limpiarReferencia(propId), hijo);
                        estado.claseDePropiedad.put(limpiarReferencia(propId), referencia);
                    }
                    leerAtributo(hijo, id, estado);
                }
                case "ownedOperation" -> leerOperacion(hijo, id, estado);
                case "generalization" -> {
                    String destino = referencia(hijo, "general");
                    if (destino != null)
                        estado.relaciones.add(
                                new RelacionPendiente(
                                        estado.id(identificador(hijo)),
                                        referencia,
                                        destino,
                                        null,
                                        "GENERALIZACION",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null));
                }
                default -> {
                    // Metadatos propios de herramientas se ignoran de manera explícita.
                }
            }
        }
    }

    private void leerAtributo(Element e, UUID clase, Estado estado) {
        if (e.hasAttribute("association")
                || estado.extremosAsociacion.contains(limpiarReferencia(identificador(e)))) {
            estado.advertencias.add(
                    "Los extremos de asociación embebidos se normalizaron desde la asociación"
                            + " principal.");
            return;
        }
        int[] multiplicidad = multiplicidad(e);
        estado.atributos.add(
                new Atributo(
                        estado.id(identificador(e)),
                        clase,
                        nombreObligatorio(e, "atributo"),
                        tipoDato(e, estado),
                        visibilidad(e),
                        multiplicidad[0],
                        multiplicidad[1] < 0 ? null : multiplicidad[1],
                        bool(e, "isID"),
                        bool(e, "isStatic"),
                        bool(e, "isOrdered"),
                        booleanDefault(e, "isUnique", true),
                        metaBool(estado, identificador(e), "es_unico", false),
                        predeterminado(e),
                        meta(estado, identificador(e), "restricciones_json")));
    }

    private void leerOperacion(Element e, UUID clase, Estado estado) {
        List<Parametro> parametros = new ArrayList<>();
        String retorno = null;
        int orden = 0;
        for (Element hijo : hijos(e)) {
            if (!"ownedParameter".equals(local(hijo))) continue;
            String direccion = atributo(hijo, "direction");
            if ("return".equalsIgnoreCase(direccion)) {
                if (retorno != null)
                    throw new ApiException(422, "Una operación tiene más de un retorno.");
                retorno = tipoDato(hijo, estado);
            }

            validarParametroEscalar(hijo);
            parametros.add(
                    new Parametro(
                            estado.id(identificador(hijo)),
                            "return".equalsIgnoreCase(direccion)
                                    ? nombre(hijo, "resultado")
                                    : nombreObligatorio(hijo, "parámetro"),
                            tipoDato(hijo, estado),
                            direccionParametro(direccion),
                            multiplicidad(hijo)[0] > 0,
                            orden++,
                            predeterminado(hijo)));
        }
        estado.operaciones.add(
                new Operacion(
                        estado.id(identificador(e)),
                        clase,
                        nombreObligatorio(e, "operación"),
                        retorno,
                        visibilidad(e),
                        bool(e, "isStatic"),
                        bool(e, "isAbstract"),
                        List.copyOf(parametros)));
    }

    private void leerRelacion(Element e, Estado estado) {
        String tipoXmi = tipo(e);
        if (es(tipoXmi, "Dependency") || "Dependency".equals(local(e))) {
            if (Arrays.stream(
                            new String[] {
                                Objects.toString(atributo(e, "client"), ""),
                                Objects.toString(atributo(e, "supplier"), "")
                            })
                    .anyMatch(v -> v.trim().contains(" "))) {
                estado.advertencias.add(
                        "Se omitió una dependencia con varios clientes/proveedores: fuera del"
                            + " subconjunto binario.");
                return;
            }
            estado.relaciones.add(
                    new RelacionPendiente(
                            estado.id(identificador(e)),
                            referencia(e, "client"),
                            referencia(e, "supplier"),
                            nombre(e, null),
                            "DEPENDENCIA",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
            return;
        }

        List<Element> extremos = new ArrayList<>();
        // 1. ownedEnd directos
        for (Element hijo : hijos(e)) {
            if ("ownedEnd".equals(local(hijo))) {
                extremos.add(hijo);
            }
        }
        // 2. memberEnd (referencias a propiedades de clases o extremos)
        List<String> memberEndIds = new ArrayList<>();
        String attrMemberEnd = atributo(e, "memberEnd");
        if (attrMemberEnd != null && !attrMemberEnd.isBlank()) {
            for (String part : attrMemberEnd.split("\\s+")) {
                if (!part.isBlank()) memberEndIds.add(part.trim());
            }
        }
        for (Element hijo : hijos(e)) {
            if ("memberEnd".equals(local(hijo))) {
                String ref = atributo(hijo, "xmi:idref");
                if (ref == null) ref = atributo(hijo, "idref");
                if (ref == null) ref = referencia(hijo, "idref");
                if (ref != null && !ref.isBlank()) memberEndIds.add(ref.trim());
            }
        }

        for (String mId : memberEndIds) {
            String limpio = limpiarReferencia(mId);
            boolean yaEsta = extremos.stream().anyMatch(ex -> limpio.equals(limpiarReferencia(identificador(ex))));
            if (!yaEsta) {
                Element prop = estado.propiedadesPorId.get(limpio);
                if (prop != null) {
                    extremos.add(prop);
                }
            }
        }

        if (extremos.size() != 2) {
            estado.advertencias.add(
                    "Se omitió una asociación que no tiene exactamente dos extremos reconocibles"
                        + " (n-arias fuera de alcance).");
            return;
        }

        Element extremo0 = extremos.get(0), extremo1 = extremos.get(1);
        String ref0 = resolverClaseDeExtremo(extremo0, estado);
        String ref1 = resolverClaseDeExtremo(extremo1, estado);

        if (ref0 == null) {
            ref0 = estado.claseDePropiedad.get(limpiarReferencia(identificador(extremo0)));
        }
        if (ref1 == null) {
            ref1 = estado.claseDePropiedad.get(limpiarReferencia(identificador(extremo1)));
        }

        // El extremo con aggregation representa la propiedad tipada por la parte.
        if (Set.of("composite", "shared")
                .contains(Objects.toString(atributo(extremo0, "aggregation"), "none"))) {
            Element temp = extremo0;
            extremo0 = extremo1;
            extremo1 = temp;
            String ref = ref0;
            ref0 = ref1;
            ref1 = ref;
        }
        if (Set.of("composite", "shared")
                        .contains(Objects.toString(atributo(extremo0, "aggregation"), "none"))
                && Set.of("composite", "shared")
                        .contains(Objects.toString(atributo(extremo1, "aggregation"), "none")))
            throw new ApiException(422, "Una asociación no admite agregación en ambos extremos.");
        int[] card0 = multiplicidad(extremo0);
        int[] card1 = multiplicidad(extremo1);
        String agregacion = atributo(extremo1, "aggregation");
        if (agregacion == null || agregacion.isBlank() || "none".equalsIgnoreCase(agregacion)) {
            agregacion = atributo(extremo0, "aggregation");
        }
        String tipoRelacion =
                "composite".equalsIgnoreCase(agregacion)
                        ? "COMPOSICION"
                        : "shared".equalsIgnoreCase(agregacion) ? "AGREGACION" : "ASOCIACION";

        RelacionPendiente rel =
                new RelacionPendiente(
                        estado.id(identificador(e)),
                        ref0,
                        ref1,
                        nombre(e, null),
                        tipoRelacion,
                        nombre(extremo0, null),
                        nombre(extremo1, null),
                        card0[0],
                        card0[1] < 0 ? null : card0[1],
                        card1[0],
                        card1[1] < 0 ? null : card1[1]);
        registrarOActualizarRelacion(estado, rel);
        boolean nav0 = navegable(e, extremo0, estado), nav1 = navegable(e, extremo1, estado);
        estado.direcciones.put(
                rel.id(),
                nav0 && nav1
                        ? "BIDIRECCIONAL"
                        : nav0 ? "DESTINO_A_ORIGEN" : nav1 ? "ORIGEN_A_DESTINO" : "SIN_DIRECCION");
    }

    private String resolverClaseDeExtremo(Element extremo, Estado estado) {
        String ref = referencia(extremo, "type");
        if (ref != null && !ref.isBlank()) {
            String limpio = limpiarReferencia(ref);
            if (estado.ids.containsKey(limpio)
                    || estado.clases.stream().anyMatch(c -> c.id().equals(estado.ids.get(limpio)))) {
                return ref;
            }
        }
        for (Element hijo : hijos(extremo)) {
            if ("type".equals(local(hijo))) {
                String idref = atributo(hijo, "xmi:idref");
                if (idref == null) idref = atributo(hijo, "idref");
                if (idref == null) idref = referencia(hijo, "href");
                if (idref != null && !idref.isBlank()) return idref;
            }
        }
        return ref;
    }

    private void recuperarConectoresEa(Element raiz, Estado estado) {
        NodeList nodos = raiz.getElementsByTagName("*");
        int conectoresEncontrados = 0;
        for (int i = 0; i < nodos.getLength(); i++) {
            if (nodos.item(i) instanceof Element elem && "connector".equalsIgnoreCase(local(elem))) {
                if (procesarConectorEa(elem, estado)) {
                    conectoresEncontrados++;
                }
            }
        }
        if (conectoresEncontrados > 0) {
            estado.advertencias.add(
                    "Se recuperaron "
                            + conectoresEncontrados
                            + " relaciones desde la extensión de Enterprise Architect.");
        }
    }

    private boolean procesarConectorEa(Element conn, Estado estado) {
        String connId = atributo(conn, "xmi:idref");
        if (connId == null) connId = atributo(conn, "idref");
        if (connId == null) connId = identificador(conn);

        Element sourceElem = hijoPorNombre(conn, "source");
        Element targetElem = hijoPorNombre(conn, "target");
        Element propsElem = hijoPorNombre(conn, "properties");
        if (sourceElem == null || targetElem == null) return false;

        String sourceId = atributo(sourceElem, "xmi:idref");
        if (sourceId == null) sourceId = atributo(sourceElem, "idref");
        String targetId = atributo(targetElem, "xmi:idref");
        if (targetId == null) targetId = atributo(targetElem, "idref");

        sourceId = resolverReferenciaClaseEa(sourceElem, sourceId, estado);
        targetId = resolverReferenciaClaseEa(targetElem, targetId, estado);

        if (sourceId == null || targetId == null) return false;

        Element roleSrc = hijoPorNombre(sourceElem, "role");
        Element roleTgt = hijoPorNombre(targetElem, "role");
        String rolOrigen = roleSrc != null ? atributo(roleSrc, "name") : null;
        String rolDestino = roleTgt != null ? atributo(roleTgt, "name") : null;
        if (rolOrigen != null && rolOrigen.isBlank()) rolOrigen = null;
        if (rolDestino != null && rolDestino.isBlank()) rolDestino = null;

        Element typeSrc = hijoPorNombre(sourceElem, "type");
        Element typeTgt = hijoPorNombre(targetElem, "type");
        String multSrc = typeSrc != null ? atributo(typeSrc, "multiplicity") : null;
        String multTgt = typeTgt != null ? atributo(typeTgt, "multiplicity") : null;
        String aggSrc = typeSrc != null ? atributo(typeSrc, "aggregation") : null;
        String aggTgt = typeTgt != null ? atributo(typeTgt, "aggregation") : null;

        String eaType = propsElem != null ? atributo(propsElem, "ea_type") : null;
        String nombre = atributo(conn, "name");
        if (nombre == null || nombre.isBlank()) {
            nombre = propsElem != null ? atributo(propsElem, "name") : null;
        }
        if (nombre != null && nombre.isBlank()) nombre = null;

        String tipoRelacion;
        if ("Generalization".equalsIgnoreCase(eaType)) {
            tipoRelacion = "GENERALIZACION";
        } else if ("Realisation".equalsIgnoreCase(eaType)
                || "Realization".equalsIgnoreCase(eaType)) {
            estado.advertencias.add(
                    "Se omitió una realización EA: interfaces y realización fuera de alcance.");
            return false;
        } else if ("Dependency".equalsIgnoreCase(eaType)) {
            tipoRelacion = "DEPENDENCIA";
        } else if ("Composition".equalsIgnoreCase(eaType)
                || "composite".equalsIgnoreCase(aggSrc)
                || "composite".equalsIgnoreCase(aggTgt)) {
            tipoRelacion = "COMPOSICION";
        } else if ("Aggregation".equalsIgnoreCase(eaType)
                || "shared".equalsIgnoreCase(aggSrc)
                || "shared".equalsIgnoreCase(aggTgt)) {
            tipoRelacion = "AGREGACION";
        } else {
            tipoRelacion = "ASOCIACION";
        }

        Integer minO = null, maxO = null, minD = null, maxD = null;
        if (Set.of("ASOCIACION", "AGREGACION", "COMPOSICION").contains(tipoRelacion)) {
            int[] mo = parsearMultiplicidadString(multSrc);
            int[] md = parsearMultiplicidadString(multTgt);
            minO = mo[0];
            maxO = mo[1] < 0 ? null : mo[1];
            minD = md[0];
            maxD = md[1] < 0 ? null : md[1];
        }

        RelacionPendiente rel =
                new RelacionPendiente(
                        estado.id(connId),
                        sourceId,
                        targetId,
                        nombre,
                        tipoRelacion,
                        rolOrigen,
                        rolDestino,
                        minO,
                        maxO,
                        minD,
                        maxD);
        registrarOActualizarRelacion(estado, rel);

        return true;
    }

    private String resolverReferenciaClaseEa(Element endElem, String refId, Estado estado) {
        if (refId != null && !refId.isBlank()) {
            String limpio = limpiarReferencia(refId);
            if (estado.ids.containsKey(limpio)) return refId;
        }
        Element modelElem = hijoPorNombre(endElem, "model");
        if (modelElem != null) {
            String modelName = atributo(modelElem, "name");
            if (modelName != null && !modelName.isBlank()) {
                for (Clase c : estado.clases) {
                    if (c.nombre().equalsIgnoreCase(modelName.trim())) {
                        for (Map.Entry<String, UUID> entry : estado.ids.entrySet()) {
                            if (entry.getValue().equals(c.id())) {
                                return entry.getKey();
                            }
                        }
                    }
                }
            }
        }
        return refId;
    }

    private Element hijoPorNombre(Element padre, String nombre) {
        for (Element hijo : hijos(padre)) {
            if (nombre.equalsIgnoreCase(local(hijo))) return hijo;
        }
        return null;
    }

    private void registrarOActualizarRelacion(Estado estado, RelacionPendiente nueva) {
        for (int i = 0; i < estado.relaciones.size(); i++) {
            RelacionPendiente r = estado.relaciones.get(i);
            boolean mismoId = nueva.id() != null && nueva.id().equals(r.id());
            if (mismoId) {
                if (!estado.preferirNucleo) estado.relaciones.set(i, nueva);
                return;
            }
        }

        estado.relaciones.add(nueva);
    }

    private int[] parsearMultiplicidadString(String texto) {
        if (texto == null || texto.isBlank()) return new int[] {1, 1};
        texto = texto.trim();
        if ("*".equals(texto) || "-1".equals(texto) || "n".equalsIgnoreCase(texto)) {
            return new int[] {0, -1};
        }
        if (texto.contains("..")) {
            String[] partes = texto.split("\\.\\.", 2);
            int min = parsearEnteroSeguro(partes[0].trim(), 0);
            String maxStr = partes[1].trim();
            int max =
                    ("*".equals(maxStr) || "-1".equals(maxStr) || "n".equalsIgnoreCase(maxStr))
                            ? -1
                            : parsearEnteroSeguro(maxStr, 1);
            return new int[] {min, max};
        }
        int val = parsearEnteroSeguro(texto, 1);
        return new int[] {val, val};
    }

    private int parsearEnteroSeguro(String val, int defecto) {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defecto;
        }
    }

    private List<Relacion> resolverRelaciones(Estado estado) {
        List<Relacion> resultado = new ArrayList<>();
        for (RelacionPendiente r : estado.relaciones) {
            UUID origen = buscarIdClase(r.origen(), estado);
            UUID destino = buscarIdClase(r.destino(), estado);
            boolean clasesValidas =
                    origen != null
                            && destino != null
                            && estado.clases.stream().anyMatch(c -> c.id().equals(origen))
                            && estado.clases.stream().anyMatch(c -> c.id().equals(destino));
            if (!clasesValidas) {
                estado.advertencias.add(
                        "Se omitió una relación cuyos extremos no son clases del documento.");
                continue;
            }
            resultado.add(
                    new Relacion(
                            r.id(),
                            origen,
                            destino,
                            meta(estado, referenciaOriginal(r.id(), estado), "nombre") != null
                                    ? meta(estado, referenciaOriginal(r.id(), estado), "nombre")
                                    : r.nombre(),
                            r.tipo(),
                            r.rolOrigen(),
                            r.rolDestino(),
                            r.minimoOrigen(),
                            r.maximoOrigen(),
                            r.minimoDestino(),
                            r.maximoDestino(),
                            estado.direcciones.getOrDefault(
                                    r.id(),
                                    Set.of("GENERALIZACION", "DEPENDENCIA").contains(r.tipo())
                                            ? "ORIGEN_A_DESTINO"
                                            : "SIN_DIRECCION")));
        }
        return resultado;
    }

    private String referenciaOriginal(UUID id, Estado estado) {
        return estado.ids.entrySet().stream()
                .filter(e -> e.getValue().equals(id))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    private UUID buscarIdClase(String ref, Estado estado) {
        if (ref == null || ref.isBlank()) return null;
        String limpio = limpiarReferencia(ref);
        UUID id = estado.ids.get(limpio);
        if (id != null) return id;
        if (limpio.startsWith("_")) {
            id = estado.ids.get(limpio.substring(1));
            if (id != null) return id;
        } else {
            id = estado.ids.get("_" + limpio);
            if (id != null) return id;
        }
        for (Clase c : estado.clases) {
            if (c.nombre().equalsIgnoreCase(limpio)) {
                return c.id();
            }
        }
        return null;
    }

    private void preindexar(Element raiz, Estado estado) {
        Map<String, Integer> nombres = new HashMap<>();
        NodeList nodes = raiz.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++)
            if (nodes.item(i) instanceof Element e && esTipo(e, "Class"))
                nombres.merge(nombre(e, ""), 1, Integer::sum);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < nodes.getLength(); i++)
            if (nodes.item(i) instanceof Element e) {
                String key = identificador(e);
                if (key != null && !ids.add(key))
                    throw new ApiException(422, "xmi:id duplicado: " + key);
                if (GeneradorXmi251Service.PRODUCTO.equals(e.getNamespaceURI())
                        && "elemento".equals(local(e))) {
                    String metaKey = limpiarReferencia(atributo(e, "ref"));
                    Element anterior = estado.metadata.get(metaKey);
                    if (anterior == null) estado.metadata.put(metaKey, e);
                    else
                        for (int j = 0; j < e.getAttributes().getLength(); j++) {
                            Node a = e.getAttributes().item(j);
                            if (!a.getNodeName().equals("ref"))
                                anterior.setAttribute(a.getNodeName(), a.getNodeValue());
                        }
                }
                if (esTipo(e, "Association")) {
                    String ends = atributo(e, "memberEnd");
                    if (ends != null)
                        for (String ref : ends.split("\\s+"))
                            estado.extremosAsociacion.add(limpiarReferencia(ref));
                    for (Element end : hijos(e))
                        if ("memberEnd".equals(local(end))) {
                            String ref = referencia(end, "idref");
                            if (ref != null) estado.extremosAsociacion.add(limpiarReferencia(ref));
                        }
                }
                if (esTipo(e, "Class") || esTipo(e, "PrimitiveType")) {
                    String n = nombre(e, "");
                    if (esTipo(e, "Class") && nombres.getOrDefault(n, 0) > 1) {
                        Node parent = e.getParentNode();
                        while (parent instanceof Element p && esTipo(p, "Package")) {
                            n = nombre(p, "") + "." + n;
                            parent = p.getParentNode();
                        }
                    }
                    estado.tiposPorId.put(limpiarReferencia(key), n);
                }
                if (esTipo(e, "Interface")
                        || esTipo(e, "DataType")
                        || esTipo(e, "Enumeration")
                        || esTipo(e, "InterfaceRealization")
                        || esTipo(e, "GeneralizationSet"))
                    estado.advertencias.add(
                            "Elemento fuera de alcance: "
                                    + tipo(e)
                                    + ". No se convierte en clase ni dependencia.");
                if (Set.of(
                                "qualifier",
                                "ownedRule",
                                "templateBinding",
                                "templateParameter",
                                "profileApplication",
                                "packageImport",
                                "elementImport")
                        .contains(local(e)))
                    estado.advertencias.add("Contenido fuera del subconjunto omitido: " + local(e));
                if (esTipo(e, "Property")
                        && (bool(e, "isDerived")
                                || bool(e, "isReadOnly")
                                || atributo(e, "subsettedProperty") != null
                                || atributo(e, "redefinedProperty") != null))
                    estado.advertencias.add(
                            "Se omiten modificadores avanzados de la propiedad "
                                    + nombre(e, identificador(e))
                                    + ": derived/readOnly/subsets/redefines fuera de alcance.");
                if ("ownedEnd".equals(local(e))
                        && (bool(e, "isOrdered") || !booleanDefault(e, "isUnique", true)))
                    estado.advertencias.add(
                            "Orden y unicidad no predeterminados de extremo fuera de alcance;"
                                + " revisar "
                                    + identificador(e));
            }
    }

    private String meta(Estado e, String id, String key) {
        Element m = e.metadata.get(limpiarReferencia(id));
        return m == null ? null : atributo(m, key);
    }

    private boolean metaBool(Estado e, String id, String key, boolean defecto) {
        String v = meta(e, id, key);
        return v == null ? defecto : Boolean.parseBoolean(v);
    }

    private String predeterminado(Element e) {
        for (Element child : hijos(e))
            if ("defaultValue".equals(local(child))) {
                String v = atributo(child, "value");
                if (v != null) return v;
                if (esTipo(child, "OpaqueExpression")) {
                    String language = null, body = null;
                    for (Element part : hijos(child)) {
                        if ("language".equals(local(part))) language = part.getTextContent();
                        if ("body".equals(local(part))) body = part.getTextContent();
                    }
                    if ("uml-colaborativo:literal".equals(language)) return body;
                }
                // LiteralString sin value representa la cadena vacía; otros literales
                // numéricos/booleanos usan sus defaults normativos.
                if (esTipo(child, "LiteralString")) return "";
                if (esTipo(child, "LiteralBoolean")) return "false";
                if (esTipo(child, "LiteralInteger")
                        || esTipo(child, "LiteralReal")
                        || esTipo(child, "LiteralUnlimitedNatural")) return "0";
                throw new ApiException(
                        422,
                        "Expresión predeterminada fuera del lenguaje soportado; no se descarta.");
            }
        return null;
    }

    private void validarParametroEscalar(Element e) {
        int[] mult = multiplicidad(e);
        if (mult[1] != 1
                || mult[0] > 1
                || bool(e, "isOrdered")
                || !booleanDefault(e, "isUnique", true))
            throw new ApiException(
                    422,
                    "Este subconjunto solo admite parámetros escalares 0..1 o 1..1, no ordenados y"
                        + " únicos.");
    }

    private boolean navegable(Element assoc, Element end, Estado estado) {
        String id = identificador(end);
        if (estado.claseDePropiedad.containsKey(limpiarReferencia(id))) return true;
        String refs = atributo(assoc, "navigableOwnedEnd");
        if (refs != null && Arrays.asList(refs.split("\\s+")).contains(id)) return true;
        for (Element e : hijos(assoc))
            if ("navigableOwnedEnd".equals(local(e)) && Objects.equals(referencia(e, "idref"), id))
                return true;
        return false;
    }

    private List<Element> hijos(Element padre) {
        List<Element> resultado = new ArrayList<>();
        NodeList nodos = padre.getChildNodes();
        for (int i = 0; i < nodos.getLength(); i++) {
            if (nodos.item(i) instanceof Element e) resultado.add(e);
        }
        return resultado;
    }

    private int[] multiplicidad(Element e) {
        int minimo = 1, maximo = 1;
        for (Element hijo : hijos(e)) {
            String local = local(hijo);
            if ("lowerValue".equals(local)) minimo = numeroCardinalidad(hijo, 1);
            if ("upperValue".equals(local)) maximo = numeroCardinalidad(hijo, 1);
        }
        if (minimo < 0 || maximo < -1 || maximo >= 0 && maximo < minimo)
            throw new ApiException(
                    422, "Multiplicidad UML inválida; no se normaliza silenciosamente.");
        return new int[] {minimo, maximo};
    }

    private int numeroCardinalidad(Element e, int defecto) {
        String valor = atributo(e, "value");
        if ("*".equals(valor) || "-1".equals(valor)) return -1;
        try {
            return valor == null || valor.isBlank() ? 0 : Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            throw new ApiException(422, "Límite de multiplicidad no entero: " + valor);
        }
    }

    private String tipoDato(Element e, Estado estado) {
        String tipo = referencia(e, "type");
        if (tipo == null) {
            for (Element hijo : hijos(e)) {
                if ("type".equals(local(hijo))) {
                    tipo = referencia(hijo, "href");
                    break;
                }
            }
        }
        if (tipo == null || tipo.isBlank()) {
            estado.advertencias.add(
                    "Tipo ausente: se propone String como valor de producto; revisar antes de"
                        + " aplicar.");
            return "String";
        }
        String limpio = limpiarReferencia(tipo);
        if (estado.preferirNucleo && (limpio.equals("Real") || limpio.equals("UnlimitedNatural")))
            return limpio;
        if (estado.tiposPorId.containsKey(limpio)) return estado.tiposPorId.get(limpio);
        return switch (limpio.toLowerCase(Locale.ROOT)) {
            case "string" -> "String";
            case "integer", "int" -> "Integer";
            case "float" -> "Float";
            case "real", "double", "decimal" -> "Double";
            case "boolean" -> "Boolean";
            case "date", "localdate" -> "LocalDate";
            case "datetime", "localdatetime" -> "LocalDateTime";
            case "long", "unlimitednatural" -> "Long";
            default -> limitar(limpio, 180);
        };
    }

    private String visibilidad(Element e) {
        String valor = atributo(e, "visibility");
        if (valor == null) return "PUBLICO";
        return switch (valor.toLowerCase(Locale.ROOT)) {
            case "public" -> "PUBLICO";
            case "private" -> "PRIVADO";
            case "protected" -> "PROTEGIDO";
            case "package" -> "PAQUETE";
            default -> throw new ApiException(422, "Visibilidad UML no reconocida: " + valor);
        };
    }

    private String direccionParametro(String valor) {
        if (valor == null) return "ENTRADA";
        return switch (valor.toLowerCase(Locale.ROOT)) {
            case "return" -> "RETORNO";
            case "out" -> "SALIDA";
            case "inout" -> "ENTRADA_SALIDA";
            case "in" -> "ENTRADA";
            default ->
                    throw new ApiException(
                            422, "Dirección de parámetro UML no reconocida: " + valor);
        };
    }

    private String comentario(Element e) {
        String body = atributo(e, "body");
        for (Element c : hijos(e))
            if ("ownedComment".equals(local(c)))
                for (Element text : hijos(c))
                    if ("body".equals(local(text))) body = text.getTextContent();
        if (body == null) return null;
        return limitar(body, 4000);
    }

    private String versionEnterpriseArchitect(Element raiz) {
        NodeList documentos = raiz.getElementsByTagNameNS("*", "Documentation");
        if (documentos.getLength() == 0) return null;
        Element d = (Element) documentos.item(0);
        String exportador = atributo(d, "exporter"), version = atributo(d, "exporterVersion");
        if (exportador == null || !exportador.toLowerCase(Locale.ROOT).contains("enterprise"))
            return null;
        return version == null || version.isBlank() ? "no declarada" : limitar(version, 60);
    }

    private boolean esElementoEmpaquetado(Element e) {
        return Set.of("packagedElement", "ownedMember").contains(local(e));
    }

    private boolean esTipo(Element e, String esperado) {
        return es(tipo(e), esperado);
    }

    private boolean es(String tipo, String esperado) {
        return tipo != null
                && (tipo.equalsIgnoreCase(esperado)
                        || tipo.toLowerCase(Locale.ROOT)
                                .endsWith(":" + esperado.toLowerCase(Locale.ROOT)));
    }

    private String tipo(Element e) {
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if ("type".equals(a.getLocalName()) && a.getPrefix() != null) return a.getNodeValue();
        }
        return atributo(e, "type");
    }

    private String identificador(Element e) {
        return atributoLocal(e, "id");
    }

    private String referencia(Element e, String nombre) {
        String valor = atributo(e, nombre);
        if (valor == null) valor = atributoLocal(e, nombre);
        if (valor == null || valor.isBlank()) {
            for (Element hijo : hijos(e)) {
                if (nombre.equalsIgnoreCase(local(hijo))) {
                    valor = atributo(hijo, "idref");
                    if (valor == null) valor = atributo(hijo, "xmi:idref");
                    if (valor == null) valor = atributoLocal(hijo, "idref");
                    if (valor != null && !valor.isBlank()) break;
                }
            }
        }
        if (valor == null || valor.isBlank()) return null;
        int espacio = valor.indexOf(' ');
        return espacio > 0 ? valor.substring(0, espacio) : valor;
    }

    private String nombreObligatorio(Element e, String entidad) {
        String valor = nombre(e, null);
        if (valor == null || valor.isBlank())
            throw new ApiException(422, "Se encontró " + entidad + " sin nombre en el XMI.");
        return limitar(valor.trim(), 180);
    }

    private String nombre(Element e, String defecto) {
        String valor = atributo(e, "name");
        return valor == null || valor.isBlank() ? defecto : limitar(valor.trim(), 180);
    }

    private boolean bool(Element e, String nombre) {
        String value = atributo(e, nombre);
        if (value == null) return false;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw new ApiException(422, "Booleano UML inválido en " + nombre);
        };
    }

    private boolean booleanDefault(Element e, String name, boolean value) {
        return atributo(e, name) == null ? value : bool(e, name);
    }

    private String atributo(Element e, String nombre) {
        String valor = e.getAttribute(nombre);
        return valor == null || valor.isBlank() ? null : valor;
    }

    private String atributoLocal(Element e, String nombre) {
        NamedNodeMap atributos = e.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Node atributo = atributos.item(i);
            String local = atributo.getLocalName();
            if (nombre.equals(local == null ? atributo.getNodeName() : local))
                return atributo.getNodeValue();
        }
        return null;
    }

    private static String limpiarReferencia(String referencia) {
        if (referencia == null) return null;
        int fragmento = referencia.lastIndexOf('#');
        String limpio = fragmento >= 0 ? referencia.substring(fragmento + 1) : referencia;
        return limpio.startsWith("_") && limpio.length() == 33 ? limpio.substring(1) : limpio;
    }

    private String local(Element e) {
        return e.getLocalName() == null
                ? e.getTagName().replaceFirst("^.*:", "")
                : e.getLocalName();
    }

    private String limitar(String valor, int maximo) {
        if (valor.length() > maximo)
            throw new ApiException(
                    422,
                    "Texto del XMI excede el límite de " + maximo + " caracteres; no se trunca.");
        return valor;
    }
}
