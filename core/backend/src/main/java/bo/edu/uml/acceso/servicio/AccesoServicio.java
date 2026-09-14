package bo.edu.uml.acceso.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.AccesoRepositorio;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class AccesoServicio {
    private final AccesoRepositorio repositorio;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final String hashInexistente;

    public AccesoServicio(AccesoRepositorio repositorio, PasswordEncoder encoder) {
        this.repositorio = repositorio;
        this.encoder = encoder;
        hashInexistente = encoder.encode(UUID.randomUUID().toString());
    }

    public record Entrada(String token, Identidad usuario) {}

    @Transactional
    public Optional<Entrada> entrar(String identificador, String contrasena) {
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > 72) return Optional.empty();
        var cuenta = repositorio.bloquearCuenta(identificador.trim());
        boolean valida =
                encoder.matches(
                        contrasena,
                        cuenta.map(AccesoRepositorio.Cuenta::hash).orElse(hashInexistente));
        if (cuenta.isEmpty()) return Optional.empty();
        var c = cuenta.get();
        if (!"ACTIVO".equals(c.estado()) || c.bloqueada() || c.requiereActivacion())
            return Optional.empty();
        if (!valida) {
            repositorio.accesoFallido(c.id());
            bo.edu.uml.compartido.Auditoria.despuesDelCommit(c.id(), "ACCESO_RECHAZADO", c.id());
            return Optional.empty();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repositorio.crearSesion(c.id(), hash(token));
        bo.edu.uml.compartido.Auditoria.despuesDelCommit(c.id(), "INICIAR_SESION", c.id());
        return Optional.of(new Entrada(token, repositorio.identidad(c.id())));
    }

    @Transactional(readOnly = true)
    public Optional<Identidad> autenticar(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return repositorio.autenticar(hash(token));
    }

    @Transactional
    public void salir(String token) {
        if (token != null) {
            var actor = repositorio.autenticar(hash(token));
            repositorio.revocar(hash(token));
            actor.ifPresent(
                    a ->
                            bo.edu.uml.compartido.Auditoria.despuesDelCommit(
                                    a.id(), "CERRAR_SESION", a.id()));
        }
    }

    @Transactional
    public boolean activar(String identificador, String temporal, String nueva) {
        if (nueva.getBytes(StandardCharsets.UTF_8).length > 72
                || temporal.getBytes(StandardCharsets.UTF_8).length > 72
                || nueva.equals(temporal)) return false;
        var cuenta = repositorio.bloquearCuenta(identificador.trim());
        boolean valida =
                encoder.matches(
                        temporal,
                        cuenta.map(AccesoRepositorio.Cuenta::hash).orElse(hashInexistente));
        if (cuenta.isEmpty()
                || !"PENDIENTE".equals(cuenta.get().estado())
                || !cuenta.get().requiereActivacion()
                || cuenta.get().bloqueada()) return false;
        if (!valida) {
            repositorio.accesoFallido(cuenta.get().id());
            return false;
        }
        repositorio.activar(cuenta.get().id(), encoder.encode(nueva));
        bo.edu.uml.compartido.Auditoria.despuesDelCommit(
                cuenta.get().id(), "ACTIVAR_CUENTA", cuenta.get().id());
        return true;
    }

    public static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
