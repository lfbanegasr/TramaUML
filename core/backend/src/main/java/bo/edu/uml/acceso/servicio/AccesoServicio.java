package bo.edu.uml.acceso.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccesoServicio {
    private final AccesoRepositorio repositorio;
    private final PasswordEncoder encoder;
    private final SecureRandom random=new SecureRandom();
    private final String hashInexistente;
    public AccesoServicio(AccesoRepositorio repositorio, PasswordEncoder encoder) {
        this.repositorio=repositorio;this.encoder=encoder;hashInexistente=encoder.encode(UUID.randomUUID().toString());
    }
    public record Entrada(String token, Identidad usuario) {}
    @Transactional
    public Optional<Entrada> entrar(String identificador, String contrasena) {
        if(contrasena.getBytes(StandardCharsets.UTF_8).length>72) return Optional.empty();
        var cuenta=repositorio.bloquearCuenta(identificador.trim());
        boolean valida=encoder.matches(contrasena,cuenta.map(AccesoRepositorio.Cuenta::hash).orElse(hashInexistente));
        if(cuenta.isEmpty()) return Optional.empty();
        var c=cuenta.get();
        if(!"ACTIVO".equals(c.estado()) || c.bloqueada()) return Optional.empty();
        if(!valida) { repositorio.accesoFallido(c.id());return Optional.empty(); }
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repositorio.crearSesion(c.id(),hash(token));
        return Optional.of(new Entrada(token,repositorio.identidad(c.id())));
    }
    @Transactional(readOnly=true)
    public Optional<Identidad> autenticar(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return repositorio.autenticar(hash(token));
    }
    @Transactional public void salir(String token) { if(token!=null) repositorio.revocar(hash(token)); }
    public static byte[] hash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
