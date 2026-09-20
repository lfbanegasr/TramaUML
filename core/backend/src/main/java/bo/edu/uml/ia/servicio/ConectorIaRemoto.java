package bo.edu.uml.ia.servicio;

import bo.edu.uml.servicios.servicio.ResolverServicioActivo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;

/**
 * Adaptador deliberadamente opt-in. Prefiere un servicio activo protegido en PostgreSQL y conserva
 * las variables de entorno como compatibilidad. El modo LOCAL no realiza tráfico de red.
 */
@Service
public class ConectorIaRemoto implements ProveedorIa {
    private static final Logger LOG = LoggerFactory.getLogger(ConectorIaRemoto.class);
    private static final String GRAMATICA =
            "Devuelve únicamente una instrucción UML breve en español con esta gramática: "
                    + "'Crea clase Nombre con atributos campo: Tipo y clase Otra con atributo "
                    + "campo: Tipo, con relación 1 a *'. Conserva nombres, tipos y cardinalidades; "
                    + "no uses Markdown ni expliques la respuesta.\n\nEntrada: ";
    private static final String LEER_DIAGRAMA =
            "Reconoce las cajas de clases UML, nombres, atributos con tipo y relaciones. Devuelve "
                    + "únicamente una instrucción en español compatible con: 'Crea clase Nombre "
                    + "con atributos campo: Tipo y clase Otra con atributo campo: Tipo, con "
                    + "relación 1 a *'. No inventes texto ilegible ni uses Markdown.";

    private final RestClient http;
    private final ObjectMapper json;
    private final ResolverServicioActivo servicios;
    private final String proveedorEntorno;
    private final String claveEntorno;
    private final String urlTexto;
    private final String urlVision;
    private final String urlTranscripcion;
    private final String modeloTexto;
    private final String modeloVision;
    private final String modeloTranscripcion;

