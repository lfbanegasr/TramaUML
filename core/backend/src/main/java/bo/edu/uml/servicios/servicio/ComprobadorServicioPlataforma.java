package bo.edu.uml.servicios.servicio;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;

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
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

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
