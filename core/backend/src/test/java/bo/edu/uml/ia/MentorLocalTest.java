package bo.edu.uml.ia;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.ia.servicio.*;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MentorLocalTest {
  final ObjectMapper json = new ObjectMapper();

  UUID clase(List<ElementoContexto> l, String nombre, boolean persistente) {
    UUID id = UUID.randomUUID();
    l.add(
        new ElementoContexto(
            id,
            "CLASE",
            nombre,
            null,
            json.createObjectNode().put("nombre", nombre).put("esPersistente", persistente)));
    return id;
  }

  void atributo(List<ElementoContexto> l, UUID clase, String nombre, String tipo, boolean id) {
    l.add(
        new ElementoContexto(
            UUID.randomUUID(),
            "ATRIBUTO",
            nombre,
            clase,
            json.createObjectNode()
                .put("nombre", nombre)
                .put("tipoUml", tipo)
                .put("esIdentificador", id)));
  }

  void relacion(
      List<ElementoContexto> l, UUID o, UUID d, String tipo, Integer minO, Integer maxO, Integer minD, Integer maxD) {
    var v = json.createObjectNode().put("origen", o.toString()).put("destino", d.toString()).put("tipo", tipo);
    v.put("minimoOrigen", minO);
    if (maxO == null) v.putNull("maximoOrigen"); else v.put("maximoOrigen", maxO);
    v.put("minimoDestino", minD);
    if (maxD == null) v.putNull("maximoDestino"); else v.put("maximoDestino", maxD);
    l.add(new ElementoContexto(UUID.randomUUID(), "RELACION", null, null, v));
  }

  @Test
  void detectaHallazgosVerificablesDelModelo() {
    var l = new ArrayList<ElementoContexto>();
    UUID producto = clase(l, "Producto", true);
    UUID almacen = clase(l, "Almacen", false);
    UUID suelta = clase(l, "Etiqueta", false);
    atributo(l, producto, "precio", "Double", false);
    relacion(l, producto, almacen, "ASOCIACION", 0, null, 0, null);
    relacion(l, producto, almacen, "COMPOSICION", 0, null, 1, 1);
    var a = AnalizadorModelo.analizar(l);
    var codigos = a.hallazgos().stream().map(AnalizadorModelo.Hallazgo::codigo).toList();
    assertThat(codigos)
        .contains(
            "CLASE_AISLADA",
            "CLASE_SIN_ATRIBUTOS",
            "PERSISTENTE_SIN_IDENTIFICADOR",
            "DINERO_PUNTO_FLOTANTE",
            "MUCHOS_A_MUCHOS",
            "COMPOSICION_MAXIMO_ORIGEN");
    assertThat(a.hallazgos())
        .anyMatch(h -> "CLASE_AISLADA".equals(h.codigo()) && "Etiqueta".equals(h.clase()));
    assertThat(suelta).isNotNull();
  }

  @Test
  void enfoqueEncuentraClasesMencionadasConPluralYSinAcentos() {
    var l = new ArrayList<ElementoContexto>();
    clase(l, "Pedido", false);
    clase(l, "Reserva", false);
    clase(l, "Cliente", false);
    var a = AnalizadorModelo.analizar(l);
    var foco = AnalizadorModelo.enfoque(a, "¿Cómo relaciono los pedidos con la RESERVA?");
    assertThat(foco).extracting(AnalizadorModelo.ClaseInfo::nombre).containsExactly("Pedido", "Reserva");
  }

  @Test
  void resumenJsonUsaNombresYMultiplicidades() {
    var l = new ArrayList<ElementoContexto>();
    UUID c = clase(l, "Cliente", false);
    UUID p = clase(l, "Pedido", false);
    atributo(l, c, "nombre", "String", false);
    relacion(l, c, p, "ASOCIACION", 1, 1, 0, null);
    var resumen = AnalizadorModelo.aJson(json, AnalizadorModelo.analizar(l), "Pedido");
    assertThat(resumen.path("relaciones").get(0).asText()).isEqualTo("Cliente [1] ASOCIACION [0..*] Pedido");
    assertThat(resumen.path("enfoque").get(0).path("vecinos").get(0).asText()).isEqualTo("Cliente");
    assertThat(resumen.path("clases").get(0).path("atributos").get(0).asText()).isEqualTo("nombre: String");
  }

  @Test
  void sinModeloNiProveedorResponde_ComoDesarrollarInventarios() {
    var a = AnalizadorModelo.analizar(List.of());
    String r = MentorLocal.responder("¿Cómo desarrollar el módulo de inventarios?", a, 0, json.createObjectNode());
    assertThat(r).contains("Guía de referencia: Inventarios");
    assertThat(r).contains("MovimientoStock").contains("Almacen").contains("Decisiones");
    assertThat(r).contains("El modelo está vacío");
    assertThat(r).doesNotContain("Guía de referencia: Ventas");
  }

  @Test
  void guiaMarcaLoQueYaExisteYLoQueFalta() {
    var l = new ArrayList<ElementoContexto>();
    UUID p = clase(l, "Producto", false);
    atributo(l, p, "nombre", "String", false);
    var a = AnalizadorModelo.analizar(l);
    String r = MentorLocal.responder("Cómo desarrollo el inventario", a, 3, json.createObjectNode());
    assertThat(r).contains("✅ «Producto»").contains("⬜ pendiente").contains("revisión 3");
  }

  @Test
  void relacionEntreDosClasesSinVinculoExplicaComoDecidir() {
    var l = new ArrayList<ElementoContexto>();
    UUID c = clase(l, "Cliente", false);
    UUID r = clase(l, "Reserva", false);
    atributo(l, c, "nombre", "String", false);
    atributo(l, r, "fecha", "LocalDate", false);
    String t = MentorLocal.responder("¿Cómo proceder con la relación entre Cliente y Reserva?",
        AnalizadorModelo.analizar(l), 1, json.createObjectNode());
    assertThat(t).contains("No hay relación directa").contains("Crea relación entre Cliente y Reserva");
  }

  @Test
  void incorporaAcuerdosYOpinionesDeOtrosIntegrantes() throws Exception {
    var datos =
        json.readTree(
            """
            {"solicitante":"Luis",
             "acuerdos":[{"id":"a1","nombreAutor":"Ana","contenido":"Las reservas no exigen anticipo"}],
             "mensajes":[
               {"emisor":"USUARIO","nombreAutor":"Ana","texto":"Propongo pagos parciales"},
               {"emisor":"USUARIO","nombreAutor":"Luis","texto":"mi propia pregunta"},
               {"emisor":"ASISTENTE","nombreAutor":"Asistente UML","texto":"respuesta previa"}],
             "antecedentes":[{"id":"m9","texto":"Carlos dijo que hay varias mesas","nombre_autor":"Carlos","emisor":"USUARIO"}]}
            """);
    String t = MentorLocal.responder("¿Qué añado a las reservas?", AnalizadorModelo.analizar(List.of()), 2, datos);
    assertThat(t)
        .contains("Acuerdo confirmado por Ana")
        .contains("Ana comentó: «Propongo pagos parciales»")
        .contains("Carlos comentó")
        .contains("no un acuerdo confirmado")
        .doesNotContain("Luis comentó")
        .doesNotContain("respuesta previa");
  }

  @Test
  void sintetizaDiagramaParaCondominioConReservasPagosEInvitados() throws Exception {
    var a = AnalizadorModelo.analizar(List.of());
    var generado =
        MentorLocal.sintetizarDiagramaJson(
            json,
            "crea un diagrama de clase para un condominio que tenga el módulo de reserva de área comunes pago e invitados",
            a);
    assertThat(generado).isPresent();
    JsonNode nodo = json.readTree(generado.get());
    assertThat(nodo.path("clases"))
        .extracting(c -> c.path("nombre").asText())
        .contains(
            "Condominio",
            "UnidadHabitacional",
            "Residente",
            "AreaComun",
            "ReservaAreaComun",
            "PagoServicio",
            "Invitado");
    assertThat(nodo.path("relaciones")).isNotEmpty();
  }

  @Test
  void modeloSimilarAUnDominioInfiereLaGuiaCuandoPreguntanQueFalta() {
    var l = new ArrayList<ElementoContexto>();
    UUID p = clase(l, "Producto", false);
    UUID a = clase(l, "Almacen", false);
    atributo(l, p, "nombre", "String", false);
    atributo(l, a, "nombre", "String", false);
    String t = MentorLocal.responder("¿Qué le falta a este diagrama para ser adecuado?",
        AnalizadorModelo.analizar(l), 5, json.createObjectNode());
    assertThat(t).contains("Guía de referencia: Inventarios").contains("se parecen a este dominio");
  }

  @Test
  void palabrasCortasNoDisparanGuiasPorSubcadena() {
    assertThat(MentorLocal.reconoceTema("agrega control de inventario")).isTrue();
    String t = MentorLocal.responder("¿Qué opinas del inventario?", AnalizadorModelo.analizar(List.of()), 0, json.createObjectNode());
    assertThat(t).contains("Inventarios").doesNotContain("Guía de referencia: Ventas");
  }
}

