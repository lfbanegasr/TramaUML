package bo.edu.uml.ia;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.ProveedorIa;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

class IaTest {
    private final MotorAsistenciaInteligente motor =
            new MotorAsistenciaInteligente(new ObjectMapper());

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
                .hasMessageContaining("No se identificó ninguna clase");
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
}
