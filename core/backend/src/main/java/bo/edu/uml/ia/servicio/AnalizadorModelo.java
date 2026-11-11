package bo.edu.uml.ia.servicio;

import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lectura determinista del modelo UML vigente. Convierte los elementos con UUID en una vista legible
 * por nombres (clases, relaciones, multiplicidades), detecta hallazgos de diseño verificables y
 * localiza las clases «en foco» mencionadas en una petición. No consulta la base de datos ni
 * modifica nada; sus resultados son evidencia para el proveedor generativo y para el mentor local.
 */
public final class AnalizadorModelo {
  private AnalizadorModelo() {}

  private static final Pattern DINERO =
      Pattern.compile("(?i).*(precio|monto|total|saldo|costo|importe|subtotal|tarifa).*");

  public record ClaseInfo(
      UUID id,
      String nombre,
      String paquete,
      boolean persistente,
      boolean abstracta,
      List<String> atributos,
      List<String> operaciones,
      boolean tieneIdentificador) {}

  public record RelacionInfo(
      UUID id,
      UUID idOrigen,
      UUID idDestino,
      String origen,
      String destino,
      String tipo,
      String nombre,
      String multiplicidadOrigen,
      String multiplicidadDestino,
      boolean maximoOrigenIlimitado,
      boolean maximoDestinoIlimitado,
      boolean sinMultiplicidad) {

    public String descripcion() {
      return origen
          + " ["
          + multiplicidadOrigen
          + "] "
          + tipo
          + (nombre == null || nombre.isBlank() ? "" : " «" + nombre + "»")
          + " ["
          + multiplicidadDestino
          + "] "
          + destino;
    }
  }

  /** severidad: ERROR, ADVERTENCIA, SUGERENCIA o INFO; clase puede ser null (hallazgo global). */
  public record Hallazgo(String severidad, String codigo, String clase, String mensaje) {}

  public record Analisis(
      List<ClaseInfo> clases,
      List<RelacionInfo> relaciones,
      List<String> paquetes,
      List<Hallazgo> hallazgos) {

    public boolean vacio() {
      return clases.isEmpty();
    }

    public Optional<ClaseInfo> clase(String nombre) {
      String n = normalizar(nombre);
      return clases.stream().filter(c -> normalizar(c.nombre()).equals(n)).findFirst();
    }

    public List<RelacionInfo> relacionesDe(ClaseInfo c) {
      return relaciones.stream()
          .filter(r -> c.id().equals(r.idOrigen()) || c.id().equals(r.idDestino()))
          .toList();
    }
  }

