package bo.edu.uml.generacion;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.generacion.dto.GeneracionDto.Dependencia;
import bo.edu.uml.generacion.servicio.MotorGeneracionSpringBoot;
import bo.edu.uml.generacion.servicio.MotorGeneracionSpringBoot.OpcionesGeneracion;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

class SemanticaGeneracion251Test {
    final MotorGeneracionSpringBoot motor = new MotorGeneracionSpringBoot();

    Map<String, Object> clase(String id, String n) {
        return Map.of("id", id, "nombre", n, "es_persistente", true, "es_abstracto", false);
    }

    Map<String, String> archivos(byte[] zip) throws Exception {
        var out = new HashMap<String, String>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; )
                out.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void maximoAusenteAnteriorEsEscalarYNullExplicitoEsIlimitado() throws Exception {
        String c = UUID.randomUUID().toString();
        var atributo =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id_clase",
                                c,
                                "nombre",
                                "nombre",
                                "tipo_uml",
                                "String",
                                "multiplicidad_minima",
                                1));
        assertThat(
                        archivos(
                                        motor.generar(
                                                "bo.edu",
                                                "dominio",
                                                "1.0.0",
                                                "bo.edu.dominio",
                                                List.of(clase(c, "Cliente")),
                                                List.of(atributo),
                                                List.of()))
                                .get("dominio/src/main/java/bo/edu/dominio/modelo/Cliente.java"))
                .contains("String nombre");
        atributo.put("multiplicidad_maxima", null);
        assertThatThrownBy(
                        () ->
                                motor.generar(
                                        "bo.edu",
                                        "dominio",
                                        "1.0.0",
                                        "bo.edu.dominio",
                                        List.of(clase(c, "Cliente")),
                                        List.of(atributo),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GENERACION_NO_SOPORTADA");
    }

    @Test
    void dependenciaNoCreaFkNiAsociacionJpa() throws Exception {
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        var bytes =
                motor.generar(
                        "bo.edu",
                        "dominio",
                        "1.0.0",
                        "bo.edu.dominio",
                        List.of(clase(a, "Cliente"), clase(b, "Proveedor")),
                        List.of(),
                        List.of(
                                Map.of(
                                        "id",
                                        UUID.randomUUID(),
                                        "id_clase_origen",
                                        a,
                                        "id_clase_destino",
                                        b,
                                        "tipo_relacion",
                                        "DEPENDENCIA")));
        assertThat(archivos(bytes).get("dominio/src/main/java/bo/edu/dominio/modelo/Cliente.java"))
                .doesNotContain("@ManyToOne", "@OneToMany", "@JoinColumn");
    }

    @Test
    void atributoColeccionValidoRecibeDiagnosticoEnLugarDeConvertirseAEscalar() {
        String c = UUID.randomUUID().toString();
        var a =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id",
                                UUID.randomUUID(),
                                "id_clase",
                                c,
                                "nombre",
                                "valores",
                                "tipo_uml",
                                "String",
                                "multiplicidad_minima",
                                0,
                                "es_ordenado",
                                true));
        a.put("multiplicidad_maxima", null);
        assertThatThrownBy(
                        () ->
                                motor.generar(
                                        "bo.edu",
                                        "dominio",
                                        "1.0.0",
                                        "bo.edu.dominio",
                                        List.of(clase(c, "Cliente")),
                                        List.of(a),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GENERACION_NO_SOPORTADA");
    }

    @Test
    void retornoEsResultadoYSoloEntradaSeConvierteEnArgumento() throws Exception {
        String c = UUID.randomUUID().toString(), op = UUID.randomUUID().toString();
        var options =
                new OpcionesGeneracion(
                        "21",
                        "3.5.16",
                        "NINGUNO",
                        true,
                        true,
                        false,
                        false,
                        false,
                        List.of(
                                new Dependencia(
                                        "org.springframework.boot",
                                        "spring-boot-starter-data-jpa",
                                        null,
                                        "COMPILACION",
                                        true)),
                        List.of());
        var operation = Map.<String, Object>of("id", op, "id_clase", c, "nombre", "calcular");
        var parameters =
                List.<Map<String, Object>>of(
                        Map.of(
                                "id",
                                UUID.randomUUID(),
                                "id_operacion",
                                op,
                                "nombre",
                                "resultado",
                                "tipo_uml",
                                "Integer",
                                "direccion",
                                "RETORNO"));
        var files =
                archivos(
                        motor.generar(
                                        "bo.edu",
                                        "dominio",
                                        "1.0.0",
                                        "bo.edu.dominio",
                                        options,
                                        List.of(clase(c, "Cliente")),
                                        List.of(),
                                        List.of(),
                                        List.of(operation),
                                        parameters)
                                .contenido());
        assertThat(files.get("dominio/src/main/java/bo/edu/dominio/servicio/ClienteServicio.java"))
                .contains("public Integer calcular()")
                .doesNotContain("Integer resultado)");
        var salida =
                List.<Map<String, Object>>of(
                        Map.of(
                                "id_operacion",
                                op,
                                "nombre",
                                "salida",
                                "tipo_uml",
                                "Integer",
                                "direccion",
                                "SALIDA"));
        assertThatThrownBy(
                        () ->
                                motor.generar(
                                        "bo.edu",
                                        "dominio",
                                        "1.0.0",
                                        "bo.edu.dominio",
                                        options,
                                        List.of(clase(c, "Cliente")),
                                        List.of(),
                                        List.of(),
                                        List.of(operation),
                                        salida))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out/inout");
    }
}
