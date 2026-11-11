package bo.edu.uml.historial.servicio;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.historial.dto.HistorialDto.ElementoDif;
import bo.edu.uml.historial.dto.HistorialDto.PropiedadDif;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ComparadorVersionesTest {

    private ComparadorVersiones comparador;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        comparador = new ComparadorVersiones();
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Snapshots idénticos devuelven lista vacía de diferencias")
    void snapshotsIdenticosSinDiferencias() throws Exception {
        UUID modeloId = UUID.randomUUID();
        String json =
                """
                {
                  "formato_version": 2,
                  "modelo": {"id": "%s", "nombre": "M1", "descripcion": "Desc", "revision": 1},
                  "paquetes": [],
                  "clases": [
                    {"id": "%s", "nombre": "Usuario", "visibilidad": "PUBLICO", "es_abstracto": false, "es_persistente": false}
                  ],
                  "atributos": [],
                  "operaciones": [],
                  "parametros": [],
                  "relaciones": [],
                  "posiciones_clases": [],
                  "posiciones_relaciones": []
                }
                """
                        .formatted(modeloId, UUID.randomUUID());

        JsonNode snapA = mapper.readTree(json);
        JsonNode snapB = mapper.readTree(json);

        List<ElementoDif> difs = comparador.comparar(snapA, snapB);
        assertThat(difs).isEmpty();
    }

    @Test
    @DisplayName("Detecta adición, eliminación y modificación de clases")
    void detectaCambiosEnClases() throws Exception {
        UUID idClase1 = UUID.randomUUID();
        UUID idClase2 = UUID.randomUUID();
        UUID idClase3 = UUID.randomUUID();

        JsonNode snapA =
                mapper.readTree(
                        """
                        {
                          "clases": [
                            {"id": "%s", "nombre": "Usuario", "visibilidad": "PUBLICO", "es_abstracto": false},
                            {"id": "%s", "nombre": "Eliminada", "visibilidad": "PUBLICO", "es_abstracto": false}
                          ]
                        }
                        """
                                .formatted(idClase1, idClase2));

        JsonNode snapB =
                mapper.readTree(
                        """
                        {
                          "clases": [
                            {"id": "%s", "nombre": "UsuarioModificado", "visibilidad": "PRIVADO", "es_abstracto": true},
                            {"id": "%s", "nombre": "NuevaClase", "visibilidad": "PUBLICO", "es_abstracto": false}
                          ]
                        }
                        """
                                .formatted(idClase1, idClase3));

        List<ElementoDif> difs = comparador.comparar(snapA, snapB);

        ElementoDif mod =
                difs.stream().filter(d -> d.id().equals(idClase1)).findFirst().orElseThrow();
        assertThat(mod.tipo()).isEqualTo("CLASE");
        assertThat(mod.operacion()).isEqualTo("MODIFICADO");
        assertThat(mod.propiedades())
                .extracting(PropiedadDif::clave)
                .contains("nombre", "visibilidad", "es_abstracto");

        ElementoDif elim =
                difs.stream().filter(d -> d.id().equals(idClase2)).findFirst().orElseThrow();
        assertThat(elim.tipo()).isEqualTo("CLASE");
        assertThat(elim.operacion()).isEqualTo("ELIMINADO");

        ElementoDif agreg =
                difs.stream().filter(d -> d.id().equals(idClase3)).findFirst().orElseThrow();
        assertThat(agreg.tipo()).isEqualTo("CLASE");
        assertThat(agreg.operacion()).isEqualTo("AGREGADO");
    }

    @Test
    @DisplayName("Posiciones visuales de clases usan id_clase como identificador único")
    void posicionesClasesUsanIdClase() throws Exception {
        UUID idClase = UUID.randomUUID();

        JsonNode snapA =
                mapper.readTree(
                        """
                        {
                          "posiciones_clases": [
                            {"id_clase": "%s", "x": 100, "y": 100, "ancho": 200, "alto": 150, "orden_capa": 1}
                          ]
                        }
                        """
                                .formatted(idClase));

        JsonNode snapB =
                mapper.readTree(
                        """
                        {
                          "posiciones_clases": [
                            {"id_clase": "%s", "x": 150, "y": 200, "ancho": 200, "alto": 150, "orden_capa": 2}
                          ]
                        }
                        """
                                .formatted(idClase));

        List<ElementoDif> difs = comparador.comparar(snapA, snapB);

        assertThat(difs).hasSize(1);
        ElementoDif diff = difs.get(0);
        assertThat(diff.tipo()).isEqualTo("VISUAL");
        assertThat(diff.operacion()).isEqualTo("MODIFICADO");
        assertThat(diff.id()).isEqualTo(idClase);
        assertThat(diff.propiedades())
                .extracting(PropiedadDif::clave)
                .containsExactlyInAnyOrder("x", "y", "orden_capa");
    }

    @Test
    @DisplayName("Posiciones visuales de relaciones usan id_relacion como identificador único")
    void posicionesRelacionesUsanIdRelacion() throws Exception {
        UUID idRelacion = UUID.randomUUID();

        JsonNode snapA =
                mapper.readTree(
                        """
                        {
                          "posiciones_relaciones": [
                            {"id_relacion": "%s", "etiqueta_x": 10, "etiqueta_y": 20, "estilo": "lineal"}
                          ]
                        }
                        """
                                .formatted(idRelacion));

        JsonNode snapB =
                mapper.readTree(
                        """
                        {
                          "posiciones_relaciones": [
                            {"id_relacion": "%s", "etiqueta_x": 40, "etiqueta_y": 20, "estilo": "ortogonal"}
                          ]
                        }
                        """
                                .formatted(idRelacion));

        List<ElementoDif> difs = comparador.comparar(snapA, snapB);

        assertThat(difs).hasSize(1);
        ElementoDif diff = difs.get(0);
        assertThat(diff.tipo()).isEqualTo("VISUAL");
        assertThat(diff.operacion()).isEqualTo("MODIFICADO");
        assertThat(diff.id()).isEqualTo(idRelacion);
        assertThat(diff.propiedades())
                .extracting(PropiedadDif::clave)
                .containsExactlyInAnyOrder("etiqueta_x", "estilo");
    }

    @Test
    @DisplayName("El campo 'orden' NO se excluye y reporta cambios en atributos y operaciones")
    void ordenNoSeExcluyeEnComparacion() throws Exception {
        UUID idAtributo = UUID.randomUUID();
        UUID idClase = UUID.randomUUID();

        JsonNode snapA =
                mapper.readTree(
                        """
                        {
                          "atributos": [
                            {"id": "%s", "id_clase": "%s", "nombre": "codigo", "tipo_uml": "String", "orden": 1}
                          ]
                        }
                        """
                                .formatted(idAtributo, idClase));

        JsonNode snapB =
                mapper.readTree(
                        """
                        {
                          "atributos": [
                            {"id": "%s", "id_clase": "%s", "nombre": "codigo", "tipo_uml": "String", "orden": 2}
                          ]
                        }
                        """
                                .formatted(idAtributo, idClase));

        List<ElementoDif> difs = comparador.comparar(snapA, snapB);

        assertThat(difs).hasSize(1);
        ElementoDif diff = difs.get(0);
        assertThat(diff.tipo()).isEqualTo("ATRIBUTO");
        assertThat(diff.operacion()).isEqualTo("MODIFICADO");
        assertThat(diff.propiedades()).extracting(PropiedadDif::clave).contains("orden");
    }
}
