package bo.edu.uml.ia;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import bo.edu.uml.ia.servicio.MotorVisionDiagrama;
import bo.edu.uml.ia.servicio.ProveedorIa;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class MotorVisionDiagramaTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MotorAsistenciaInteligente motorTexto = new MotorAsistenciaInteligente(json);

    @Test
    void interpretaJsonEstructuradoDeVisionConMultiplesClasesYRelaciones() {
        String jsonVision =
                """
                {
                  "resumen": "Diagrama de ventas con Cliente, Pedido y Producto",
                  "clases": [
                    {
                      "nombre": "Cliente",
                      "atributos": [
                        { "nombre": "id", "tipo": "Long", "esIdentificador": true },
                        { "nombre": "nombre", "tipo": "String", "visibilidad": "PUBLICO" }
                      ]
                    },
                    {
                      "nombre": "Pedido",
                      "atributos": [
                        { "nombre": "id", "tipo": "Long", "esIdentificador": true },
                        { "nombre": "total", "tipo": "Double" }
                      ],
                      "operaciones": [
                        { "nombre": "calcularTotal", "tipoRetorno": "Double", "visibilidad": "PUBLICO" }
                      ]
                    },
                    {
                      "nombre": "Producto",
                      "atributos": [
                        { "nombre": "id", "tipo": "Long", "esIdentificador": true },
                        { "nombre": "precio", "tipo": "Double" }
                      ]
                    }
                  ],
                  "relaciones": [
                    {
                      "origen": "Pedido",
                      "destino": "Cliente",
                      "tipo": "ASOCIACION",
                      "multiplicidadOrigen": "0..*",
                      "multiplicidadDestino": "1"
                    },
                    {
                      "origen": "Pedido",
                      "destino": "Producto",
                      "tipo": "COMPOSICION",
                      "multiplicidadOrigen": "1",
                      "multiplicidadDestino": "1..*"
                    }
                  ]
                }
                """;

        ProveedorIa proveedorSimulado = new ProveedorIa() {
            @Override
            public Optional<String> reconocerDiagrama(byte[] imagen, String tipoContenido) {
                return Optional.of(jsonVision);
            }
        };

        MotorVisionDiagrama motorVision = new MotorVisionDiagrama(motorTexto, proveedorSimulado, json);
        var resultado =
                motorVision.interpretar(
                        new byte[] {1, 2, 3}, "image/png", null, new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).isNotEmpty();

        // 3 clases + 6 atributos + 1 operacion + 2 relaciones = 12 operaciones
        long clases = resultado.operaciones().stream().filter(o -> "CLASE".equals(o.tipoEntidad())).count();
        long atributos = resultado.operaciones().stream().filter(o -> "ATRIBUTO".equals(o.tipoEntidad())).count();
        long operacionesMetodo = resultado.operaciones().stream().filter(o -> "OPERACION".equals(o.tipoEntidad())).count();
        long relaciones = resultado.operaciones().stream().filter(o -> "RELACION".equals(o.tipoEntidad())).count();

        assertThat(clases).isEqualTo(3);
        assertThat(atributos).isEqualTo(6);
        assertThat(operacionesMetodo).isEqualTo(1);
        assertThat(relaciones).isEqualTo(2);

        // Verificar cardinalidades y tipos de relación
        var relAsoc =
                resultado.operaciones().stream()
                        .filter(
                                o ->
                                        "RELACION".equals(o.tipoEntidad())
                                                && "ASOCIACION"
                                                        .equals(
                                                                o.valorPropuesto()
                                                                        .path("tipo")
                                                                        .asText()))
                        .findFirst()
                        .orElseThrow();
        assertThat(relAsoc.valorPropuesto().path("minimoOrigen").asInt()).isZero();
        assertThat(relAsoc.valorPropuesto().path("maximoOrigen").isNull()).isTrue();
        assertThat(relAsoc.valorPropuesto().path("minimoDestino").asInt()).isEqualTo(1);
        assertThat(relAsoc.valorPropuesto().path("maximoDestino").asInt()).isEqualTo(1);

        var relComp =
                resultado.operaciones().stream()
                        .filter(
                                o ->
                                        "RELACION".equals(o.tipoEntidad())
                                                && "COMPOSICION"
                                                        .equals(
                                                                o.valorPropuesto()
                                                                        .path("tipo")
                                                                        .asText()))
                        .findFirst()
                        .orElseThrow();
        assertThat(relComp.valorPropuesto().path("minimoOrigen").asInt()).isEqualTo(1);
        assertThat(relComp.valorPropuesto().path("minimoDestino").asInt()).isEqualTo(1);
        assertThat(relComp.valorPropuesto().path("maximoDestino").isNull()).isTrue();
    }

    @Test
    void fusionaConClasesExistentesEnContextoSinDuplicar() {
        UUID idClienteExistente = UUID.randomUUID();
        ElementoContexto claseCliente =
                new ElementoContexto(idClienteExistente, "CLASE", "Cliente", null, null);
        ElementoContexto idAttr =
                new ElementoContexto(UUID.randomUUID(), "ATRIBUTO", "id", idClienteExistente, null);

        Contexto contexto = new Contexto(1, Set.of("Cliente"), List.of(claseCliente, idAttr));

        String jsonVision =
                """
                {
                  "clases": [
                    {
                      "nombre": "Cliente",
                      "atributos": [
                        { "nombre": "id", "tipo": "Long" },
                        { "nombre": "correo", "tipo": "String" }
                      ]
                    },
                    {
                      "nombre": "Factura",
                      "atributos": [
                        { "nombre": "id", "tipo": "Long" },
                        { "nombre": "monto", "tipo": "Double" }
                      ]
                    }
                  ],
                  "relaciones": [
                    {
                      "origen": "Factura",
                      "destino": "Cliente",
                      "tipo": "ASOCIACION",
                      "multiplicidadOrigen": "*",
                      "multiplicidadDestino": "1"
                    }
                  ]
                }
                """;

        ProveedorIa proveedor = new ProveedorIa() {
            @Override
            public Optional<String> reconocerDiagrama(byte[] imagen, String tipoContenido) {
                return Optional.of(jsonVision);
            }
        };

        MotorVisionDiagrama motorVision = new MotorVisionDiagrama(motorTexto, proveedor, json);
        var resultado = motorVision.interpretar(new byte[]{1, 2}, "image/png", null, contexto);

        // Cliente ya existe: NO debe haber operación CREAR CLASE para Cliente, solo para Factura
        var clasesCreadas = resultado.operaciones().stream()
                .filter(o -> "CLASE".equals(o.tipoEntidad()) && "CREAR".equals(o.tipoOperacion()))
                .toList();
        assertThat(clasesCreadas).hasSize(1);
        assertThat(clasesCreadas.getFirst().valorPropuesto().path("nombre").asText())
                .isEqualTo("Factura");

        // El atributo 'correo' debe crearse vinculado al idClienteExistente
        var correoAttr =
                resultado.operaciones().stream()
                        .filter(
                                o ->
                                        "ATRIBUTO".equals(o.tipoEntidad())
                                                && "correo"
                                                        .equals(
                                                                o.valorPropuesto()
                                                                        .path("nombre")
                                                                        .asText()))
                        .findFirst()
                        .orElseThrow();
        assertThat(correoAttr.valorPropuesto().path("idClase").asText())
                .isEqualTo(idClienteExistente.toString());

        // La relación debe enlazar Factura con el idClienteExistente
        var relacion = resultado.operaciones().stream()
                .filter(o -> "RELACION".equals(o.tipoEntidad()))
                .findFirst().orElseThrow();
        assertThat(relacion.valorPropuesto().path("destino").asText())
                .isEqualTo(idClienteExistente.toString());
    }

    @Test
    void fallaCon503CuandoNoHayProveedorOcr() {
        MotorVisionDiagrama motorVision = new MotorVisionDiagrama(motorTexto, new ProveedorIa() {}, json);
        assertThatThrownBy(
                        () ->
                                motorVision.interpretar(
                                        new byte[] {1},
                                        "image/png",
                                        null,
                                        new Contexto(0, Set.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("proveedor OCR o multimodal disponible");
    }

    @Test
    void respaldaConTextoDetectadoSiNoEsJson() {
        MotorVisionDiagrama motorVision = new MotorVisionDiagrama(motorTexto, new ProveedorIa() {}, json);
        var resultado = motorVision.interpretar(
                new byte[]{1},
                "image/png",
                "Crea clase Usuario con atributo email: String",
                new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).isNotEmpty();
        assertThat(resultado.operaciones().getFirst().valorPropuesto().path("nombre").asText())
                .isEqualTo("Usuario");
    }

    @Test
    void interpretaBocetoPapelConTiposSqlYClavesForaneasImplicitamente() {
        String jsonBocetoPapel =
                """
                {
                  "resumen": "Boceto a mano en libreta de modelo de datos Ventas",
                  "clases": [
                    {
                      "nombre": "Cliente",
                      "atributos": [
                        { "nombre": "id (PK)", "tipo": "BIGINT" },
                        { "nombre": "razon_social", "tipo": "VARCHAR(150)" }
                      ]
                    },
                    {
                      "nombre": "Pedido",
                      "atributos": [
                        { "nombre": "id_pedido (PK)", "tipo": "BIGINT" },
                        { "nombre": "cliente_id (FK)", "tipo": "BIGINT" },
                        { "nombre": "monto_total", "tipo": "DECIMAL(12,2)" },
                        { "nombre": "unidades", "tipo": "INT" }
                      ]
                    }
                  ],
                  "relaciones": []
                }
                """;

        ProveedorIa proveedor = new ProveedorIa() {
            @Override
            public Optional<String> reconocerDiagrama(byte[] imagen, String tipoContenido, String guia) {
                return Optional.of(jsonBocetoPapel);
            }
        };

        MotorVisionDiagrama motorVision = new MotorVisionDiagrama(motorTexto, proveedor, json);
        var resultado = motorVision.interpretar(
                new byte[]{1, 2, 3},
                "image/jpeg",
                "[TIPO_ORIGEN: PAPEL_CUADERNO]",
                new Contexto(0, Set.of()));

        // Verificar normalización de tipos SQL a UML
        var atributos = resultado.operaciones().stream()
                .filter(o -> "ATRIBUTO".equals(o.tipoEntidad()))
                .toList();

        // id (PK) en Cliente -> nombre: "id", tipoUml: "Long", esIdentificador: true
        var pkCliente = atributos.stream()
                .filter(a -> "id".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(pkCliente.valorPropuesto().path("tipoUml").asText()).isEqualTo("Long");
        assertThat(pkCliente.valorPropuesto().path("esIdentificador").asBoolean()).isTrue();

        // razon_social -> tipo "String" (normalizado de VARCHAR(150))
        var razonSocial = atributos.stream()
                .filter(a -> "razon_social".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(razonSocial.valorPropuesto().path("tipoUml").asText()).isEqualTo("String");

        // monto_total -> tipo "Double" (normalizado de DECIMAL(12,2))
        var monto = atributos.stream()
                .filter(a -> "monto_total".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(monto.valorPropuesto().path("tipoUml").asText()).isEqualTo("Double");

        // unidades -> tipo "Integer" (normalizado de INT)
        var unidades = atributos.stream()
                .filter(a -> "unidades".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(unidades.valorPropuesto().path("tipoUml").asText()).isEqualTo("Integer");

        // cliente_id (FK) -> sanitizado a cliente_id
        var fkAttr = atributos.stream()
                .filter(a -> "cliente_id".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(fkAttr.valorPropuesto().path("tipoUml").asText()).isEqualTo("Long");

        // Relación implícita generada de Pedido a Cliente por cliente_id
        var relaciones = resultado.operaciones().stream()
                .filter(o -> "RELACION".equals(o.tipoEntidad()))
                .toList();
        assertThat(relaciones).hasSize(1);
        var relImplicita = relaciones.getFirst();
        assertThat(relImplicita.valorPropuesto().path("tipo").asText()).isEqualTo("ASOCIACION");
        assertThat(relImplicita.justificacion()).contains("cliente_id");
    }

    @Test
    void interpretaDiagramaPizarraVerdeEcommerceConAtributosSinTipoYTituloDescartado() {
        String jsonPizarraVerde =
                """
                {
                  "resumen": "Diagrama de clases de sistema de ventas e-commerce en pizarra verde",
                  "clases": [
                    {
                      "nombre": "MODELO CONCEPTUAL / DIAGRAMA DE CLASES: SISTEMA DE VENTAS (E-COMMERCE)",
                      "atributos": []
                    },
                    {
                      "nombre": "CLIENTE",
                      "atributos": [
                        { "nombre": "id_cliente (PK)", "tipo": "" },
                        { "nombre": "nombre", "tipo": "" },
                        { "nombre": "correo", "tipo": "" },
                        { "nombre": "telefono", "tipo": "" },
                        { "nombre": "direccion", "tipo": "" }
                      ]
                    },
                    {
                      "nombre": "PEDIDO",
                      "atributos": [
                        { "nombre": "id_pedido (PK)", "tipo": "" },
                        { "nombre": "fecha", "tipo": "" },
                        { "nombre": "estado", "tipo": "" },
                        { "nombre": "total", "tipo": "" },
                        { "nombre": "id_cliente (FK)", "tipo": "" }
                      ]
                    },
                    {
                      "nombre": "PAGO",
                      "atributos": [
                        { "nombre": "id_pago (PK)", "tipo": "" },
                        { "nombre": "id_pedido (FK)", "tipo": "" },
                        { "nombre": "monto", "tipo": "" },
                        { "nombre": "fecha_pago", "tipo": "" },
                        { "nombre": "metodo", "tipo": "" }
                      ]
                    },
                    {
                      "nombre": "DETALLE_PEDIDO",
                      "atributos": [
                        { "nombre": "id_detalle (PK)", "tipo": "" },
                        { "nombre": "id_pedido (FK)", "tipo": "" },
                        { "nombre": "id_producto (FK)", "tipo": "" },
                        { "nombre": "cantidad", "tipo": "" },
                        { "nombre": "subtotal", "tipo": "" }
                      ]
                    },
                    {
                      "nombre": "PRODUCTO",
                      "atributos": [
                        { "nombre": "id_producto (PK)", "tipo": "" },
                        { "nombre": "nombre", "tipo": "" },
                        { "nombre": "precio", "tipo": "" },
                        { "nombre": "stock", "tipo": "" },
                        { "nombre": "id_categoria (FK)", "tipo": "" }
                      ]
                    },
                    {
                      "nombre": "CATEGORÍA",
                      "atributos": [
                        { "nombre": "id_categoria (PK)", "tipo": "" },
                        { "nombre": "nombre_categoria", "tipo": "" },
                        { "nombre": "descripcion", "tipo": "" }
                      ]
                    }
                  ],
                  "relaciones": [
                    { "origen": "CLIENTE", "destino": "PEDIDO", "tipo": "ASOCIACION", "multiplicidadOrigen": "1", "multiplicidadDestino": "0..*" },
                    { "origen": "PEDIDO", "destino": "PAGO", "tipo": "ASOCIACION", "multiplicidadOrigen": "1", "multiplicidadDestino": "1" },
                    { "origen": "PEDIDO", "destino": "DETALLE_PEDIDO", "tipo": "ASOCIACION", "multiplicidadOrigen": "1", "multiplicidadDestino": "1..*" },
                    { "origen": "DETALLE_PEDIDO", "destino": "PRODUCTO", "tipo": "ASOCIACION", "multiplicidadOrigen": "0..*", "multiplicidadDestino": "1" },
                    { "origen": "PRODUCTO", "destino": "CATEGORÍA", "tipo": "ASOCIACION", "multiplicidadOrigen": "0..*", "multiplicidadDestino": "1" }
                  ]
                }
                """;

        ProveedorIa proveedor = new ProveedorIa() {
            @Override
            public Optional<String> reconocerDiagrama(byte[] imagen, String tipoContenido, String guia) {
                return Optional.of(jsonPizarraVerde);
            }
        };

        MotorVisionDiagrama motorVision = new MotorVisionDiagrama(motorTexto, proveedor, json);
        var resultado = motorVision.interpretar(
                new byte[]{1, 2, 3},
                "image/jpeg",
                "[TIPO_ORIGEN: PIZARRA]",
                new Contexto(0, Set.of()));

        // 1. El título NO debe haberse creado como clase (deben haber exactamente 6 clases, no 7)
        var clases = resultado.operaciones().stream()
                .filter(o -> "CLASE".equals(o.tipoEntidad()))
                .map(o -> o.valorPropuesto().path("nombre").asText())
                .toList();
        assertThat(clases)
                .containsExactlyInAnyOrder(
                        "Cliente", "Pedido", "Pago", "DetallePedido", "Producto", "Categoria");

        // 2. Comprobar inferencia de tipos de atributos no tipados
        var atributos = resultado.operaciones().stream()
                .filter(o -> "ATRIBUTO".equals(o.tipoEntidad()))
                .toList();

        // id_cliente -> tipo Long, identificador true
        var idCliente = atributos.stream()
                .filter(a -> "id_cliente".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(idCliente.valorPropuesto().path("tipoUml").asText()).isEqualTo("Long");
        assertThat(idCliente.valorPropuesto().path("esIdentificador").asBoolean()).isTrue();

        // total en Pedido -> Double
        var total = atributos.stream()
                .filter(a -> "total".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(total.valorPropuesto().path("tipoUml").asText()).isEqualTo("Double");

        // cantidad en DetallePedido -> Integer
        var cantidad = atributos.stream()
                .filter(a -> "cantidad".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(cantidad.valorPropuesto().path("tipoUml").asText()).isEqualTo("Integer");

        // fecha_pago en Pago -> LocalDate
        var fechaPago = atributos.stream()
                .filter(a -> "fecha_pago".equals(a.valorPropuesto().path("nombre").asText()))
                .findFirst().orElseThrow();
        assertThat(fechaPago.valorPropuesto().path("tipoUml").asText()).isEqualTo("LocalDate");

        // 3. Las 5 relaciones deben haberse resuelto exitosamente
        var relaciones = resultado.operaciones().stream()
                .filter(o -> "RELACION".equals(o.tipoEntidad()))
                .toList();
        assertThat(relaciones).hasSize(5);
    }

    @Test
    void uml251VisionConservaColeccionesLimitesFinitosYParametros() {
        var motor =
                new MotorVisionDiagrama(
                        motorTexto, org.mockito.Mockito.mock(ProveedorIa.class), json);
        var propuesta =
                motor.interpretarEstructuraVisual(
                        """
                        {"clases":[{"nombre":"Carpeta","atributos":[{"nombre":"nombres","tipo":"String","minimo":2,"maximo":8,"esOrdenado":true,"coleccionUnica":false}],"operaciones":[{"nombre":"leer","parametros":[{"nombre":"indice","tipoUml":"Integer","direccion":"ENTRADA","esObligatorio":false}]}]},{"nombre":"Archivo"}],"relaciones":[{"origen":"Carpeta","destino":"Archivo","tipo":"COMPOSICION","multiplicidadOrigen":"0..1","multiplicidadDestino":"2..8","rolDestino":"archivos"}]}
                        """,
                        null);
        var attr =
                propuesta.operaciones().stream()
                        .filter(o -> o.tipoEntidad().equals("ATRIBUTO"))
                        .findFirst()
                        .orElseThrow()
                        .valorPropuesto();
        assertThat(attr.path("maximo").asInt()).isEqualTo(8);
        assertThat(attr.path("esOrdenado").asBoolean()).isTrue();
        assertThat(attr.path("coleccionUnica").asBoolean()).isFalse();
        var rel =
                propuesta.operaciones().stream()
                        .filter(o -> o.tipoEntidad().equals("RELACION"))
                        .findFirst()
                        .orElseThrow()
                        .valorPropuesto();
        assertThat(rel.path("maximoDestino").asInt()).isEqualTo(8);
        assertThat(rel.path("rolDestino").asText()).isEqualTo("archivos");
        var op =
                propuesta.operaciones().stream()
                        .filter(o -> o.tipoEntidad().equals("OPERACION"))
                        .findFirst()
                        .orElseThrow()
                        .valorPropuesto();
        assertThat(op.path("parametros").get(0).path("esObligatorio").asBoolean()).isFalse();
    }
}
