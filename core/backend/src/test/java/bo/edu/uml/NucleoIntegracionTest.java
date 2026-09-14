package bo.edu.uml;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import bo.edu.uml.acceso.servicio.*;
import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.*;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;
import java.util.concurrent.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.datasource.url=${UML_TEST_DB_URL}",
            "spring.datasource.password=${UML_TEST_DB_PASSWORD}",
            "uml.cookie-secure=false",
            "uml.bootstrap.enabled=false",
            "uml.validacion.worker=false"
        })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NucleoIntegracionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired InicializarAdministrador inicializar;
    @Autowired AccesoRepositorio acceso;
    private String usuario, clave;
    private UUID admin;

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
        return mvc.perform(
                autorizado(post("/api/modelos/" + modelo + "/cambios"), c)
                        .contentType("application/json")
                        .content(cuerpo));
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
}
