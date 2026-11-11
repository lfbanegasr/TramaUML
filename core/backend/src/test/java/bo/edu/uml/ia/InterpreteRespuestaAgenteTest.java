package bo.edu.uml.ia;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.*;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InterpreteRespuestaAgenteTest {
  final ObjectMapper json = new ObjectMapper();
  final MotorAsistenciaInteligente motor = new MotorAsistenciaInteligente(json);
  final MotorVisionDiagrama vision = new MotorVisionDiagrama(motor, new ProveedorIa() {}, json);
  final InterpreteRespuestaAgente interprete = new InterpreteRespuestaAgente(json, motor, vision);
  final Contexto vacio = new Contexto(0, Set.of());

  @Test
  void consultaNoNecesitaPropuestaYFiltraFuentesInventadas() throws Exception {
    var datos = json.readTree("{\"acuerdos\":[{\"id\":\"vigente\"}]}");
    var respuesta =
        interprete.interpretar(
            "{\"tipo\":\"RESPUESTA\",\"texto\":\"El pago es"
                + " opcional\",\"fuentes\":[\"vigente\",\"inventado\"],\"supuestos\":[]}",
            vacio,
            datos,
            true);
    assertThat(respuesta.propuesta()).isNull();
    assertThat(respuesta.fuentes()).hasSize(1);
    assertThat(respuesta.fuentes().get(0).asText()).isEqualTo("vigente");
  }

  @Test
  void modoConsultaConservaLaExplicacionPeroDescartaLaPropuestaDelProveedor() {
    var r =
        interprete.interpretar(
            "{\"tipo\":\"PROPUESTA\",\"texto\":\"Así modelaría el inventario: Producto,"
                + " Almacen…\",\"operaciones\":[{\"operacion\":\"CREAR\",\"entidad\":\"CLASE\","
                + "\"referencia\":\"p\",\"datos\":{\"nombre\":\"Producto\"}}]}",
            vacio,
            json.createObjectNode(),
            true);
    assertThat(r.tipo()).isEqualTo("RESPUESTA");
    assertThat(r.propuesta()).isNull();
    assertThat(r.texto()).contains("inventario");
    assertThat(r.supuestos()).hasSize(1);
    assertThat(r.supuestos().get(0).asText()).contains("consulta");
  }

  @Test
  void respuestaDeMentorAdmiteMarkdownLargo() {
    String md = "### Diagnóstico\n- Falta **Lote**\n\n| A | B |\n|---|---|\n| 1 | 2 |\n".repeat(50);
    var r =
        interprete.interpretar(
            json.createObjectNode().put("tipo", "RESPUESTA").put("texto", md).toString(),
            vacio,
            json.createObjectNode(),
            true);
    assertThat(r.texto()).startsWith("### Diagnóstico");
  }

  @Test
  void jsonInvalidoYTipoDesconocidoSeRechazan() {
    for (String r :
        List.of(
            "no es json",
            "{\"tipo\":\"SQL\",\"texto\":\"ejecutar\"}",
            "{\"tipo\":\"RESPUESTA\",\"texto\":\"\"}"))
      assertThatThrownBy(() -> interprete.interpretar(r, vacio, json.createObjectNode(), false))
          .isInstanceOf(ApiException.class);
  }

  @Test
  void creaIdsEnServidorOrdenaReferenciasYSusDependencias() throws Exception {
    var acciones =
        json.readTree(
            """
            [{"operacion":"CREAR","entidad":"ATRIBUTO","referencia":"nombre","datos":{"idClase":"cliente","nombre":"cliente","tipoUml":"String"}},
             {"operacion":"CREAR","entidad":"CLASE","referencia":"cliente","datos":{"nombre":"Cliente","esPersistente":false}}]
            """);
    var r = interprete.operaciones(acciones, vacio, "Cliente");
    assertThat(r.operaciones())
        .extracting(OperacionGenerada::tipoEntidad)
        .containsExactly("CLASE", "ATRIBUTO");
    var clase = r.operaciones().getFirst();
    var atributo = r.operaciones().getLast();
    assertThat(atributo.valorPropuesto().path("idClase").asText())
        .isEqualTo(clase.idEntidad().toString());
    assertThat(atributo.valorPropuesto().path("nombre").asText()).isEqualTo("cliente");
    assertThat(atributo.dependencias()).containsExactly(clase.id());
    assertThat(clase.valorPropuesto().path("esPersistente").asBoolean()).isFalse();
  }

  @Test
  void actualizacionUsaUuidConservaValorActualYNoPierdeCampos() throws Exception {
    UUID id = UUID.randomUUID();
    var valor =
        json.createObjectNode()
            .put("nombre", "Cliente")
            .put("esAbstracto", true)
            .put("esPersistente", false);
    var c =
        new Contexto(
            1,
            Set.of("Cliente"),
            List.of(new ElementoContexto(id, "CLASE", "Cliente", null, valor)));
    var r =
        interprete.operaciones(
            json.readTree(
                "[{\"operacion\":\"ACTUALIZAR\",\"entidad\":\"CLASE\",\"referencia\":\""
                    + id
                    + "\",\"datos\":{\"nombre\":\"Persona\"}}]"),
            c,
            "Renombrar");
    var o = r.operaciones().getFirst();
    assertThat(o.idEntidad()).isEqualTo(id);
    assertThat(o.valorActual()).isEqualTo(valor);
    assertThat(o.valorPropuesto().path("esAbstracto").asBoolean()).isTrue();
    assertThat(o.valorPropuesto().path("nombre").asText()).isEqualTo("Persona");
    assertThat(valor.path("nombre").asText()).isEqualTo("Cliente");
  }

  @Test
  void noAdmiteReferenciasAjenasNiHomonimosPorNombre() throws Exception {
    for (String referencia : List.of("Cliente", UUID.randomUUID().toString())) {
      var acciones =
          json.readTree(
              "[{\"operacion\":\"ELIMINAR\",\"entidad\":\"CLASE\",\"referencia\":\""
                  + referencia
                  + "\"}]");
      assertThatThrownBy(() -> interprete.operaciones(acciones, vacio, "Eliminar"))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void noAdmiteInterfacesSqlDuplicadosNiCiclosDeDependencia() throws Exception {
    for (String acciones :
        List.of(
            "[{\"operacion\":\"SQL\",\"entidad\":\"CLASE\",\"referencia\":\"c\",\"datos\":{}}]",
            "[{\"operacion\":\"CREAR\",\"entidad\":\"INTERFAZ\",\"referencia\":\"c\",\"datos\":{}}]",
            "[{\"operacion\":\"CREAR\",\"entidad\":\"CLASE\",\"referencia\":\"c\",\"datos\":{}},{\"operacion\":\"CREAR\",\"entidad\":\"CLASE\",\"referencia\":\"c\",\"datos\":{}}]",
            "[{\"operacion\":\"CREAR\",\"entidad\":\"PAQUETE\",\"referencia\":\"p\",\"datos\":{\"padre\":\"p\"}}]")) {
      var nodos = json.readTree(acciones);
      assertThatThrownBy(() -> interprete.operaciones(nodos, vacio, "Rechazar"))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void distinguePreguntasDeOrdenesSinRequerirSignoInicial() {
    for (String texto :
        List.of(
            "¿Cómo agregaría fidelización?",
            "Explica la composición",
            "Cómo funcionan los pagos",
            "Qué contiene el modelo",
            "¿Qué le falta a este diagrama?",
            "Qué le falta a este diagrama para ser adecuado",
            "Dime cómo proceder con esta relación",
            "Sugiere qué añadir a Reserva",
            "Cómo desarrollar el módulo de inventarios",
            "Necesito saber si Pedido y Pago están bien relacionados?"))
      assertThat(AgenteColaborativoServicio.esConsulta(texto)).isTrue();
    for (String texto :
        List.of(
            "Agrega fidelización",
            "Crea clase Pago",
            "Elimina clase Temporal",
            "Aplícala",
            "Añade el atributo stock a Producto"))
      assertThat(AgenteColaborativoServicio.esConsulta(texto)).isFalse();
  }
}
