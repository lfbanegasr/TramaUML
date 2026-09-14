package bo.edu.uml.validacion.modelo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.regex.Pattern;

public final class MotorSemantico {
    public record Hallazgo(
            UUID regla,
            String codigo,
            String severidad,
            String entidad,
            UUID id,
            String mensaje,
            String recomendacion,
            UUID clase) {}

    public record Resultado(List<Hallazgo> hallazgos, boolean incompleto) {}

    private static final Set<String> CLAVES =
            Set.of(
                    "operacion.firma.unica",
                    "persistente.id.obligatorio",
                    "clase.nombre.unico",
                    "clase.nombre.obligatorio",
                    "atributo.tipo.obligatorio",
                    "xmi.caracteristica.no_compatible",
                    "generalizacion.sin_ciclos",
                    "relacion.cardinalidad.valida",
                    "relacion.extremos.validos");
    private final List<Hallazgo> hallazgos = new ArrayList<>();
    private final Map<String, JsonNode> reglas = new HashMap<>();
    private JsonNode modelo;
    private final Map<String, List<JsonNode>> atributosPorClase = new HashMap<>();
    private final Map<String, List<JsonNode>> parametrosPorOperacion = new HashMap<>();

    public Resultado evaluar(JsonNode instantanea, JsonNode catalogo) {
        modelo = instantanea;
        for (JsonNode a : modelo.path("atributos"))
            atributosPorClase
                    .computeIfAbsent(a.path("id_clase").asText(), k -> new ArrayList<>())
                    .add(a);
        for (JsonNode p : modelo.path("parametros"))
            parametrosPorOperacion
                    .computeIfAbsent(p.path("id_operacion").asText(), k -> new ArrayList<>())
                    .add(p);
        for (JsonNode regla : catalogo)
            reglas.put(regla.path("clave_implementacion").asText(), regla);
        boolean incompleto = false;
        for (String clave : CLAVES)
            if (!reglas.containsKey(clave)) {
                incompleto = true;
                local(
                        "REGLA_NO_DISPONIBLE",
                        "ERROR",
                        "MODELO_UML",
                        null,
                        "No está disponible la regla " + clave,
                        "Revisar el catálogo vigente del servidor.",
                        null);
            }
        for (String clave : reglas.keySet())
            if (!CLAVES.contains(clave)) {
                incompleto = true;
                local(
                        "REGLA_NO_IMPLEMENTADA",
                        "ERROR",
                        "MODELO_UML",
                        null,
                        "Regla activa sin implementación: " + clave,
                        "Implementar esta regla antes de considerar válido el modelo.",
                        null);
            }
        var clases = indexar(modelo.path("clases"));
        var paquetes = indexar(modelo.path("paquetes"));
        if (clases.isEmpty())
            local(
                    "MODELO_VACIO",
                    "ERROR",
                    "MODELO_UML",
                    null,
                    "El modelo no contiene clases.",
                    "Añade clases y sus datos mínimos antes de generar código.",
                    null);
        Map<String, Integer> nombres = new HashMap<>();
        Map<String, Integer> simples = new HashMap<>();
        Set<String> completos = new HashSet<>();
        for (JsonNode c : clases.values()) {
            String nombre = c.path("nombre").asText().trim();
            String paquete = c.path("id_paquete").asText("");
            nombres.merge(paquete + "|" + nombre.toLowerCase(Locale.ROOT), 1, Integer::sum);
            simples.merge(nombre, 1, Integer::sum);
            completos.add(nombrePaquete(paquete, paquetes, new HashSet<>()) + nombre);
        }
        validarPaquetes(paquetes);
        validarClases(clases, nombres);
        validarAtributos(clases, simples, completos);
        validarOperaciones(clases, simples, completos);
        validarRelaciones(clases);
        boolean xmi = false;
        for (String grupo : List.of("clases", "paquetes", "relaciones"))
            for (JsonNode e : modelo.path(grupo)) if (e.hasNonNull("id_externo_xmi")) xmi = true;
        if (xmi)
            regla(
                    "xmi.caracteristica.no_compatible",
                    "MODELO_UML",
                    null,
                    "La compatibilidad XMI completa no se comprobó en esta fase.",
                    "Revisar con el servicio de intercambio antes de exportar.",
                    null);
        return new Resultado(List.copyOf(hallazgos), incompleto);
    }

