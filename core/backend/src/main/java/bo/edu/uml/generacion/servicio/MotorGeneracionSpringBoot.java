package bo.edu.uml.generacion.servicio;

import bo.edu.uml.generacion.dto.GeneracionDto.Dependencia;
import bo.edu.uml.generacion.dto.GeneracionDto.MapeoTipo;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Motor de generación de proyectos Spring Boot completos en 4 capas.
 *
 * <p>Produce un archivo ZIP en memoria con la estructura estándar Maven que contiene:
 *
 * <ul>
 *   <li><b>Capa Modelo:</b> Entidades JPA {@code @Entity}, {@code @Table}, {@code @Id},
 *       {@code @ManyToOne}/{@code @OneToMany} según cardinalidades.
 *   <li><b>Capa Repositorio:</b> Interfaces {@code JpaRepository<T, ID>}.
 *   <li><b>Capa Controlador:</b> {@code @RestController} con CRUD completo.
 *   <li><b>Capa Vista (DTOs):</b> Records inmutables de solicitud/respuesta.
 *   <li><b>Infraestructura:</b> Maven, PostgreSQL, migración SQL, Docker Compose, OpenAPI y
 *       colección Postman.
 * </ul>
 */
@Service
public class MotorGeneracionSpringBoot {

    /** Opciones congeladas de una configuración avanzada. */
    public record OpcionesGeneracion(
            String versionJava,
            String versionSpringBoot,
            String herramientaMigracion,
            boolean generarRepositorios,
            boolean generarServicios,
            boolean generarControladores,
            boolean generarDtos,
            boolean exponerRest,
            List<Dependencia> dependencias,
            List<MapeoTipo> mapeosTipo) {}

    public record ResultadoMotor(byte[] contenido, int totalArchivos) {}

    /**
     * Genera el ZIP del proyecto Spring Boot.
     *
     * @param groupId Maven groupId (ej: {@code com.empresa}).
     * @param artifactId Maven artifactId (ej: {@code mi-sistema}).
     * @param version Versión del proyecto generado.
     * @param paqueteBase Paquete Java base.
     * @param clases Lista de mapas con {@code id, nombre, es_persistente, es_abstracto}.
     * @param atributos Lista de mapas con {@code id, id_clase, nombre, tipo_uml, es_identificador,
     *     es_unico, multiplicidad_minima, multiplicidad_maxima}.
     * @param relaciones Lista de mapas con {@code id, id_clase_origen, id_clase_destino,
     *     tipo_relacion, minimo_destino, maximo_destino}.
     * @return Bytes del ZIP listo para descarga.
     */
    public byte[] generar(
            String groupId,
            String artifactId,
            String version,
            String paqueteBase,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> relaciones) {

        OpcionesGeneracion opciones =
                new OpcionesGeneracion(
                        "21",
                        "3.5.16",
                        "NINGUNO",
                        true,
                        false,
                        true,
                        true,
                        true,
                        dependenciasPredeterminadas(),
                        List.of());
        return generar(
                        groupId,
                        artifactId,
                        version,
                        paqueteBase,
                        opciones,
                        clases,
                        atributos,
                        relaciones,
                        List.of(),
                        List.of())
                .contenido();
    }

    /** Genera respetando capas, dependencias y mapeos guardados en la configuración. */
    public ResultadoMotor generar(
            String groupId,
            String artifactId,
            String version,
            String paqueteBase,
            OpcionesGeneracion opciones,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros) {

        if (atributos.stream()
                .anyMatch(
                        a ->
                                Set.of("codex_sync_token", "codex_version")
                                        .contains(snakeCase(str(a.get("nombre"))))))
            throw new IllegalArgumentException(
                    "Los nombres codex_sync_token y codex_version están reservados para la"
                            + " sincronización móvil.");

        if (clases.stream()
                .anyMatch(c -> bool(c.get("es_persistente")) && bool(c.get("es_abstracto"))))
            throw new IllegalArgumentException(
                    "GENERACION_NO_SOPORTADA: clase abstracta UML válida; este generador CRUD no"
                        + " implementa su herencia/polimorfismo.");
        Set<String> entidades =
                clases.stream()
                        .filter(c -> bool(c.get("es_persistente")))
                        .map(c -> str(c.get("id")))
                        .collect(Collectors.toSet());
        if (relaciones.stream()
                .anyMatch(
                        r ->
                                "GENERALIZACION".equals(r.get("tipo_relacion"))
                                        && (entidades.contains(str(r.get("id_clase_origen")))
                                                || entidades.contains(
                                                        str(r.get("id_clase_destino"))))))
            throw new IllegalArgumentException(
                    "GENERACION_NO_SOPORTADA: generalización UML válida; el mapeo de herencia JPA"
                            + " requiere configuración no implementada.");
        if (atributos.stream()
                .anyMatch(
                        a ->
                                entidades.contains(str(a.get("id_clase")))
                                        && (bool(a.get("es_estatico"))
                                                || bool(a.get("es_ordenado"))
                                                || Boolean.FALSE.equals(a.get("coleccion_unica"))
                                                || maximoNoEscalar(a))))
            throw new IllegalArgumentException(
                    "GENERACION_NO_SOPORTADA: atributos estáticos o colecciones UML válidos"
                            + " requieren un mapeo que este generador CRUD no implementa.");
        relaciones =
                relaciones.stream()
                        .filter(r -> !"DEPENDENCIA".equals(r.get("tipo_relacion")))
                        .toList();
        Set<String> opIds =
                operaciones.stream().map(o -> str(o.get("id"))).collect(Collectors.toSet());
        if (parametros.stream()
                .anyMatch(
                        p ->
                                opIds.contains(str(p.get("id_operacion")))
                                        && Set.of("SALIDA", "ENTRADA_SALIDA")
                                                .contains(
                                                        Objects.toString(
                                                                p.get("direccion"), "ENTRADA"))))
            throw new IllegalArgumentException(
                    "GENERACION_NO_SOPORTADA: parámetros out/inout UML válidos requieren un mapeo"
                        + " de respuesta no implementado.");
        List<Map<String, Object>> opsCopia = new ArrayList<>();
        for (var o : operaciones) {
            var copy = new LinkedHashMap<String, Object>(o);
            for (var p : parametros)
                if (str(o.get("id")).equals(str(p.get("id_operacion")))
                        && "RETORNO".equals(p.get("direccion"))
                        && copy.get("tipo_retorno") == null)
                    copy.put("tipo_retorno", p.get("tipo_uml"));
            opsCopia.add(copy);
        }
        operaciones = List.copyOf(opsCopia);
        parametros =
                parametros.stream().filter(p -> !"RETORNO".equals(p.get("direccion"))).toList();
        aplicarMapeos(atributos, opciones.mapeosTipo());

        // Solo generamos código para clases persistentes
        List<Map<String, Object>> persistentes =
                clases.stream().filter(c -> bool(c.get("es_persistente"))).toList();
        Set<String> idsPersistentes =
                persistentes.stream().map(c -> str(c.get("id"))).collect(Collectors.toSet());
        // Índices
        Map<String, String> idANombre = new HashMap<>();
        for (var c : clases) idANombre.put(str(c.get("id")), str(c.get("nombre")));
        Map<String, String> idATabla = new HashMap<>();
        for (var c : persistentes)
            idATabla.put(
                    str(c.get("id")),
                    snakeCase(str(c.getOrDefault("nombre_tabla", c.get("nombre")))));

        Map<String, List<Map<String, Object>>> atribPorClase = new LinkedHashMap<>();
        for (var a : atributos) {
            atribPorClase.computeIfAbsent(str(a.get("id_clase")), k -> new ArrayList<>()).add(a);
        }

        Map<String, List<Map<String, Object>>> relacionesPorClase = new LinkedHashMap<>();
        for (var r : relaciones) {
            relacionesPorClase
                    .computeIfAbsent(str(r.get("id_clase_origen")), k -> new ArrayList<>())
                    .add(r);
        }
        // Un diagrama suele representar una relación 1:N con una sola arista.
        // La entidad hija también necesita el lado propietario para que mappedBy y la FK funcionen.
        for (var r : relaciones) {
            String origen = str(r.get("id_clase_origen"));
            String destino = str(r.get("id_clase_destino"));
            if (!idsPersistentes.contains(origen)
                    || !idsPersistentes.contains(destino)
                    || "GENERALIZACION".equalsIgnoreCase(str(r.get("tipo_relacion")))
                    || !esMuchos(r.get("maximo_destino"))
                    || esMuchos(r.get("maximo_origen"))) continue;
            boolean inversaExplicita =
                    relaciones.stream()
                            .anyMatch(
                                    otra ->
                                            destino.equals(str(otra.get("id_clase_origen")))
                                                    && origen.equals(
                                                            str(otra.get("id_clase_destino")))
                                                    && !"GENERALIZACION"
                                                            .equalsIgnoreCase(
                                                                    str(
                                                                            otra.get(
                                                                                    "tipo_relacion"))));
            if (inversaExplicita) continue;
            Map<String, Object> inversa = new LinkedHashMap<>(r);
            inversa.put("id_clase_origen", destino);
            inversa.put("id_clase_destino", origen);
            inversa.put(
                    "maximo_destino", r.get("maximo_origen") == null ? 1 : r.get("maximo_origen"));
            inversa.put("maximo_origen", r.get("maximo_destino"));
            inversa.put("minimo_destino", r.get("minimo_origen"));
            inversa.put("minimo_origen", r.get("minimo_destino"));
            relacionesPorClase.computeIfAbsent(destino, k -> new ArrayList<>()).add(inversa);
        }

        Map<String, List<Map<String, Object>>> operacionesPorClase = new LinkedHashMap<>();
        for (var o : operaciones) {
            operacionesPorClase
                    .computeIfAbsent(str(o.get("id_clase")), k -> new ArrayList<>())
                    .add(o);
        }
        Map<String, List<Map<String, Object>>> parametrosPorOperacion = new LinkedHashMap<>();
        for (var p : parametros) {
            parametrosPorOperacion
                    .computeIfAbsent(str(p.get("id_operacion")), k -> new ArrayList<>())
                    .add(p);
        }

        String paqueteRuta = paqueteBase.replace('.', '/');
        String dirBase = artifactId + "/src/main/java/" + paqueteRuta + "/";
        String esquema = generarSchemaSql(persistentes, atribPorClase, relacionesPorClase, idANombre);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {

            // pom.xml
            agregar(
                    zip,
                    artifactId + "/pom.xml",
                    generarPom(groupId, artifactId, version, opciones));

            // .gitignore
            agregar(zip, artifactId + "/.gitignore", gitIgnore());

            // README.md
            agregar(
                    zip,
                    artifactId + "/README.md",
                    generarReadme(artifactId, groupId, version, opciones));

            // docker-compose.yml
            agregar(
                    zip,
                    artifactId + "/docker-compose.yml",
                    generarDockerCompose(artifactId, esquema, opciones.exponerRest()));

            // Dockerfile
            agregar(zip, artifactId + "/Dockerfile", generarDockerfile());

            // run.bat & run.ps1
            agregar(zip, artifactId + "/run.bat", generarRunBat(artifactId));
            agregar(zip, artifactId + "/run.ps1", generarRunPs1(artifactId));

            // postman_collection.json
            agregar(
                    zip,
                    artifactId + "/postman_collection.json",
                    generarPostmanCollection(
                            artifactId,
                            persistentes,
                            atribPorClase,
                            relacionesPorClase,
                            idANombre,
                            idsPersistentes));

            // Application.java
            agregar(
                    zip,
                    dirBase + nomClase(artifactId) + "Application.java",
                    generarApplication(paqueteBase, nomClase(artifactId)));

            // ApplicationTests.java
            String testDirBase = artifactId + "/src/test/java/" + paqueteRuta + "/";
            agregar(
                    zip,
                    testDirBase + nomClase(artifactId) + "ApplicationTests.java",
                    generarApplicationTests(paqueteBase, nomClase(artifactId)));

            if (opciones.exponerRest()) {
                agregar(
                        zip,
                        dirBase + "config/OpenApiConfig.java",
                        generarOpenApiConfig(paqueteBase, artifactId, version));
                agregar(zip, dirBase + "config/CorsConfig.java", generarCorsConfig(paqueteBase));
                agregar(
                        zip,
                        dirBase + "config/GlobalExceptionHandler.java",
                        generarGlobalExceptionHandler(paqueteBase));
            }

            // application.properties
            agregar(
                    zip,
                    artifactId + "/src/main/resources/application.properties",
                    generarApplicationProperties(artifactId, opciones.herramientaMigracion()));

            // schema.sql
            String rutaMigracion =
                    switch (opciones.herramientaMigracion()) {
                        case "FLYWAY" -> "/src/main/resources/db/migration/V1__schema.sql";
                        case "LIQUIBASE" ->
                                "/src/main/resources/db/changelog/db.changelog-master.xml";
                        default -> "/src/main/resources/schema.sql";
                    };
            agregar(
                    zip,
                    artifactId + rutaMigracion,
                    "LIQUIBASE".equals(opciones.herramientaMigracion())
                            ? generarLiquibase(esquema)
                            : esquema);

            // Capas por clase persistente
            for (var c : persistentes) {
                String id = str(c.get("id"));
                String nombre = str(c.get("nombre"));
                String cls = nomClase(nombre);
                List<Map<String, Object>> atr = atribPorClase.getOrDefault(id, List.of());
                List<Map<String, Object>> rels = relacionesPorClase.getOrDefault(id, List.of());

                // Entidad JPA
                agregar(
                        zip,
                        dirBase + "modelo/" + cls + ".java",
                        generarEntidad(
                                paqueteBase,
                                cls,
                                str(c.getOrDefault("nombre_tabla", nombre)),
                                atr,
                                rels,
                                idANombre,
                                idATabla));

                if (opciones.generarRepositorios()) {
                    agregar(
                            zip,
                            dirBase + "repositorio/" + cls + "Repositorio.java",
                            generarRepositorio(paqueteBase, cls, tipoId(atr)));
                }

                if (opciones.generarDtos()) {
                    agregar(
                            zip,
                            dirBase + "dto/" + cls + "Dto.java",
                            generarDto(
                                    paqueteBase,
                                    cls,
                                    atr,
                                    rels,
                                    idANombre,
                                    idsPersistentes,
                                    atribPorClase));
                }

                if (opciones.generarServicios()) {
                    agregar(
                            zip,
                            dirBase + "servicio/" + cls + "Servicio.java",
                            generarServicio(
                                    paqueteBase,
                                    cls,
                                    tipoId(atr),
                                    operacionesPorClase.getOrDefault(id, List.of()),
                                    parametrosPorOperacion));
                }

                if (opciones.generarControladores()
                        && (!c.containsKey("exponer_rest") || bool(c.get("exponer_rest")))) {
                    agregar(
                            zip,
                            dirBase + "controlador/" + cls + "Controlador.java",
                            generarControlador(
                                    paqueteBase,
                                    cls,
                                    nombre,
                                    tipoId(atr),
                                    opciones.exponerRest(),
                                    opciones.generarServicios(),
                                    opciones.generarDtos(),
                                    operacionesPorClase.getOrDefault(id, List.of()),
                                    parametrosPorOperacion));
                }
            }

            zip.finish();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error generando proyecto Spring Boot: " + e.getMessage(), e);
        }

        byte[] contenido = bos.toByteArray();
        return new ResultadoMotor(contenido, contarArchivos(contenido));
    }