    public ConectorIaRemoto(
            RestClient.Builder builder,
            ObjectMapper json,
            ResolverServicioActivo servicios,
            @Value("${uml.ia.proveedor:LOCAL}") String proveedor,
            @Value("${uml.ia.clave:}") String clave,
            @Value("${uml.ia.url-texto:}") String urlTexto,
            @Value("${uml.ia.url-vision:}") String urlVision,
            @Value("${uml.ia.url-transcripcion:}") String urlTranscripcion,
            @Value("${uml.ia.modelo-texto:}") String modeloTexto,
            @Value("${uml.ia.modelo-vision:}") String modeloVision,
            @Value("${uml.ia.modelo-transcripcion:}") String modeloTranscripcion) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5000);
        fabrica.setReadTimeout(20000);
        this.http = builder.requestFactory(fabrica).build();
        this.json = json;
        this.servicios = servicios;
        this.proveedorEntorno =
                proveedor == null ? "LOCAL" : proveedor.trim().toUpperCase(Locale.ROOT);
        this.claveEntorno = limpiarConfiguracion(clave);
        this.urlTexto = limpiarConfiguracion(urlTexto);
        this.urlVision = limpiarConfiguracion(urlVision);
        this.urlTranscripcion = limpiarConfiguracion(urlTranscripcion);
        this.modeloTexto = limpiarConfiguracion(modeloTexto);
        this.modeloVision = limpiarConfiguracion(modeloVision);
        this.modeloTranscripcion = limpiarConfiguracion(modeloTranscripcion);
    }

    @Override
    public Optional<String> normalizar(String instruccion) {
        var configuracion = configuracion("IA_TEXTO", urlTexto, modeloTexto);
        if (!configurado(configuracion)) return Optional.empty();
        return generar(configuracion, GRAMATICA + instruccion, null, null, "texto");
    }

    @Override
    public Optional<String> transcribir(byte[] audio, String tipoContenido, String nombre) {
        var configuracion = configuracion("VOZ_A_TEXTO", urlTranscripcion, modeloTranscripcion);
        if (!configurado(configuracion)) return Optional.empty();
        if (es(configuracion, "OPENAI"))
            return transcribirOpenAi(configuracion, audio, tipoContenido, nombre);
        if (es(configuracion, "GEMINI"))
            return generar(
                    configuracion,
                    "Transcribe literalmente este audio en español. Devuelve solo la"
                            + " transcripción.",
                    tipoContenido,
                    audio,
                    "transcripción");
        return Optional.empty();
    }

    @Override
    public Optional<String> reconocerDiagrama(byte[] imagen, String tipoContenido) {
        var configuracion = configuracion("IA_IMAGEN", urlVision, modeloVision);
        if (!configurado(configuracion)) return Optional.empty();
        return generar(configuracion, LEER_DIAGRAMA, tipoContenido, imagen, "visión");
    }

    private Optional<String> generar(
            ConfiguracionProveedor configuracion,
            String prompt,
            String tipoContenido,
            byte[] contenido,
            String capacidad) {
        try {
            JsonNode respuesta;
            if (es(configuracion, "OPENAI"))
                respuesta =
                        enviarJson(
                                configuracion,
                                solicitudOpenAi(
                                        configuracion.modelo(), prompt, tipoContenido, contenido));
            else if (es(configuracion, "GEMINI"))
                respuesta =
                        enviarJson(
                                configuracion, solicitudGemini(prompt, tipoContenido, contenido));
            else if (es(configuracion, "OLLAMA"))
                respuesta =
                        enviarJson(
                                configuracion,
                                solicitudOllama(configuracion.modelo(), prompt, contenido));
            else return Optional.empty();
            return limpiar(textoRespuesta(configuracion, respuesta));
        } catch (RestClientException | IllegalArgumentException e) {
            LOG.warn(
                    "Proveedor IA {} no disponible para {}: {}",
                    configuracion.proveedor(),
                    capacidad,
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<String> transcribirOpenAi(
            ConfiguracionProveedor configuracion,
            byte[] audio,
            String tipoContenido,
            String nombre) {
        try {
            var partes = new MultipartBodyBuilder();
            partes.part(
                            "file",
                            new ByteArrayResource(audio) {
                                @Override
                                public String getFilename() {
                                    return nombre;
                                }
                            })
                    .contentType(MediaType.parseMediaType(tipoContenido));
            partes.part("model", configuracion.modelo());
            partes.part("language", "es");
            var solicitud =
                    http.post()
                            .uri(configuracion.url())
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .headers(cabeceras -> autorizar(configuracion, cabeceras))
                            .body(partes.build());
            JsonNode respuesta = solicitud.retrieve().body(JsonNode.class);
            return limpiar(respuesta == null ? null : respuesta.path("text").asText(null));
        } catch (RestClientException | IllegalArgumentException e) {
            LOG.warn(
                    "Proveedor IA {} no disponible para transcripción: {}",
                    configuracion.proveedor(),
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private ObjectNode solicitudOpenAi(
            String modelo, String prompt, String tipoContenido, byte[] contenido) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("model", modelo);
        cuerpo.put("temperature", 0);
        ArrayNode mensajes = cuerpo.putArray("messages");
        if (contenido == null) {
            mensajes.addObject().put("role", "user").put("content", prompt);
            return cuerpo;
        }
        ObjectNode mensaje = mensajes.addObject();
        mensaje.put("role", "user");
        ArrayNode partes = mensaje.putArray("content");
        partes.addObject().put("type", "text").put("text", prompt);
        partes.addObject()
                .put("type", "image_url")
                .putObject("image_url")
                .put(
                        "url",
                        "data:"
                                + tipoContenido
                                + ";base64,"
                                + Base64.getEncoder().encodeToString(contenido));
        return cuerpo;
    }

    private ObjectNode solicitudGemini(String prompt, String tipoContenido, byte[] contenido) {
        ObjectNode cuerpo = json.createObjectNode();
        ArrayNode partes = cuerpo.putArray("contents").addObject().putArray("parts");
        partes.addObject().put("text", prompt);
        if (contenido != null)
            partes.addObject()
                    .putObject("inline_data")
                    .put("mime_type", tipoContenido)
                    .put("data", Base64.getEncoder().encodeToString(contenido));
        return cuerpo;
    }

    private ObjectNode solicitudOllama(String modelo, String prompt, byte[] contenido) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("model", modelo);
        cuerpo.put("prompt", prompt);
        cuerpo.put("stream", false);
        if (contenido != null)
            cuerpo.putArray("images").add(Base64.getEncoder().encodeToString(contenido));
        return cuerpo;
    }

    private JsonNode enviarJson(ConfiguracionProveedor configuracion, JsonNode cuerpo) {
        return http.post()
                .uri(configuracion.url())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(cabeceras -> autorizar(configuracion, cabeceras))
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class);
    }

    private void autorizar(ConfiguracionProveedor configuracion, HttpHeaders cabeceras) {
        if (configuracion.clave().isBlank()) return;
        if (es(configuracion, "GEMINI")) cabeceras.set("x-goog-api-key", configuracion.clave());
        else cabeceras.setBearerAuth(configuracion.clave());
    }

    private String textoRespuesta(ConfiguracionProveedor configuracion, JsonNode respuesta) {
        if (respuesta == null) return null;
        if (es(configuracion, "OPENAI"))
            return respuesta.path("choices").path(0).path("message").path("content").asText(null);
        if (es(configuracion, "GEMINI"))
            return respuesta
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText(null);
        if (es(configuracion, "OLLAMA")) return respuesta.path("response").asText(null);
        return null;
    }

    private Optional<String> limpiar(String respuesta) {
        if (respuesta == null || respuesta.isBlank()) return Optional.empty();
        String limpia = respuesta.trim();
        if (limpia.startsWith("```")) {
            limpia = limpia.replaceFirst("(?s)^```(?:json|text)?\\s*", "");
            limpia = limpia.replaceFirst("(?s)\\s*```$", "").trim();
        }
        if (limpia.startsWith("{")) {
            try {
                JsonNode objeto = json.readTree(limpia);
                String valor = objeto.path("prompt").asText(objeto.path("texto").asText(null));
                if (valor != null && !valor.isBlank()) limpia = valor.trim();
            } catch (Exception ignored) {
                // La salida no es JSON: el motor local decidirá si la gramática es utilizable.
            }
        }
        return limpia.isBlank() ? Optional.empty() : Optional.of(limpia);
    }

    private ConfiguracionProveedor configuracion(
            String tipo, String urlEntorno, String modeloEntorno) {
        return servicios
                .resolver(tipo)
                .map(
                        activa ->
                                new ConfiguracionProveedor(
                                        activa.proveedor(),
                                        activa.credencial(),
                                        activa.url(),
                                        activa.modelo()))
                .orElseGet(
                        () ->
                                new ConfiguracionProveedor(
                                        proveedorEntorno, claveEntorno, urlEntorno, modeloEntorno));
    }

    private boolean configurado(ConfiguracionProveedor configuracion) {
        return !es(configuracion, "LOCAL")
                && !configuracion.url().isBlank()
                && !configuracion.modelo().isBlank();
    }

    private boolean es(ConfiguracionProveedor configuracion, String esperado) {
        return esperado.equals(configuracion.proveedor());
    }

    private String limpiarConfiguracion(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private record ConfiguracionProveedor(
            String proveedor, String clave, String url, String modelo) {}
}
