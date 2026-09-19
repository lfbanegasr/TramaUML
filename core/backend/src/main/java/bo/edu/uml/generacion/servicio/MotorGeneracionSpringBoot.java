package bo.edu.uml.generacion.servicio;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Motor de generación de proyectos Spring Boot completos en 4 capas.
 *
 * <p>Produce un archivo ZIP en memoria con la estructura estándar Maven que contiene:
 *
 * <ul>
 *   <li><b>Capa Modelo:</b> Entidades JPA {@code @Entity}, {@code @Table}, {@code @Id},
 *       {@code @ManyToOne}/{@code @OneToMany} según cardinalidades.
 *   <li><b>Capa Repositorio:</b> Interfaces {@code JpaRepository<T, Long>}.
 *   <li><b>Capa Controlador:</b> {@code @RestController} con CRUD completo.
 *   <li><b>Capa Vista (DTOs):</b> Records inmutables de solicitud/respuesta.
 *   <li><b>Infraestructura:</b> {@code pom.xml} ejecutable, {@code application.properties}, {@code
 *       schema.sql} DDL, {@code .gitignore}.
 * </ul>
 */
@Service
public class MotorGeneracionSpringBoot {

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

        // Solo generamos código para clases persistentes
        List<Map<String, Object>> persistentes =
                clases.stream().filter(c -> bool(c.get("es_persistente"))).toList();

        // Índices
        Map<String, String> idANombre = new HashMap<>();
        for (var c : clases) idANombre.put(str(c.get("id")), str(c.get("nombre")));

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

        String paqueteRuta = paqueteBase.replace('.', '/');
        String dirBase = artifactId + "/src/main/java/" + paqueteRuta + "/";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {

            // pom.xml
            agregar(zip, artifactId + "/pom.xml", generarPom(groupId, artifactId, version));

            // .gitignore
            agregar(zip, artifactId + "/.gitignore", gitIgnore());

            // Application.java
            agregar(
                    zip,
                    dirBase + "Application.java",
                    generarApplication(paqueteBase, nomClase(artifactId)));

            // OpenApiConfig.java
            agregar(
                    zip,
                    dirBase + "config/OpenApiConfig.java",
                    generarOpenApiConfig(paqueteBase, artifactId, version));

            // application.properties
            agregar(
                    zip,
                    artifactId + "/src/main/resources/application.properties",
                    generarApplicationProperties(artifactId));

            // schema.sql
            agregar(
                    zip,
                    artifactId + "/src/main/resources/schema.sql",
                    generarSchemaSql(persistentes, atribPorClase));

            // Capas por clase persistente
            int archivos = 6; // pom + gitignore + Application + OpenApiConfig + properties + schema
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
                        generarEntidad(paqueteBase, cls, nombre, atr, rels, idANombre));
                archivos++;

                // Repositorio
                agregar(
                        zip,
                        dirBase + "repositorio/" + cls + "Repositorio.java",
                        generarRepositorio(paqueteBase, cls));
                archivos++;

                // DTO
                agregar(
                        zip,
                        dirBase + "dto/" + cls + "Dto.java",
                        generarDto(paqueteBase, cls, atr));
                archivos++;

