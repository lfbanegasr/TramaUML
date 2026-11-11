package bo.edu.uml.xmi.servicio;

import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exportador legado XMI 2.1 / UML 2.1 con extensión EA. Compatibilidad visual no verificada.
 *
 * <p>Produce el formato histórico XMI 2.1 mapeando paquetes, clases, atributos, operaciones y
 * asociaciones con cardinalidades usando {@code lowerValue}/{@code upperValue}. No depende de
 * Jackson ni de ningún modelo de persistencia; recibe listas de mapas provenientes del repositorio.
 */
@Service
public class GeneradorXmi21Service {

    private static final String XMI_NS = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML_NS = "http://schema.omg.org/spec/UML/2.1";
    private static final String EXPORTER = "Enterprise Architect";
    private static final String EXPORTER_VERSION = "6.5";

    // ─── Punto de entrada ────────────────────────────────────────────────────

    /**
     * Genera el documento XMI completo como {@code byte[]}.
     *
     * @param nombreModelo nombre del modelo UML de la plataforma.
     * @param paquetes lista de mapas {@code {id, id_paquete_padre, nombre, descripcion}}.
     * @param clases lista de mapas {@code {id, nombre, estereotipo, visibilidad, es_abstracto,
     *     es_persistente, id_paquete}}.
     * @param atributos lista de mapas {@code {id, id_clase, nombre, tipo_uml, visibilidad,
     *     multiplicidad_minima, multiplicidad_maxima, es_identificador, es_estatico}}.
     * @param operaciones lista de mapas {@code {id, id_clase, nombre, visibilidad, tipo_retorno,
     *     es_estatico, es_abstracto}}.
     * @param parametros lista de mapas {@code {id, id_operacion, nombre, tipo_uml, direccion}}.
     * @param relaciones lista de mapas {@code {id, id_clase_origen, id_clase_destino, nombre,
     *     tipo_relacion, rol_origen, rol_destino, minimo_origen, maximo_origen, minimo_destino,
     *     maximo_destino}}.
     * @return bytes UTF-8 del archivo XMI.
     */
    public byte[] generar(
            String nombreModelo,
            List<Map<String, Object>> paquetes,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros,
            List<Map<String, Object>> relaciones) {
        return generar(
                nombreModelo,
                paquetes,
                clases,
                atributos,
                operaciones,
                parametros,
                relaciones,
                List.of(),
                List.of(),
                true);
    }

