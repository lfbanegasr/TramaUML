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

    @Test
    void generaExtensionEnterpriseArchitectConDiagramaYMultiplicidades() {
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();
        String idRel = UUID.randomUUID().toString();
        List<Map<String, Object>> posClases =
                List.of(
                        Map.of(
                                "id_clase", idA,
                                "x", 120.0,
                                "y", 200.0,
                                "ancho", 180.0,
                                "alto", 100.0));

        byte[] xmi =
                generador.generar(
                        "ModeloEa",
                        List.of(),
                        List.of(
                                clase(idA, "Cliente", null, false),
                                clase(idB, "Factura", null, false)),
                        List.of(
                                atributo(
                                        UUID.randomUUID().toString(),
                                        idA,
                                        "codigo",
                                        "String",
                                        true)),
                        List.of(),
                        List.of(),
                        List.of(relacion(idRel, idA, idB, "ASOCIACION", 1, 1, 0, -1)),
                        posClases,
                        List.of(),
                        true);

        String xml = new String(xmi, StandardCharsets.UTF_8);

        assertThat(xml).contains("extender=\"Enterprise Architect\"");
        assertThat(xml).contains("<diagrams>");
        assertThat(xml)
                .contains("<properties name=\"ModeloEa - Diagrama de Clases\" type=\"Logical\"/>");
        assertThat(xml).contains("Left=120;Top=-200;Right=300;Bottom=-300;");
        assertThat(xml).contains("multiplicity=\"1\"");
        assertThat(xml).contains("multiplicity=\"0..*\"");
        assertThat(xml).contains("ea_type=\"Association\"");
    }

    @Test
    void generaScriptVbsParaEnterpriseArchitect() {
        var generadorVbs = new bo.edu.uml.xmi.servicio.GeneradorScriptEaService();
        String idA = UUID.randomUUID().toString();
        String idB = UUID.randomUUID().toString();
        String idRel = UUID.randomUUID().toString();

        byte[] vbs =
                generadorVbs.generar(
                        "Facturacion",
                        List.of(),
                        List.of(
                                clase(idA, "Emisor", null, false),
                                clase(idB, "Receptor", null, false)),
                        List.of(atributo(UUID.randomUUID().toString(), idA, "nit", "String", true)),
                        List.of(),
                        List.of(),
                        List.of(relacion(idRel, idA, idB, "ASOCIACION", 1, 1, 1, -1)),
                        List.of());

        String script = new String(vbs, StandardCharsets.UTF_8);

        assertThat(script).contains("Option Explicit");
        assertThat(script).contains("Set eaApp = GetObject(, \"EA.App\")");
        assertThat(script).contains("Packages.AddNew(\"Facturacion\", \"Nothing\")");
        assertThat(script)
                .contains("Diagrams.AddNew(\"Facturacion - Diagrama de Clases\", \"Logical\")");
        assertThat(script).contains("Elements.AddNew(\"Emisor\", \"Class\")");
        assertThat(script).contains("ClientEnd.Cardinality = \"1\"");
        assertThat(script).contains("SupplierEnd.Cardinality = \"1..*\"");
        assertThat(script).contains("repo.OpenDiagram(diagrama.DiagramID)");
    }

    @Test
    void scriptEaConservaPaquetesYUbicaClasesEnSuPaquete() {
        var generadorVbs = new bo.edu.uml.xmi.servicio.GeneradorScriptEaService();
        String padre = UUID.randomUUID().toString();
        String hijo = UUID.randomUUID().toString();
        String clase = UUID.randomUUID().toString();
        var paquetePadre = Map.<String, Object>of("id", padre, "nombre", "Administracion");
        var paqueteHijo =
                Map.<String, Object>of(
                        "id", hijo,
                        "id_paquete_padre", padre,
                        "nombre", "Clientes");
        String script =
                new String(
                        generadorVbs.generar(
                                "Modelo",
                                List.of(paqueteHijo, paquetePadre),
                                List.of(clase(clase, "Persona", hijo, false)),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()),
                        StandardCharsets.UTF_8);

        assertThat(script.indexOf("Packages.AddNew(\"Administracion\""))
                .isLessThan(script.indexOf("Packages.AddNew(\"Clientes\""));
        assertThat(script)
                .contains("If dictPaquetes.Exists(\"" + padre + "\") Then Set pkgDestino");
        assertThat(script).contains("If dictPaquetes.Exists(\"" + hijo + "\") Then Set pkgDestino");
        assertThat(script).contains("pkgDestino.Elements.AddNew(\"Persona\", \"Class\")");
    }

    @Test
    void analizadorRecuperaCoordenadasVisualesEa() {
        String idA = UUID.randomUUID().toString();
        String rawId = idA.replace("-", "");
        String xmi =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<xmi:XMI xmi:version=\"2.1\""
                    + " xmlns:xmi=\"http://schema.omg.org/spec/XMI/2.1\""
                    + " xmlns:uml=\"http://schema.omg.org/spec/UML/2.1\">\n"
                    + "  <uml:Model xmi:type=\"uml:Model\" xmi:id=\"_M\" name=\"ModeloConEa\">\n"
                    + "    <packagedElement xmi:type=\"uml:Class\" xmi:id=\"_"
                        + rawId
                        + "\" name=\"Articulo\" visibility=\"public\"/>\n"
                        + "  </uml:Model>\n"
                        + "  <xmi:Extension extender=\"Enterprise Architect\" extenderID=\"6.5\">\n"
                        + "    <diagrams>\n"
                        + "      <diagram xmi:id=\"_D\">\n"
                        + "        <elements>\n"
                        + "          <element geometry=\"Left=250;Top=340;Right=430;Bottom=460;\""
                        + " subject=\"_"
                        + rawId
                        + "\"/>\n"
                        + "        </elements>\n"
                        + "      </diagram>\n"
                        + "    </diagrams>\n"
                        + "  </xmi:Extension>\n"
                        + "</xmi:XMI>\n";

        var analisis = analizador.analizar(xmi.getBytes(StandardCharsets.UTF_8), UUID.randomUUID());

        assertThat(analisis.clases()).hasSize(1);
        var clase = analisis.clases().get(0);
        assertThat(clase.nombre()).isEqualTo("Articulo");
        assertThat(clase.x()).isEqualTo(250);
        assertThat(clase.y()).isEqualTo(340);
        assertThat(analisis.advertencias())
                .anyMatch(
                        adv ->
                                adv.contains("disposición visual")
                                        && adv.contains("Enterprise Architect"));
    }

    @Test
    void analizadorImportaRelacionesEnterpriseArchitectConConectoresYMemberEnd() {
        String xmi =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmi:version="2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1">
                  <uml:Model xmi:type="uml:Model" xmi:id="EA_Model" name="ModeloEA">
                    <packagedElement xmi:type="uml:Class" xmi:id="EAID_USR_1" name="Usuario" visibility="public">
                      <ownedAttribute xmi:type="uml:Property" xmi:id="EAID_ATTR_PED" name="pedido" association="EAID_CONN_1">
                        <type xmi:idref="EAID_PED_1"/>
                      </ownedAttribute>
                    </packagedElement>
                    <packagedElement xmi:type="uml:Class" xmi:id="EAID_PED_1" name="Pedido" visibility="public"/>
                    <packagedElement xmi:type="uml:Association" xmi:id="EAID_CONN_1" name="realiza" memberEnd="EAID_ATTR_PED EAID_END_USR">
                      <ownedEnd xmi:type="uml:Property" xmi:id="EAID_END_USR" name="usuario" type="EAID_USR_1"/>
                    </packagedElement>
                  </uml:Model>
                  <xmi:Extension extender="Enterprise Architect" extenderID="6.5">
                    <connectors>
                      <connector xmi:idref="EAID_CONN_1" name="realiza">
                        <source xmi:idref="EAID_USR_1">
                          <model type="Class" name="Usuario"/>
                          <role name="cliente"/>
                          <type multiplicity="1..*" aggregation="none"/>
                        </source>
                        <target xmi:idref="EAID_PED_1">
                          <model type="Class" name="Pedido"/>
                          <role name="orden"/>
                          <type multiplicity="0..1" aggregation="none"/>
                        </target>
                        <properties ea_type="Association" direction="Source -> Destination"/>
                      </connector>
                    </connectors>
                  </xmi:Extension>
                </xmi:XMI>
                """;

        var analisis = analizador.analizar(xmi.getBytes(StandardCharsets.UTF_8), UUID.randomUUID());

        assertThat(analisis.clases()).hasSize(2);
        assertThat(analisis.relaciones()).hasSize(1);
        var rel = analisis.relaciones().get(0);
        assertThat(rel.nombre()).isEqualTo("realiza");
        assertThat(rel.tipo()).isEqualTo("ASOCIACION");
        assertThat(rel.rolOrigen()).isEqualTo("cliente");
        assertThat(rel.rolDestino()).isEqualTo("orden");
        assertThat(rel.minimoOrigen()).isEqualTo(1);
        assertThat(rel.maximoOrigen()).isNull(); // 1..* -> max es null (ilimitado)
        assertThat(rel.minimoDestino()).isEqualTo(0);
        assertThat(rel.maximoDestino()).isEqualTo(1); // 0..1
    }
}
