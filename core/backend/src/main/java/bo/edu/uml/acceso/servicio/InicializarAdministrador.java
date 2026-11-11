package bo.edu.uml.acceso.servicio;

import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import bo.edu.uml.compartido.ApiException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class InicializarAdministrador {
    private final AccesoRepositorio repositorio;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public InicializarAdministrador(
            AccesoRepositorio repositorio, JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.repositorio = repositorio;
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Transactional
    public void inicializar(String usuario, String correo, String nombre, String clave) {
        if (usuario == null
                || !usuario.matches("[A-Za-z0-9_.-]{3,80}")
                || correo == null
                || !correo.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || correo.length() > 254
                || nombre == null
                || nombre.isBlank()
                || nombre.length() > 160
                || clave == null
                || clave.length() < 12
                || clave.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException(
                    "Define usuario, correo, nombre y contraseña de 12 a 72 bytes en el entorno.");
        // Coordina varias inicializaciones y bloquea inserciones concurrentes hasta el commit.
        jdbc.execute("LOCK TABLE uml_colaborativo.usuario IN SHARE ROW EXCLUSIVE MODE");
        if (repositorio.hayUsuarios())
            throw new ApiException(409, "La inicialización está cerrada: ya existen usuarios.");
        repositorio.registrarAdministrador(usuario, correo, nombre.trim(), encoder.encode(clave));
    }
}
