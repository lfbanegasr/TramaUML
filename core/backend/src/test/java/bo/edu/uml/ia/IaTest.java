package bo.edu.uml.ia;

import static org.assertj.core.api.Assertions.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import bo.edu.uml.ia.servicio.ProveedorIa;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class IaTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MotorAsistenciaInteligente motor = new MotorAsistenciaInteligente(json);

    @Test
    void interpretaClasesAtributosRelacionYDependencias() {
        var resultado =
                motor.interpretar(
                        "Crea una clase Cliente con atributos id: Long, nombre: String y una clase"
                                + " Pedido con atributo fecha: LocalDate, con relación 1 a *",
                        new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).hasSize(6);
        assertThat(resultado.operaciones())
                .extracting(MotorAsistenciaInteligente.OperacionGenerada::tipoEntidad)
                .containsExactly("CLASE", "ATRIBUTO", "ATRIBUTO", "CLASE", "ATRIBUTO", "RELACION");
        var cliente = resultado.operaciones().getFirst();
        var atributo = resultado.operaciones().get(1);
        var relacion = resultado.operaciones().getLast();
        assertThat(atributo.dependencias()).containsExactly(cliente.id());
        assertThat(relacion.dependencias()).hasSize(2);
        assertThat(relacion.valorPropuesto().get("minimoOrigen").asInt()).isEqualTo(1);
        assertThat(relacion.valorPropuesto().get("maximoOrigen").asInt()).isEqualTo(1);
        assertThat(relacion.valorPropuesto().get("minimoDestino").asInt()).isZero();
        assertThat(relacion.valorPropuesto().get("maximoDestino").isNull()).isTrue();
        assertThat(resultado.resumen()).contains("2 clase(s)", "3 atributo(s)", "1 relación(es)");
    }

    @Test
    void distribuyeClasesSinSuperponerLaPosicionInicial() {
        var resultado =
                motor.interpretar(
                        "Crea clase Uno, clase Dos, clase Tres y clase Cuatro",
                        new Contexto(2, Set.of()));

        assertThat(resultado.operaciones())
                .extracting(
                        o ->
                                o.valorPropuesto().get("x").asInt()
                                        + ":"
                                        + o.valorPropuesto().get("y").asInt())
                .doesNotHaveDuplicates();
    }

    @Test
    void marcaDuplicadoComoInvalidoYNoSeleccionado() {
        var resultado =
                motor.interpretar("Crea una clase Cliente", new Contexto(1, Set.of("Cliente")));

        assertThat(resultado.operaciones().getFirst().estado()).isEqualTo("INVALIDO");
        assertThat(resultado.operaciones().getFirst().seleccionada()).isFalse();
        assertThat(resultado.confianzaGeneral()).isEqualByComparingTo("0.6500");
    }

    @Test
    void exigeUnaClaseIdentificable() {
        assertThatThrownBy(
                        () ->
                                motor.interpretar(
                                        "Agrega algo al diagrama", new Contexto(0, Set.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No se identificó una instrucción compatible");
    }

    @Test
    void usaNormalizacionRemotaOpcionalYConservaElRespaldoLocal() {
        var remoto =
                new ProveedorIa() {
                    @Override
                    public Optional<String> normalizar(String instruccion) {
                        return Optional.of("Crea clase Normalizada");
                    }
                };
        var hibrido = new MotorAsistenciaInteligente(new ObjectMapper(), remoto);
        assertThat(hibrido.interpretar("haz una entidad", new Contexto(0, Set.of())).operaciones())
                .extracting(o -> o.valorPropuesto().path("nombre").asText())
                .containsExactly("Normalizada");

        var remotoInvalido =
                new ProveedorIa() {
                    @Override
                    public Optional<String> normalizar(String instruccion) {
                        return Optional.of("respuesta sin gramática");
                    }
                };
        var respaldo = new MotorAsistenciaInteligente(new ObjectMapper(), remotoInvalido);
        assertThat(
                        respaldo.interpretar("Crea clase Local", new Contexto(0, Set.of()))
                                .operaciones())
                .extracting(o -> o.valorPropuesto().path("nombre").asText())
                .containsExactly("Local");
    }

    @Test
    void interpretaActualizacionYEliminacionConElValorActual() {
        UUID cliente = UUID.randomUUID();
        var valor = json.createObjectNode().put("nombre", "Cliente").put("visibilidad", "PUBLICO");
        var contexto =
                new Contexto(
                        1,
                        Set.of("Cliente"),
                        List.of(new ElementoContexto(cliente, "CLASE", "Cliente", null, valor)));

        var renombre = motor.interpretar("Renombra la clase Cliente a Comprador", contexto);
        assertThat(renombre.operaciones())
                .singleElement()
                .satisfies(
                        operacion -> {
                            assertThat(operacion.tipoOperacion()).isEqualTo("ACTUALIZAR");
                            assertThat(operacion.idEntidad()).isEqualTo(cliente);
                            assertThat(operacion.valorActual().path("nombre").asText())
                                    .isEqualTo("Cliente");
                            assertThat(operacion.valorPropuesto().path("nombre").asText())
                                    .isEqualTo("Comprador");
                        });

        var eliminacion = motor.interpretar("Elimina la clase Cliente", contexto);
        assertThat(eliminacion.operaciones())
                .singleElement()
                .satisfies(
                        operacion -> {
                            assertThat(operacion.tipoOperacion()).isEqualTo("ELIMINAR");
                            assertThat(operacion.valorActual()).isEqualTo(valor);
                            assertThat(operacion.valorPropuesto()).isNull();
                        });
    }

    @Test
    void interpretaCambiosSobreClasesExistentesYMarcaReferenciasDudosas() {
        UUID cliente = UUID.randomUUID(), pedido = UUID.randomUUID();
        var contexto =
                new Contexto(
                        2,
                        Set.of("Cliente", "Pedido"),
                        List.of(
                                new ElementoContexto(
                                        cliente,
                                        "CLASE",
                                        "Cliente",
                                        null,
                                        json.createObjectNode().put("nombre", "Cliente")),
                                new ElementoContexto(
                                        pedido,
                                        "CLASE",
                                        "Pedido",
                                        null,
                                        json.createObjectNode().put("nombre", "Pedido"))));

        var resultado =
                motor.interpretar(
                        "Crea atributo correo: String en clase Cliente; agrega operación guardar():"
                                + " void en clase Cliente; relaciona Cliente con Pedido 1 a *",
                        contexto);
        assertThat(resultado.operaciones())
                .extracting(MotorAsistenciaInteligente.OperacionGenerada::tipoEntidad)
                .containsExactly("ATRIBUTO", "OPERACION", "RELACION");
        assertThat(resultado.operaciones()).allMatch(o -> o.estado().equals("VALIDO"));

        var dudosa = motor.interpretar("Crea atributo codigo: Long en clase Ausente", contexto);
        assertThat(dudosa.operaciones().getFirst().seleccionada()).isFalse();
        assertThat(dudosa.operaciones().getFirst().estado()).isEqualTo("INVALIDO");
    }

    @Test
    void infiereTiposUmlAdecuadosCuandoNoSeEspecifican() {
        var resultado =
                motor.interpretar(
                        "Agrega la clase Producto con atributos nombre, precio, stock, activo y fechaVencimiento",
                        new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).hasSize(6);
        assertThat(resultado.operaciones())
                .extracting(o -> o.valorPropuesto().path("tipoUml").asText())
                .containsExactly("", "String", "Double", "Integer", "Boolean", "LocalDate");
    }

    @Test
    void interpretaSinonimosYAgregaAtributosEnClaseExistenteConInferencia() {
        UUID cliente = UUID.randomUUID();
        var contexto =
                new Contexto(
                        1,
                        Set.of("Cliente"),
                        List.of(
                                new ElementoContexto(
                                        cliente,
                                        "CLASE",
                                        "Cliente",
                                        null,
                                        json.createObjectNode().put("nombre", "Cliente"))));

        var resultado = motor.interpretar("añade el atributo nombre a la clase Cliente", contexto);
        assertThat(resultado.operaciones()).hasSize(1);
        var op = resultado.operaciones().getFirst();
        assertThat(op.tipoOperacion()).isEqualTo("CREAR");
        assertThat(op.tipoEntidad()).isEqualTo("ATRIBUTO");
        assertThat(op.valorPropuesto().path("nombre").asText()).isEqualTo("nombre");
        assertThat(op.valorPropuesto().path("tipoUml").asText()).isEqualTo("String");
        assertThat(op.estado()).isEqualTo("VALIDO");

        var resultadoPrefijo =
                motor.interpretar("en la clase Cliente agrega el atributo telefono", contexto);
        assertThat(resultadoPrefijo.operaciones()).hasSize(1);
        assertThat(resultadoPrefijo.operaciones().getFirst().valorPropuesto().path("tipoUml").asText())
                .isEqualTo("String");

        var resultadoVarios =
                motor.interpretar("agrega los atributos direccion, totalCompras a la clase Cliente", contexto);
        assertThat(resultadoVarios.operaciones()).hasSize(2);
        assertThat(resultadoVarios.operaciones().get(0).valorPropuesto().path("tipoUml").asText())
                .isEqualTo("String");
        assertThat(resultadoVarios.operaciones().get(1).valorPropuesto().path("tipoUml").asText())
                .isEqualTo("Double");
    }

    @Test
    void interpretaRelacionesConSintaxisNaturalYClaseExplicita() {
        UUID producto = UUID.randomUUID(), menu = UUID.randomUUID();
        var contexto =
                new Contexto(
                        2,
                        Set.of("Producto", "Menu"),
                        List.of(
                                new ElementoContexto(
                                        producto,
                                        "CLASE",
                                        "Producto",
                                        null,
                                        json.createObjectNode().put("nombre", "Producto")),
                                new ElementoContexto(
                                        menu,
                                        "CLASE",
                                        "Menu",
                                        null,
                                        json.createObjectNode().put("nombre", "Menu"))));

        var res1 = motor.interpretar("relaciona la clase producto con menu", contexto);
        assertThat(res1.operaciones()).hasSize(1);
        assertThat(res1.operaciones().getFirst().tipoEntidad()).isEqualTo("RELACION");
        assertThat(res1.operaciones().getFirst().estado()).isEqualTo("VALIDO");

        var res2 = motor.interpretar("crea una relacion entre la clase Producto y la clase Menu", contexto);
        assertThat(res2.operaciones()).hasSize(1);
        assertThat(res2.operaciones().getFirst().tipoEntidad()).isEqualTo("RELACION");

        var res3 = motor.interpretar("asocia Producto con Menu", contexto);
        assertThat(res3.operaciones()).hasSize(1);
        assertThat(res3.operaciones().getFirst().tipoEntidad()).isEqualTo("RELACION");
    }

    @Test
    void interpretaHerenciaConDireccionYTiposDeRelacion() {
        String prompt =
                "crea una nueva clase llamada cliente y relacionala con la clase usuario sera una relacion de herencia ,la clase usuario herada a la clase cliente";
        var resultado = motor.interpretar(prompt, new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).hasSize(3);
        assertThat(resultado.operaciones())
                .extracting(MotorAsistenciaInteligente.OperacionGenerada::tipoEntidad)
                .containsExactly("CLASE", "CLASE", "RELACION");
        assertThat(resultado.operaciones()).allMatch(o -> "VALIDO".equals(o.estado()));

        var opCliente = resultado.operaciones().get(0);
        var opUsuario = resultado.operaciones().get(1);
        var opRelacion = resultado.operaciones().get(2);

        assertThat(opCliente.valorPropuesto().path("nombre").asText()).isEqualTo("cliente");
        assertThat(opUsuario.valorPropuesto().path("nombre").asText()).isEqualTo("usuario");

        var rel = opRelacion.valorPropuesto();
        assertThat(rel.path("tipo").asText()).isEqualTo("GENERALIZACION");
        assertThat(rel.path("direccion").asText()).isEqualTo("ORIGEN_A_DESTINO");
        // Origen debe ser la subclase (cliente) y destino la superclase (usuario)
        assertThat(rel.path("origen").asText()).isEqualTo(opCliente.idEntidad().toString());
        assertThat(rel.path("destino").asText()).isEqualTo(opUsuario.idEntidad().toString());
        // Generalización en UML no lleva cardinalidades
        assertThat(rel.hasNonNull("minimoOrigen")).isFalse();
        assertThat(rel.hasNonNull("maximoOrigen")).isFalse();
    }

    @Test
    void interpretaHerenciaHaciaClaseExistenteSinDuplicar() {
        UUID usuarioId = UUID.randomUUID();
        var contexto =
                new Contexto(
                        1,
                        Set.of("usuario"),
                        List.of(
                                new ElementoContexto(
                                        usuarioId,
                                        "CLASE",
                                        "usuario",
                                        null,
                                        json.createObjectNode().put("nombre", "usuario"))));

        String prompt =
                "crea una nueva clase llamada cliente y relacionala con la clase usuario sera una relacion de herencia ,la clase usuario herada a la clase cliente";
        var resultado = motor.interpretar(prompt, contexto);

        // Solo debe crear Cliente y la Relación con el Usuario existente
        assertThat(resultado.operaciones()).hasSize(2);
        assertThat(resultado.operaciones())
                .extracting(MotorAsistenciaInteligente.OperacionGenerada::tipoEntidad)
                .containsExactly("CLASE", "RELACION");
        assertThat(resultado.operaciones()).allMatch(o -> "VALIDO".equals(o.estado()));

        var opCliente = resultado.operaciones().get(0);
        var opRelacion = resultado.operaciones().get(1);

        assertThat(opCliente.valorPropuesto().path("nombre").asText()).isEqualTo("cliente");

        var rel = opRelacion.valorPropuesto();
        assertThat(rel.path("tipo").asText()).isEqualTo("GENERALIZACION");
        assertThat(rel.path("direccion").asText()).isEqualTo("ORIGEN_A_DESTINO");
        assertThat(rel.path("origen").asText()).isEqualTo(opCliente.idEntidad().toString());
        assertThat(rel.path("destino").asText()).isEqualTo(usuarioId.toString());
    }

    @Test
    void interpretaSintaxisDirectaDeHerencia() {
        UUID usuarioId = UUID.randomUUID(), clienteId = UUID.randomUUID();
        var contexto =
                new Contexto(
                        2,
                        Set.of("Usuario", "Cliente"),
                        List.of(
                                new ElementoContexto(
                                        usuarioId,
                                        "CLASE",
                                        "Usuario",
                                        null,
                                        json.createObjectNode().put("nombre", "Usuario")),
                                new ElementoContexto(
                                        clienteId,
                                        "CLASE",
                                        "Cliente",
                                        null,
                                        json.createObjectNode().put("nombre", "Cliente"))));

        var resDirecta = motor.interpretar("cliente hereda de usuario", contexto);
        assertThat(resDirecta.operaciones()).hasSize(1);
        var relDirecta = resDirecta.operaciones().getFirst().valorPropuesto();
        assertThat(relDirecta.path("tipo").asText()).isEqualTo("GENERALIZACION");
        assertThat(relDirecta.path("origen").asText()).isEqualTo(clienteId.toString());
        assertThat(relDirecta.path("destino").asText()).isEqualTo(usuarioId.toString());

        var resInversa = motor.interpretar("la clase usuario herada a la clase cliente", contexto);
        assertThat(resInversa.operaciones()).hasSize(1);
        var relInversa = resInversa.operaciones().getFirst().valorPropuesto();
        assertThat(relInversa.path("tipo").asText()).isEqualTo("GENERALIZACION");
        assertThat(relInversa.path("origen").asText()).isEqualTo(clienteId.toString());
        assertThat(relInversa.path("destino").asText()).isEqualTo(usuarioId.toString());
    }

    @Test
    void interpretaPromptCompuestoConAtributosYCardinalidadDual() {
        String prompt =
                "crea la clase cliente con sus atributos id:integer y nombre:string y relaciona la clase cliente con la clase pedido ,la relacion sera de asociacion de 1 a 1 y 1 a muchos";
        var resultado = motor.interpretar(prompt, new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).hasSize(5);
        assertThat(resultado.operaciones())
                .extracting(MotorAsistenciaInteligente.OperacionGenerada::tipoEntidad)
                .containsExactly("CLASE", "ATRIBUTO", "ATRIBUTO", "CLASE", "RELACION");
        assertThat(resultado.operaciones()).allMatch(o -> "VALIDO".equals(o.estado()));

        var opCliente = resultado.operaciones().get(0);
        var opAttrId = resultado.operaciones().get(1);
        var opAttrNombre = resultado.operaciones().get(2);
        var opPedido = resultado.operaciones().get(3);
        var opRelacion = resultado.operaciones().get(4);

        assertThat(opCliente.valorPropuesto().path("nombre").asText()).isEqualTo("cliente");
        assertThat(opAttrId.valorPropuesto().path("nombre").asText()).isEqualTo("id");
        assertThat(opAttrId.valorPropuesto().path("tipoUml").asText()).isEqualTo("Integer");
        assertThat(opAttrNombre.valorPropuesto().path("nombre").asText()).isEqualTo("nombre");
        assertThat(opAttrNombre.valorPropuesto().path("tipoUml").asText()).isEqualTo("String");
        assertThat(opPedido.valorPropuesto().path("nombre").asText()).isEqualTo("pedido");

        var rel = opRelacion.valorPropuesto();
        assertThat(rel.path("tipo").asText()).isEqualTo("ASOCIACION");
        assertThat(rel.path("origen").asText()).isEqualTo(opCliente.idEntidad().toString());
        assertThat(rel.path("destino").asText()).isEqualTo(opPedido.idEntidad().toString());
        // Cardinalidad dual: 1 a 1 y 1 a muchos
        assertThat(rel.path("minimoOrigen").asInt()).isEqualTo(1);
        assertThat(rel.path("maximoOrigen").asInt()).isEqualTo(1);
        assertThat(rel.path("minimoDestino").asInt()).isEqualTo(1);
        assertThat(rel.path("maximoDestino").isNull()).isTrue();
    }

    @Test
    void interpretaVerbosDeEliminacionYActualizacionFlexibles() {
        UUID clienteId = UUID.randomUUID();
        var contexto =
                new Contexto(
                        1,
                        Set.of("Cliente"),
                        List.of(
                                new ElementoContexto(
                                        clienteId,
                                        "CLASE",
                                        "Cliente",
                                        null,
                                        json.createObjectNode().put("nombre", "Cliente")),
                                new ElementoContexto(
                                        UUID.randomUUID(),
                                        "ATRIBUTO",
                                        "nombre",
                                        clienteId,
                                        json.createObjectNode().put("nombre", "nombre").put("tipoUml", "String"))));

        var resQuita = motor.interpretar("quita la clase Cliente", contexto);
        assertThat(resQuita.operaciones()).hasSize(1);
        assertThat(resQuita.operaciones().getFirst().tipoOperacion()).isEqualTo("ELIMINAR");

        var resBorra = motor.interpretar("borra el atributo nombre de la clase Cliente", contexto);
        assertThat(resBorra.operaciones()).hasSize(1);
        assertThat(resBorra.operaciones().getFirst().tipoOperacion()).isEqualTo("ELIMINAR");

        var resActualiza = motor.interpretar("actualiza el atributo nombre en clase Cliente a razonSocial: string", contexto);
        assertThat(resActualiza.operaciones()).hasSize(1);
        var opAct = resActualiza.operaciones().getFirst();
        assertThat(opAct.tipoOperacion()).isEqualTo("ACTUALIZAR");
        assertThat(opAct.valorPropuesto().path("nombre").asText()).isEqualTo("razonSocial");
        assertThat(opAct.valorPropuesto().path("tipoUml").asText()).isEqualTo("String");
    }

    @Test
    void interpretaSinonimosCampoPropiedadMetodoYOrdenesInversos() {
        UUID cuentaId = UUID.randomUUID(), opCalcularId = UUID.randomUUID();
        var contexto =
                new Contexto(
                        1,
                        Set.of("Cuenta"),
                        List.of(
                                new ElementoContexto(
                                        cuentaId,
                                        "CLASE",
                                        "Cuenta",
                                        null,
                                        json.createObjectNode().put("nombre", "Cuenta")),
                                new ElementoContexto(
                                        UUID.randomUUID(),
                                        "ATRIBUTO",
                                        "saldo",
                                        cuentaId,
                                        json.createObjectNode().put("nombre", "saldo").put("tipoUml", "Double")),
                                new ElementoContexto(
                                        opCalcularId,
                                        "OPERACION",
                                        "calcular",
                                        cuentaId,
                                        json.createObjectNode().put("nombre", "calcular").put("tipoRetorno", "Double"))));

        // Campo y propiedad
        var resCampo = motor.interpretar("crear el campo limite: double en la clase Cuenta", contexto);
        assertThat(resCampo.operaciones()).hasSize(1);
        assertThat(resCampo.operaciones().getFirst().tipoEntidad()).isEqualTo("ATRIBUTO");
        assertThat(resCampo.operaciones().getFirst().valorPropuesto().path("tipoUml").asText()).isEqualTo("Double");

        var resPropiedad = motor.interpretar("agregar la propiedad activa: bool en clase Cuenta", contexto);
        assertThat(resPropiedad.operaciones()).hasSize(1);
        assertThat(resPropiedad.operaciones().getFirst().valorPropuesto().path("tipoUml").asText()).isEqualTo("Boolean");

        // Método y función
        var resMetodo = motor.interpretar("definir el método depositar(): void en clase Cuenta", contexto);
        assertThat(resMetodo.operaciones()).hasSize(1);
        assertThat(resMetodo.operaciones().getFirst().tipoEntidad()).isEqualTo("OPERACION");
        assertThat(resMetodo.operaciones().getFirst().valorPropuesto().path("tipoRetorno").asText()).isEqualTo("void");

        // Eliminación con orden inverso
        var resElimAttr = motor.interpretar("eliminar de la clase Cuenta el campo saldo", contexto);
        assertThat(resElimAttr.operaciones()).hasSize(1);
        assertThat(resElimAttr.operaciones().getFirst().tipoOperacion()).isEqualTo("ELIMINAR");

        var resElimOp = motor.interpretar("quitar de la clase Cuenta la función calcular()", contexto);
        assertThat(resElimOp.operaciones()).hasSize(1);
        assertThat(resElimOp.operaciones().getFirst().tipoOperacion()).isEqualTo("ELIMINAR");
    }

    @Test
    void diferenciaCorrectamenteTipoFloatYDouble() {
        var resultado =
                motor.interpretar(
                        "crea clase Sensor con atributos temperatura: float y precision: double",
                        new Contexto(0, Set.of()));

        assertThat(resultado.operaciones()).hasSize(3);
        var atributos = resultado.operaciones().stream()
                .filter(o -> "ATRIBUTO".equals(o.tipoEntidad()))
                .toList();
        assertThat(atributos).hasSize(2);
        assertThat(atributos.get(0).valorPropuesto().path("nombre").asText()).isEqualTo("temperatura");
        assertThat(atributos.get(0).valorPropuesto().path("tipoUml").asText()).isEqualTo("Float");
        assertThat(atributos.get(1).valorPropuesto().path("nombre").asText()).isEqualTo("precision");
        assertThat(atributos.get(1).valorPropuesto().path("tipoUml").asText()).isEqualTo("Double");
    }
}
