package bo.edu.uml.generacion;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.generacion.dto.GeneracionDto.Dependencia;
import bo.edu.uml.generacion.dto.GeneracionDto.MapeoTipo;
import bo.edu.uml.generacion.servicio.MotorGeneracionSpringBoot;
import bo.edu.uml.generacion.servicio.MotorGeneracionSpringBoot.OpcionesGeneracion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Pruebas unitarias del motor de generación Spring Boot sin depender de Spring ni PostgreSQL. */
class GeneracionTest {

    private final MotorGeneracionSpringBoot motor = new MotorGeneracionSpringBoot();

    private Map<String, Object> clase(String id, String nombre, boolean persistente) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nombre", nombre);
        m.put("es_persistente", persistente);
        m.put("es_abstracto", false);
        m.put("visibilidad", "PUBLICO");
        m.put("id_paquete", null);
        return m;
    }

    private Map<String, Object> atributo(
            String id, String idClase, String nombre, String tipo, boolean esId, boolean esUnico) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("id_clase", idClase);
        m.put("nombre", nombre);
        m.put("tipo_uml", tipo);
        m.put("es_identificador", esId);
        m.put("es_unico", esUnico);
        m.put("es_estatico", false);
        m.put("multiplicidad_minima", 1);
        m.put("multiplicidad_maxima", 1);
        m.put("orden", 1);
        return m;
    }

    private Map<String, String> leerZip(byte[] zip) throws Exception {
        Map<String, String> entradas = new LinkedHashMap<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entradas.put(
                        entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entradas;
    }

    @Test
    void zipContienePomXml() throws Exception {
        var clases = List.of(clase("c1", "Persona", true));
        byte[] zip =
                motor.generar(
                        "com.test",
                        "mi-sistema",
                        "1.0.0",
                        "com.test.sistema",
                        clases,
                        List.of(),
                        List.of());

        var archivos = leerZip(zip);
        assertThat(archivos).containsKey("mi-sistema/pom.xml");

        String pom = archivos.get("mi-sistema/pom.xml");
        assertThat(pom).contains("<groupId>com.test</groupId>");
        assertThat(pom).contains("<artifactId>mi-sistema</artifactId>");
        assertThat(pom).contains("spring-boot-starter-data-jpa");
        assertThat(pom).contains("postgresql");
        assertThat(pom).contains("springdoc-openapi-starter-webmvc-ui");
        assertThat(pom).contains("<java.version>21</java.version>");
    }

    @Test
    void generaEntidadJpa() throws Exception {
        String idClase = UUID.randomUUID().toString();
        var clases = List.of(clase(idClase, "Producto", true));
        var atributos =
                List.of(
                        atributo(
                                UUID.randomUUID().toString(),
                                idClase,
                                "nombre",
                                "String",
                                false,
                                false),
                        atributo(
                                UUID.randomUUID().toString(),
                                idClase,
                                "precio",
                                "Decimal",
                                false,
                                false));

        byte[] zip =
                motor.generar(
                        "com.test", "tienda", "1.0.0", "com.test", clases, atributos, List.of());

        var archivos = leerZip(zip);
        String entidadKey =
                archivos.keySet().stream()
                        .filter(k -> k.contains("modelo/Producto.java"))
                        .findFirst()
                        .orElse(null);
        assertThat(entidadKey).isNotNull();

        String entidad = archivos.get(entidadKey);
        assertThat(entidad).contains("@Entity");
        assertThat(entidad).contains("@Table");
        assertThat(entidad).contains("@Id");
        assertThat(entidad).contains("@GeneratedValue");
        assertThat(entidad).contains("private String nombre");
        assertThat(entidad).contains("private Double precio");
    }

    @Test
    void generaRepositorioJpa() throws Exception {
        var clases = List.of(clase("c1", "Cliente", true));
        byte[] zip =
                motor.generar("com.test", "crm", "1.0.0", "com.test", clases, List.of(), List.of());

        var archivos = leerZip(zip);
        String repoKey =
                archivos.keySet().stream()
                        .filter(k -> k.contains("repositorio/ClienteRepositorio.java"))
                        .findFirst()
                        .orElse(null);
        assertThat(repoKey).isNotNull();

        String repo = archivos.get(repoKey);
        assertThat(repo).contains("JpaRepository<Cliente, Long>");
        assertThat(repo).contains("@Repository");
    }

    @Test
    void generaControladorRestConCrud() throws Exception {
        var clases = List.of(clase("c1", "Pedido", true));
        byte[] zip =
                motor.generar(
                        "com.test", "ventas", "1.0.0", "com.test", clases, List.of(), List.of());

        var archivos = leerZip(zip);
        String ctrlKey =
                archivos.keySet().stream()
                        .filter(k -> k.contains("controlador/PedidoControlador.java"))
                        .findFirst()
                        .orElse(null);
        assertThat(ctrlKey).isNotNull();

        String ctrl = archivos.get(ctrlKey);
        assertThat(ctrl).contains("@RestController");
        assertThat(ctrl).contains("@RequestMapping");
        assertThat(ctrl).contains("@GetMapping");
        assertThat(ctrl).contains("@PostMapping");
        assertThat(ctrl).contains("@PutMapping");
        assertThat(ctrl).contains("@DeleteMapping");
    }

    @Test
    void generaDtosComoRecords() throws Exception {
        String idClase = UUID.randomUUID().toString();
        var clases = List.of(clase(idClase, "Factura", true));
        var atributos =
                List.of(
                        atributo(
                                UUID.randomUUID().toString(),
                                idClase,
                                "numero",
                                "Integer",
                                false,
                                true));

        byte[] zip =
                motor.generar(
                        "com.test", "contable", "1.0.0", "com.test", clases, atributos, List.of());

        var archivos = leerZip(zip);
        String dtoKey =
                archivos.keySet().stream()
                        .filter(k -> k.contains("dto/FacturaDto.java"))
                        .findFirst()
                        .orElse(null);
        assertThat(dtoKey).isNotNull();

        String dto = archivos.get(dtoKey);
        assertThat(dto).contains("record Respuesta");
        assertThat(dto).contains("record Solicitud");
        assertThat(dto).contains("Integer numero");
    }

    @Test
    void generaSchemaSqlConDdl() throws Exception {
        String idClase = UUID.randomUUID().toString();
        var clases = List.of(clase(idClase, "Categoria", true));
        var atributos =
                List.of(
                        atributo(
                                UUID.randomUUID().toString(),
                                idClase,
                                "nombre",
                                "Texto",
                                false,
                                false));

        byte[] zip =
                motor.generar(
                        "com.test", "catalogo", "1.0.0", "com.test", clases, atributos, List.of());

        var archivos = leerZip(zip);
        String sqlKey =
                archivos.keySet().stream()
                        .filter(k -> k.endsWith("schema.sql"))
                        .findFirst()
                        .orElse(null);
        assertThat(sqlKey).isNotNull();

        String sql = archivos.get(sqlKey);
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS");
        assertThat(sql).contains("categoria");
        assertThat(sql).contains("BIGSERIAL PRIMARY KEY");
        assertThat(sql).contains("nombre");
    }

    @Test
    void noGeneraCodigoParaClasesNoPersistentes() throws Exception {
        String idP = UUID.randomUUID().toString();
        String idNP = UUID.randomUUID().toString();
        var clases = List.of(clase(idP, "Persistente", true), clase(idNP, "NoPersistente", false));

        byte[] zip =
                motor.generar("com.test", "app", "1.0.0", "com.test", clases, List.of(), List.of());

        var archivos = leerZip(zip);
        assertThat(archivos.keySet().stream().anyMatch(k -> k.contains("Persistente.java")))
                .isTrue();
        assertThat(archivos.keySet().stream().anyMatch(k -> k.contains("NoPersistente.java")))
                .isFalse();
    }

    @Test
    void generaApplicationProperties() throws Exception {
        var clases = List.of(clase("c1", "Entidad", true));
        byte[] zip =
                motor.generar(
                        "com.test", "myapp", "1.0.0", "com.test", clases, List.of(), List.of());

        var archivos = leerZip(zip);
        String propsKey =
                archivos.keySet().stream()
                        .filter(k -> k.endsWith("application.properties"))
                        .findFirst()
                        .orElse(null);
        assertThat(propsKey).isNotNull();

        String props = archivos.get(propsKey);
        assertThat(props).contains("spring.datasource.url");
        assertThat(props).contains("postgresql");
        assertThat(props).contains("springdoc.swagger-ui.path");
        assertThat(props).contains("spring.jpa.hibernate.ddl-auto");
    }

    @Test
    void generaConfiguracionOpenApi() throws Exception {
        var clases = List.of(clase("c1", "Entidad", true));
        byte[] zip =
                motor.generar(
                        "com.test",
                        "mi-app",
                        "1.0.0",
                        "com.test.app",
                        clases,
                        List.of(),
                        List.of());

        var archivos = leerZip(zip);
        String configKey =
                archivos.keySet().stream()
                        .filter(k -> k.endsWith("OpenApiConfig.java"))
                        .findFirst()
                        .orElse(null);
        assertThat(configKey).isNotNull();

        String config = archivos.get(configKey);
        assertThat(config).contains("@Configuration");
        assertThat(config).contains("OpenAPI");
        assertThat(config).contains("customOpenAPI");
        assertThat(config).contains("mi-app API");
    }

    @Test
    void configuracionAvanzadaRespetaCapasDependenciasMapeosYMigracion() throws Exception {
        String idClase = UUID.randomUUID().toString();
        Map<String, Object> clase = clase(idClase, "Pago", true);
        clase.put("nombre_tabla", "pagos_auditados");
        clase.put("exponer_rest", false);
        var atributo =
                atributo(UUID.randomUUID().toString(), idClase, "monto", "Dinero", false, false);
        var opciones =
                new OpcionesGeneracion(
                        "17",
                        "3.5.16",
                        "FLYWAY",
                        true,
                        false,
                        false,
                        false,
                        false,
                        List.of(
                                new Dependencia(
                                        "org.springframework.boot",
                                        "spring-boot-starter-data-jpa",
                                        null,
                                        "COMPILACION",
                                        true),
                                new Dependencia(
                                        "org.postgresql", "postgresql", null, "EJECUCION", true)),
                        List.of(new MapeoTipo("Dinero", "BigDecimal", "NUMERIC(19,2)", null)));

        var resultado =
                motor.generar(
                        "bo.edu",
                        "pagos",
                        "2.0.0",
                        "bo.edu.pagos",
                        opciones,
                        List.of(clase),
                        List.of(atributo),
                        List.of(),
                        List.of(),
                        List.of());
        var archivos = leerZip(resultado.contenido());

        assertThat(archivos).containsKey("pagos/src/main/resources/db/migration/V1__schema.sql");
        assertThat(archivos.keySet())
                .noneMatch(k -> k.contains("controlador/") || k.contains("dto/"));
        assertThat(archivos.get("pagos/pom.xml")).contains("<java.version>17</java.version>");
        assertThat(archivos.get("pagos/pom.xml")).doesNotContain("springdoc-openapi");
        assertThat(archivos.get("pagos/src/main/resources/db/migration/V1__schema.sql"))
                .contains("pagos_auditados")
                .contains("NUMERIC(19,2)");
        assertThat(
                        archivos.entrySet().stream()
                                .filter(e -> e.getKey().endsWith("Pago.java"))
                                .findFirst()
                                .orElseThrow()
                                .getValue())
                .contains("private BigDecimal monto");
    }

    @Test
    void generaServicioConOperacionesUmlSeleccionadas() throws Exception {
        String idClase = UUID.randomUUID().toString();
        String idOperacion = UUID.randomUUID().toString();
        Map<String, Object> operacion = new LinkedHashMap<>();
        operacion.put("id", idOperacion);
        operacion.put("id_clase", idClase);
        operacion.put("nombre", "calcularTotal");
        operacion.put("tipo_retorno", "Double");
        Map<String, Object> parametro = new LinkedHashMap<>();
        parametro.put("id_operacion", idOperacion);
        parametro.put("nombre", "descuento");
        parametro.put("tipo_uml", "Double");
        var opciones =
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

        var resultado =
                motor.generar(
                        "bo.edu",
                        "ventas",
                        "1.0.0",
                        "bo.edu.ventas",
                        opciones,
                        List.of(clase(idClase, "Pedido", true)),
                        List.of(),
                        List.of(),
                        List.of(operacion),
                        List.of(parametro));
        var servicio =
                leerZip(resultado.contenido()).entrySet().stream()
                        .filter(e -> e.getKey().endsWith("PedidoServicio.java"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        assertThat(servicio).contains("Double calcularTotal(Double descuento)");
    }
}
