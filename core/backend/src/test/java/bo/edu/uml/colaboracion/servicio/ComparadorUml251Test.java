package bo.edu.uml.colaboracion.servicio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class ComparadorUml251Test {
    final ObjectMapper json = new ObjectMapper();

    @Test
    void clienteAnteriorFusionaNombreSinBorrarOrdenConcurrente() {
        UUID id = UUID.randomUUID(), clase = UUID.randomUUID();
        var base = json.createObjectNode();
        base.putArray("atributos")
                .addObject()
                .put("id", id.toString())
                .put("id_clase", clase.toString())
                .put("nombre", "antes")
                .put("tipo_uml", "String")
                .put("es_ordenado", false)
                .put("coleccion_unica", true);
        var actual = base.deepCopy();
        ((ObjectNode) actual.path("atributos").get(0)).put("es_ordenado", true);
        var datos =
                json.createObjectNode()
                        .put("idClase", clase.toString())
                        .put("nombre", "despues")
                        .put("tipoUml", "String");
        var accion = new Accion("ACTUALIZAR", "ATRIBUTO", id, datos);
        var motor = new ComparadorConflicto(json);
        assertThat(motor.comparar(base, actual, List.of(accion)).fusionable()).isTrue();
        var fusion = motor.fusionar(base, actual, List.of(accion)).getFirst().datos();
        assertThat(fusion.path("esOrdenado").asBoolean()).isTrue();
        assertThat(fusion.path("coleccionUnica").asBoolean()).isTrue();
        assertThat(fusion.path("nombre").asText()).isEqualTo("despues");
    }

    @Test
    void dimensionesDeClaseRequierenOrganizarTambienEnElCaminoCompartido() {
        UUID actor = UUID.randomUUID(), modelo = UUID.randomUUID(), proyecto = UUID.randomUUID();
        var repo = mock(ModeloRepositorio.class, CALLS_REAL_METHODS);
        doReturn(new ModeloRepositorio.Cabecera(modelo, proyecto, "M", 1, null, "ACTIVO", "ACTIVO"))
                .when(repo)
                .cabecera(modelo);
        doReturn(false).when(repo).permiso(actor, proyecto, "ORGANIZAR_MODELO");
        var action =
                new Accion(
                        "ACTUALIZAR",
                        "CLASE",
                        UUID.randomUUID(),
                        json.createObjectNode().put("ancho", 300));
        assertThatThrownBy(() -> repo.exigirOrganizacion(actor, modelo, action))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("organizar");
        doReturn(true).when(repo).permiso(actor, proyecto, "ORGANIZAR_MODELO");
        assertThatCode(() -> repo.exigirOrganizacion(actor, modelo, action))
                .doesNotThrowAnyException();
    }
}
