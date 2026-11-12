package bo.edu.uml.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import bo.edu.uml.ia.servicio.ConectorIaRemoto;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.servicios.servicio.ResolverServicioActivo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ConectorGeminiRespuestaTest {
  @Test
  void laCuotaDeTextoNoDeshabilitaElServicioDeImagenes() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/texto", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.sendResponseHeaders(429, -1);
      exchange.close();
    });
    server.createContext("/vision", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] data = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"clases\\\":[{\\\"nombre\\\":\\\"Cliente\\\"}]}\"}]}}]}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, data.length);
      try (var body = exchange.getResponseBody()) { body.write(data); }
    });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      var connector = new ConectorIaRemoto(RestClient.builder(), new ObjectMapper(),
          mock(ResolverServicioActivo.class), "GEMINI", "ficticia", base + "/texto", base + "/vision", "",
          "gemini-3.8-flash", "gemini-3.8-flash", "");
      assertThat(connector.conversar("{}")).isEmpty();
      assertThat(connector.reconocerDiagrama(new byte[] {1, 2, 3}, "image/png"))
          .contains("{\"clases\":[{\"nombre\":\"Cliente\"}]}");
    } finally { server.stop(0); }
  }

  @Test
  void faltaDeConfiguracionVisualEsExplicitaSinRomperElModoLocalDeTexto() {
    var connector = new ConectorIaRemoto(RestClient.builder(), new ObjectMapper(),
        mock(ResolverServicioActivo.class), "LOCAL", "", "", "", "", "", "", "");
    assertThat(connector.conversar("{}")).isEmpty();
    assertThatThrownBy(() -> connector.reconocerDiagrama(new byte[] {1}, "image/png"))
        .isInstanceOf(ApiException.class).hasMessageContaining("Habilitar imágenes con Gemini");
  }

  @Test
  void uneRespuestaDivididaYOmitePartesDeRazonamiento() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/gemini", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] data = """
        {"candidates":[{"content":{"parts":[
          {"thought":true,"text":"Razonamiento interno que no debe mostrarse"},
          {"text":"```json\\n{\\"tipo\\":\\"RESPUESTA\\","},
          {"text":"\\"texto\\":\\"Hola equipo\\"}\\n```"}
        ]}}]}
        """.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, data.length);
      try (var body = exchange.getResponseBody()) { body.write(data); }
    });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/gemini";
      var connector = new ConectorIaRemoto(RestClient.builder(), new ObjectMapper(),
          mock(ResolverServicioActivo.class), "GEMINI", "ficticia", url, url, url,
          "gemini-3.8-flash", "gemini-3.8-flash", "gemini-2.5-flash");
      assertThat(connector.conversar("{}"))
          .contains("{\"tipo\":\"RESPUESTA\",\"texto\":\"Hola equipo\"}");
      assertThat(connector.configuracionPublica()).containsEntry("modelo", "gemini-3.8-flash");
    } finally {
      server.stop(0);
    }
  }
}