                // Controlador
                agregar(
                        zip,
                        dirBase + "controlador/" + cls + "Controlador.java",
                        generarControlador(paqueteBase, cls, nombre));
                archivos++;
            }

            zip.finish();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error generando proyecto Spring Boot: " + e.getMessage(), e);
        }

        return bos.toByteArray();
    }

    // ═══════════════════════════════════════════════════════════════════
    // pom.xml
    // ═══════════════════════════════════════════════════════════════════

    private String generarPom(String groupId, String artifactId, String version) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
                + " https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <parent>\n"
                + "    <groupId>org.springframework.boot</groupId>\n"
                + "    <artifactId>spring-boot-starter-parent</artifactId>\n"
                + "    <version>3.5.16</version>\n"
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
                + "    <java.version>21</java.version>\n"
                + "  </properties>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>org.springframework.boot</groupId>\n"
                + "      <artifactId>spring-boot-starter-web</artifactId>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>org.springframework.boot</groupId>\n"
                + "      <artifactId>spring-boot-starter-data-jpa</artifactId>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>org.springframework.boot</groupId>\n"
                + "      <artifactId>spring-boot-starter-validation</artifactId>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>org.postgresql</groupId>\n"
                + "      <artifactId>postgresql</artifactId>\n"
                + "      <scope>runtime</scope>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>org.springdoc</groupId>\n"
                + "      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>\n"
                + "      <version>2.8.9</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>org.springframework.boot</groupId>\n"
                + "      <artifactId>spring-boot-starter-test</artifactId>\n"
                + "      <scope>test</scope>\n"
                + "    </dependency>\n"
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
    // application.properties
    // ═══════════════════════════════════════════════════════════════════

    private String generarApplicationProperties(String artifactId) {
        return "# Configuración generada por Plataforma UML Colaborativa\n"
                + "spring.application.name="
                + artifactId
                + "\n\n"
                + "# Base de datos PostgreSQL — ajusta host, puerto y credenciales\n"
                + "spring.datasource.url=jdbc:postgresql://localhost:5432/"
                + artifactId
                + "\n"
                + "spring.datasource.username=postgres\n"
                + "spring.datasource.password=\n"
                + "spring.datasource.driver-class-name=org.postgresql.Driver\n\n"
                + "# JPA / Hibernate\n"
                + "spring.jpa.hibernate.ddl-auto=validate\n"
                + "spring.jpa.show-sql=false\n"
                + "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect\n\n"
                + "# Inicialización del esquema\n"
                + "spring.sql.init.mode=always\n"
                + "spring.sql.init.schema-locations=classpath:schema.sql\n\n"
                + "# Swagger UI\n"
                + "springdoc.swagger-ui.path=/swagger-ui.html\n"
                + "springdoc.api-docs.path=/api-docs\n\n"
                + "# Servidor\n"
                + "server.port=8080\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // schema.sql — DDL
    // ═══════════════════════════════════════════════════════════════════

    private String generarSchemaSql(
            List<Map<String, Object>> persistentes,
            Map<String, List<Map<String, Object>>> atribPorClase) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Schema generado por Plataforma UML Colaborativa\n\n");
        for (var c : persistentes) {
            String tabla = snakeCase(str(c.get("nombre")));
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tabla).append(" (\n");
            sb.append("    id BIGSERIAL PRIMARY KEY");
            List<Map<String, Object>> atr = atribPorClase.getOrDefault(str(c.get("id")), List.of());
            for (var a : atr) {
                if (bool(a.get("es_identificador"))) continue; // id ya generado
                sb.append(",\n    ")
                        .append(snakeCase(str(a.get("nombre"))))
                        .append(" ")
                        .append(tipoSql(str(a.get("tipo_uml"))));
                if (bool(a.get("es_unico"))) sb.append(" UNIQUE");
            }
            sb.append("\n);\n\n");
        }
        return sb.toString();
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
            Map<String, String> idANombre) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paqueteBase).append(".modelo;\n\n");
        sb.append("import jakarta.persistence.*;\n");
        sb.append("import java.io.Serializable;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.util.List;\n\n");
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(snakeCase(nombreTabla)).append("\")\n");
        sb.append("public class ").append(cls).append(" implements Serializable {\n\n");
        sb.append("    @Id\n");
        sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        sb.append("    private Long id;\n");

        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String tipo = tipoJava(str(a.get("tipo_uml")));
            sb.append("\n    @Column(name = \"")
                    .append(snakeCase(str(a.get("nombre"))))
                    .append("\"");
            if (bool(a.get("es_unico"))) sb.append(", unique = true");
            sb.append(")\n");
            sb.append("    private ").append(tipo).append(" ").append(campo).append(";\n");
        }

        // Relaciones
        for (var r : relaciones) {
            String destId = str(r.get("id_clase_destino"));
            String destNombre = idANombre.getOrDefault(destId, destId);
            String destCls = nomClase(destNombre);
            String tipoRel = str(r.get("tipo_relacion"));
            Object maxDest = r.get("maximo_destino");
            boolean esColeccion =
                    maxDest == null || (maxDest instanceof Number n && n.intValue() != 1);

            if (esColeccion) {
                sb.append("\n    @OneToMany(mappedBy = \"")
                        .append(campoJava(cls))
                        .append("\", cascade = CascadeType.ALL)\n");
                sb.append("    private List<")
                        .append(destCls)
                        .append("> ")
                        .append(campoJava(destNombre) + "s")
                        .append(";\n");
            } else {
                sb.append("\n    @ManyToOne(fetch = FetchType.LAZY)\n");
                sb.append("    @JoinColumn(name = \"")
                        .append(snakeCase(destNombre))
                        .append("_id\")\n");
                sb.append("    private ")
                        .append(destCls)
                        .append(" ")
                        .append(campoJava(destNombre))
                        .append(";\n");
            }
        }

        // Getters y setters
        sb.append("\n    public Long getId() { return id; }\n");
        sb.append("    public void setId(Long id) { this.id = id; }\n");

        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String tipo = tipoJava(str(a.get("tipo_uml")));
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

        sb.append("}\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Repositorio
    // ═══════════════════════════════════════════════════════════════════

    private String generarRepositorio(String paqueteBase, String cls) {
        return "package "
                + paqueteBase
                + ".repositorio;\n\n"
                + "import "
                + paqueteBase
                + ".modelo."
                + cls
                + ";\n"
                + "import org.springframework.data.jpa.repository.JpaRepository;\n"
                + "import org.springframework.stereotype.Repository;\n\n"
                + "@Repository\n"
                + "public interface "
                + cls
                + "Repositorio extends JpaRepository<"
                + cls
                + ", Long> {\n}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Vista — DTO como Record inmutable
    // ═══════════════════════════════════════════════════════════════════

    private String generarDto(String paqueteBase, String cls, List<Map<String, Object>> atributos) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paqueteBase).append(".dto;\n\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n\n");
        sb.append("public class ").append(cls).append("Dto {\n\n");

        // Record de respuesta
        sb.append("    /** Representa un ").append(cls).append(" en la respuesta de la API. */\n");
        sb.append("    public record Respuesta(\n");
        sb.append("            Long id");
        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            sb.append(",\n            ")
                    .append(tipoJava(str(a.get("tipo_uml"))))
                    .append(" ")
                    .append(campoJava(str(a.get("nombre"))));
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
            sb.append("            ")
                    .append(tipoJava(str(a.get("tipo_uml"))))
                    .append(" ")
                    .append(campoJava(str(a.get("nombre"))));
            primero = false;
        }
        if (primero) sb.append("            // Sin atributos adicionales");
        sb.append(") {}\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Controlador — REST CRUD completo
    // ═══════════════════════════════════════════════════════════════════

    private String generarControlador(String paqueteBase, String cls, String nombre) {
        String ruta = "/" + snakeCase(nombre).replace('_', '-') + "s";
        String campoInst = campoJava(cls);
        String campoRepo = campoInst + "Repositorio";

        return "package "
                + paqueteBase
                + ".controlador;\n\n"
                + "import "
                + paqueteBase
                + ".dto."
                + cls
                + "Dto;\n"
                + "import "
                + paqueteBase
                + ".modelo."
                + cls
                + ";\n"
                + "import "
                + paqueteBase
                + ".repositorio."
                + cls
                + "Repositorio;\n"
                + "import io.swagger.v3.oas.annotations.Operation;\n"
                + "import io.swagger.v3.oas.annotations.tags.Tag;\n"
                + "import jakarta.validation.Valid;\n"
                + "import org.springframework.http.ResponseEntity;\n"
                + "import org.springframework.web.bind.annotation.*;\n\n"
                + "import java.util.List;\n\n"
                + "@RestController\n"
                + "@RequestMapping(\"/api"
                + ruta
                + "\")\n"
                + "@Tag(name = \""
                + cls
                + "\", description = \"CRUD de "
                + nombre
                + "\")\n"
                + "public class "
                + cls
                + "Controlador {\n\n"
                + "    private final "
                + cls
                + "Repositorio "
                + campoRepo
                + ";\n\n"
                + "    public "
                + cls
                + "Controlador("
                + cls
                + "Repositorio "
                + campoRepo
                + ") {\n"
                + "        this."
                + campoRepo
                + " = "
                + campoRepo
                + ";\n"
                + "    }\n\n"
                + "    @GetMapping\n"
                + "    @Operation(summary = \"Listar todos los "
                + nombre
                + "\")\n"
                + "    public List<"
                + cls
                + "> listar() {\n"
                + "        return "
                + campoRepo
                + ".findAll();\n"
                + "    }\n\n"
                + "    @GetMapping(\"/{id}\")\n"
                + "    @Operation(summary = \"Obtener un "
                + nombre
                + " por ID\")\n"
                + "    public ResponseEntity<"
                + cls
                + "> obtener(@PathVariable Long id) {\n"
                + "        return "
                + campoRepo
                + ".findById(id)\n"
                + "                .map(ResponseEntity::ok)\n"
                + "                .orElse(ResponseEntity.notFound().build());\n"
                + "    }\n\n"
                + "    @PostMapping\n"
                + "    @Operation(summary = \"Crear un nuevo "
                + nombre
                + "\")\n"
                + "    public "
                + cls
                + " crear(@Valid @RequestBody "
                + cls
                + " entidad) {\n"
                + "        return "
                + campoRepo
                + ".save(entidad);\n"
                + "    }\n\n"
                + "    @PutMapping(\"/{id}\")\n"
                + "    @Operation(summary = \"Actualizar un "
                + nombre
                + " existente\")\n"
                + "    public ResponseEntity<"
                + cls
                + "> actualizar(\n"
                + "            @PathVariable Long id,\n"
                + "            @Valid @RequestBody "
                + cls
                + " entidad) {\n"
                + "        return "
                + campoRepo
                + ".findById(id)\n"
                + "                .map(existente -> {\n"
                + "                    entidad.setId(id);\n"
                + "                    return ResponseEntity.ok("
                + campoRepo
                + ".save(entidad));\n"
                + "                })\n"
                + "                .orElse(ResponseEntity.notFound().build());\n"
                + "    }\n\n"
                + "    @DeleteMapping(\"/{id}\")\n"
                + "    @Operation(summary = \"Eliminar un "
                + nombre
                + "\")\n"
                + "    public ResponseEntity<Void> eliminar(@PathVariable Long id) {\n"
                + "        if (!"
                + campoRepo
                + ".existsById(id))\n"
                + "            return ResponseEntity.notFound().build();\n"
                + "        "
                + campoRepo
                + ".deleteById(id);\n"
                + "        return ResponseEntity.noContent().build();\n"
                + "    }\n"
                + "}\n";
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
            case "Texto", "String", "VARCHAR", "TEXT" -> "String";
            case "Entero", "Integer", "INT" -> "Integer";
            case "Decimal", "Double", "FLOAT" -> "Double";
            case "Booleano", "Boolean" -> "Boolean";
            case "Fecha", "LocalDate", "DATE" -> "LocalDate";
            case "FechaHora", "LocalDateTime", "TIMESTAMP" -> "LocalDateTime";
            case "Long", "BIGINT" -> "Long";
            default -> "String";
        };
    }

    private String tipoSql(String uml) {
        if (uml == null) return "VARCHAR(255)";
        return switch (uml) {
            case "Texto", "String", "VARCHAR" -> "VARCHAR(255)";
            case "TEXT" -> "TEXT";
            case "Entero", "Integer", "INT" -> "INTEGER";
            case "Long", "BIGINT" -> "BIGINT";
            case "Decimal", "Double", "FLOAT" -> "DOUBLE PRECISION";
            case "Booleano", "Boolean" -> "BOOLEAN";
            case "Fecha", "DATE" -> "DATE";
            case "FechaHora", "TIMESTAMP" -> "TIMESTAMP";
            default -> "VARCHAR(255)";
        };
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

    private boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }
}
