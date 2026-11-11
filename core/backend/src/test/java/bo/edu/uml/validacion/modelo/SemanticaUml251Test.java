package bo.edu.uml.validacion.modelo;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.historial.servicio.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class SemanticaUml251Test {
    final ObjectMapper json = new ObjectMapper();

    ArrayNode catalogo() {
        var c = json.createArrayNode();
        for (String key :
                List.of(
                        "operacion.firma.unica",
                        "persistente.id.obligatorio",
                        "clase.nombre.unico",
                        "clase.nombre.obligatorio",
                        "atributo.tipo.obligatorio",
                        "xmi.caracteristica.no_compatible",
                        "generalizacion.sin_ciclos",
                        "relacion.cardinalidad.valida",
                        "relacion.extremos.validos"))
            c.addObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("clave_implementacion", key)
                    .put("codigo", key)
                    .put("severidad", "ERROR");
        return c;
    }

    ObjectNode modelo() {
        var m = json.createObjectNode();
        m.put("formato_version", 2);
        m.putObject("modelo").put("id", UUID.randomUUID().toString()).put("nombre", "M");
        for (String key :
                List.of(
                        "clases",
                        "atributos",
                        "operaciones",
                        "parametros",
                        "paquetes",
                        "relaciones",
                        "posiciones_clases",
                        "posiciones_relaciones")) m.putArray(key);
        return m;
    }

    ObjectNode clase(ObjectNode m, String name) {
        return ((ArrayNode) m.get("clases"))
                .addObject()
                .put("id", UUID.randomUUID().toString())
                .put("nombre", name)
                .put("es_persistente", false);
    }

    ObjectNode relacion(ObjectNode m, ObjectNode src, ObjectNode dst) {
        return ((ArrayNode) m.get("relaciones"))
                .addObject()
                .put("id", UUID.randomUUID().toString())
                .put("tipo_relacion", "COMPOSICION")
                .put("id_clase_origen", src.path("id").asText())
                .put("id_clase_destino", dst.path("id").asText())
                .put("direccion", "SIN_DIRECCION")
                .put("minimo_origen", 0)
                .put("maximo_origen", 1)
                .put("minimo_destino", 0)
                .putNull("maximo_destino");
    }

    ObjectNode atributo(ObjectNode m, ObjectNode c) {
        var a =
                ((ArrayNode) m.get("atributos"))
                        .addObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("id_clase", c.path("id").asText())
                        .put("nombre", "valores")
                        .put("tipo_uml", "String")
                        .put("multiplicidad_minima", 0)
                        .putNull("multiplicidad_maxima");
        a.putObject("restricciones_json");
        return a;
    }

    @Test
    void variosConjuntosYComposicionRecursivaSonValidosComoTipos() {
        var m = modelo();
        var a = clase(m, "A");
        var b = clase(m, "B");
        var p = clase(m, "Parte");
        relacion(m, a, p);
        relacion(m, b, p);
        relacion(m, p, p);
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos()).isEmpty();
    }

    @Test
    void composicionNoAdmiteMasDeUnConjuntoPorInstancia() {
        var m = modelo();
        var a = clase(m, "A");
        var p = clase(m, "P");
        var r = relacion(m, a, p).putNull("maximo_origen");
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos())
                .anyMatch(h -> h.codigo().equals("COMPOSICION_MULTIPLICIDAD_CONJUNTO"));
        r.put("maximo_origen", 1);
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos()).isEmpty();
    }

    @Test
    void idJpaFaltanteNoInvalidaUmlYLaInstanciaSePuedeReutilizar() {
        var m = modelo();
        var c = clase(m, "A").put("es_persistente", true);
        var motor = new MotorSemantico();
        assertThat(motor.evaluar(m, catalogo()).hallazgos())
                .noneMatch(h -> h.severidad().equals("ERROR"))
                .anyMatch(h -> h.codigo().equals("GENERACION_ID_REQUERIDO"));
        c.put("es_persistente", false);
        assertThat(motor.evaluar(m, catalogo()).hallazgos()).isEmpty();
    }

    @Test
    void coleccionOrdenadaNoUnicaEsUmlValidoInclusoConMarcaSqlIndependiente() {
        var m = modelo();
        var c = clase(m, "A");
        atributo(m, c)
                .put("es_ordenado", true)
                .put("coleccion_unica", false)
                .put("es_unico", true)
                .put("es_identificador", true);
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos()).isEmpty();
    }

    @Test
    void parametroHuerfanoSeDetecta() {
        var m = modelo();
        clase(m, "A");
        ((ArrayNode) m.get("parametros"))
                .addObject()
                .put("id", UUID.randomUUID().toString())
                .put("id_operacion", UUID.randomUUID().toString());
        assertThat(new MotorSemantico().evaluar(m, catalogo()).hallazgos())
                .anyMatch(h -> h.codigo().equals("REFERENCIA_INVALIDA"));
    }

    @Test
    void formatosDosYTresLeenGeneralizacionSinReescribirLaInstantanea() {
        var m = modelo();
        var a = clase(m, "A");
        var b = clase(m, "B");
        var r = relacion(m, a, b).put("tipo_relacion", "GENERALIZACION");
        for (String key :
                List.of("minimo_origen", "maximo_origen", "minimo_destino", "maximo_destino"))
            r.putNull(key);
        var validator = new ValidadorSnapshot();
        for (int format : List.of(2, 3)) {
            m.put("formato_version", format);
            String before = m.toString();
            validator.validar(m, UUID.fromString(m.path("modelo").path("id").asText()), null, null);
            assertThat(m.toString()).isEqualTo(before);
        }
    }

    @Test
    void compararDefaultsAgregadosNoCreaCambiosFalsos() {
        var old = modelo();
        var c = clase(old, "A");
        atributo(old, c);
        var current = old.deepCopy();
        current.put("formato_version", 3);
        ((ObjectNode) current.path("atributos").get(0))
                .put("es_ordenado", false)
                .put("coleccion_unica", true);
        assertThat(new ComparadorVersiones().comparar(old, current)).isEmpty();
        ((ObjectNode) current.path("atributos").get(0)).put("es_ordenado", true);
        assertThat(new ComparadorVersiones().comparar(old, current)).hasSize(1);
    }
}
