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

    @Test
    void identificadorTecnicoNoDuplicaUnAtributoIdDelModelo() throws Exception {
        String c = UUID.randomUUID().toString();
        var clases = List.of(clase(c, "Cliente", true));
        var atributos = List.of(
                atributo(UUID.randomUUID().toString(), c, "id", "String", false, false),
                atributo(UUID.randomUUID().toString(), c, "idBackend", "String", false, false));
        var archivos = leerZip(motor.generar("bo.test", "ejemplo", "1.0.0", "bo.test", clases, atributos, List.of()));
        assertThat(archivos.get("ejemplo/src/main/java/bo/test/modelo/Cliente.java"))
                .contains("private Long idBackend2;", "private String id;", "private String idBackend;");
        assertThat(archivos.get("ejemplo/src/main/resources/schema.sql"))
                .contains("id_backend2 BIGSERIAL PRIMARY KEY", "id VARCHAR(255)", "id_backend VARCHAR(255)");
    }

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

    @Test
    void generaReadmeYApplicationTestsEnZip() throws Exception {
        var clases = List.of(clase("c1", "Usuario", true));
        byte[] zip =
                motor.generar(
                        "com.test", "auth", "1.0.0", "com.test.auth", clases, List.of(), List.of());

        var archivos = leerZip(zip);
        assertThat(archivos).containsKey("auth/README.md");
        assertThat(archivos.get("auth/README.md"))
                .contains("Proyecto Spring Boot generado por Plataforma UML Colaborativa")
                .contains("docker compose up -d --build --wait")
                .contains("swagger-ui.html");

        assertThat(archivos)
                .containsKey("auth/src/test/java/com/test/auth/AuthApplicationTests.java");
        assertThat(archivos.get("auth/src/test/java/com/test/auth/AuthApplicationTests.java"))
                .contains("@SpringBootTest")
                .contains("void contextLoads()");
    }

    @Test
    void generaClavesForaneasYRelacionesEnSchemaSql() throws Exception {
        String idPedido = UUID.randomUUID().toString();
        String idCliente = UUID.randomUUID().toString();
        String idProducto = UUID.randomUUID().toString();

        var clases =
                List.of(
                        clase(idPedido, "Pedido", true),
                        clase(idCliente, "Cliente", true),
                        clase(idProducto, "Producto", true));

        // Pedido -> Cliente (ManyToOne: maxDest=1, maxOrig=*)
        Map<String, Object> relCliente = new LinkedHashMap<>();
        relCliente.put("id", UUID.randomUUID().toString());
        relCliente.put("id_clase_origen", idPedido);
        relCliente.put("id_clase_destino", idCliente);
        relCliente.put("tipo_relacion", "ASOCIACION");
        relCliente.put("minimo_origen", 0);
        relCliente.put("maximo_origen", -1);
        relCliente.put("minimo_destino", 1);
        relCliente.put("maximo_destino", 1);

        // Pedido -> Producto (ManyToMany: maxOrig=*, maxDest=*)
        Map<String, Object> relProductos = new LinkedHashMap<>();
        relProductos.put("id", UUID.randomUUID().toString());
        relProductos.put("id_clase_origen", idPedido);
        relProductos.put("id_clase_destino", idProducto);
        relProductos.put("tipo_relacion", "ASOCIACION");
        relProductos.put("minimo_origen", 0);
        relProductos.put("maximo_origen", -1);
        relProductos.put("minimo_destino", 0);
        relProductos.put("maximo_destino", -1);

        byte[] zip =
                motor.generar(
                        "com.test",
                        "ventas",
                        "1.0.0",
                        "com.test.ventas",
                        clases,
                        List.of(),
                        List.of(relCliente, relProductos));

        var archivos = leerZip(zip);
        String sql = archivos.get("ventas/src/main/resources/schema.sql");
        assertThat(sql).contains("cliente_id BIGINT");
        assertThat(sql)
                .contains(
                        "ALTER TABLE pedido ADD CONSTRAINT fk_pedido_cliente FOREIGN KEY"
                                + " (cliente_id) REFERENCES cliente(id);");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS pedido_producto");
        assertThat(sql).contains("PRIMARY KEY (pedido_id, producto_id)");

        String pedidoJava =
                archivos.entrySet().stream()
                        .filter(e -> e.getKey().endsWith("Pedido.java"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        assertThat(pedidoJava).contains("@ManyToOne");
        assertThat(pedidoJava).contains("@JoinColumn(name = \"cliente_id\", nullable = false)");
        assertThat(pedidoJava).contains("public Cliente getCliente()");
        assertThat(pedidoJava).contains("public void setCliente(Cliente cliente)");
        assertThat(pedidoJava).contains("@ManyToMany");
        assertThat(pedidoJava).contains("@JoinTable");
        assertThat(pedidoJava).contains("public List<Producto> getProductos()");
    }

    @Test
    void controladorUsaDtosYExponeOperacionesUml() throws Exception {
        String idClase = UUID.randomUUID().toString();
        String idOperacion = UUID.randomUUID().toString();
        Map<String, Object> operacion = new LinkedHashMap<>();
        operacion.put("id", idOperacion);
        operacion.put("id_clase", idClase);
        operacion.put("nombre", "confirmar");
        operacion.put("tipo_retorno", "void");

        var opciones =
                new OpcionesGeneracion(
                        "21",
                        "3.5.16",
                        "NINGUNO",
                        true,
                        true,
                        true,
                        true,
                        true,
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
                        "ordenes",
                        "1.0.0",
                        "bo.edu.ordenes",
                        opciones,
                        List.of(clase(idClase, "Orden", true)),
                        List.of(),
                        List.of(),
                        List.of(operacion),
                        List.of());

        var archivos = leerZip(resultado.contenido());
        String ctrl =
                archivos.entrySet().stream()
                        .filter(e -> e.getKey().endsWith("OrdenControlador.java"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();

        assertThat(ctrl).contains("OrdenDto.Respuesta");
        assertThat(ctrl).contains("OrdenDto.Solicitud");
        assertThat(ctrl).contains("OrdenServicio ordenServicio");
        assertThat(ctrl).contains("@PostMapping(\"/{id}/confirmar\")");
        assertThat(ctrl).contains("ordenServicio.confirmar()");

        String dto =
                archivos.entrySet().stream()
                        .filter(e -> e.getKey().endsWith("OrdenDto.java"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        assertThat(dto).contains("public static Respuesta deEntidad(Orden entidad)");
        assertThat(dto)
                .contains("public static Orden aEntidad(Solicitud dto, EntityManager manager)");
    }

    @Test
    void generaArchivosDockerYRutasEjecucion() throws Exception {
        var clases = List.of(clase("c1", "Reserva", true));
        byte[] zip =
                motor.generar(
                        "bo.barberia",
                        "barberia-api",
                        "1.0.0",
                        "bo.barberia.api",
                        clases,
                        List.of(),
                        List.of());

        var archivos = leerZip(zip);
        assertThat(archivos).containsKey("barberia-api/docker-compose.yml");
        assertThat(archivos).containsKey("barberia-api/Dockerfile");
        assertThat(archivos).containsKey("barberia-api/run.bat");
        assertThat(archivos).containsKey("barberia-api/run.ps1");

        String compose = archivos.get("barberia-api/docker-compose.yml");
        assertThat(compose).contains("postgres:16-alpine");
        assertThat(compose).doesNotContain("container_name:");
        assertThat(compose).containsPattern("pgdata_[0-9a-f]{16}:/var/lib/postgresql/data");
        assertThat(compose).doesNotContain("${DB_PORT:-5434}:5432");
        assertThat(compose).contains("POSTGRES_DB: barberia-api");
        assertThat(compose).contains("POSTGRES_PASSWORD: postgrespassword");
        assertThat(compose).contains("${APP_PORT:-18081}:8081");
        assertThat(compose).contains("wget -q -O /dev/null http://127.0.0.1:8081/api-docs");

        String dockerfile = archivos.get("barberia-api/Dockerfile");
        assertThat(dockerfile).contains("maven:3.9-eclipse-temurin-21-alpine");
        assertThat(dockerfile).contains("mvn -q -B -Dmaven.test.skip=true package").doesNotContain("dependency:go-offline");
        assertThat(dockerfile).contains("eclipse-temurin:21-jre-alpine");
        assertThat(dockerfile).contains("EXPOSE 8081");

        String prop = archivos.get("barberia-api/src/main/resources/application.properties");
        assertThat(prop).contains("jdbc:postgresql://localhost:5434/barberia-api");
        assertThat(prop)
                .contains(
                        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:postgrespassword}");

        String readme = archivos.get("barberia-api/README.md");
        assertThat(readme).contains("docker compose up -d --build --wait");
        assertThat(archivos.get("barberia-api/run.bat"))
                .contains("docker compose up -d --build --wait")
                .doesNotContain("mvn spring-boot:run");
        assertThat(archivos.get("barberia-api/run.ps1"))
                .contains("docker compose up -d --build --wait")
                .doesNotContain("mvn spring-boot:run");
        assertThat(readme).contains("Acoplamiento con Frontend Móvil Flutter");
        assertThat(readme).contains("10.0.2.2:18081");
    }

    @Test
    void esquemaNuevoUsaOtroVolumenSinDesactivarValidacionFlyway() throws Exception {
        var opciones = new OpcionesGeneracion(
                "21", "3.5.16", "FLYWAY", true, false, true, true, true,
                List.of(
                        new Dependencia("org.springframework.boot", "spring-boot-starter-data-jpa", null, "COMPILACION", true),
                        new Dependencia("org.postgresql", "postgresql", null, "EJECUCION", true)),
                List.of());
        var clase = clase("c1", "Libro", true);
        var atributosBase = List.of(atributo("a1", "c1", "titulo", "String", false, false));
        var atributosNuevos = List.of(
                atributo("a1", "c1", "titulo", "String", false, false),
                atributo("a2", "c1", "isbn", "String", false, false));

        var base = leerZip(motor.generar("bo.edu", "biblioteca", "1.0", "bo.edu.biblioteca",
                opciones, List.of(clase), atributosBase, List.of(), List.of(), List.of()).contenido());
        var nuevo = leerZip(motor.generar("bo.edu", "biblioteca", "1.0", "bo.edu.biblioteca",
                opciones, List.of(clase), atributosNuevos, List.of(), List.of(), List.of()).contenido());
        String composeBase = base.get("biblioteca/docker-compose.yml");
        String composeNuevo = nuevo.get("biblioteca/docker-compose.yml");
        assertThat(composeBase).isNotEqualTo(composeNuevo);
        assertThat(composeBase).containsPattern("pgdata_[0-9a-f]{16}:/var/lib/postgresql/data");
        assertThat(composeNuevo).containsPattern("pgdata_[0-9a-f]{16}:/var/lib/postgresql/data");
        assertThat(nuevo.get("biblioteca/src/main/resources/application.properties"))
                .contains("spring.flyway.enabled=true")
                .doesNotContain("validate-on-migrate=false");
    }

    @Test
    void generaCorsConfigParaFlutter() throws Exception {
        var clases = List.of(clase("c1", "Cliente", true));
        byte[] zip =
                motor.generar(
                        "bo.barberia",
                        "barberia-api",
                        "1.0.0",
                        "bo.barberia.api",
                        clases,
                        List.of(),
                        List.of());

        var archivos = leerZip(zip);
        assertThat(archivos)
                .containsKey("barberia-api/src/main/java/bo/barberia/api/config/CorsConfig.java");

        String cors =
                archivos.get("barberia-api/src/main/java/bo/barberia/api/config/CorsConfig.java");
        assertThat(cors).contains("@Configuration");
        assertThat(cors).contains("implements WebMvcConfigurer");
        assertThat(cors).contains("allowedOriginPatterns(\"*\")");
        assertThat(cors)
                .contains(
                        "allowedMethods(\"GET\", \"POST\", \"PUT\", \"DELETE\", \"PATCH\","
                            + " \"OPTIONS\", \"HEAD\")");
    }

    @Test
    void generaPostmanCollectionConCrud() throws Exception {
        String idCliente = UUID.randomUUID().toString();
        var clases = List.of(clase(idCliente, "Cliente", true));
        var atributos =
                List.of(
                        atributo(
                                UUID.randomUUID().toString(),
                                idCliente,
                                "nombre",
                                "Texto",
                                false,
                                false),
                        atributo(
                                UUID.randomUUID().toString(),
                                idCliente,
                                "activo",
                                "Booleano",
                                false,
                                false));

        byte[] zip =
                motor.generar(
                        "bo.test",
                        "sistema-test",
                        "1.0.0",
                        "bo.test.sistema",
                        clases,
                        atributos,
                        List.of());

        var archivos = leerZip(zip);
        assertThat(archivos).containsKey("sistema-test/postman_collection.json");

        String postman = archivos.get("sistema-test/postman_collection.json");
        assertThat(postman).contains("\"name\": \"sistema-test API\"");
        assertThat(postman).contains("\"baseUrl\"");
        assertThat(postman).contains("http://localhost:18081");
        assertThat(postman).contains("3. Listar todos los Cliente");
        assertThat(postman).contains("2. Obtener Cliente por ID");
        assertThat(postman).contains("1. Crear Cliente");
        assertThat(postman).contains("4. Actualizar Cliente");
        assertThat(postman).contains("5. Eliminar Cliente");
        assertThat(postman).contains("{{baseUrl}}/api/clientes");
        assertThat(postman).contains("\\\"nombre\\\": \\\"nombre_ejemplo\\\"");
        assertThat(postman).contains("{{clienteId}}");
        assertThat(postman).contains("pm.collectionVariables.set('clienteId', creado.id)");
        var coleccion = new com.fasterxml.jackson.databind.ObjectMapper().readTree(postman);
        assertThat(coleccion.path("item").get(0).path("item").get(0).path("name").asText())
                .isEqualTo("1. Crear Cliente");
    }

    @Test
    void generaGlobalExceptionHandler() throws Exception {
        var clases = List.of(clase("c1", "Usuario", true));
        byte[] zip =
                motor.generar(
                        "bo.test",
                        "app-test",
                        "1.0.0",
                        "bo.test.app",
                        clases,
                        List.of(),
                        List.of());

        var archivos = leerZip(zip);
        assertThat(archivos)
                .containsKey(
                        "app-test/src/main/java/bo/test/app/config/GlobalExceptionHandler.java");

        String handler =
                archivos.get(
                        "app-test/src/main/java/bo/test/app/config/GlobalExceptionHandler.java");
        assertThat(handler).contains("@RestControllerAdvice");
        assertThat(handler).contains("record ErrorRespuesta");
        assertThat(handler).contains("manejarNoEncontrado(NoSuchElementException");
        assertThat(handler).contains("manejarValidacion(MethodArgumentNotValidException");
        assertThat(handler).contains("manejarIntegridad(DataIntegrityViolationException");
    }

    @Test
    void generaDtosConClavesForaneas() throws Exception {
        String idPedido = UUID.randomUUID().toString();
        String idCliente = UUID.randomUUID().toString();

        var clases = List.of(clase(idPedido, "Pedido", true), clase(idCliente, "Cliente", true));

        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("id", UUID.randomUUID().toString());
        rel.put("id_clase_origen", idPedido);
        rel.put("id_clase_destino", idCliente);
        rel.put("tipo_relacion", "ASOCIACION");
        rel.put("minimo_origen", 0);
        rel.put("maximo_origen", -1);
        rel.put("minimo_destino", 1);
        rel.put("maximo_destino", 1);

        byte[] zip =
                motor.generar(
                        "bo.test",
                        "ventas-test",
                        "1.0.0",
                        "bo.test.ventas",
                        clases,
                        List.of(),
                        List.of(rel));

        var archivos = leerZip(zip);
        String dtoKey =
                archivos.keySet().stream()
                        .filter(k -> k.endsWith("PedidoDto.java"))
                        .findFirst()
                        .orElseThrow();

        String dto = archivos.get(dtoKey);
        assertThat(dto).contains("Long clienteId");
        assertThat(dto)
                .contains("entidad.getCliente() != null ? entidad.getCliente().getId() : null");
        assertThat(dto)
                .contains(
                        "entidad.setCliente(manager.getReference(Cliente.class,"
                            + " dto.clienteId()));");
    }
}
