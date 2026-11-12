package bo.edu.uml.servicios.servicio;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Service
public class ComprobadorServicioPlataforma {
    private final HttpClient http;

    public ComprobadorServicioPlataforma() {
        this(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build());
    }

    ComprobadorServicioPlataforma(HttpClient http) {
        this.http = http;
    }

    public record Resultado(String estado, Integer latenciaMs, String detalle) {
        public boolean disponible() {
            return "DISPONIBLE".equals(estado);
        }
    }

    public Resultado comprobar(
            String proveedor, String urlServicio, JsonNode configuracion, String credencial) {
        if ("LOCAL".equals(proveedor))
            return new Resultado(
                    "DISPONIBLE", 0, "Motor local disponible; no requiere conexión externa.");

        String urlComprobacion =
                configuracion.path("urlVerificacion").asText("").isBlank()
                        ? urlServicio
                        : configuracion.path("urlVerificacion").asText();
        // generateContent acepta POST. Su prueba GET debe consultar el recurso del modelo.
        if ("GEMINI".equals(proveedor) && urlComprobacion != null) {
            if (urlComprobacion.startsWith("https://generativelanguage.googleapis.com/")) {
                urlComprobacion = urlComprobacion.replaceFirst(
                        ":(?:generateContent|streamGenerateContent)(?=\\?|$)", "");
            }
        }
        URI uri;
        try {
            uri = validarDestino(urlComprobacion);
        } catch (DestinoNoDisponibleException e) {
            return new Resultado(
                    "NO_DISPONIBLE", null, "El proveedor no respondió; puede reintentarse.");
        }
        var solicitud =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/json")
                        .GET();
        if (credencial != null && !credencial.isBlank()) {
            if ("GEMINI".equals(proveedor)) solicitud.header("x-goog-api-key", credencial);
            else solicitud.header("Authorization", "Bearer " + credencial);
        }
        long inicio = System.nanoTime();
        try {
            int codigo =
                    http.send(solicitud.build(), HttpResponse.BodyHandlers.discarding())
                            .statusCode();
            int latencia =
                    (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - inicio) / 1_000_000L);
            if (codigo >= 200 && codigo < 300)
                return new Resultado(
                        "DISPONIBLE", latencia, "El proveedor respondió correctamente.");
            if (codigo == 401 || codigo == 403)
                return new Resultado(
                        "NO_DISPONIBLE", latencia, "El proveedor rechazó la credencial.");
            if (codigo == 408 || codigo == 429 || codigo >= 500)
                return new Resultado(
                        "DEGRADADO", latencia, "El proveedor respondió temporalmente degradado.");
            return new Resultado(
                    "NO_DISPONIBLE", latencia, "El proveedor rechazó la solicitud de prueba.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resultado(
                    "NO_DISPONIBLE", null, "La comprobación fue interrumpida; puede reintentarse.");
        } catch (Exception e) {
            return new Resultado(
                    "NO_DISPONIBLE", null, "El proveedor no respondió; puede reintentarse.");
        }
    }

    public Resultado comprobarImagenGemini(String modelo, String credencial) {
        if (!modelo.matches("gemini-[a-zA-Z0-9.-]+"))
            return new Resultado("NO_DISPONIBLE", null, "El identificador del modelo Gemini no es válido.");
        if (credencial == null || credencial.isBlank())
            return new Resultado("NO_DISPONIBLE", null, "No hay una clave de Gemini configurada.");
        long inicio = System.nanoTime();
        try {
            URI uri = validarDestino("https://generativelanguage.googleapis.com/v1beta/models/"
                    + modelo + ":generateContent");
            // Imagen local de prueba, sin datos del usuario y sin escribir archivos.
            var imagen = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) imagen.setRGB(x, y, 0x176b52);
            var png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(imagen, "png", png);
            var json = new ObjectMapper();
            var cuerpo = json.createObjectNode();
            var partes = cuerpo.putArray("contents").addObject().putArray("parts");
            partes.addObject().put("text", "Describe el color predominante de esta imagen en una frase breve.");
            partes.addObject().putObject("inline_data").put("mime_type", "image/png")
                    .put("data", java.util.Base64.getEncoder().encodeToString(png.toByteArray()));
            var generacion = cuerpo.putObject("generationConfig").put("maxOutputTokens", 1024);
            if (modelo.startsWith("gemini-3"))
                generacion.putObject("thinkingConfig").put("thinkingLevel", "low");
            else generacion.putObject("thinkingConfig").put("thinkingBudget", 0);
            var solicitud = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", credencial)
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())).build();
            var respuesta = http.send(solicitud, HttpResponse.BodyHandlers.ofString());
            int latencia = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - inicio) / 1_000_000L);
            int codigo = respuesta.statusCode();
            if (codigo >= 200 && codigo < 300) {
                var resultado = json.readTree(respuesta.body());
                for (var parte : resultado.path("candidates").path(0).path("content").path("parts")) {
                    if (!parte.path("thought").asBoolean(false) && !parte.path("text").asText("").isBlank())
                        return new Resultado("DISPONIBLE", latencia, "Gemini analizó correctamente la imagen de prueba.");
                }
                return new Resultado("NO_DISPONIBLE", latencia, "Gemini respondió sin un análisis de imagen utilizable.");
            }
            if (codigo == 401 || (codigo == 400 && respuesta.body().contains("API_KEY_INVALID")))
                return new Resultado("NO_DISPONIBLE", latencia, "Gemini rechazó la credencial configurada.");
            if (codigo == 403)
                return new Resultado("NO_DISPONIBLE", latencia, "Google no autorizó el acceso a este modelo de imagen.");
            if (codigo == 429)
                return new Resultado("DEGRADADO", latencia, "Gemini agotó la cuota disponible. Revisa los límites de la API key en Google AI Studio.");
            if (codigo == 404)
                return new Resultado("NO_DISPONIBLE", latencia, "El modelo no está disponible para esta API key.");
            return new Resultado("NO_DISPONIBLE", latencia,
                    "Gemini no pudo procesar la imagen de prueba (HTTP " + codigo + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resultado("NO_DISPONIBLE", null, "La prueba de imagen fue interrumpida.");
        } catch (Exception e) {
            return new Resultado("NO_DISPONIBLE", null, "Gemini no respondió a la prueba de imagen; reintenta la conexión.");
        }
    }

    URI validarDestino(String valor) {
        try {
            URI uri = URI.create(valor == null ? "" : valor.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null)
                throw new ApiException(
                        422, "Los servicios remotos requieren una URL HTTPS sin credenciales.");
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".internal"))
                throw new ApiException(
                        422, "La URL del servicio no puede apuntar a una red interna.");
            for (InetAddress direccion : InetAddress.getAllByName(host))
                if (privada(direccion))
                    throw new ApiException(
                            422, "La URL del servicio no puede apuntar a una red interna.");
            return uri;
        } catch (ApiException e) {
            throw e;
        } catch (UnknownHostException e) {
            throw new DestinoNoDisponibleException();
        } catch (Exception e) {
            throw new ApiException(422, "La URL del servicio no es válida.");
        }
    }

    private static final class DestinoNoDisponibleException extends RuntimeException {}

    private boolean privada(InetAddress direccion) {
        if (direccion.isAnyLocalAddress()
                || direccion.isLoopbackAddress()
                || direccion.isLinkLocalAddress()
                || direccion.isSiteLocalAddress()
                || direccion.isMulticastAddress()) return true;
        if (direccion instanceof Inet6Address) {
            byte primero = direccion.getAddress()[0];
            return (primero & 0xfe) == 0xfc;
        }
        return false;
    }
}
