package bo.edu.uml.xmi;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.modelo.SemanticaUml;
import bo.edu.uml.xmi.servicio.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.xml.parsers.DocumentBuilderFactory;

/** Aceptación del subconjunto, no certificación del metamodelo completo ni prueba de EA. */
class Uml251Test {
    final GeneradorXmi251Service exporter = new GeneradorXmi251Service();
    final AnalizadorXmi21Service parser = new AnalizadorXmi21Service();
    final String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();

    Map<String, Object> clase(String id, String nombre) {
        return new LinkedHashMap<>(
                Map.of(
                        "id",
                        id,
                        "nombre",
                        nombre,
                        "es_persistente",
                        false,
                        "visibilidad",
                        "PUBLICO",
                        "es_abstracto",
                        false));
    }

    Map<String, Object> relacion(String tipo, String direccion) {
        var r =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id",
                                UUID.randomUUID().toString(),
                                "id_clase_origen",
                                a,
                                "id_clase_destino",
                                b,
                                "tipo_relacion",
                                tipo,
                                "direccion",
                                direccion,
                                "rol_origen",
                                "propietario",
                                "rol_destino",
                                "partes",
                                "minimo_origen",
                                0,
                                "maximo_origen",
                                1));
        r.put("minimo_destino", 2);
        r.put("maximo_destino", null);
        return r;
    }

    byte[] export(
            List<Map<String, Object>> attrs,
            List<Map<String, Object>> ops,
            List<Map<String, Object>> params,
            List<Map<String, Object>> rels,
            boolean ea) {
        return exporter.generar(
                "Modelo",
                List.of(),
                List.of(clase(a, "Conjunto"), clase(b, "Parte")),
                attrs,
                ops,
                params,
                rels,
                List.of(Map.of("id_clase", a, "x", 123, "y", 234, "ancho", 240, "alto", 160)),
                List.of(),
                ea);
    }

    String xml(String elements) {
        return "<xmi:XMI xmlns:xmi='http://www.omg.org/spec/XMI/20131001'"
                   + " xmlns:uml='http://www.omg.org/spec/UML/20161101'"
                   + " xmi:version='2.5.1'><uml:Model xmi:id='m' name='M'>"
                + elements
                + "</uml:Model></xmi:XMI>";
    }

    AnalizadorXmi21Service.Analisis parse(String text) {
        return parser.analizar(text.getBytes(StandardCharsets.UTF_8), UUID.randomUUID());
    }

    @Test
    void referenciasDeclaradasYTiposDeClaseSeResuelvenSinRecursosExternos() throws Exception {
        var attr =
                Map.<String, Object>of(
                        "id",
                        UUID.randomUUID(),
                        "id_clase",
                        a,
                        "nombre",
                        "parte",
                        "tipo_uml",
                        "Parte");
        byte[] bytes =
                export(
                        List.of(attr),
                        List.of(),
                        List.of(),
                        List.of(relacion("COMPOSICION", "ORIGEN_A_DESTINO")),
                        false);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        assertThat(doc.getDocumentElement().getAttributeNS(GeneradorXmi251Service.XMI, "version"))
                .isEqualTo("2.5.1");
        Set<String> ids = new HashSet<>();
        NodeList elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            String id = e.getAttributeNS(GeneradorXmi251Service.XMI, "id");
            if (!id.isBlank()) assertThat(ids.add(id)).isTrue();
        }
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            for (String key :
                    List.of(
                            "type",
                            "general",
                            "client",
                            "supplier",
                            "memberEnd",
                            "navigableOwnedEnd",
                            "association"))
                if (e.hasAttribute(key))
                    for (String ref : e.getAttribute(key).split("\\s+"))
                        assertThat(ids).contains(ref);
        }
        var result = parser.analizar(bytes, UUID.randomUUID());
        assertThat(result.atributos().getFirst().tipo()).isEqualTo("Parte");
        assertThat(result.relaciones().getFirst().tipo()).isEqualTo("COMPOSICION");
        assertThat(result.relaciones().getFirst().maximoDestino()).isNull();
        assertThat(result.relaciones().getFirst().minimoDestino()).isEqualTo(2);
    }

    @Test
    void navegabilidadEnCuatroEstadosConRolesYComposicion() {
        for (String dir :
                List.of("SIN_DIRECCION", "ORIGEN_A_DESTINO", "DESTINO_A_ORIGEN", "BIDIRECCIONAL")) {
            var r =
                    parser.analizar(
                                    export(
                                            List.of(),
                                            List.of(),
                                            List.of(),
                                            List.of(relacion("COMPOSICION", dir)),
                                            false),
                                    UUID.randomUUID())
                            .relaciones()
                            .getFirst();
            assertThat(r.direccion()).isEqualTo(dir);
            assertThat(r.rolOrigen()).isEqualTo("propietario");
            assertThat(r.rolDestino()).isEqualTo("partes");
            assertThat(r.maximoOrigen()).isEqualTo(1);
        }
    }

    @Test
    void metadatosDeProductoYModificadoresNoSeConfundenConUnicidadSql() {
        var attr =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "id",
                                UUID.randomUUID(),
                                "id_clase",
                                a,
                                "nombre",
                                "nombres",
                                "tipo_uml",
                                "String",
                                "es_ordenado",
                                true,
                                "coleccion_unica",
                                false,
                                "es_unico",
                                true,
                                "multiplicidad_minima",
                                0,
                                "valor_predeterminado",
                                "Hola"));
        attr.put("multiplicidad_maxima", null);
        attr.put("restricciones_json", "{\"maxLength\":40}");
        var result =
                parser.analizar(
                        export(List.of(attr), List.of(), List.of(), List.of(), false),
                        UUID.randomUUID());
        var read = result.atributos().getFirst();
        assertThat(read.ordenado()).isTrue();
        assertThat(read.coleccionUnica()).isFalse();
        assertThat(read.unicoSql()).isTrue();
        assertThat(read.predeterminado()).isEqualTo("Hola");
        assertThat(read.restricciones()).contains("maxLength");
        assertThat(result.clases()).allMatch(c -> !c.persistente());
    }

    @Test
    void retornaUnSoloParametroYVoidNoCreaUnTipo() {
        var op =
                Map.<String, Object>of(
                        "id",
                        UUID.randomUUID(),
                        "id_clase",
                        a,
                        "nombre",
                        "guardar",
                        "tipo_retorno",
                        "void");
        String text =
                new String(
                        export(List.of(), List.of(op), List.of(), List.of(), false),
                        StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("direction=\"return\"").doesNotContain("name=\"void\"");
    }

    @Test
    void parametrosOpcionalesDireccionesYValoresSeConservan() {
        String op = UUID.randomUUID().toString();
        var operations =
                List.<Map<String, Object>>of(
                        Map.of(
                                "id",
                                op,
                                "id_clase",
                                a,
                                "nombre",
                                "calcular",
                                "tipo_retorno",
                                "Integer"));
        var params =
                List.<Map<String, Object>>of(
                        Map.of(
                                "id",
                                UUID.randomUUID(),
                                "id_operacion",
                                op,
                                "nombre",
                                "cantidad",
                                "tipo_uml",
                                "Integer",
                                "direccion",
                                "ENTRADA_SALIDA",
                                "es_obligatorio",
                                false,
                                "valor_predeterminado",
                                "4"));
        var result =
                parser.analizar(
                                export(List.of(), operations, params, List.of(), false),
                                UUID.randomUUID())
                        .operaciones()
                        .getFirst();
        assertThat(result.retorno()).isEqualTo("Integer");
        assertThat(result.parametros()).hasSize(2);
        assertThat(result.parametros().getFirst().direccion()).isEqualTo("ENTRADA_SALIDA");
        assertThat(result.parametros().getFirst().obligatorio()).isFalse();
        assertThat(result.parametros().getFirst().predeterminado()).isEqualTo("4");
    }

    @Test
    void posicionEaSeRecuperaSinConfundirlaConUmlDi() {
        var result =
                parser.analizar(
                        export(List.of(), List.of(), List.of(), List.of(), true),
                        UUID.randomUUID());
        var c =
                result.clases().stream()
                        .filter(v -> v.nombre().equals("Conjunto"))
                        .findFirst()
                        .orElseThrow();
        assertThat(c.x()).isEqualTo(123);
        assertThat(c.y()).isEqualTo(234);
    }

    @Test
    void noColapsaAsociacionesParalelas() {
        var result =
                parser.analizar(
                        export(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(
                                        relacion("ASOCIACION", "ORIGEN_A_DESTINO"),
                                        relacion("ASOCIACION", "DESTINO_A_ORIGEN")),
                                false),
                        UUID.randomUUID());
        assertThat(result.relaciones()).hasSize(2);
    }

    @Test
    void cardinalidadNoEnteraYReferenciasDuplicadasSonRechazadas() {
        assertThatThrownBy(
                        () ->
                                parse(
                                        xml(
                                                "<packagedElement xmi:type='uml:Class' xmi:id='a'"
                                                    + " name='A'><ownedAttribute xmi:id='at'"
                                                    + " name='x' type='String'><upperValue"
                                                    + " value='muchos'/></ownedAttribute></packagedElement>")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("multiplicidad");
        assertThatThrownBy(
                        () ->
                                parse(
                                        xml(
                                                "<packagedElement xmi:type='uml:Class' xmi:id='a'"
                                                    + " name='A'/><packagedElement"
                                                    + " xmi:type='uml:Class' xmi:id='a'"
                                                    + " name='B'/>")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("duplicado");
    }

    @Test
    void limitesFinitosYLiteralCeroSinAtributoNoSeCambianAInfinito() {
        var result =
                parse(
                        xml(
                                "<packagedElement xmi:type='uml:Class' xmi:id='a'"
                                    + " name='A'><ownedAttribute xmi:id='at' name='x'"
                                    + " type='String'><lowerValue"
                                    + " xmi:type='uml:LiteralInteger'/><upperValue"
                                    + " value='8'/></ownedAttribute></packagedElement>"));
        assertThat(result.atributos().getFirst().minimo()).isZero();
        assertThat(result.atributos().getFirst().maximo()).isEqualTo(8);
    }

    @Test
    void reportaInterfacesRealizacionesYNariasEnLugarDeConvertirlas() {
        var result =
                parse(
                        xml(
                                "<packagedElement xmi:type='uml:Class' xmi:id='a'"
                                    + " name='A'/><packagedElement xmi:type='uml:Interface'"
                                    + " xmi:id='i' name='I'/><packagedElement"
                                    + " xmi:type='uml:Realization' xmi:id='r' client='a'"
                                    + " supplier='i'/><packagedElement xmi:type='uml:Association'"
                                    + " xmi:id='n'><ownedEnd type='a'/><ownedEnd"
                                    + " type='a'/><ownedEnd type='a'/></packagedElement>"));
        assertThat(result.clases()).hasSize(1);
        assertThat(result.relaciones()).isEmpty();
        assertThat(result.advertencias())
                .anyMatch(v -> v.contains("Interface"))
                .anyMatch(v -> v.contains("Realization"))
                .anyMatch(v -> v.contains("n-arias"));
    }

    @Test
    void tipoNoResueltoNoSeExportaComoReferenciaColgante() {
        assertThatThrownBy(
                        () ->
                                export(
                                        List.of(
                                                Map.of(
                                                        "id",
                                                        UUID.randomUUID(),
                                                        "id_clase",
                                                        a,
                                                        "nombre",
                                                        "x",
                                                        "tipo_uml",
                                                        "NoExiste")),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tipo no resuelto");
    }

    @Test
    void reglasDeDireccionYRetornoSeCompartenConEdicionEia() throws Exception {
        assertThatThrownBy(() -> SemanticaUml.direccion("GENERALIZACION", "BIDIRECCIONAL"))
                .isInstanceOf(ApiException.class);
        var json = new ObjectMapper();
        var params = json.readTree("[{\"direccion\":\"RETORNO\",\"tipoUml\":\"String\"}]");
        assertThat(SemanticaUml.errorRetorno("Integer", params, "tipoUml")).contains("contradice");
        assertThat(SemanticaUml.errorRetorno("String", params, "tipoUml")).isNull();
    }

    @Test
    void composicionHistoricaInvalidaNoSeExportaComoPerfilModernoValido() {
        var r = relacion("COMPOSICION", "SIN_DIRECCION");
        r.put("maximo_origen", null);
        assertThatThrownBy(() -> export(List.of(), List.of(), List.of(), List.of(r), false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Composición inválida");
    }
}
