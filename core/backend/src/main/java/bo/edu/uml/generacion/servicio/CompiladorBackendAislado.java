package bo.edu.uml.generacion.servicio;

import bo.edu.uml.generacion.dto.GeneracionDto.*;
import bo.edu.uml.generacion.dto.ValidacionBackendDto.*;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.*;
import bo.edu.uml.generacion.repositorio.ValidacionBackendRepositorio;
import bo.edu.uml.generacion.repositorio.ValidacionBackendRepositorio.Artefacto;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Compila fuentes, sin ejecutar la aplicación, pruebas ni procesadores de anotaciones. */
@Component
public class CompiladorBackendAislado {
    private final String mavenHome;
    private final String repositorio;
    private final String directorio;
    private final int segundos;

    public CompiladorBackendAislado(
            @Value("${uml.compilacion.maven-home:}") String mavenHome,
            @Value("${uml.compilacion.repositorio:}") String repositorio,
            @Value("${uml.compilacion.directorio:}") String directorio,
            @Value("${uml.compilacion.segundos:90}") int segundos) {
        this.mavenHome = mavenHome;
        this.repositorio = repositorio;
        this.directorio = directorio;
        this.segundos = Math.clamp(segundos, 1, 180);
    }

    public record Entrada(
            Artefacto artefacto,
            ConfiguracionPersistida configuracion,
            JsonNode snapshot,
            List<ClasePersistida> clases,
            List<MapeoTipo> mapeos,
            List<Dependencia> dependencias) {}