    // ═══════════════════════════════════════════════════════════════════
    // pom.xml
    // ═══════════════════════════════════════════════════════════════════

    private String generarPom(
            String groupId, String artifactId, String version, OpcionesGeneracion opciones) {
        StringBuilder dependencias = new StringBuilder();
        for (Dependencia dependencia : opciones.dependencias()) {
            dependencias.append("    <dependency>\n");
            dependencias
                    .append("      <groupId>")
                    .append(xml(dependencia.idGrupo()))
                    .append("</groupId>\n");
            dependencias
                    .append("      <artifactId>")
                    .append(xml(dependencia.idArtefacto()))
                    .append("</artifactId>\n");
            if (dependencia.version() != null && !dependencia.version().isBlank())
                dependencias
                        .append("      <version>")
                        .append(xml(dependencia.version()))
                        .append("</version>\n");
            String ambito =
                    switch (String.valueOf(dependencia.ambito())) {
                        case "EJECUCION" -> "runtime";
                        case "PRUEBA" -> "test";
                        case "PROVISTO" -> "provided";
                        default -> null;
                    };
            if (ambito != null)
                dependencias.append("      <scope>").append(ambito).append("</scope>\n");
            dependencias.append("    </dependency>\n");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
                + " https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <parent>\n"
                + "    <groupId>org.springframework.boot</groupId>\n"
                + "    <artifactId>spring-boot-starter-parent</artifactId>\n"
                + "    <version>"
                + xml(opciones.versionSpringBoot())
                + "</version>\n"
                + "    <relativePath/>\n"
                + "  </parent>\n"
                + "  <groupId>"
                + groupId
                + "</groupId>\n"
                + "  <artifactId>"
                + artifactId
                + "</artifactId>\n"
                + "  <version>"
                + version
                + "</version>\n"
                + "  <name>"
                + artifactId
                + "</name>\n"
                + "  <description>Proyecto generado por Plataforma UML Colaborativa</description>\n"
                + "  <properties>\n"
                + "    <java.version>"
                + xml(opciones.versionJava())
                + "</java.version>\n"
                + "  </properties>\n"
                + "  <dependencies>\n"
                + dependencias
                + "  </dependencies>\n"
                + "  <build>\n"
                + "    <plugins>\n"
                + "      <plugin>\n"
                + "        <groupId>org.springframework.boot</groupId>\n"
                + "        <artifactId>spring-boot-maven-plugin</artifactId>\n"
                + "      </plugin>\n"
                + "    </plugins>\n"
                + "  </build>\n"
                + "</project>\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // Application.java
    // ═══════════════════════════════════════════════════════════════════

    private String generarApplication(String paqueteBase, String nombreApp) {
        return "package "
                + paqueteBase
                + ";\n\n"
                + "import org.springframework.boot.SpringApplication;\n"
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
                + "@SpringBootApplication\n"
                + "public class "
                + nombreApp
                + "Application {\n\n"
                + "    public static void main(String[] args) {\n"
                + "        SpringApplication.run("
                + nombreApp
                + "Application.class, args);\n"
                + "    }\n"
                + "}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // config/OpenApiConfig.java
    // ═══════════════════════════════════════════════════════════════════

    private String generarOpenApiConfig(String paqueteBase, String artifactId, String version) {
        return "package "
                + paqueteBase
                + ".config;\n\n"
                + "import io.swagger.v3.oas.models.OpenAPI;\n"
                + "import io.swagger.v3.oas.models.info.Info;\n"
                + "import org.springframework.context.annotation.Bean;\n"
                + "import org.springframework.context.annotation.Configuration;\n\n"
                + "@Configuration\n"
                + "public class OpenApiConfig {\n\n"
                + "    @Bean\n"
                + "    public OpenAPI customOpenAPI() {\n"
                + "        return new OpenAPI()\n"
                + "                .info(new Info()\n"
                + "                        .title(\""
                + artifactId
                + " API\")\n"
                + "                        .version(\""
                + version
                + "\")\n"
                + "                        .description(\"API REST generada por Plataforma UML"
                + " Colaborativa\"));\n"
                + "    }\n"
                + "}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // config/CorsConfig.java
    // ═══════════════════════════════════════════════════════════════════

    private String generarCorsConfig(String paqueteBase) {
        return "package "
                + paqueteBase
                + ".config;\n\n"
                + "import org.springframework.context.annotation.Configuration;\n"
                + "import org.springframework.web.servlet.config.annotation.CorsRegistry;\n"
                + "import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;\n\n"
                + "/**\n"
                + " * Configuración CORS global para clientes móviles (Flutter), web y agentes"
                + " externos.\n"
                + " */\n"
                + "@Configuration\n"
                + "public class CorsConfig implements WebMvcConfigurer {\n\n"
                + "    @Override\n"
                + "    public void addCorsMappings(CorsRegistry registry) {\n"
                + "        registry.addMapping(\"/**\")\n"
                + "                .allowedOriginPatterns(\"*\")\n"
                + "                .allowedMethods(\"GET\", \"POST\", \"PUT\", \"DELETE\","
                + " \"PATCH\", \"OPTIONS\", \"HEAD\")\n"
                + "                .allowedHeaders(\"*\")\n"
                + "                .allowCredentials(true);\n"
                + "    }\n"
                + "}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // config/GlobalExceptionHandler.java
    // ═══════════════════════════════════════════════════════════════════

    private String generarGlobalExceptionHandler(String paqueteBase) {
        return "package "
                + paqueteBase
                + ".config;\n\n"
                + "import org.springframework.dao.DataIntegrityViolationException;\n"
                + "import org.springframework.orm.ObjectOptimisticLockingFailureException;\n"
                + "import org.springframework.http.HttpStatus;\n"
                + "import org.springframework.http.ResponseEntity;\n"
                + "import org.springframework.validation.FieldError;\n"
                + "import org.springframework.web.bind.MethodArgumentNotValidException;\n"
                + "import org.springframework.web.bind.annotation.ExceptionHandler;\n"
                + "import org.springframework.web.bind.annotation.RestControllerAdvice;\n\n"
                + "import java.time.Instant;\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n"
                + "import java.util.NoSuchElementException;\n\n"
                + "/**\n"
                + " * Manejador global de excepciones para estandarizar respuestas de error en"
                + " Postman y Flutter.\n"
                + " */\n"
                + "@RestControllerAdvice\n"
                + "public class GlobalExceptionHandler {\n\n"
                + "    public record ErrorRespuesta(\n"
                + "            Instant timestamp,\n"
                + "            int estado,\n"
                + "            String error,\n"
                + "            String mensaje,\n"
                + "            Map<String, String> detalles) {}\n\n"
                + "    @ExceptionHandler(NoSuchElementException.class)\n"
                + "    public ResponseEntity<ErrorRespuesta>"
                + " manejarNoEncontrado(NoSuchElementException ex) {\n"
                + "        return ResponseEntity.status(HttpStatus.NOT_FOUND)\n"
                + "                .body(new ErrorRespuesta(\n"
                + "                        Instant.now(),\n"
                + "                        HttpStatus.NOT_FOUND.value(),\n"
                + "                        \"Recurso no encontrado\",\n"
                + "                        ex.getMessage() != null ? ex.getMessage() : \"El recurso"
                + " solicitado no existe\",\n"
                + "                        Map.of()));\n"
                + "    }\n\n"
                + "    @ExceptionHandler(MethodArgumentNotValidException.class)\n"
                + "    public ResponseEntity<ErrorRespuesta>"
                + " manejarValidacion(MethodArgumentNotValidException ex) {\n"
                + "        Map<String, String> errores = new HashMap<>();\n"
                + "        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {\n"
                + "            errores.put(fieldError.getField(),"
                + " fieldError.getDefaultMessage());\n"
                + "        }\n"
                + "        return ResponseEntity.status(HttpStatus.BAD_REQUEST)\n"
                + "                .body(new ErrorRespuesta(\n"
                + "                        Instant.now(),\n"
                + "                        HttpStatus.BAD_REQUEST.value(),\n"
                + "                        \"Error de validación\",\n"
                + "                        \"Uno o más campos no cumplen las restricciones\",\n"
                + "                        errores));\n"
                + "    }\n\n"
                + "    @ExceptionHandler(DataIntegrityViolationException.class)\n"
                + "    public ResponseEntity<ErrorRespuesta>"
                + " manejarIntegridad(DataIntegrityViolationException ex) {\n"
                + "        return ResponseEntity.status(HttpStatus.CONFLICT)\n"
                + "                .body(new ErrorRespuesta(\n"
                + "                        Instant.now(),\n"
                + "                        HttpStatus.CONFLICT.value(),\n"
                + "                        \"Conflicto de integridad de datos\",\n"
                + "                        \"No se pudo completar la operación debido a una"
                + " restricción de base de datos (clave foránea o valor único)\",\n"
                + "                        Map.of()));\n"
                + "    }\n\n"
                + "    @ExceptionHandler(IllegalArgumentException.class)\n"
                + "    public ResponseEntity<ErrorRespuesta>"
                + " manejarArgumentoInvalido(IllegalArgumentException ex) {\n"
                + "        return ResponseEntity.status(HttpStatus.BAD_REQUEST)\n"
                + "                .body(new ErrorRespuesta(\n"
                + "                        Instant.now(),\n"
                + "                        HttpStatus.BAD_REQUEST.value(),\n"
                + "                        \"Solicitud inválida\",\n"
                + "                        ex.getMessage(),\n"
                + "                        Map.of()));\n"
                + "    }\n\n"
                + "    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)\n"
                + "    public ResponseEntity<ErrorRespuesta>"
                + " manejarConflictoVersion(ObjectOptimisticLockingFailureException ex) {\n"
                + "        return ResponseEntity.status(HttpStatus.CONFLICT)\n"
                + "                .body(new ErrorRespuesta(Instant.now(), 409, \"Conflicto de"
                + " versión\",\n"
                + "                        \"El recurso cambió; vuelve a consultarlo antes de"
                + " actualizar.\", Map.of()));\n"
                + "    }\n\n"
                + "    @ExceptionHandler(Exception.class)\n"
                + "    public ResponseEntity<ErrorRespuesta> manejarGeneral(Exception ex) {\n"
                + "        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)\n"
                + "                .body(new ErrorRespuesta(\n"
                + "                        Instant.now(),\n"
                + "                        HttpStatus.INTERNAL_SERVER_ERROR.value(),\n"
                + "                        \"Error interno del servidor\",\n"
                + "                        ex.getMessage() != null ? ex.getMessage() : \"Ocurrió un"
                + " error inesperado\",\n"
                + "                        Map.of()));\n"
                + "    }\n"
                + "}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // application.properties
    // ═══════════════════════════════════════════════════════════════════

    private String generarApplicationProperties(String artifactId, String herramientaMigracion) {
        String inicializacion =
                switch (herramientaMigracion) {
                    case "FLYWAY" -> "spring.flyway.enabled=true\n";
                    case "LIQUIBASE" ->
                            "spring.liquibase.enabled=true\n"
                                + "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml\n";
                    default ->
                            "spring.sql.init.mode=always\n"
                                    + "spring.sql.init.schema-locations=classpath:schema.sql\n";
                };
        return "# Configuración generada por Plataforma UML Colaborativa\n"
                + "spring.application.name="
                + artifactId
                + "\n\n"
                + "# Base de datos PostgreSQL — soporta Docker Compose y PostgreSQL local\n"
                + "spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5434/"
                + artifactId
                + "}\n"
                + "spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}\n"
                + "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:postgrespassword}\n"
                + "spring.datasource.driver-class-name=org.postgresql.Driver\n\n"
                + "# JPA / Hibernate\n"
                + "spring.jpa.hibernate.ddl-auto=validate\n"
                + "spring.jpa.show-sql=false\n"
                + "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect\n\n"
                + "# Inicialización del esquema\n"
                + inicializacion
                + "\n"
                + "# Swagger UI\n"
                + "springdoc.swagger-ui.path=/swagger-ui.html\n"
                + "springdoc.api-docs.path=/api-docs\n\n"
                + "# Servidor (puerto por defecto 8081 para evitar colisiones con la plataforma en"
                + " 8080)\n"
                + "server.port=${SERVER_PORT:8081}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // schema.sql — DDL
    // ═══════════════════════════════════════════════════════════════════

    private String generarSchemaSql(
            List<Map<String, Object>> persistentes,
            Map<String, List<Map<String, Object>>> atribPorClase,
            Map<String, List<Map<String, Object>>> relacionesPorClase,
            Map<String, String> idANombre) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Schema generado por Plataforma UML Colaborativa\n\n");
        Set<String> idsPersistentes =
                persistentes.stream().map(c -> str(c.get("id"))).collect(Collectors.toSet());
        Map<String, Map<String, Object>> persistentesPorId =
                persistentes.stream()
                        .collect(Collectors.toMap(c -> str(c.get("id")), Function.identity()));

        List<String> fkConstraints = new ArrayList<>();
        Set<String> tablasIntermedias = new LinkedHashSet<>();
        StringBuilder manyToManySql = new StringBuilder();

        for (var c : persistentes) {
            String id = str(c.get("id"));
            String tabla = str(c.getOrDefault("nombre_tabla", snakeCase(str(c.get("nombre")))));
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tabla).append(" (\n");
            List<Map<String, Object>> atr = atribPorClase.getOrDefault(id, List.of());
            var identificador = identificador(atr);
            String colId = snakeCase(str(identificador.get("nombre")));
            sb.append("    ")
                    .append(colId)
                    .append(" ")
                    .append(
                            "Long".equals(tipoId(atr))
                                    ? "BIGSERIAL"
                                    : "Integer".equals(tipoId(atr))
                                            ? "SERIAL"
                                            : tipoSqlAtributo(identificador))
                    .append(" PRIMARY KEY");

            for (var a : atr) {
                if (bool(a.get("es_identificador"))) continue; // id ya generado
                sb.append(",\n    ")
                        .append(snakeCase(str(a.get("nombre"))))
                        .append(" ")
                        .append(tipoSqlAtributo(a));
                if (esObligatorio(a.get("multiplicidad_minima"))) sb.append(" NOT NULL");
                if (bool(a.get("es_unico"))) sb.append(" UNIQUE");
            }
            sb.append(",\n    codex_sync_token UUID UNIQUE");
            sb.append(",\n    codex_version BIGINT NOT NULL DEFAULT 0");

            // Columnas para claves foráneas de relaciones salientes hacia entidades persistentes
            List<Map<String, Object>> rels = relacionesPorClase.getOrDefault(id, List.of());
            for (var r : rels) {
                String destId = str(r.get("id_clase_destino"));
                if (!idsPersistentes.contains(destId)
                        || "GENERALIZACION".equalsIgnoreCase(str(r.get("tipo_relacion")))) continue;
                String destNombre = idANombre.getOrDefault(destId, destId);
                var destClase = persistentesPorId.get(destId);
                String destTabla =
                        str(destClase.getOrDefault("nombre_tabla", snakeCase(destNombre)));
                List<Map<String, Object>> destAtr = atribPorClase.getOrDefault(destId, List.of());
                var destIdentificador = identificador(destAtr);
                String destIdCol = snakeCase(str(destIdentificador.get("nombre")));
                String destSqlTipo = tipoSqlId(tipoId(destAtr));

                Object maxDest = r.get("maximo_destino");
                Object maxOrig = r.get("maximo_origen");
                boolean coleccionDest = esMuchos(maxDest);
                boolean coleccionOrig = maxOrig != null && esMuchos(maxOrig);

                if (coleccionOrig && coleccionDest) {
                    // ManyToMany: generar tabla intermedia única
                    String tablaIntermedia = tabla + "_" + destTabla;
                    String tablaIntermediaAlt = destTabla + "_" + tabla;
                    if (!tablasIntermedias.contains(tablaIntermedia)
                            && !tablasIntermedias.contains(tablaIntermediaAlt)) {
                        tablasIntermedias.add(tablaIntermedia);
                        String origSqlTipo = tipoSqlId(tipoId(atr));
                        manyToManySql
                                .append("CREATE TABLE IF NOT EXISTS ")
                                .append(tablaIntermedia)
                                .append(" (\n")
                                .append("    ")
                                .append(tabla)
                                .append("_id ")
                                .append(origSqlTipo)
                                .append(",\n")
                                .append("    ")
                                .append(destTabla)
                                .append("_id ")
                                .append(destSqlTipo)
                                .append(",\n")
                                .append("    PRIMARY KEY (")
                                .append(tabla)
                                .append("_id, ")
                                .append(destTabla)
                                .append("_id),\n")
                                .append("    CONSTRAINT fk_")
                                .append(tablaIntermedia)
                                .append("_")
                                .append(tabla)
                                .append(" FOREIGN KEY (")
                                .append(tabla)
                                .append("_id) REFERENCES ")
                                .append(tabla)
                                .append("(")
                                .append(colId)
                                .append(") ON DELETE CASCADE,\n")
                                .append("    CONSTRAINT fk_")
                                .append(tablaIntermedia)
                                .append("_")
                                .append(destTabla)
                                .append(" FOREIGN KEY (")
                                .append(destTabla)
                                .append("_id) REFERENCES ")
                                .append(destTabla)
                                .append("(")
                                .append(destIdCol)
                                .append(") ON DELETE CASCADE\n")
                                .append(");\n\n");
                    }
                } else if (!coleccionDest) {
                    // ManyToOne o OneToOne: la columna FK va en esta tabla
                    String fkCol = snakeCase(destNombre) + "_id";
                    sb.append(",\n    ").append(fkCol).append(" ").append(destSqlTipo);
                    if (esObligatorio(r.get("minimo_destino"))) sb.append(" NOT NULL");
                    if (maxOrig != null && !coleccionOrig) {
                        sb.append(" UNIQUE");
                    }
                    fkConstraints.add(
                            "ALTER TABLE "
                                    + tabla
                                    + " ADD CONSTRAINT fk_"
                                    + tabla
                                    + "_"
                                    + snakeCase(destNombre)
                                    + " FOREIGN KEY ("
                                    + fkCol
                                    + ") REFERENCES "
                                    + destTabla
                                    + "("
                                    + destIdCol
                                    + ");");
                }
            }

            sb.append("\n);\n\n");
        }

        // Tablas intermedias N-N
        if (!manyToManySql.isEmpty()) {
            sb.append("-- Tablas intermedias para relaciones N-N\n").append(manyToManySql);
        }

        // Restricciones de clave foránea
        if (!fkConstraints.isEmpty()) {
            sb.append("-- Claves foráneas\n");
            for (String fk : fkConstraints) {
                sb.append(fk).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String generarLiquibase(String esquema) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<databaseChangeLog xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n"
                + "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "  xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog "
                + "https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd\">\n"
                + "  <changeSet id=\"1\" author=\"uml-colaborativo\">\n"
                + "    <sql splitStatements=\"true\"><![CDATA[\n"
                + esquema
                + "    ]]></sql>\n"
                + "  </changeSet>\n"
                + "</databaseChangeLog>\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Modelo — Entidad JPA
    // ═══════════════════════════════════════════════════════════════════

    private String generarEntidad(
            String paqueteBase,
            String cls,
            String nombreTabla,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> relaciones,
            Map<String, String> idANombre,
            Map<String, String> idATabla) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paqueteBase).append(".modelo;\n\n");
        sb.append("import jakarta.persistence.*;\n");
        sb.append("import java.io.Serializable;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.UUID;\n\n");
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(snakeCase(nombreTabla)).append("\")\n");
        sb.append("public class ").append(cls).append(" implements Serializable {\n\n");
        String tipoId = tipoId(atributos);
        String campoId = campoJava(str(identificador(atributos).get("nombre")));
        sb.append("    @Id\n");
        if ("Long".equals(tipoId) || "Integer".equals(tipoId))
            sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        if ("java.util.UUID".equals(tipoId))
            sb.append("    @GeneratedValue(strategy = GenerationType.UUID)\n");
        sb.append("    private ").append(tipoId).append(" ").append(campoId).append(";\n");
        sb.append("\n    @Column(name = \"codex_sync_token\", unique = true)\n");
        sb.append("    private UUID codexSyncToken;\n");
        sb.append("\n    @Version\n    @Column(name = \"codex_version\", nullable = false)\n");
        sb.append("    private Long codexVersion;\n");

        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String tipo = tipoJavaAtributo(a);
            sb.append("\n    @Column(name = \"")
                    .append(snakeCase(str(a.get("nombre"))))
                    .append("\"");
            if (esObligatorio(a.get("multiplicidad_minima"))) sb.append(", nullable = false");
            if (bool(a.get("es_unico"))) sb.append(", unique = true");
            sb.append(")\n");
            sb.append("    private ").append(tipo).append(" ").append(campo).append(";\n");
        }

        // Relaciones
        for (var r : relaciones) {
            String destId = str(r.get("id_clase_destino"));
            String destNombre = idANombre.getOrDefault(destId, destId);
            String destCls = nomClase(destNombre);
            if ("GENERALIZACION".equalsIgnoreCase(str(r.get("tipo_relacion")))) continue;

            Object maxDest = r.get("maximo_destino");
            Object maxOrig = r.get("maximo_origen");
            boolean esColeccionDest = esMuchos(maxDest);
            boolean esColeccionOrig = maxOrig != null && esMuchos(maxOrig);
            boolean esComposicion = "COMPOSICION".equalsIgnoreCase(str(r.get("tipo_relacion")));

            if (esColeccionOrig && esColeccionDest) {
                // ManyToMany
                String tablaIntermedia = snakeCase(nombreTabla) + "_" + idATabla.get(destId);
                sb.append("\n    @ManyToMany\n")
                        .append("    @JoinTable(\n")
                        .append("            name = \"")
                        .append(tablaIntermedia)
                        .append("\",\n")
                        .append("            joinColumns = @JoinColumn(name = \"")
                        .append(snakeCase(nombreTabla))
                        .append("_id\"),\n")
                        .append("            inverseJoinColumns = @JoinColumn(name = \"")
                        .append(idATabla.get(destId))
                        .append("_id\"))\n")
                        .append("    private List<")
                        .append(destCls)
                        .append("> ")
                        .append(campoJava(destNombre))
                        .append("s;\n");
            } else if (esColeccionDest) {
                // OneToMany
                String cascade =
                        esComposicion ? ", cascade = CascadeType.ALL, orphanRemoval = true" : "";
                sb.append("\n    @OneToMany(mappedBy = \"")
                        .append(campoJava(cls))
                        .append("\"")
                        .append(cascade)
                        .append(")\n");
                sb.append("    private List<")
                        .append(destCls)
                        .append("> ")
                        .append(campoJava(destNombre))
                        .append("s;\n");
            } else if (maxOrig != null && !esColeccionOrig) {
                // OneToOne
                String cascade =
                        esComposicion ? ", cascade = CascadeType.ALL, orphanRemoval = true" : "";
                sb.append("\n    @OneToOne(fetch = FetchType.LAZY").append(cascade).append(")\n");
                sb.append("    @JoinColumn(name = \"")
                        .append(snakeCase(destNombre))
                        .append("_id\", unique = true");
                if (esObligatorio(r.get("minimo_destino"))) sb.append(", nullable = false");
                sb.append(")\n");
                sb.append("    private ")
                        .append(destCls)
                        .append(" ")
                        .append(campoJava(destNombre))
                        .append(";\n");
            } else {
                // ManyToOne
                sb.append("\n    @ManyToOne(fetch = FetchType.LAZY)\n");
                sb.append("    @JoinColumn(name = \"")
                        .append(snakeCase(destNombre))
                        .append("_id\"");
                if (esObligatorio(r.get("minimo_destino"))) sb.append(", nullable = false");
                sb.append(")\n");
                sb.append("    private ")
                        .append(destCls)
                        .append(" ")
                        .append(campoJava(destNombre))
                        .append(";\n");
            }
        }

        // Getters y setters de campos propios
        sb.append("\n    public ")
                .append(tipoId)
                .append(" getId() { return ")
                .append(campoId)
                .append("; }\n");
        sb.append("    public void setId(")
                .append(tipoId)
                .append(" id) { this.")
                .append(campoId)
                .append(" = id; }\n");
        sb.append("\n    public UUID getCodexSyncToken() { return codexSyncToken; }\n");
        sb.append(
                "    public void setCodexSyncToken(UUID token) { this.codexSyncToken = token; }\n");
        sb.append("    public Long getCodexVersion() { return codexVersion; }\n");
        sb.append(
                "    public void setCodexVersion(Long version) { this.codexVersion = version; }\n");

        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String tipo = tipoJavaAtributo(a);
            String cap = campo.substring(0, 1).toUpperCase() + campo.substring(1);
            sb.append("\n    public ")
                    .append(tipo)
                    .append(" get")
                    .append(cap)
                    .append("() { return ")
                    .append(campo)
                    .append("; }\n");
            sb.append("    public void set")
                    .append(cap)
                    .append("(")
                    .append(tipo)
                    .append(" ")
                    .append(campo)
                    .append(") { this.")
                    .append(campo)
                    .append(" = ")
                    .append(campo)
                    .append("; }\n");
        }

        // Getters y setters de relaciones
        for (var r : relaciones) {
            String destId = str(r.get("id_clase_destino"));
            String destNombre = idANombre.getOrDefault(destId, destId);
            String destCls = nomClase(destNombre);
            if ("GENERALIZACION".equalsIgnoreCase(str(r.get("tipo_relacion")))) continue;

            Object maxDest = r.get("maximo_destino");
            Object maxOrig = r.get("maximo_origen");
            boolean esColeccionDest = esMuchos(maxDest);
            boolean esColeccionOrig = maxOrig != null && esMuchos(maxOrig);

            if ((esColeccionOrig && esColeccionDest) || esColeccionDest) {
                String campo = campoJava(destNombre) + "s";
                String cap = campo.substring(0, 1).toUpperCase() + campo.substring(1);
                sb.append("\n    public List<")
                        .append(destCls)
                        .append("> get")
                        .append(cap)
                        .append("() { return ")
                        .append(campo)
                        .append("; }\n");
                sb.append("    public void set")
                        .append(cap)
                        .append("(List<")
                        .append(destCls)
                        .append("> ")
                        .append(campo)
                        .append(") { this.")
                        .append(campo)
                        .append(" = ")
                        .append(campo)
                        .append("; }\n");
            } else {
                String campo = campoJava(destNombre);
                String cap = campo.substring(0, 1).toUpperCase() + campo.substring(1);
                sb.append("\n    public ")
                        .append(destCls)
                        .append(" get")
                        .append(cap)
                        .append("() { return ")
                        .append(campo)
                        .append("; }\n");
                sb.append("    public void set")
                        .append(cap)
                        .append("(")
                        .append(destCls)
                        .append(" ")
                        .append(campo)
                        .append(") { this.")
                        .append(campo)
                        .append(" = ")
                        .append(campo)
                        .append("; }\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Repositorio
    // ═══════════════════════════════════════════════════════════════════

    private String generarRepositorio(String paqueteBase, String cls, String tipoId) {
        return "package "
                + paqueteBase
                + ".repositorio;\n\n"
                + "import "
                + paqueteBase
                + ".modelo."
                + cls
                + ";\n"
                + "import org.springframework.data.jpa.repository.JpaRepository;\n"
                + "import org.springframework.stereotype.Repository;\n"
                + "import java.util.Optional;\n"
                + "import java.util.UUID;\n\n"
                + "@Repository\n"
                + "public interface "
                + cls
                + "Repositorio extends JpaRepository<"
                + cls
                + ", "
                + tipoId
                + "> {\n"
                + "    Optional<"
                + cls
                + "> findByCodexSyncToken(UUID token);\n"
                + "}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Vista — DTO como Record inmutable
    // ═══════════════════════════════════════════════════════════════════

    private String generarDto(String paqueteBase, String cls, List<Map<String, Object>> atributos) {
        return generarDto(paqueteBase, cls, atributos, List.of(), Map.of(), Set.of(), Map.of());
    }

    private String generarDto(
            String paqueteBase,
            String cls,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> relaciones,
            Map<String, String> idANombre,
            Set<String> idsPersistentes,
            Map<String, List<Map<String, Object>>> atribPorClase) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paqueteBase).append(".dto;\n\n");
        sb.append("import ").append(paqueteBase).append(".modelo.*;\n");
        sb.append("import jakarta.persistence.EntityManager;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import jakarta.validation.constraints.NotBlank;\n");
        sb.append("import jakarta.validation.constraints.NotNull;\n\n");
        sb.append("public class ").append(cls).append("Dto {\n\n");

        // Relaciones salientes a entidades persistentes (ManyToOne o OneToOne)
        List<Map<String, String>> fks = new ArrayList<>();
        List<Map<String, String>> colecciones = new ArrayList<>();
        for (var r : relaciones) {
            String destId = str(r.get("id_clase_destino"));
            if (!idsPersistentes.contains(destId)
                    || "GENERALIZACION".equalsIgnoreCase(str(r.get("tipo_relacion")))) continue;
            if (esMuchos(r.get("maximo_destino"))) {
                if (r.get("maximo_origen") != null && esMuchos(r.get("maximo_origen"))) {
                    String destino = idANombre.getOrDefault(destId, destId);
                    String campo = campoJava(destino) + "s";
                    colecciones.add(
                            Map.of(
                                    "campoIds",
                                    campo + "Ids",
                                    "capRel",
                                    Character.toUpperCase(campo.charAt(0)) + campo.substring(1),
                                    "destCls",
                                    nomClase(destino),
                                    "destTipoId",
                                    tipoId(atribPorClase.getOrDefault(destId, List.of()))));
                }
                continue;
            }
            String destNombre = idANombre.getOrDefault(destId, destId);
            String destCls = nomClase(destNombre);
            List<Map<String, Object>> destAtr = atribPorClase.getOrDefault(destId, List.of());
            String destTipoId = tipoId(destAtr);
            String campoFk = campoJava(destNombre) + "Id";
            String campoRel = campoJava(destNombre);
            String capRel = campoRel.substring(0, 1).toUpperCase() + campoRel.substring(1);
            fks.add(
                    Map.of(
                            "campoFk", campoFk,
                            "destTipoId", destTipoId,
                            "destCls", destCls,
                            "campoRel", campoRel,
                            "capRel", capRel,
                            "requerido", String.valueOf(esObligatorio(r.get("minimo_destino")))));
        }

        // Record de respuesta
        sb.append("    /** Representa un ").append(cls).append(" en la respuesta de la API. */\n");
        sb.append("    public record Respuesta(\n");
        sb.append("            ")
                .append(tipoId(atributos))
                .append(" id,\n            Long version");
        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            sb.append(",\n            ")
                    .append(tipoJavaAtributo(a))
                    .append(" ")
                    .append(campoJava(str(a.get("nombre"))));
        }
        for (var fk : fks) {
            sb.append(",\n            ")
                    .append(fk.get("destTipoId"))
                    .append(" ")
                    .append(fk.get("campoFk"));
        }
        for (var coleccion : colecciones) {
            sb.append(",\n            List<")
                    .append(coleccion.get("destTipoId"))
                    .append("> ")
                    .append(coleccion.get("campoIds"));
        }
        sb.append(") {}\n\n");

        // Record de solicitud
        sb.append("    /** Datos requeridos para crear o actualizar un ")
                .append(cls)
                .append(". */\n");
        sb.append("    public record Solicitud(\n");
        boolean primero = true;
        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            if (!primero) sb.append(",\n");
            sb.append("            ");
            if (esObligatorio(a.get("multiplicidad_minima")))
                sb.append("String".equals(tipoJavaAtributo(a)) ? "@NotBlank " : "@NotNull ");
            sb.append("")
                    .append(tipoJavaAtributo(a))
                    .append(" ")
                    .append(campoJava(str(a.get("nombre"))));
            primero = false;
        }
        for (var fk : fks) {
            if (!primero) sb.append(",\n");
            sb.append("            ");
            if ("true".equals(fk.get("requerido"))) sb.append("@NotNull ");
            sb.append("").append(fk.get("destTipoId")).append(" ").append(fk.get("campoFk"));
            primero = false;
        }
        for (var coleccion : colecciones) {
            if (!primero) sb.append(",\n");
            sb.append("            List<")
                    .append(coleccion.get("destTipoId"))
                    .append("> ")
                    .append(coleccion.get("campoIds"));
            primero = false;
        }
        if (primero) sb.append("            /* Sin atributos adicionales */");
        sb.append(") {}\n\n");

        // Mapeadores estáticos
        sb.append("    public static Respuesta deEntidad(").append(cls).append(" entidad) {\n");
        sb.append("        if (entidad == null) return null;\n");
        sb.append("        return new Respuesta(\n");
        sb.append("                entidad.getId(),\n                entidad.getCodexVersion()");
        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String cap = campo.substring(0, 1).toUpperCase() + campo.substring(1);
            sb.append(",\n                entidad.get").append(cap).append("()");
        }
        for (var fk : fks) {
            sb.append(",\n                entidad.get")
                    .append(fk.get("capRel"))
                    .append("() != null ? entidad.get")
                    .append(fk.get("capRel"))
                    .append("().getId() : null");
        }
        for (var coleccion : colecciones) {
            sb.append(",\n                entidad.get")
                    .append(coleccion.get("capRel"))
                    .append("() == null ? List.of() : entidad.get")
                    .append(coleccion.get("capRel"))
                    .append("().stream().map(")
                    .append(coleccion.get("destCls"))
                    .append("::getId).toList()");
        }
        sb.append(");\n    }\n\n");

        sb.append("    public static ")
                .append(cls)
                .append(" aEntidad(Solicitud dto, EntityManager manager) {\n");
        sb.append("        if (dto == null) return null;\n");
        sb.append("        ").append(cls).append(" entidad = new ").append(cls).append("();\n");
        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String cap = campo.substring(0, 1).toUpperCase() + campo.substring(1);
            sb.append("        entidad.set")
                    .append(cap)
                    .append("(dto.")
                    .append(campo)
                    .append("());\n");
        }
        for (var fk : fks) {
            sb.append("        if (dto.")
                    .append(fk.get("campoFk"))
                    .append("() != null) {\n")
                    .append("            entidad.set")
                    .append(fk.get("capRel"))
                    .append("(manager.getReference(")
                    .append(fk.get("destCls"))
                    .append(".class, dto.")
                    .append(fk.get("campoFk"))
                    .append("()));\n")
                    .append("        }\n");
        }
        for (var coleccion : colecciones) {
            sb.append("        if (dto.")
                    .append(coleccion.get("campoIds"))
                    .append("() != null) entidad.set")
                    .append(coleccion.get("capRel"))
                    .append("(dto.")
                    .append(coleccion.get("campoIds"))
                    .append("().stream().map(id -> manager.getReference(")
                    .append(coleccion.get("destCls"))
                    .append(".class, id)).toList());\n");
        }
        sb.append("        return entidad;\n    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Servicio — CRUD y firmas UML seleccionadas
    // ═══════════════════════════════════════════════════════════════════

    private String generarServicio(
            String paqueteBase,
            String cls,
            String tipoId,
            List<Map<String, Object>> operaciones,
            Map<String, List<Map<String, Object>>> parametrosPorOperacion) {
        String repositorio = campoJava(cls) + "Repositorio";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paqueteBase).append(".servicio;\n\n");
        sb.append("import ").append(paqueteBase).append(".modelo.").append(cls).append(";\n");
        sb.append("import ")
                .append(paqueteBase)
                .append(".repositorio.")
                .append(cls)
                .append("Repositorio;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Optional;\n");
        sb.append("import java.util.UUID;\n\n");
        sb.append("@Service\npublic class ").append(cls).append("Servicio {\n\n");
        sb.append("    private final ")
                .append(cls)
                .append("Repositorio ")
                .append(repositorio)
                .append(";\n\n");
        sb.append("    public ")
                .append(cls)
                .append("Servicio(")
                .append(cls)
                .append("Repositorio ")
                .append(repositorio)
                .append(") {\n        this.")
                .append(repositorio)
                .append(" = ")
                .append(repositorio)
                .append(";\n    }\n\n");
        sb.append("    public List<")
                .append(cls)
                .append("> listar() { return ")
                .append(repositorio)
                .append(".findAll(); }\n");
        sb.append("    public Optional<")
                .append(cls)
                .append("> obtener(")
                .append(tipoId)
                .append(" id) { return ")
                .append(repositorio)
                .append(".findById(id); }\n");
        sb.append("    public ")
                .append(cls)
                .append(" guardar(")
                .append(cls)
                .append(" entidad) { return ")
                .append(repositorio)
                .append(".save(entidad); }\n");
        sb.append("    public void eliminar(")
                .append(tipoId)
                .append(" id) { ")
                .append(repositorio)
                .append(".deleteById(id); }\n");
        sb.append("    public Optional<")
                .append(cls)
                .append("> porSolicitud(UUID token) { return ")
                .append(repositorio)
                .append(".findByCodexSyncToken(token); }\n");
        for (Map<String, Object> operacion : operaciones) {
            String tipoRetorno = tipoJava(str(operacion.get("tipo_retorno")));
            if (operacion.get("tipo_retorno") == null
                    || "void".equalsIgnoreCase(str(operacion.get("tipo_retorno"))))
                tipoRetorno = "void";
            sb.append("\n    public ")
                    .append(tipoRetorno)
                    .append(" ")
                    .append(campoJava(str(operacion.get("nombre"))))
                    .append("(");
            List<Map<String, Object>> parametros =
                    parametrosPorOperacion.getOrDefault(str(operacion.get("id")), List.of());
            for (int i = 0; i < parametros.size(); i++) {
                if (i > 0) sb.append(", ");
                Map<String, Object> parametro = parametros.get(i);
                sb.append(tipoJava(str(parametro.get("tipo_uml"))))
                        .append(" ")
                        .append(campoJava(str(parametro.get("nombre"))));
            }
            sb.append(
                    ") {\n"
                            + "        throw new UnsupportedOperationException(\"Operación UML"
                            + " pendiente de implementación\");\n"
                            + "    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Controlador — REST CRUD completo
    // ═══════════════════════════════════════════════════════════════════

    private String generarControlador(
            String paqueteBase,
            String cls,
            String nombre,
            String tipoId,
            boolean documentarRest,
            boolean usarServicio,
            boolean usarDtos,
            List<Map<String, Object>> operaciones,
            Map<String, List<Map<String, Object>>> parametrosPorOperacion) {
        String ruta = "/" + snakeCase(nombre).replace('_', '-') + "s";
        String campoInst = campoJava(cls);
        String dependenciaTipo = usarServicio ? cls + "Servicio" : cls + "Repositorio";
        String campoDep = campoInst + (usarServicio ? "Servicio" : "Repositorio");

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paqueteBase).append(".controlador;\n\n");
        sb.append("import ").append(paqueteBase).append(".modelo.").append(cls).append(";\n");
        if (usarDtos) {
            sb.append("import ").append(paqueteBase).append(".dto.").append(cls).append("Dto;\n");
            sb.append("import jakarta.persistence.EntityManager;\n");
        }
        if (usarServicio) {
            sb.append("import ")
                    .append(paqueteBase)
                    .append(".servicio.")
                    .append(cls)
                    .append("Servicio;\n");
        } else {
            sb.append("import ")
                    .append(paqueteBase)
                    .append(".repositorio.")
                    .append(cls)
                    .append("Repositorio;\n");
        }
        if (documentarRest) {
            sb.append("import io.swagger.v3.oas.annotations.Operation;\n");
            sb.append("import io.swagger.v3.oas.annotations.tags.Tag;\n");
        }
        sb.append("import jakarta.validation.Valid;\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.UUID;\n\n");

        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"/api").append(ruta).append("\")\n");
        if (documentarRest) {
            sb.append("@Tag(name = \"")
                    .append(cls)
                    .append("\", description = \"CRUD de ")
                    .append(nombre)
                    .append("\")\n");
        }
        sb.append("public class ").append(cls).append("Controlador {\n\n");
        sb.append("    private final ")
                .append(dependenciaTipo)
                .append(" ")
                .append(campoDep)
                .append(";\n\n");
        if (usarDtos) sb.append("    private final EntityManager entityManager;\n\n");
        sb.append("    public ")
                .append(cls)
                .append("Controlador(")
                .append(dependenciaTipo)
                .append(" ")
                .append(campoDep);
        if (usarDtos) sb.append(", EntityManager entityManager");
        sb.append(") {\n");
        sb.append("        this.").append(campoDep).append(" = ").append(campoDep).append(";\n");
        if (usarDtos) sb.append("        this.entityManager = entityManager;\n");
        sb.append("    }\n\n");

        // GET all
        sb.append("    @GetMapping\n");
        if (documentarRest)
            sb.append("    @Operation(summary = \"Listar todos los ")
                    .append(nombre)
                    .append("\")\n");
        if (usarDtos) {
            sb.append("    public List<").append(cls).append("Dto.Respuesta> listar() {\n");
            String llamada = usarServicio ? campoDep + ".listar()" : campoDep + ".findAll()";
            sb.append("        return ")
                    .append(llamada)
                    .append(".stream().map(")
                    .append(cls)
                    .append("Dto::deEntidad).toList();\n");
        } else {
            sb.append("    public List<").append(cls).append("> listar() {\n");
            String llamada = usarServicio ? campoDep + ".listar()" : campoDep + ".findAll()";
            sb.append("        return ").append(llamada).append(";\n");
        }
        sb.append("    }\n\n");

        // GET by ID
        sb.append("    @GetMapping(\"/{id}\")\n");
        if (documentarRest)
            sb.append("    @Operation(summary = \"Obtener un ")
                    .append(nombre)
                    .append(" por ID\")\n");
        if (usarDtos) {
            sb.append("    public ResponseEntity<")
                    .append(cls)
                    .append("Dto.Respuesta> obtener(@PathVariable ")
                    .append(tipoId)
                    .append(" id) {\n");
            String llamada = usarServicio ? campoDep + ".obtener(id)" : campoDep + ".findById(id)";
            sb.append("        return ").append(llamada).append("\n");
            sb.append("                .map(").append(cls).append("Dto::deEntidad)\n");
            sb.append("                .map(ResponseEntity::ok)\n");
            sb.append("                .orElse(ResponseEntity.notFound().build());\n");
        } else {
            sb.append("    public ResponseEntity<")
                    .append(cls)
                    .append("> obtener(@PathVariable ")
                    .append(tipoId)
                    .append(" id) {\n");
            String llamada = usarServicio ? campoDep + ".obtener(id)" : campoDep + ".findById(id)";
            sb.append("        return ").append(llamada).append("\n");
            sb.append("                .map(ResponseEntity::ok)\n");
            sb.append("                .orElse(ResponseEntity.notFound().build());\n");
        }
        sb.append("    }\n\n");

        // POST create
        sb.append("    @PostMapping\n");
        if (documentarRest)
            sb.append("    @Operation(summary = \"Crear un nuevo ").append(nombre).append("\")\n");
        if (usarDtos) {
            sb.append("    public ResponseEntity<").append(cls).append("Dto.Respuesta> crear(\n");
            sb.append(
                    "            @RequestHeader(value = \"X-Idempotency-Key\", required = false)"
                            + " UUID clave,\n");
            sb.append("            @Valid @RequestBody ")
                    .append(cls)
                    .append("Dto.Solicitud solicitud) {\n");
            String buscarSolicitud =
                    usarServicio
                            ? campoDep + ".porSolicitud(clave)"
                            : campoDep + ".findByCodexSyncToken(clave)";
            sb.append("        if (clave != null) {\n");
            sb.append("            var previa = ").append(buscarSolicitud).append(";\n");
            sb.append("            if (previa.isPresent()) return ResponseEntity.ok(")
                    .append(cls)
                    .append("Dto.deEntidad(previa.get()));\n");
            sb.append("        }\n");
            sb.append("        ")
                    .append(cls)
                    .append(" entidad = ")
                    .append(cls)
                    .append("Dto.aEntidad(solicitud, entityManager);\n");
            sb.append("        entidad.setCodexSyncToken(clave);\n");
            String guardarLlamada =
                    usarServicio ? campoDep + ".guardar(entidad)" : campoDep + ".save(entidad)";
            sb.append("        ")
                    .append(cls)
                    .append(" guardada = ")
                    .append(guardarLlamada)
                    .append(";\n");
            sb.append("        return ResponseEntity.created(URI.create(\"/api")
                    .append(ruta)
                    .append("/\" + guardada.getId())).body(")
                    .append(cls)
                    .append("Dto.deEntidad(guardada));\n");
        } else {
            sb.append("    public ResponseEntity<").append(cls).append("> crear(\n");
            sb.append(
                    "            @RequestHeader(value = \"X-Idempotency-Key\", required = false)"
                            + " UUID clave,\n");
            sb.append("            @Valid @RequestBody ").append(cls).append(" entidad) {\n");
            String buscarSolicitud =
                    usarServicio
                            ? campoDep + ".porSolicitud(clave)"
                            : campoDep + ".findByCodexSyncToken(clave)";
            sb.append("        if (clave != null) {\n");
            sb.append("            var previa = ").append(buscarSolicitud).append(";\n");
            sb.append(
                    "            if (previa.isPresent()) return"
                            + " ResponseEntity.ok(previa.get());\n");
            sb.append("        }\n");
            sb.append("        entidad.setCodexSyncToken(clave);\n");
            String guardarLlamada =
                    usarServicio ? campoDep + ".guardar(entidad)" : campoDep + ".save(entidad)";
            sb.append("        ")
                    .append(cls)
                    .append(" guardada = ")
                    .append(guardarLlamada)
                    .append(";\n");
            sb.append("        return ResponseEntity.created(URI.create(\"/api")
                    .append(ruta)
                    .append("/\" + guardada.getId())).body(guardada);\n");
        }
        sb.append("    }\n\n");

        // PUT update
        sb.append("    @PutMapping(\"/{id}\")\n");
        if (documentarRest)
            sb.append("    @Operation(summary = \"Actualizar un ")
                    .append(nombre)
                    .append(" existente\")\n");
        if (usarDtos) {
            sb.append("    public ResponseEntity<")
                    .append(cls)
                    .append("Dto.Respuesta> actualizar(\n");
            sb.append("            @PathVariable ").append(tipoId).append(" id,\n");
            sb.append(
                    "            @RequestHeader(value = \"X-Expected-Version\", required = false)"
                            + " Long versionEsperada,\n");
            sb.append("            @Valid @RequestBody ")
                    .append(cls)
                    .append("Dto.Solicitud solicitud) {\n");
            String llamadaObtener =
                    usarServicio ? campoDep + ".obtener(id)" : campoDep + ".findById(id)";
            String guardarLlamada =
                    usarServicio ? campoDep + ".guardar(entidad)" : campoDep + ".save(entidad)";
            sb.append("        return ").append(llamadaObtener).append("\n");
            sb.append("                .map(existente -> {\n");
            sb.append(
                    "                    if (versionEsperada != null &&"
                            + " !versionEsperada.equals(existente.getCodexVersion()))\n");
            sb.append("                        return ResponseEntity.status(409).<")
                    .append(cls)
                    .append("Dto.Respuesta>build();\n");
            sb.append("                    ")
                    .append(cls)
                    .append(" entidad = ")
                    .append(cls)
                    .append("Dto.aEntidad(solicitud, entityManager);\n");
            sb.append("                    entidad.setId(id);\n");
            sb.append(
                    "                   "
                            + " entidad.setCodexSyncToken(existente.getCodexSyncToken());\n");
            sb.append(
                    "                    entidad.setCodexVersion(existente.getCodexVersion());\n");
            sb.append("                    return ResponseEntity.ok(")
                    .append(cls)
                    .append("Dto.deEntidad(")
                    .append(guardarLlamada)
                    .append("));\n");
            sb.append("                })\n");
            sb.append("                .orElse(ResponseEntity.notFound().build());\n");
        } else {
            sb.append("    public ResponseEntity<").append(cls).append("> actualizar(\n");
            sb.append("            @PathVariable ").append(tipoId).append(" id,\n");
            sb.append(
                    "            @RequestHeader(value = \"X-Expected-Version\", required = false)"
                            + " Long versionEsperada,\n");
            sb.append("            @Valid @RequestBody ").append(cls).append(" entidad) {\n");
            String llamadaObtener =
                    usarServicio ? campoDep + ".obtener(id)" : campoDep + ".findById(id)";
            String guardarLlamada =
                    usarServicio ? campoDep + ".guardar(entidad)" : campoDep + ".save(entidad)";
            sb.append("        return ").append(llamadaObtener).append("\n");
            sb.append("                .map(existente -> {\n");
            sb.append(
                    "                    if (versionEsperada != null &&"
                            + " !versionEsperada.equals(existente.getCodexVersion()))\n");
            sb.append("                        return ResponseEntity.status(409).<")
                    .append(cls)
                    .append(">build();\n");
            sb.append("                    entidad.setId(id);\n");
            sb.append(
                    "                   "
                            + " entidad.setCodexSyncToken(existente.getCodexSyncToken());\n");
            sb.append(
                    "                    entidad.setCodexVersion(existente.getCodexVersion());\n");
            sb.append("                    return ResponseEntity.ok(")
                    .append(guardarLlamada)
                    .append(");\n");
            sb.append("                })\n");
            sb.append("                .orElse(ResponseEntity.notFound().build());\n");
        }
        sb.append("    }\n\n");

        // DELETE
        sb.append("    @DeleteMapping(\"/{id}\")\n");
        if (documentarRest)
            sb.append("    @Operation(summary = \"Eliminar un ").append(nombre).append("\")\n");
        sb.append("    public ResponseEntity<Void> eliminar(\n");
        sb.append("            @PathVariable ").append(tipoId).append(" id,\n");
        sb.append(
                "            @RequestHeader(value = \"X-Expected-Version\", required = false) Long"
                        + " versionEsperada) {\n");
        String llamadaObtener =
                usarServicio ? campoDep + ".obtener(id)" : campoDep + ".findById(id)";
        String llamadaEliminar =
                usarServicio ? campoDep + ".eliminar(id)" : campoDep + ".deleteById(id)";
        sb.append("        var existente = ").append(llamadaObtener).append(";\n");
        sb.append("        if (existente.isEmpty())\n");
        sb.append("            return ResponseEntity.notFound().build();\n");
        sb.append(
                "        if (versionEsperada != null &&"
                        + " !versionEsperada.equals(existente.get().getCodexVersion()))\n");
        sb.append("            return ResponseEntity.status(409).build();\n");
        sb.append("        ").append(llamadaEliminar).append(";\n");
        sb.append("        return ResponseEntity.noContent().build();\n");
        sb.append("    }\n");

        // Operaciones personalizadas UML
        for (var operacion : operaciones) {
            String nomOp = str(operacion.get("nombre"));
            String rutaOp = snakeCase(nomOp).replace('_', '-');
            String tipoRet = tipoJava(str(operacion.get("tipo_retorno")));
            if (operacion.get("tipo_retorno") == null
                    || "void".equalsIgnoreCase(str(operacion.get("tipo_retorno"))))
                tipoRet = "void";
            String retResp = "void".equals(tipoRet) ? "Void" : tipoRet;

            sb.append("\n    @PostMapping(\"/{id}/").append(rutaOp).append("\")\n");
            if (documentarRest) {
                sb.append("    @Operation(summary = \"Operación ").append(nomOp).append("\")\n");
            }
            sb.append("    public ResponseEntity<")
                    .append(retResp)
                    .append("> ")
                    .append(campoJava(nomOp))
                    .append("(@PathVariable ")
                    .append(tipoId)
                    .append(" id");

            List<Map<String, Object>> params =
                    parametrosPorOperacion.getOrDefault(str(operacion.get("id")), List.of());
            for (var p : params) {
                sb.append(", @RequestParam ")
                        .append(tipoJava(str(p.get("tipo_uml"))))
                        .append(" ")
                        .append(campoJava(str(p.get("nombre"))));
            }
            sb.append(") {\n");
            if (usarServicio) {
                sb.append("        ");
                if (!"void".equals(tipoRet)) sb.append(tipoRet).append(" resultado = ");
                sb.append(campoDep).append(".").append(campoJava(nomOp)).append("(");
                for (int i = 0; i < params.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(campoJava(str(params.get(i).get("nombre"))));
                }
                sb.append(");\n");
                if ("void".equals(tipoRet)) {
                    sb.append("        return ResponseEntity.ok().build();\n");
                } else {
                    sb.append("        return ResponseEntity.ok(resultado);\n");
                }
            } else {
                sb.append("        return ResponseEntity.notFound().build();\n");
            }
            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // README.md y Pruebas
    // ═══════════════════════════════════════════════════════════════════

    private String generarReadme(
            String artifactId, String groupId, String version, OpcionesGeneracion opciones) {
        return "# "
                + artifactId
                + "\n\n"
                + "Proyecto Spring Boot generado por Plataforma UML Colaborativa.\n\n"
                + "## Requisitos previos\n\n"
                + "- Docker Desktop abierto y con el motor iniciado. No necesitas instalar Java,"
                + " Maven ni PostgreSQL.\n\n"
                + "## Inicio en Windows\n\n"
                + "1. Descomprime el ZIP y abre PowerShell dentro de la carpeta `"
                + artifactId
                + "` (donde está `docker-compose.yml`).\n"
                + "2. Copia y pega este único comando:\n"
                + "```powershell\n"
                + "docker compose up -d --build --wait\n"
                + "```\n"
                + "La primera compilación puede tardar varios minutos. Cuando termine, abre"
                + " `http://localhost:18081/api-docs`. `run.bat` y `run.ps1` ejecutan el mismo"
                + " comando.\n\n"
                + "Si aparece un error, ejecuta `docker compose logs app --tail 100`. Para"
                + " detener los contenedores usa `docker compose stop`; los datos quedan"
                + " guardados. Para volver a arrancar, usa el comando anterior.\n\n"
                + "Cada esquema generado usa un volumen PostgreSQL propio. Si cambias el"
                + " diagrama y descargas otro ZIP con el mismo nombre, la versión nueva arranca"
                + " con una base vacía y conserva el volumen de la versión anterior.\n\n"
                + "## Documentación de la API\n\n"
                + "- **Swagger UI:** `http://localhost:18081/swagger-ui.html`\n"
                + "- **Contrato OpenAPI JSON:** `http://localhost:18081/api-docs`\n\n"
                + "## Pruebas Inmediatas con Postman (Colección Incluida)\n\n"
                + "En la raíz de este proyecto se incluye el archivo `postman_collection.json`:\n"
                + "1. Abre **Postman** y selecciona **Import**.\n"
                + "2. Arrastra o selecciona el archivo `postman_collection.json`.\n"
                + "3. Ejecuta la colección en orden: crea las entidades relacionadas, consulta,"
                + " actualiza y elimina al final en orden inverso. Los IDs y las versiones se"
                + " guardan automáticamente como variables.\n"
                + "4. La colección usa la variable `baseUrl` (por defecto"
                + " `http://localhost:18081`).\n\n"
                + "## Contrato para sincronización móvil\n\n"
                + "- Una creación normal responde `201 Created`, devuelve `id` y `version` y fija"
                + " `Location`.\n"
                + "- Flutter debe asignar un UUID a cada creación pendiente y enviarlo en"
                + " `X-Idempotency-Key`. Un reintento con la misma clave devuelve el mismo recurso"
                + " con `200 OK`.\n"
                + "- Para actualizar o eliminar, envía el valor `version` conocido en"
                + " `X-Expected-Version`. Una versión antigua recibe `409 Conflict`; `404 Not"
                + " Found` indica recurso ausente.\n"
                + "- Guarda primero los cambios en la base local del móvil y sincroniza cuando"
                + " alcance el servidor. Una operación local pendiente no equivale a confirmación"
                + " compartida.\n"
                + "- Los identificadores locales UUID se convierten a `id` del servidor al"
                + " confirmar cada POST. Sincroniza antes las entidades referenciadas por claves"
                + " foráneas.\n\n"
                + "## Acoplamiento con Frontend Móvil Flutter e Inteligencia Artificial\n\n"
                + "Este backend cuenta con CORS global habilitado y especificación OpenAPI"
                + " estándar, lo que permite acoplar un frontend móvil en Flutter generado mediante"
                + " asistentes de IA (Codex o Antigravity).\n\n"
                + "### Direcciones de red para Flutter:\n"
                + "- **Emulador de Android:** `http://10.0.2.2:18081/api`\n"
                + "- **Simulador de iOS:** `http://localhost:18081/api`\n"
                + "- **Dispositivo Móvil Físico:** `http://<IP_DE_TU_PC>:18081/api` (ambos en la"
                + " misma Wi-Fi)\n"
                + "- **Flutter Web / Escritorio:** `http://localhost:18081/api`\n\n"
                + "### Prompt para generar la App Flutter en IA (Codex / Antigravity):\n"
                + "```text\n"
                + "Actúa como un desarrollador Flutter senior. Genera una aplicación móvil"
                + " funcional en Flutter para los recursos reales del contrato OpenAPI en"
                + " http://localhost:18081/api-docs. Usa un servicio HTTP con baseUrl configurable"
                + " (10.0.2.2 para Android y localhost para iOS/Web), modelos tipados con"
                + " fromJson/toJson, almacenamiento local y una cola de sincronización. Incluye"
                + " una interfaz de agente guiada principalmente por voz que convierta la intención"
                + " en datos estructurados, los valide y los guarde localmente antes de enviarlos."
                + " Usa X-Idempotency-Key en POST y X-Expected-Version en PUT/DELETE. Deduce el"
                + " dominio y sus pantallas del contrato; no presupongas que es una app de citas.\n"
                + "```\n";
    }

    private String huellaEsquema(String esquema) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(esquema.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private String generarDockerCompose(String artifactId, String esquema, boolean exponerRest) {
        String volumen = "pgdata_" + huellaEsquema(esquema);
        return "services:\n"
                + "  db:\n"
                + "    image: postgres:16-alpine\n"
                + "    restart: unless-stopped\n"
                + "    environment:\n"
                + "      POSTGRES_DB: "
                + artifactId
                + "\n"
                + "      POSTGRES_USER: postgres\n"
                + "      POSTGRES_PASSWORD: postgrespassword\n"
                + "    volumes:\n"
                + "      - "
                + volumen
                + ":/var/lib/postgresql/data\n"
                + "    healthcheck:\n"
                + "      test: [\"CMD-SHELL\", \"pg_isready -U postgres -d "
                + artifactId
                + "\"]\n"
                + "      interval: 5s\n"
                + "      timeout: 5s\n"
                + "      retries: 5\n\n"
                + "  app:\n"
                + "    build: .\n"
                + "    restart: unless-stopped\n"
                + "    depends_on:\n"
                + "      db:\n"
                + "        condition: service_healthy\n"
                + "    environment:\n"
                + "      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/"
                + artifactId
                + "\n"
                + "      SPRING_DATASOURCE_USERNAME: postgres\n"
                + "      SPRING_DATASOURCE_PASSWORD: postgrespassword\n"
                + "    ports:\n"
                + "      - \"${APP_PORT:-18081}:8081\"\n"
                + (exponerRest
                        ? "    healthcheck:\n"
                              + "      test: [\"CMD-SHELL\", \"wget -q -O /dev/null"
                              + " http://127.0.0.1:8081/api-docs || exit 1\"]\n"
                              + "      interval: 5s\n"
                              + "      timeout: 5s\n"
                              + "      retries: 24\n"
                              + "      start_period: 30s\n"
                        : "")
                + "\n"
                + "volumes:\n"
                + "  "
                + volumen
                + ":\n";
    }

    private String generarDockerfile() {
        return "# Etapa 1: Compilación del proyecto con Maven y JDK 21\n"
                   + "FROM maven:3.9-eclipse-temurin-21-alpine AS builder\n"
                   + "WORKDIR /app\n"
                   + "COPY pom.xml .\n"
                   + "COPY src ./src\n"
                   + "RUN --mount=type=cache,target=/root/.m2 mvn -q -B -Dmaven.test.skip=true"
                   + " package\n\n"
                   + "# Etapa 2: Imagen ligera de ejecución con JRE 21\n"
                   + "FROM eclipse-temurin:21-jre-alpine\n"
                   + "WORKDIR /app\n"
                   + "COPY --from=builder /app/target/*.jar app.jar\n"
                   + "EXPOSE 8081\n"
                   + "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n";
    }

    private String generarRunBat(String artifactId) {
        return "@echo off\n"
                + "setlocal\n"
                + "cd /d \"%~dp0\"\n"
                + "docker compose up -d --build --wait\n"
                + "if errorlevel 1 (\n"
                + "    echo Fallo el inicio. Revisa: docker compose logs app --tail 100\n"
                + "    exit /b 1\n"
                + ")\n"
                + "echo Backend listo: http://localhost:18081/api-docs\n";
    }

    private String generarRunPs1(String artifactId) {
        return "Set-Location $PSScriptRoot\n"
                   + "docker compose up -d --build --wait\n"
                   + "if ($LASTEXITCODE -ne 0) { throw 'Fallo el inicio. Revisa: docker compose"
                   + " logs app --tail 100' }\n"
                   + "Write-Host 'Backend listo: http://localhost:18081/api-docs'\n";
    }

    private String generarApplicationTests(String paqueteBase, String nombreApp) {
        return "package "
                + paqueteBase
                + ";\n\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import org.springframework.boot.test.context.SpringBootTest;\n\n"
                + "@SpringBootTest\n"
                + "class "
                + nombreApp
                + "ApplicationTests {\n\n"
                + "    @Test\n"
                + "    void contextLoads() {\n"
                + "    }\n"
                + "}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // postman_collection.json — Colección Postman v2.1
    // ═══════════════════════════════════════════════════════════════════

    private String generarPostmanCollection(
            String artifactId,
            List<Map<String, Object>> persistentes,
            Map<String, List<Map<String, Object>>> atribPorClase,
            Map<String, List<Map<String, Object>>> relacionesPorClase,
            Map<String, String> idANombre,
            Set<String> idsPersistentes) {

        StringBuilder sb = new StringBuilder();
        String colId =
                UUID.nameUUIDFromBytes(artifactId.getBytes(StandardCharsets.UTF_8)).toString();

        sb.append("{\n");
        sb.append("  \"info\": {\n");
        sb.append("    \"_postman_id\": \"").append(colId).append("\",\n");
        sb.append("    \"name\": \"").append(escapeJson(artifactId)).append(" API\",\n");
        sb.append(
                "    \"description\": \"Colección de pruebas CRUD generada por Plataforma UML"
                        + " Colaborativa\",\n");
        sb.append(
                "    \"schema\":"
                    + " \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n");
        sb.append("  },\n");
        sb.append("  \"variable\": [\n");
        sb.append("    {\n");
        sb.append("      \"key\": \"baseUrl\",\n");
        sb.append("      \"value\": \"http://localhost:18081\",\n");
        sb.append("      \"type\": \"string\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"item\": [\n");

        List<Map<String, Object>> ordenPostman =
                ordenarParaPostman(persistentes, relacionesPorClase);
        List<String> eliminaciones = new ArrayList<>();
        for (int i = 0; i < ordenPostman.size(); i++) {
            var c = ordenPostman.get(i);
            String id = str(c.get("id"));
            String nombre = str(c.get("nombre"));
            String cls = nomClase(nombre);
            String ruta = snakeCase(nombre).replace('_', '-') + "s";
            String variableId = campoJava(cls) + "Id";
            String variableVersion = campoJava(cls) + "Version";
            List<Map<String, Object>> atr = atribPorClase.getOrDefault(id, List.of());
            List<Map<String, Object>> rels = relacionesPorClase.getOrDefault(id, List.of());

            String payloadCrear =
                    escapeJson(
                            construirPayloadJson(
                                    atr, rels, idANombre, idsPersistentes, atribPorClase, false));
            String payloadActualizar =
                    escapeJson(
                            construirPayloadJson(
                                    atr, rels, idANombre, idsPersistentes, atribPorClase, true));

            if (i > 0) sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escapeJson(cls)).append("\",\n");
            sb.append("      \"item\": [\n");

            StringBuilder carpeta = sb;
            StringBuilder listar = new StringBuilder();
            sb = listar;
            // 1. Listar
            sb.append("        {\n");
            sb.append("          \"name\": \"3. Listar todos los ")
                    .append(escapeJson(nombre))
                    .append("\",\n");
            sb.append("          \"request\": {\n");
            sb.append("            \"method\": \"GET\",\n");
            sb.append("            \"header\": [],\n");
            sb.append("            \"url\": {\n");
            sb.append("              \"raw\": \"{{baseUrl}}/api/").append(ruta).append("\",\n");
            sb.append("              \"host\": [\"{{baseUrl}}\"],\n");
            sb.append("              \"path\": [\"api\", \"").append(ruta).append("\"]\n");
            sb.append("            }\n");
            sb.append("          }\n");
            sb.append("        },\n");

            StringBuilder obtener = new StringBuilder();
            sb = obtener;
            // 2. Obtener por ID
            sb.append("        {\n");
            sb.append("          \"name\": \"2. Obtener ")
                    .append(escapeJson(nombre))
                    .append(" por ID\",\n");
            sb.append("          \"request\": {\n");
            sb.append("            \"method\": \"GET\",\n");
            sb.append("            \"header\": [],\n");
            sb.append("            \"url\": {\n");
            sb.append("              \"raw\": \"{{baseUrl}}/api/")
                    .append(ruta)
                    .append("/{{")
                    .append(variableId)
                    .append("}}\",\n");
            sb.append("              \"host\": [\"{{baseUrl}}\"],\n");
            sb.append("              \"path\": [\"api\", \"")
                    .append(ruta)
                    .append("\", \"{{")
                    .append(variableId)
                    .append("}}\"]\n");
            sb.append("            }\n");
            sb.append("          }\n");
            sb.append("        },\n");

            StringBuilder crear = new StringBuilder();
            sb = crear;
            // 1. Crear
            sb.append("        {\n");
            sb.append("          \"name\": \"1. Crear ").append(escapeJson(nombre)).append("\",\n");
            sb.append("          \"request\": {\n");
            sb.append("            \"method\": \"POST\",\n");
            sb.append("            \"header\": [\n");
            sb.append("              {\n");
            sb.append("                \"key\": \"Content-Type\",\n");
            sb.append("                \"value\": \"application/json\"\n");
            sb.append("              }\n");
            sb.append("            ],\n");
            sb.append("            \"body\": {\n");
            sb.append("              \"mode\": \"raw\",\n");
            sb.append("              \"raw\": \"").append(payloadCrear).append("\"\n");
            sb.append("            },\n");
            sb.append("            \"url\": {\n");
            sb.append("              \"raw\": \"{{baseUrl}}/api/").append(ruta).append("\",\n");
            sb.append("              \"host\": [\"{{baseUrl}}\"],\n");
            sb.append("              \"path\": [\"api\", \"").append(ruta).append("\"]\n");
            sb.append("            }\n");
            sb.append("          },\n");
            sb.append(
                    "          \"event\": [{\"listen\": \"test\", \"script\": {\"type\":"
                            + " \"text/javascript\", \"exec\": [\n");
            sb.append("            \"")
                    .append(
                            escapeJson(
                                    "pm.test('Creación correcta', () =>"
                                            + " pm.response.to.have.status(201));"))
                    .append("\",\n");
            sb.append("            \"")
                    .append(escapeJson("const creado = pm.response.json();"))
                    .append("\",\n");
            sb.append("            \"")
                    .append(
                            escapeJson(
                                    "pm.collectionVariables.set('" + variableId + "', creado.id);"))
                    .append("\",\n");
            sb.append("            \"")
                    .append(
                            escapeJson(
                                    "pm.collectionVariables.set('"
                                            + variableVersion
                                            + "', creado.version);"))
                    .append("\"\n");
            sb.append("          ]}}]\n");
            sb.append("        },\n");

            StringBuilder actualizar = new StringBuilder();
            sb = actualizar;
            // 4. Actualizar
            sb.append("        {\n");
            sb.append("          \"name\": \"4. Actualizar ")
                    .append(escapeJson(nombre))
                    .append("\",\n");
            sb.append("          \"request\": {\n");
            sb.append("            \"method\": \"PUT\",\n");
            sb.append("            \"header\": [\n");
            sb.append("              {\n");
            sb.append("                \"key\": \"Content-Type\",\n");
            sb.append("                \"value\": \"application/json\"\n");
            sb.append("              },\n");
            sb.append("              {\"key\": \"X-Expected-Version\", \"value\": \"{{")
                    .append(variableVersion)
                    .append("}}\"}\n");
            sb.append("            ],\n");
            sb.append("            \"body\": {\n");
            sb.append("              \"mode\": \"raw\",\n");
            sb.append("              \"raw\": \"").append(payloadActualizar).append("\"\n");
            sb.append("            },\n");
            sb.append("            \"url\": {\n");
            sb.append("              \"raw\": \"{{baseUrl}}/api/")
                    .append(ruta)
                    .append("/{{")
                    .append(variableId)
                    .append("}}\",\n");
            sb.append("              \"host\": [\"{{baseUrl}}\"],\n");
            sb.append("              \"path\": [\"api\", \"")
                    .append(ruta)
                    .append("\", \"{{")
                    .append(variableId)
                    .append("}}\"]\n");
            sb.append("            }\n");
            sb.append("          },\n");
            sb.append(
                    "          \"event\": [{\"listen\": \"test\", \"script\": {\"type\":"
                            + " \"text/javascript\", \"exec\": [\n");
            sb.append("            \"")
                    .append(
                            escapeJson(
                                    "pm.test('Actualización correcta', () =>"
                                            + " pm.response.to.have.status(200));"))
                    .append("\",\n");
            sb.append("            \"")
                    .append(
                            escapeJson(
                                    "pm.collectionVariables.set('"
                                            + variableVersion
                                            + "', pm.response.json().version);"))
                    .append("\"\n");
            sb.append("          ]}}]\n");
            sb.append("        },\n");

            StringBuilder eliminar = new StringBuilder();
            sb = eliminar;
            // 5. Eliminar
            sb.append("        {\n");
            sb.append("          \"name\": \"5. Eliminar ")
                    .append(escapeJson(nombre))
                    .append("\",\n");
            sb.append("          \"request\": {\n");
            sb.append("            \"method\": \"DELETE\",\n");
            sb.append("            \"header\": [{\"key\": \"X-Expected-Version\", \"value\": \"{{")
                    .append(variableVersion)
                    .append("}}\"}],\n");
            sb.append("            \"url\": {\n");
            sb.append("              \"raw\": \"{{baseUrl}}/api/")
                    .append(ruta)
                    .append("/{{")
                    .append(variableId)
                    .append("}}\",\n");
            sb.append("              \"host\": [\"{{baseUrl}}\"],\n");
            sb.append("              \"path\": [\"api\", \"")
                    .append(ruta)
                    .append("\", \"{{")
                    .append(variableId)
                    .append("}}\"]\n");
            sb.append("            }\n");
            sb.append("          }\n");
            sb.append("        }\n");

            sb = carpeta;
            sb.append(crear).append(obtener).append(listar);
            sb.append(actualizar.substring(0, actualizar.length() - 2)).append("\n");
            eliminaciones.add(eliminar.toString().trim());
            sb.append("      ]\n");
            sb.append("    }");
        }

        if (!eliminaciones.isEmpty()) {
            sb.append(",\n    {\"name\": \"5. Eliminar en orden inverso\", \"item\": [\n");
            for (int i = eliminaciones.size() - 1; i >= 0; i--) {
                if (i < eliminaciones.size() - 1) sb.append(",\n");
                sb.append(eliminaciones.get(i));
            }
            sb.append("\n    ]}");
        }

        sb.append("\n  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private List<Map<String, Object>> ordenarParaPostman(
            List<Map<String, Object>> clases,
            Map<String, List<Map<String, Object>>> relacionesPorClase) {
        List<Map<String, Object>> pendientes = new ArrayList<>(clases);
        List<Map<String, Object>> orden = new ArrayList<>();
        Set<String> disponibles = new HashSet<>();
        while (!pendientes.isEmpty()) {
            boolean avance = false;
            for (var c : List.copyOf(pendientes)) {
                String id = str(c.get("id"));
                boolean faltaDependencia =
                        relacionesPorClase.getOrDefault(id, List.of()).stream()
                                .filter(
                                        r ->
                                                !"GENERALIZACION"
                                                        .equalsIgnoreCase(
                                                                str(r.get("tipo_relacion"))))
                                .filter(r -> !esMuchos(r.get("maximo_destino")))
                                .map(r -> str(r.get("id_clase_destino")))
                                .anyMatch(
                                        destino ->
                                                !destino.equals(id)
                                                        && clases.stream()
                                                                .anyMatch(
                                                                        otra ->
                                                                                destino.equals(
                                                                                        str(
                                                                                                otra
                                                                                                        .get(
                                                                                                                "id"))))
                                                        && !disponibles.contains(destino));
                if (faltaDependencia) continue;
                orden.add(c);
                disponibles.add(id);
                pendientes.remove(c);
                avance = true;
            }
            if (!avance) {
                orden.addAll(pendientes);
                break;
            }
        }
        return orden;
    }

    private String construirPayloadJson(
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> relaciones,
            Map<String, String> idANombre,
            Set<String> idsPersistentes,
            Map<String, List<Map<String, Object>>> atribPorClase,
            boolean modificado) {

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        boolean primero = true;

        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String tipo = tipoJavaAtributo(a);
            if (!primero) json.append(",\n");
            json.append("  \"").append(campo).append("\": ");
            json.append(valorJsonSintetico(campo, tipo, modificado));
            primero = false;
        }

        for (var r : relaciones) {
            String destId = str(r.get("id_clase_destino"));
            if (!idsPersistentes.contains(destId)
                    || "GENERALIZACION".equalsIgnoreCase(str(r.get("tipo_relacion")))) continue;
            if (esMuchos(r.get("maximo_destino"))) continue;
            String destNombre = idANombre.getOrDefault(destId, destId);
            String campoFk = campoJava(destNombre) + "Id";
            String variableFk = "{{" + campoJava(destNombre) + "Id}}";
            String tipoFk = tipoId(atribPorClase.getOrDefault(destId, List.of()));
            if (!primero) json.append(",\n");
            json.append("  \"").append(campoFk).append("\": ");
            if (!Set.of("Long", "Integer", "Double").contains(tipoFk)) json.append('"');
            json.append(variableFk);
            if (!Set.of("Long", "Integer", "Double").contains(tipoFk)) json.append('"');
            primero = false;
        }

        for (var r : relaciones) {
            String destId = str(r.get("id_clase_destino"));
            if (!idsPersistentes.contains(destId)
                    || !esMuchos(r.get("maximo_destino"))
                    || r.get("maximo_origen") == null
                    || !esMuchos(r.get("maximo_origen"))) continue;
            String campo = campoJava(idANombre.getOrDefault(destId, destId)) + "sIds";
            if (!primero) json.append(",\n");
            json.append("  \"").append(campo).append("\": []");
            primero = false;
        }

        json.append("\n}");
        return json.toString();
    }

    private String valorJsonSintetico(String campo, String tipo, boolean modificado) {
        return switch (tipo) {
            case "String" -> "\"" + campo + (modificado ? "_modificado" : "_ejemplo") + "\"";
            case "Integer", "int" -> modificado ? "20" : "10";
            case "Long", "long" -> modificado ? "200" : "100";
            case "Double", "Float", "BigDecimal" -> modificado ? "199.99" : "99.50";
            case "Boolean", "boolean" -> modificado ? "false" : "true";
            case "LocalDate" -> modificado ? "\"2026-10-01\"" : "\"2026-09-22\"";
            case "LocalDateTime" ->
                    modificado ? "\"2026-10-01T18:00:00\"" : "\"2026-09-22T15:30:00\"";
            case "UUID", "java.util.UUID" -> "\"a0000000-0000-0000-0000-000000000001\"";
            default -> "\"" + campo + "_valor\"";
        };
    }

    private String escapeJson(String texto) {
        if (texto == null) return "";
        return texto.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean esMuchos(Object maximo) {
        if (maximo == null) return true;
        if (maximo instanceof Number n) {
            return n.intValue() != 1;
        }
        String s = maximo.toString().trim();
        return "*".equals(s)
                || "n".equalsIgnoreCase(s)
                || "m".equalsIgnoreCase(s)
                || (!"1".equals(s) && !"0".equals(s));
    }

    private boolean esObligatorio(Object minimo) {
        if (minimo == null) return false;
        try {
            return Integer.parseInt(minimo.toString()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String tipoSqlId(String tipoId) {
        if (tipoId == null) return "BIGINT";
        return switch (tipoId) {
            case "Long" -> "BIGINT";
            case "Integer" -> "INTEGER";
            case "UUID", "java.util.UUID" -> "UUID";
            default -> "VARCHAR(255)";
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // .gitignore
    // ═══════════════════════════════════════════════════════════════════

    private String gitIgnore() {
        return "target/\n.idea/\n*.iml\n.mvn/\n*.class\n*.jar\n*.war\n"
                + "application-local.properties\n.env\n";
    }

    // ─── Utilidades ZIP ──────────────────────────────────────────────────────

    private void agregar(ZipOutputStream zip, String nombre, String contenido) throws Exception {
        ZipEntry entry = new ZipEntry(nombre);
        zip.putNextEntry(entry);
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ─── Mapeos de tipos ─────────────────────────────────────────────────────

    private String tipoJava(String uml) {
        if (uml == null) return "String";
        return switch (uml) {
            case "Texto", "String", "VARCHAR", "TEXT", "string" -> "String";
            case "Entero", "Integer", "INT", "int" -> "Integer";
            case "Float", "float" -> "Float";
            case "Decimal", "Double", "FLOAT", "double" -> "Double";
            case "Booleano", "Boolean", "boolean", "bool" -> "Boolean";
            case "Fecha", "LocalDate", "DATE", "Date", "date" -> "LocalDate";
            case "FechaHora", "LocalDateTime", "TIMESTAMP", "DateTime", "datetime" ->
                    "LocalDateTime";
            case "Long", "BIGINT", "long" -> "Long";
            case "UUID", "java.util.UUID" -> "UUID";
            case "BigDecimal", "NUMERIC" -> "BigDecimal";
            default -> "String";
        };
    }

    private String tipoJavaAtributo(Map<String, Object> atributo) {
        Object configurado = atributo.get("_tipo_java");
        return configurado == null
                ? tipoJava(str(atributo.get("tipo_uml")))
                : configurado.toString();
    }

    private String tipoSql(String uml) {
        if (uml == null) return "VARCHAR(255)";
        return switch (uml) {
            case "Texto", "String", "VARCHAR", "string" -> "VARCHAR(255)";
            case "TEXT" -> "TEXT";
            case "Entero", "Integer", "INT", "int" -> "INTEGER";
            case "Long", "BIGINT", "long" -> "BIGINT";
            case "Float", "float" -> "REAL";
            case "Decimal", "Double", "FLOAT", "double" -> "DOUBLE PRECISION";
            case "Booleano", "Boolean", "boolean", "bool" -> "BOOLEAN";
            case "Fecha", "LocalDate", "DATE", "Date", "date" -> "DATE";
            case "FechaHora", "LocalDateTime", "TIMESTAMP", "DateTime", "datetime" -> "TIMESTAMP";
            case "UUID", "java.util.UUID" -> "UUID";
            case "BigDecimal", "NUMERIC" -> "NUMERIC";
            default -> "VARCHAR(255)";
        };
    }

    private String tipoSqlAtributo(Map<String, Object> atributo) {
        Object configurado = atributo.get("_tipo_sql");
        return configurado == null
                ? tipoSql(str(atributo.get("tipo_uml")))
                : configurado.toString();
    }

    private void aplicarMapeos(
            List<Map<String, Object>> atributos, List<MapeoTipo> mapeosConfigurados) {
        Map<String, MapeoTipo> mapeos = new HashMap<>();
        for (MapeoTipo mapeo : mapeosConfigurados)
            mapeos.put(mapeo.tipoUml().toLowerCase(Locale.ROOT), mapeo);
        for (Map<String, Object> atributo : atributos) {
            MapeoTipo mapeo = mapeos.get(str(atributo.get("tipo_uml")).toLowerCase(Locale.ROOT));
            if (mapeo != null) {
                atributo.put("_tipo_java", mapeo.tipoJava());
                atributo.put("_tipo_sql", mapeo.tipoPostgresql());
            }
        }
    }

    private List<Dependencia> dependenciasPredeterminadas() {
        return List.of(
                new Dependencia(
                        "org.springframework.boot",
                        "spring-boot-starter-web",
                        null,
                        "COMPILACION",
                        true),
                new Dependencia(
                        "org.springframework.boot",
                        "spring-boot-starter-data-jpa",
                        null,
                        "COMPILACION",
                        true),
                new Dependencia(
                        "org.springframework.boot",
                        "spring-boot-starter-validation",
                        null,
                        "COMPILACION",
                        true),
                new Dependencia("org.postgresql", "postgresql", null, "EJECUCION", true),
                new Dependencia(
                        "org.springdoc",
                        "springdoc-openapi-starter-webmvc-ui",
                        "2.8.9",
                        "COMPILACION",
                        true),
                new Dependencia(
                        "org.springframework.boot",
                        "spring-boot-starter-test",
                        null,
                        "PRUEBA",
                        true));
    }

    private int contarArchivos(byte[] contenido) {
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            while (zip.getNextEntry() != null) total++;
            return total;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo contar el contenido generado.", e);
        }
    }

    private String xml(String valor) {
        return valor.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // ─── Transformaciones de nombres ─────────────────────────────────────────

    /** PascalCase para clases: "mi_clase" → "MiClase". */
    private String nomClase(String s) {
        if (s == null || s.isBlank()) return "Entidad";
        String[] partes = s.trim().split("[_\\s\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /** camelCase para campos: "MiClase" → "miClase". */
    private String campoJava(String s) {
        String cls = nomClase(s);
        if (cls.isEmpty()) return "campo";
        return Character.toLowerCase(cls.charAt(0)) + cls.substring(1);
    }

    /** snake_case para tablas y columnas: "MiClase" → "mi_clase". */
    private String snakeCase(String s) {
        if (s == null) return "";
        return s.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("[\\s\\-]+", "_")
                .toLowerCase();
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Map<String, Object> identificador(List<Map<String, Object>> atributos) {
        var definido = atributos.stream()
                .filter(a -> bool(a.get("es_identificador")))
                .findFirst();
        if (definido.isPresent()) return definido.get();
        Set<String> nombres = atributos.stream().map(a -> campoJava(str(a.get("nombre"))))
                .collect(Collectors.toSet());
        String tecnico = "id";
        for (int n = 1; nombres.contains(tecnico); n++) tecnico = "idBackend" + (n == 1 ? "" : n);
        return Map.of("nombre", tecnico, "tipo_uml", "Long");
    }

    private String tipoId(List<Map<String, Object>> atributos) {
        String tipo = tipoJavaAtributo(identificador(atributos));
        return "UUID".equals(tipo) ? "java.util.UUID" : tipo;
    }

    private boolean maximoNoEscalar(Map<String, Object> atributo) {
        // Los contratos anteriores podían omitir el máximo escalar; null explícito sigue siendo *.
        Object maximo = atributo.getOrDefault("multiplicidad_maxima", 1);
        return !(maximo instanceof Number numero) || numero.doubleValue() != 1;
    }

    private boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }
}
