package bo.edu.uml.generacion;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.uml.generacion.dto.GeneracionDto.Dependencia;
import bo.edu.uml.generacion.servicio.MotorGeneracionSpringBoot;
import bo.edu.uml.generacion.servicio.MotorGeneracionSpringBoot.OpcionesGeneracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

/** Prueba real del ZIP generado. Se activa con UML_RUNTIME_SMOKE=1 y usa PostgreSQL aislado. */
class GeneracionRuntimeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final String BASE = "http://127.0.0.1:8081";

    @Test
    @EnabledIfEnvironmentVariable(named = "UML_RUNTIME_SMOKE", matches = "1")
    void zipGeneradoArrancaYPersisteCrudEnPostgresql() throws Exception {
        Path raiz = Path.of("../..").toAbsolutePath().normalize();
        Path trabajo = Files.createTempDirectory(raiz.resolve(".qa"), "backend-runtime-");
        String nombreBase =
                "uml_gen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int puertoPg =
                Integer.parseInt(System.getenv().getOrDefault("UML_RUNTIME_PG_PORT", "55433"));
        String urlAdmin = "jdbc:postgresql://127.0.0.1:" + puertoPg + "/postgres";
        try (var conexion = DriverManager.getConnection(urlAdmin, "uml_test_owner", "");
                var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombreBase);
        }

        Path proyecto = extraerFixture(trabajo);
        Path logMaven = trabajo.resolve("maven.log");
        Path maven = raiz.resolve(".qa/tools/apache-maven-3.9.9/bin/mvn.cmd");
        String repositorio =
                System.getenv()
                        .getOrDefault(
                                "UML_RUNTIME_MAVEN_REPO",
                                Path.of(System.getProperty("user.home"), ".m2", "repository")
                                        .toString());
        Process compilacion =
                new ProcessBuilder(
                                maven.toString(),
                                "-o",
                                "-B",
                                "-ntp",
                                "-Dmaven.repo.local=" + repositorio,
                                "-Dmaven.test.skip=true",
                                "package")
                        .directory(proyecto.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(logMaven.toFile())
                        .start();
        assertThat(compilacion.waitFor(180, TimeUnit.SECONDS))
                .as("Maven terminó dentro del límite")
                .isTrue();
        assertThat(compilacion.exitValue())
                .as("Compilación del ZIP: %s", Files.readString(logMaven))
                .isZero();

        Path jar = proyecto.resolve("target/smoke-backend-1.0.0.jar");
        String url = "jdbc:postgresql://127.0.0.1:" + puertoPg + "/" + nombreBase;
        Process app = iniciar(jar, url, trabajo.resolve("app-1.log"));
        try {
            esperarArranque(app, trabajo.resolve("app-1.log"));
            assertThat(pedir("GET", "/api-docs", null).statusCode()).isEqualTo(200);

            String claveCreacion = UUID.randomUUID().toString();
            var cliente =
                    pedir(
                            "POST",
                            "/api/clientes",
                            "{\"nombre\":\"Ana\"}",
                            Map.of("X-Idempotency-Key", claveCreacion));
            assertThat(cliente.statusCode()).isEqualTo(201);
            long clienteId = JSON.readTree(cliente.body()).path("id").asLong();
            assertThat(clienteId).isPositive();
            assertThat(cliente.headers().firstValue("Location").orElse(""))
                    .endsWith("/api/clientes/" + clienteId);
            var repetida =
                    pedir(
                            "POST",
                            "/api/clientes",
                            "{\"nombre\":\"Ana\"}",
                            Map.of("X-Idempotency-Key", claveCreacion));
            assertThat(repetida.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(repetida.body()).path("id").asLong()).isEqualTo(clienteId);

            var etiqueta = pedir("POST", "/api/etiquetas", "{\"nombre\":\"Preferente\"}");
            assertThat(etiqueta.statusCode()).isEqualTo(201);
            String etiquetaId = JSON.readTree(etiqueta.body()).path("id").asText();
            assertThat(UUID.fromString(etiquetaId)).isNotNull();

            var reserva =
                    pedir(
                            "POST",
                            "/api/reservas",
                            "{\"inicio\":\"2026-10-02T16:00:00\",\"estado\":\"PENDIENTE\",\"clienteId\":"
                                    + clienteId
                                    + ",\"etiquetasIds\":[\""
                                    + etiquetaId
                                    + "\"]}");
            assertThat(reserva.statusCode()).isEqualTo(201);
            long reservaId = JSON.readTree(reserva.body()).path("id").asLong();
            long versionInicial = JSON.readTree(reserva.body()).path("version").asLong();
            assertThat(JSON.readTree(reserva.body()).path("clienteId").asLong())
                    .isEqualTo(clienteId);
            assertThat(JSON.readTree(reserva.body()).path("etiquetasIds").get(0).asText())
                    .isEqualTo(etiquetaId);

            assertThat(pedir("GET", "/api/clientes/" + clienteId, null).statusCode())
                    .isEqualTo(200);
            assertThat(pedir("GET", "/api/reservas", null).statusCode()).isEqualTo(200);
            String cambio =
                    "{\"inicio\":\"2026-10-02T17:00:00\",\"estado\":\"CONFIRMADA\",\"clienteId\":"
                            + clienteId
                            + ",\"etiquetasIds\":[\""
                            + etiquetaId
                            + "\"]}";
            var actualizada =
                    pedir(
                            "PUT",
                            "/api/reservas/" + reservaId,
                            cambio,
                            Map.of("X-Expected-Version", String.valueOf(versionInicial)));
            assertThat(actualizada.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(actualizada.body()).path("estado").asText())
                    .isEqualTo("CONFIRMADA");
            long versionActual = JSON.readTree(actualizada.body()).path("version").asLong();
            assertThat(versionActual).isGreaterThan(versionInicial);
            assertThat(
                            pedir(
                                            "PUT",
                                            "/api/reservas/" + reservaId,
                                            cambio,
                                            Map.of(
                                                    "X-Expected-Version",
                                                    String.valueOf(versionInicial)))
                                    .statusCode())
                    .isEqualTo(409);

            detener(app);
            app = iniciar(jar, url, trabajo.resolve("app-2.log"));
            esperarArranque(app, trabajo.resolve("app-2.log"));
            assertThat(
                            JSON.readTree(pedir("GET", "/api/reservas/" + reservaId, null).body())
                                    .path("estado")
                                    .asText())
                    .isEqualTo("CONFIRMADA");

            assertThat(
                            pedir(
                                            "DELETE",
                                            "/api/reservas/" + reservaId,
                                            null,
                                            Map.of(
                                                    "X-Expected-Version",
                                                    String.valueOf(versionInicial)))
                                    .statusCode())
                    .isEqualTo(409);
            assertThat(
                            pedir(
                                            "DELETE",
                                            "/api/reservas/" + reservaId,
                                            null,
                                            Map.of(
                                                    "X-Expected-Version",
                                                    String.valueOf(versionActual)))
                                    .statusCode())
                    .isEqualTo(204);
            assertThat(pedir("GET", "/api/reservas/" + reservaId, null).statusCode())
                    .isEqualTo(404);
            assertThat(pedir("DELETE", "/api/etiquetas/" + etiquetaId, null).statusCode())
                    .isEqualTo(204);
            assertThat(pedir("DELETE", "/api/clientes/" + clienteId, null).statusCode())
                    .isEqualTo(204);
            assertThat(pedir("POST", "/api/clientes", "{}").statusCode()).isEqualTo(400);
        } finally {
            detener(app);
        }
    }

    private static Process iniciar(Path jar, String url, Path log) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        return new ProcessBuilder(
                        java.toString(),
                        "-jar",
                        jar.toString(),
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=uml_test_owner",
                        "--spring.datasource.password=")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private static void esperarArranque(Process app, Path log) throws Exception {
        long limite = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < limite && app.isAlive()) {
            try {
                if (pedir("GET", "/api/clientes", null).statusCode() == 200) return;
            } catch (Exception ignored) {
                // El puerto aún no está abierto.
            }
            Thread.sleep(500);
        }
        throw new AssertionError(
                "El backend generado no arrancó. Registro: " + Files.readString(log));
    }

    private static void detener(Process app) throws Exception {
        if (app == null || !app.isAlive()) return;
        app.destroy();
        if (!app.waitFor(8, TimeUnit.SECONDS)) {
            app.destroyForcibly();
            app.waitFor(8, TimeUnit.SECONDS);
        }
    }

    private static HttpResponse<String> pedir(String metodo, String ruta, String cuerpo)
            throws Exception {
        return pedir(metodo, ruta, cuerpo, Map.of());
    }

    private static HttpResponse<String> pedir(
            String metodo, String ruta, String cuerpo, Map<String, String> cabeceras)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create(BASE + ruta))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .method(
                                metodo,
                                cuerpo == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        cabeceras.forEach(builder::header);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Path extraerFixture(Path trabajo) throws Exception {
        String idCliente = UUID.randomUUID().toString();
        String idReserva = UUID.randomUUID().toString();
        String idEtiqueta = UUID.randomUUID().toString();
        var opciones =
                new OpcionesGeneracion(
                        "21",
                        "3.5.16",
                        "FLYWAY",
                        true,
                        true,
                        true,
                        true,
                        true,
                        List.of(
                                dep(
                                        "org.springframework.boot",
                                        "spring-boot-starter-web",
                                        null,
                                        "COMPILACION"),
                                dep(
                                        "org.springframework.boot",
                                        "spring-boot-starter-data-jpa",
                                        null,
                                        "COMPILACION"),
                                dep(
                                        "org.springframework.boot",
                                        "spring-boot-starter-validation",
                                        null,
                                        "COMPILACION"),
                                dep("org.postgresql", "postgresql", null, "EJECUCION"),
                                dep("org.flywaydb", "flyway-core", null, "COMPILACION"),
                                dep(
                                        "org.flywaydb",
                                        "flyway-database-postgresql",
                                        null,
                                        "COMPILACION"),
                                dep(
                                        "org.springdoc",
                                        "springdoc-openapi-starter-webmvc-ui",
                                        "2.8.9",
                                        "COMPILACION"),
                                dep(
                                        "org.springframework.boot",
                                        "spring-boot-starter-test",
                                        null,
                                        "PRUEBA")),
                        List.of());
        var clases =
                List.of(
                        clase(idCliente, "Cliente"),
                        clase(idEtiqueta, "Etiqueta"),
                        clase(idReserva, "Reserva"));
        var atributos =
                List.of(
                        atributo(idCliente, "id", "Long", true),
                        atributo(idCliente, "nombre", "String", false),
                        atributo(idEtiqueta, "id", "UUID", true),
                        atributo(idEtiqueta, "nombre", "String", false),
                        atributo(idReserva, "id", "Long", true),
                        atributo(idReserva, "inicio", "LocalDateTime", false),
                        atributo(idReserva, "estado", "String", false));
        Map<String, Object> relacion = new LinkedHashMap<>();
        relacion.put("id_clase_origen", idCliente);
        relacion.put("id_clase_destino", idReserva);
        relacion.put("tipo_relacion", "ASOCIACION");
        relacion.put("minimo_origen", 1);
        relacion.put("maximo_origen", 1);
        relacion.put("minimo_destino", 0);
        relacion.put("maximo_destino", -1);
        Map<String, Object> etiquetas = new LinkedHashMap<>();
        etiquetas.put("id_clase_origen", idReserva);
        etiquetas.put("id_clase_destino", idEtiqueta);
        etiquetas.put("tipo_relacion", "ASOCIACION");
        etiquetas.put("minimo_origen", 0);
        etiquetas.put("maximo_origen", -1);
        etiquetas.put("minimo_destino", 0);
        etiquetas.put("maximo_destino", -1);
        byte[] zip =
                new MotorGeneracionSpringBoot()
                        .generar(
                                "bo.edu.prueba",
                                "smoke-backend",
                                "1.0.0",
                                "bo.edu.prueba.smoke",
                                opciones,
                                clases,
                                atributos,
                                List.of(relacion, etiquetas),
                                List.of(),
                                List.of())
                        .contenido();
        try (var entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var item = entrada.getNextEntry(); item != null; item = entrada.getNextEntry()) {
                Path destino = trabajo.resolve(item.getName()).normalize();
                assertThat(destino.startsWith(trabajo)).isTrue();
                Files.createDirectories(destino.getParent());
                Files.write(destino, entrada.readAllBytes());
            }
        }
        JsonNode postman =
                JSON.readTree(
                        Files.readString(trabajo.resolve("smoke-backend/postman_collection.json")));
        assertThat(postman.path("info").path("name").asText()).isEqualTo("smoke-backend API");
        assertThat(postman.toString()).contains("{{clienteId}}").contains("{{reservaId}}");
        return trabajo.resolve("smoke-backend");
    }

    private static Dependencia dep(String grupo, String artefacto, String version, String ambito) {
        return new Dependencia(grupo, artefacto, version, ambito, true);
    }

    private static Map<String, Object> clase(String id, String nombre) {
        Map<String, Object> clase = new LinkedHashMap<>();
        clase.put("id", id);
        clase.put("nombre", nombre);
        clase.put("es_persistente", true);
        return clase;
    }

    private static Map<String, Object> atributo(
            String clase, String nombre, String tipo, boolean id) {
        Map<String, Object> atributo = new LinkedHashMap<>();
        atributo.put("id_clase", clase);
        atributo.put("nombre", nombre);
        atributo.put("tipo_uml", tipo);
        atributo.put("es_identificador", id);
        atributo.put("multiplicidad_minima", 1);
        return atributo;
    }
}