    /**
     * Genera el documento XMI completo con soporte opcional de extensiones Enterprise Architect.
     */
    public byte[] generar(
            String nombreModelo,
            List<Map<String, Object>> paquetes,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> posicionesClases,
            List<Map<String, Object>> posicionesRelaciones,
            boolean incluirExtensionEa) {

        var w = new StringWriter(8192);

        // Encabezado XML
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write(
                "<xmi:XMI xmi:version=\"2.1\""
                        + " xmlns:xmi=\""
                        + XMI_NS
                        + "\""
                        + " xmlns:uml=\""
                        + UML_NS
                        + "\">\n");

        // Documentación del exportador (Enterprise Architect requiere que sea Enterprise Architect
        // para procesar diagramas)
        w.write(
                "  <xmi:Documentation exporter=\""
                        + esc(EXPORTER)
                        + "\" exporterVersion=\""
                        + esc(EXPORTER_VERSION)
                        + "\">\n"
                        + "    <xmi:exporterExtension>Plataforma UML"
                        + " Colaborativa</xmi:exporterExtension>\n"
                        + "  </xmi:Documentation>\n");

        // Modelo raíz y paquete contenedor para Enterprise Architect
        String packageId = eaPackageId("MODEL:" + nombreModelo);
        w.write("  <uml:Model xmi:type=\"uml:Model\" name=\"EA_Model\" visibility=\"public\">\n");
        w.write(
                "    <packagedElement xmi:type=\"uml:Package\" xmi:id=\""
                        + packageId
                        + "\" name=\""
                        + esc(nombreModelo)
                        + "\" visibility=\"public\">\n");

        // Índices por ID para búsqueda O(1)
        Map<String, List<Map<String, Object>>> atribPorClase = agrupar(atributos, "id_clase");
        Map<String, List<Map<String, Object>>> opPorClase = agrupar(operaciones, "id_clase");
        Map<String, List<Map<String, Object>>> paramPorOp = agrupar(parametros, "id_operacion");
        Map<String, List<Map<String, Object>>> generalizacionesPorClase =
                agrupar(
                        relaciones.stream()
                                .filter(r -> "GENERALIZACION".equals(str(r.get("tipo_relacion"))))
                                .toList(),
                        "id_clase_origen");

        // Paquetes raíz (sin padre) → recursivo
        escribirPaquetes(
                w,
                null,
                paquetes,
                clases,
                atribPorClase,
                opPorClase,
                paramPorOp,
                generalizacionesPorClase);

        // Clases sin paquete (id_paquete == null)
        for (var c : clases) {
            if (c.get("id_paquete") == null) {
                escribirClase(
                        w,
                        c,
                        "      ",
                        atribPorClase,
                        opPorClase,
                        paramPorOp,
                        generalizacionesPorClase);
            }
        }

        // Asociaciones (relaciones)
        for (var r : relaciones) {
            if (!"GENERALIZACION".equals(str(r.get("tipo_relacion")))) escribirRelacion(w, r);
        }

        w.write("    </packagedElement>\n");
        w.write("  </uml:Model>\n");

        // Extensión Enterprise Architect (Diagrama, Conectores con Multiplicidades y Layout)
        if (incluirExtensionEa) {
            escribirExtensionEnterpriseArchitect(
                    w,
                    packageId,
                    nombreModelo,
                    paquetes,
                    clases,
                    relaciones,
                    posicionesClases);
        }

        w.write("</xmi:XMI>\n");

        return w.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ─── Paquetes recursivos ─────────────────────────────────────────────────

    private void escribirPaquetes(
            StringWriter w,
            String idPadreFiltro,
            List<Map<String, Object>> paquetes,
            List<Map<String, Object>> clases,
            Map<String, List<Map<String, Object>>> atribPorClase,
            Map<String, List<Map<String, Object>>> opPorClase,
            Map<String, List<Map<String, Object>>> paramPorOp,
            Map<String, List<Map<String, Object>>> generalizacionesPorClase) {

        for (var p : paquetes) {
            String padre = str(p.get("id_paquete_padre"));
            boolean esRaiz = idPadreFiltro == null && (padre == null || padre.isBlank());
            boolean esHijo = idPadreFiltro != null && idPadreFiltro.equals(padre);
            if (!esRaiz && !esHijo) continue;

            String id = eaPackageId(str(p.get("id")));
            String nombre = str(p.get("nombre"));
            w.write(
                    "    <packagedElement xmi:type=\"uml:Package\" xmi:id=\""
                            + id
                            + "\" name=\""
                            + esc(nombre)
                            + "\">\n");

            // Sub-paquetes
            escribirPaquetes(
                    w,
                    str(p.get("id")),
                    paquetes,
                    clases,
                    atribPorClase,
                    opPorClase,
                    paramPorOp,
                    generalizacionesPorClase);

            // Clases de este paquete
            for (var c : clases) {
                if (str(p.get("id")).equals(str(c.get("id_paquete")))) {
                    escribirClase(
                            w,
                            c,
                            "      ",
                            atribPorClase,
                            opPorClase,
                            paramPorOp,
                            generalizacionesPorClase);
                }
            }

            w.write("    </packagedElement>\n");
        }
    }

    // ─── Clases ─────────────────────────────────────────────────────────────

    private void escribirClase(
            StringWriter w,
            Map<String, Object> c,
            String indent,
            Map<String, List<Map<String, Object>>> atribPorClase,
            Map<String, List<Map<String, Object>>> opPorClase,
            Map<String, List<Map<String, Object>>> paramPorOp,
            Map<String, List<Map<String, Object>>> generalizacionesPorClase) {

        String id = xmiId(str(c.get("id")));
        String nombre = str(c.get("nombre"));
        boolean esAbstracto = bool(c.get("es_abstracto"));
        String visibilidad = visibilidadUml(str(c.get("visibilidad")));

        w.write(
                indent
                        + "<packagedElement xmi:type=\"uml:Class\" xmi:id=\""
                        + id
                        + "\" name=\""
                        + esc(nombre)
                        + "\""
                        + " visibility=\""
                        + visibilidad
                        + "\""
                        + (esAbstracto ? " isAbstract=\"true\"" : "")
                        + ">\n");

        // Atributos
        for (var a : atribPorClase.getOrDefault(str(c.get("id")), List.of())) {
            escribirAtributo(w, a, indent + "  ");
        }

        // Operaciones
        for (var o : opPorClase.getOrDefault(str(c.get("id")), List.of())) {
            escribirOperacion(w, o, indent + "  ", paramPorOp);
        }

        for (var r : generalizacionesPorClase.getOrDefault(str(c.get("id")), List.of())) {
            w.write(
                    indent
                            + "  <generalization xmi:type=\"uml:Generalization\" xmi:id=\""
                            + xmiId(str(r.get("id")))
                            + "\" general=\""
                            + xmiId(str(r.get("id_clase_destino")))
                            + "\"/>\n");
        }

        w.write(indent + "</packagedElement>\n");
    }

    // ─── Atributos ────────────────────────────────────────────────────────────

    private void escribirAtributo(StringWriter w, Map<String, Object> a, String indent) {
        String id = xmiId(str(a.get("id")));
        String nombre = str(a.get("nombre"));
        String tipo = tipoUml(str(a.get("tipo_uml")));
        boolean esEstatico = bool(a.get("es_estatico"));
        boolean esId = bool(a.get("es_identificador"));
        String visibilidad = visibilidadUml(str(a.get("visibilidad")));

        w.write(
                indent
                        + "<ownedAttribute xmi:type=\"uml:Property\" xmi:id=\""
                        + id
                        + "\" name=\""
                        + esc(nombre)
                        + "\""
                        + " visibility=\""
                        + visibilidad
                        + "\""
                        + " type=\""
                        + esc(tipo)
                        + "\""
                        + (esEstatico ? " isStatic=\"true\"" : "")
                        + (esId ? " isID=\"true\"" : "")
                        + ">\n");

        // Cardinalidad
        Object minObj = a.get("multiplicidad_minima");
        Object maxObj = a.get("multiplicidad_maxima");
        int min = minObj instanceof Number n ? n.intValue() : 1;
        escribirCardinalidad(w, min, maxObj, indent + "  ");

        w.write(indent + "</ownedAttribute>\n");
    }

    // ─── Operaciones ──────────────────────────────────────────────────────────

    private void escribirOperacion(
            StringWriter w,
            Map<String, Object> o,
            String indent,
            Map<String, List<Map<String, Object>>> paramPorOp) {

        String id = xmiId(str(o.get("id")));
        String nombre = str(o.get("nombre"));
        boolean esAbstracto = bool(o.get("es_abstracto"));
        boolean esEstatico = bool(o.get("es_estatico"));
        String visibilidad = visibilidadUml(str(o.get("visibilidad")));

        w.write(
                indent
                        + "<ownedOperation xmi:type=\"uml:Operation\" xmi:id=\""
                        + id
                        + "\" name=\""
                        + esc(nombre)
                        + "\""
                        + " visibility=\""
                        + visibilidad
                        + "\""
                        + (esAbstracto ? " isAbstract=\"true\"" : "")
                        + (esEstatico ? " isStatic=\"true\"" : "")
                        + ">\n");

        // Parámetros de retorno
        String retorno = str(o.get("tipo_retorno"));
        if (retorno != null && !retorno.isBlank()) {
            w.write(
                    indent
                            + "  <ownedParameter xmi:type=\"uml:Parameter\""
                            + " xmi:id=\""
                            + xmiId(str(o.get("id")) + "_ret")
                            + "\""
                            + " direction=\"return\" type=\""
                            + esc(tipoUml(retorno))
                            + "\"/>\n");
        }

        // Parámetros de entrada/salida
        for (var p : paramPorOp.getOrDefault(str(o.get("id")), List.of())) {
            String pid = xmiId(str(p.get("id")));
            String pNombre = str(p.get("nombre"));
            String pTipo = tipoUml(str(p.get("tipo_uml")));
            String dir = direccionUml(str(p.get("direccion")));
            w.write(
                    indent
                            + "  <ownedParameter xmi:type=\"uml:Parameter\""
                            + " xmi:id=\""
                            + pid
                            + "\""
                            + " name=\""
                            + esc(pNombre)
                            + "\""
                            + " direction=\""
                            + dir
                            + "\""
                            + " type=\""
                            + esc(pTipo)
                            + "\"/>\n");
        }

        w.write(indent + "</ownedOperation>\n");
    }

    // ─── Relaciones/Asociaciones ─────────────────────────────────────────────

    private void escribirRelacion(StringWriter w, Map<String, Object> r) {
        String id = xmiId(str(r.get("id")));
        String tipo = tipoRelacionUml(str(r.get("tipo_relacion")));
        String nombre = str(r.get("nombre"));
        String origen = xmiId(str(r.get("id_clase_origen")));
        String destino = xmiId(str(r.get("id_clase_destino")));

        if ("uml:Dependency".equals(tipo)) {
            w.write(
                    "    <packagedElement xmi:type=\"uml:Dependency\" xmi:id=\""
                            + id
                            + "\""
                            + (nombre != null && !nombre.isBlank()
                                    ? " name=\"" + esc(nombre) + "\""
                                    : "")
                            + " client=\""
                            + origen
                            + "\" supplier=\""
                            + destino
                            + "\"/>\n");
            return;
        }

        w.write(
                "    <packagedElement xmi:type=\""
                        + tipo
                        + "\" xmi:id=\""
                        + id
                        + "\""
                        + (nombre != null && !nombre.isBlank()
                                ? " name=\"" + esc(nombre) + "\""
                                : "")
                        + " visibility=\"public\">\n");
        w.write("      <memberEnd xmi:idref=\"" + id + "_src\"/>\n");
        w.write("      <memberEnd xmi:idref=\"" + id + "_tgt\"/>\n");

        // Extremo origen
        String rolOrigen = str(r.get("rol_origen"));
        w.write(
                "      <ownedEnd xmi:type=\"uml:Property\" xmi:id=\""
                        + id
                        + "_src\""
                        + " association=\""
                        + id
                        + "\""
                        + " type=\""
                        + origen
                        + "\""
                        + (rolOrigen != null && !rolOrigen.isBlank()
                                ? " name=\"" + esc(rolOrigen) + "\""
                                : "")
                        + " visibility=\"public\">\n");
        w.write("        <type xmi:idref=\"" + origen + "\"/>\n");
        escribirCardinalidad(w, r.get("minimo_origen"), r.get("maximo_origen"), "        ");
        w.write("      </ownedEnd>\n");

        // Extremo destino
        String rolDestino = str(r.get("rol_destino"));
        String agregacion =
                switch (Objects.toString(r.get("tipo_relacion"), "ASOCIACION")) {
                    case "COMPOSICION" -> " aggregation=\"composite\"";
                    case "AGREGACION" -> " aggregation=\"shared\"";
                    default -> " aggregation=\"none\"";
                };
        w.write(
                "      <ownedEnd xmi:type=\"uml:Property\" xmi:id=\""
                        + id
                        + "_tgt\""
                        + " association=\""
                        + id
                        + "\""
                        + " type=\""
                        + destino
                        + "\""
                        + agregacion
                        + (rolDestino != null && !rolDestino.isBlank()
                                ? " name=\"" + esc(rolDestino) + "\""
                                : "")
                        + " visibility=\"public\">\n");
        w.write("        <type xmi:idref=\"" + destino + "\"/>\n");
        escribirCardinalidad(w, r.get("minimo_destino"), r.get("maximo_destino"), "        ");
        w.write("      </ownedEnd>\n");

        w.write("    </packagedElement>\n");
    }

    // ─── Cardinalidades ──────────────────────────────────────────────────────

    private void escribirCardinalidad(StringWriter w, Object minObj, Object maxObj, String indent) {
        int min = minObj instanceof Number n ? n.intValue() : 0;
        escribirCardinalidad(w, min, maxObj, indent);
    }

    private void escribirCardinalidad(StringWriter w, int min, Object maxObj, String indent) {
        w.write(
                indent
                        + "<lowerValue xmi:type=\"uml:LiteralInteger\""
                        + " value=\""
                        + min
                        + "\"/>\n");
        if (maxObj == null) {
            w.write(
                    indent
                            + "<upperValue xmi:type=\"uml:LiteralUnlimitedNatural\""
                            + " value=\"-1\"/>\n");
        } else {
            int max = ((Number) maxObj).intValue();
            if (max < 0) {
                w.write(
                        indent
                                + "<upperValue xmi:type=\"uml:LiteralUnlimitedNatural\""
                                + " value=\"-1\"/>\n");
            } else {
                w.write(
                        indent
                                + "<upperValue xmi:type=\"uml:LiteralInteger\""
                                + " value=\""
                                + max
                                + "\"/>\n");
            }
        }
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    /** Agrupa una lista de mapas por el valor de {@code campo}. */
    private Map<String, List<Map<String, Object>>> agrupar(
            List<Map<String, Object>> lista, String campo) {
        Map<String, List<Map<String, Object>>> mapa = new LinkedHashMap<>();
        for (var item : lista) {
            String clave = str(item.get(campo));
            if (clave != null) mapa.computeIfAbsent(clave, k -> new ArrayList<>()).add(item);
        }
        return mapa;
    }

    /** Genera identificador de paquete compatible con Enterprise Architect (prefijo EAPK_). */
    public String eaPackageId(String id) {
        if (id == null || id.isBlank()) return "EAPK_00000000_0000_0000_0000_000000000000";
        try {
            UUID u = UUID.fromString(id);
            return "EAPK_" + u.toString().toUpperCase(Locale.ROOT).replace("-", "_");
        } catch (Exception e) {
            UUID u = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
            return "EAPK_" + u.toString().toUpperCase(Locale.ROOT).replace("-", "_");
        }
    }

    /** Genera identificador de elemento compatible con Enterprise Architect (prefijo EAID_). */
    public String eaElementId(String id) {
        if (id == null || id.isBlank()) return "EAID_00000000_0000_0000_0000_000000000000";
        try {
            UUID u = UUID.fromString(id);
            return "EAID_" + u.toString().toUpperCase(Locale.ROOT).replace("-", "_");
        } catch (Exception e) {
            UUID u = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
            return "EAID_" + u.toString().toUpperCase(Locale.ROOT).replace("-", "_");
        }
    }

    /** Convierte UUID a identificador XMI/EA. */
    private String xmiId(String uuid) {
        return eaElementId(uuid);
    }

    /** Escapa caracteres especiales XML. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Extrae String de un Object (puede ser String o UUID). */
    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }

    /** Mapea visibilidades internas a nombres UML 2.1. */
    private String visibilidadUml(String v) {
        if (v == null) return "public";
        return switch (v) {
            case "PUBLICO" -> "public";
            case "PRIVADO" -> "private";
            case "PROTEGIDO" -> "protected";
            case "PAQUETE" -> "package";
            default -> "public";
        };
    }

    /** Mapea direcciones de parámetro a UML 2.1. */
    private String direccionUml(String d) {
        if (d == null) return "in";
        return switch (d) {
            case "ENTRADA" -> "in";
            case "SALIDA" -> "out";
            case "ENTRADA_SALIDA" -> "inout";
            default -> "in";
        };
    }

    /** Mapea tipos UML internos a primitivos UML 2.1. */
    private String tipoUml(String tipo) {
        if (tipo == null || tipo.isBlank()) return "String";
        return switch (tipo) {
            case "Texto", "String", "VARCHAR", "TEXT" -> "String";
            case "Entero", "Integer", "INT", "BIGINT" -> "Integer";
            case "Float", "float" -> "Float";
            case "Decimal", "Double", "FLOAT", "NUMERIC" -> "Double";
            case "Booleano", "Boolean" -> "Boolean";
            case "Fecha", "LocalDate", "DATE" -> "LocalDate";
            case "FechaHora", "LocalDateTime", "TIMESTAMP" -> "LocalDateTime";
            case "UUID" -> "String";
            default -> tipo;
        };
    }

    /** Mapea tipos de relación a elementos XMI. */
    private String tipoRelacionUml(String tipo) {
        if (tipo == null) return "uml:Association";
        return switch (tipo) {
            case "HERENCIA" -> "uml:Generalization";
            case "REALIZACION" -> "uml:Realization";
            case "DEPENDENCIA" -> "uml:Dependency";
            case "COMPOSICION" -> "uml:Association";
            case "AGREGACION" -> "uml:Association";
            default -> "uml:Association";
        };
    }

    /** Mapea multiplicidades numéricas a texto UML/EA (ej: "1", "0..1", "0..*", "1..*"). */
    public String multiplicidadTexto(Object minObj, Object maxObj) {
        int min = minObj instanceof Number n ? n.intValue() : 1;
        if (maxObj == null) {
            return min == 0 ? "0..*" : min == 1 ? "1..*" : min + "..*";
        }
        int max = ((Number) maxObj).intValue();
        if (max < 0) {
            return min == 0 ? "0..*" : min == 1 ? "1..*" : min + "..*";
        }
        if (min == max) {
            return String.valueOf(min);
        }
        return min + ".." + max;
    }

    /**
     * Escribe la extensión propietaria de Enterprise Architect que define elementos,
     * conectores con multiplicidades y el diagrama de clases con la geometría de cada clase.
     */
    private void escribirExtensionEnterpriseArchitect(
            StringWriter w,
            String modelId,
            String nombreModelo,
            List<Map<String, Object>> paquetes,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> posicionesClases) {

        w.write("  <xmi:Extension extender=\"Enterprise Architect\" extenderID=\"6.5\">\n");

        // 1. Elements
        w.write("    <elements>\n");
        // Elemento del paquete raíz en EA
        w.write(
                "      <element xmi:idref=\""
                        + modelId
                        + "\" xmi:type=\"uml:Package\" name=\""
                        + esc(nombreModelo)
                        + "\" scope=\"public\">\n");
        w.write(
                "        <model package2=\""
                        + modelId
                        + "\" package=\""
                        + modelId
                        + "\" tpos=\"0\" ea_localid=\"1\" ea_eleType=\"package\"/>\n");
        w.write(
                "        <properties isSpecification=\"false\" sType=\"Package\" nType=\"0\""
                    + " scope=\"public\"/>\n");
        w.write(
                "        <project author=\"Plataforma UML\" version=\"1.0\" phase=\"1.0\""
                    + " status=\"Proposed\"/>\n");
        w.write("        <packageproperties version=\"1.0\"/>\n");
        w.write("      </element>\n");

        int localId = 2;
        Map<String, Integer> localIds = new HashMap<>();
        for (var c : clases) {
            String cid = xmiId(str(c.get("id")));
            localIds.put(cid, localId++);
            String nombre = esc(str(c.get("nombre")));
            boolean esAbstracto = bool(c.get("es_abstracto"));
            String idPaquete = str(c.get("id_paquete"));
            String pkgRef = idPaquete != null ? eaPackageId(idPaquete) : modelId;

            w.write(
                    "      <element xmi:idref=\""
                            + cid
                            + "\" xmi:type=\"uml:Class\" name=\""
                            + nombre
                            + "\" scope=\"public\">\n");
            w.write(
                    "        <model package=\""
                            + pkgRef
                            + "\" tpos=\"0\" ea_localid=\""
                            + localIds.get(cid)
                            + "\" ea_eleType=\"element\"/>\n");
            w.write(
                    "        <properties isSpecification=\"false\" sType=\"Class\" nType=\"0\""
                        + " scope=\"public\" isAbstract=\""
                            + esAbstracto
                            + "\"/>\n");
            w.write(
                    "        <project author=\"Plataforma UML\" version=\"1.0\" phase=\"1.0\""
                        + " status=\"Proposed\"/>\n");
            w.write("      </element>\n");
        }
        w.write("    </elements>\n");

        // 2. Connectors
        w.write("    <connectors>\n");
        for (var r : relaciones) {
            String rid = xmiId(str(r.get("id")));
            String origen = xmiId(str(r.get("id_clase_origen")));
            String destino = xmiId(str(r.get("id_clase_destino")));
            String tipoRelacion = Objects.toString(r.get("tipo_relacion"), "ASOCIACION");
            String nombreRel = esc(str(r.get("nombre")));
            String rolOrigen = esc(str(r.get("rol_origen")));
            String rolDestino = esc(str(r.get("rol_destino")));
            String multOrigen =
                    esc(multiplicidadTexto(r.get("minimo_origen"), r.get("maximo_origen")));
            String multDestino =
                    esc(multiplicidadTexto(r.get("minimo_destino"), r.get("maximo_destino")));

            String eaType = switch (tipoRelacion) {
                case "GENERALIZACION", "HERENCIA" -> "Generalization";
                case "DEPENDENCIA" -> "Dependency";
                case "REALIZACION" -> "Realisation";
                default -> "Association";
            };

            String direction =
                    switch (tipoRelacion) {
                        case "GENERALIZACION", "HERENCIA", "DEPENDENCIA", "REALIZACION" ->
                                "Source -> Destination";
                        default -> "Bi-Directional";
                    };

            String aggregationDestino = switch (tipoRelacion) {
                case "COMPOSICION" -> "composite";
                case "AGREGACION" -> "shared";
                default -> "none";
            };

            w.write(
                    "      <connector xmi:idref=\""
                            + rid
                            + "\""
                            + (nombreRel.isBlank() ? "" : " name=\"" + nombreRel + "\"")
                            + ">\n");
            w.write("        <source xmi:idref=\"" + origen + "\">\n");
            w.write(
                    "          <model ea_localid=\""
                            + localIds.getOrDefault(origen, 0)
                            + "\" type=\"Class\"/>\n");
            w.write(
                    "          <role name=\""
                            + rolOrigen
                            + "\" visibility=\"Public\" targetScope=\"instance\"/>\n");
            w.write(
                    "          <type multiplicity=\""
                            + multOrigen
                            + "\" aggregation=\"none\" containment=\"Unspecified\"/>\n");
            w.write(
                    "          <modifiers isOrdered=\"false\" changeable=\"none\""
                        + " isNavigable=\"true\"/>\n");
            w.write(
                    "          <style"
                        + " value=\"Union=0;Derived=0;AllowDuplicates=0;Owned=0;Navigable=Navigable;\"/>\n");
            w.write("        </source>\n");
            w.write("        <target xmi:idref=\"" + destino + "\">\n");
            w.write(
                    "          <model ea_localid=\""
                            + localIds.getOrDefault(destino, 0)
                            + "\" type=\"Class\"/>\n");
            w.write(
                    "          <role name=\""
                            + rolDestino
                            + "\" visibility=\"Public\" targetScope=\"instance\"/>\n");
            w.write(
                    "          <type multiplicity=\""
                            + multDestino
                            + "\" aggregation=\""
                            + aggregationDestino
                            + "\" containment=\"Unspecified\"/>\n");
            w.write(
                    "          <modifiers isOrdered=\"false\" changeable=\"none\""
                        + " isNavigable=\"true\"/>\n");
            w.write(
                    "          <style"
                        + " value=\"Union=0;Derived=0;AllowDuplicates=0;Owned=0;Navigable=Navigable;\"/>\n");
            w.write("        </target>\n");
            w.write(
                    "        <properties ea_type=\""
                            + eaType
                            + "\" direction=\""
                            + direction
                            + "\"/>\n");
            w.write(
                    "        <appearance linemode=\"3\" linecolor=\"-1\" linewidth=\"0\""
                        + " seqno=\"0\" headStyle=\"0\" lineStyle=\"0\"/>\n");
            w.write(
                    "        <labels lb=\""
                            + multOrigen
                            + "\" lt=\""
                            + rolOrigen
                            + "\" mt=\""
                            + nombreRel
                            + "\" rb=\""
                            + multDestino
                            + "\" rt=\""
                            + rolDestino
                            + "\"/>\n");
            w.write("      </connector>\n");
        }
        w.write("    </connectors>\n");

        // 3. Diagrams
        w.write("    <diagrams>\n");
        String diagId = eaElementId("DIAG:" + modelId);
        w.write("      <diagram xmi:id=\"" + diagId + "\">\n");
        w.write(
                "        <model package=\""
                        + modelId
                        + "\" localID=\"1\" owner=\""
                        + modelId
                        + "\"/>\n");
        w.write(
                "        <properties name=\""
                        + esc(nombreModelo)
                        + " - Diagrama de Clases\" type=\"Logical\"/>\n");
        w.write(
                "        <project author=\"Plataforma UML\" version=\"1.0\" created=\"2026-09-21"
                    + " 00:00:00\" modified=\"2026-09-21 00:00:00\"/>\n");
        w.write(
                "        <style1"
                    + " value=\"ShowPrivate=1;ShowProtected=1;ShowPublic=1;HideRelationships=0;Locked=0;Border=1;HighlightForeign=1;PackageContents=1;SequenceNotes=0;ScalePrintImage=0;PPgs.cx=1;PPgs.cy=1;DocSize.cx=850;DocSize.cy=1098;ShowDetails=0;Orientation=P;Zoom=100;ShowTags=0;OpParams=1;VisibleAttributeDetail=0;ShowOpRetType=1;ShowIcons=1;CollabNums=0;HideProps=0;ShowReqs=0;ShowCons=0;PaperSize=1;HideParents=0;UseAlias=0;HideAtts=0;HideOps=0;HideStereo=0;HideElemStereo=0;ShowTests=0;ShowMaint=0;ConnectorNotation=UML"
                    + " 2.1;ExplicitNavigability=0;ShowShape=1;AdvancedElementProps=1;AdvancedFeatureProps=1;AdvancedConnectorProps=1;m_bElementClassifier=1;ShowNotes=0;SuppressBrackets=0;SuppConnectorLabels=0;PrintPageHeadFoot=0;ShowAsList=0;\"/>\n");
        w.write(
                "        <style2"
                    + " value=\"ExcludeRTF=0;DocAll=0;HideQuals=0;AttPkg=1;ShowTests=0;ShowMaint=0;SuppressFOC=0;INT_ARGS=;INT_RET=;INT_ATT=;SeqTopMargin=50;MatrixActive=0;SwimlanesActive=1;KanbanActive=0;MatrixLineWidth=1;MatrixLineClr=0;MatrixLocked=0;TConnectorNotation=UML"
                    + " 2.1;TExplicitNavigability=0;AdvancedElementProps=1;AdvancedFeatureProps=1;AdvancedConnectorProps=1;m_bElementClassifier=1;ProfileData=;MDGDgm=;STBLDgm=;ShowNotes=0;VisibleAttributeDetail=0;ShowOpRetType=1;SuppressBrackets=0;SuppConnectorLabels=0;PrintPageHeadFoot=0;ShowAsList=0;SuppressedCompartments=;Theme=:119;SaveTag=D95F455B;\"/>\n");
        w.write(
                "        <swimlanes"
                    + " value=\"locked=false;orientation=0;width=0;inbar=false;names=false;color=-1;bold=false;fcol=0;tcol=-1;ofCol=-1;ufCol=-1;hl=0;ufh=0;cls=0;SwimlaneFont=lfh:-10,lfw:0,lfi:0,lfu:0,lfs:0,lfface:Calibri,lfe:0,lfo:0,lfchar:1,lfop:0,lfcp:0,lfq:0,lfpf=0,lfWidth=0;\"/>\n");
        w.write(
                "        <matrixitems"
                    + " value=\"locked=false;matrixactive=false;swimlanesactive=true;kanbanactive=false;width=1;clrLine=0;\"/>\n");
        w.write("        <extendedProperties/>\n");
        w.write("        <elements>\n");

        Map<String, Map<String, Object>> posMap = new HashMap<>();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        boolean hayPos = false;
        if (posicionesClases != null) {
            for (var p : posicionesClases) {
                String idC = str(p.get("id_clase"));
                if (idC != null) {
                    posMap.put(idC, p);
                    if (p.get("x") instanceof Number nx && p.get("y") instanceof Number ny) {
                        minX = Math.min(minX, nx.doubleValue());
                        minY = Math.min(minY, ny.doubleValue());
                        hayPos = true;
                    }
                }
            }
        }
        // Desplazamiento para asegurar que todas las coordenadas sean estrictamente positivas (>= 50)
        double shiftX = hayPos && minX < 50 ? 50 - minX : 0;
        double shiftY = hayPos && minY < 50 ? 50 - minY : 0;

        int seq = 1;
        for (int i = 0; i < clases.size(); i++) {
            var c = clases.get(i);
            String cid = xmiId(str(c.get("id")));
            String rawId = str(c.get("id"));
            int left, top, right, bottom;

            if (posMap.containsKey(rawId)) {
                var p = posMap.get(rawId);
                double x = p.get("x") instanceof Number n ? n.doubleValue() : 80;
                double y = p.get("y") instanceof Number n ? n.doubleValue() : 80;
                double wVal = p.get("ancho") instanceof Number n ? n.doubleValue() : 180;
                double hVal = p.get("alto") instanceof Number n ? n.doubleValue() : 120;
                left = (int) Math.round(x + shiftX);
                int yPos = (int) Math.round(y + shiftY);
                top = -yPos;
                right = left + (int) Math.round(wVal > 0 ? wVal : 180);
                bottom = -(yPos + (int) Math.round(hVal > 0 ? hVal : 120));
            } else {
                int col = i % 4;
                int row = i / 4;
                left = 80 + col * 260;
                int yPos = 80 + row * 180;
                top = -yPos;
                right = left + 180;
                bottom = -(yPos + 110);
            }

            String geom =
                    "Left=" + left + ";Top=" + top + ";Right=" + right + ";Bottom=" + bottom + ";";
            String duid = cid.length() >= 13 ? cid.substring(5, 13) : cid;
            w.write(
                    "          <element geometry=\""
                            + geom
                            + "\" subject=\""
                            + cid
                            + "\" seqno=\""
                            + (seq++)
                            + "\" style=\"DUID="
                            + duid
                            + ";\"/>\n");
        }

        // Conectores en el diagrama
        for (var r : relaciones) {
            String rid = xmiId(str(r.get("id")));
            w.write(
                    "          <element geometry=\"SX=0;SY=0;EX=0;EY=0;Path=;\" subject=\""
                            + rid
                            + "\" style=\";Hidden=0;\"/>\n");
        }

        w.write("        </elements>\n");
        w.write("      </diagram>\n");
        w.write("    </diagrams>\n");
        w.write("  </xmi:Extension>\n");
    }
}
