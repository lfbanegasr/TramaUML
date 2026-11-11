package bo.edu.uml.ia.servicio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Convierte datos no confiables del proveedor en propuestas limitadas, nunca en
 * ejecución.
 */
@Component
public class InterpreteRespuestaAgente {
  private final ObjectMapper json;
  private final MotorAsistenciaInteligente motor;
  private final MotorVisionDiagrama vision;

  public InterpreteRespuestaAgente(
      ObjectMapper json, MotorAsistenciaInteligente motor, MotorVisionDiagrama vision) {
    this.json = json;
    this.motor = motor;
    this.vision = vision;
  }

  public AgenteColaborativoServicio.Respuesta interpretar(
      String respuesta, Contexto contexto, JsonNode datos, boolean consulta) {
    if (respuesta.length() > 100000)
      throw new ApiException(422, "La respuesta del proveedor supera el límite.");
    JsonNode r;
    try {
      r = json.readTree(respuesta);
    } catch (Exception e) {
      throw new ApiException(422, "El proveedor no devolvió una respuesta estructurada válida.");
    }
    String tipo = r.path("tipo").asText();
    final String texto = r.path("texto").asText().trim();
    if (!Set.of("RESPUESTA", "ACLARACION", "PROPUESTA").contains(tipo)
        || texto.isEmpty()
        || texto.length() > 24000)
      throw new ApiException(422, "Respuesta del agente incompleta o fuera de contrato.");
    ArrayNode fuentes = json.createArrayNode(), supuestos = lista(r.path("supuestos"));
    if (consulta && "PROPUESTA".equals(tipo)) {
      // Un mentor puede explicar una solución sin que eso modifique el modelo: la
      // explicación se
      // conserva como respuesta, se descartan las acciones y se avisa al usuario.
      tipo = "RESPUESTA";
      if (supuestos.size() < 20)
        supuestos.add(
            "Esta petición era una consulta: no se generó una propuesta aplicable. Si quieres"
                + " llevarlo al modelo, pídelo explícitamente (por ejemplo «Agrega…» o «Aplica"
                + " este diseño»).");
    }
    Set<String> permitidas = new HashSet<>();
    for (String grupo : List.of("mensajes", "acuerdos", "antecedentes"))
      for (JsonNode f : datos.path(grupo))
        permitidas.add(f.path("id").asText());
    for (JsonNode f : r.path("fuentes"))
      if (f.isTextual() && permitidas.contains(f.asText()))
        fuentes.add(f.asText());
    Interpretacion propuesta = null;
    if ("PROPUESTA".equals(tipo)) {
      if (r.path("operaciones").isArray() && !r.path("operaciones").isEmpty())
        propuesta = operaciones(r.path("operaciones"), contexto, texto);
      else if (r.path("diagrama").isObject())
        propuesta = vision.interpretarEstructuraVisual(r.path("diagrama").toString(), contexto);
      else if (r.hasNonNull("instruccion"))
        propuesta = motor.interpretarLocal(r.path("instruccion").asText(), contexto);
      else
        throw new ApiException(422, "La propuesta no contiene acciones revisables.");
    }
    return new AgenteColaborativoServicio.Respuesta(
        tipo, texto, propuesta, fuentes, supuestos, "REMOTO");
  }

  private ArrayNode lista(JsonNode datos) {
    ArrayNode valores = json.createArrayNode();
    if (!datos.isMissingNode() && !datos.isArray())
      throw new ApiException(422, "Los supuestos deben ser una lista.");
    if (datos.size() > 20)
      throw new ApiException(422, "Demasiados supuestos en la respuesta.");
    for (JsonNode d : datos) {
      if (!d.isTextual() || d.asText().length() > 2000)
        throw new ApiException(422, "Supuesto inválido.");
      valores.add(d.asText());
    }
    return valores;
  }

