package bo.edu.uml.generacion.servicio;

import bo.edu.uml.generacion.dto.GeneracionDto.Dependencia;
import bo.edu.uml.generacion.dto.GeneracionDto.MapeoTipo;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
 *   <li><b>Capa Repositorio:</b> Interfaces {@code JpaRepository<T, Long>}.
 *   <li><b>Capa Controlador:</b> {@code @RestController} con CRUD completo.
 *   <li><b>Capa Vista (DTOs):</b> Records inmutables de solicitud/respuesta.
 *   <li><b>Infraestructura:</b> {@code pom.xml} ejecutable, {@code application.properties}, {@code
 *       schema.sql} DDL, {@code .gitignore}.
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

        aplicarMapeos(atributos, opciones.mapeosTipo());

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

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {

            // pom.xml
            agregar(
                    zip,
                    artifactId + "/pom.xml",
                    generarPom(groupId, artifactId, version, opciones));

            // .gitignore
            agregar(zip, artifactId + "/.gitignore", gitIgnore());

            // Application.java
            agregar(
                    zip,
                    dirBase + nomClase(artifactId) + "Application.java",
                    generarApplication(paqueteBase, nomClase(artifactId)));

            int archivos = 3;
            if (opciones.exponerRest()) {
                agregar(
                        zip,
                        dirBase + "config/OpenApiConfig.java",
                        generarOpenApiConfig(paqueteBase, artifactId, version));
                archivos++;
            }

            // application.properties
            agregar(
                    zip,
                    artifactId + "/src/main/resources/application.properties",
                    generarApplicationProperties(artifactId, opciones.herramientaMigracion()));
            archivos++;

            // schema.sql
            String esquema = generarSchemaSql(persistentes, atribPorClase);
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
            archivos++;

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
                                idANombre));
                archivos++;

                if (opciones.generarRepositorios()) {
                    agregar(
                            zip,
                            dirBase + "repositorio/" + cls + "Repositorio.java",
                            generarRepositorio(paqueteBase, cls, tipoId(atr)));
                    archivos++;
                }

                if (opciones.generarDtos()) {
                    agregar(
                            zip,
                            dirBase + "dto/" + cls + "Dto.java",
                            generarDto(paqueteBase, cls, atr));
                    archivos++;
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
                    archivos++;
                }

                if (opciones.generarControladores()
                        && (!c.containsKey("exponer_rest") || bool(c.get("exponer_rest")))) {
                    agregar(
                            zip,
                            dirBase + "controlador/" + cls + "Controlador.java",
                            generarControlador(
                                    paqueteBase, cls, nombre, tipoId(atr), opciones.exponerRest()));
                    archivos++;
                }
            }

            zip.finish();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error generando proyecto Spring Boot: " + e.getMessage(), e);
        }

        return new ResultadoMotor(bos.toByteArray(), contarArchivos(bos.toByteArray()));
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
                + inicializacion
                + "\n"
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
            String tabla = str(c.getOrDefault("nombre_tabla", snakeCase(str(c.get("nombre")))));
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tabla).append(" (\n");
            List<Map<String, Object>> atr = atribPorClase.getOrDefault(str(c.get("id")), List.of());
            var identificador = identificador(atr);
            sb.append("    ")
                    .append(snakeCase(str(identificador.get("nombre"))))
                    .append(" ")
                    .append(
                            "Long".equals(tipoId(atr))
                                    ? "BIGSERIAL"
                                    : tipoSqlAtributo(identificador))
                    .append(" PRIMARY KEY");
            for (var a : atr) {
                if (bool(a.get("es_identificador"))) continue; // id ya generado
                sb.append(",\n    ")
                        .append(snakeCase(str(a.get("nombre"))))
                        .append(" ")
                        .append(tipoSqlAtributo(a));
                if (bool(a.get("es_unico"))) sb.append(" UNIQUE");
            }
            sb.append("\n);\n\n");
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
            Map<String, String> idANombre) {
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

        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            String campo = campoJava(str(a.get("nombre")));
            String tipo = tipoJavaAtributo(a);
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
                + "import org.springframework.stereotype.Repository;\n\n"
                + "@Repository\n"
                + "public interface "
                + cls
                + "Repositorio extends JpaRepository<"
                + cls
                + ", "
                + tipoId
                + "> {\n}\n";
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capa Vista — DTO como Record inmutable
    // ═══════════════════════════════════════════════════════════════════

    private String generarDto(String paqueteBase, String cls, List<Map<String, Object>> atributos) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paqueteBase).append(".dto;\n\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.UUID;\n\n");
        sb.append("public class ").append(cls).append("Dto {\n\n");

        // Record de respuesta
        sb.append("    /** Representa un ").append(cls).append(" en la respuesta de la API. */\n");
        sb.append("    public record Respuesta(\n");
        sb.append("            ").append(tipoId(atributos)).append(" id");
        for (var a : atributos) {
            if (bool(a.get("es_identificador"))) continue;
            sb.append(",\n            ")
                    .append(tipoJavaAtributo(a))
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
                    .append(tipoJavaAtributo(a))
                    .append(" ")
                    .append(campoJava(str(a.get("nombre"))));
            primero = false;
        }
        if (primero) sb.append("            /* Sin atributos adicionales */");
        sb.append(") {}\n");
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
            String paqueteBase, String cls, String nombre, String tipoId, boolean documentarRest) {
        String ruta = "/" + snakeCase(nombre).replace('_', '-') + "s";
        String campoInst = campoJava(cls);
        String campoRepo = campoInst + "Repositorio";

        return "package "
                + paqueteBase
                + ".controlador;\n\n"
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
                + (documentarRest
                        ? "import io.swagger.v3.oas.annotations.Operation;\n"
                                + "import io.swagger.v3.oas.annotations.tags.Tag;\n"
                        : "")
                + "import jakarta.validation.Valid;\n"
                + "import org.springframework.http.ResponseEntity;\n"
                + "import org.springframework.web.bind.annotation.*;\n\n"
                + "import java.util.List;\n\n"
                + "@RestController\n"
                + "@RequestMapping(\"/api"
                + ruta
                + "\")\n"
                + (documentarRest
                        ? "@Tag(name = \"" + cls + "\", description = \"CRUD de " + nombre + "\")\n"
                        : "")
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
                + (documentarRest
                        ? "    @Operation(summary = \"Listar todos los " + nombre + "\")\n"
                        : "")
                + "    public List<"
                + cls
                + "> listar() {\n"
                + "        return "
                + campoRepo
                + ".findAll();\n"
                + "    }\n\n"
                + "    @GetMapping(\"/{id}\")\n"
                + (documentarRest
                        ? "    @Operation(summary = \"Obtener un " + nombre + " por ID\")\n"
                        : "")
                + "    public ResponseEntity<"
                + cls
                + "> obtener(@PathVariable "
                + tipoId
                + " id) {\n"
                + "        return "
                + campoRepo
                + ".findById(id)\n"
                + "                .map(ResponseEntity::ok)\n"
                + "                .orElse(ResponseEntity.notFound().build());\n"
                + "    }\n\n"
                + "    @PostMapping\n"
                + (documentarRest
                        ? "    @Operation(summary = \"Crear un nuevo " + nombre + "\")\n"
                        : "")
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
                + (documentarRest
                        ? "    @Operation(summary = \"Actualizar un " + nombre + " existente\")\n"
                        : "")
                + "    public ResponseEntity<"
                + cls
                + "> actualizar(\n"
                + "            @PathVariable "
                + tipoId
                + " id,\n"
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
                + (documentarRest
                        ? "    @Operation(summary = \"Eliminar un " + nombre + "\")\n"
                        : "")
                + "    public ResponseEntity<Void> eliminar(@PathVariable "
                + tipoId
                + " id) {\n"
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

    private String tipoJavaAtributo(Map<String, Object> atributo) {
        Object configurado = atributo.get("_tipo_java");
        return configurado == null
                ? tipoJava(str(atributo.get("tipo_uml")))
                : configurado.toString();
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
        return atributos.stream()
                .filter(a -> bool(a.get("es_identificador")))
                .findFirst()
                .orElse(Map.of("nombre", "id", "tipo_uml", "Long"));
    }

    private String tipoId(List<Map<String, Object>> atributos) {
        String tipo = tipoJavaAtributo(identificador(atributos));
        return "UUID".equals(tipo) ? "java.util.UUID" : tipo;
    }

    private boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }
}