    private void validarPaquetes(Map<String, JsonNode> paquetes) {
        for (JsonNode p : paquetes.values()) {
            if (!identificador(p.path("nombre").asText()))
                local(
                        "NOMBRE_CODIGO",
                        "ADVERTENCIA",
                        "PAQUETE_UML",
                        id(p),
                        "El nombre del paquete necesita una normalización para generar código.",
                        "Usa un nombre compatible con identificadores.",
                        null);
            String padre = p.path("id_paquete_padre").asText("");
            if (!padre.isEmpty() && !paquetes.containsKey(padre))
                local(
                        "REFERENCIA_PAQUETE",
                        "ERROR",
                        "PAQUETE_UML",
                        id(p),
                        "El paquete padre no está activo.",
                        "Reubica el paquete.",
                        null);
            if (cicloPaquete(p, paquetes))
                local(
                        "CICLO_PAQUETES",
                        "ERROR",
                        "PAQUETE_UML",
                        id(p),
                        "La jerarquía de paquetes contiene un ciclo.",
                        "Revisa los padres de los paquetes.",
                        null);
        }
    }

    private void validarClases(Map<String, JsonNode> clases, Map<String, Integer> nombres) {
        for (JsonNode c : clases.values()) {
            UUID id = id(c);
            String nombre = c.path("nombre").asText().trim();
            if (nombre.isEmpty())
                regla(
                        "clase.nombre.obligatorio",
                        "CLASE_UML",
                        id,
                        "La clase no tiene nombre.",
                        "Define un nombre.",
                        id);
            if (nombres.getOrDefault(
                            c.path("id_paquete").asText("") + "|" + nombre.toLowerCase(Locale.ROOT),
                            0)
                    > 1)
                regla(
                        "clase.nombre.unico",
                        "CLASE_UML",
                        id,
                        "Nombre de clase repetido dentro del paquete.",
                        "Renombra o reubica la clase.",
                        id);
            if (!identificador(nombre))
                local(
                        "NOMBRE_CODIGO",
                        "ADVERTENCIA",
                        "CLASE_UML",
                        id,
                        "El nombre de clase necesita normalización para generar código.",
                        "Usa un identificador válido.",
                        id);
            if (c.path("es_persistente").asBoolean()
                    && hijos("atributos", "id_clase", c.path("id").asText()).stream()
                            .noneMatch(a -> a.path("es_identificador").asBoolean()))
                regla(
                        "persistente.id.obligatorio",
                        "CLASE_UML",
                        id,
                        "La clase persistente " + nombre + " no tiene identificador.",
                        "Añade un atributo identificador con multiplicidad 1..1.",
                        id);
        }
    }

    private void validarAtributos(
            Map<String, JsonNode> clases, Map<String, Integer> simples, Set<String> completos) {
        for (JsonNode a : modelo.path("atributos")) {
            UUID clase = uuid(a.path("id_clase").asText());
            String tipo = a.path("tipo_uml").asText();
            if (tipo.isBlank())
                regla(
                        "atributo.tipo.obligatorio",
                        "ATRIBUTO_UML",
                        id(a),
                        "El atributo no declara tipo.",
                        "Define su tipo UML.",
                        clase);
            else tipo(tipo, "ATRIBUTO_UML", id(a), clase, simples, completos, false);
            referencia(a, "id_clase", clases, "ATRIBUTO_UML", clase);
            if (!cardinalidad(a, "multiplicidad_minima", "multiplicidad_maxima")
                    || (a.path("es_identificador").asBoolean()
                            && (a.path("multiplicidad_minima").asInt() != 1
                                    || a.path("multiplicidad_maxima").asInt(-1) != 1
                                    || a.path("es_estatico").asBoolean())))
                local(
                        "IDENTIFICADOR_MULTIPLICIDAD",
                        "ERROR",
                        "ATRIBUTO_UML",
                        id(a),
                        "La multiplicidad o el identificador no son coherentes.",
                        "Un identificador requiere 1..1 y no ser estático.",
                        clase);
            restricciones(a, clase);
            predeterminado(a, tipo, "ATRIBUTO_UML", clase);
        }
    }

