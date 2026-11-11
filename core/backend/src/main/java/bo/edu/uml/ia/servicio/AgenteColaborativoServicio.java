package bo.edu.uml.ia.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.dto.AgenteDto.*;
import bo.edu.uml.ia.dto.IaDto.*;
import bo.edu.uml.ia.repositorio.*;
import bo.edu.uml.ia.repositorio.ConversacionRepositorio.Trabajo;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AgenteColaborativoServicio {
  private final ConversacionRepositorio chat;
  private final IaRepositorio ia;
  private final AccesoRepositorio acceso;
  private final NamedParameterJdbcTemplate db;
  private final ObjectMapper json;
  private final MotorAsistenciaInteligente motor;
  private final MotorVisionDiagrama vision;
  private final SimuladorPropuesta simulador;
  private final TransactionTemplate tx;
  private final TransactionTemplate lectura;
  private final ProveedorIa proveedor;
  private final InterpreteRespuestaAgente interprete;
  private final boolean contextoRemoto;
  private final IaServicio entradas;

  public AgenteColaborativoServicio(
      ConversacionRepositorio chat,
      IaRepositorio ia,
      AccesoRepositorio acceso,
      NamedParameterJdbcTemplate db,
      ObjectMapper json,
      MotorAsistenciaInteligente motor,
      MotorVisionDiagrama vision,
      SimuladorPropuesta simulador,
      PlatformTransactionManager tm,
      ProveedorIa proveedor,
      InterpreteRespuestaAgente interprete,
      @Value("${uml.ia.contexto-remoto:false}") boolean contextoRemoto,
      IaServicio entradas) {
    this.chat = chat;
    this.ia = ia;
    this.acceso = acceso;
    this.db = db;
    this.json = json;
    this.motor = motor;
    this.vision = vision;
    this.simulador = simulador;
    tx = new TransactionTemplate(tm);
    this.proveedor = proveedor;
    this.interprete = interprete;
    this.contextoRemoto = contextoRemoto;
    this.entradas = entradas;
    lectura = new TransactionTemplate(tm);
    lectura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  public IaRepositorio.CabeceraModelo autorizar(Identidad actor, UUID modelo, String permiso) {
    var c = ia.cabecera(modelo);
    Boolean activo =
        db.queryForObject(
            "select estado='ACTIVO' from uml_colaborativo.usuario where id=:id",
            Map.of("id", actor.id()),
            Boolean.class);
    if (!Boolean.TRUE.equals(activo)
        || !"ACTIVO".equals(c.estadoProyecto())
        || "ARCHIVADO".equals(c.estadoModelo()))
      throw new ApiException(404, "Modelo no disponible.");
    if (!actor.esAdministrador() && !ia.permiso(actor.id(), c.proyecto(), permiso))
      throw new ApiException(403, "No tienes permiso para esta conversación.");
    return c;
  }

  public Historial historial(Identidad actor, UUID modelo, long cursor) {
    if (cursor < 0) throw new ApiException(400, "Cursor inválido.");
    return tx.execute(
        s -> {
          autorizar(actor, modelo, "VER_MODELO");
          UUID hilo = chat.hilo(modelo, actor.id());
          chat.bloquearHilo(hilo);
          var datos = chat.mensajes(hilo, cursor, 101);
          boolean mas = datos.size() > 100;
          var pagina = mas ? List.copyOf(datos.subList(0, 100)) : datos;
          long siguiente = pagina.isEmpty() ? cursor : pagina.getLast().secuencia();
          return new Historial(
              hilo, pagina, siguiente, mas, chat.activas(hilo), chat.acuerdos(hilo));
        });
  }

  public Map<String, Object> capacidades(Identidad actor, UUID modelo) {
    autorizar(actor, modelo, "VER_MODELO");
    var datos = new LinkedHashMap<>(proveedor.configuracionPublica());
    datos.put("contextoRemoto", contextoRemoto);
    datos.put("almacenamiento", "POSTGRESQL");
    datos.put("maxCaracteresContexto", 120000);
    datos.put("requiereRevision", true);
    return datos;
  }

  public Aceptado enviar(Identidad actor, UUID modelo, Entrada entrada) {
    return recibir(
        actor, modelo, entrada.clave(), entrada.texto(), "TEXTO", null, entrada.intencion());
  }

  public Aceptado recibir(
      Identidad actor,
      UUID modelo,
      UUID clave,
      String texto,
      String modalidad,
      UUID archivo,
      String intencion) {
    return tx.execute(
        s -> {
          autorizar(actor, modelo, "SOLICITAR_IA");
          return chat.recibir(
              chat.hilo(modelo, actor.id()),
              actor.id(),
              clave,
              texto.trim(),
              modalidad,
              archivo,
              intencion == null ? "AUTO" : intencion);
        });
  }

  public Ejecucion ejecucion(Identidad actor, UUID modelo, UUID id) {
    return tx.execute(
        s -> {
          autorizar(actor, modelo, "VER_MODELO");
          return chat.ejecucion(chat.hilo(modelo, actor.id()), id);
        });
  }

  public Aceptado adjuntar(
      Identidad actor,
      UUID modelo,
      UUID clave,
      MultipartFile archivo,
      String texto,
      String modalidad,
      String intencion) {
    autorizar(actor, modelo, "SOLICITAR_IA");
    if (texto != null && texto.length() > 12000)
      throw new ApiException(422, "La instrucción supera 12000 caracteres.");
    if (!Set.of("AUTO", "CONSULTAR", "MODIFICAR").contains(intencion))
      throw new ApiException(422, "Intención inválida.");
    if ("VOZ".equals(modalidad) && (archivo == null || archivo.isEmpty())) {
      if (texto == null || texto.isBlank())
        throw new ApiException(422, "Se requiere un audio o una transcripción de voz.");
      return recibir(actor, modelo, clave, texto, "VOZ", null, intencion);
    }
    byte[] contenido =
        "VOZ".equals(modalidad) ? entradas.leerAudio(archivo) : entradas.leerImagen(archivo);
    String peticion =
        texto == null || texto.isBlank()
            ? ("VOZ".equals(modalidad)
                ? "[Audio pendiente de transcripción]"
                : "Describe la imagen y sus incertidumbres.")
            : texto.trim();
    return tx.execute(
        s -> {
          var c = autorizar(actor, modelo, "SOLICITAR_IA");
          UUID hilo = chat.hilo(modelo, actor.id());
          chat.bloquearHilo(hilo);
          var previo =
              db.queryForList(
                  "select f.contenido from uml_colaborativo.mensaje_agente m join"
                      + " uml_colaborativo.archivo_almacenado f on f.id=m.id_archivo where"
                      + " m.id_conversacion=:h and m.autor=:a and m.clave=:k",
                  Map.of("h", hilo, "a", actor.id(), "k", clave));
          UUID idArchivo = null;
          if (!previo.isEmpty()) {
            if (!Arrays.equals(contenido, (byte[]) previo.getFirst().get("contenido")))
              throw new ApiException(409, "La clave ya corresponde a un adjunto diferente.");
          } else {
            String nombre =
                Optional.ofNullable(archivo.getOriginalFilename())
                    .orElse("adjunto")
                    .replaceAll("[^A-Za-z0-9._-]", "_");
            if (nombre.length() > 255) nombre = nombre.substring(0, 255);
            String mime = archivo.getContentType().split(";")[0];
            idArchivo =
                ia.guardarArchivo(
                    c.proyecto(),
                    actor.id(),
                    "VOZ".equals(modalidad) ? "AUDIO_VOZ" : "IMAGEN_DIAGRAMA",
                    nombre,
                    mime,
                    contenido);
          }
          return chat.recibir(hilo, actor.id(), clave, peticion, modalidad, idArchivo, intencion);
        });
  }

  public void cancelar(Identidad actor, UUID modelo, UUID id) {
    tx.executeWithoutResult(
        s -> {
          autorizar(actor, modelo, "SOLICITAR_IA");
          UUID hilo = chat.hilo(modelo, actor.id());
          var e = chat.ejecucion(hilo, id);
          UUID autor =
              db.queryForObject(
                  "select autor from uml_colaborativo.mensaje_agente where id=:id",
                  Map.of("id", e.idMensaje()),
                  UUID.class);
          if (!actor.id().equals(autor)) autorizar(actor, modelo, "APLICAR_PROPUESTA_IA");
          chat.cancelar(hilo, id);
        });
  }

  public List<Acuerdo> confirmar(Identidad actor, UUID modelo, ConfirmarAcuerdo acuerdo) {
    return tx.execute(
        s -> {
          autorizar(actor, modelo, "APLICAR_PROPUESTA_IA");
          UUID hilo = chat.hilo(modelo, actor.id());
          chat.acuerdo(hilo, actor.id(), acuerdo);
          return chat.acuerdos(hilo);
        });
  }

  public record ContextoTrabajo(
      Identidad actor, long revision, Contexto modelo, ObjectNode datos) {}

  public ContextoTrabajo contexto(Trabajo t) {
    return lectura.execute(
        s -> {
          Identidad actor = acceso.identidad(t.autor());
          var c = autorizar(actor, t.modelo(), "SOLICITAR_IA");
          List<ElementoContexto> elementos = ia.elementosContexto(t.modelo());
          if (elementos.size() > 2000)
            throw new ApiException(
                413,
                "El modelo supera el contexto de 2000 elementos. Divide el trabajo por modelos.");
          var contexto =
              new Contexto(ia.cantidadClases(t.modelo()), ia.nombresClases(t.modelo()), elementos);
          ObjectNode datos =
              json.createObjectNode()
                  .put("idModelo", t.modelo().toString())
                  .put("revision", c.revision())
                  .put("corte", t.corte())
                  .put("modalidad", t.modalidad())
                  .put("intencion", t.intencion())
                  .put("peticion", t.texto());
          datos.set("elementos", json.valueToTree(elementos));
          var recientes = chat.recientes(t.hilo(), t.corte());
          datos.set("mensajes", json.valueToTree(recientes));
          datos.set(
              "acuerdos",
              json.valueToTree(chat.acuerdos(t.hilo()).stream().filter(Acuerdo::vigente).toList()));
          // Recuperación adicional acotada por términos del mensaje y SIEMPRE dentro del hilo. Se
          // buscan los términos significativos con OR (no AND): una pregunta larga rara vez
          // contiene todas sus palabras en un mensaje anterior.
          String terminos = terminosBusqueda(t.texto());
          List<Map<String, Object>> antecedentes =
              terminos.isEmpty()
                  ? List.of()
                  : db.queryForList(
                      "select m.id,m.texto,m.autor,m.emisor,m.secuencia,"
                          + "coalesce(u.nombre_completo,'Asistente UML') nombre_autor "
                          + "from uml_colaborativo.mensaje_agente m left join"
                          + " uml_colaborativo.usuario u on u.id=m.autor where"
                          + " m.id_conversacion=:h and m.secuencia<:c and"
                          + " to_tsvector('spanish',m.texto) @@ to_tsquery('spanish',:q) order by"
                          + " ts_rank(to_tsvector('spanish',m.texto),to_tsquery('spanish',:q))"
                          + " desc, m.secuencia desc limit 8",
                      Map.of(
                          "h",
                          t.hilo(),
                          "c",
                          recientes.isEmpty() ? t.corte() : recientes.getFirst().secuencia(),
                          "q",
                          terminos));
          datos.set("antecedentes", json.valueToTree(antecedentes));
          // Vista legible por nombres, hallazgos verificables y elementos en foco de la petición.
          datos.set(
              "resumenModelo",
              AnalizadorModelo.aJson(json, AnalizadorModelo.analizar(elementos), t.texto()));
          datos.put("solicitante", nombreUsuario(t.autor()));
          ArrayNode participantes = datos.putArray("participantes");
          recientes.stream()
              .filter(m -> "USUARIO".equals(m.emisor()))
              .map(Mensaje::nombreAutor)
              .distinct()
              .forEach(participantes::add);

          ArrayNode actividadSimultanea = datos.putArray("actividadSimultanea");
          var enCurso =
              db.queryForList(
                  "select coalesce(u.nombre_completo,'Integrante') nombre_autor,m.texto,"
                      + "m.modalidad,e.intencion,e.estado from uml_colaborativo.ejecucion_agente e"
                      + " join uml_colaborativo.mensaje_agente m on m.id=e.id_mensaje left join"
                      + " uml_colaborativo.usuario u on u.id=m.autor where m.id_conversacion=:h"
                      + " and e.id<>:id and (e.estado in ('PENDIENTE','PROCESANDO') or"
                      + " m.secuencia>:c) order by m.secuencia desc limit 6",
                  Map.of("h", t.hilo(), "id", t.id(), "c", t.corte()));
          for (var fila : enCurso) {
            String autorFila = String.valueOf(fila.get("nombre_autor"));
            String estadoFila = String.valueOf(fila.get("estado"));
            String modFila = String.valueOf(fila.get("modalidad"));
            String textoFila = limpiarEtiquetasImagen(String.valueOf(fila.get("texto")));
            if (textoFila.length() > 140) textoFila = textoFila.substring(0, 140) + "…";
            actividadSimultanea
                .addObject()
                .put("nombreAutor", autorFila)
                .put("modalidad", modFila)
                .put("estado", estadoFila)
                .put(
                    "detalle",
                    "(" + modFila + " · " + estadoFila + ") «" + textoFila + "»");
          }

          var propuestas =
              db.queryForList(
                  "select p.id from uml_colaborativo.propuesta_inteligente p join"
                      + " uml_colaborativo.solicitud_inteligente s on s.id=p.id_solicitud where"
                      + " s.id_modelo=:m order by p.creado_en desc limit 3",
                  Map.of("m", t.modelo()),
                  UUID.class);
          datos.set(
              "propuestas",
              json.valueToTree(propuestas.stream().map(id -> ia.detalle(t.modelo(), id)).toList()));
          datos.put("memoriaAcotada", true);
          if (datos.toString().length() > 120000)
            throw new ApiException(
                413,
                "El contexto supera el presupuesto de 120000 caracteres. Reduce el modelo o la"
                    + " conversación.");
          chat.contexto(t, c.revision(), datos);
          return new ContextoTrabajo(actor, c.revision(), contexto, datos);
        });
  }

  public Optional<Trabajo> reclamar() {
    return tx.execute(s -> chat.reclamar());
  }

  public record Respuesta(
      String tipo,
      String texto,
      Interpretacion propuesta,
      ArrayNode fuentes,
      ArrayNode supuestos,
      String modo) {}

  public Respuesta interpretar(Trabajo t, ContextoTrabajo c) {
    // Esta función se ejecuta SIN transacción: los adaptadores pueden esperar red.
    if (t.archivo() != null) {
      var archivo =
          tx.execute(
              s -> {
                autorizar(acceso.identidad(t.autor()), t.modelo(), "SOLICITAR_IA");
                return db.queryForMap(
                    "select f.contenido,f.tipo_contenido,f.nombre_original from"
                        + " uml_colaborativo.archivo_almacenado f join"
                        + " uml_colaborativo.mensaje_agente m on m.id_archivo=f.id where m.id=:m"
                        + " and m.id_conversacion=:h",
                    Map.of("m", t.mensaje(), "h", t.hilo()));
              });
      String contenido;
      if ("VOZ".equals(t.modalidad())) {
        contenido =
            t.texto().equals("[Audio pendiente de transcripción]")
                ? proveedor
                    .transcribir(
                        (byte[]) archivo.get("contenido"),
                        (String) archivo.get("tipo_contenido"),
                        (String) archivo.get("nombre_original"))
                    .orElse(null)
                : t.texto();
      } else {
        contenido =
            proveedor
                .reconocerDiagrama(
                    (byte[]) archivo.get("contenido"),
                    (String) archivo.get("tipo_contenido"),
                    t.texto())
                .orElse(null);
        if (contenido == null || contenido.isBlank()) {
          String guiaUsuario = limpiarEtiquetasImagen(t.texto());
          if (!guiaUsuario.isBlank()
              && !"Describe la imagen y sus incertidumbres.".equals(guiaUsuario)) {
            contenido = guiaUsuario;
          }
        }
      }
      if (contenido == null || contenido.isBlank())
        return new Respuesta(
            "ACLARACION",
            "El adjunto se conservó, pero el proveedor de "
                + ("VOZ".equals(t.modalidad()) ? "transcripción" : "visión")
                + " no está disponible. Corrige o describe la entrada antes de proponer cambios.",
            null,
            json.createArrayNode(),
            json.createArrayNode(),
            "LOCAL");
      if (contenido.length() > 60000)
        throw new ApiException(422, "La extracción del adjunto supera el presupuesto permitido.");
      c.datos().put("extraccionAdjunto", contenido);
      if ("VOZ".equals(t.modalidad())) {
        c.datos().put("peticion", contenido);
        // El foco se recalcula con la transcripción real, no con el marcador de audio pendiente.
        c.datos()
            .set(
                "resumenModelo",
                AnalizadorModelo.aJson(
                    json, AnalizadorModelo.analizar(c.modelo().elementos()), contenido));
      } else if ("IMAGEN".equals(t.modalidad())) {
        String guiaUsuario = limpiarEtiquetasImagen(t.texto());
        if (!guiaUsuario.isBlank()) {
          c.datos().put("peticion", guiaUsuario);
        }
      }
    } else if ("VOZ".equals(t.modalidad())) {
      c.datos().put("extraccionAdjunto", t.texto());
    }
    String peticion = limpiarEtiquetasImagen(c.datos().path("peticion").asText());
    boolean consulta =
        "CONSULTAR".equals(t.intencion())
            || (!ORDEN_DE_CAMBIO.matcher(sinAcentos(peticion).strip()).find()
                && esConsulta(peticion));
    Trabajo entrada =
        new Trabajo(
            t.id(),
            t.token(),
            t.mensaje(),
            t.hilo(),
            t.modelo(),
            t.autor(),
            peticion.isBlank() ? t.texto() : peticion,
            t.modalidad(),
            t.archivo(),
            t.intencion(),
            t.corte());
    if (contextoRemoto) {
      var remoto = proveedor.conversar(c.datos().toString());
      if (remoto.isPresent()) {
        try {
          return interprete.interpretar(remoto.get(), c.modelo(), c.datos(), consulta);
        } catch (ApiException e) {
          // Una consulta no queda sin respuesta porque el proveedor devolvió un formato inválido:
          // el mentor local responde con el modelo y el contexto del equipo.
          if (consulta) return interpretarLocal(entrada, c, true);
          return new Respuesta(
              "ACLARACION",
              e.getMessage()
                  + " No se aplicaron cambios. Precisa la solicitud o usa un comando local.",
              null,
              json.createArrayNode(),
              json.createArrayNode(),
              "REMOTO_INVALIDO");
        }
      }
    }
    if ("IMAGEN".equals(t.modalidad()) && c.datos().has("extraccionAdjunto") && !consulta) {
      String ext = c.datos().path("extraccionAdjunto").asText();
      if (vision.esJsonDiagrama(ext)) {
        var i = vision.interpretarEstructuraVisual(ext, c.modelo());
        return new Respuesta(
            "PROPUESTA",
            i.resumen(),
            i,
            json.createArrayNode(),
            json.createArrayNode()
                .add(
                    "Extracción visual: revisa tipos, multiplicidades y partes ilegibles antes de"
                        + " aplicar."),
            "VISION");
      }
    }
    return interpretarLocal(entrada, c, consulta);
  }

  private Respuesta interpretarLocal(Trabajo t, ContextoTrabajo c, boolean consulta) {
    ArrayNode fuentes = json.createArrayNode(), supuestos = json.createArrayNode();
    if (consulta) return mentorLocal(t, c, null);
    String textoLimpio = limpiarEtiquetasImagen(t.texto());
    try {
      Interpretacion i =
          vision.esJsonDiagrama(textoLimpio)
              ? vision.interpretarEstructuraVisual(textoLimpio, c.modelo())
              : motor.interpretarLocal(textoLimpio, c.modelo());
      return new Respuesta(
          "PROPUESTA", "Modo local: " + i.resumen(), i, fuentes, supuestos, "LOCAL");
    } catch (ApiException e) {
      var analisis = AnalizadorModelo.analizar(c.modelo().elementos());
      var jsonDominio = MentorLocal.sintetizarDiagramaJson(json, textoLimpio, analisis);
      if (jsonDominio.isPresent()) {
        try {
          Interpretacion i = vision.interpretarEstructuraVisual(jsonDominio.get(), c.modelo());
          if (!i.operaciones().isEmpty()) {
            for (JsonNode acuerdo : c.datos().path("acuerdos"))
              fuentes.add(acuerdo.path("id").asText());
            supuestos.add(
                "Arquitectura de dominio generada con heurísticas de ingeniería de software;"
                    + " revisa las clases, atributos y multiplicidades antes de aplicar.");
            String explicacion =
                "**Propuesta de arquitectura generada** ("
                    + i.resumen()
                    + ").\n\n"
                    + MentorLocal.responder(textoLimpio, analisis, c.revision(), c.datos());
            return new Respuesta("PROPUESTA", explicacion, i, fuentes, supuestos, "LOCAL");
          }
        } catch (ApiException ignored) {
          // Si todas las clases ya existen o hay conflicto, responder con orientación del mentor
        }
      }
      // Una petición abierta («desarrolla el módulo de inventarios») no es un comando puntual:
      // en lugar de pedir un proveedor, el mentor local orienta con el modelo y el equipo.
      if (MentorLocal.reconoceTema(textoLimpio))
        return mentorLocal(
            t,
            c,
            "No pude convertir la petición en cambios concretos en modo local ("
                + e.getMessage()
                + "). Te dejo orientación para decidir el diseño; después pídeme cambios"
                + " puntuales y los preparo como propuesta revisable.\n\n");
      return new Respuesta(
          "ACLARACION",
          "Modo local: "
              + e.getMessage()
              + " Indica la clase y el cambio concreto, o formula una pregunta de diseño (por"
              + " ejemplo «¿qué le falta a este diagrama?») y te oriento con el modelo actual.",
          null,
          fuentes,
          supuestos,
          "LOCAL");
    }
  }

  private static String limpiarEtiquetasImagen(String texto) {
    if (texto == null) return "";
    return texto
        .replaceAll("(?m)^\\[(ORIGEN|MODO):[^\\]]*\\]\\s*", "")
        .strip();
  }

  private Respuesta mentorLocal(Trabajo t, ContextoTrabajo c, String prefijo) {
    ArrayNode fuentes = json.createArrayNode();
    for (JsonNode acuerdo : c.datos().path("acuerdos")) fuentes.add(acuerdo.path("id").asText());
    var analisis = AnalizadorModelo.analizar(c.modelo().elementos());
    String texto =
        (prefijo == null ? "" : prefijo)
            + MentorLocal.responder(t.texto(), analisis, c.revision(), c.datos());
    return new Respuesta("RESPUESTA", texto, null, fuentes, json.createArrayNode(), "LOCAL");
  }

  private static final java.util.regex.Pattern ORDEN_DE_CAMBIO =
      java.util.regex.Pattern.compile(
          "^(agrega|agregar|anade|anadir|crea|crear|elimina|eliminar|borra|borrar|modifica|cambia"
              + "|renombra|aplica|aplicala|genera|haz|hazlo|quita|actualiza|mueve|inserta|define"
              + "|relaciona)\\b");

  private static final java.util.regex.Pattern PREGUNTA_ABIERTA =
      java.util.regex.Pattern.compile(
          "^(como|que |cual|cuales|cuanto|cuantos|donde|cuando|quien|por que|para que|explica"
              + "|puedes explicar|describe|revisa|analiza|evalua|compara|dime|recuerdas|sugiere"
              + "|sugieres|recomienda|recomiendas|opinas|deberia|conviene|falta|es correcto"
              + "|esta bien|tiene sentido|me ayudas|ayudame|necesito saber|quiero saber)");

  private static final java.util.regex.Pattern PREGUNTA_EN_MEDIO =
      java.util.regex.Pattern.compile(
          "(que (le |me |nos )?falta|que mas|que (puedo |debo |deberia )?(anadir|agregar)"
              + "|como (procedo|proceder|desarrollo|desarrollar|empiezo|armo|modelo))");

  public static boolean esConsulta(String texto) {
    String p = sinAcentos(texto).strip();
    if (p.isEmpty()) return false;
    if (p.startsWith("¿") || p.startsWith("?")) return true;
    if (ORDEN_DE_CAMBIO.matcher(p).find()) return false;
    return p.contains("?")
        || PREGUNTA_ABIERTA.matcher(p).find()
        || PREGUNTA_EN_MEDIO.matcher(p).find();
  }

  /** Términos significativos para buscar antecedentes: solo [a-z0-9], unidos con OR de tsquery. */
  static String terminosBusqueda(String texto) {
    Set<String> vacias =
        Set.of(
            "como", "para", "pero", "este", "esta", "esto", "estos", "estas", "tiene", "tienen",
            "puede", "puedes", "hacer", "debe", "debo", "sobre", "entre", "desde", "hasta",
            "donde", "cuando", "porque", "quiero", "necesito", "dime", "algo", "todo", "toda",
            "nuestro", "nuestra", "cual", "cuales", "agrega", "agregar", "crea", "crear",
            "falta", "mas", "muy", "con", "los", "las", "una", "unos", "que", "del", "por");
    LinkedHashSet<String> terminos = new LinkedHashSet<>();
    for (String w : sinAcentos(texto).split("[^a-z0-9]+"))
      if (w.length() >= 4 && !vacias.contains(w)) terminos.add(w);
    return String.join(" | ", terminos.stream().limit(12).toList());
  }

  private String nombreUsuario(UUID id) {
    return db.queryForList(
            "select nombre_completo from uml_colaborativo.usuario where id=:id",
            Map.of("id", id),
            String.class)
        .stream()
        .findFirst()
        .orElse("");
  }

  private static String sinAcentos(String t) {
    return Normalizer.normalize(t, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  public void procesar(Trabajo t) {
    long inicio = System.nanoTime();
    try {
      if (!Boolean.TRUE.equals(tx.execute(s -> chat.vigente(t)))) return;
      ContextoTrabajo c = contexto(t);
      Respuesta respuesta = interpretar(t, c);
      SimuladorPropuesta.Informe informe = null;
      if (respuesta.propuesta() != null) {
        var ops = respuesta.propuesta().operaciones();
        if (ops.isEmpty() || ops.size() > 100)
          throw new ApiException(
              422,
              "La propuesta debe contener de 1 a 100 operaciones. Divide el trabajo en"
                  + " incrementos.");
        informe = simulador.validar(c.actor(), t.modelo(), c.revision(), ops);
        if (!informe.nuevos().isEmpty())
          throw new ApiException(
              422, "La propuesta introduce errores: " + informe.nuevos().getFirst().mensaje());
      }
      var validacion = informe;
      tx.executeWithoutResult(
          s -> {
            if (!chat.vigente(t)) return;
            autorizar(acceso.identidad(t.autor()), t.modelo(), "SOLICITAR_IA");
            chat.contexto(t, c.revision(), c.datos());
            UUID propuesta = null;
            if (respuesta.propuesta() != null) {
              if (ia.cabecera(t.modelo()).revision() != c.revision())
                throw new ApiException(
                    409, "El modelo cambió durante la consulta. Solicita una propuesta nueva.");
              UUID solicitud =
                  ia.crearSolicitud(
                      t.modelo(), c.revision(), t.autor(), t.modalidad(), t.texto(), t.archivo());
              propuesta =
                  ia.guardarPropuesta(
                      solicitud,
                      respuesta.propuesta().resumen(),
                      respuesta.propuesta().confianzaGeneral(),
                      respuesta.propuesta().operaciones());
              ia.completarSolicitud(solicitud, c.datos().path("peticion").asText());
            }
            ObjectNode resultado =
                json.createObjectNode()
                    .put("tipo", respuesta.tipo())
                    .put("texto", respuesta.texto())
                    .put("revisionBase", c.revision());
            if (propuesta != null) resultado.put("idPropuesta", propuesta.toString());
            if (c.datos().has("extraccionAdjunto"))
              resultado.set("extraccionAdjunto", c.datos().get("extraccionAdjunto"));
            if (validacion != null) resultado.set("validacion", json.valueToTree(validacion));
            chat.completar(
                t,
                respuesta.tipo(),
                respuesta.texto(),
                propuesta,
                respuesta.fuentes(),
                respuesta.supuestos(),
                resultado,
                respuesta.modo(),
                (System.nanoTime() - inicio) / 1000000);
          });
    } catch (Exception e) {
      if (!(e instanceof ApiException))
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn(
                "Ejecución {} falló: {} en {}; causa {}. Se omite contenido privado.",
                t.id(),
                e.getClass().getSimpleName(),
                e.getStackTrace().length == 0 ? "sin ubicación" : e.getStackTrace()[0],
                e.getCause() == null ? "ninguna" : e.getCause().getClass().getSimpleName());
      String mensaje =
          e instanceof ApiException
              ? e.getMessage()
              : "No se pudo completar la solicitud. La entrada se conserva; no se aplicaron cambios.";
      tx.executeWithoutResult(
          s -> chat.fallar(t, mensaje.length() > 1000 ? mensaje.substring(0, 1000) : mensaje));
    }
  }
}