    public Resultado validar(Entrada entrada) {
        String hash = ValidacionBackendRepositorio.sha256(entrada.artefacto().contenido());
        List<Hallazgo> hallazgos = new ArrayList<>();
        Path trabajo = null;
        Path raiz = null;
        try {
            if (!hash.equals(entrada.artefacto().sha256()))
                return resultado(
                        "INVALIDO",
                        0,
                        List.of(
                                Hallazgo.error(
                                        "SEGURIDAD",
                                        "El ZIP no coincide con su huella registrada.")),
                        "",
                        hash);
            var archivos = leerZip(entrada.artefacto().contenido());
            var cfg = entrada.configuracion();
            String prefijo = cfg.idArtefacto() + "/";
            if (archivos.keySet().stream().anyMatch(n -> !n.startsWith(prefijo)))
                throw new IllegalArgumentException(
                        "El ZIP contiene archivos ajenos a la raíz del proyecto.");
            comprobarPom(archivos.get(prefijo + "pom.xml"), entrada);
            compararModelo(entrada, archivos, hallazgos);
            String propiedades =
                    texto(archivos, prefijo + "src/main/resources/application.properties");
            if (!propiedades.contains("jdbc:postgresql:")
                    || !propiedades.contains("spring.datasource.password="))
                hallazgos.add(
                        Hallazgo.error(
                                "CONFIGURACION_POSTGRESQL",
                                "Falta la configuración PostgreSQL del proyecto."));
            if (hallazgos.stream().anyMatch(h -> "ERROR".equals(h.severidad())))
                return resultado(
                        "INVALIDO", 2, hallazgos, "Compilación omitida por errores previos.", hash);
            if (!"3.5.16".equals(cfg.versionSpringBoot()))
                return resultado(
                        "INCOMPLETO",
                        3,
                        List.of(
                                Hallazgo.error(
                                        "DEPENDENCIA",
                                        "El compilador habilitado admite Spring Boot 3.5.16."
                                            + " Selecciona esa versión o instala un perfil de"
                                            + " compilación compatible.")),
                        "",
                        hash);

            Path base = buscarBase();
            Path maven = ruta(mavenHome, base.resolve(".qa/tools/apache-maven-3.9.9"));
            Path cache = ruta(repositorio, base.resolve(".qa/maven-repository"));
            if (!Files.isDirectory(maven.resolve("boot")) || !Files.isDirectory(cache))
                return resultado(
                        "INCOMPLETO",
                        3,
                        List.of(
                                Hallazgo.error(
                                        "DEPENDENCIA",
                                        "Falta preparar Maven y la caché local de dependencias del"
                                            + " compilador.")),
                        "",
                        hash);
            raiz = ruta(directorio, base.resolve(".qa/compilaciones"));
            Files.createDirectories(raiz);
            trabajo = Files.createTempDirectory(raiz, "backend-");
            Path home = Files.createDirectory(trabajo.resolve("home"));
            Path tmp = Files.createDirectory(trabajo.resolve("tmp"));
            for (var archivo : archivos.entrySet()) {
                Path destino = trabajo.resolve(archivo.getKey()).normalize();
                if (!destino.startsWith(trabajo))
                    throw new IllegalArgumentException("Ruta ZIP no permitida.");
                Files.createDirectories(destino.getParent());
                Files.write(destino, archivo.getValue(), StandardOpenOption.CREATE_NEW);
            }
            Path settings = trabajo.resolve("settings.xml");
            Files.writeString(
                    settings,
                    "<settings"
                        + " xmlns=\"http://maven.apache.org/SETTINGS/1.2.0\"><offline>true</offline></settings>");
            Path boot;
            try (var jars = Files.list(maven.resolve("boot"))) {
                boot =
                        jars.filter(
                                        p ->
                                                p.getFileName()
                                                        .toString()
                                                        .matches(
                                                                "plexus-classworlds-[0-9.]+\\.jar"))
                                .findFirst()
                                .orElseThrow(
                                        () -> new IOException("Distribución Maven incompleta."));
            }
            Path proyecto = trabajo.resolve(cfg.idArtefacto());
            List<String> comando =
                    List.of(
                            javaEjecutable(),
                            "-Xmx384m",
                            "-XX:ActiveProcessorCount=2",
                            "-Duser.home=" + home,
                            "-Djava.io.tmpdir=" + tmp,
                            "-Dmaven.home=" + maven,
                            "-Dmaven.multiModuleProjectDirectory=" + proyecto,
                            "-Dclassworlds.conf=" + maven.resolve("bin/m2.conf"),
                            "-classpath",
                            boot.toString(),
                            "org.codehaus.plexus.classworlds.launcher.Launcher",
                            "-o",
                            "-B",
                            "-ntp",
                            "-s",
                            settings.toString(),
                            "-gs",
                            settings.toString(),
                            "-Dmaven.repo.local=" + cache,
                            "-Dmaven.compiler.proc=none",
                            "-Dmaven.compiler.fork=false",
                            "-Dmaven.compiler.release=" + cfg.versionJava(),
                            "-Dmaven.compiler.useIncrementalCompilation=false",
                            "org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile");
            var proceso = ejecutar(comando, proyecto, tmp, Duration.ofSeconds(segundos));
            String registro =
                    proceso.salida()
                            .replace(trabajo.toString(), "[temporal]")
                            .replace(maven.toString(), "[maven]")
                            .replace(cache.toString(), "[dependencias]");
            if (proceso.agotado()) {
                hallazgos.add(
                        Hallazgo.error(
                                "COMPILACION",
                                "La compilación superó el límite de tiempo; el proceso fue"
                                    + " terminado."));
                return resultado("FALLIDO", 3, hallazgos, registro, hash);
            }
            if (proceso.codigo() != 0) {
                boolean codigo =
                        registro.contains("COMPILATION ERROR")
                                || registro.contains("compilation failure");
                hallazgos.add(
                        Hallazgo.error(
                                codigo ? "COMPILACION" : "DEPENDENCIA",
                                codigo
                                        ? "El código generado no compila. Consulta el registro para"
                                              + " localizar los errores."
                                        : "No se pudo completar la compilación offline. Revisa"
                                              + " Maven, el JDK y las dependencias de la caché"
                                              + " local."));
                return resultado(codigo ? "INVALIDO" : "INCOMPLETO", 3, hallazgos, registro, hash);
            }
            try (var clases = Files.walk(proyecto.resolve("target/classes"))) {
                if (clases.noneMatch(p -> p.toString().endsWith(".class")))
                    throw new IOException("El compilador no produjo clases.");
            }
            hallazgos.add(
                    new Hallazgo(
                            "INFORMACION",
                            "COMPILACION",
                            null,
                            "Compilación Java aprobada. No se ejecutó la aplicación ni se conectó a"
                                + " PostgreSQL.",
                            Map.of()));
            return resultado(
                    hallazgos.stream().anyMatch(h -> "ADVERTENCIA".equals(h.severidad()))
                            ? "VALIDO_CON_ADVERTENCIAS"
                            : "VALIDO",
                    4,
                    hallazgos,
                    registro,
                    hash);
        } catch (IllegalArgumentException e) {
            hallazgos.add(Hallazgo.error("ESTRUCTURA", e.getMessage()));
            return resultado("INVALIDO", 0, hallazgos, "", hash);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return resultado(
                    "INCOMPLETO",
                    0,
                    List.of(
                            Hallazgo.error(
                                    "COMPILACION",
                                    "La compilación fue interrumpida; puede volver a"
                                        + " solicitarse.")),
                    "",
                    hash);
        } catch (Exception e) {
            return resultado(
                    "FALLIDO",
                    0,
                    List.of(
                            Hallazgo.error(
                                    "COMPILACION",
                                    "No se pudo ejecutar o completar el compilador aislado.")),
                    "",
                    hash);
        } finally {
            limpiar(trabajo, raiz);
        }
    }