    private void validarOperaciones(
            Map<String, JsonNode> clases, Map<String, Integer> simples, Set<String> completos) {
        Map<String, Integer> firmas = new HashMap<>();
        for (JsonNode o : modelo.path("operaciones")) {
            UUID clase = uuid(o.path("id_clase").asText());
            var parametros = hijos("parametros", "id_operacion", o.path("id").asText());
            parametros.sort(Comparator.comparingInt(p -> p.path("orden").asInt()));
            String firma =
                    o.path("id_clase").asText()
                            + "|"
                            + o.path("nombre").asText().toLowerCase(Locale.ROOT)
                            + parametros.stream()
                                    .map(
                                            p ->
                                                    p.path("tipo_uml").asText()
                                                            + "|"
                                                            + p.path("direccion").asText())
                                    .toList();
            if (firmas.merge(firma, 1, Integer::sum) > 1)
                regla(
                        "operacion.firma.unica",
                        "OPERACION_UML",
                        id(o),
                        "La firma de operación está repetida.",
                        "Cambia el nombre o los tipos/direcciones de parámetros.",
                        clase);
            referencia(o, "id_clase", clases, "OPERACION_UML", clase);
            tipo(
                    o.path("tipo_retorno").isNull()
                            ? "void"
                            : o.path("tipo_retorno").asText("void"),
                    "OPERACION_UML",
                    id(o),
                    clase,
                    simples,
                    completos,
                    true);
            Set<String> pn = new HashSet<>();
            for (JsonNode p : parametros) {
                if (!pn.add(p.path("nombre").asText().toLowerCase(Locale.ROOT)))
                    local(
                            "PARAMETRO_REPETIDO",
                            "ERROR",
                            "PARAMETRO_UML",
                            id(p),
                            "Parámetro repetido.",
                            "Usa nombres distintos dentro de la operación.",
                            clase);
                tipo(
                        p.path("tipo_uml").asText(),
                        "PARAMETRO_UML",
                        id(p),
                        clase,
                        simples,
                        completos,
                        false);
                predeterminado(p, p.path("tipo_uml").asText(), "PARAMETRO_UML", clase);
            }
        }
    }

    private void validarRelaciones(Map<String, JsonNode> clases) {
        Map<String, List<String>> padres = new HashMap<>();
        Map<String, Integer> composiciones = new HashMap<>();
        for (JsonNode r : modelo.path("relaciones"))
            if (r.path("tipo_relacion").asText().equals("GENERALIZACION"))
                padres.computeIfAbsent(r.path("id_clase_origen").asText(), k -> new ArrayList<>())
                        .add(r.path("id_clase_destino").asText());
        for (JsonNode r : modelo.path("relaciones")) {
            UUID clase = uuid(r.path("id_clase_origen").asText());
            String origen = r.path("id_clase_origen").asText(),
                    destino = r.path("id_clase_destino").asText(),
                    tipo = r.path("tipo_relacion").asText();
            if (!clases.containsKey(origen) || !clases.containsKey(destino))
                regla(
                        "relacion.extremos.validos",
                        "RELACION_UML",
                        id(r),
                        "La relación tiene un extremo fuera del modelo.",
                        "Revisa sus clases origen y destino.",
                        clase);
            if (Set.of("ASOCIACION", "AGREGACION", "COMPOSICION").contains(tipo)
                    && (!cardinalidad(r, "minimo_origen", "maximo_origen")
                            || !cardinalidad(r, "minimo_destino", "maximo_destino")))
                regla(
                        "relacion.cardinalidad.valida",
                        "RELACION_UML",
                        id(r),
                        "Cardinalidades incoherentes.",
                        "Revisa los mínimos y máximos; null representa *.",
                        clase);
            if (tipo.equals("GENERALIZACION") && alcanza(destino, origen, padres, new HashSet<>()))
                regla(
                        "generalizacion.sin_ciclos",
                        "RELACION_UML",
                        id(r),
                        "La generalización produce una herencia circular.",
                        "Elimina el ciclo de herencia.",
                        clase);
            if (tipo.equals("COMPOSICION")
                    && (origen.equals(destino)
                            || composiciones.merge(destino, 1, Integer::sum) > 1))
                local(
                        "COMPOSICION_PROPIETARIO",
                        "ERROR",
                        "RELACION_UML",
                        id(r),
                        "La composición no conserva una parte con propietario único.",
                        "Revisa el conjunto y la parte.",
                        clase);
        }
    }

