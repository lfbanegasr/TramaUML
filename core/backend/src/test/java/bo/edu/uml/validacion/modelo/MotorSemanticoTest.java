package bo.edu.uml.validacion.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class MotorSemanticoTest {
    private final ObjectMapper json = new ObjectMapper();

    private ArrayNode catalogo() {
        ArrayNode c = json.createArrayNode();
        for (String clave :
                new String[] {
                    "operacion.firma.unica",
                    "persistente.id.obligatorio",
                    "clase.nombre.unico",
                    "clase.nombre.obligatorio",
                    "atributo.tipo.obligatorio",
                    "xmi.caracteristica.no_compatible",
                    "generalizacion.sin_ciclos",
                    "relacion.cardinalidad.valida",
                    "relacion.extremos.validos"
                }) {
            c.addObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("codigo", clave)
                    .put("clave_implementacion", clave)
                    .put("severidad", "ERROR");
        }
        return c;
    }

    private ObjectNode modelo() {
        ObjectNode m = json.createObjectNode();
        for (String grupo :
                new String[] {
                    "clases", "atributos", "operaciones", "parametros", "paquetes", "relaciones"
                }) m.putArray(grupo);
        return m;
    }

    private ObjectNode clase(ObjectNode m, String nombre) {
        return ((ArrayNode) m.get("clases"))
                .addObject()
                .put("id", UUID.randomUUID().toString())
                .put("nombre", nombre)
                .put("es_persistente", false);
    }

    private ObjectNode atributo(ObjectNode m, ObjectNode c, String nombre, String tipo) {
        ObjectNode atributo =
                ((ArrayNode) m.get("atributos"))
                        .addObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("id_clase", c.get("id").asText())
                        .put("nombre", nombre)
                        .put("tipo_uml", tipo)
                        .put("multiplicidad_minima", 1)
                        .put("multiplicidad_maxima", 1);
        atributo.putObject("restricciones_json");
        return atributo;
    }

    @Test
    void tipoSimpleAmbiguoSeResuelveConPaqueteCompleto() {
        var m = modelo();
        String ventas = UUID.randomUUID().toString(), compras = UUID.randomUUID().toString();
        ((ArrayNode) m.get("paquetes")).addObject().put("id", ventas).put("nombre", "ventas");
        ((ArrayNode) m.get("paquetes")).addObject().put("id", compras).put("nombre", "compras");
        clase(m, "Cliente").put("id_paquete", ventas);
        clase(m, "Cliente").put("id_paquete", compras);
        var orden = clase(m, "Orden");
        var a = atributo(m, orden, "cliente", "Cliente");
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos())
                .anyMatch(h -> h.codigo().equals("TIPO_AMBIGUO"));
        a.put("tipo_uml", "ventas.Cliente");
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos()).isEmpty();
    }

    @Test
    void valoresFueraDelRangoDelTipoYRestriccionesInvalidasSonErrores() {
        var m = modelo();
        var c = clase(m, "Producto");
        var a = atributo(m, c, "cantidad", "Integer").put("valor_predeterminado", "2147483648");
        a.putObject("restricciones_json").put("min", 10).put("max", 2).put("pattern", "[");
        var hallazgos = new MotorSemantico().evaluar(m, catalogo()).hallazgos();
        assertThat(hallazgos).anyMatch(h -> h.codigo().equals("VALOR_PREDETERMINADO"));
        assertThat(hallazgos).anyMatch(h -> h.codigo().equals("RESTRICCION_INVALIDA"));
        a.put("valor_predeterminado", "3");
        a.putObject("restricciones_json").put("min", 0).put("max", 10);
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos()).isEmpty();
    }

    @Test
    void cicloDeGeneralizacionSeDetectaSinInterpretarReglasDesconocidas() {
        var m = modelo();
        var a = clase(m, "Padre");
        var b = clase(m, "Hijo");
        for (ObjectNode[] par : new ObjectNode[][] {{a, b}, {b, a}})
            ((ArrayNode) m.get("relaciones"))
                    .addObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("tipo_relacion", "GENERALIZACION")
                    .put("id_clase_origen", par[0].get("id").asText())
                    .put("id_clase_destino", par[1].get("id").asText())
                    .put("minimo_origen", 1)
                    .put("maximo_origen", 1)
                    .put("minimo_destino", 1)
                    .put("maximo_destino", 1);
        var reglas = catalogo();
        reglas.addObject()
                .put("id", UUID.randomUUID().toString())
                .put("codigo", "REGLA_FUTURA")
                .put("clave_implementacion", "futura.sin_implementar")
                .put("severidad", "ADVERTENCIA");
        var r = new MotorSemantico().evaluar(m, reglas);
        assertThat(r.incompleto()).isTrue();
        assertThat(r.hallazgos()).anyMatch(h -> h.codigo().equals("generalizacion.sin_ciclos"));
        assertThat(r.hallazgos())
                .anyMatch(
                        h ->
                                h.codigo().equals("REGLA_NO_IMPLEMENTADA")
                                        && h.severidad().equals("ERROR"));
    }
}
