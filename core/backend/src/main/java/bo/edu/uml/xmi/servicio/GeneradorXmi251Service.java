package bo.edu.uml.xmi.servicio;

import bo.edu.uml.compartido.ApiException;

import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Serialización del subconjunto de clases; UML y XMI tienen identidades independientes. */
@Service
public class GeneradorXmi251Service {
    public static final String XMI = "http://www.omg.org/spec/XMI/20131001";
    public static final String UML = "http://www.omg.org/spec/UML/20161101";
    public static final String PRODUCTO = "urn:uml-colaborativo:clases:2026-11-10";
    private static final Set<String> PRIMITIVOS =
            Set.of(
                    "String",
                    "Integer",
                    "Boolean",
                    "Real",
                    "UnlimitedNatural",
                    "Long",
                    "Double",
                    "Float",
                    "BigDecimal",
                    "UUID",
                    "LocalDate",
                    "LocalDateTime",
                    "Instant",
                    "Date",
                    "Byte",
                    "Short",
                    "Character",
                    "byte[]",
                    "int",
                    "long",
                    "double",
                    "float",
                    "boolean",
                    "short",
                    "char",
                    "byte");

    public byte[] generar(
            String nombre,
            List<Map<String, Object>> paquetes,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> posiciones,
            List<Map<String, Object>> posicionesRelaciones,
            boolean extensionEa) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document d = f.newDocumentBuilder().newDocument();
            Element root = d.createElementNS(XMI, "xmi:XMI");
            d.appendChild(root);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:uml", UML);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:pc", PRODUCTO);
            root.setAttributeNS(XMI, "xmi:version", "2.5.1");
            Element docs = add(root, "xmi:Documentation");
            docs.setAttribute("exporter", "Plataforma UML Colaborativa");
            docs.setAttribute("exporterVersion", "clases-2026-11-10");
            Element model = d.createElementNS(UML, "uml:Model");
            root.appendChild(model);
            model.setAttributeNS(
                    XMI,
                    "xmi:id",
                    "model_"
                            + UUID.nameUUIDFromBytes(
                                    nombre.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            model.setAttribute("name", nombre);
            Element extension = add(root, "xmi:Extension");
            extension.setAttribute("extender", "Plataforma UML Colaborativa");
            Map<String, Element> containers = new HashMap<>();
            Map<String, Map<String, Object>> pkgs = new HashMap<>();
            for (var p : paquetes) pkgs.put(s(p.get("id")), p);
            for (var p : paquetes) paquete(model, p, pkgs, containers, new HashSet<>());
            Map<String, List<String>> tipos = new HashMap<>();
            for (var c : clases) {
                String id = id(c.get("id"));
                tipos.computeIfAbsent(s(c.get("nombre")), k -> new ArrayList<>()).add(id);
                String full = nombreCompleto(c, pkgs);
                if (!full.equals(s(c.get("nombre"))))
                    tipos.computeIfAbsent(full, k -> new ArrayList<>()).add(id);
            }
            Map<String, String> primitivos = new HashMap<>();
            for (var c : clases) {
                Element parent = model;
                if (c.get("id_paquete") != null) {
                    parent = containers.get(s(c.get("id_paquete")));
                    if (parent == null)
                        throw new ApiException(
                                422, "La clase referencia un paquete fuera del alcance exportado.");
                }
                Element e =
                        element(
                                parent,
                                "packagedElement",
                                "Class",
                                c.get("id"),
                                s(c.get("nombre")));
                e.setAttribute("visibility", vis(c.get("visibilidad")));
                e.setAttribute("isAbstract", s(c.getOrDefault("es_abstracto", false)));
                comentario(e, c.get("descripcion"));
                metadata(extension, c, List.of("es_persistente", "estereotipo"));
                for (var a : atributos)
                    if (Objects.equals(s(a.get("id_clase")), s(c.get("id")))) {
                        Element prop =
                                element(
                                        e,
                                        "ownedAttribute",
                                        "Property",
                                        a.get("id"),
                                        s(a.get("nombre")));
                        prop.setAttribute("visibility", vis(a.get("visibilidad")));
                        prop.setAttribute("isStatic", s(a.getOrDefault("es_estatico", false)));
                        prop.setAttribute("isID", s(a.getOrDefault("es_identificador", false)));
                        prop.setAttribute("isOrdered", s(a.getOrDefault("es_ordenado", false)));
                        prop.setAttribute("isUnique", s(a.getOrDefault("coleccion_unica", true)));
                        prop.setAttribute(
                                "type", tipo(model, s(a.get("tipo_uml")), tipos, primitivos));
                        multiplicidad(
                                prop,
                                a.getOrDefault("multiplicidad_minima", 1),
                                a.containsKey("multiplicidad_maxima")
                                        ? a.get("multiplicidad_maxima")
                                        : 1);
                        defecto(prop, a.get("valor_predeterminado"), s(a.get("tipo_uml")));
                        metadata(extension, a, List.of("es_unico", "restricciones_json"));
                    }
                for (var o : operaciones)
                    if (Objects.equals(s(o.get("id_clase")), s(c.get("id")))) {
                        Element op =
                                element(
                                        e,
                                        "ownedOperation",
                                        "Operation",
                                        o.get("id"),
                                        s(o.get("nombre")));
                        op.setAttribute("visibility", vis(o.get("visibilidad")));
                        op.setAttribute("isStatic", s(o.getOrDefault("es_estatico", false)));
                        op.setAttribute("isAbstract", s(o.getOrDefault("es_abstracto", false)));
                        boolean tieneRetorno = false;
                        for (var p : parametros)
                            if (Objects.equals(s(p.get("id_operacion")), s(o.get("id")))) {
                                boolean ret = "RETORNO".equals(p.get("direccion"));
                                if (ret && tieneRetorno)
                                    throw new ApiException(
                                            422, "Una operación tiene más de un retorno.");
                                tieneRetorno |= ret;
                                if (ret
                                        && o.get("tipo_retorno") != null
                                        && !s(o.get("tipo_retorno")).equals(s(p.get("tipo_uml"))))
                                    throw new ApiException(
                                            422,
                                            "El parámetro de retorno contradice tipo_retorno.");
                                Element param =
                                        element(
                                                op,
                                                "ownedParameter",
                                                "Parameter",
                                                p.get("id"),
                                                s(p.get("nombre")));
                                param.setAttribute(
                                        "direction",
                                        switch (s(p.get("direccion"))) {
                                            case "SALIDA" -> "out";
                                            case "ENTRADA_SALIDA" -> "inout";
                                            case "RETORNO" -> "return";
                                            default -> "in";
                                        });
                                param.setAttribute(
                                        "type",
                                        tipo(model, s(p.get("tipo_uml")), tipos, primitivos));
                                multiplicidad(
                                        param,
                                        Boolean.FALSE.equals(p.get("es_obligatorio")) ? 0 : 1,
                                        1);
                                defecto(param, p.get("valor_predeterminado"), s(p.get("tipo_uml")));
                            }
                        String ret = s(o.get("tipo_retorno"));
                        if (!tieneRetorno && !ret.isBlank() && !ret.equals("void")) {
                            Element param =
                                    element(
                                            op,
                                            "ownedParameter",
                                            "Parameter",
                                            s(o.get("id")) + "_ret",
                                            null);
                            param.setAttribute("direction", "return");
                            param.setAttribute("type", tipo(model, ret, tipos, primitivos));
                        }
                    }
                for (var r : relaciones)
                    if ("GENERALIZACION".equals(r.get("tipo_relacion"))
                            && s(c.get("id")).equals(s(r.get("id_clase_origen")))) {
                        Element gen =
                                element(e, "generalization", "Generalization", r.get("id"), null);
                        gen.setAttribute("general", id(r.get("id_clase_destino")));
                        metadata(extension, r, List.of("nombre", "direccion"));
                    }
            }
            Set<String> idsClase = new HashSet<>();
            for (var c : clases) idsClase.add(s(c.get("id")));
            for (var r : relaciones) {
                if (!idsClase.contains(s(r.get("id_clase_origen")))
                        || !idsClase.contains(s(r.get("id_clase_destino"))))
                    throw new ApiException(
                            422, "Una relación tiene extremos fuera del alcance exportado.");
                String tipo = s(r.get("tipo_relacion"));
                if (tipo.equals("COMPOSICION")
                        && (!(r.get("maximo_origen") instanceof Number n) || n.longValue() > 1))
                    throw new ApiException(
                            422,
                            "Composición inválida: máximo del conjunto mayor que uno o ilimitado."
                                + " Revisar el modelo anterior sin modificar su historia.");
                bo.edu.uml.modelado.modelo.SemanticaUml.direccion(
                        tipo, s(r.getOrDefault("direccion", "SIN_DIRECCION")));
                if (tipo.equals("GENERALIZACION")) continue;
                if (tipo.equals("DEPENDENCIA")) {
                    Element dep =
                            element(
                                    model,
                                    "packagedElement",
                                    "Dependency",
                                    r.get("id"),
                                    s(r.get("nombre")));
                    dep.setAttribute("client", id(r.get("id_clase_origen")));
                    dep.setAttribute("supplier", id(r.get("id_clase_destino")));
                    continue;
                }
                if (!Set.of("ASOCIACION", "AGREGACION", "COMPOSICION").contains(tipo))
                    throw new ApiException(422, "Relación no soportada: " + tipo);
                Element assoc =
                        element(
                                model,
                                "packagedElement",
                                "Association",
                                r.get("id"),
                                s(r.get("nombre")));
                String src = id(r.get("id")) + "_src", tgt = id(r.get("id")) + "_tgt";
                assoc.setAttribute("memberEnd", src + " " + tgt);
                String dir = s(r.get("direccion"));
                List<String> nav = new ArrayList<>();
                if (Set.of("DESTINO_A_ORIGEN", "BIDIRECCIONAL").contains(dir)) nav.add(src);
                if (Set.of("ORIGEN_A_DESTINO", "BIDIRECCIONAL").contains(dir)) nav.add(tgt);
                if (!nav.isEmpty()) assoc.setAttribute("navigableOwnedEnd", String.join(" ", nav));
                for (boolean origen : List.of(true, false)) {
                    Element end =
                            element(
                                    assoc,
                                    "ownedEnd",
                                    "Property",
                                    s(r.get("id")) + (origen ? "_src" : "_tgt"),
                                    s(r.get(origen ? "rol_origen" : "rol_destino")));
                    end.setAttribute("association", id(r.get("id")));
                    end.setAttribute(
                            "type", id(r.get(origen ? "id_clase_origen" : "id_clase_destino")));
                    if (!origen)
                        end.setAttribute(
                                "aggregation",
                                tipo.equals("COMPOSICION")
                                        ? "composite"
                                        : tipo.equals("AGREGACION") ? "shared" : "none");
                    multiplicidad(
                            end,
                            r.get(origen ? "minimo_origen" : "minimo_destino"),
                            r.get(origen ? "maximo_origen" : "maximo_destino"));
                }
            }
            if (extensionEa)
                for (var pos : posiciones) {
                    var row = new LinkedHashMap<String, Object>(pos);
                    row.put("id", pos.get("id_clase"));
                    metadata(extension, row, List.of("x", "y", "ancho", "alto"));
                }
            for (var p : paquetes) comentario(containers.get(s(p.get("id"))), p.get("descripcion"));
            // La extensión EA es propietaria y opcional. No representa UMLDI ni certificación
            // visual.
            if (extensionEa) {
                byte[] legacy =
                        new GeneradorXmi21Service()
                                .generar(
                                        nombre,
                                        paquetes,
                                        clases,
                                        atributos,
                                        operaciones,
                                        parametros,
                                        relaciones,
                                        posiciones,
                                        posicionesRelaciones,
                                        true);
                Document ea = f.newDocumentBuilder().parse(new ByteArrayInputStream(legacy));
                for (Node n = ea.getDocumentElement().getFirstChild();
                        n != null;
                        n = n.getNextSibling())
                    if (n instanceof Element el && "Extension".equals(el.getLocalName())) {
                        Node imported = d.importNode(el, true);
                        rebind(d, imported);
                        Map<String, String> refs = new HashMap<>();
                        var old = new GeneradorXmi21Service();
                        for (var c : clases)
                            refs.put(old.eaElementId(s(c.get("id"))), id(c.get("id")));
                        for (var r : relaciones)
                            refs.put(old.eaElementId(s(r.get("id"))), id(r.get("id")));
                        for (var p : paquetes)
                            refs.put(old.eaPackageId(s(p.get("id"))), id(p.get("id")));
                        refs.put(
                                old.eaPackageId("MODEL:" + nombre),
                                model.getAttributeNS(XMI, "id"));
                        remap(imported, refs);
                        root.appendChild(imported);
                    }
            }
            Set<String> identidades = new HashSet<>();
            NodeList elementos = d.getElementsByTagName("*");
            for (int i = 0; i < elementos.getLength(); i++) {
                Element e = (Element) elementos.item(i);
                String key = e.getAttributeNS(XMI, "id");
                if (!key.isBlank() && !identidades.add(key))
                    throw new ApiException(
                            422,
                            "Identificadores internos coinciden entre entidades; no se exporta un"
                                + " XMI con IDs ambiguos.");
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(new DOMSource(d), new StreamResult(out));
            return out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el subconjunto UML.", e);
        }
    }

    private Element paquete(
            Element model,
            Map<String, Object> p,
            Map<String, Map<String, Object>> all,
            Map<String, Element> cache,
            Set<String> visitados) {
        String key = s(p.get("id"));
        if (cache.containsKey(key)) return cache.get(key);
        if (!visitados.add(key))
            throw new ApiException(422, "Ciclo de paquetes en la exportación.");
        Element parent = model;
        String padre = s(p.get("id_paquete_padre"));
        if (!padre.isBlank()) {
            if (!all.containsKey(padre))
                throw new ApiException(422, "Paquete padre fuera del alcance.");
            parent = paquete(model, all.get(padre), all, cache, visitados);
        }
        Element result = element(parent, "packagedElement", "Package", key, s(p.get("nombre")));
        cache.put(key, result);
        return result;
    }

    private String nombreCompleto(
            Map<String, Object> c, Map<String, Map<String, Object>> paquetes) {
        String result = s(c.get("nombre")), key = s(c.get("id_paquete"));
        Set<String> seen = new HashSet<>();
        while (paquetes.containsKey(key) && seen.add(key)) {
            var p = paquetes.get(key);
            result = s(p.get("nombre")) + "." + result;
            key = s(p.get("id_paquete_padre"));
        }
        return result;
    }

    private String tipo(
            Element model,
            String nombre,
            Map<String, List<String>> tipos,
            Map<String, String> primitivos) {
        var ids = tipos.getOrDefault(nombre, List.of());
        if (ids.size() == 1) return ids.getFirst();
        if (ids.size() > 1)
            throw new ApiException(422, "Tipo ambiguo: " + nombre + ". Usa el nombre cualificado.");
        if (!PRIMITIVOS.contains(nombre))
            throw new ApiException(422, "Tipo no resuelto en el alcance: " + nombre);
        return primitivos.computeIfAbsent(
                nombre,
                n -> {
                    String key =
                            "primitive_"
                                    + UUID.nameUUIDFromBytes(
                                            n.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    element(model, "packagedElement", "PrimitiveType", key, n);
                    return id(key);
                });
    }

    private static Element add(Element p, String tag) {
        Element e = p.getOwnerDocument().createElementNS(tag.startsWith("xmi:") ? XMI : null, tag);
        p.appendChild(e);
        return e;
    }

    private static Element element(Element p, String tag, String type, Object key, String name) {
        Element e = add(p, tag);
        e.setAttributeNS(XMI, "xmi:type", "uml:" + type);
        e.setAttributeNS(XMI, "xmi:id", id(key));
        if (name != null && !name.isBlank()) e.setAttribute("name", name);
        return e;
    }

    private static void multiplicidad(Element p, Object min, Object max) {
        long lower = min instanceof Number n ? n.longValue() : 1;
        if (lower < 0
                || max != null && (!(max instanceof Number) || ((Number) max).longValue() < lower))
            throw new ApiException(422, "Multiplicidad inválida al exportar.");
        Element l = add(p, "lowerValue");
        l.setAttributeNS(XMI, "xmi:type", "uml:LiteralInteger");
        l.setAttribute("value", s(lower));
        Element u = add(p, "upperValue");
        u.setAttributeNS(XMI, "xmi:type", "uml:LiteralUnlimitedNatural");
        u.setAttribute("value", max == null ? "*" : s(max));
    }

    private static void comentario(Element e, Object text) {
        if (e != null && text != null && !s(text).isBlank()) {
            Element c = element(e, "ownedComment", "Comment", UUID.randomUUID(), null);
            add(c, "body").setTextContent(s(text));
        }
    }

    private static void defecto(Element p, Object value, String tipo) {
        if (value != null) {
            Element e = add(p, "defaultValue");
            // Tipar expresiones de los tokens de producto sin atribuirles un PrimitiveType OMG
            // distinto.
            e.setAttributeNS(XMI, "xmi:type", "uml:OpaqueExpression");
            e.setAttribute("type", p.getAttribute("type"));
            add(e, "language").setTextContent("uml-colaborativo:literal");
            add(e, "body").setTextContent(s(value));
        }
    }

    private static void metadata(Element ext, Map<String, Object> row, List<String> keys) {
        Element e = ext.getOwnerDocument().createElementNS(PRODUCTO, "pc:elemento");
        ext.appendChild(e);
        e.setAttribute("ref", id(row.get("id")));
        for (String key : keys) if (row.get(key) != null) e.setAttribute(key, s(row.get(key)));
    }

    private static void remap(Node n, Map<String, String> refs) {
        if (n instanceof Element e)
            for (int i = 0; i < e.getAttributes().getLength(); i++) {
                Node a = e.getAttributes().item(i);
                if (refs.containsKey(a.getNodeValue())) a.setNodeValue(refs.get(a.getNodeValue()));
            }
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) remap(c, refs);
    }

    private static void rebind(Document d, Node n) {
        if (n instanceof Element e) {
            if (e.getPrefix() != null && e.getPrefix().equals("xmi"))
                d.renameNode(e, XMI, e.getNodeName());
            for (int i = 0; i < e.getAttributes().getLength(); i++) {
                Node a = e.getAttributes().item(i);
                if ("xmi".equals(a.getPrefix())) d.renameNode(a, XMI, a.getNodeName());
            }
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) rebind(d, c);
    }

    private static String id(Object key) {
        return "_" + s(key).replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String s(Object value) {
        return Objects.toString(value, "");
    }

    private static String vis(Object v) {
        return switch (s(v)) {
            case "PRIVADO" -> "private";
            case "PROTEGIDO" -> "protected";
            case "PAQUETE" -> "package";
            default -> "public";
        };
    }
}