    public static Map<String, byte[]> leerZip(byte[] zip) throws IOException {
        if (zip == null || zip.length > 32 * 1024 * 1024)
            throw new IllegalArgumentException("ZIP ausente o demasiado grande.");
        Map<String, byte[]> archivos = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String n = e.getName();
                if (n.startsWith("/")
                        || n.contains("\\")
                        || n.contains(":")
                        || Arrays.asList(n.split("/")).contains("..")
                        || n.contains("/.mvn/")
                        || !n.matches("[A-Za-z0-9_./$-]+"))
                    throw new IllegalArgumentException("Ruta ZIP no permitida.");
                if (e.isDirectory()) continue;
                if (archivos.size() >= 1500 || archivos.containsKey(n))
                    throw new IllegalArgumentException(
                            "ZIP con demasiados archivos o nombres duplicados.");
                byte[] datos = in.readNBytes(1024 * 1024 + 1);
                total += datos.length;
                if (datos.length > 1024 * 1024 || total > 48 * 1024 * 1024)
                    throw new IllegalArgumentException(
                            "El contenido descomprimido supera el límite permitido.");
                if (!(n.endsWith("/pom.xml")
                        || n.endsWith("/.gitignore")
                        || n.endsWith("/README.md")
                        || n.endsWith("/docker-compose.yml")
                        || n.endsWith("/Dockerfile")
                        || n.endsWith("/run.bat")
                        || n.endsWith("/run.ps1")
                        || n.endsWith("/postman_collection.json")
                        || n.contains("/src/main/java/") && n.endsWith(".java")
                        || n.contains("/src/test/java/") && n.endsWith(".java")
                        || n.contains("/src/main/resources/")
                                && (n.endsWith(".properties")
                                        || n.endsWith(".sql")
                                        || n.endsWith(".xml"))
                        || n.contains("/docs/") && n.endsWith(".json")))
                    throw new IllegalArgumentException(
                            "El ZIP contiene un archivo no admitido para compilar.");
                archivos.put(n, datos);
            }
        }
        if (archivos.isEmpty())
            throw new IllegalArgumentException("El ZIP está vacío o no es válido.");
        return archivos;
    }

    private void comprobarPom(byte[] bytes, Entrada entrada) throws Exception {
        if (bytes == null) throw new IllegalArgumentException("Falta pom.xml.");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        Set<String> permitidas =
                Set.of(
                        "project",
                        "modelVersion",
                        "parent",
                        "groupId",
                        "artifactId",
                        "version",
                        "relativePath",
                        "name",
                        "description",
                        "properties",
                        "java.version",
                        "dependencies",
                        "dependency",
                        "scope",
                        "build",
                        "plugins",
                        "plugin");
        var nodos = doc.getElementsByTagName("*");
        for (int i = 0; i < nodos.getLength(); i++)
            if (!permitidas.contains(nodos.item(i).getNodeName()))
                throw new IllegalArgumentException(
                        "El POM incluye instrucciones de construcción no permitidas.");
        if (new String(bytes, StandardCharsets.UTF_8).contains("${"))
            throw new IllegalArgumentException(
                    "El POM incluye expansión de propiedades no permitida.");
        var parent = doc.getElementsByTagName("parent");
        if (parent.getLength() != 1
                || !"org.springframework.boot".equals(valor((Element) parent.item(0), "groupId"))
                || !"spring-boot-starter-parent"
                        .equals(valor((Element) parent.item(0), "artifactId"))
                || !entrada.configuracion()
                        .versionSpringBoot()
                        .equals(valor((Element) parent.item(0), "version")))
            throw new IllegalArgumentException(
                    "El POM no corresponde al perfil Spring Boot guardado.");
        var plugins = doc.getElementsByTagName("plugin");
        if (plugins.getLength() != 1
                || !"org.springframework.boot".equals(valor((Element) plugins.item(0), "groupId"))
                || !"spring-boot-maven-plugin"
                        .equals(valor((Element) plugins.item(0), "artifactId")))
            throw new IllegalArgumentException("El POM incluye plugins no permitidos.");
        Set<String> esperadas = new HashSet<>();
        for (var d : entrada.dependencias())
            esperadas.add(
                    d.idGrupo()
                            + ":"
                            + d.idArtefacto()
                            + ":"
                            + (d.version() == null ? "" : d.version()));
        Set<String> presentes = new HashSet<>();
        var deps = doc.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            Element e = (Element) deps.item(i);
            presentes.add(
                    valor(e, "groupId") + ":" + valor(e, "artifactId") + ":" + valor(e, "version"));
        }
        if (!esperadas.equals(presentes))
            throw new IllegalArgumentException(
                    "Las dependencias del ZIP no coinciden con la configuración guardada.");
    }

    private void compararModelo(Entrada e, Map<String, byte[]> archivos, List<Hallazgo> hallazgos) {
        var cfg = e.configuracion();
        String base =
                cfg.idArtefacto() + "/src/main/java/" + cfg.paqueteBase().replace('.', '/') + "/";
        for (var clase : e.clases()) {
            if (!clase.esPersistente()) continue;
            String ruta = base + "modelo/" + clase.nombreEntidad() + ".java";
            String fuente = texto(archivos, ruta);
            if (fuente.isEmpty())
                hallazgos.add(
                        Hallazgo.error(
                                "MAPEO_MODELO",
                                "Falta la entidad seleccionada " + clase.nombreEntidad() + "."));
            for (var atributo : e.snapshot().path("atributos")) {
                if (!clase.idClase().toString().equals(atributo.path("id_clase").asText()))
                    continue;
                String uml = atributo.path("tipo_uml").asText();
                var m =
                        e.mapeos().stream()
                                .filter(t -> t.tipoUml().equalsIgnoreCase(uml))
                                .findFirst();
                String tipo = m.map(MapeoTipo::tipoJava).orElse(uml);
                if (m.isPresent()
                        && m.get().claseConversor() != null
                        && !m.get().claseConversor().isBlank())
                    hallazgos.add(
                            Hallazgo.error(
                                    "MAPEO_MODELO",
                                    "El conversor personalizado de "
                                            + uml
                                            + " requiere implementación antes de validar."));
                String campo = campo(atributo.path("nombre").asText());
                String variantes = switch (tipo.toLowerCase(Locale.ROOT)) {
                    case "int", "integer" -> "(?:Integer|int)";
                    case "long" -> "(?:Long|long)";
                    case "double" -> "(?:Double|double)";
                    case "float" -> "(?:Float|float)";
                    case "boolean", "bool" -> "(?:Boolean|boolean)";
                    case "date", "localdate" -> "(?:LocalDate|Date|java\\.util\\.Date|java\\.time\\.LocalDate)";
                    case "datetime", "localdatetime", "timestamp" -> "(?:LocalDateTime|Timestamp|java\\.util\\.Date|java\\.time\\.LocalDateTime)";
                    default -> "(?:" + Pattern.quote(tipo) + "|java\\.util\\." + Pattern.quote(tipo) + ")";
                };
                String patron = "\\b" + variantes + "\\s+" + Pattern.quote(campo) + "\\s*;";
                if (!Pattern.compile(patron).matcher(fuente).find())
                    hallazgos.add(
                            new Hallazgo(
                                    "ERROR",
                                    "MAPEO_MODELO",
                                    ruta,
                                    "El atributo "
                                            + campo
                                            + " no conserva el tipo "
                                            + tipo
                                            + " de la configuración.",
                                    Map.of("idAtributo", atributo.path("id").asText())));
            }
            if (cfg.generarRepositorios()
                    && !archivos.containsKey(
                            base + "repositorio/" + clase.nombreEntidad() + "Repositorio.java"))
                hallazgos.add(Hallazgo.error("ESTRUCTURA", "Falta un repositorio seleccionado."));
            if (cfg.generarServicios()
                    && !archivos.containsKey(
                            base + "servicio/" + clase.nombreEntidad() + "Servicio.java"))
                hallazgos.add(Hallazgo.error("ESTRUCTURA", "Falta un servicio seleccionado."));
        }
        if (archivos.values().stream()
                .map(b -> new String(b, StandardCharsets.UTF_8))
                .anyMatch(s -> s.contains("UnsupportedOperationException")))
            hallazgos.add(
                    new Hallazgo(
                            "ADVERTENCIA",
                            "MAPEO_MODELO",
                            null,
                            "Las operaciones UML tienen firmas generadas; su lógica de negocio"
                                + " todavía debe implementarse.",
                            Map.of()));
        if (e.snapshot().path("relaciones").size() > 0)
            hallazgos.add(
                    new Hallazgo(
                            "ADVERTENCIA",
                            "CONFIGURACION_POSTGRESQL",
                            null,
                            "La compilación no comprueba el funcionamiento de las relaciones JPA ni"
                                + " ejecuta las migraciones en PostgreSQL.",
                            Map.of()));
    }

    public record Proceso(int codigo, boolean agotado, String salida) {}

    static Proceso ejecutar(List<String> comando, Path cwd, Path tmp, Duration limite)
            throws IOException, InterruptedException {
        var builder = new ProcessBuilder(comando).directory(cwd.toFile()).redirectErrorStream(true);
        Map<String, String> original = Map.copyOf(builder.environment());
        builder.environment().clear();
        for (String nombre : List.of("SystemRoot", "SYSTEMROOT", "WINDIR"))
            if (original.containsKey(nombre))
                builder.environment().put(nombre, original.get(nombre));
        builder.environment().put("TEMP", tmp.toString());
        builder.environment().put("TMP", tmp.toString());
        Process proceso = builder.start();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        Thread lector =
                Thread.ofPlatform()
                        .daemon()
                        .start(
                                () -> {
                                    try (var in = proceso.getInputStream()) {
                                        byte[] buffer = new byte[4096];
                                        int n;
                                        while ((n = in.read(buffer)) != -1) {
                                            synchronized (log) {
                                                if (log.size() < 65536)
                                                    log.write(
                                                            buffer,
                                                            0,
                                                            Math.min(n, 65536 - log.size()));
                                            }
                                        }
                                    } catch (IOException ignored) {
                                    }
                                });
        boolean listo = false;
        try {
            listo = proceso.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS);
            if (!listo) matar(proceso);
            lector.join(2000);
            synchronized (log) {
                return new Proceso(
                        listo ? proceso.exitValue() : -1,
                        !listo,
                        log.toString(StandardCharsets.UTF_8));
            }
        } finally {
            if (proceso.isAlive()) matar(proceso);
        }
    }

    private static void matar(Process proceso) {
        proceso.descendants().forEach(ProcessHandle::destroyForcibly);
        proceso.destroyForcibly();
        try {
            proceso.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void limpiar(Path trabajo, Path raiz) {
        if (trabajo == null
                || raiz == null
                || !trabajo.getParent().equals(raiz)
                || !trabajo.getFileName().toString().startsWith("backend-")) return;
        try (var rutas = Files.walk(trabajo)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(ruta);
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(CompiladorBackendAislado.class)
                    .warn("No se pudo limpiar un directorio temporal de compilación.");
        }
    }

    public static String javaEjecutable() {
        return Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
    }

    private Path buscarBase() {
        Path p = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 5 && p != null; i++, p = p.getParent())
            if (Files.isDirectory(p.resolve(".qa"))) return p;
        return Path.of(System.getProperty("java.io.tmpdir"), "uml-compilador");
    }

    private Path ruta(String valor, Path defecto) {
        return (valor.isBlank() ? defecto : Path.of(valor)).toAbsolutePath().normalize();
    }

    private static String valor(Element e, String tag) {
        var n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
    }

    private String texto(Map<String, byte[]> archivos, String ruta) {
        return new String(archivos.getOrDefault(ruta, new byte[0]), StandardCharsets.UTF_8);
    }

    private String campo(String valor) {
        StringBuilder b = new StringBuilder();
        for (String p : valor.split("[_\\s-]+"))
            if (!p.isEmpty()) b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        if (b.isEmpty()) return "campo";
        b.setCharAt(0, Character.toLowerCase(b.charAt(0)));
        return b.toString();
    }

    private Resultado resultado(
            String estado, int aprobadas, List<Hallazgo> hallazgos, String registro, String hash) {
        return new Resultado(estado, aprobadas, List.copyOf(hallazgos), registro, hash);
    }
}
