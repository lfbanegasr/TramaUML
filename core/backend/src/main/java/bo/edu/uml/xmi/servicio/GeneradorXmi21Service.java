package bo.edu.uml.xmi.servicio;

import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Motor de generación XMI 2.1 / UML 2.1 compatible con Enterprise Architect 12-16+.
 *
 * <p>Produce XML conforme a OMG XMI 2.1 mapeando paquetes, clases, atributos, operaciones y
 * asociaciones con cardinalidades usando {@code lowerValue}/{@code upperValue}. No depende de
 * Jackson ni de ningún modelo de persistencia; recibe listas de mapas provenientes del repositorio.
 */
@Service
public class GeneradorXmi21Service {

    private static final String XMI_NS = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML_NS = "http://schema.omg.org/spec/UML/2.1";
    private static final String EXPORTER = "Plataforma UML Colaborativa";
    private static final String EXPORTER_VERSION = "0.11.0";

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

        // Documentación del exportador
        w.write(
                "  <xmi:Documentation exporter=\""
                        + esc(EXPORTER)
                        + "\" exporterVersion=\""
                        + esc(EXPORTER_VERSION)
                        + "\"/>\n");

        // Modelo raíz
        String modelId = "_MODEL_" + nombreModelo.replaceAll("[^A-Za-z0-9]", "_");
        w.write(
                "  <uml:Model xmi:type=\"uml:Model\" xmi:id=\""
                        + esc(modelId)
                        + "\" name=\""
                        + esc(nombreModelo)
                        + "\">\n");

        // Índices por ID para búsqueda O(1)
        Map<String, List<Map<String, Object>>> atribPorClase = agrupar(atributos, "id_clase");
        Map<String, List<Map<String, Object>>> opPorClase = agrupar(operaciones, "id_clase");
        Map<String, List<Map<String, Object>>> paramPorOp = agrupar(parametros, "id_operacion");

        // Paquetes raíz (sin padre) → recursivo
        escribirPaquetes(w, null, paquetes, clases, atribPorClase, opPorClase, paramPorOp);

        // Clases sin paquete (id_paquete == null)
        for (var c : clases) {
            if (c.get("id_paquete") == null) {
                escribirClase(w, c, "    ", atribPorClase, opPorClase, paramPorOp);
            }
        }

        // Asociaciones (relaciones)
        for (var r : relaciones) {
            escribirRelacion(w, r);
        }

        w.write("  </uml:Model>\n");
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
            Map<String, List<Map<String, Object>>> paramPorOp) {

        for (var p : paquetes) {
            String padre = str(p.get("id_paquete_padre"));
            boolean esRaiz = idPadreFiltro == null && (padre == null || padre.isBlank());
            boolean esHijo = idPadreFiltro != null && idPadreFiltro.equals(padre);
            if (!esRaiz && !esHijo) continue;

            String id = xmiId(str(p.get("id")));
            String nombre = str(p.get("nombre"));
            w.write(
                    "    <packagedElement xmi:type=\"uml:Package\" xmi:id=\""
                            + id
                            + "\" name=\""
                            + esc(nombre)
                            + "\">\n");

            // Sub-paquetes
            escribirPaquetes(
                    w, str(p.get("id")), paquetes, clases, atribPorClase, opPorClase, paramPorOp);

            // Clases de este paquete
            for (var c : clases) {
                if (str(p.get("id")).equals(str(c.get("id_paquete")))) {
                    escribirClase(w, c, "      ", atribPorClase, opPorClase, paramPorOp);
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
            Map<String, List<Map<String, Object>>> paramPorOp) {

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

        if ("uml:Generalization".equals(tipo)) {
            // Herencia: elemento dentro de la clase
            w.write(
                    "    <packagedElement xmi:type=\"uml:Class\" xmi:id=\""
                            + id
                            + "_gen_wrapper\" name=\"\">\n");
            w.write(
                    "      <generalization xmi:type=\"uml:Generalization\" xmi:id=\""
                            + id
                            + "\" specific=\""
                            + origen
                            + "\" general=\""
                            + destino
                            + "\"/>\n");
            w.write("    </packagedElement>\n");
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
                        + " memberEnd=\""
                        + id
                        + "_src "
                        + id
                        + "_tgt\">\n");

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
                        + ">\n");
        escribirCardinalidad(w, r.get("minimo_origen"), r.get("maximo_origen"), "        ");
        w.write("      </ownedEnd>\n");

        // Extremo destino
        String rolDestino = str(r.get("rol_destino"));
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
                        + (rolDestino != null && !rolDestino.isBlank()
                                ? " name=\"" + esc(rolDestino) + "\""
                                : "")
                        + ">\n");
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
                            + " value=\"*\"/>\n");
        } else {
            int max = ((Number) maxObj).intValue();
            if (max < 0) {
                w.write(
                        indent
                                + "<upperValue xmi:type=\"uml:LiteralUnlimitedNatural\""
                                + " value=\"*\"/>\n");
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

    /** Convierte UUID a xmi:id con prefijo {@code _}. */
    private String xmiId(String uuid) {
        if (uuid == null) return "_unknown";
        return "_" + uuid.replace("-", "");
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
}
