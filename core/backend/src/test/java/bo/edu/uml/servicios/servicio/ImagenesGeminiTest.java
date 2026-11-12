package bo.edu.uml.servicios.servicio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.servicios.dto.ServicioPlataformaDto.*;
import bo.edu.uml.servicios.repositorio.ServicioPlataformaRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;

class ImagenesGeminiTest {
    private static final String MASTER = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final Identidad admin = new Identidad(UUID.randomUUID(), "admin", "Administración", List.of("SUPERADMINISTRADOR"), List.of());
    private final ServicioPlataformaRepositorio repo = mock(ServicioPlataformaRepositorio.class);
    private final AccesoRepositorio acceso = mock(AccesoRepositorio.class);
    private final ResolverServicioActivo resolver = mock(ResolverServicioActivo.class);
    private final ComprobadorServicioPlataforma comprobador = mock(ComprobadorServicioPlataforma.class);
    private final CifradorSecretosServicio cifrador = new CifradorSecretosServicio(MASTER, "v1");
    private final AbstractPlatformTransactionManager manager = new AbstractPlatformTransactionManager() {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object tx, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    };

    private ActivarImagenesGeminiServicio activador(String key) {
        when(acceso.identidad(admin.id())).thenReturn(admin);
        return new ActivarImagenesGeminiServicio(repo, acceso, cifrador, resolver, comprobador, manager, key, "gemini-3.8-flash");
    }

