package bo.edu.uml.servicios.servicio;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class ServiciosPlataformaTest {
    private static final String CLAVE = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void aesGcmCifraConNonceYDetectaAlteraciones() {
        var cifrador = new CifradorSecretosServicio(CLAVE, "pruebas-v1");
        byte[] primero = cifrador.cifrar("credencial-ficticia");
        byte[] segundo = cifrador.cifrar("credencial-ficticia");

        assertThat(primero).isNotEqualTo(segundo);
        assertThat(new String(primero, StandardCharsets.ISO_8859_1))
                .doesNotContain("credencial-ficticia");
        assertThat(cifrador.descifrar(primero, CifradorSecretosServicio.ALGORITMO, "pruebas-v1"))
                .isEqualTo("credencial-ficticia");

        primero[primero.length - 1] ^= 1;
        assertThatThrownBy(
                        () ->
                                cifrador.descifrar(
                                        primero, CifradorSecretosServicio.ALGORITMO, "pruebas-v1"))
                .isInstanceOf(ApiException.class)
                .hasMessageNotContaining("credencial-ficticia");
    }

    @Test
    void unaClaveAusenteImpidePersistirSecretos() {
        var cifrador = new CifradorSecretosServicio("", "v1");
        assertThatThrownBy(() -> cifrador.cifrar("no-persistir"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("UML_SERVICIOS_MASTER_KEY");
    }

    @Test
    void comprobacionLocalNoUsaRedYLosDestinosInternosSeRechazan() {
        var comprobador = new ComprobadorServicioPlataforma();
        assertThat(
                        comprobador
                                .comprobar(
                                        "LOCAL", null, JsonNodeFactory.instance.objectNode(), null)
                                .estado())
                .isEqualTo("DISPONIBLE");
        assertThatThrownBy(() -> comprobador.validarDestino("https://127.0.0.1:8443/comprobacion"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("red interna");
        assertThatThrownBy(() -> comprobador.validarDestino("http://proveedor.example/prueba"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HTTPS");
    }
}
