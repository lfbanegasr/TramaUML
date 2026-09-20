package bo.edu.uml.ia;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import bo.edu.uml.ia.servicio.ProveedorIa;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class IaTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MotorAsistenciaInteligente motor = new MotorAsistenciaInteligente(json);

    @Test
    void interpretaClasesAtributosRelacionYDependencias() {
        var resultado =
                motor.interpretar(
                        "Crea una clase Cliente con atributos id: Long, nombre: String y una clase"
                                + " Pedido con atributo fecha: LocalDate, con relación 1 a *",
                        new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).hasSize(6);
        assertThat(resultado.operaciones())
                .extracting(MotorAsistenciaInteligente.OperacionGenerada::tipoEntidad)
                .containsExactly("CLASE", "ATRIBUTO", "ATRIBUTO", "CLASE", "ATRIBUTO", "RELACION");
        var cliente = resultado.operaciones().getFirst();
        var atributo = resultado.operaciones().get(1);
        var relacion = resultado.operaciones().getLast();
        assertThat(atributo.dependencias()).containsExactly(cliente.id());
        assertThat(relacion.dependencias()).hasSize(2);
        assertThat(relacion.valorPropuesto().get("minimoOrigen").asInt()).isEqualTo(1);
        assertThat(relacion.valorPropuesto().get("maximoOrigen").asInt()).isEqualTo(1);
        assertThat(relacion.valorPropuesto().get("minimoDestino").asInt()).isZero();
        assertThat(relacion.valorPropuesto().get("maximoDestino").isNull()).isTrue();
        assertThat(resultado.resumen()).contains("2 clase(s)", "3 atributo(s)", "1 relación(es)");
    }

    @Test
    void distribuyeClasesSinSuperponerLaPosicionInicial() {
        var resultado =
                motor.interpretar(
                        "Crea clase Uno, clase Dos, clase Tres y clase Cuatro",
                        new Contexto(2, Set.of()));

        assertThat(resultado.operaciones())
                .extracting(
                        o ->
                                o.valorPropuesto().get("x").asInt()
                                        + ":"
                                        + o.valorPropuesto().get("y").asInt())
                .doesNotHaveDuplicates();
    }

    @Test
    void marcaDuplicadoComoInvalidoYNoSeleccionado() {
        var resultado =
                motor.interpretar("Crea una clase Cliente", new Contexto(1, Set.of("Cliente")));

        assertThat(resultado.operaciones().getFirst().estado()).isEqualTo("INVALIDO");
        assertThat(resultado.operaciones().getFirst().seleccionada()).isFalse();
        assertThat(resultado.confianzaGeneral()).isEqualByComparingTo("0.6500");
    }

    @Test
    void exigeUnaClaseIdentificable() {
        assertThatThrownBy(
                        () ->
                                motor.interpretar(
                                        "Agrega algo al diagrama", new Contexto(0, Set.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No se identificó una instrucción compatible");
    }

    @Test
    void usaNormalizacionRemotaOpcionalYConservaElRespaldoLocal() {
        var remoto =
                new ProveedorIa() {
                    @Override
                    public Optional<String> normalizar(String instruccion) {
                        return Optional.of("Crea clase Normalizada");
                    }
                };
        var hibrido = new MotorAsistenciaInteligente(new ObjectMapper(), remoto);
        assertThat(hibrido.interpretar("haz una entidad", new Contexto(0, Set.of())).operaciones())
                .extracting(o -> o.valorPropuesto().path("nombre").asText())
                .containsExactly("Normalizada");

        var remotoInvalido =
                new ProveedorIa() {
                    @Override
                    public Optional<String> normalizar(String instruccion) {
                        return Optional.of("respuesta sin gramática");
                    }
                };
        var respaldo = new MotorAsistenciaInteligente(new ObjectMapper(), remotoInvalido);
        assertThat(
                        respaldo.interpretar("Crea clase Local", new Contexto(0, Set.of()))
                                .operaciones())
                .extracting(o -> o.valorPropuesto().path("nombre").asText())
                .containsExactly("Local");
    }

    @Test
    void interpretaActualizacionYEliminacionConElValorActual() {
        UUID cliente = UUID.randomUUID();
        var valor = json.createObjectNode().put("nombre", "Cliente").put("visibilidad", "PUBLICO");
        var contexto =
                new Contexto(
                        1,
                        Set.of("Cliente"),
                        List.of(new ElementoContexto(cliente, "CLASE", "Cliente", null, valor)));

        var renombre = motor.interpretar("Renombra la clase Cliente a Comprador", contexto);
        assertThat(renombre.operaciones())
                .singleElement()
                .satisfies(
                        operacion -> {
                            assertThat(operacion.tipoOperacion()).isEqualTo("ACTUALIZAR");
                            assertThat(operacion.idEntidad()).isEqualTo(cliente);
                            assertThat(operacion.valorActual().path("nombre").asText())
                                    .isEqualTo("Cliente");
                            assertThat(operacion.valorPropuesto().path("nombre").asText())
                                    .isEqualTo("Comprador");
                        });

        var eliminacion = motor.interpretar("Elimina la clase Cliente", contexto);
        assertThat(eliminacion.operaciones())
                .singleElement()
                .satisfies(
                        operacion -> {
                            assertThat(operacion.tipoOperacion()).isEqualTo("ELIMINAR");
                            assertThat(operacion.valorActual()).isEqualTo(valor);
                            assertThat(operacion.valorPropuesto()).isNull();
                        });
    }

    @Test
    void interpretaCambiosSobreClasesExistentesYMarcaReferenciasDudosas() {
        UUID cliente = UUID.randomUUID(), pedido = UUID.randomUUID();
        var contexto =
                new Contexto(
                        2,
                        Set.of("Cliente", "Pedido"),
                        List.of(
                                new ElementoContexto(
                                        cliente,
                                        "CLASE",
                                        "Cliente",
                                        null,
                                        json.createObjectNode().put("nombre", "Cliente")),
                                new ElementoContexto(
                                        pedido,
                                        "CLASE",
                                        "Pedido",
                                        null,
                                        json.createObjectNode().put("nombre", "Pedido"))));

        var resultado =
                motor.interpretar(
                        "Crea atributo correo: String en clase Cliente; agrega operación guardar():"
                                + " void en clase Cliente; relaciona Cliente con Pedido 1 a *",
                        contexto);
        assertThat(resultado.operaciones())
                .extracting(MotorAsistenciaInteligente.OperacionGenerada::tipoEntidad)
                .containsExactly("ATRIBUTO", "OPERACION", "RELACION");
        assertThat(resultado.operaciones()).allMatch(o -> o.estado().equals("VALIDO"));

        var dudosa = motor.interpretar("Crea atributo codigo: Long en clase Ausente", contexto);
        assertThat(dudosa.operaciones().getFirst().seleccionada()).isFalse();
        assertThat(dudosa.operaciones().getFirst().estado()).isEqualTo("INVALIDO");
    }
}
