package bo.edu.uml.xmi;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.xmi.servicio.AnalizadorXmi21Service;
import bo.edu.uml.xmi.servicio.GeneradorXmi21Service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pruebas unitarias del generador XMI 2.1 sin depender de Spring ni PostgreSQL. */
class XmiTest {

    private final GeneradorXmi21Service generador = new GeneradorXmi21Service();
    private final AnalizadorXmi21Service analizador = new AnalizadorXmi21Service();

    private Map<String, Object> clase(String id, String nombre, String paquete, boolean abstracto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nombre", nombre);
        m.put("visibilidad", "PUBLICO");
        m.put("es_abstracto", abstracto);
        m.put("es_persistente", true);
        m.put("id_paquete", paquete);
        return m;
    }

    private Map<String, Object> atributo(
            String id, String idClase, String nombre, String tipo, boolean esId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("id_clase", idClase);
        m.put("nombre", nombre);
        m.put("tipo_uml", tipo);
        m.put("visibilidad", "PRIVADO");
        m.put("es_identificador", esId);
        m.put("es_estatico", false);
        m.put("multiplicidad_minima", 1);
        m.put("multiplicidad_maxima", 1);
        return m;
    }

    private Map<String, Object> relacion(
            String id,
            String origen,
            String destino,
            String tipo,
            Integer minO,
            Integer maxO,
            Integer minD,
            Integer maxD) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("id_clase_origen", origen);
        m.put("id_clase_destino", destino);
        m.put("tipo_relacion", tipo);
        m.put("nombre", "");
        m.put("rol_origen", "");
        m.put("rol_destino", "");
        m.put("minimo_origen", minO);
        m.put("maximo_origen", maxO);
        m.put("minimo_destino", minD);
        m.put("maximo_destino", maxD);
        return m;
    }

    @Test
    void generaXmiConEncabezadoCorrecto() {
        byte[] resultado =
                generador.generar(
                        "TestModelo",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        String xml = new String(resultado, StandardCharsets.UTF_8);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("xmi:XMI");
        assertThat(xml).contains("xmi:version=\"2.1\"");
        assertThat(xml).contains("uml:Model");
        assertThat(xml).contains("Plataforma UML Colaborativa");
    }

    @Test
    void incluyeClaseConAtributos() {
        String idClase = UUID.randomUUID().toString();
        var clases = List.of(clase(idClase, "Persona", null, false));
        var atributos =
                List.of(
                        atributo(UUID.randomUUID().toString(), idClase, "nombre", "String", false),
                        atributo(UUID.randomUUID().toString(), idClase, "edad", "Integer", false));

        byte[] resultado =
                generador.generar(
                        "Test", List.of(), clases, atributos, List.of(), List.of(), List.of());
        String xml = new String(resultado, StandardCharsets.UTF_8);

        assertThat(xml).contains("name=\"Persona\"");
        assertThat(xml).contains("name=\"nombre\"");
        assertThat(xml).contains("name=\"edad\"");
        assertThat(xml).contains("type=\"String\"");
        assertThat(xml).contains("type=\"Integer\"");
    }

    @Test
    void incluyePaquetesYClasesAnidadas() {
        String idPaquete = UUID.randomUUID().toString();
        String idClase = UUID.randomUUID().toString();
        Map<String, Object> paquete = new LinkedHashMap<>();
        paquete.put("id", idPaquete);
        paquete.put("id_paquete_padre", null);
        paquete.put("nombre", "dominio");

        byte[] resultado =
                generador.generar(
                        "Test",
                        List.of(paquete),
                        List.of(clase(idClase, "Pedido", idPaquete, false)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        String xml = new String(resultado, StandardCharsets.UTF_8);

        assertThat(xml).contains("name=\"dominio\"");
        assertThat(xml).contains("name=\"Pedido\"");
    }

    @Test
    void incluyeAsociacionConCardinalidades() {
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();
        String idRel = UUID.randomUUID().toString();

        byte[] resultado =
                generador.generar(
                        "Test",
                        List.of(),
                        List.of(
                                clase(idA, "Cliente", null, false),
                                clase(idB, "Pedido", null, false)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(relacion(idRel, idA, idB, "ASOCIACION", 1, 1, 0, null)));
        String xml = new String(resultado, StandardCharsets.UTF_8);

        assertThat(xml).contains("uml:Association");
        assertThat(xml).contains("lowerValue");
        assertThat(xml).contains("upperValue");
    }

    @Test
    void claseAbstractaTieneAtributoIsAbstract() {
        String idClase = UUID.randomUUID().toString();
        byte[] resultado =
                generador.generar(
                        "Test",
                        List.of(),
                        List.of(clase(idClase, "Entidad", null, true)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        String xml = new String(resultado, StandardCharsets.UTF_8);

        assertThat(xml).contains("isAbstract=\"true\"");
    }

    @Test
    void esXmlValido() {
        String idA = UUID.randomUUID().toString();
        byte[] resultado =
                generador.generar(
                        "ModeloComplejo",
                        List.of(),
                        List.of(clase(idA, "Producto", null, false)),
                        List.of(
                                atributo(
                                        UUID.randomUUID().toString(),
                                        idA,
                                        "precio",
                                        "Decimal",
                                        false)),
                        List.of(),
                        List.of(),
                        List.of());
        String xml = new String(resultado, StandardCharsets.UTF_8);

        // Contabilizar tags de apertura y cierre básicos
        assertThat(xml).contains("</xmi:XMI>");
        assertThat(xml).contains("</uml:Model>");
        // Bien formado: no hay < sin cerrar (verificación básica)
        assertThat(xml.chars().filter(c -> c == '<').count())
                .isEqualTo(xml.chars().filter(c -> c == '>').count());
    }

    @Test
    void exportacionPuedeAnalizarseSinPerderClasesAtributosYRelacion() {
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();
        byte[] xmi =
                generador.generar(
                        "Ventas",
                        List.of(),
                        List.of(
                                clase(idA, "Cliente", null, false),
                                clase(idB, "Pedido", null, false)),
                        List.of(
                                atributo(
                                        UUID.randomUUID().toString(),
                                        idA,
                                        "nombre",
                                        "String",
                                        false)),
                        List.of(),
                        List.of(),
                        List.of(
                                relacion(
                                        UUID.randomUUID().toString(),
                                        idA,
                                        idB,
                                        "ASOCIACION",
                                        1,
                                        1,
                                        0,
                                        null)));

        var resultado = analizador.analizar(xmi, UUID.randomUUID());

        assertThat(resultado.nombreModelo()).isEqualTo("Ventas");
        assertThat(resultado.clases())
                .extracting(AnalizadorXmi21Service.Clase::nombre)
                .containsExactly("Cliente", "Pedido");
        assertThat(resultado.atributos())
                .singleElement()
                .extracting(a -> a.nombre())
                .isEqualTo("nombre");
        assertThat(resultado.relaciones())
                .singleElement()
                .satisfies(
                        r -> {
                            assertThat(r.tipo()).isEqualTo("ASOCIACION");
                            assertThat(r.maximoDestino()).isNull();
                        });
    }

    @Test
    void generalizacionYDependenciaUsanEstructurasUmlImportables() {
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();
        byte[] xmi =
                generador.generar(
                        "Tipos",
                        List.of(),
                        List.of(
                                clase(idA, "Especializada", null, false),
                                clase(idB, "Base", null, true)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                relacion(
                                        UUID.randomUUID().toString(),
                                        idA,
                                        idB,
                                        "GENERALIZACION",
                                        null,
                                        null,
                                        null,
                                        null),
                                relacion(
                                        UUID.randomUUID().toString(),
                                        idA,
                                        idB,
                                        "DEPENDENCIA",
                                        null,
                                        null,
                                        null,
                                        null)));
        String xml = new String(xmi, StandardCharsets.UTF_8);

        assertThat(xml).contains("<generalization").contains("uml:Dependency");
        assertThat(xml).doesNotContain("_gen_wrapper");
        assertThat(analizador.analizar(xmi, UUID.randomUUID()).relaciones())
                .extracting(AnalizadorXmi21Service.Relacion::tipo)
                .containsExactlyInAnyOrder("GENERALIZACION", "DEPENDENCIA");
    }

    @Test
    void rechazaDoctypeYEntidadesExternas() {
        String xmi =
                """
                <?xml version="1.0"?>
                <!DOCTYPE xmi:XMI [<!ENTITY archivo SYSTEM "file:///etc/passwd">]>
                <xmi:XMI xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmi:version="2.1"
                         xmlns:uml="http://schema.omg.org/spec/UML/2.1">
                  <uml:Model xmi:id="m" name="&archivo;"/>
                </xmi:XMI>
                """;

        assertThatThrownBy(
                        () ->
                                analizador.analizar(
                                        xmi.getBytes(StandardCharsets.UTF_8), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("seguro");
    }
}