    private void tipo(
            String t,
            String entidad,
            UUID id,
            UUID clase,
            Map<String, Integer> simples,
            Set<String> completos,
            boolean retorno) {
        Set<String> basicos =
                Set.of(
                        "String",
                        "Integer",
                        "Long",
                        "Double",
                        "Float",
                        "BigDecimal",
                        "Boolean",
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
        if (retorno && t.equals("void")) return;
        if (basicos.contains(t)) return;
        if (simples.getOrDefault(t, 0) > 1)
            local(
                    "TIPO_AMBIGUO",
                    "ERROR",
                    entidad,
                    id,
                    "Tipo ambiguo: " + t,
                    "Usa el nombre completo del paquete y la clase.",
                    clase);
        else if (!completos.contains(t) && !simples.containsKey(t))
            local(
                    "TIPO_NO_RESUELTO",
                    "ERROR",
                    entidad,
                    id,
                    "No se encuentra el tipo UML " + t,
                    "Usa un tipo básico admitido o una clase existente, con paquete si es"
                            + " necesario.",
                    clase);
    }

    private void restricciones(JsonNode a, UUID clase) {
        JsonNode r = a.path("restricciones_json");
        if (!r.isObject()) {
            local(
                    "RESTRICCION_INVALIDA",
                    "ERROR",
                    "ATRIBUTO_UML",
                    id(a),
                    "Las restricciones no son un objeto JSON.",
                    "Revisa las restricciones.",
                    clase);
            return;
        }
        Set<String> soportadas = Set.of("min", "max", "minLength", "maxLength", "pattern");
        r.fieldNames()
                .forEachRemaining(
                        k -> {
                            if (!soportadas.contains(k))
                                local(
                                        "RESTRICCION_NO_INTERPRETADA",
                                        "ADVERTENCIA",
                                        "ATRIBUTO_UML",
                                        id(a),
                                        "No se interpreta todavía la restricción " + k,
                                        "Revisa manualmente esa regla antes de generar código.",
                                        clase);
                        });
        for (String k : List.of("min", "max", "minLength", "maxLength"))
            if (r.has(k)
                    && (!r.get(k).isNumber()
                            || (k.endsWith("Length")
                                    && (!r.get(k).isIntegralNumber() || r.get(k).asInt() < 0))))
                local(
                        "RESTRICCION_INVALIDA",
                        "ERROR",
                        "ATRIBUTO_UML",
                        id(a),
                        "Restricción numérica inválida: " + k,
                        "Usa valores numéricos y longitudes enteras no negativas.",
                        clase);
        for (String[] par :
                List.of(new String[] {"min", "max"}, new String[] {"minLength", "maxLength"}))
            if (r.path(par[0]).isNumber()
                    && r.path(par[1]).isNumber()
                    && r.get(par[0]).decimalValue().compareTo(r.get(par[1]).decimalValue()) > 0)
                local(
                        "RESTRICCION_INVALIDA",
                        "ERROR",
                        "ATRIBUTO_UML",
                        id(a),
                        "El mínimo supera el máximo.",
                        "Corrige el intervalo.",
                        clase);
        if (r.has("pattern"))
            try {
                if (!r.get("pattern").isTextual()) throw new IllegalArgumentException();
                Pattern.compile(r.get("pattern").asText());
            } catch (IllegalArgumentException e) {
                local(
                        "RESTRICCION_INVALIDA",
                        "ERROR",
                        "ATRIBUTO_UML",
                        id(a),
                        "Patrón inválido.",
                        "Corrige la expresión regular; no se ejecuta como SQL.",
                        clase);
            }
    }

    private void predeterminado(JsonNode a, String t, String entidad, UUID clase) {
        if (!a.hasNonNull("valor_predeterminado")
                || a.get("valor_predeterminado").asText().isBlank()) return;
        String v = a.get("valor_predeterminado").asText();
        try {
            switch (t) {
                case "Integer", "int" -> Integer.parseInt(v);
                case "Short", "short" -> Short.parseShort(v);
                case "Long", "long" -> Long.parseLong(v);
                case "Byte", "byte" -> Byte.parseByte(v);
                case "Double", "double" -> {
                    if (!Double.isFinite(Double.parseDouble(v)))
                        throw new IllegalArgumentException();
                }
                case "Float", "float" -> {
                    if (!Float.isFinite(Float.parseFloat(v))) throw new IllegalArgumentException();
                }
                case "BigDecimal" -> new java.math.BigDecimal(v);
                case "Boolean", "boolean" -> {
                    if (!Set.of("true", "false").contains(v)) throw new IllegalArgumentException();
                }
                case "UUID" -> UUID.fromString(v);
                case "LocalDate" -> java.time.LocalDate.parse(v);
                case "LocalDateTime" -> java.time.LocalDateTime.parse(v);
                case "Instant" -> java.time.Instant.parse(v);
                default -> {}
            }
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            local(
                    "VALOR_PREDETERMINADO",
                    "ERROR",
                    entidad,
                    id(a),
                    "El valor predeterminado no corresponde al tipo " + t,
                    "Usa un literal compatible.",
                    clase);
        }
    }

    private void referencia(
            JsonNode e, String campo, Map<String, JsonNode> indice, String entidad, UUID clase) {
        if (!indice.containsKey(e.path(campo).asText()))
            local(
                    "REFERENCIA_INVALIDA",
                    "ERROR",
                    entidad,
                    id(e),
                    "La referencia no corresponde a un elemento activo.",
                    "Revisa el elemento padre.",
                    clase);
    }

    private boolean cardinalidad(JsonNode r, String min, String max) {
        return r.path(min).isIntegralNumber()
                && r.get(min).asLong() >= 0
                && (r.path(max).isNull()
                        || (r.path(max).isIntegralNumber()
                                && r.get(max).asLong() >= r.get(min).asLong()));
    }

    private boolean alcanza(
            String desde, String objetivo, Map<String, List<String>> padres, Set<String> vistos) {
        var cola = new ArrayDeque<String>();
        cola.add(desde);
        while (!cola.isEmpty()) {
            String id = cola.remove();
            if (id.equals(objetivo)) return true;
            if (vistos.add(id)) cola.addAll(padres.getOrDefault(id, List.of()));
        }
        return false;
    }

    private Map<String, JsonNode> indexar(JsonNode lista) {
        Map<String, JsonNode> r = new LinkedHashMap<>();
        for (JsonNode n : lista) r.put(n.path("id").asText(), n);
        return r;
    }

    private List<JsonNode> hijos(String lista, String campo, String id) {
        return new ArrayList<>(
                (lista.equals("atributos") ? atributosPorClase : parametrosPorOperacion)
                        .getOrDefault(id, List.of()));
    }

    private String nombrePaquete(String id, Map<String, JsonNode> paquetes, Set<String> vistos) {
        var partes = new ArrayDeque<String>();
        while (!id.isEmpty() && paquetes.containsKey(id) && vistos.add(id)) {
            JsonNode p = paquetes.get(id);
            partes.addFirst(p.path("nombre").asText());
            id = p.path("id_paquete_padre").asText("");
        }
        return partes.isEmpty() ? "" : String.join(".", partes) + ".";
    }

    private boolean cicloPaquete(JsonNode p, Map<String, JsonNode> paquetes) {
        Set<String> vistos = new HashSet<>();
        while (p != null) {
            if (!vistos.add(p.path("id").asText())) return true;
            p = paquetes.get(p.path("id_paquete_padre").asText());
        }
        return false;
    }

    private boolean identificador(String n) {
        if (n.isBlank()
                || javax.lang.model.SourceVersion.isKeyword(n)
                || !Character.isJavaIdentifierStart(n.charAt(0))) return false;
        for (int i = 1; i < n.length(); i++)
            if (!Character.isJavaIdentifierPart(n.charAt(i))) return false;
        return true;
    }

    private UUID id(JsonNode n) {
        return uuid(n.path("id").asText());
    }

    private UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void regla(
            String clave,
            String entidad,
            UUID id,
            String mensaje,
            String recomendacion,
            UUID clase) {
        JsonNode r = reglas.get(clave);
        if (r != null)
            hallazgos.add(
                    new Hallazgo(
                            uuid(r.path("id").asText()),
                            r.path("codigo").asText(),
                            r.path("severidad").asText(),
                            entidad,
                            id,
                            mensaje,
                            recomendacion,
                            clase));
    }

    private void local(
            String codigo,
            String severidad,
            String entidad,
            UUID id,
            String mensaje,
            String recomendacion,
            UUID clase) {
        hallazgos.add(
                new Hallazgo(null, codigo, severidad, entidad, id, mensaje, recomendacion, clase));
    }
}
