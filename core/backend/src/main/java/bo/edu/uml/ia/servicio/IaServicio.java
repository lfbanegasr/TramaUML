package bo.edu.uml.ia.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.dto.IaDto.*;
import bo.edu.uml.ia.repositorio.IaRepositorio;
import bo.edu.uml.ia.repositorio.IaRepositorio.CabeceraModelo;
import bo.edu.uml.ia.repositorio.IaRepositorio.PropuestaBloqueada;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Interpretacion;
import bo.edu.uml.modelado.dto.ModeladoDto.Cambio;
import bo.edu.uml.modelado.dto.ModeladoDto.Confirmacion;
import bo.edu.uml.modelado.modelo.ReglasElemento;
import bo.edu.uml.modelado.servicio.EdicionTransaccional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IaServicio {
  private static final Logger LOG = LoggerFactory.getLogger(IaServicio.class);
  private static final long MAX_AUDIO = 15L * 1024 * 1024;
  private static final long MAX_IMAGEN = 16L * 1024 * 1024;
  private static final int MAX_DURACION_AUDIO_SEGUNDOS = 120;

  private final IaRepositorio repo;
  private final MotorAsistenciaInteligente motor;
  private final MotorVisionDiagrama vision;
  private final ProveedorIa proveedor;
  private final EdicionTransaccional edicion;
  private final ObjectMapper json;
  private final SimuladorPropuesta simulador;
  private final TransactionTemplate tx;

  public IaServicio(
      IaRepositorio repo,
      MotorAsistenciaInteligente motor,
      MotorVisionDiagrama vision,
      ProveedorIa proveedor,
      EdicionTransaccional edicion,
      ObjectMapper json,
      SimuladorPropuesta simulador,
      PlatformTransactionManager tm) {
    this.repo = repo;
    this.motor = motor;
    this.vision = vision;
    this.proveedor = proveedor;
    this.edicion = edicion;
    this.json = json;
    this.simulador = simulador;
    this.tx = new TransactionTemplate(tm);
  }

  public ResultadoSolicitud solicitarTexto(Identidad actor, UUID modelo, SolicitudTexto solicitud) {
    Preparada p = preparar(actor, modelo, "TEXTO", solicitud.prompt().trim(), null, null);
    return procesar(
        actor,
        p,
        solicitud.prompt().trim(),
        () -> procesarSolicitudTexto(solicitud.prompt().trim(), p.contexto()));
  }

  private Interpretacion procesarSolicitudTexto(String prompt, Contexto contexto) {
    if (vision.esJsonDiagrama(prompt)) {
      return vision.interpretarEstructuraVisual(prompt, contexto);
    }
    Optional<String> normalizada = proveedor.normalizar(prompt);
    if (normalizada.isPresent() && !normalizada.get().isBlank()) {
      String textoNormalizado = normalizada.get();
      if (vision.esJsonDiagrama(textoNormalizado)) {
        try {
          return vision.interpretarEstructuraVisual(textoNormalizado, contexto);
        } catch (Exception e) {
          LOG.warn("Fallo al interpretar estructura JSON de IA: {}", e.getMessage());
        }
      }
      try {
        return motor.interpretarLocal(textoNormalizado, contexto);
      } catch (Exception e) {
        LOG.warn(
            "Fallo con instrucción normalizada por IA, recurriendo a original: {}", e.getMessage());
      }
    }
    return motor.interpretarLocal(prompt, contexto);
  }

  public ResultadoSolicitud solicitarVoz(
      Identidad actor,
      UUID modelo,
      MultipartFile archivo,
      String textoTranscrito,
      Integer duracionSegundos) {
    autorizar(actor, modelo, "SOLICITAR_IA");
    if (duracionSegundos != null
        && (duracionSegundos < 1 || duracionSegundos > MAX_DURACION_AUDIO_SEGUNDOS))
      throw new ApiException(422, "La grabación debe durar entre 1 y 120 segundos.");
    byte[] contenido = leerAudio(archivo);
    Preparada p = preparar(actor, modelo, "VOZ", null, archivo, contenido);
    String texto = textoTranscrito;
    if (texto == null || texto.isBlank())
      texto =
          proveedor
              .transcribir(
                  contenido,
                  tipoAudio(archivo.getContentType()),
                  nombreSeguro(archivo.getOriginalFilename(), "voz.webm"))
              .orElse(null);
    if (texto == null || texto.isBlank()) {
      String mensaje =
          "El audio se conservó, pero no hay un proveedor de transcripción disponible.";
      tx.executeWithoutResult(s -> repo.fallarSolicitud(p.solicitud(), mensaje));
      return fallida(p.solicitud(), p.cabecera().revision(), mensaje);
    }
    texto = texto.trim();
    String textoProcesado = texto;
    return procesar(
        actor, p, textoProcesado, () -> procesarSolicitudTexto(textoProcesado, p.contexto()));
  }

  public ResultadoSolicitud solicitarImagen(
      Identidad actor, UUID modelo, MultipartFile archivo, String textoDetectado) {
    autorizar(actor, modelo, "SOLICITAR_IA");
    byte[] contenido = leerImagen(archivo);
    Preparada p = preparar(actor, modelo, "IMAGEN", null, archivo, contenido);
    return procesar(
        actor,
        p,
        textoDetectado == null ? null : textoDetectado.trim(),
        () ->
            vision.interpretar(contenido, archivo.getContentType(), textoDetectado, p.contexto()));
  }

  private record Preparada(UUID solicitud, CabeceraModelo cabecera, Contexto contexto) {}

  private Preparada preparar(
      Identidad actor,
      UUID modelo,
      String tipo,
      String texto,
      MultipartFile archivo,
      byte[] contenido) {
    return tx.execute(
        s -> {
          var c = autorizar(actor, modelo, "SOLICITAR_IA");
          UUID adjunto = null;
          if (archivo != null)
            adjunto =
                repo.guardarArchivo(
                    c.proyecto(),
                    actor.id(),
                    "VOZ".equals(tipo) ? "AUDIO_VOZ" : "IMAGEN_DIAGRAMA",
                    nombreSeguro(archivo.getOriginalFilename(), "adjunto"),
                    "VOZ".equals(tipo)
                        ? tipoAudio(archivo.getContentType())
                        : archivo.getContentType(),
                    contenido);
          UUID id = repo.crearSolicitud(modelo, c.revision(), actor.id(), tipo, texto, adjunto);
          return new Preparada(id, c, contexto(modelo));
        });
  }

  @Transactional(readOnly = true)
  public Optional<Propuesta> vigente(Identidad actor, UUID modelo) {
    autorizar(actor, modelo, "REVISAR_PROPUESTA_IA");
    return repo.propuestaVigente(modelo).map(id -> repo.detalle(modelo, id));
  }

  @Transactional(readOnly = true)
  public Propuesta detalle(Identidad actor, UUID modelo, UUID propuesta) {
    autorizar(actor, modelo, "REVISAR_PROPUESTA_IA");
    return repo.detalle(modelo, propuesta);
  }

  @Transactional
  public Propuesta revisar(
      Identidad actor, UUID modelo, UUID propuesta, RevisionPropuesta solicitud) {
    PropuestaBloqueada fila = repo.bloquear(modelo, propuesta);
    autorizar(actor, modelo, "APLICAR_PROPUESTA_IA");
    if (!"LISTO".equals(fila.estado()))
      throw new ApiException(409, "La propuesta ya fue procesada o no está disponible.");
    if (fila.revisionBase() != fila.revisionActual()) {
      repo.desactualizar(propuesta);
      return repo.detalle(modelo, propuesta);
    }
    Propuesta actual = repo.detalle(modelo, propuesta);
    Map<UUID, Operacion> existentes = new LinkedHashMap<>();
    actual.operaciones().forEach(o -> existentes.put(o.id(), o));
    Map<UUID, AjusteOperacion> ajustes = new LinkedHashMap<>();
    for (AjusteOperacion ajuste : solicitud.operaciones()) {
      if (ajustes.put(ajuste.id(), ajuste) != null)
        throw new ApiException(400, "Una operación no puede enviarse dos veces.");
    }
    if (!ajustes.keySet().equals(existentes.keySet()))
      throw new ApiException(400, "La revisión debe incluir exactamente sus operaciones.");

    List<ElementoContexto> elementos = repo.elementosContexto(modelo);
    Set<UUID> clases = new HashSet<>();
    elementos.stream()
        .filter(e -> "CLASE".equals(e.tipo()))
        .map(ElementoContexto::id)
        .forEach(clases::add);
    for (Operacion operacion : actual.operaciones()) {
      AjusteOperacion ajuste = ajustes.get(operacion.id());
      if (ajuste.seleccionada()
          && "CREAR".equals(operacion.tipoOperacion())
          && "CLASE".equals(operacion.tipoEntidad())
          && nombreValido(ajuste.valorPropuesto())) clases.add(operacion.idEntidadDestino());
    }

    Set<UUID> idsExistentes = new HashSet<>();
    elementos.stream().map(ElementoContexto::id).forEach(idsExistentes::add);
    for (Operacion operacion : actual.operaciones()) {
      AjusteOperacion ajuste = ajustes.get(operacion.id());
      String error = validarAjuste(operacion, ajuste, ajustes, idsExistentes, clases);
      ArrayNode mensajes = json.createArrayNode();
      if (operacion.justificacion() != null)
        mensajes.addObject().put("tipo", "JUSTIFICACION").put("mensaje", operacion.justificacion());
      if (error != null) mensajes.addObject().put("tipo", "ERROR").put("mensaje", error);
      repo.actualizarOperacion(
          propuesta,
          operacion.id(),
          ajuste.seleccionada(),
          ajuste.valorPropuesto(),
          error == null ? "VALIDO" : "INVALIDO",
          mensajes);
    }
    repo.registrarRevision(propuesta, actor.id(), solicitud.revision());
    return repo.detalle(modelo, propuesta);
  }

  @Transactional
  public ResultadoAplicacion aplicar(Identidad actor, UUID modelo, UUID propuesta) {
    PropuestaBloqueada fila = repo.bloquear(modelo, propuesta);
    autorizar(actor, modelo, "APLICAR_PROPUESTA_IA");
    if (!"LISTO".equals(fila.estado()))
      throw new ApiException(409, "La propuesta ya fue procesada o no está disponible.");
    if (fila.revisionBase() != fila.revisionActual()) {
      repo.desactualizar(propuesta);
      return new ResultadoAplicacion(
          propuesta,
          "DESACTUALIZADO",
          false,
          fila.revisionActual(),
          null,
          "El modelo cambió; genera y revisa una propuesta nueva.");
    }
    if (repo.dependenciasInvalidas(propuesta))
      throw new ApiException(422, "La selección omite una operación requerida.");
    if (repo.operacionesSeleccionadasInvalidas(propuesta))
      throw new ApiException(422, "Corrige o desmarca las operaciones inválidas.");
    var acciones = repo.accionesAplicables(propuesta);
    if (acciones.isEmpty())
      throw new ApiException(422, "La propuesta no contiene operaciones válidas seleccionadas.");
    // El ensayo anidado se revierte a un savepoint; no confirma cambios ni publica eventos.
    var seleccion =
        repo.detalle(modelo, propuesta).operaciones().stream()
            .filter(Operacion::seleccionada)
            .map(
                o ->
                    new MotorAsistenciaInteligente.OperacionGenerada(
                        o.id(),
                        o.numeroSecuencia(),
                        o.tipoOperacion(),
                        o.tipoEntidad(),
                        o.idEntidadDestino(),
                        o.valorActual(),
                        o.valorPropuesto(),
                        o.confianza(),
                        true,
                        o.estado(),
                        o.mensajesValidacion(),
                        o.justificacion(),
                        o.dependencias()))
            .toList();
    var informe = simulador.validar(actor, modelo, fila.revisionBase(), seleccion);
    if (!informe.nuevos().isEmpty())
      throw new ApiException(
          422,
          "La selección introduce un error semántico: " + informe.nuevos().getFirst().mensaje());
    UUID sesion =
        repo.sesionEditable(modelo, actor.id())
            .orElseThrow(
                () ->
                    new ApiException(
                        409,
                        "Conecta una sesión colaborativa editable antes de"
                            + " aplicar la propuesta."));
    UUID cambio = UUID.randomUUID();
    Cambio solicitud = new Cambio(sesion, cambio, fila.revisionBase(), List.copyOf(acciones));
    Confirmacion confirmacion =
        edicion.aplicarPropuestaIa(actor, modelo, solicitud, serializar(acciones), propuesta);
    repo.marcarAplicada(propuesta, actor.id(), confirmacion.idVersion());
    return new ResultadoAplicacion(
        propuesta,
        "APLICADO",
        true,
        confirmacion.revision(),
        confirmacion.idVersion(),
        "La propuesta se aplicó como una única versión del modelo.");
  }

  @Transactional
  public ResultadoRechazo rechazar(
      Identidad actor, UUID modelo, UUID propuesta, Rechazo solicitud) {
    PropuestaBloqueada fila = repo.bloquear(modelo, propuesta);
    autorizar(actor, modelo, "APLICAR_PROPUESTA_IA");
    if (!"LISTO".equals(fila.estado()))
      throw new ApiException(409, "La propuesta ya fue procesada o no está disponible.");
    String motivo =
        solicitud == null || solicitud.motivo() == null || solicitud.motivo().isBlank()
            ? null
            : solicitud.motivo().trim();
    repo.rechazar(propuesta, actor.id(), motivo);
    return new ResultadoRechazo(propuesta, "RECHAZADO", motivo);
  }

  private ResultadoSolicitud procesar(
      Identidad actor, Preparada preparada, String textoProcesado, Interpretador interpretador) {
    UUID solicitud = preparada.solicitud();
    CabeceraModelo cabecera = preparada.cabecera();
    tx.executeWithoutResult(s -> repo.procesando(solicitud));
    try {
      Interpretacion interpretacion = interpretador.interpretar();
      return tx.execute(
          s -> {
            var actual = autorizar(actor, cabecera.id(), "SOLICITAR_IA");
            if (actual.revision() != cabecera.revision())
              throw new ApiException(
                  409, "El modelo cambió durante la inferencia. Solicita una propuesta nueva.");
            UUID propuesta =
                repo.guardarPropuesta(
                    solicitud,
                    interpretacion.resumen(),
                    interpretacion.confianzaGeneral(),
                    interpretacion.operaciones());
            repo.completarSolicitud(solicitud, textoProcesado);
            return new ResultadoSolicitud(
                solicitud,
                propuesta,
                "COMPLETADO",
                interpretacion.resumen(),
                interpretacion.confianzaGeneral(),
                cabecera.revision(),
                "Propuesta lista para revisión.");
          });
    } catch (ApiException e) {
      tx.executeWithoutResult(s -> repo.fallarSolicitud(solicitud, e.getMessage()));
      return fallida(solicitud, cabecera.revision(), e.getMessage());
    }
  }

  private Contexto contexto(UUID modelo) {
    return new Contexto(
        repo.cantidadClases(modelo), repo.nombresClases(modelo), repo.elementosContexto(modelo));
  }

  private String validarAjuste(
      Operacion operacion,
      AjusteOperacion ajuste,
      Map<UUID, AjusteOperacion> ajustes,
      Set<UUID> idsExistentes,
      Set<UUID> clases) {
    if (ajuste.seleccionada()) {
      for (UUID requerida : operacion.dependencias()) {
        AjusteOperacion dependencia = ajustes.get(requerida);
        if (dependencia == null || !dependencia.seleccionada())
          return "Esta operación requiere otra operación que fue desmarcada.";
      }
    }
    if (!"CREAR".equals(operacion.tipoOperacion())
        && !idsExistentes.contains(operacion.idEntidadDestino()))
      return "El elemento de destino ya no existe en el modelo.";
    JsonNode valor = ajuste.valorPropuesto();
    if ("ELIMINAR".equals(operacion.tipoOperacion()))
      return valor == null || valor.isNull() || (valor.isObject() && valor.isEmpty())
          ? null
          : "Una eliminación no acepta datos propuestos.";
    if (valor == null || !valor.isObject()) return "Los datos propuestos deben ser un objeto JSON.";
    String errorEstructura = validarEstructura(operacion, valor);
    if (errorEstructura != null) return errorEstructura;
    return switch (operacion.tipoEntidad()) {
      case "CLASE", "PAQUETE" ->
          nombreValido(valor) ? null : "El nombre es obligatorio y admite hasta 180 caracteres.";
      case "ATRIBUTO" ->
          referenciaClaseValida(valor, clases)
                  && nombreValido(valor)
                  && textoValido(valor, "tipoUml", 180)
              ? null
              : "El atributo requiere clase, nombre y tipo UML válidos.";
      case "OPERACION" -> {
        JsonNode parametros = valor.path("parametros");
        yield referenciaClaseValida(valor, clases)
                && nombreValido(valor)
                && parametros.isArray()
                && parametros.size() <= 100
            ? null
            : "La operación requiere clase, nombre y una lista válida de parámetros.";
      }
      case "RELACION" -> {
        UUID origen = uuid(valor, "origen"), destino = uuid(valor, "destino");
        yield origen != null
                && destino != null
                && clases.contains(origen)
                && clases.contains(destino)
            ? null
            : "Los extremos de la relación deben ser clases de este modelo.";
      }
      default -> "El tipo de elemento no está permitido.";
    };
  }

  private String validarEstructura(Operacion operacion, JsonNode valor) {
    try {
      switch (operacion.tipoEntidad()) {
        case "CLASE" -> {
          ReglasElemento.objeto(
              valor,
              Set.of(
                  "nombre",
                  "descripcion",
                  "estereotipo",
                  "visibilidad",
                  "esAbstracto",
                  "esPersistente",
                  "x",
                  "y",
                  "idPaquete",
                  "ancho",
                  "alto"));
          ReglasElemento.texto(valor, "nombre", 180);
          ReglasElemento.opcional(valor, "descripcion", 4000);
          ReglasElemento.opcional(valor, "estereotipo", 100);
          ReglasElemento.enumerado(
              valor, "visibilidad", "PUBLICO", "PUBLICO", "PRIVADO", "PROTEGIDO", "PAQUETE");
          ReglasElemento.dimension(valor, "ancho", 240);
          ReglasElemento.dimension(valor, "alto", 160);
          ReglasElemento.bool(valor, "esAbstracto", false);
          ReglasElemento.bool(valor, "esPersistente", true);
          if ("CREAR".equals(operacion.tipoOperacion())) {
            ReglasElemento.coordenada(valor, "x");
            ReglasElemento.coordenada(valor, "y");
          }
        }
        case "ATRIBUTO" -> validarEstructuraAtributo(valor);
        case "OPERACION" -> validarEstructuraOperacion(valor);
        case "RELACION" -> validarEstructuraRelacion(valor);
        case "PAQUETE" -> {
          ReglasElemento.objeto(valor, Set.of("nombre", "descripcion", "padre"));
          ReglasElemento.texto(valor, "nombre", 180);
          ReglasElemento.opcional(valor, "descripcion", 4000);
        }
        default -> {
          return "El tipo de elemento no está permitido.";
        }
      }
      return null;
    } catch (ApiException e) {
      return e.getMessage();
    }
  }

  private void validarEstructuraAtributo(JsonNode valor) {
    ReglasElemento.objeto(
        valor,
        Set.of(
            "idClase",
            "nombre",
            "tipoUml",
            "visibilidad",
            "minimo",
            "maximo",
            "esIdentificador",
            "esEstatico",
            "esUnico",
            "esOrdenado",
            "coleccionUnica",
            "valorPredeterminado",
            "restricciones"));
    ReglasElemento.uuid(valor, "idClase");
    ReglasElemento.texto(valor, "nombre", 180);
    ReglasElemento.texto(valor, "tipoUml", 180);
    ReglasElemento.enumerado(
        valor, "visibilidad", "PRIVADO", "PUBLICO", "PRIVADO", "PROTEGIDO", "PAQUETE");
    int minimo = ReglasElemento.entero(valor, "minimo", 1);
    Integer maximo = ReglasElemento.maximo(valor, "maximo", 1);
    boolean identificador = ReglasElemento.bool(valor, "esIdentificador", false);
    boolean estatico = ReglasElemento.bool(valor, "esEstatico", false);
    ReglasElemento.bool(valor, "esUnico", false);
    ReglasElemento.bool(valor, "esOrdenado", false);
    ReglasElemento.bool(valor, "coleccionUnica", true);
    ReglasElemento.opcional(valor, "valorPredeterminado", 4000);
    JsonNode restricciones = valor.path("restricciones");
    if ((!restricciones.isMissingNode() && !restricciones.isObject())
        || restricciones.toString().length() > 8000)
      throw new ApiException(422, "Las restricciones del atributo no son válidas.");
    if (maximo != null && maximo < minimo)
      throw new ApiException(422, "La multiplicidad del atributo no es válida.");
  }

  private void validarEstructuraOperacion(JsonNode valor) {
    ReglasElemento.objeto(
        valor,
        Set.of(
            "idClase",
            "nombre",
            "tipoRetorno",
            "visibilidad",
            "esEstatico",
            "esAbstracto",
            "parametros"));
    ReglasElemento.uuid(valor, "idClase");
    ReglasElemento.texto(valor, "nombre", 180);
    ReglasElemento.opcional(valor, "tipoRetorno", 180);
    ReglasElemento.enumerado(
        valor, "visibilidad", "PUBLICO", "PUBLICO", "PRIVADO", "PROTEGIDO", "PAQUETE");
    ReglasElemento.bool(valor, "esEstatico", false);
    ReglasElemento.bool(valor, "esAbstracto", false);
    JsonNode parametros = valor.path("parametros");
    if (!parametros.isArray() || parametros.size() > 100)
      throw new ApiException(422, "La lista de parámetros no es válida.");
    String errorRetorno =
        bo.edu.uml.modelado.modelo.SemanticaUml.errorRetorno(
            valor.path("tipoRetorno").asText(null), parametros, "tipoUml");
    if (errorRetorno != null) throw new ApiException(422, errorRetorno);
    Set<UUID> ids = new HashSet<>();
    for (JsonNode parametro : parametros) {
      ReglasElemento.objeto(
          parametro,
          Set.of("id", "nombre", "tipoUml", "direccion", "esObligatorio", "valorPredeterminado"));
      if (!ids.add(ReglasElemento.uuid(parametro, "id")))
        throw new ApiException(422, "Los identificadores de parámetros se repiten.");
      ReglasElemento.texto(parametro, "nombre", 180);
      ReglasElemento.texto(parametro, "tipoUml", 180);
      ReglasElemento.enumerado(
          parametro, "direccion", "ENTRADA", "ENTRADA", "SALIDA", "ENTRADA_SALIDA", "RETORNO");
      ReglasElemento.bool(parametro, "esObligatorio", true);
      ReglasElemento.opcional(parametro, "valorPredeterminado", 4000);
    }
  }

  private void validarEstructuraRelacion(JsonNode valor) {
    ReglasElemento.objeto(
        valor,
        Set.of(
            "origen",
            "destino",
            "nombre",
            "tipo",
            "direccion",
            "rolOrigen",
            "rolDestino",
            "minimoOrigen",
            "maximoOrigen",
            "minimoDestino",
            "maximoDestino"));
    ReglasElemento.uuid(valor, "origen");
    ReglasElemento.uuid(valor, "destino");
    ReglasElemento.opcional(valor, "nombre", 180);
    String tipo =
        ReglasElemento.enumerado(
            valor,
            "tipo",
            "ASOCIACION",
            "ASOCIACION",
            "AGREGACION",
            "COMPOSICION",
            "GENERALIZACION",
            "DEPENDENCIA");
    ReglasElemento.enumerado(
        valor,
        "direccion",
        "SIN_DIRECCION",
        "SIN_DIRECCION",
        "ORIGEN_A_DESTINO",
        "DESTINO_A_ORIGEN",
        "BIDIRECCIONAL");
    ReglasElemento.opcional(valor, "rolOrigen", 180);
    ReglasElemento.opcional(valor, "rolDestino", 180);
    bo.edu.uml.modelado.modelo.SemanticaUml.direccion(
        tipo, valor.path("direccion").asText("SIN_DIRECCION"));
    if (tipo.equals("COMPOSICION")) {
      Integer maxConjunto = ReglasElemento.maximo(valor, "maximoOrigen", 1);
      if (maxConjunto == null || maxConjunto > 1)
        throw new ApiException(
            422,
            "El conjunto de una composición admite como máximo un propietario por" + " instancia.");
    }
    boolean estructural = Set.of("ASOCIACION", "AGREGACION", "COMPOSICION").contains(tipo);
    if (estructural) {
      int minimoOrigen = ReglasElemento.entero(valor, "minimoOrigen", 1);
      Integer maximoOrigen = ReglasElemento.maximo(valor, "maximoOrigen", 1);
      int minimoDestino = ReglasElemento.entero(valor, "minimoDestino", 1);
      Integer maximoDestino = ReglasElemento.maximo(valor, "maximoDestino", 1);
      if ((maximoOrigen != null && maximoOrigen < minimoOrigen)
          || (maximoDestino != null && maximoDestino < minimoDestino))
        throw new ApiException(422, "La cardinalidad de la relación no es válida.");
    } else if (valor.hasNonNull("minimoOrigen")
        || valor.hasNonNull("maximoOrigen")
        || valor.hasNonNull("minimoDestino")
        || valor.hasNonNull("maximoDestino")) {
      throw new ApiException(422, "Este tipo de relación no utiliza cardinalidades.");
    }
  }

  private boolean referenciaClaseValida(JsonNode valor, Set<UUID> clases) {
    UUID clase = uuid(valor, "idClase");
    return clase != null && clases.contains(clase);
  }

  private boolean nombreValido(JsonNode valor) {
    return textoValido(valor, "nombre", 180);
  }

  private boolean textoValido(JsonNode valor, String campo, int maximo) {
    if (valor == null || !valor.path(campo).isTextual()) return false;
    String texto = valor.path(campo).asText().trim();
    return !texto.isEmpty() && texto.length() <= maximo;
  }

  private UUID uuid(JsonNode valor, String campo) {
    try {
      return UUID.fromString(valor.path(campo).asText());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private CabeceraModelo autorizar(Identidad actor, UUID modelo, String permiso) {
    CabeceraModelo cabecera = repo.cabecera(modelo);
    if (!"ACTIVO".equals(cabecera.estadoProyecto()) || "ARCHIVADO".equals(cabecera.estadoModelo()))
      throw new ApiException(404, "Modelo no disponible.");
    if (!actor.esAdministrador() && !repo.permiso(actor.id(), cabecera.proyecto(), permiso))
      throw new ApiException(403, "No tienes permiso para utilizar esta función inteligente.");
    return cabecera;
  }

  public byte[] leerAudio(MultipartFile archivo) {
    byte[] contenido = leer(archivo, MAX_AUDIO, "audio");
    String tipo = tipoAudio(archivo.getContentType());
    if (!Set.of("audio/webm", "audio/wav", "audio/x-wav", "audio/ogg").contains(tipo))
      throw new ApiException(415, "El audio debe estar en formato WebM, WAV u OGG.");
    if (!firmaAudio(contenido, tipo))
      throw new ApiException(422, "El archivo no contiene audio del formato declarado.");
    return contenido;
  }

  private boolean firmaAudio(byte[] contenido, String tipo) {
    if (tipo.equals("audio/webm"))
      return contenido.length >= 4
          && contenido[0] == 0x1A
          && contenido[1] == 0x45
          && contenido[2] == (byte) 0xDF
          && contenido[3] == (byte) 0xA3;
    if (tipo.equals("audio/ogg"))
      return contenido.length >= 4
          && contenido[0] == 'O'
          && contenido[1] == 'g'
          && contenido[2] == 'g'
          && contenido[3] == 'S';
    return contenido.length >= 12
        && contenido[0] == 'R'
        && contenido[1] == 'I'
        && contenido[2] == 'F'
        && contenido[3] == 'F'
        && contenido[8] == 'W'
        && contenido[9] == 'A'
        && contenido[10] == 'V'
        && contenido[11] == 'E';
  }

  public byte[] leerImagen(MultipartFile archivo) {
    byte[] contenido = leer(archivo, MAX_IMAGEN, "imagen");
    if (!Set.of("image/png", "image/jpeg").contains(archivo.getContentType()))
      throw new ApiException(415, "La imagen debe estar en formato PNG o JPG.");
    try {
      BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
      if (imagen == null) throw new ApiException(422, "El archivo no contiene una imagen válida.");
      if (imagen.getWidth() > 12000 || imagen.getHeight() > 12000)
        throw new ApiException(413, "Las dimensiones de la imagen son demasiado grandes.");
    } catch (IOException e) {
      throw new ApiException(422, "No se pudo leer la imagen.");
    }
    return contenido;
  }

  private byte[] leer(MultipartFile archivo, long limite, String nombreTipo) {
    if (archivo == null || archivo.isEmpty())
      throw new ApiException(400, "Selecciona un archivo de " + nombreTipo + ".");
    if (archivo.getSize() > limite)
      throw new ApiException(413, "El archivo de " + nombreTipo + " supera el límite permitido.");
    try {
      return archivo.getBytes();
    } catch (IOException e) {
      throw new ApiException(400, "No se pudo leer el archivo de " + nombreTipo + ".");
    }
  }

  private String tipoAudio(String tipo) {
    if (tipo == null) return "application/octet-stream";
    int separador = tipo.indexOf(';');
    return (separador < 0 ? tipo : tipo.substring(0, separador)).toLowerCase(Locale.ROOT);
  }

  private String nombreSeguro(String original, String predeterminado) {
    if (original == null || original.isBlank()) return predeterminado;
    String nombre = original.replace('\\', '/');
    nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
    return nombre.isBlank() ? predeterminado : nombre.substring(0, Math.min(nombre.length(), 255));
  }

  private String serializar(Object valor) {
    try {
      return json.writeValueAsString(valor);
    } catch (JsonProcessingException e) {
      throw new ApiException(400, "La propuesta contiene datos inválidos.");
    }
  }

  private ResultadoSolicitud fallida(UUID solicitud, long revision, String mensaje) {
    return new ResultadoSolicitud(solicitud, null, "FALLIDO", null, null, revision, mensaje);
  }

  @FunctionalInterface
  private interface Interpretador {
    Interpretacion interpretar();
  }
}