  public Interpretacion operaciones(JsonNode entradas, Contexto contexto, String resumen) {
    if (!entradas.isArray() || entradas.isEmpty() || entradas.size() > 100)
      throw new ApiException(422, "La propuesta admite entre 1 y 100 acciones.");
    Map<UUID, ElementoContexto> existentes = new HashMap<>();
    contexto.elementos().forEach(e -> existentes.put(e.id(), e));
    Map<String, UUID> referencias = new LinkedHashMap<>(), operaciones = new HashMap<>();
    Set<UUID> destinos = new HashSet<>();
    for (JsonNode e : entradas) {
      String ref = e.path("referencia").asText();
      if (ref.isBlank() || ref.length() > 100 || referencias.containsKey(ref))
        throw new ApiException(422, "Cada acción necesita una referencia única.");
      UUID id;
      if ("CREAR".equals(e.path("operacion").asText()))
        id = UUID.randomUUID();
      else {
        try {
          id = UUID.fromString(ref);
        } catch (Exception ex) {
          throw new ApiException(422, "Actualizar o eliminar requiere un UUID existente.");
        }
        var actual = existentes.get(id);
        if (actual == null || !actual.tipo().equals(e.path("entidad").asText()))
          throw new ApiException(422, "La referencia no pertenece al modelo o al tipo solicitado.");
      }
      if (!destinos.add(id))
        throw new ApiException(422, "Un elemento solo puede tener una acción por propuesta.");
      referencias.put(ref, id);
      operaciones.put(ref, UUID.randomUUID());
    }
    List<OperacionGenerada> resultado = new ArrayList<>();
    for (JsonNode e : entradas) {
      String ref = e.path("referencia").asText(),
          op = e.path("operacion").asText(),
          ent = e.path("entidad").asText();
      if (!Set.of("CREAR", "ACTUALIZAR", "ELIMINAR").contains(op)
          || !Set.of("CLASE", "ATRIBUTO", "OPERACION", "RELACION", "PAQUETE").contains(ent))
        throw new ApiException(422, "Acción no soportada en el subconjunto UML.");
      JsonNode previo = existentes.containsKey(referencias.get(ref))
          ? existentes.get(referencias.get(ref)).valor()
          : null;
      JsonNode valor = null;
      Set<UUID> deps = new LinkedHashSet<>();
      if (!"ELIMINAR".equals(op)) {
        if (!e.path("datos").isObject())
          throw new ApiException(422, "La acción necesita datos estructurados.");
        ObjectNode objeto = previo != null ? previo.deepCopy() : json.createObjectNode();
        objeto.setAll((ObjectNode) e.path("datos").deepCopy());
        for (String campo : List.of("idClase", "idPaquete", "padre", "origen", "destino")) {
          String alias = objeto.path(campo).asText();
          if (referencias.containsKey(alias)) {
            objeto.put(campo, referencias.get(alias).toString());
            if (!existentes.containsKey(referencias.get(alias)))
              deps.add(operaciones.get(alias));
          }
        }
        if ("CREAR".equals(op) && "CLASE".equals(ent)) {
          if (!objeto.has("x"))
            objeto.put("x", 100 + resultado.size() % 4 * 300);
          if (!objeto.has("y"))
            objeto.put("y", 100 + resultado.size() / 4 * 250);
          if (!objeto.has("esPersistente"))
            objeto.put("esPersistente", false);
        }
        if ("OPERACION".equals(ent)) {
          if (!objeto.has("parametros"))
            objeto.putArray("parametros");
          Set<String> idsPrevios = new HashSet<>();
          if (previo != null)
            for (JsonNode p : previo.path("parametros"))
              idsPrevios.add(p.path("id").asText());
          for (JsonNode p : objeto.path("parametros")) {
            if (!p.isObject())
              throw new ApiException(422, "Parámetro no estructurado.");
            if (!idsPrevios.contains(p.path("id").asText()))
              ((ObjectNode) p).put("id", UUID.randomUUID().toString());
          }
        }
        valor = objeto;
      }
      resultado.add(
          new OperacionGenerada(
              operaciones.get(ref),
              resultado.size() + 1,
              op,
              ent,
              referencias.get(ref),
              previo,
              valor,
              new BigDecimal("0.9000"),
              true,
              "VALIDO",
              json.createArrayNode(),
              "Propuesta del asistente; requiere validación y revisión humana.",
              List.copyOf(deps)));
    }
    // Ordenar dependencias sin aceptar ciclos ni referencias a acciones no
    // incluidas.
    List<OperacionGenerada> ordenadas = new ArrayList<>();
    Set<UUID> hechas = new HashSet<>();
    while (ordenadas.size() < resultado.size()) {
      boolean progreso = false;
      for (var o : resultado)
        if (!hechas.contains(o.id()) && hechas.containsAll(o.dependencias())) {
          ordenadas.add(
              new OperacionGenerada(
                  o.id(),
                  ordenadas.size() + 1,
                  o.tipoOperacion(),
                  o.tipoEntidad(),
                  o.idEntidad(),
                  o.valorActual(),
                  o.valorPropuesto(),
                  o.confianza(),
                  o.seleccionada(),
                  o.estado(),
                  o.mensajes(),
                  o.justificacion(),
                  o.dependencias()));
          hechas.add(o.id());
          progreso = true;
        }
      if (!progreso)
        throw new ApiException(422, "La propuesta tiene dependencias cíclicas.");
    }
    return new Interpretacion(resumen, new BigDecimal("0.9000"), ordenadas);
  }
}