    @Test
    void habilitaSoloDespuesDeAnalizarImagenYGuardaClaveCifrada() {
        var service = activador("");
        when(resolver.resolver("IA_TEXTO")).thenReturn(Optional.of(
                new ResolverServicioActivo.ConfiguracionActiva("GEMINI", "", "gemini-3.8-flash", "clave-ficticia")));
        when(comprobador.comprobarImagenGemini("gemini-3.8-flash", "clave-ficticia")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            verify(repo, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any());
            return new ComprobadorServicioPlataforma.Resultado("DISPONIBLE", 25, "Imagen analizada");
        });
        when(repo.listar("", "GEMINI", "IA_IMAGEN", "", 0, 100))
                .thenReturn(new PaginaServicios(List.of(), 0, 100, 0));
        service.activar(admin);
        var secret = ArgumentCaptor.forClass(byte[].class);
        verify(repo).reemplazarSecreto(any(), secret.capture(), eq(CifradorSecretosServicio.ALGORITMO), eq("v1"));
        assertThat(cifrador.descifrar(secret.getValue(), CifradorSecretosServicio.ALGORITMO, "v1")).isEqualTo("clave-ficticia");
        verify(repo).insertar(any(), eq(admin.id()), anyString(), eq("GEMINI"), eq("IA_IMAGEN"),
                eq("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"), any(), eq("ACTIVO"));
        verify(repo).guardarComprobacion(any(), eq(admin.id()), eq("DISPONIBLE"), eq(25), eq("Imagen analizada"));
    }

    @Test
    void unaCuotaAgotadaNoCreaNiActivaServicios() {
        var service = activador("clave-ficticia");
        when(comprobador.comprobarImagenGemini(anyString(), anyString())).thenReturn(
                new ComprobadorServicioPlataforma.Resultado("DEGRADADO", 5, "Gemini agotó la cuota disponible."));
        assertThatThrownBy(() -> service.activar(admin)).isInstanceOf(ApiException.class).hasMessageContaining("cuota");
        verifyNoInteractions(repo);
        verify(comprobador, times(4)).comprobarImagenGemini(anyString(), eq("clave-ficticia"));
    }

    @Test
    void reutilizaRegistroInactivoYModeloDeRespaldoComprobado() {
        var service = activador("clave-ficticia");
        when(comprobador.comprobarImagenGemini("gemini-3.8-flash", "clave-ficticia")).thenReturn(
                new ComprobadorServicioPlataforma.Resultado("NO_DISPONIBLE", 3, "Modelo no disponible"));
        when(comprobador.comprobarImagenGemini("gemini-3.6-flash", "clave-ficticia")).thenReturn(
                new ComprobadorServicioPlataforma.Resultado("DISPONIBLE", 5, "Imagen analizada"));
        UUID id = UUID.randomUUID();
        var existente = new Servicio(id, "Visión anterior", "GEMINI", "IA_IMAGEN", "https://example.org", JsonNodeFactory.instance.objectNode(),
                "INACTIVO", false, admin.id(), Instant.now(), Instant.now(), "revision", null);
        when(repo.listar("", "GEMINI", "IA_IMAGEN", "", 0, 100)).thenReturn(new PaginaServicios(List.of(existente), 0, 100, 1));
        service.activar(admin);
        verify(repo).actualizar(eq(id), eq("Visión anterior"), eq("GEMINI"), eq("IA_IMAGEN"),
                contains("gemini-3.6-flash:generateContent"), any(), eq("ACTIVO"), eq("revision"));
        verify(repo, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rechazaUsuariosSinPermisoAntesDeUsarLaClave() {
        var service = activador("clave-ficticia");
        var invitado = new Identidad(admin.id(), "invitado", "Invitado", List.of(), List.of());
        when(acceso.identidad(admin.id())).thenReturn(invitado);
        assertThatThrownBy(() -> service.activar(admin)).isInstanceOf(ApiException.class).hasMessageContaining("permiso");
        verifyNoInteractions(resolver, comprobador, repo);
    }

    @Test
    void faltaDeClaveNoSePresentaComoServicioActivo() {
        var service = activador("");
        assertThatThrownBy(() -> service.activar(admin)).isInstanceOf(ApiException.class).hasMessageContaining("UML_IA_CLAVE");
        verifyNoInteractions(comprobador, repo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void laComprobacionGetUsaElRecursoDelModeloYLaPruebaVisualUsaPostConImagen() throws Exception {
        var http = mock(HttpClient.class);
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Verde\"}]}}]}");
        doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var checker = spy(new ComprobadorServicioPlataforma(http));
        doAnswer(invocation -> URI.create(invocation.getArgument(0))).when(checker).validarDestino(anyString());
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent";
        assertThat(checker.comprobar("GEMINI", url, JsonNodeFactory.instance.objectNode(), "ficticia").disponible()).isTrue();
        assertThat(checker.comprobarImagenGemini("gemini-3.8-flash", "ficticia").disponible()).isTrue();
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(request.capture(), any(HttpResponse.BodyHandler.class));
        var get = request.getAllValues().get(0);
        assertThat(get.method()).isEqualTo("GET");
        assertThat(get.uri().getPath()).endsWith("/gemini-3.8-flash");
        var post = request.getAllValues().get(1);
        assertThat(post.method()).isEqualTo("POST");
        assertThat(post.headers().firstValue("x-goog-api-key")).contains("ficticia");
        var body = new java.io.ByteArrayOutputStream();
        post.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { var bytes = new byte[buffer.remaining()]; buffer.get(bytes); body.writeBytes(bytes); }
            public void onError(Throwable e) { throw new AssertionError(e); }
            public void onComplete() {}
        });
        JsonNode json = new ObjectMapper().readTree(body.toString(StandardCharsets.UTF_8));
        String encoded = json.path("contents").path(0).path("parts").path(1).path("inline_data").path("data").asText();
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
        assertThat(image.getWidth()).isEqualTo(16);
        assertThat(image.getHeight()).isEqualTo(16);
    }

    @Test
    @SuppressWarnings("unchecked")
    void laPruebaVisualRechazaCuotaAgotadaYRespuestaSinAnalisis() throws Exception {
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429, 200);
        when(response.body()).thenReturn("{\"candidates\":[]}");
        doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var checker = spy(new ComprobadorServicioPlataforma(http));
        doAnswer(invocation -> URI.create(invocation.getArgument(0))).when(checker).validarDestino(anyString());
        assertThat(checker.comprobarImagenGemini("gemini-3.8-flash", "ficticia").detalle()).contains("cuota");
        assertThat(checker.comprobarImagenGemini("gemini-3.8-flash", "ficticia").disponible()).isFalse();
    }
}
