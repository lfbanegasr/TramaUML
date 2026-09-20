package bo.edu.uml;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import bo.edu.uml.acceso.servicio.*;
import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.*;

import jakarta.servlet.http.Cookie;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.datasource.url=${UML_TEST_DB_URL}",
            "spring.datasource.password=${UML_TEST_DB_PASSWORD}",
            "uml.cookie-secure=false",
            "uml.bootstrap.enabled=false",
            "uml.validacion.worker=false",
            "uml.servicios.clave-maestra=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "uml.servicios.version-clave=pruebas-v1"
        })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NucleoIntegracionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired InicializarAdministrador inicializar;
    @Autowired AccesoRepositorio acceso;
    @Autowired bo.edu.uml.colaboracion.servicio.ColaboracionServicio colaboracion;
    @MockitoSpyBean bo.edu.uml.colaboracion.controlador.ColaboracionWebSocketHandler canal;
    private String usuario, clave;
    private UUID admin;
    private final Map<String, UUID> sesionesEdicion = new ConcurrentHashMap<>();

    private record Cliente(Cookie sesion, Cookie csrf, String token, String header) {}

    @BeforeAll
    void preparar() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .matches("uml_collab_test_[a-z0-9_]+");
        assertThat(acceso.hayUsuarios())
                .as("Utiliza una base aislada nueva sin usuarios")
                .isFalse();
        usuario = "admin_" + UUID.randomUUID().toString().substring(0, 8);
        clave = UUID.randomUUID().toString();
        inicializar.inicializar(
                usuario, usuario + "@example.test", "Administrador de pruebas", clave);
        admin = acceso.bloquearCuenta(usuario).orElseThrow().id();
    }

    private Cookie cookie(MvcResult r, String nombre) {
        return r.getResponse().getHeaders("Set-Cookie").stream()
                .filter(s -> s.startsWith(nombre + "="))
                .map(s -> new Cookie(nombre, s.substring(nombre.length() + 1, s.indexOf(';'))))
                .findFirst()
                .orElseThrow();
    }

    private Cliente csrf(Cookie sesion) throws Exception {
        var r = mvc.perform(get("/api/acceso/csrf")).andExpect(status().isOk()).andReturn();
        var d = json.readTree(r.getResponse().getContentAsString());
        return new Cliente(
                sesion, cookie(r, "XSRF-TOKEN"), d.get("token").asText(), d.get("header").asText());
    }

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder r, Cliente c) {
        if (c.sesion() != null) r.cookie(c.sesion());
        return r.cookie(c.csrf()).header(c.header(), c.token());
    }

    private Cliente login(String u, String pw) throws Exception {
        var r =
                mvc.perform(
                                autorizado(post("/api/acceso/entrar"), csrf(null))
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "identificador",
                                                                u,
                                                                "contrasena",
                                                                pw))))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(r.getResponse().getHeaders("Set-Cookie").toString())
                .contains("HttpOnly", "SameSite=Strict")
                .doesNotContain(pw);
        return csrf(cookie(r, "UML_SESION"));
    }

    private JsonNode proyecto(Cliente c) throws Exception {
        var r =
                mvc.perform(
                                autorizado(post("/api/proyectos"), c)
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "codigo",
                                                                "P_" + UUID.randomUUID(),
                                                                "nombre",
                                                                "Proyecto de prueba",
                                                                "descripcion",
                                                                "Ficticio"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        assertThat(r.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(s -> s.startsWith("XSRF-TOKEN=") && s.contains("Max-Age=0"));
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String cambio(long base, UUID cliente, Object... a) throws Exception {
        return json.writeValueAsString(
                Map.of("revisionBase", base, "idCambioCliente", cliente, "acciones", List.of(a)));
    }

    private Map<String, Object> clase(UUID id, String nombre) {
        return Map.of(
                "operacion",
                "CREAR",
                "entidad",
                "CLASE",
                "idEntidad",
                id,
                "datos",
                Map.of("nombre", nombre, "x", 0, "y", 0));
    }

    private ResultActions enviar(Cliente c, String modelo, String cuerpo) throws Exception {
        var cambio = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(cuerpo);
        if (!cambio.hasNonNull("idSesionEdicion"))
            cambio.put("idSesionEdicion", asegurarSesion(c, modelo).toString());
        return mvc.perform(
                autorizado(post("/api/modelos/" + modelo + "/cambios"), c)
                        .contentType("application/json")
                        .content(json.writeValueAsString(cambio)));
    }

    private UUID asegurarSesion(Cliente c, String modelo) throws Exception {
        String llave = c.sesion().getValue() + ":" + modelo;
        synchronized (sesionesEdicion) {
            var existente = sesionesEdicion.get(llave);
            UUID solicitud = UUID.randomUUID();
            var respuesta =
                    escribir(
                                    c,
                                    post("/api/modelos/" + modelo + "/sesiones"),
                                    Map.of("idSolicitudCliente", solicitud))
                            .andReturn()
                            .getResponse();
            if (respuesta.getStatus() != 201)
                return existente == null ? UUID.randomUUID() : existente;
            UUID sesion =
                    UUID.fromString(
                            json.readTree(respuesta.getContentAsString()).get("id").asText());
            jdbc.update(
                    "update uml_colaborativo.participante_sesion_modelo set"
                            + " estado_presencia='EN_LINEA' where id_sesion_edicion=? and"
                            + " id_usuario=(select id_usuario from"
                            + " uml_colaborativo.sesion_autenticacion where"
                            + " hash_token=digest(?,'sha256'))",
                    sesion,
                    c.sesion().getValue());
            jdbc.update(
                    "update uml_colaborativo.sesion_edicion_modelo set modo='EDICION' where id=?"
                            + " and exists(select 1 from"
                            + " uml_colaborativo.participante_sesion_modelo where"
                            + " id_sesion_edicion=? and modo_participante='EDITOR' and"
                            + " estado_presencia='EN_LINEA')",
                    sesion,
                    sesion);
            sesionesEdicion.put(llave, sesion);
            return sesion;
        }
    }

    private UUID fixture(String u) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.usuario(id,nombre_usuario,correo_electronico,nombre_completo,hash_contrasena)"
                    + " values(?,?,?,?,?)",
                id,
                u,
                u + "@example.test",
                "Usuario ficticio",
                encoder.encode(clave));
        return id;
    }

    @Test
    void inicializacionCerradaYHash() {
        assertThat(
                        jdbc.queryForObject(
                                "select hash_contrasena from uml_colaborativo.usuario where id=?",
                                String.class,
                                admin))
                .startsWith("$2");
        assertThatThrownBy(
                        () ->
                                inicializar.inicializar(
                                        "otro_admin",
                                        "otro@example.test",
                                        "Otro",
                                        UUID.randomUUID().toString()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () -> inicializar.inicializar("otro", "otro@example.test", "Otro", "corta"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accesoYCsrf() throws Exception {
        mvc.perform(get("/api/proyectos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/acceso/entrar").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        var c = login(usuario, clave);
        mvc.perform(get("/api/acceso/sesion").cookie(c.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(admin.toString()));
    }

    @Test
    void revocacionEInactividad() throws Exception {
        var c = login(usuario, clave);
        mvc.perform(autorizado(post("/api/acceso/salir"), c)).andExpect(status().isNoContent());
        mvc.perform(get("/api/proyectos").cookie(c.sesion())).andExpect(status().isUnauthorized());
        String u = "inactivo_" + UUID.randomUUID();
        UUID id = fixture(u);
        var otra = login(u, clave);
        jdbc.update("update uml_colaborativo.usuario set estado='INACTIVO' where id=?", id);
        mvc.perform(get("/api/proyectos").cookie(otra.sesion()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historialSesionesPropioYAdministrativoRespetaPaginacionYPermisos() throws Exception {
        var administrador = login(usuario, clave);
        String nombre = "sesiones_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cuenta = fixture(nombre);
        var primera = login(nombre, clave);
        var segunda = login(nombre, clave);

        mvc.perform(autorizado(post("/api/acceso/salir"), primera))
                .andExpect(status().isNoContent());

        var propias =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                get("/api/acceso/sesiones")
                                                        .param("pagina", "0")
                                                        .param("tamano", "1"),
                                                segunda))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.pagina").value(0))
                                .andExpect(jsonPath("$.tamano").value(1))
                                .andExpect(jsonPath("$.total").value(2))
                                .andExpect(jsonPath("$.sesiones.length()").value(1)));
        assertThat(propias.get("sesiones").get(0).get("activa").asBoolean()).isTrue();

        var administrativas =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                get("/api/administracion/usuarios/"
                                                                + cuenta
                                                                + "/sesiones")
                                                        .param("tamano", "20"),
                                                administrador))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2)));
        assertThat(administrativas.get("sesiones"))
                .anySatisfy(
                        sesion -> {
                            assertThat(sesion.get("activa").asBoolean()).isFalse();
                            assertThat(sesion.get("motivoRevocacion").asText())
                                    .isEqualTo("CIERRE_USUARIO");
                        });

        mvc.perform(autorizado(get("/api/administracion/usuarios/" + admin + "/sesiones"), segunda))
                .andExpect(status().isForbidden());
        mvc.perform(autorizado(get("/api/acceso/sesiones").param("tamano", "101"), segunda))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bloqueoPersistente() throws Exception {
        String u = "intentos_" + UUID.randomUUID();
        UUID id = fixture(u);
        for (int i = 0; i < 5; i++)
            mvc.perform(
                            autorizado(post("/api/acceso/entrar"), csrf(null))
                                    .contentType("application/json")
                                    .content(
                                            json.writeValueAsString(
                                                    Map.of(
                                                            "identificador",
                                                            u,
                                                            "contrasena",
                                                            "incorrecta"))))
                    .andExpect(status().isUnauthorized());
        assertThat(
                        jdbc.queryForObject(
                                "select intentos_fallidos_acceso from uml_colaborativo.usuario"
                                        + " where id=?",
                                Integer.class,
                                id))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "select bloqueado_hasta>clock_timestamp() from"
                                        + " uml_colaborativo.usuario where id=?",
                                Boolean.class,
                                id))
                .isTrue();
    }

    @Test
    void proyectosPrivadosYLector() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        mvc.perform(get("/api/modelos/" + modelo).cookie(c.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.clases").isEmpty());
        String u = "externo_" + UUID.randomUUID();
        UUID id = fixture(u);
        var ext = login(u, clave);
        mvc.perform(get("/api/proyectos").cookie(ext.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proyectos").isEmpty());
        mvc.perform(get("/api/proyectos/" + p.get("id").asText()).cookie(ext.sesion()))
                .andExpect(status().isNotFound());
        enviar(ext, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Intruso")))
                .andExpect(status().isNotFound());
        UUID miembro = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.miembro_proyecto(id,id_proyecto,id_usuario)"
                        + " values(?,?,?)",
                miembro,
                UUID.fromString(p.get("id").asText()),
                id);
        jdbc.update(
                "insert into uml_colaborativo.rol_miembro_proyecto(id_miembro_proyecto,id_rol)"
                        + " select ?,id from uml_colaborativo.rol where codigo='LECTOR_PROYECTO'",
                miembro);
        mvc.perform(get("/api/modelos/" + modelo).cookie(ext.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeEditar").value(false));
        enviar(ext, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Intruso")))
                .andExpect(status().isNotFound());
        jdbc.update(
                "update uml_colaborativo.miembro_proyecto set estado='RETIRADO' where id=?",
                miembro);
        mvc.perform(get("/api/modelos/" + modelo).cookie(ext.sesion()))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotenciaYRevisionObsoleta() throws Exception {
        var c = login(usuario, clave);
        String modelo = proyecto(c).get("idModelo").asText();
        UUID cliente = UUID.randomUUID(), id = UUID.randomUUID();
        String cuerpo = cambio(1, cliente, clase(id, "Persona"));
        enviar(c, modelo, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));
        enviar(c, modelo, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(true));
        enviar(c, modelo, cambio(1, cliente, clase(id, "Otra"))).andExpect(status().isConflict());
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Obsoleta")))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.clase_uml where id_modelo=?",
                                Integer.class,
                                UUID.fromString(modelo)))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.version_modelo_uml where"
                                        + " id_modelo=?",
                                Integer.class,
                                UUID.fromString(modelo)))
                .isEqualTo(2);
    }

    @Test
    void rollbackTotal() throws Exception {
        var c = login(usuario, clave);
        String modelo = proyecto(c).get("idModelo").asText();
        Object a =
                Map.of(
                        "operacion",
                        "CREAR",
                        "entidad",
                        "ATRIBUTO",
                        "idEntidad",
                        UUID.randomUUID(),
                        "datos",
                        Map.of("idClase", UUID.randomUUID(), "nombre", "id", "tipoUml", "UUID"));
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Revertir"), a))
                .andExpect(status().isNotFound());
        assertThat(
                        jdbc.queryForObject(
                                "select revision_actual from uml_colaborativo.modelo_uml where"
                                        + " id=?",
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.clase_uml where id_modelo=?",
                                Integer.class,
                                UUID.fromString(modelo)))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.cambio_modelo where"
                                        + " id_modelo=?",
                                Integer.class,
                                UUID.fromString(modelo)))
                .isEqualTo(1);
    }

    @Test
    void borradoLogicoEIdentificador() throws Exception {
        var c = login(usuario, clave);
        String modelo = proyecto(c).get("idModelo").asText();
        UUID id = UUID.randomUUID(), a = UUID.randomUUID();
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(id, "Cliente")))
                .andExpect(status().isOk());
        var d =
                new HashMap<String, Object>(
                        Map.of(
                                "idClase",
                                id,
                                "nombre",
                                "id",
                                "tipoUml",
                                "UUID",
                                "esIdentificador",
                                true,
                                "minimo",
                                1));
        d.put("maximo", null);
        enviar(
                        c,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "CREAR",
                                        "entidad",
                                        "ATRIBUTO",
                                        "idEntidad",
                                        a,
                                        "datos",
                                        d)))
                .andExpect(status().isUnprocessableEntity());
        d.put("maximo", 1);
        enviar(
                        c,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "CREAR",
                                        "entidad",
                                        "ATRIBUTO",
                                        "idEntidad",
                                        a,
                                        "datos",
                                        d)))
                .andExpect(status().isOk());
        enviar(
                        c,
                        modelo,
                        cambio(
                                3,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ELIMINAR",
                                        "entidad",
                                        "CLASE",
                                        "idEntidad",
                                        id)))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select eliminado_en is not null from uml_colaborativo.atributo_uml"
                                        + " where id=?",
                                Boolean.class,
                                a))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select instantanea::text from uml_colaborativo.version_modelo_uml"
                                        + " where id_modelo=? and revision=3",
                                String.class,
                                UUID.fromString(modelo)))
                .contains("Cliente", "UUID");
    }

    @Test
    void concurrenciaReal() throws Exception {
        var c = login(usuario, clave);
        String modelo = proyecto(c).get("idModelo").asText();
        String a = cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "A")),
                b = cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "B"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var f1 =
                    pool.submit(
                            () -> {
                                gate.await();
                                return enviar(c, modelo, a).andReturn().getResponse().getStatus();
                            });
            var f2 =
                    pool.submit(
                            () -> {
                                gate.await();
                                return enviar(c, modelo, b).andReturn().getResponse().getStatus();
                            });
            gate.countDown();
            assertThat(List.of(f1.get(20, TimeUnit.SECONDS), f2.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void protocoloObligatorio() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into uml_colaborativo.clase_uml(id_modelo,nombre)"
                                                + " values(?,?)",
                                        UUID.randomUUID(),
                                        "Fuera"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select"
                                    + " has_table_privilege(current_user,'uml_colaborativo.version_modelo_uml','INSERT')",
                                Boolean.class))
                .isFalse();
    }

    private Map<String, Object> parametro(UUID id, String nombre, String tipo) {
        return Map.of(
                "id",
                id,
                "nombre",
                nombre,
                "tipoUml",
                tipo,
                "direccion",
                "ENTRADA",
                "esObligatorio",
                true);
    }

    private Map<String, Object> operacion(
            String accion, UUID id, UUID clase, String nombre, Object... parametros) {
        return Map.of(
                "operacion",
                accion,
                "entidad",
                "OPERACION",
                "idEntidad",
                id,
                "datos",
                Map.of(
                        "idClase",
                        clase,
                        "nombre",
                        nombre,
                        "tipoRetorno",
                        "String",
                        "parametros",
                        List.of(parametros)));
    }

    @Test
    void operacionesParametrosYFirmaAtomica() throws Exception {
        var c = login(usuario, clave);
        String modelo = proyecto(c).get("idModelo").asText();
        UUID clase = UUID.randomUUID(),
                op = UUID.randomUUID(),
                p1 = UUID.randomUUID(),
                p2 = UUID.randomUUID();
        enviar(
                        c,
                        modelo,
                        cambio(
                                1,
                                UUID.randomUUID(),
                                clase(clase, "Servicio"),
                                operacion(
                                        "CREAR",
                                        op,
                                        clase,
                                        "buscar",
                                        parametro(p1, "texto", "String"),
                                        parametro(p2, "limite", "Integer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));
        enviar(
                        c,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                operacion(
                                        "ACTUALIZAR",
                                        op,
                                        clase,
                                        "buscar",
                                        parametro(p2, "limite", "Integer"),
                                        parametro(p1, "texto", "String"))))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select orden from uml_colaborativo.parametro_uml where id=?",
                                Integer.class,
                                p2))
                .isZero();
        UUID duplicada = UUID.randomUUID();
        enviar(
                        c,
                        modelo,
                        cambio(
                                3,
                                UUID.randomUUID(),
                                operacion(
                                        "CREAR",
                                        duplicada,
                                        clase,
                                        "buscar",
                                        parametro(UUID.randomUUID(), "n", "Integer"),
                                        parametro(UUID.randomUUID(), "t", "String"))))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "select revision_actual from uml_colaborativo.modelo_uml where"
                                        + " id=?",
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.operacion_uml where id=?",
                                Integer.class,
                                duplicada))
                .isZero();
        enviar(
                        c,
                        modelo,
                        cambio(
                                3,
                                UUID.randomUUID(),
                                operacion(
                                        "ACTUALIZAR",
                                        op,
                                        clase,
                                        "buscar",
                                        parametro(p1, "texto", "String"))))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select eliminado_en is not null from"
                                        + " uml_colaborativo.parametro_uml where id=?",
                                Boolean.class,
                                p2))
                .isTrue();
        enviar(
                        c,
                        modelo,
                        cambio(
                                4,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ELIMINAR",
                                        "entidad",
                                        "OPERACION",
                                        "idEntidad",
                                        op)))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select eliminado_en is not null from"
                                        + " uml_colaborativo.parametro_uml where id=?",
                                Boolean.class,
                                p1))
                .isTrue();
    }

    @Autowired bo.edu.uml.validacion.servicio.ProcesarValidacion procesadorValidacion;

    private ResultActions escribir(Cliente c, MockHttpServletRequestBuilder peticion, Object datos)
            throws Exception {
        return mvc.perform(
                autorizado(peticion, c)
                        .contentType("application/json")
                        .content(json.writeValueAsString(datos)));
    }

    private JsonNode respuesta(ResultActions accion) throws Exception {
        return json.readTree(accion.andReturn().getResponse().getContentAsString());
    }

    private UUID rol(String codigo) {
        return jdbc.queryForObject(
                "select id from uml_colaborativo.rol where codigo=?", UUID.class, codigo);
    }

    private UUID permiso(String codigo) {
        return jdbc.queryForObject(
                "select id from uml_colaborativo.permiso where codigo=?", UUID.class, codigo);
    }

    private Set<String> permisosRol(String codigo) {
        return new HashSet<>(
                jdbc.queryForList(
                        "select p.codigo from uml_colaborativo.rol r join"
                                + " uml_colaborativo.rol_permiso rp on rp.id_rol=r.id join"
                                + " uml_colaborativo.permiso p on p.id=rp.id_permiso where"
                                + " r.codigo=?",
                        String.class,
                        codigo));
    }

    private Map<String, Object> datosUsuario(String u, List<UUID> roles) {
        return new HashMap<>(
                Map.of(
                        "usuario",
                        u,
                        "correo",
                        u + "@example.test",
                        "nombre",
                        "Cuenta ficticia " + u,
                        "estado",
                        "PENDIENTE",
                        "roles",
                        roles));
    }

    private JsonNode listaUsuarios(Cliente c, String filtro) throws Exception {
        return respuesta(
                mvc.perform(
                                autorizado(
                                        get("/api/administracion/usuarios").param("filtro", filtro),
                                        c))
                        .andExpect(status().isOk()));
    }

    @Test
    void altaActivacionRestablecimientoYRevocacion() throws Exception {
        var a = login(usuario, clave);
        String u = "nuevo_" + UUID.randomUUID().toString().substring(0, 8);
        var d = datosUsuario(u, List.of());
        var credencial =
                respuesta(
                        escribir(a, post("/api/administracion/usuarios"), d)
                                .andExpect(status().isCreated()));
        UUID id = UUID.fromString(credencial.get("id").asText());
        String temporal = credencial.get("credencialTemporal").asText(),
                nueva = UUID.randomUUID().toString();
        assertThat(
                        jdbc.queryForObject(
                                "select hash_contrasena from uml_colaborativo.usuario where id=?",
                                String.class,
                                id))
                .doesNotContain(temporal);
        escribir(
                        csrf(null),
                        post("/api/acceso/entrar"),
                        Map.of("identificador", u, "contrasena", temporal))
                .andExpect(status().isUnauthorized());
        escribir(
                        csrf(null),
                        post("/api/acceso/activar"),
                        Map.of("identificador", u, "temporal", "incorrecta", "nueva", nueva))
                .andExpect(status().isUnauthorized());
        assertThat(
                        jdbc.queryForObject(
                                "select intentos_fallidos_acceso from uml_colaborativo.usuario"
                                        + " where id=?",
                                Integer.class,
                                id))
                .isEqualTo(1);
        escribir(
                        csrf(null),
                        post("/api/acceso/activar"),
                        Map.of("identificador", u, "temporal", temporal, "nueva", nueva))
                .andExpect(status().isNoContent());
        escribir(
                        csrf(null),
                        post("/api/acceso/activar"),
                        Map.of("identificador", u, "temporal", temporal, "nueva", nueva))
                .andExpect(status().isUnauthorized());
        var cliente = login(u, nueva);
        var cuenta = listaUsuarios(a, u).get(0);
        d.put("estado", "INACTIVO");
        d.put("revision", cuenta.get("revision").asText());
        escribir(a, put("/api/administracion/usuarios/" + id), d).andExpect(status().isOk());
        mvc.perform(autorizado(get("/api/acceso/sesion"), cliente))
                .andExpect(status().isUnauthorized());
        var vigente = listaUsuarios(a, u).get(0);
        var restablecida =
                respuesta(
                        escribir(
                                        a,
                                        post("/api/administracion/usuarios/" + id + "/restablecer"),
                                        Map.of("revision", vigente.get("revision").asText()))
                                .andExpect(status().isOk()));
        assertThat(restablecida.get("credencialTemporal").asText()).isNotEqualTo(temporal);
        escribir(a, post("/api/administracion/usuarios"), d).andExpect(status().isConflict());
        assertThat(listaUsuarios(a, u).toString())
                .doesNotContain(
                        temporal,
                        nueva,
                        restablecida.get("credencialTemporal").asText(),
                        "hash_contrasena");
    }

    @Test
    void administracionProtegidaYRevisionObsoleta() throws Exception {
        var a = login(usuario, clave);
        String u = "perfil_" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = fixture(u);
        var lector = login(u, clave);
        mvc.perform(autorizado(get("/api/administracion/usuarios"), lector))
                .andExpect(status().isForbidden());
        var propio = datosUsuario(usuario, List.of(rol("SUPERADMINISTRADOR")));
        propio.put("estado", "INACTIVO");
        propio.put("revision", listaUsuarios(a, usuario).get(0).get("revision").asText());
        escribir(a, put("/api/administracion/usuarios/" + admin), propio)
                .andExpect(status().isUnprocessableEntity());
        var d = datosUsuario(u, List.of());
        d.put("estado", "ACTIVO");
        d.put("revision", listaUsuarios(a, u).get(0).get("revision").asText());
        escribir(a, put("/api/administracion/usuarios/" + id), d).andExpect(status().isOk());
        escribir(a, put("/api/administracion/usuarios/" + id), d).andExpect(status().isConflict());
        escribir(
                        a,
                        post("/api/administracion/usuarios"),
                        datosUsuario(
                                "proyecto_" + UUID.randomUUID().toString().substring(0, 8),
                                List.of(rol("EDITOR_PROYECTO"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rolesPermisosAmbitoYProteccion() throws Exception {
        var a = login(usuario, clave);
        var d =
                new HashMap<String, Object>(
                        Map.of(
                                "codigo",
                                "ROL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                                "nombre",
                                "Rol ficticio",
                                "descripcion",
                                "Prueba",
                                "ambito",
                                "GLOBAL",
                                "estado",
                                "ACTIVO",
                                "permisos",
                                List.of(permiso("CREAR_PROYECTOS"))));
        var r =
                respuesta(
                        escribir(a, post("/api/administracion/roles"), d)
                                .andExpect(status().isCreated()));
        String id = r.get("id").asText();
        d.put("revision", r.get("revision").asText());
        d.put("permisos", List.of());
        escribir(a, put("/api/administracion/roles/" + id), d).andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.rol_permiso where id_rol=?",
                                Integer.class,
                                UUID.fromString(id)))
                .isZero();
        escribir(a, put("/api/administracion/roles/" + id), d).andExpect(status().isConflict());
        d.put("codigo", "BAD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        d.put("permisos", List.of(permiso("EDITAR_MODELO")));
        escribir(a, post("/api/administracion/roles"), d)
                .andExpect(status().isUnprocessableEntity());
        d.put("codigo", "SUPERADMINISTRADOR");
        escribir(a, put("/api/administracion/roles/" + rol("SUPERADMINISTRADOR")), d)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void permisosDeHistorialEInteroperabilidadUsanRolesVigentes() {
        assertThat(permisosRol("PROPIETARIO_PROYECTO"))
                .contains(
                        "VER_HISTORIAL_MODELO",
                        "RESTAURAR_MODELO",
                        "EXPORTAR_XMI",
                        "IMPORTAR_XMI",
                        "CONFIGURAR_BACKEND",
                        "GENERAR_BACKEND",
                        "DESCARGAR_ARTEFACTO");
        assertThat(permisosRol("EDITOR_PROYECTO"))
                .contains(
                        "VER_HISTORIAL_MODELO",
                        "EXPORTAR_XMI",
                        "IMPORTAR_XMI",
                        "DESCARGAR_ARTEFACTO")
                .doesNotContain("RESTAURAR_MODELO", "CONFIGURAR_BACKEND", "GENERAR_BACKEND");
        assertThat(permisosRol("LECTOR_PROYECTO"))
                .contains("VER_HISTORIAL_MODELO")
                .doesNotContain("RESTAURAR_MODELO", "EXPORTAR_XMI", "GENERAR_BACKEND");
    }

    @Test
    void serviciosPlataformaProtegenRotanYNoExponenCredenciales() throws Exception {
        var administrador = login(usuario, clave);
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String credencialInicial = "credencial-inicial-" + sufijo;
        var datos =
                new HashMap<String, Object>(
                        Map.of(
                                "nombre",
                                "Motor local " + sufijo,
                                "proveedor",
                                "LOCAL",
                                "tipoServicio",
                                "IA_TEXTO",
                                "urlServicio",
                                "",
                                "configuracion",
                                Map.of(),
                                "credencial",
                                credencialInicial,
                                "retirarCredencial",
                                false));
        var creado =
                respuesta(
                        escribir(administrador, post("/api/administracion/servicios"), datos)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                                .andExpect(jsonPath("$.tieneCredencial").value(true))
                                .andExpect(
                                        jsonPath("$.ultimaComprobacion.estado")
                                                .value("DISPONIBLE")));
        UUID servicio = UUID.fromString(creado.get("id").asText());
        assertThat(creado.toString()).doesNotContain(credencialInicial);
        byte[] contenidoInicial =
                jdbc.queryForObject(
                        "select contenido_cifrado from uml_colaborativo.secreto_servicio where"
                                + " id_servicio=? and retirado_en is null",
                        byte[].class,
                        servicio);
        assertThat(new String(contenidoInicial, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain(credencialInicial);
        assertThat(
                        jdbc.queryForObject(
                                "select algoritmo_cifrado from"
                                        + " uml_colaborativo.secreto_servicio where id_servicio=?"
                                        + " and retirado_en is null",
                                String.class,
                                servicio))
                .isEqualTo("AES-256-GCM");

        mvc.perform(
                        autorizado(
                                get("/api/administracion/servicios")
                                        .param("nombre", sufijo)
                                        .param("proveedor", "LOCAL")
                                        .param("tipo", "IA_TEXTO")
                                        .param("estado", "ACTIVO"),
                                administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.servicios[0].id").value(servicio.toString()));

        escribir(administrador, post("/api/administracion/servicios"), datos)
                .andExpect(status().isConflict());

        String credencialRotada = "credencial-rotada-" + sufijo;
        datos.put("nombre", "Motor local actualizado " + sufijo);
        datos.put("credencial", credencialRotada);
        datos.put("revision", creado.get("revision").asText());
        var actualizado =
                respuesta(
                        escribir(
                                        administrador,
                                        put("/api/administracion/servicios/" + servicio),
                                        datos)
                                .andExpect(status().isOk()));
        assertThat(actualizado.toString())
                .doesNotContain(credencialInicial)
                .doesNotContain(credencialRotada);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.secreto_servicio where"
                                        + " id_servicio=? and retirado_en is not null and"
                                        + " rotado_en is not null",
                                Long.class,
                                servicio))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.secreto_servicio where"
                                        + " id_servicio=? and retirado_en is null",
                                Long.class,
                                servicio))
                .isEqualTo(1);

        escribir(
                        administrador,
                        post("/api/administracion/servicios/" + servicio + "/estado"),
                        Map.of(
                                "estado",
                                "INACTIVO",
                                "revision",
                                actualizado.get("revision").asText(),
                                "confirmarImpacto",
                                false))
                .andExpect(status().isConflict());
        var inactivo =
                respuesta(
                        escribir(
                                        administrador,
                                        post(
                                                "/api/administracion/servicios/"
                                                        + servicio
                                                        + "/estado"),
                                        Map.of(
                                                "estado",
                                                "INACTIVO",
                                                "revision",
                                                actualizado.get("revision").asText(),
                                                "confirmarImpacto",
                                                true))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("INACTIVO")));
        var comprobado =
                respuesta(
                        escribir(
                                        administrador,
                                        post(
                                                "/api/administracion/servicios/"
                                                        + servicio
                                                        + "/verificar"),
                                        Map.of())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("INACTIVO")));
        escribir(
                        administrador,
                        post("/api/administracion/servicios/" + servicio + "/estado"),
                        Map.of(
                                "estado",
                                "ACTIVO",
                                "revision",
                                comprobado.get("revision").asText(),
                                "confirmarImpacto",
                                false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        datos.put("revision", creado.get("revision").asText());
        escribir(administrador, put("/api/administracion/servicios/" + servicio), datos)
                .andExpect(status().isConflict());
    }

    @Test
    void serviciosPlataformaExigenPermisoCsrfYConfiguracionNoSensible() throws Exception {
        var administrador = login(usuario, clave);
        String nombre = "servicio_seguro_" + UUID.randomUUID().toString().substring(0, 8);
        UUID usuarioComun =
                fixture("sin_servicios_" + UUID.randomUUID().toString().substring(0, 8));
        var comun =
                login(
                        jdbc.queryForObject(
                                "select nombre_usuario from uml_colaborativo.usuario where id=?",
                                String.class,
                                usuarioComun),
                        clave);
        mvc.perform(autorizado(get("/api/administracion/servicios"), comun))
                .andExpect(status().isForbidden());

        var sensible =
                Map.of(
                        "nombre",
                        nombre,
                        "proveedor",
                        "LOCAL",
                        "tipoServicio",
                        "IA_IMAGEN",
                        "urlServicio",
                        "",
                        "configuracion",
                        Map.of("apiKey", "no-debe-guardarse"),
                        "retirarCredencial",
                        false);
        escribir(administrador, post("/api/administracion/servicios"), sensible)
                .andExpect(status().isUnprocessableEntity());

        var privada =
                Map.of(
                        "nombre",
                        nombre,
                        "proveedor",
                        "OPENAI",
                        "tipoServicio",
                        "IA_TEXTO",
                        "urlServicio",
                        "https://127.0.0.1:8443/prueba",
                        "configuracion",
                        Map.of("modelo", "modelo-ficticio"),
                        "retirarCredencial",
                        false);
        escribir(administrador, post("/api/administracion/servicios"), privada)
                .andExpect(status().isUnprocessableEntity());

        var noDisponible =
                Map.of(
                        "nombre",
                        nombre + " externo",
                        "proveedor",
                        "OPENAI",
                        "tipoServicio",
                        "IA_TEXTO",
                        "urlServicio",
                        "https://proveedor-inexistente.invalid/prueba",
                        "configuracion",
                        Map.of("modelo", "modelo-ficticio"),
                        "retirarCredencial",
                        false);
        escribir(administrador, post("/api/administracion/servicios"), noDisponible)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("SIN_VERIFICAR"))
                .andExpect(jsonPath("$.ultimaComprobacion.estado").value("NO_DISPONIBLE"));

        mvc.perform(
                        post("/api/administracion/servicios")
                                .cookie(administrador.sesion())
                                .contentType("application/json")
                                .content(json.writeValueAsString(sensible)))
                .andExpect(status().isForbidden());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.servicio_plataforma where"
                                        + " nombre=?",
                                Long.class,
                                nombre))
                .isZero();
    }

    @Test
    void unServicioEnUsoNoPuedeDesactivarse() throws Exception {
        var administrador = login(usuario, clave);
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        var creado =
                respuesta(
                        escribir(
                                        administrador,
                                        post("/api/administracion/servicios"),
                                        Map.of(
                                                "nombre",
                                                "Servicio en uso " + sufijo,
                                                "proveedor",
                                                "LOCAL",
                                                "tipoServicio",
                                                "IA_TEXTO",
                                                "urlServicio",
                                                "",
                                                "configuracion",
                                                Map.of("perfilPrueba", sufijo),
                                                "retirarCredencial",
                                                false))
                                .andExpect(status().isCreated()));
        UUID servicio = UUID.fromString(creado.get("id").asText());
        String modelo = proyecto(administrador).get("idModelo").asText();
        UUID solicitud = UUID.randomUUID();
        jdbc.update(
                """
                insert into uml_colaborativo.solicitud_inteligente
                  (id,id_modelo,solicitado_por,id_servicio,tipo_entrada,texto_original,revision_base,estado)
                values(?,?,?,?,?,?,?,'PROCESANDO')
                """,
                solicitud,
                UUID.fromString(modelo),
                admin,
                servicio,
                "TEXTO",
                "Solicitud ficticia en curso",
                0L);
        escribir(
                        administrador,
                        post("/api/administracion/servicios/" + servicio + "/estado"),
                        Map.of(
                                "estado",
                                "INACTIVO",
                                "revision",
                                creado.get("revision").asText(),
                                "confirmarImpacto",
                                true))
                .andExpect(status().isConflict());
        jdbc.update(
                "update uml_colaborativo.solicitud_inteligente set estado='CANCELADO',"
                        + " completado_en=clock_timestamp() where id=?",
                solicitud);
        escribir(
                        administrador,
                        post("/api/administracion/servicios/" + servicio + "/estado"),
                        Map.of(
                                "estado",
                                "INACTIVO",
                                "revision",
                                creado.get("revision").asText(),
                                "confirmarImpacto",
                                true))
                .andExpect(status().isOk());
    }

    private Map<String, Object> datosMiembro(
            UUID usuario, UUID rol, String estado, String revision, List<?> permisos) {
        var d =
                new HashMap<String, Object>(
                        Map.of(
                                "usuario",
                                usuario,
                                "rol",
                                rol,
                                "estado",
                                estado,
                                "motivo",
                                "Motivo ficticio",
                                "permisos",
                                permisos));
        if (revision != null) d.put("revision", revision);
        return d;
    }

    @Test
    void miembrosDenegacionSuspensionConflictoYTransferencia() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String proyecto = p.get("id").asText(), modelo = p.get("idModelo").asText();
        String u = "miembro_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cuenta = fixture(u);
        var ruta = "/api/proyectos/" + proyecto + "/miembros";
        var incorporado =
                respuesta(
                        escribir(
                                        a,
                                        post(ruta),
                                        datosMiembro(
                                                cuenta,
                                                rol("EDITOR_PROYECTO"),
                                                "ACTIVO",
                                                null,
                                                List.of()))
                                .andExpect(status().isCreated()));
        String id = incorporado.get("id").asText();
        var cliente = login(u, clave);
        mvc.perform(autorizado(get("/api/modelos/" + modelo), cliente)).andExpect(status().isOk());
        escribir(
                        a,
                        post(ruta),
                        datosMiembro(cuenta, rol("EDITOR_PROYECTO"), "ACTIVO", null, List.of()))
                .andExpect(status().isConflict());
        var d =
                datosMiembro(
                        cuenta,
                        rol("EDITOR_PROYECTO"),
                        "ACTIVO",
                        incorporado.get("revision").asText(),
                        List.of(
                                Map.of(
                                        "permiso",
                                        permiso("EDITAR_MODELO"),
                                        "decision",
                                        "DENEGAR")));
        var actualizado =
                respuesta(escribir(a, put(ruta + "/" + id), d).andExpect(status().isOk()));
        enviar(cliente, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Prohibida")))
                .andExpect(status().isNotFound());
        escribir(a, put(ruta + "/" + id), d).andExpect(status().isConflict());
        d.put("estado", "SUSPENDIDO");
        d.put("revision", actualizado.get("revision").asText());
        var suspendido = respuesta(escribir(a, put(ruta + "/" + id), d).andExpect(status().isOk()));
        mvc.perform(autorizado(get("/api/modelos/" + modelo), cliente))
                .andExpect(status().isNotFound());
        d.put("estado", "ACTIVO");
        d.put("rol", rol("LECTOR_PROYECTO"));
        d.put("permisos", List.of());
        d.put("revision", suspendido.get("revision").asText());
        escribir(a, put(ruta + "/" + id), d).andExpect(status().isOk());
        escribir(a, post(ruta + "/propietario"), Map.of("miembro", id)).andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select id_usuario_propietario from uml_colaborativo.proyecto where"
                                        + " id=?",
                                UUID.class,
                                UUID.fromString(proyecto)))
                .isEqualTo(cuenta);
        var equipo = respuesta(mvc.perform(autorizado(get(ruta), a)).andExpect(status().isOk()));
        var nuevo =
                java.util.stream.StreamSupport.stream(equipo.get("miembros").spliterator(), false)
                        .filter(m -> m.get("id").asText().equals(id))
                        .findFirst()
                        .orElseThrow();
        escribir(
                        a,
                        put(ruta + "/" + id),
                        datosMiembro(
                                cuenta,
                                rol("LECTOR_PROYECTO"),
                                "RETIRADO",
                                nuevo.get("revision").asText(),
                                List.of()))
                .andExpect(status().isUnprocessableEntity());
        escribir(
                        a,
                        post(ruta),
                        datosMiembro(
                                fixture("otro_" + UUID.randomUUID().toString().substring(0, 8)),
                                rol("SUPERADMINISTRADOR"),
                                "ACTIVO",
                                null,
                                List.of()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void paquetesJerarquiaReubicacionYBorradoSeguro() throws Exception {
        var a = login(usuario, clave);
        String modelo = proyecto(a).get("idModelo").asText(),
                otro = proyecto(a).get("idModelo").asText();
        UUID padre = UUID.randomUUID(),
                hijo = UUID.randomUUID(),
                clase = UUID.randomUUID(),
                ajeno = UUID.randomUUID();
        var pkg =
                Map.of(
                        "operacion",
                        "CREAR",
                        "entidad",
                        "PAQUETE",
                        "idEntidad",
                        padre,
                        "datos",
                        Map.of("nombre", "dominio"));
        var c = new HashMap<String, Object>(clase(clase, "Cliente"));
        c.put("datos", Map.of("nombre", "Cliente", "x", 0, "y", 0, "idPaquete", padre));
        enviar(
                        a,
                        modelo,
                        cambio(
                                1,
                                UUID.randomUUID(),
                                pkg,
                                c,
                                Map.of(
                                        "operacion",
                                        "CREAR",
                                        "entidad",
                                        "PAQUETE",
                                        "idEntidad",
                                        hijo,
                                        "datos",
                                        Map.of("nombre", "ventas", "padre", padre))))
                .andExpect(status().isOk());
        enviar(
                        a,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ELIMINAR",
                                        "entidad",
                                        "PAQUETE",
                                        "idEntidad",
                                        padre)))
                .andExpect(status().isUnprocessableEntity());
        enviar(
                        a,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ACTUALIZAR",
                                        "entidad",
                                        "PAQUETE",
                                        "idEntidad",
                                        padre,
                                        "datos",
                                        Map.of("nombre", "dominio", "padre", hijo))))
                .andExpect(status().isUnprocessableEntity());
        enviar(
                        a,
                        otro,
                        cambio(
                                1,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "CREAR",
                                        "entidad",
                                        "PAQUETE",
                                        "idEntidad",
                                        ajeno,
                                        "datos",
                                        Map.of("nombre", "ajeno"))))
                .andExpect(status().isOk());
        enviar(
                        a,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ACTUALIZAR",
                                        "entidad",
                                        "PAQUETE",
                                        "idEntidad",
                                        hijo,
                                        "datos",
                                        Map.of("nombre", "ventas", "padre", ajeno))))
                .andExpect(status().isUnprocessableEntity());
        var editarClase =
                new HashMap<String, Object>(
                        Map.of(
                                "operacion",
                                "ACTUALIZAR",
                                "entidad",
                                "CLASE",
                                "idEntidad",
                                clase,
                                "datos",
                                Map.of("nombre", "ClienteEditado")));
        enviar(a, modelo, cambio(2, UUID.randomUUID(), editarClase)).andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select id_paquete from uml_colaborativo.clase_uml where id=?",
                                UUID.class,
                                clase))
                .isEqualTo(padre);
        var datos = new HashMap<String, Object>(Map.of("nombre", "ClienteEditado"));
        datos.put("idPaquete", null);
        editarClase.put("datos", datos);
        enviar(
                        a,
                        modelo,
                        cambio(
                                3,
                                UUID.randomUUID(),
                                editarClase,
                                Map.of(
                                        "operacion",
                                        "ELIMINAR",
                                        "entidad",
                                        "PAQUETE",
                                        "idEntidad",
                                        hijo),
                                Map.of(
                                        "operacion",
                                        "ELIMINAR",
                                        "entidad",
                                        "PAQUETE",
                                        "idEntidad",
                                        padre)))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select id_paquete is null from uml_colaborativo.clase_uml where"
                                        + " id=?",
                                Boolean.class,
                                clase))
                .isTrue();
    }

    private JsonNode validar(Cliente a, String modelo) throws Exception {
        var t =
                respuesta(
                        escribir(a, post("/api/modelos/" + modelo + "/validaciones"), Map.of())
                                .andExpect(status().isAccepted()));
        procesadorValidacion.procesar(UUID.fromString(t.get("id").asText()));
        return respuesta(
                mvc.perform(
                                autorizado(
                                        get(
                                                "/api/modelos/"
                                                        + modelo
                                                        + "/validaciones/"
                                                        + t.get("id").asText()),
                                        a))
                        .andExpect(status().isOk()));
    }

    @Test
    void validacionSemanticaPersistidaCorreccionYDesactualizacion() throws Exception {
        var a = login(usuario, clave);
        String modelo = proyecto(a).get("idModelo").asText();
        UUID clase = UUID.randomUUID(), atributo = UUID.randomUUID();
        assertThat(validar(a, modelo).get("estado").asText()).isEqualTo("INVALIDO");
        enviar(a, modelo, cambio(1, UUID.randomUUID(), clase(clase, "Cliente")))
                .andExpect(status().isOk());
        var sinId = validar(a, modelo);
        assertThat(sinId.toString()).contains("UML_IDENTIFICADOR_PERSISTENTE_OBLIGATORIO");
        enviar(
                        a,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "CREAR",
                                        "entidad",
                                        "ATRIBUTO",
                                        "idEntidad",
                                        atributo,
                                        "datos",
                                        Map.of(
                                                "idClase",
                                                clase,
                                                "nombre",
                                                "id",
                                                "tipoUml",
                                                "TipoInexistente",
                                                "esIdentificador",
                                                true))))
                .andExpect(status().isOk());
        assertThat(validar(a, modelo).toString()).contains("TIPO_NO_RESUELTO");
        enviar(
                        a,
                        modelo,
                        cambio(
                                3,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ACTUALIZAR",
                                        "entidad",
                                        "ATRIBUTO",
                                        "idEntidad",
                                        atributo,
                                        "datos",
                                        Map.of(
                                                "idClase",
                                                clase,
                                                "nombre",
                                                "id",
                                                "tipoUml",
                                                "UUID",
                                                "esIdentificador",
                                                true))))
                .andExpect(status().isOk());
        var correcto = validar(a, modelo);
        assertThat(correcto.get("estado").asText()).isEqualTo("VALIDO");
        assertThat(correcto.get("revision").asLong()).isEqualTo(4);
        var pendiente =
                respuesta(
                        escribir(a, post("/api/modelos/" + modelo + "/validaciones"), Map.of())
                                .andExpect(status().isAccepted()));
        enviar(
                        a,
                        modelo,
                        cambio(4, UUID.randomUUID(), clase(UUID.randomUUID(), "CambioConcurrente")))
                .andExpect(status().isOk());
        procesadorValidacion.procesar(UUID.fromString(pendiente.get("id").asText()));
        var viejo =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                get(
                                                        "/api/modelos/"
                                                                + modelo
                                                                + "/validaciones/"
                                                                + correcto.get("id").asText()),
                                                a))
                                .andExpect(status().isOk()));
        assertThat(viejo.get("estado").asText()).isEqualTo("DESACTUALIZADO");
        assertThat(
                        jdbc.queryForObject(
                                "select estado from uml_colaborativo.validacion_modelo where id=?",
                                String.class,
                                UUID.fromString(pendiente.get("id").asText())))
                .isEqualTo("DESACTUALIZADO");
        assertThat(
                        jdbc.queryForObject(
                                "select revision_actual from uml_colaborativo.modelo_uml where"
                                        + " id=?",
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(5);
    }

    @Test
    void reglaDesconocidaImpideDeclararValido() throws Exception {
        var a = login(usuario, clave);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.regla_validacion(id,codigo,nombre,descripcion,severidad,aplica_a,clave_implementacion)"
                    + " values(?,?,?,?,?,?,?)",
                id,
                "TEST_" + id,
                "Regla ficticia",
                "Solo prueba aislada",
                "ERROR",
                "MODELO_UML",
                "regla.no.implementada");
        try {
            assertThat(validar(a, proyecto(a).get("idModelo").asText()).get("estado").asText())
                    .isEqualTo("FALLIDO");
        } finally {
            jdbc.update(
                    "update uml_colaborativo.regla_validacion set esta_activo=false where id=?",
                    id);
        }
    }

    @Test
    void credencialTemporalNoPuedeActivarseCambiandoEstado() throws Exception {
        var a = login(usuario, clave);
        String u = "pendiente_" + UUID.randomUUID().toString().substring(0, 8);
        var d = datosUsuario(u, List.of());
        var credencial =
                respuesta(
                        escribir(a, post("/api/administracion/usuarios"), d)
                                .andExpect(status().isCreated()));
        String id = credencial.get("id").asText();
        d.put("revision", listaUsuarios(a, u).get(0).get("revision").asText());
        d.put("estado", "ACTIVO");
        escribir(a, put("/api/administracion/usuarios/" + id), d)
                .andExpect(status().isUnprocessableEntity());
        d.put("estado", "INACTIVO");
        escribir(a, put("/api/administracion/usuarios/" + id), d).andExpect(status().isOk());
        d.put("revision", listaUsuarios(a, u).get(0).get("revision").asText());
        d.put("estado", "ACTIVO");
        escribir(a, put("/api/administracion/usuarios/" + id), d)
                .andExpect(status().isUnprocessableEntity());
        d.put("estado", "PENDIENTE");
        escribir(a, put("/api/administracion/usuarios/" + id), d).andExpect(status().isOk());
        String nueva = UUID.randomUUID().toString();
        escribir(
                        csrf(null),
                        post("/api/acceso/activar"),
                        Map.of(
                                "identificador",
                                u,
                                "temporal",
                                credencial.get("credencialTemporal").asText(),
                                "nueva",
                                nueva))
                .andExpect(status().isNoContent());
        login(u, nueva);
    }

    @Test
    void administradorDelegadoNoEscalaPermisosNiModificaSuperadministrador() throws Exception {
        var a = login(usuario, clave);
        String sufijo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var d =
                new HashMap<String, Object>(
                        Map.of(
                                "codigo",
                                "DELEGADO_" + sufijo,
                                "nombre",
                                "Delegado " + sufijo,
                                "descripcion",
                                "Prueba de autorización",
                                "ambito",
                                "GLOBAL",
                                "estado",
                                "ACTIVO",
                                "permisos",
                                List.of(
                                        permiso("GESTIONAR_USUARIOS"),
                                        permiso("GESTIONAR_ROLES"))));
        var r =
                respuesta(
                        escribir(a, post("/api/administracion/roles"), d)
                                .andExpect(status().isCreated()));
        String u = "delegado_" + sufijo.toLowerCase();
        UUID id = fixture(u);
        var perfil = datosUsuario(u, List.of(UUID.fromString(r.get("id").asText())));
        perfil.put("estado", "ACTIVO");
        perfil.put("revision", listaUsuarios(a, u).get(0).get("revision").asText());
        escribir(a, put("/api/administracion/usuarios/" + id), perfil).andExpect(status().isOk());
        var delegado = login(u, clave);
        escribir(
                        delegado,
                        post("/api/administracion/usuarios"),
                        datosUsuario("escalado_" + sufijo, List.of(rol("SUPERADMINISTRADOR"))))
                .andExpect(status().isForbidden());
        var superPerfil = datosUsuario(usuario, List.of());
        superPerfil.put("estado", "ACTIVO");
        superPerfil.put("revision", listaUsuarios(a, usuario).get(0).get("revision").asText());
        escribir(delegado, put("/api/administracion/usuarios/" + admin), superPerfil)
                .andExpect(status().isForbidden());
        d.put("codigo", "ESCALADO_" + sufijo);
        d.put("nombre", "Escalado " + sufijo);
        d.put("permisos", List.of(permiso("CREAR_PROYECTOS")));
        escribir(delegado, post("/api/administracion/roles"), d).andExpect(status().isForbidden());
        mvc.perform(autorizado(get("/api/administracion/usuarios"), delegado))
                .andExpect(status().isOk());
    }

    private JsonNode resumen(Cliente c, String id) throws Exception {
        return respuesta(
                mvc.perform(get("/api/proyectos/" + id).cookie(c.sesion()))
                        .andExpect(status().isOk()));
    }

    private Map<String, Object> actualizacion(JsonNode p, String nombre, UUID solicitud) {
        return Map.of(
                "codigo",
                p.get("codigo").asText(),
                "nombre",
                nombre,
                "descripcion",
                "Descripción actualizada",
                "revision",
                p.get("revision").asText(),
                "idSolicitudCliente",
                solicitud);
    }

    private Map<String, Object> transicion(JsonNode p) {
        return Map.of(
                "revision",
                p.get("revision").asText(),
                "idSolicitudCliente",
                UUID.randomUUID(),
                "confirmar",
                true);
    }

    private UUID miembroFixture(UUID proyecto, UUID cuenta, String codigoRol) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.miembro_proyecto(id,id_proyecto,id_usuario)"
                        + " values(?,?,?)",
                id,
                proyecto,
                cuenta);
        jdbc.update(
                "insert into uml_colaborativo.rol_miembro_proyecto(id_miembro_proyecto,id_rol)"
                        + " values(?,?)",
                id,
                rol(codigoRol));
        return id;
    }

    private long actividad(String proyecto) {
        return jdbc.queryForObject(
                "select count(*) from uml_colaborativo.actividad_proyecto where id_proyecto=?",
                Long.class,
                UUID.fromString(proyecto));
    }

    @Test
    void proyectosFiltradosPorRolFechaYPaginaConAutorizacionRenovada() throws Exception {
        var a = login(usuario, clave);
        var rolesConsulta =
                respuesta(
                        mvc.perform(get("/api/proyectos/roles-consulta").cookie(a.sesion()))
                                .andExpect(status().isOk()));
        assertThat(rolesConsulta.isArray()).isTrue();
        for (var r : rolesConsulta)
            assertThat(r.get("codigo").isTextual())
                    .as("CITEXT debe exponerse como código de texto")
                    .isTrue();
        var p = proyecto(a);
        var otro = proyecto(a);
        String u = "consulta_" + UUID.randomUUID();
        UUID cuenta = fixture(u);
        var c = login(u, clave);
        UUID miembro =
                miembroFixture(UUID.fromString(p.get("id").asText()), cuenta, "LECTOR_PROYECTO");
        miembroFixture(UUID.fromString(otro.get("id").asText()), cuenta, "EDITOR_PROYECTO");
        jdbc.update(
                "update uml_colaborativo.proyecto set creado_en='2026-09-01T23:59:59Z' where id=?",
                UUID.fromString(p.get("id").asText()));
        jdbc.update(
                "update uml_colaborativo.proyecto set creado_en='2026-09-02T00:00:00Z' where id=?",
                UUID.fromString(otro.get("id").asText()));
        mvc.perform(
                        get("/api/proyectos")
                                .cookie(c.sesion())
                                .param("desde", "2026-09-01")
                                .param("hasta", "2026-09-01")
                                .param("rol", "LECTOR_PROYECTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.proyectos[0].id").value(p.get("id").asText()));
        var primera =
                respuesta(
                        mvc.perform(get("/api/proyectos").cookie(c.sesion()).param("tamano", "1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2)));
        var segunda =
                respuesta(
                        mvc.perform(
                                        get("/api/proyectos")
                                                .cookie(c.sesion())
                                                .param("tamano", "1")
                                                .param("pagina", "1"))
                                .andExpect(status().isOk()));
        assertThat(primera.get("proyectos").get(0).get("id"))
                .isNotEqualTo(segunda.get("proyectos").get(0).get("id"));
        mvc.perform(get("/api/proyectos/" + p.get("id").asText() + "/equipo").cookie(c.sesion()))
                .andExpect(status().isOk());
        jdbc.update(
                "update uml_colaborativo.miembro_proyecto set estado='RETIRADO' where id=?",
                miembro);
        mvc.perform(get("/api/proyectos/" + p.get("id").asText()).cookie(c.sesion()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/proyectos/" + p.get("id").asText() + "/equipo").cookie(c.sesion()))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultaRechazaFechasYPaginacionInvalidas() throws Exception {
        var a = login(usuario, clave);
        String id = proyecto(a).get("id").asText();
        for (String ruta : List.of("/api/proyectos", "/api/proyectos/" + id + "/actividad")) {
            mvc.perform(
                            get(ruta)
                                    .cookie(a.sesion())
                                    .param("desde", "2026-09-15")
                                    .param("hasta", "2026-09-01"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(ruta).cookie(a.sesion()).param("tamano", "101"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(ruta).cookie(a.sesion()).param("pagina", "-1"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(ruta).cookie(a.sesion()).param("desde", "no-fecha"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void actualizacionEsIdempotenteYDetectaRevisionObsoleta() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String id = p.get("id").asText();
        long antes = actividad(id);
        UUID uuid = UUID.randomUUID();
        var datos = actualizacion(p, "Nombre actualizado", uuid);
        var nuevo =
                respuesta(
                        escribir(a, put("/api/proyectos/" + id), datos)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.nombre").value("Nombre actualizado")));
        assertThat(nuevo.get("revision").asText()).isNotEqualTo(p.get("revision").asText());
        escribir(a, put("/api/proyectos/" + id), datos).andExpect(status().isOk());
        assertThat(actividad(id)).isEqualTo(antes + 1);
        escribir(a, put("/api/proyectos/" + id), actualizacion(p, "Obsoleta", UUID.randomUUID()))
                .andExpect(status().isConflict());
        escribir(a, put("/api/proyectos/" + id), actualizacion(p, "UUID reutilizado", uuid))
                .andExpect(status().isConflict());
        assertThat(resumen(a, id).get("nombre").asText()).isEqualTo("Nombre actualizado");
        assertThat(actividad(id)).isEqualTo(antes + 1);
    }

    @Test
    void gestionDenegadaNoProduceCambiosNiActividad() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String id = p.get("id").asText();
        String u = "gestion_" + UUID.randomUUID();
        UUID cuenta = fixture(u);
        var c = login(u, clave);
        String codigo = "GESTION_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        escribir(
                        a,
                        post("/api/administracion/roles"),
                        Map.of(
                                "codigo",
                                codigo,
                                "nombre",
                                codigo,
                                "ambito",
                                "PROYECTO",
                                "estado",
                                "ACTIVO",
                                "permisos",
                                List.of(
                                        permiso("VER_PROYECTOS"),
                                        permiso("GESTIONAR_PROYECTOS"),
                                        permiso("VER_ACTIVIDAD"))))
                .andExpect(status().isCreated());
        UUID miembro = miembroFixture(UUID.fromString(id), cuenta, codigo);
        assertThat(resumen(c, id).get("puedeGestionarProyecto").asBoolean()).isTrue();
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.permiso_miembro_proyecto(id_miembro_proyecto,id_permiso,decision)"
                    + " values(?,?,'DENEGAR')",
                miembro,
                permiso("GESTIONAR_PROYECTOS"));
        long antes = actividad(id);
        assertThat(resumen(c, id).get("puedeGestionarProyecto").asBoolean()).isFalse();
        escribir(c, put("/api/proyectos/" + id), actualizacion(p, "Denegado", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        escribir(c, post("/api/proyectos/" + id + "/archivar"), transicion(p))
                .andExpect(status().isForbidden());
        assertThat(actividad(id)).isEqualTo(antes);
        escribir(
                        a,
                        put("/api/proyectos/" + id),
                        Map.of(
                                "codigo",
                                p.get("codigo").asText(),
                                "nombre",
                                "",
                                "revision",
                                p.get("revision").asText(),
                                "idSolicitudCliente",
                                UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/proyectos/" + id + "/archivar")
                                .cookie(c.sesion())
                                .contentType("application/json")
                                .content(json.writeValueAsString(transicion(p))))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivoConSesionesActivasYReactivacionRespetanModelo() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String id = p.get("id").asText(), modelo = p.get("idModelo").asText();
        UUID sesion = UUID.randomUUID();
        long antes = actividad(id);
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.sesion_edicion_modelo(id,id_modelo,abierto_por,modo,revision_base)"
                    + " values(?,?,?,'EDICION',1)",
                sesion,
                UUID.fromString(modelo),
                admin);
        escribir(a, post("/api/proyectos/" + id + "/archivar"), transicion(p))
                .andExpect(status().isConflict());
        assertThat(actividad(id)).isEqualTo(antes);
        assertThat(
                        jdbc.queryForObject(
                                "select estado from uml_colaborativo.sesion_edicion_modelo where"
                                        + " id=?",
                                String.class,
                                sesion))
                .isEqualTo("ACTIVO");
        jdbc.update(
                "update uml_colaborativo.sesion_edicion_modelo set"
                        + " estado='CERRADO',cerrado_en=clock_timestamp() where id=?",
                sesion);
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.sesion_edicion_modelo(id_modelo,abierto_por,modo,revision_base)"
                    + " values(?,?,'SOLO_LECTURA',1)",
                UUID.fromString(modelo),
                admin);
        var datos = transicion(p);
        var archivado =
                respuesta(
                        escribir(a, post("/api/proyectos/" + id + "/archivar"), datos)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("ARCHIVADO"))
                                .andExpect(jsonPath("$.puedeEditar").value(false)));
        escribir(a, post("/api/proyectos/" + id + "/archivar"), datos).andExpect(status().isOk());
        assertThat(actividad(id)).isEqualTo(antes + 1);
        mvc.perform(get("/api/modelos/" + modelo).cookie(a.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeEditar").value(false));
        enviar(a, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Imposible")))
                .andExpect(status().isConflict());
        escribir(
                        a,
                        put("/api/proyectos/" + id),
                        actualizacion(archivado, "Archivo modificado", UUID.randomUUID()))
                .andExpect(status().isConflict());
        var reactivado =
                respuesta(
                        escribir(
                                        a,
                                        post("/api/proyectos/" + id + "/reactivar"),
                                        transicion(archivado))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("ACTIVO")));
        assertThat(reactivado.get("archivadoEn").isNull()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.actividad_proyecto where"
                                        + " id_proyecto=? and categoria='PROYECTO' and accion in"
                                        + " ('ARCHIVAR','REACTIVAR')",
                                Long.class,
                                UUID.fromString(id)))
                .isEqualTo(2);
        enviar(a, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Permitida")))
                .andExpect(status().isOk());
    }

    @Autowired javax.sql.DataSource datasource;

    @Test
    void archivoNoInterfiereConTransaccionDeEdicionEnCurso() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String id = p.get("id").asText();
        long antes = actividad(id);
        try (var conexion = datasource.getConnection()) {
            conexion.setAutoCommit(false);
            try (var s =
                    conexion.prepareStatement(
                            "select uml_colaborativo.iniciar_edicion_modelo(?,1,?)")) {
                s.setObject(1, UUID.fromString(p.get("idModelo").asText()));
                s.setObject(2, admin);
                s.execute();
            }
            escribir(a, post("/api/proyectos/" + id + "/archivar"), transicion(p))
                    .andExpect(status().isConflict());
            conexion.rollback();
        }
        assertThat(actividad(id)).isEqualTo(antes);
        assertThat(resumen(a, id).get("estado").asText()).isEqualTo("ACTIVO");
    }

    @Test
    void actualizacionConcurrenteSoloConfirmaUnaSolicitud() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String id = p.get("id").asText();
        long antes = actividad(id);
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tareas = new ArrayList<Future<Integer>>();
            for (String nombre : List.of("Concurrente A", "Concurrente B"))
                tareas.add(
                        pool.submit(
                                () -> {
                                    inicio.await();
                                    return escribir(
                                                    a,
                                                    put("/api/proyectos/" + id),
                                                    actualizacion(p, nombre, UUID.randomUUID()))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            inicio.countDown();
            assertThat(
                            List.of(
                                    tareas.get(0).get(10, TimeUnit.SECONDS),
                                    tareas.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(actividad(id)).isEqualTo(antes + 1);
    }

    @Test
    void falloDeUnicidadRevierteDatosRevisionYActividad() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        var otro = proyecto(a);
        String id = p.get("id").asText();
        long antes = actividad(id);
        var datos = new HashMap<>(actualizacion(p, "No confirmado", UUID.randomUUID()));
        datos.put("codigo", otro.get("codigo").asText());
        escribir(a, put("/api/proyectos/" + id), datos).andExpect(status().isConflict());
        assertThat(resumen(a, id).get("revision")).isEqualTo(p.get("revision"));
        assertThat(resumen(a, id).get("nombre")).isEqualTo(p.get("nombre"));
        assertThat(actividad(id)).isEqualTo(antes);
    }

    @Test
    void actividadPaginadaFiltradaYDetalleRespetanProyecto() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String id = p.get("id").asText();
        var primera =
                respuesta(
                        mvc.perform(
                                        get("/api/proyectos/" + id + "/actividad")
                                                .cookie(a.sesion())
                                                .param("tamano", "1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(2)));
        var segunda =
                respuesta(
                        mvc.perform(
                                        get("/api/proyectos/" + id + "/actividad")
                                                .cookie(a.sesion())
                                                .param("tamano", "1")
                                                .param("pagina", "1"))
                                .andExpect(status().isOk()));
        assertThat(primera.get("eventos").get(0).get("id").isTextual()).isTrue();
        assertThat(primera.get("eventos").get(0).get("id"))
                .isNotEqualTo(segunda.get("eventos").get(0).get("id"));
        var actual =
                respuesta(
                        escribir(
                                        a,
                                        put("/api/proyectos/" + id),
                                        actualizacion(p, "Auditado", UUID.randomUUID()))
                                .andExpect(status().isOk()));
        String hoy = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        var filtro =
                respuesta(
                        mvc.perform(
                                        get("/api/proyectos/" + id + "/actividad")
                                                .cookie(a.sesion())
                                                .param("accion", "ACTUALIZAR")
                                                .param("responsable", admin.toString())
                                                .param("tipo", "PROYECTO")
                                                .param("entidad", id)
                                                .param("desde", hoy)
                                                .param("hasta", hoy))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1)));
        String evento = filtro.get("eventos").get(0).get("id").asText();
        mvc.perform(get("/api/proyectos/" + id + "/actividad/" + evento).cookie(a.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAnterior.nombre").value(p.get("nombre").asText()))
                .andExpect(
                        jsonPath("$.valorPosterior.nombre").value(actual.get("nombre").asText()));
        mvc.perform(
                        get("/api/proyectos/"
                                        + proyecto(a).get("id").asText()
                                        + "/actividad/"
                                        + evento)
                                .cookie(a.sesion()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/proyectos/" + id + "/actividad")
                                .cookie(a.sesion())
                                .param("accion", "INEXISTENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos").isEmpty());
        String u = "actividad_" + UUID.randomUUID();
        UUID cuenta = fixture(u);
        var c = login(u, clave);
        mvc.perform(get("/api/proyectos/" + id + "/actividad").cookie(c.sesion()))
                .andExpect(status().isNotFound());
        UUID m = miembroFixture(UUID.fromString(id), cuenta, "LECTOR_PROYECTO");
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.permiso_miembro_proyecto(id_miembro_proyecto,id_permiso,decision)"
                    + " values(?,?,'DENEGAR')",
                m,
                permiso("VER_ACTIVIDAD"));
        for (String sufijo : List.of("", "/filtros", "/" + evento))
            mvc.perform(get("/api/proyectos/" + id + "/actividad" + sufijo).cookie(c.sesion()))
                    .andExpect(status().isForbidden());
    }

    @Test
    void actividadUmlTieneValoresVersionYNoSeDuplicaEnReenvios() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        String id = p.get("id").asText(), modelo = p.get("idModelo").asText();
        UUID clase = UUID.randomUUID();
        String peticion = cambio(1, UUID.randomUUID(), clase(clase, "Antes"));
        var confirmado = respuesta(enviar(a, modelo, peticion).andExpect(status().isOk()));
        long cantidad = actividad(id);
        enviar(a, modelo, peticion).andExpect(status().isOk());
        assertThat(actividad(id)).isEqualTo(cantidad);
        var f =
                respuesta(
                        mvc.perform(
                                        get("/api/proyectos/" + id + "/actividad")
                                                .cookie(a.sesion())
                                                .param("entidad", clase.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1)));
        String evento = f.get("eventos").get(0).get("id").asText();
        mvc.perform(get("/api/proyectos/" + id + "/actividad/" + evento).cookie(a.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAnterior").doesNotExist())
                .andExpect(jsonPath("$.valorPosterior.nombre").value("Antes"))
                .andExpect(
                        jsonPath("$.evento.idVersionModelo")
                                .value(confirmado.get("idVersion").asText()))
                .andExpect(jsonPath("$.evento.revisionModelo").value(2));
        enviar(
                        a,
                        modelo,
                        cambio(
                                2,
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ACTUALIZAR",
                                        "entidad",
                                        "CLASE",
                                        "idEntidad",
                                        clase,
                                        "datos",
                                        Map.of("nombre", "Después"))))
                .andExpect(status().isOk());
        var cambios =
                respuesta(
                        mvc.perform(
                                        get("/api/proyectos/" + id + "/actividad")
                                                .cookie(a.sesion())
                                                .param("entidad", clase.toString())
                                                .param("accion", "ACTUALIZAR"))
                                .andExpect(status().isOk()));
        mvc.perform(
                        get("/api/proyectos/"
                                        + id
                                        + "/actividad/"
                                        + cambios.get("eventos").get(0).get("id").asText())
                                .cookie(a.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAnterior.nombre").value("Antes"))
                .andExpect(jsonPath("$.valorPosterior.nombre").value("Después"));
        long antes = actividad(id);
        enviar(
                        a,
                        modelo,
                        cambio(
                                3,
                                UUID.randomUUID(),
                                clase(UUID.randomUUID(), "Rollback"),
                                clase(UUID.randomUUID(), "Después")))
                .andExpect(status().isConflict());
        assertThat(actividad(id)).isEqualTo(antes);
        assertThat(
                        jdbc.queryForObject(
                                "select revision_actual from uml_colaborativo.modelo_uml where"
                                        + " id=?",
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(3);
    }

    @Test
    void actividadOrdenaEmpatesPorIdYFiltraFechasUtc() throws Exception {
        var a = login(usuario, clave);
        String id = proyecto(a).get("id").asText();
        var esperados = new ArrayList<String>();
        for (int i = 0; i < 3; i++)
            esperados.add(
                    jdbc.queryForObject(
                            "insert into"
                                + " uml_colaborativo.actividad_proyecto(id_proyecto,id_usuario_actor,categoria,accion,ocurrido_en)"
                                + " values(?,?,'PRUEBA','PAGINA','2026-09-01T23:59:59Z') returning"
                                + " id::text",
                            String.class,
                            UUID.fromString(id),
                            admin));
        Collections.reverse(esperados);
        var reales = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            var p =
                    respuesta(
                            mvc.perform(
                                            get("/api/proyectos/" + id + "/actividad")
                                                    .cookie(a.sesion())
                                                    .param("accion", "PAGINA")
                                                    .param("desde", "2026-09-01")
                                                    .param("hasta", "2026-09-01")
                                                    .param("tamano", "1")
                                                    .param("pagina", String.valueOf(i)))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.total").value(3)));
            reales.add(p.get("eventos").get(0).get("id").asText());
        }
        assertThat(reales).containsExactlyElementsOf(esperados);
        mvc.perform(
                        get("/api/proyectos/" + id + "/actividad")
                                .cookie(a.sesion())
                                .param("accion", "PAGINA")
                                .param("desde", "2026-09-02")
                                .param("hasta", "2026-09-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(
                        get("/api/proyectos/" + id + "/actividad/" + esperados.getFirst())
                                .cookie(a.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAnterior").doesNotExist())
                .andExpect(jsonPath("$.valorPosterior").doesNotExist());
    }

    @Test
    void parametrosAjenosNoSeReasignan() throws Exception {
        var c = login(usuario, clave);
        String primero = proyecto(c).get("idModelo").asText(),
                segundo = proyecto(c).get("idModelo").asText();
        UUID clase1 = UUID.randomUUID(),
                clase2 = UUID.randomUUID(),
                op1 = UUID.randomUUID(),
                op2 = UUID.randomUUID(),
                parametro = UUID.randomUUID();
        enviar(
                        c,
                        primero,
                        cambio(
                                1,
                                UUID.randomUUID(),
                                clase(clase1, "Uno"),
                                operacion(
                                        "CREAR",
                                        op1,
                                        clase1,
                                        "buscar",
                                        parametro(parametro, "texto", "String"))))
                .andExpect(status().isOk());
        enviar(
                        c,
                        segundo,
                        cambio(
                                1,
                                UUID.randomUUID(),
                                clase(clase2, "Dos"),
                                operacion(
                                        "CREAR",
                                        op2,
                                        clase2,
                                        "buscar",
                                        parametro(parametro, "robado", "String"))))
                .andExpect(status().isNotFound());
        assertThat(
                        jdbc.queryForObject(
                                "select nombre from uml_colaborativo.parametro_uml where id=?",
                                String.class,
                                parametro))
                .isEqualTo("texto");
        assertThat(
                        jdbc.queryForObject(
                                "select revision_actual from uml_colaborativo.modelo_uml where"
                                        + " id=?",
                                Long.class,
                                UUID.fromString(segundo)))
                .isEqualTo(1);
    }

    @Test
    void sesionReutilizadaExigeConexionYVinculaCambioIdempotente() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        UUID solicitud = UUID.randomUUID();
        var primera =
                respuesta(
                        escribir(
                                        c,
                                        post("/api/modelos/" + modelo + "/sesiones"),
                                        Map.of("idSolicitudCliente", solicitud))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.modo").value("SOLO_LECTURA"))
                                .andExpect(jsonPath("$.modoPropio").value("EDITOR"))
                                .andExpect(jsonPath("$.participantes[0].estado").value("AUSENTE")));
        UUID sesion = UUID.fromString(primera.get("id").asText());
        assertThat(sesion).isEqualTo(solicitud);
        var repetida =
                respuesta(
                        escribir(
                                        c,
                                        post("/api/modelos/" + modelo + "/sesiones"),
                                        Map.of("idSolicitudCliente", solicitud))
                                .andExpect(status().isCreated()));
        assertThat(repetida.get("id").asText()).isEqualTo(sesion.toString());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.actividad_proyecto where"
                                        + " id_proyecto=? and categoria='COLABORACION' and"
                                        + " accion='ABRIR_SESION'",
                                Long.class,
                                UUID.fromString(p.get("id").asText())))
                .isEqualTo(1);

        var identidad = acceso.identidad(admin);
        var conectada = colaboracion.conectar(identidad, UUID.fromString(modelo), sesion);
        assertThat(conectada.modo()).isEqualTo("EDICION");
        assertThat(conectada.participantes().getFirst().estado()).isEqualTo("EN_LINEA");

        UUID idCambio = UUID.randomUUID(), clase = UUID.randomUUID();
        var cuerpo =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.readTree(cambio(1, idCambio, clase(clase, "Colaborativa")));
        cuerpo.put("idSesionEdicion", sesion.toString());
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/cambios"), c)
                                .contentType("application/json")
                                .content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(false));
        assertThat(
                        jdbc.queryForObject(
                                "select id_sesion_edicion from uml_colaborativo.cambio_modelo where"
                                        + " id_modelo=? and id_cambio_cliente=?",
                                UUID.class,
                                UUID.fromString(modelo),
                                idCambio))
                .isEqualTo(sesion);

        escribir(c, delete("/api/modelos/" + modelo + "/sesiones/" + sesion), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/cambios"), c)
                                .contentType("application/json")
                                .content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(true));
        cuerpo.put("idCambioCliente", UUID.randomUUID().toString());
        cuerpo.put("revisionBase", 2);
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/cambios"), c)
                                .contentType("application/json")
                                .content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isConflict());
    }

    @Test
    void aperturaConcurrenteProduceUnaSalaYUnParticipante() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tareas = new ArrayList<Future<String>>();
            for (int i = 0; i < 2; i++)
                tareas.add(
                        pool.submit(
                                () -> {
                                    inicio.await();
                                    return respuesta(
                                                    escribir(
                                                                    c,
                                                                    post(
                                                                            "/api/modelos/"
                                                                                    + modelo
                                                                                    + "/sesiones"),
                                                                    Map.of(
                                                                            "idSolicitudCliente",
                                                                            UUID.randomUUID()))
                                                            .andExpect(status().isCreated()))
                                            .get("id")
                                            .asText();
                                }));
            inicio.countDown();
            assertThat(
                            new HashSet<>(
                                    List.of(
                                            tareas.get(0).get(10, TimeUnit.SECONDS),
                                            tareas.get(1).get(10, TimeUnit.SECONDS))))
                    .hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.sesion_edicion_modelo where"
                                        + " id_modelo=? and estado='ACTIVO'",
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from"
                                        + " uml_colaborativo.participante_sesion_modelo ps join"
                                        + " uml_colaborativo.sesion_edicion_modelo s on"
                                        + " s.id=ps.id_sesion_edicion where s.id_modelo=?",
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(1);
    }

    @Test
    void lectorParticipaSinPoderEditarYFocoAjenoRevierte() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        UUID proyecto = UUID.fromString(p.get("id").asText());
        UUID modelo = UUID.fromString(p.get("idModelo").asText());
        String nombre = "lector_sesion_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cuenta = fixture(nombre);
        miembroFixture(proyecto, cuenta, "LECTOR_PROYECTO");
        var lector = login(nombre, clave);
        var abierta =
                respuesta(
                        escribir(
                                        lector,
                                        post("/api/modelos/" + modelo + "/sesiones"),
                                        Map.of("idSolicitudCliente", UUID.randomUUID()))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.modoPropio").value("LECTOR")));
        UUID sesion = UUID.fromString(abierta.get("id").asText());
        var conectada = colaboracion.conectar(acceso.identidad(cuenta), modelo, sesion);
        assertThat(conectada.modo()).isEqualTo("SOLO_LECTURA");
        var cuerpo =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.readTree(
                                cambio(
                                        1,
                                        UUID.randomUUID(),
                                        clase(UUID.randomUUID(), "No permitida")));
        cuerpo.put("idSesionEdicion", sesion.toString());
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/cambios"), lector)
                                .contentType("application/json")
                                .content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isNotFound());
        assertThatThrownBy(
                        () ->
                                colaboracion.enfocar(
                                        acceso.identidad(cuenta),
                                        modelo,
                                        sesion,
                                        new bo.edu.uml.colaboracion.dto.ColaboracionDto.Foco(
                                                "CLASE", UUID.randomUUID())))
                .isInstanceOf(ApiException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select id_entidad_enfocada is null from"
                                        + " uml_colaborativo.participante_sesion_modelo where"
                                        + " id_sesion_edicion=? and id_usuario=?",
                                Boolean.class,
                                sesion,
                                cuenta))
                .isTrue();
    }

    @Test
    void perdidaDeEdicionDegradaParticipanteSinExpulsarAlLector() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        UUID proyecto = UUID.fromString(p.get("id").asText());
        UUID modelo = UUID.fromString(p.get("idModelo").asText());
        String nombre = "editor_sesion_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cuenta = fixture(nombre);
        var incorporado =
                respuesta(
                        escribir(
                                        a,
                                        post("/api/proyectos/" + proyecto + "/miembros"),
                                        datosMiembro(
                                                cuenta,
                                                rol("EDITOR_PROYECTO"),
                                                "ACTIVO",
                                                null,
                                                List.of()))
                                .andExpect(status().isCreated()));
        var cliente = login(nombre, clave);
        var abierta =
                respuesta(
                        escribir(
                                        cliente,
                                        post("/api/modelos/" + modelo + "/sesiones"),
                                        Map.of("idSolicitudCliente", UUID.randomUUID()))
                                .andExpect(status().isCreated()));
        UUID sesion = UUID.fromString(abierta.get("id").asText());
        colaboracion.conectar(acceso.identidad(cuenta), modelo, sesion);
        escribir(
                        a,
                        put(
                                "/api/proyectos/"
                                        + proyecto
                                        + "/miembros/"
                                        + incorporado.get("id").asText()),
                        datosMiembro(
                                cuenta,
                                rol("LECTOR_PROYECTO"),
                                "ACTIVO",
                                incorporado.get("revision").asText(),
                                List.of()))
                .andExpect(status().isOk());
        var estado =
                respuesta(
                        mvc.perform(
                                        get("/api/modelos/" + modelo + "/sesiones/" + sesion)
                                                .cookie(cliente.sesion()))
                                .andExpect(status().isOk()));
        assertThat(estado.get("modo").asText()).isEqualTo("SOLO_LECTURA");
        assertThat(estado.get("modoPropio").asText()).isEqualTo("LECTOR");
        assertThat(estado.get("participantes").get(0).get("estado").asText()).isEqualTo("EN_LINEA");
    }

    @Test
    void sesionRealBloqueaArchivoHastaSalidaExplicita() throws Exception {
        var a = login(usuario, clave);
        var p = proyecto(a);
        UUID modelo = UUID.fromString(p.get("idModelo").asText());
        var abierta =
                respuesta(
                        escribir(
                                        a,
                                        post("/api/modelos/" + modelo + "/sesiones"),
                                        Map.of("idSolicitudCliente", UUID.randomUUID()))
                                .andExpect(status().isCreated()));
        UUID sesion = UUID.fromString(abierta.get("id").asText());
        colaboracion.conectar(acceso.identidad(admin), modelo, sesion);
        escribir(a, post("/api/proyectos/" + p.get("id").asText() + "/archivar"), transicion(p))
                .andExpect(status().isConflict());
        escribir(a, delete("/api/modelos/" + modelo + "/sesiones/" + sesion), Map.of())
                .andExpect(status().isOk());
        escribir(a, post("/api/proyectos/" + p.get("id").asText() + "/archivar"), transicion(p))
                .andExpect(status().isOk());
    }

    private record EscenarioConflicto(
            Cliente propietario,
            Cliente editor,
            UUID usuarioEditor,
            UUID miembroEditor,
            UUID proyecto,
            UUID modelo,
            UUID sesionEditor,
            UUID clase,
            UUID conflicto,
            long revision) {}

    private Map<String, Object> datosClase(String nombre, String descripcion) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", nombre);
        if (descripcion != null) datos.put("descripcion", descripcion);
        datos.put("visibilidad", "PUBLICO");
        datos.put("esAbstracto", false);
        datos.put("esPersistente", false);
        return datos;
    }

    private Map<String, Object> actualizarClase(UUID clase, String nombre, String descripcion) {
        return Map.of(
                "operacion",
                "ACTUALIZAR",
                "entidad",
                "CLASE",
                "idEntidad",
                clase,
                "datos",
                datosClase(nombre, descripcion));
    }

    private EscenarioConflicto conflictoClase(
            String nombreConfirmado,
            String descripcionConfirmada,
            String nombrePropuesto,
            String descripcionPropuesta)
            throws Exception {
        Cliente propietario = login(usuario, clave);
        JsonNode proyecto = proyecto(propietario);
        UUID idProyecto = UUID.fromString(proyecto.get("id").asText());
        UUID modelo = UUID.fromString(proyecto.get("idModelo").asText());
        UUID clase = UUID.randomUUID();
        UUID sesionPropietario = asegurarSesion(propietario, modelo.toString());
        var crear = new HashMap<>(clase(clase, "BaseConflicto"));
        var datosCreacion = new HashMap<>(datosClase("BaseConflicto", null));
        datosCreacion.put("x", 0);
        datosCreacion.put("y", 0);
        crear.put("datos", datosCreacion);
        enviar(propietario, modelo.toString(), cambio(1, UUID.randomUUID(), crear))
                .andExpect(status().isOk());

        String nombre = "conflicto_" + UUID.randomUUID().toString().substring(0, 8);
        UUID usuarioEditor = fixture(nombre);
        UUID miembroEditor = miembroFixture(idProyecto, usuarioEditor, "EDITOR_PROYECTO");
        Cliente editor = login(nombre, clave);
        UUID sesionEditor = asegurarSesion(editor, modelo.toString());
        assertThat(sesionEditor).isEqualTo(sesionPropietario);
        long base =
                respuesta(
                                mvc.perform(get("/api/modelos/" + modelo).cookie(editor.sesion()))
                                        .andExpect(status().isOk()))
                        .get("revision")
                        .asLong();
        enviar(
                        propietario,
                        modelo.toString(),
                        cambio(
                                base,
                                UUID.randomUUID(),
                                actualizarClase(clase, nombreConfirmado, descripcionConfirmada)))
                .andExpect(status().isOk());
        enviar(
                        editor,
                        modelo.toString(),
                        cambio(
                                base,
                                UUID.randomUUID(),
                                actualizarClase(clase, nombrePropuesto, descripcionPropuesta)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.mensaje")
                                .value(org.hamcrest.Matchers.containsString("se conservó")));
        JsonNode lista =
                respuesta(
                        mvc.perform(
                                        get("/api/modelos/" + modelo + "/conflictos")
                                                .cookie(editor.sesion()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(1)));
        JsonNode conflicto = lista.get("conflictos").get(0);
        return new EscenarioConflicto(
                propietario,
                editor,
                usuarioEditor,
                miembroEditor,
                idProyecto,
                modelo,
                sesionEditor,
                clase,
                UUID.fromString(conflicto.get("id").asText()),
                conflicto.get("revisionActual").asLong());
    }

    private Map<String, Object> resolver(
            EscenarioConflicto e, UUID solicitud, long revision, String tipo) {
        return Map.of(
                "idSesionEdicion", e.sesionEditor(),
                "idSolicitudResolucion", solicitud,
                "revisionEsperada", revision,
                "tipoResolucion", tipo,
                "confirmar", true);
    }

    @Test
    void conflictoMismaPropiedadExponeAutoresValoresYPermiteConservar() throws Exception {
        EscenarioConflicto e = conflictoClase("Confirmada", null, "Propuesta", null);
        JsonNode detalle =
                respuesta(
                        mvc.perform(
                                        get("/api/modelos/"
                                                        + e.modelo()
                                                        + "/conflictos/"
                                                        + e.conflicto())
                                                .cookie(e.editor().sesion()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.diferencias.length()").value(1))
                                .andExpect(
                                        jsonPath("$.diferencias[?(@.propiedad == 'nombre')]")
                                                .exists())
                                .andExpect(
                                        jsonPath("$.diferencias[?(@.incompatible == true)]")
                                                .exists())
                                .andExpect(jsonPath("$.cambioEntrante.autor.nombre").exists())
                                .andExpect(jsonPath("$.cambioEntrante.creadoEn").exists()));
        assertThat(detalle.get("fusionable").asBoolean()).isFalse();
        clearInvocations(canal);
        UUID solicitud = UUID.randomUUID();
        JsonNode resultado =
                respuesta(
                        escribir(
                                        e.editor(),
                                        post(
                                                "/api/modelos/"
                                                        + e.modelo()
                                                        + "/conflictos/"
                                                        + e.conflicto()
                                                        + "/resoluciones"),
                                        resolver(
                                                e, solicitud, e.revision(), "CONSERVAR_ALMACENADO"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.conflicto.estado").value("RESUELTO"))
                                .andExpect(jsonPath("$.revision").doesNotExist()));
        assertThat(resultado.get("repetido").asBoolean()).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "select nombre from uml_colaborativo.clase_uml where id=?",
                                String.class,
                                e.clase()))
                .isEqualTo("Confirmada");
        verify(canal, atLeastOnce())
                .publicarConflicto(
                        argThat(
                                c ->
                                        c.conflicto().equals(e.conflicto())
                                                && c.estado().equals("RESUELTO")));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.actividad_proyecto where"
                                        + " id_proyecto=? and accion='RESOLVER_CONFLICTO'",
                                Long.class,
                                e.proyecto()))
                .isEqualTo(1);
    }

    @Test
    void propiedadesCompatiblesSeFusionanEIdempotenciaNoCreaOtraVersion() throws Exception {
        EscenarioConflicto e = conflictoClase("Confirmada", null, "BaseConflicto", "Entrante");
        mvc.perform(get("/api/modelos/" + e.modelo() + "/conflictos").cookie(e.editor().sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictos[0].fusionable").value(true));
        UUID solicitud = UUID.randomUUID();
        Map<String, Object> cuerpo = resolver(e, solicitud, e.revision(), "FUSIONAR");
        JsonNode primera =
                respuesta(
                        escribir(
                                        e.editor(),
                                        post(
                                                "/api/modelos/"
                                                        + e.modelo()
                                                        + "/conflictos/"
                                                        + e.conflicto()
                                                        + "/resoluciones"),
                                        cuerpo)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.revision").value(e.revision() + 1)));
        JsonNode repetida =
                respuesta(
                        escribir(
                                        e.editor(),
                                        post(
                                                "/api/modelos/"
                                                        + e.modelo()
                                                        + "/conflictos/"
                                                        + e.conflicto()
                                                        + "/resoluciones"),
                                        cuerpo)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.repetido").value(true)));
        assertThat(repetida.get("idVersion")).isEqualTo(primera.get("idVersion"));
        Map<String, Object> clase =
                jdbc.queryForMap(
                        "select nombre,descripcion from uml_colaborativo.clase_uml where id=?",
                        e.clase());
        assertThat(clase)
                .containsEntry("nombre", "Confirmada")
                .containsEntry("descripcion", "Entrante");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.cambio_modelo where"
                                        + " id_modelo=? and id_cambio_cliente=?",
                                Long.class,
                                e.modelo(),
                                solicitud))
                .isEqualTo(1);
    }

    @Test
    void aplicarEntranteRestauraElementoEliminadoYFusionInvalidaNoModifica() throws Exception {
        EscenarioConflicto invalido = conflictoClase("Confirmada", null, "Propuesta", null);
        escribir(
                        invalido.editor(),
                        post(
                                "/api/modelos/"
                                        + invalido.modelo()
                                        + "/conflictos/"
                                        + invalido.conflicto()
                                        + "/resoluciones"),
                        resolver(invalido, UUID.randomUUID(), invalido.revision(), "FUSIONAR"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(
                        jdbc.queryForObject(
                                "select estado from uml_colaborativo.conflicto_edicion where id=?",
                                String.class,
                                invalido.conflicto()))
                .isEqualTo("ABIERTO");
        escribir(
                        invalido.editor(),
                        post(
                                "/api/modelos/"
                                        + invalido.modelo()
                                        + "/conflictos/"
                                        + invalido.conflicto()
                                        + "/descartes"),
                        Map.of(
                                "idSesionEdicion", invalido.sesionEditor(),
                                "idSolicitudResolucion", UUID.randomUUID(),
                                "revisionEsperada", invalido.revision(),
                                "confirmar", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicto.estado").value("DESCARTADO"));

        EscenarioConflicto eliminado = conflictoClase("Temporal", null, "Reactivada", null);
        // La modificación confirmada del escenario se convierte en eliminación antes de revisar.
        enviar(
                        eliminado.propietario(),
                        eliminado.modelo().toString(),
                        cambio(
                                eliminado.revision(),
                                UUID.randomUUID(),
                                Map.of(
                                        "operacion",
                                        "ELIMINAR",
                                        "entidad",
                                        "CLASE",
                                        "idEntidad",
                                        eliminado.clase())))
                .andExpect(status().isOk());
        long revision = eliminado.revision() + 1;
        mvc.perform(
                        get("/api/modelos/"
                                        + eliminado.modelo()
                                        + "/conflictos/"
                                        + eliminado.conflicto())
                                .cookie(eliminado.editor().sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementoEliminado").value(true));
        escribir(
                        eliminado.editor(),
                        post(
                                "/api/modelos/"
                                        + eliminado.modelo()
                                        + "/conflictos/"
                                        + eliminado.conflicto()
                                        + "/resoluciones"),
                        resolver(eliminado, UUID.randomUUID(), revision, "APLICAR_ENTRANTE"))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select nombre from uml_colaborativo.clase_uml where id=? and"
                                        + " eliminado_en is null",
                                String.class,
                                eliminado.clase()))
                .isEqualTo("Reactivada");
    }

    @Test
    void lectorConsultaPeroNoResuelveYPerdidaDePermisoSeRevalida() throws Exception {
        EscenarioConflicto e = conflictoClase("Confirmada", null, "Propuesta", null);
        String nombre = "lector_conf_" + UUID.randomUUID().toString().substring(0, 8);
        UUID lectorId = fixture(nombre);
        miembroFixture(e.proyecto(), lectorId, "LECTOR_PROYECTO");
        Cliente lector = login(nombre, clave);
        UUID sesionLector = asegurarSesion(lector, e.modelo().toString());
        mvc.perform(get("/api/modelos/" + e.modelo() + "/conflictos").cookie(lector.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeResolver").value(false))
                .andExpect(jsonPath("$.conflictos[0].diferencias").isArray());
        escribir(
                        lector,
                        post(
                                "/api/modelos/"
                                        + e.modelo()
                                        + "/conflictos/"
                                        + e.conflicto()
                                        + "/resoluciones"),
                        Map.of(
                                "idSesionEdicion",
                                sesionLector,
                                "idSolicitudResolucion",
                                UUID.randomUUID(),
                                "revisionEsperada",
                                e.revision(),
                                "tipoResolucion",
                                "CONSERVAR_ALMACENADO",
                                "confirmar",
                                true))
                .andExpect(status().isForbidden());

        jdbc.update(
                "insert into"
                    + " uml_colaborativo.permiso_miembro_proyecto(id_miembro_proyecto,id_permiso,decision)"
                    + " values(?,?,'DENEGAR')",
                e.miembroEditor(),
                permiso("RESOLVER_CONFLICTO"));
        escribir(
                        e.editor(),
                        post(
                                "/api/modelos/"
                                        + e.modelo()
                                        + "/conflictos/"
                                        + e.conflicto()
                                        + "/resoluciones"),
                        resolver(e, UUID.randomUUID(), e.revision(), "CONSERVAR_ALMACENADO"))
                .andExpect(status().isForbidden());
        assertThat(
                        jdbc.queryForObject(
                                "select estado from uml_colaborativo.conflicto_edicion where id=?",
                                String.class,
                                e.conflicto()))
                .isEqualTo("ABIERTO");
    }

    @Test
    void nuevaRevisionYDesconexionImpidenDecidirHastaActualizar() throws Exception {
        EscenarioConflicto e = conflictoClase("Confirmada", null, "Propuesta", null);
        enviar(
                        e.propietario(),
                        e.modelo().toString(),
                        cambio(
                                e.revision(),
                                UUID.randomUUID(),
                                clase(UUID.randomUUID(), "CambioPosterior")))
                .andExpect(status().isOk());
        escribir(
                        e.editor(),
                        post(
                                "/api/modelos/"
                                        + e.modelo()
                                        + "/conflictos/"
                                        + e.conflicto()
                                        + "/resoluciones"),
                        resolver(e, UUID.randomUUID(), e.revision(), "CONSERVAR_ALMACENADO"))
                .andExpect(status().isConflict());
        long vigente = e.revision() + 1;
        escribir(
                        e.editor(),
                        delete("/api/modelos/" + e.modelo() + "/sesiones/" + e.sesionEditor()),
                        Map.of())
                .andExpect(status().isOk());
        escribir(
                        e.editor(),
                        post(
                                "/api/modelos/"
                                        + e.modelo()
                                        + "/conflictos/"
                                        + e.conflicto()
                                        + "/resoluciones"),
                        resolver(e, UUID.randomUUID(), vigente, "CONSERVAR_ALMACENADO"))
                .andExpect(status().isConflict());
        var reabierta =
                respuesta(
                        escribir(
                                        e.editor(),
                                        post("/api/modelos/" + e.modelo() + "/sesiones"),
                                        Map.of("idSolicitudCliente", UUID.randomUUID()))
                                .andExpect(status().isCreated()));
        UUID sesion = UUID.fromString(reabierta.get("id").asText());
        colaboracion.conectar(acceso.identidad(e.usuarioEditor()), e.modelo(), sesion);
        Map<String, Object> recuperada =
                new HashMap<>(resolver(e, UUID.randomUUID(), vigente, "CONSERVAR_ALMACENADO"));
        recuperada.put("idSesionEdicion", sesion);
        escribir(
                        e.editor(),
                        post(
                                "/api/modelos/"
                                        + e.modelo()
                                        + "/conflictos/"
                                        + e.conflicto()
                                        + "/resoluciones"),
                        recuperada)
                .andExpect(status().isOk());
    }

    @Test
    void falloAlAplicarRevierteRevisionConflictoActividadYNotificacion() throws Exception {
        Cliente propietario = login(usuario, clave);
        JsonNode p = proyecto(propietario);
        UUID proyecto = UUID.fromString(p.get("id").asText());
        UUID modelo = UUID.fromString(p.get("idModelo").asText());
        UUID primera = UUID.randomUUID(), segunda = UUID.randomUUID();
        enviar(propietario, modelo.toString(), cambio(1, UUID.randomUUID(), clase(primera, "Uno")))
                .andExpect(status().isOk());
        enviar(
                        propietario,
                        modelo.toString(),
                        cambio(2, UUID.randomUUID(), clase(segunda, "Duplicado")))
                .andExpect(status().isOk());
        String nombre = "rollback_conf_" + UUID.randomUUID().toString().substring(0, 8);
        UUID editorId = fixture(nombre);
        miembroFixture(proyecto, editorId, "EDITOR_PROYECTO");
        Cliente editor = login(nombre, clave);
        UUID sesion = asegurarSesion(editor, modelo.toString());
        enviar(
                        propietario,
                        modelo.toString(),
                        cambio(3, UUID.randomUUID(), actualizarClase(primera, "Uno", "Confirmado")))
                .andExpect(status().isOk());
        enviar(
                        editor,
                        modelo.toString(),
                        cambio(3, UUID.randomUUID(), actualizarClase(primera, "Duplicado", null)))
                .andExpect(status().isConflict());
        JsonNode conflicto =
                respuesta(
                                mvc.perform(
                                                get("/api/modelos/" + modelo + "/conflictos")
                                                        .cookie(editor.sesion()))
                                        .andExpect(status().isOk()))
                        .get("conflictos")
                        .get(0);
        UUID id = UUID.fromString(conflicto.get("id").asText());
        long actividadAntes = actividad(proyecto.toString());
        clearInvocations(canal);
        escribir(
                        editor,
                        post("/api/modelos/" + modelo + "/conflictos/" + id + "/resoluciones"),
                        Map.of(
                                "idSesionEdicion",
                                sesion,
                                "idSolicitudResolucion",
                                UUID.randomUUID(),
                                "revisionEsperada",
                                4,
                                "tipoResolucion",
                                "APLICAR_ENTRANTE",
                                "confirmar",
                                true))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "select revision_actual from uml_colaborativo.modelo_uml where"
                                        + " id=?",
                                Long.class,
                                modelo))
                .isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "select estado from uml_colaborativo.conflicto_edicion where id=?",
                                String.class,
                                id))
                .isEqualTo("ABIERTO");
        assertThat(actividad(proyecto.toString())).isEqualTo(actividadAntes);
        verify(canal, never()).publicarConflicto(any());
    }

    // ─── CU19 – Consultar historial del modelo ─────────────────────────────

    @Test
    void historialVacioYSinPermiso() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        // Rev 1 viene del proyecto; la primera versión guarda el modelo vacío.
        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.versiones[0].numeroVersion").value(1));

        // Sin permiso: usuario sin acceso al proyecto.
        String u2 = "historial_ext_" + UUID.randomUUID().toString().substring(0, 8);
        fixture(u2);
        var ext = login(u2, clave);
        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), ext))
                .andExpect(status().isNotFound());
        mvc.perform(
                        autorizado(
                                get("/api/modelos/" + modelo + "/versiones/" + UUID.randomUUID()),
                                ext))
                .andExpect(status().isNotFound());
    }

    @Test
    void historialConVersionesYDetalle() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();

        // Crear una clase para generar rev 2 y versión 2.
        UUID idClase = UUID.randomUUID();
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(idClase, "HistorialClase")))
                .andExpect(status().isOk());

        // Listar: debe haber 2 versiones.
        var lista =
                mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(2))
                        .andReturn();
        var nodos = json.readTree(lista.getResponse().getContentAsString());
        assertThat(nodos.get("versiones").size()).isEqualTo(2);
        // Orden descendente por número de versión.
        assertThat(nodos.get("versiones").get(0).get("numeroVersion").asInt())
                .isGreaterThan(nodos.get("versiones").get(1).get("numeroVersion").asInt());

        // Detalle de la versión 2.
        String idVersion2 = nodos.get("versiones").get(0).get("id").asText();
        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones/" + idVersion2), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.revision").value(2))
                .andExpect(jsonPath("$.instantanea.formato_version").value(2));

        // Comparación entre v1 y v2.
        String idVersion1 = nodos.get("versiones").get(1).get("id").asText();
        mvc.perform(
                        autorizado(
                                get(
                                        "/api/modelos/"
                                                + modelo
                                                + "/comparaciones?desde="
                                                + idVersion1
                                                + "&hasta="
                                                + idVersion2),
                                c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diferencias").isArray())
                .andExpect(jsonPath("$.diferencias[0].operacion").value("AGREGADO"));
    }

    @Test
    void historialComparacionVersionesCruzadas() throws Exception {
        // Versiones de modelos distintos no se pueden comparar entre sí.
        var c = login(usuario, clave);
        var p1 = proyecto(c);
        var p2 = proyecto(c);
        String m1 = p1.get("idModelo").asText();
        String m2 = p2.get("idModelo").asText();
        String v1 =
                mvc.perform(autorizado(get("/api/modelos/" + m1 + "/versiones"), c))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String v2 =
                mvc.perform(autorizado(get("/api/modelos/" + m2 + "/versiones"), c))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String idV1 = json.readTree(v1).get("versiones").get(0).get("id").asText();
        String idV2 = json.readTree(v2).get("versiones").get(0).get("id").asText();
        mvc.perform(
                        autorizado(
                                get(
                                        "/api/modelos/"
                                                + m1
                                                + "/comparaciones?desde="
                                                + idV1
                                                + "&hasta="
                                                + idV2),
                                c))
                .andExpect(status().isBadRequest());
    }

    // ─── CU20 – Restaurar versión del modelo ───────────────────────────────

    @Test
    void restauracionPrevisualizacionSinBloqueadores() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        // Crear clase para generar rev 2 / versión 2.
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "PrevizClase")))
                .andExpect(status().isOk());
        // Obtener id de la versión 1.
        var lista =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String idV1 = null;
        for (var v : lista.get("versiones")) {
            if (v.get("numeroVersion").asInt() == 1) {
                idV1 = v.get("id").asText();
                break;
            }
        }
        assertThat(idV1).isNotNull();
        // Vista previa sin bloqueadores.
        mvc.perform(
                        autorizado(
                                        post(
                                                "/api/modelos/"
                                                        + modelo
                                                        + "/restauraciones/previsualizaciones"),
                                        c)
                                .contentType("application/json")
                                .content(json.writeValueAsString(Map.of("idVersionOrigen", idV1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueadores").isEmpty());
    }

    @Test
    void restauracionCompletaYSnapshotEquivalente() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();

        // Rev 2: crear clase A.
        UUID idClaseA = UUID.randomUUID();
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(idClaseA, "ClaseA")))
                .andExpect(status().isOk());
        // Rev 3: crear clase B.
        UUID idClaseB = UUID.randomUUID();
        enviar(c, modelo, cambio(2, UUID.randomUUID(), clase(idClaseB, "ClaseB")))
                .andExpect(status().isOk());

        // Obtener versión 2 (tiene solo ClaseA).
        var lista =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String idV2 = null;
        for (var v : lista.get("versiones")) {
            if (v.get("numeroVersion").asInt() == 2) {
                idV2 = v.get("id").asText();
                break;
            }
        }
        assertThat(idV2).isNotNull();

        // Restaurar a versión 2 desde rev 3.
        UUID solicitud = UUID.randomUUID();
        var resultado =
                mvc.perform(
                                autorizado(post("/api/modelos/" + modelo + "/restauraciones"), c)
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "idSolicitudRestauracion",
                                                                solicitud,
                                                                "idVersionOrigen",
                                                                idV2,
                                                                "revisionEsperada",
                                                                3,
                                                                "motivo",
                                                                "Test de restauración",
                                                                "confirmar",
                                                                true))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.revision").value(4))
                        .andExpect(jsonPath("$.repetido").value(false))
                        .andReturn();

        // Verificar que ClaseB ya no existe en el modelo restaurado.
        mvc.perform(autorizado(get("/api/modelos/" + modelo), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(4));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.clase_uml where id=?"
                                        + " and eliminado_en is null",
                                Integer.class,
                                idClaseB))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.clase_uml where id=?"
                                        + " and eliminado_en is null",
                                Integer.class,
                                idClaseA))
                .isEqualTo(1);

        // La versión restaurada debe tener id_version_restaurada apuntando a v2.
        String idV4 =
                json.readTree(resultado.getResponse().getContentAsString()).get("version").asText();
        assertThat(
                        jdbc.queryForObject(
                                "select id_version_restaurada from"
                                        + " uml_colaborativo.version_modelo_uml where id=?",
                                UUID.class,
                                UUID.fromString(idV4)))
                .isEqualTo(UUID.fromString(idV2));
    }

    @Test
    void restauracionIdempotente() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "IdempClase")))
                .andExpect(status().isOk());
        var lista =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String idV1 = null;
        for (var v : lista.get("versiones")) {
            if (v.get("numeroVersion").asInt() == 1) {
                idV1 = v.get("id").asText();
                break;
            }
        }
        UUID solicitud = UUID.randomUUID();
        var cuerpo =
                json.writeValueAsString(
                        Map.of(
                                "idSolicitudRestauracion",
                                solicitud,
                                "idVersionOrigen",
                                idV1,
                                "revisionEsperada",
                                2,
                                "motivo",
                                "Test idempotencia",
                                "confirmar",
                                true));
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(false));
        // Segunda petición con el mismo UUID → repetido=true.
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(true));
        // Debe haber solo una versión 3.
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from uml_colaborativo.version_modelo_uml"
                                        + " where id_modelo=?",
                                Integer.class,
                                UUID.fromString(modelo)))
                .isEqualTo(3);
    }

    @Test
    void restauracionSinConfimarRechazada() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        var lista =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String idV1 = lista.get("versiones").get(0).get("id").asText();
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Sin confirmación",
                                                        "confirmar",
                                                        false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restauracionRevisionObsoleta() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "ObsClase")))
                .andExpect(status().isOk());
        var lista =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String idV1 = null;
        for (var v : lista.get("versiones")) {
            if (v.get("numeroVersion").asInt() == 1) {
                idV1 = v.get("id").asText();
                break;
            }
        }
        // Enviar revisión incorrecta (1 en lugar de 2 que es la actual).
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Revisión obsoleta",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isConflict());
    }

    @Test
    void restauracionPermisosSeparados() throws Exception {
        var cAdmin = login(usuario, clave);
        var p = proyecto(cAdmin);
        String modelo = p.get("idModelo").asText();
        UUID idProyecto = UUID.fromString(p.get("id").asText());

        var lista =
                json.readTree(
                        mvc.perform(
                                        autorizado(
                                                get("/api/modelos/" + modelo + "/versiones"),
                                                cAdmin))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String idV1 = lista.get("versiones").get(0).get("id").asText();

        // 1. Usuario con rol EDITOR_PROYECTO (tiene EDITAR_MODELO, pero NO RESTAURAR_MODELO)
        String uEditor = "editor_" + UUID.randomUUID().toString().substring(0, 8);
        UUID idEditor = fixture(uEditor);
        miembroFixture(idProyecto, idEditor, "EDITOR_PROYECTO");
        var cEditor = login(uEditor, clave);

        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), cEditor)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Intento no autorizado",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isForbidden());

        // 2. Usuario con rol LECTOR_PROYECTO (solo lectura)
        String uLector = "lector_" + UUID.randomUUID().toString().substring(0, 8);
        UUID idLector = fixture(uLector);
        miembroFixture(idProyecto, idLector, "LECTOR_PROYECTO");
        var cLector = login(uLector, clave);

        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), cLector)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Intento no autorizado",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isForbidden());

        // 3. Usuario ajeno al proyecto
        String uAjeno = "ajeno_" + UUID.randomUUID().toString().substring(0, 8);
        fixture(uAjeno);
        var cAjeno = login(uAjeno, clave);

        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), cAjeno)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Intento ajeno",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isNotFound());

        // 4. Usuario con rol PROPIETARIO_PROYECTO (sí tiene RESTAURAR_MODELO)
        String uProp = "prop_" + UUID.randomUUID().toString().substring(0, 8);
        UUID idProp = fixture(uProp);
        miembroFixture(idProyecto, idProp, "PROPIETARIO_PROYECTO");
        var cProp = login(uProp, clave);

        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), cProp)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Restauración autorizada",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isOk());
    }

    @Test
    void restauracionIdempotenciaAisladaPorModelo() throws Exception {
        var c = login(usuario, clave);
        var p1 = proyecto(c);
        var p2 = proyecto(c);
        String m1 = p1.get("idModelo").asText();
        String m2 = p2.get("idModelo").asText();

        var lista1 =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + m1 + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var lista2 =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + m2 + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String v1M1 = lista1.get("versiones").get(0).get("id").asText();
        String v1M2 = lista2.get("versiones").get(0).get("id").asText();

        UUID idSolicitudCompartido = UUID.randomUUID();

        // Restaurar modelo 1
        mvc.perform(
                        autorizado(post("/api/modelos/" + m1 + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        idSolicitudCompartido,
                                                        "idVersionOrigen",
                                                        v1M1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Restaurar m1",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(false));

        // Reintento en modelo 1 con mismo UUID -> repetido=true
        mvc.perform(
                        autorizado(post("/api/modelos/" + m1 + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        idSolicitudCompartido,
                                                        "idVersionOrigen",
                                                        v1M1,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Restaurar m1",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(true));

        // Mismo idSolicitud en modelo 2 NO devuelve la repetición del modelo 1
        mvc.perform(
                        autorizado(post("/api/modelos/" + m2 + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        idSolicitudCompartido,
                                                        "idVersionOrigen",
                                                        v1M2,
                                                        "revisionEsperada",
                                                        1,
                                                        "motivo",
                                                        "Restaurar m2",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(false));
    }

    @Test
    void restauracionBloqueadaPorSesionEditoraActiva() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        UUID idModelo = UUID.fromString(modelo);

        // Crear versión 2
        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "BloqClase")))
                .andExpect(status().isOk());

        var lista =
                json.readTree(
                        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/versiones"), c))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String idV1 = null;
        for (var v : lista.get("versiones")) {
            if (v.get("numeroVersion").asInt() == 1) {
                idV1 = v.get("id").asText();
                break;
            }
        }

        // Asegurar que hay una sesión editora de otro usuario EN_LINEA
        String uColab = "colab_bloq_" + UUID.randomUUID().toString().substring(0, 8);
        UUID idColab = fixture(uColab);
        UUID idProyecto = UUID.fromString(p.get("id").asText());
        miembroFixture(idProyecto, idColab, "EDITOR_PROYECTO");
        var cColab = login(uColab, clave);
        asegurarSesion(cColab, modelo);

        // Previsualización debe incluir bloqueador
        mvc.perform(
                        autorizado(
                                        post(
                                                "/api/modelos/"
                                                        + modelo
                                                        + "/restauraciones/previsualizaciones"),
                                        c)
                                .contentType("application/json")
                                .content(json.writeValueAsString(Map.of("idVersionOrigen", idV1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueadores").isNotEmpty());

        // Restauración debe fallar con 409
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        2,
                                                        "motivo",
                                                        "Bloqueado",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isConflict());

        // Al desconectar la sesión, la restauración procede
        jdbc.update(
                "update uml_colaborativo.participante_sesion_modelo set salida_en=now(),"
                    + " estado_presencia='DESCONECTADO' where id_sesion_edicion in (select id from"
                    + " uml_colaborativo.sesion_edicion_modelo where id_modelo=?) and id_usuario=?",
                idModelo,
                idColab);

        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/restauraciones"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitudRestauracion",
                                                        UUID.randomUUID(),
                                                        "idVersionOrigen",
                                                        idV1,
                                                        "revisionEsperada",
                                                        2,
                                                        "motivo",
                                                        "Desbloqueado",
                                                        "confirmar",
                                                        true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(3));
    }

    @Test
    void exportacionXmiYDescarga() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        UUID idClase1 = UUID.randomUUID();
        UUID idClase2 = UUID.randomUUID();

        enviar(
                        c,
                        modelo,
                        cambio(
                                1,
                                UUID.randomUUID(),
                                clase(idClase1, "Producto"),
                                clase(idClase2, "Categoria")))
                .andExpect(status().isOk());

        // Exportar XMI
        var resExport =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                        post(
                                                                "/api/modelos/"
                                                                        + modelo
                                                                        + "/xmi/exportar"),
                                                        c)
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of(
                                                                        "idSolicitud",
                                                                        UUID.randomUUID(),
                                                                        "descripcion",
                                                                        "Exportacion para"
                                                                                + " Enterprise"
                                                                                + " Architect"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.idIntercambio").isNotEmpty())
                                .andExpect(jsonPath("$.idArchivoSalida").isNotEmpty())
                                .andExpect(
                                        jsonPath("$.nombreArchivo")
                                                .value(Matchers.endsWith(".xmi")))
                                .andExpect(jsonPath("$.totalClases").value(2)));

        String idIntercambio = resExport.get("idIntercambio").asText();

        // Descargar XMI
        var resDescarga =
                mvc.perform(
                                autorizado(
                                        get(
                                                "/api/modelos/"
                                                        + modelo
                                                        + "/xmi/"
                                                        + idIntercambio
                                                        + "/descargar"),
                                        c))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                "Content-Type",
                                                Matchers.containsString("application/xml")))
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                Matchers.containsString("attachment")))
                        .andReturn()
                        .getResponse();

        String xml = resDescarga.getContentAsString();
        assertThat(xml)
                .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<xmi:XMI")
                .contains("name=\"Producto\"")
                .contains("name=\"Categoria\"");
    }

    @Test
    void configuracionYGeneracionBackendYDescarga() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        UUID idClase = UUID.randomUUID();

        enviar(c, modelo, cambio(1, UUID.randomUUID(), clase(idClase, "Usuario")))
                .andExpect(status().isOk());

        // 1. Leer configuración por defecto (200 OK)
        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/generacion/configuracion"), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idGrupo").value("com.empresa"))
                .andExpect(jsonPath("$.generarRepositorios").value(true));

        // 2. Guardar configuración personalizada
        mvc.perform(
                        autorizado(put("/api/modelos/" + modelo + "/generacion/configuracion"), c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "nombre", "Backend Académico",
                                                        "nombreProyecto", "proyecto-academico",
                                                        "idGrupo", "bo.edu.uml",
                                                        "idArtefacto", "proyecto-academico",
                                                        "paqueteBase", "bo.edu.uml.academico",
                                                        "versionInicial", "1.0.0-SNAPSHOT",
                                                        "generarRepositorios", true,
                                                        "generarControladores", true,
                                                        "generarDtos", true,
                                                        "exponerRest", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idGrupo").value("bo.edu.uml"))
                .andExpect(jsonPath("$.paqueteBase").value("bo.edu.uml.academico"));

        // 3. Generar backend Spring Boot en ZIP
        var resGen =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                post(
                                                        "/api/modelos/"
                                                                + modelo
                                                                + "/generacion/generar"),
                                                c))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.idGeneracion").isNotEmpty())
                                .andExpect(jsonPath("$.idArchivoSalida").isNotEmpty())
                                .andExpect(
                                        jsonPath("$.nombreArchivo")
                                                .value("proyecto-academico-generated.zip"))
                                .andExpect(jsonPath("$.totalClases").value(1)));

        String idGeneracion = resGen.get("idGeneracion").asText();

        // 4. Descargar ZIP generado
        var resDescarga =
                mvc.perform(
                                autorizado(
                                        get(
                                                "/api/modelos/"
                                                        + modelo
                                                        + "/generacion/"
                                                        + idGeneracion
                                                        + "/descargar"),
                                        c))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                "Content-Type",
                                                Matchers.containsString("application/zip")))
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                Matchers.containsString("attachment")))
                        .andReturn()
                        .getResponse();

        byte[] bytes = resDescarga.getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
        // Magic bytes del formato ZIP: PK\x03\x04
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void interoperabilidadAutorizaRolesRealesSinDependerDelSuperadministrador() throws Exception {
        var administrador = login(usuario, clave);
        var proyecto = proyecto(administrador);
        UUID idProyecto = UUID.fromString(proyecto.get("id").asText());
        String modelo = proyecto.get("idModelo").asText();
        enviar(
                        administrador,
                        modelo,
                        cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "Interoperable")))
                .andExpect(status().isOk());

        String editorNombre = "xmi_editor_" + UUID.randomUUID().toString().substring(0, 8);
        UUID editorId = fixture(editorNombre);
        miembroFixture(idProyecto, editorId, "EDITOR_PROYECTO");
        var editor = login(editorNombre, clave);
        escribir(
                        editor,
                        post("/api/modelos/" + modelo + "/xmi/exportar"),
                        Map.of("idSolicitud", UUID.randomUUID(), "descripcion", "Rol editor"))
                .andExpect(status().isOk());
        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/generacion/configuracion"), editor))
                .andExpect(status().isForbidden());

        String propietarioNombre = "gen_prop_" + UUID.randomUUID().toString().substring(0, 8);
        UUID propietarioId = fixture(propietarioNombre);
        miembroFixture(idProyecto, propietarioId, "PROPIETARIO_PROYECTO");
        var propietario = login(propietarioNombre, clave);
        mvc.perform(
                        autorizado(
                                get("/api/modelos/" + modelo + "/generacion/configuracion"),
                                propietario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paqueteBase").value("com.empresa.modelodeclases"));
        mvc.perform(autorizado(post("/api/modelos/" + modelo + "/generacion/generar"), propietario))
                .andExpect(status().isOk());

        String lectorNombre = "xmi_lector_" + UUID.randomUUID().toString().substring(0, 8);
        UUID lectorId = fixture(lectorNombre);
        miembroFixture(idProyecto, lectorId, "LECTOR_PROYECTO");
        var lector = login(lectorNombre, clave);
        escribir(
                        lector,
                        post("/api/modelos/" + modelo + "/xmi/exportar"),
                        Map.of("idSolicitud", UUID.randomUUID(), "descripcion", "Rol lector"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permisosXmiYGeneracionRechazadosParaUsuariosSinRol() throws Exception {
        var adminCliente = login(usuario, clave);
        var p = proyecto(adminCliente);
        String modelo = p.get("idModelo").asText();

        // Crear usuario sin membresía
        String otroUser = "ajeno_" + UUID.randomUUID().toString().substring(0, 8);
        fixture(otroUser);
        var clienteAjeno = login(otroUser, clave);

        // Exportación XMI debe fallar con 403
        mvc.perform(
                        autorizado(post("/api/modelos/" + modelo + "/xmi/exportar"), clienteAjeno)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "idSolicitud",
                                                        UUID.randomUUID(),
                                                        "descripcion",
                                                        "Intento ajeno"))))
                .andExpect(status().isForbidden());

        // Generación debe fallar con 403
        mvc.perform(
                        autorizado(
                                post("/api/modelos/" + modelo + "/generacion/generar"),
                                clienteAjeno))
                .andExpect(status().isForbidden());
    }

    @Test
    void asistenciaTextoRevisionAplicacionYRechazo() throws Exception {
        var c = login(usuario, clave);
        var p = proyecto(c);
        String modelo = p.get("idModelo").asText();
        asegurarSesion(c, modelo);

        String prompt =
                "Crea una clase Cliente con atributos id: Long, nombre: String y una clase Pedido"
                        + " con atributo fecha: LocalDate, con relación 1 a *";
        var creada =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                        post(
                                                                "/api/modelos/"
                                                                        + modelo
                                                                        + "/ia/solicitud/texto"),
                                                        c)
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of("prompt", prompt))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.estadoSolicitud").value("COMPLETADO"))
                                .andExpect(jsonPath("$.confianzaGeneral").value(0.93)));
        String propuesta = creada.get("idPropuesta").asText();

        mvc.perform(autorizado(get("/api/modelos/" + modelo + "/ia/propuestas/" + propuesta), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.operaciones", Matchers.hasSize(6)))
                .andExpect(jsonPath("$.operaciones[5].tipoEntidad").value("RELACION"))
                .andExpect(jsonPath("$.operaciones[5].dependencias", Matchers.hasSize(2)));

        mvc.perform(
                        autorizado(
                                post(
                                        "/api/modelos/"
                                                + modelo
                                                + "/ia/propuestas/"
                                                + propuesta
                                                + "/aplicar"),
                                c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADO"))
                .andExpect(jsonPath("$.revision").value(2));

        mvc.perform(autorizado(get("/api/modelos/" + modelo), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clases", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.atributos", Matchers.hasSize(3)))
                .andExpect(jsonPath("$.relaciones", Matchers.hasSize(1)));
        verify(canal, atLeastOnce()).publicarCambio(any());

        mvc.perform(
                        autorizado(
                                post(
                                        "/api/modelos/"
                                                + modelo
                                                + "/ia/propuestas/"
                                                + propuesta
                                                + "/aplicar"),
                                c))
                .andExpect(status().isConflict());

        var segunda =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                        post(
                                                                "/api/modelos/"
                                                                        + modelo
                                                                        + "/ia/solicitud/texto"),
                                                        c)
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of(
                                                                        "prompt",
                                                                        "Crea una clase Factura"))))
                                .andExpect(status().isCreated()));
        String propuestaRechazada = segunda.get("idPropuesta").asText();
        mvc.perform(
                        autorizado(
                                        post(
                                                "/api/modelos/"
                                                        + modelo
                                                        + "/ia/propuestas/"
                                                        + propuestaRechazada
                                                        + "/rechazar"),
                                        c)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("motivo", "No corresponde al alcance"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));

        mvc.perform(autorizado(get("/api/modelos/" + modelo), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.clases", Matchers.hasSize(2)));
    }

    @Test
    void propuestaIaDesactualizadaAisladaYCsrf() throws Exception {
        var c = login(usuario, clave);
        var p1 = proyecto(c);
        var p2 = proyecto(c);
        String modelo = p1.get("idModelo").asText();
        String otroModelo = p2.get("idModelo").asText();
        asegurarSesion(c, modelo);

        mvc.perform(
                        post("/api/modelos/" + modelo + "/ia/solicitud/texto")
                                .cookie(c.sesion())
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("prompt", "Crea clase SinCsrf"))))
                .andExpect(status().isForbidden());

        var creada =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                        post(
                                                                "/api/modelos/"
                                                                        + modelo
                                                                        + "/ia/solicitud/texto"),
                                                        c)
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of(
                                                                        "prompt",
                                                                        "Crea clase"
                                                                            + " PropuestaAntigua"))))
                                .andExpect(status().isCreated()));
        String propuesta = creada.get("idPropuesta").asText();

        mvc.perform(
                        autorizado(
                                get("/api/modelos/" + otroModelo + "/ia/propuestas/" + propuesta),
                                c))
                .andExpect(status().isNotFound());

        enviar(
                        c,
                        modelo,
                        cambio(1, UUID.randomUUID(), clase(UUID.randomUUID(), "CambioConcurrente")))
                .andExpect(status().isOk());

        mvc.perform(
                        autorizado(
                                post(
                                        "/api/modelos/"
                                                + modelo
                                                + "/ia/propuestas/"
                                                + propuesta
                                                + "/aplicar"),
                                c))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value("DESACTUALIZADO"))
                .andExpect(jsonPath("$.aplicada").value(false));

        assertThat(
                        jdbc.queryForObject(
                                "select estado from uml_colaborativo.propuesta_inteligente where"
                                        + " id=?",
                                String.class,
                                UUID.fromString(propuesta)))
                .isEqualTo("DESACTUALIZADO");
        mvc.perform(autorizado(get("/api/modelos/" + modelo), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.clases", Matchers.hasSize(1)));
    }

    @Test
    void vozEImagenConservanElOriginalYNoInventanReconocimiento() throws Exception {
        var c = login(usuario, clave);
        String modelo = proyecto(c).get("idModelo").asText();
        var audio =
                new MockMultipartFile(
                        "archivo", "instruccion.webm", "audio/webm", new byte[] {26, 69, -33, -93});

        mvc.perform(
                        autorizado(
                                multipart("/api/modelos/" + modelo + "/ia/solicitud/voz")
                                        .file(audio),
                                c))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.estadoSolicitud").value("FALLIDO"))
                .andExpect(jsonPath("$.idPropuesta").isEmpty())
                .andExpect(jsonPath("$.mensaje", Matchers.containsString("transcripción")));

        mvc.perform(
                        autorizado(
                                multipart("/api/modelos/" + modelo + "/ia/solicitud/voz")
                                        .file(audio)
                                        .file(
                                                new MockMultipartFile(
                                                        "texto",
                                                        "",
                                                        "text/plain",
                                                        "Crea clase Dictada"
                                                                .getBytes(
                                                                        java.nio.charset
                                                                                .StandardCharsets
                                                                                .UTF_8))),
                                c))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoSolicitud").value("COMPLETADO"))
                .andExpect(jsonPath("$.idPropuesta").isNotEmpty());

        var salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", salida);
        var imagen =
                new MockMultipartFile("archivo", "pizarra.png", "image/png", salida.toByteArray());
        mvc.perform(
                        autorizado(
                                multipart("/api/modelos/" + modelo + "/ia/solicitud/imagen")
                                        .file(imagen),
                                c))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.estadoSolicitud").value("FALLIDO"))
                .andExpect(jsonPath("$.mensaje", Matchers.containsString("OCR")));

        assertThat(
                        jdbc.queryForObject(
                                """
                                select count(*) from uml_colaborativo.archivo_almacenado a
                                join uml_colaborativo.solicitud_inteligente s on s.id_archivo_origen=a.id
                                where s.id_modelo=? and a.categoria='AUDIO_VOZ'
                                """,
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                """
                                select count(*) from uml_colaborativo.archivo_almacenado a
                                join uml_colaborativo.solicitud_inteligente s on s.id_archivo_origen=a.id
                                where s.id_modelo=? and a.categoria='IMAGEN_DIAGRAMA'
                                  and octet_length(a.contenido)>0 and s.estado='FALLIDO'
                                """,
                                Long.class,
                                UUID.fromString(modelo)))
                .isEqualTo(1);
    }

    @Test
    void imagenValidaFirmaYAsistenciaRespetaPermisosReales() throws Exception {
        var propietario = login(usuario, clave);
        String modelo = proyecto(propietario).get("idModelo").asText();
        var falsa =
                new MockMultipartFile(
                        "archivo", "no-es-imagen.png", "image/png", "contenido".getBytes());
        mvc.perform(
                        autorizado(
                                multipart("/api/modelos/" + modelo + "/ia/solicitud/imagen")
                                        .file(falsa),
                                propietario))
                .andExpect(status().isUnprocessableEntity());

        String ajeno = "ia_ajeno_" + UUID.randomUUID().toString().substring(0, 8);
        fixture(ajeno);
        var sinPermiso = login(ajeno, clave);
        mvc.perform(
                        autorizado(
                                        post("/api/modelos/" + modelo + "/ia/solicitud/texto"),
                                        sinPermiso)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("prompt", "Crea clase Indebida"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/modelos/" + modelo + "/ia/propuestas/vigente")
                                .cookie(sinPermiso.sesion()))
                .andExpect(status().isForbidden());

        assertThat(
                        jdbc.queryForObject(
                                """
                                select count(*) from uml_colaborativo.rol r
                                join uml_colaborativo.rol_permiso rp on rp.id_rol=r.id
                                join uml_colaborativo.permiso p on p.id=rp.id_permiso
                                where r.codigo in ('PROPIETARIO_PROYECTO','EDITOR_PROYECTO')
                                  and p.codigo in ('SOLICITAR_IA','REVISAR_PROPUESTA_IA','APLICAR_PROPUESTA_IA')
                                """,
                                Long.class))
                .isEqualTo(6);
    }

    @Test
    void falloAlAplicarRevierteTodaLaRevisionIa() throws Exception {
        var c = login(usuario, clave);
        String modelo = proyecto(c).get("idModelo").asText();
        asegurarSesion(c, modelo);
        var creada =
                respuesta(
                        mvc.perform(
                                        autorizado(
                                                        post(
                                                                "/api/modelos/"
                                                                        + modelo
                                                                        + "/ia/solicitud/texto"),
                                                        c)
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of(
                                                                        "prompt",
                                                                        "Crea clase Rollback con"
                                                                            + " atributo nombre:"
                                                                            + " String"))))
                                .andExpect(status().isCreated()));
        UUID propuesta = UUID.fromString(creada.get("idPropuesta").asText());
        jdbc.update(
                """
                update uml_colaborativo.operacion_propuesta
                set valor_propuesto=jsonb_set(valor_propuesto,'{nombre}','\"\"'::jsonb)
                where id_propuesta=? and tipo_entidad='ATRIBUTO'
                """,
                propuesta);
        clearInvocations(canal);

        mvc.perform(
                        autorizado(
                                post(
                                        "/api/modelos/"
                                                + modelo
                                                + "/ia/propuestas/"
                                                + propuesta
                                                + "/aplicar"),
                                c))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(autorizado(get("/api/modelos/" + modelo), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.clases").isEmpty());
        assertThat(
                        jdbc.queryForObject(
                                "select estado from uml_colaborativo.propuesta_inteligente where"
                                        + " id=?",
                                String.class,
                                propuesta))
                .isEqualTo("LISTO");
        verify(canal, never()).publicarCambio(any());
    }
}
