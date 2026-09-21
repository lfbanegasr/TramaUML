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
            int x,
            int y) {}

    public record Atributo(
            UUID id,
            UUID clase,
            String nombre,
            String tipo,
            String visibilidad,
            int minimo,
            Integer maximo,
            boolean identificador,
            boolean estatico) {}

    public record Parametro(
            UUID id,
            String nombre,
            String tipo,
            String direccion,
            boolean obligatorio,
            int orden) {}

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
            Integer maximoDestino) {}

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
        private final LinkedHashSet<String> advertencias = new LinkedHashSet<>();
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
        if (!version.startsWith("2.1"))
            estado.advertencias.add(
                    "El documento declara XMI " + version + "; se normalizará al perfil XMI 2.1.");
        recorrer(modelo, null, estado);
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

        List<Relacion> relaciones = resolverRelaciones(estado);
        String exportador = versionEnterpriseArchitect(raiz);
        return new Analisis(
                nombre(modelo, "Modelo importado"),
                "XMI_2.1",
                exportador,
                List.copyOf(estado.paquetes),
                List.copyOf(estado.clases),
                List.copyOf(estado.atributos),
                List.copyOf(estado.operaciones),
                List.copyOf(relaciones),
                List.copyOf(estado.advertencias));
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
            } else if (es(tipo, "Association") || es(tipo, "Dependency")) {
                leerRelacion(e, estado);
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
                        60 + (indice / 4) * 220));
        for (Element hijo : hijos(e)) {
            switch (local(hijo)) {
                case "ownedAttribute" -> leerAtributo(hijo, id, estado);
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
        if (e.hasAttribute("association")) {
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
                        tipoDato(e),
                        visibilidad(e),
                        multiplicidad[0],
                        multiplicidad[1] < 0 ? null : multiplicidad[1],
                        bool(e, "isID"),
                        bool(e, "isStatic")));
    }

    private void leerOperacion(Element e, UUID clase, Estado estado) {
        List<Parametro> parametros = new ArrayList<>();
        String retorno = null;
        int orden = 0;
        for (Element hijo : hijos(e)) {
            if (!"ownedParameter".equals(local(hijo))) continue;
            String direccion = atributo(hijo, "direction");
            if ("return".equalsIgnoreCase(direccion)) retorno = tipoDato(hijo);
            else
                parametros.add(
                        new Parametro(
                                estado.id(identificador(hijo)),
                                nombreObligatorio(hijo, "parámetro"),
                                tipoDato(hijo),
                                direccionParametro(direccion),
                                multiplicidad(hijo)[0] > 0,
                                orden++));
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
        if (es(tipoXmi, "Dependency")) {
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
        List<Element> extremos =
                hijos(e).stream().filter(h -> "ownedEnd".equals(local(h))).toList();
        if (extremos.size() < 2) {
            estado.advertencias.add("Se omitió una asociación sin dos extremos reconocibles.");
            return;
        }
        Element origen = extremos.get(0), destino = extremos.get(1);
        int[] cardinalidadOrigen = multiplicidad(origen),
                cardinalidadDestino = multiplicidad(destino);
        String agregacion = atributo(destino, "aggregation");
        if (agregacion == null || agregacion.isBlank())
            agregacion = atributo(origen, "aggregation");
        String tipoRelacion =
                "composite".equalsIgnoreCase(agregacion)
                        ? "COMPOSICION"
                        : "shared".equalsIgnoreCase(agregacion) ? "AGREGACION" : "ASOCIACION";
        estado.relaciones.add(
                new RelacionPendiente(
                        estado.id(identificador(e)),
                        referencia(origen, "type"),
                        referencia(destino, "type"),
                        nombre(e, null),
                        tipoRelacion,
                        nombre(origen, null),
                        nombre(destino, null),
                        cardinalidadOrigen[0],
                        cardinalidadOrigen[1] < 0 ? null : cardinalidadOrigen[1],
                        cardinalidadDestino[0],
                        cardinalidadDestino[1] < 0 ? null : cardinalidadDestino[1]));
    }

    private List<Relacion> resolverRelaciones(Estado estado) {
        List<Relacion> resultado = new ArrayList<>();
        for (RelacionPendiente r : estado.relaciones) {
            UUID origen = estado.ids.get(limpiarReferencia(r.origen()));
            UUID destino = estado.ids.get(limpiarReferencia(r.destino()));
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
                            r.nombre(),
                            r.tipo(),
                            r.rolOrigen(),
                            r.rolDestino(),
                            r.minimoOrigen(),
                            r.maximoOrigen(),
                            r.minimoDestino(),
                            r.maximoDestino()));
        }
        return resultado;
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
        return new int[] {minimo, maximo};
    }

    private int numeroCardinalidad(Element e, int defecto) {
        String valor = atributo(e, "value");
        if ("*".equals(valor) || "-1".equals(valor)) return -1;
        try {
            return valor == null || valor.isBlank() ? defecto : Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            return defecto;
        }
    }

    private String tipoDato(Element e) {
        String tipo = referencia(e, "type");
        if (tipo == null) {
            for (Element hijo : hijos(e)) {
                if ("type".equals(local(hijo))) {
                    tipo = referencia(hijo, "href");
                    break;
                }
            }
        }
        if (tipo == null || tipo.isBlank()) return "String";
        String limpio = limpiarReferencia(tipo);
        return switch (limpio.toLowerCase(Locale.ROOT)) {
            case "string" -> "String";
            case "integer", "int" -> "Integer";
            case "real", "double", "float", "decimal" -> "Double";
            case "boolean" -> "Boolean";
            case "date", "localdate" -> "LocalDate";
            case "datetime", "localdatetime" -> "LocalDateTime";
            case "long", "unlimitednatural" -> "Long";
            default -> limpio.length() > 180 ? limpio.substring(0, 180) : limpio;
        };
    }

    private String visibilidad(Element e) {
        String valor = atributo(e, "visibility");
        if (valor == null) return "PUBLICO";
        return switch (valor.toLowerCase(Locale.ROOT)) {
            case "private" -> "PRIVADO";
            case "protected" -> "PROTEGIDO";
            case "package" -> "PAQUETE";
            default -> "PUBLICO";
        };
    }

    private String direccionParametro(String valor) {
        if (valor == null) return "ENTRADA";
        return switch (valor.toLowerCase(Locale.ROOT)) {
            case "out" -> "SALIDA";
            case "inout" -> "ENTRADA_SALIDA";
            default -> "ENTRADA";
        };
    }

    private String comentario(Element e) {
        String body = atributo(e, "body");
        if (body == null) return null;
        return body.length() > 4000 ? body.substring(0, 4000) : body;
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
        return atributoLocal(e, "type");
    }

    private String identificador(Element e) {
        return atributoLocal(e, "id");
    }

    private String referencia(Element e, String nombre) {
        String valor = atributo(e, nombre);
        if (valor == null) valor = atributoLocal(e, nombre);
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
        return "true".equalsIgnoreCase(atributo(e, nombre));
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
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