  public static String normalizar(String t) {
    if (t == null) return "";
    return Normalizer.normalize(t, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  public static Analisis analizar(List<ElementoContexto> elementos) {
    Map<UUID, String> paquetes = new HashMap<>();
    for (var e : elementos) if ("PAQUETE".equals(e.tipo())) paquetes.put(e.id(), e.nombre());

    Map<UUID, List<String>> atributos = new HashMap<>(), operaciones = new HashMap<>();
    Set<UUID> conIdentificador = new HashSet<>();
    List<Hallazgo> hallazgos = new ArrayList<>();
    for (var e : elementos) {
      if ("ATRIBUTO".equals(e.tipo()) && e.idContenedor() != null) {
        JsonNode v = e.valor();
        boolean id = v.path("esIdentificador").asBoolean(false);
        String tipo = v.path("tipoUml").asText("?");
        atributos
            .computeIfAbsent(e.idContenedor(), k -> new ArrayList<>())
            .add(e.nombre() + ": " + tipo + (id ? " (id)" : ""));
        if (id) conIdentificador.add(e.idContenedor());
        if (DINERO.matcher(e.nombre() == null ? "" : e.nombre()).matches()
            && Set.of("double", "float").contains(tipo.toLowerCase(Locale.ROOT)))
          hallazgos.add(
              new Hallazgo(
                  "SUGERENCIA",
                  "DINERO_PUNTO_FLOTANTE",
                  null,
                  "El atributo «"
                      + e.nombre()
                      + "» representa dinero y usa "
                      + tipo
                      + ". Considera BigDecimal para evitar errores de redondeo."));
      }
      if ("OPERACION".equals(e.tipo()) && e.idContenedor() != null) {
        JsonNode v = e.valor();
        StringBuilder firma = new StringBuilder(e.nombre()).append('(');
        boolean primero = true;
        for (JsonNode p : v.path("parametros")) {
          if (!primero) firma.append(", ");
          firma.append(p.path("nombre").asText("?")).append(": ").append(p.path("tipoUml").asText("?"));
          primero = false;
        }
        firma.append("): ").append(v.path("tipoRetorno").asText("void"));
        operaciones.computeIfAbsent(e.idContenedor(), k -> new ArrayList<>()).add(firma.toString());
      }
    }

    List<ClaseInfo> clases = new ArrayList<>();
    for (var e : elementos) {
      if (!"CLASE".equals(e.tipo())) continue;
      JsonNode v = e.valor();
      UUID idPaquete = idDe(v.path("idPaquete"));
      clases.add(
          new ClaseInfo(
              e.id(),
              e.nombre(),
              idPaquete == null ? null : paquetes.get(idPaquete),
              v.path("esPersistente").asBoolean(false),
              v.path("esAbstracto").asBoolean(false),
              List.copyOf(atributos.getOrDefault(e.id(), List.of())),
              List.copyOf(operaciones.getOrDefault(e.id(), List.of())),
              conIdentificador.contains(e.id())));
    }
    Map<UUID, String> nombres = new HashMap<>();
    clases.forEach(c -> nombres.put(c.id(), c.nombre()));

    List<RelacionInfo> relaciones = new ArrayList<>();
    for (var e : elementos) {
      if (!"RELACION".equals(e.tipo())) continue;
      JsonNode v = e.valor();
      UUID o = idDe(v.path("origen")), d = idDe(v.path("destino"));
      if (o == null || d == null || !nombres.containsKey(o) || !nombres.containsKey(d)) continue;
      String tipo = v.path("tipo").asText("ASOCIACION");
      boolean estructural = !Set.of("GENERALIZACION", "DEPENDENCIA").contains(tipo);
      boolean sinMult =
          estructural
              && (sinLimites(v, "Origen") || sinLimites(v, "Destino"));
      relaciones.add(
          new RelacionInfo(
              e.id(),
              o,
              d,
              nombres.get(o),
              nombres.get(d),
              tipo,
              v.path("nombre").isNull() ? null : v.path("nombre").asText(null),
              multiplicidad(v, "Origen"),
              multiplicidad(v, "Destino"),
              ilimitado(v, "Origen"),
              ilimitado(v, "Destino"),
              sinMult));
    }
    hallazgos.addAll(hallazgos(clases, relaciones));
    return new Analisis(
        List.copyOf(clases),
        List.copyOf(relaciones),
        paquetes.values().stream().sorted().toList(),
        List.copyOf(hallazgos));
  }

  private static UUID idDe(JsonNode n) {
    if (n == null || !n.isTextual()) return null;
    try {
      return UUID.fromString(n.asText());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static boolean sinLimites(JsonNode v, String lado) {
    JsonNode min = v.path("minimo" + lado), max = v.path("maximo" + lado);
    return (min.isNull() || min.isMissingNode()) && (max.isNull() || max.isMissingNode());
  }

  private static boolean ilimitado(JsonNode v, String lado) {
    JsonNode max = v.path("maximo" + lado);
    return max.isNull() || max.isMissingNode() || max.asInt(0) > 1;
  }

  private static String multiplicidad(JsonNode v, String lado) {
    JsonNode min = v.path("minimo" + lado), max = v.path("maximo" + lado);
    boolean sinMin = min.isNull() || min.isMissingNode();
    boolean sinMax = max.isNull() || max.isMissingNode();
    if (sinMin && sinMax) return "?";
    String maximo = sinMax ? "*" : String.valueOf(max.asInt());
    String minimo = sinMin ? "0" : String.valueOf(min.asInt());
    return minimo.equals(maximo) ? minimo : minimo + ".." + maximo;
  }

  private static List<Hallazgo> hallazgos(List<ClaseInfo> clases, List<RelacionInfo> relaciones) {
    List<Hallazgo> h = new ArrayList<>();
    if (clases.isEmpty()) {
      h.add(
          new Hallazgo(
              "INFO",
              "MODELO_VACIO",
              null,
              "El modelo no tiene clases todavía; conviene empezar por el núcleo del dominio."));
      return h;
    }
    Set<UUID> conRelacion = new HashSet<>();
    for (var r : relaciones) {
      conRelacion.add(r.idOrigen());
      conRelacion.add(r.idDestino());
    }
    for (var c : clases) {
      if (clases.size() > 1 && !conRelacion.contains(c.id()))
        h.add(
            new Hallazgo(
                "ADVERTENCIA",
                "CLASE_AISLADA",
                c.nombre(),
                "«" + c.nombre() + "» no está relacionada con ninguna otra clase."));
      if (c.atributos().isEmpty() && !c.abstracta())
        h.add(
            new Hallazgo(
                "ADVERTENCIA",
                "CLASE_SIN_ATRIBUTOS",
                c.nombre(),
                "«" + c.nombre() + "» no tiene atributos."));
      if (c.persistente() && !c.tieneIdentificador())
        h.add(
            new Hallazgo(
                "ADVERTENCIA",
                "PERSISTENTE_SIN_IDENTIFICADOR",
                c.nombre(),
                "«" + c.nombre() + "» es persistente pero no declara atributo identificador."));
    }
    Map<String, List<ClaseInfo>> porNombre = new HashMap<>();
    for (var c : clases)
      porNombre.computeIfAbsent(normalizar(c.nombre()), k -> new ArrayList<>()).add(c);
    porNombre.values().stream()
        .filter(l -> l.size() > 1)
        .forEach(
            l ->
                h.add(
                    new Hallazgo(
                        "ADVERTENCIA",
                        "NOMBRE_DUPLICADO",
                        l.getFirst().nombre(),
                        "Hay " + l.size() + " clases llamadas «" + l.getFirst().nombre() + "».")));
    boolean algunaOperacion = clases.stream().anyMatch(c -> !c.operaciones().isEmpty());
    if (clases.size() >= 2 && !algunaOperacion)
      h.add(
          new Hallazgo(
              "INFO",
              "SIN_COMPORTAMIENTO",
              null,
              "Ninguna clase declara operaciones; si es un diagrama de diseño, falta"
                  + " comportamiento."));
    for (var r : relaciones) {
      if (r.sinMultiplicidad())
        h.add(
            new Hallazgo(
                "ADVERTENCIA",
                "RELACION_SIN_MULTIPLICIDAD",
                r.origen(),
                "La relación " + r.descripcion() + " no define multiplicidad en algún extremo."));
      if ("ASOCIACION".equals(r.tipo()) && r.maximoOrigenIlimitado() && r.maximoDestinoIlimitado()
          && !r.sinMultiplicidad())
        h.add(
            new Hallazgo(
                "SUGERENCIA",
                "MUCHOS_A_MUCHOS",
                r.origen(),
                "La asociación "
                    + r.descripcion()
                    + " es de muchos a muchos: evalúa una clase intermedia si la relación tiene"
                    + " datos propios (fecha, cantidad, estado)."));
      if ("COMPOSICION".equals(r.tipo()) && r.maximoOrigenIlimitado())
        h.add(
            new Hallazgo(
                "ERROR",
                "COMPOSICION_MAXIMO_ORIGEN",
                r.origen(),
                "En la composición "
                    + r.descripcion()
                    + " el conjunto (origen) no puede tener máximo mayor que 1."));
    }
    Set<String> ciclo = cicloGeneralizacion(clases, relaciones);
    if (!ciclo.isEmpty())
      h.add(
          new Hallazgo(
              "ERROR",
              "CICLO_GENERALIZACION",
              null,
              "Hay un ciclo de generalización entre: " + String.join(", ", ciclo) + "."));
    return h;
  }

  private static Set<String> cicloGeneralizacion(
      List<ClaseInfo> clases, List<RelacionInfo> relaciones) {
    Map<UUID, List<UUID>> padres = new HashMap<>();
    for (var r : relaciones)
      if ("GENERALIZACION".equals(r.tipo()))
        padres.computeIfAbsent(r.idOrigen(), k -> new ArrayList<>()).add(r.idDestino());
    Map<UUID, String> nombres = new HashMap<>();
    clases.forEach(c -> nombres.put(c.id(), c.nombre()));
    Set<String> encontrados = new TreeSet<>();
    for (UUID inicio : padres.keySet()) {
      Deque<UUID> pila = new ArrayDeque<>(padres.getOrDefault(inicio, List.of()));
      Set<UUID> visitados = new HashSet<>();
      while (!pila.isEmpty()) {
        UUID actual = pila.pop();
        if (actual.equals(inicio)) {
          encontrados.add(nombres.get(inicio));
          break;
        }
        if (visitados.add(actual)) pila.addAll(padres.getOrDefault(actual, List.of()));
      }
    }
    return encontrados;
  }

  /** Clases cuyo nombre aparece como palabra en la petición, en orden de aparición. */
  public static List<ClaseInfo> enfoque(Analisis a, String peticion) {
    String texto = " " + normalizar(peticion).replaceAll("[^a-z0-9_]+", " ") + " ";
    List<ClaseInfo> encontradas = new ArrayList<>();
    for (var c : a.clases()) {
      String n = normalizar(c.nombre());
      if (n.length() < 3) continue;
      // Acepta singular/plural simple: «Pedido» coincide con «pedidos».
      if (texto.contains(" " + n + " ")
          || texto.contains(" " + n + "s ")
          || texto.contains(" " + n + "es ")
          || (n.endsWith("s") && texto.contains(" " + n.substring(0, n.length() - 1) + " ")))
        encontradas.add(c);
    }
    return encontradas.size() > 5 ? encontradas.subList(0, 5) : encontradas;
  }

  /** Vista JSON compacta y legible: no sustituye a «elementos», que conserva los UUID. */
  public static ObjectNode aJson(ObjectMapper json, Analisis a, String peticion) {
    ObjectNode raiz = json.createObjectNode();
    ArrayNode paquetes = raiz.putArray("paquetes");
    a.paquetes().forEach(paquetes::add);
    ArrayNode clases = raiz.putArray("clases");
    for (var c : a.clases()) {
      ObjectNode o = clases.addObject();
      o.put("nombre", c.nombre());
      if (c.paquete() != null) o.put("paquete", c.paquete());
      o.put("persistente", c.persistente());
      o.put("abstracta", c.abstracta());
      ArrayNode at = o.putArray("atributos");
      c.atributos().forEach(at::add);
      ArrayNode op = o.putArray("operaciones");
      c.operaciones().forEach(op::add);
      ArrayNode rel = o.putArray("relaciones");
      a.relacionesDe(c).forEach(r -> rel.add(r.descripcion()));
    }
    ArrayNode relaciones = raiz.putArray("relaciones");
    a.relaciones().forEach(r -> relaciones.add(r.descripcion()));
    ArrayNode hallazgos = raiz.putArray("hallazgos");
    for (var h : a.hallazgos()) {
      ObjectNode o = hallazgos.addObject();
      o.put("severidad", h.severidad());
      o.put("codigo", h.codigo());
      if (h.clase() != null) o.put("clase", h.clase());
      o.put("mensaje", h.mensaje());
    }
    ArrayNode enfoque = raiz.putArray("enfoque");
    for (var c : enfoque(a, peticion)) {
      ObjectNode o = enfoque.addObject();
      o.put("clase", c.nombre());
      ArrayNode vecinos = o.putArray("vecinos");
      a.relacionesDe(c)
          .forEach(r -> vecinos.add(c.id().equals(r.idOrigen()) ? r.destino() : r.origen()));
      ArrayNode rel = o.putArray("relaciones");
      a.relacionesDe(c).forEach(r -> rel.add(r.descripcion()));
    }
    return raiz;
  }
}
