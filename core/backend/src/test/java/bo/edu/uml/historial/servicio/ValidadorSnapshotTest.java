package bo.edu.uml.historial.servicio;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

class ValidadorSnapshotTest {

    private ValidadorSnapshot validador;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        validador = new ValidadorSnapshot();
    }

    private String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("Snapshot válido pasa la validación sin errores")
    void snapshotValido() throws Exception {
        UUID modeloId = UUID.randomUUID();
        UUID paqueteId = UUID.randomUUID();
        UUID claseId = UUID.randomUUID();
        UUID attrId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        UUID paramId = UUID.randomUUID();

        String rawJson =
                """
                {
                  "formato_version": 2,
                  "modelo": {"id": "%s", "nombre": "Sistema Ventas", "descripcion": "Modelo test", "revision": 5},
                  "paquetes": [
                    {"id": "%s", "nombre": "dominio", "descripcion": null, "id_paquete_padre": null}
                  ],
                  "clases": [
                    {"id": "%s", "nombre": "Cliente", "descripcion": null, "estereotipo": null, "visibilidad": "PUBLICO", "es_abstracto": false, "es_persistente": true, "id_paquete": "%s"}
                  ],
                  "atributos": [
                    {"id": "%s", "id_clase": "%s", "nombre": "codigo", "tipo_uml": "String", "visibilidad": "PRIVADO", "multiplicidad_minima": 1, "multiplicidad_maxima": 1, "es_identificador": true, "es_estatico": false, "es_unico": true, "valor_predeterminado": null, "restricciones_json": null, "orden": 1}
                  ],
                  "operaciones": [
                    {"id": "%s", "id_clase": "%s", "nombre": "calcularTotal", "tipo_retorno": "BigDecimal", "visibilidad": "PUBLICO", "es_estatico": false, "es_abstracto": false, "orden": 1}
                  ],
                  "parametros": [
                    {"id": "%s", "id_operacion": "%s", "nombre": "tasa", "tipo_uml": "BigDecimal", "direccion": "ENTRADA", "es_obligatorio": true, "valor_predeterminado": null, "orden": 1}
                  ],
                  "relaciones": [],
                  "posiciones_clases": [
                    {"id_clase": "%s", "x": 100, "y": 150, "ancho": 220, "alto": 180, "orden_capa": 0, "estilo": null}
                  ],
                  "posiciones_relaciones": []
                }
                """
                        .formatted(
                                modeloId, paqueteId, claseId, paqueteId, attrId, claseId, opId,
                                claseId, paramId, opId, claseId);

        String hashEsperado = sha256(rawJson);
        JsonNode node = mapper.readTree(rawJson);
        assertThatCode(() -> validador.validar(node, modeloId, hashEsperado, rawJson))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Snapshot con formato_version inválido lanza 422")
    void formatoVersionInvalido() throws Exception {
        UUID modeloId = UUID.randomUUID();
        String raw =
                """
                {"formato_version": 1, "modelo": {"id": "%s", "nombre": "Sistema"}}
                """
                        .formatted(modeloId);
        JsonNode node = mapper.readTree(raw);

        assertThatThrownBy(() -> validador.validar(node, modeloId, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("formato de versión");
    }

    @Test
    @DisplayName("Snapshot con modeloId discordante lanza 422")
    void modeloIdDiscordante() throws Exception {
        UUID modeloId = UUID.randomUUID();
        UUID otroModeloId = UUID.randomUUID();
        String raw =
                """
                {
                  "formato_version": 2,
                  "modelo": {"id": "%s", "nombre": "Sistema"},
                  "paquetes": [], "clases": [], "atributos": [], "operaciones": [], "parametros": [], "relaciones": []
                }
                """
                        .formatted(otroModeloId);
        JsonNode node = mapper.readTree(raw);

        assertThatThrownBy(() -> validador.validar(node, modeloId, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no pertenece al modelo");
    }

    @Test
    @DisplayName("Atributo apuntando a clase inexistente viola integridad referencial (422)")
    void integridadReferencialAtributoClaseInexistente() throws Exception {
        UUID modeloId = UUID.randomUUID();
        UUID claseInexistente = UUID.randomUUID();
        String raw =
                """
                {
                  "formato_version": 2,
                  "modelo": {"id": "%s", "nombre": "Sistema"},
                  "paquetes": [],
                  "clases": [],
                  "atributos": [
                    {"id": "%s", "id_clase": "%s", "nombre": "edad", "tipo_uml": "int", "visibilidad": "PUBLICO"}
                  ],
                  "operaciones": [],
                  "parametros": [],
                  "relaciones": [],
                  "posiciones_clases": [],
                  "posiciones_relaciones": []
                }
                """
                        .formatted(modeloId, UUID.randomUUID(), claseInexistente);
        JsonNode node = mapper.readTree(raw);

        assertThatThrownBy(() -> validador.validar(node, modeloId, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no existe en el snapshot");
    }

    @Test
    @DisplayName("Hash SHA-256 no coincide lanza 422")
    void hashSha256Invalido() throws Exception {
        UUID modeloId = UUID.randomUUID();
        String raw =
                """
                {
                  "formato_version": 2,
                  "modelo": {"id": "%s", "nombre": "Sistema"},
                  "paquetes": [],
                  "clases": [],
                  "atributos": [],
                  "operaciones": [],
                  "parametros": [],
                  "relaciones": [],
                  "posiciones_clases": [],
                  "posiciones_relaciones": []
                }
                """
                        .formatted(modeloId);
        JsonNode node = mapper.readTree(raw);

        assertThatThrownBy(() -> validador.validar(node, modeloId, "hash_corrupto_invalido", raw))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("SHA-256");
    }
}
