package bo.edu.uml.acceso.servicio;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@ConditionalOnProperty(name = "uml.bootstrap.enabled", havingValue = "true")
public class InicializacionCli implements ApplicationRunner {
    private final InicializarAdministrador servicio;
    private final Environment env;

    public InicializacionCli(InicializarAdministrador servicio, Environment env) {
        this.servicio = servicio;
        this.env = env;
    }

    public void run(ApplicationArguments args) {
        servicio.inicializar(
                env.getProperty("uml.bootstrap.nombre-usuario"),
                env.getProperty("uml.bootstrap.correo"),
                env.getProperty("uml.bootstrap.nombre-completo"),
                env.getProperty("uml.bootstrap.contrasena"));
        System.out.println(
                "Primer administrador inicializado. Desactiva el modo de inicialización y retira"
                        + " las variables UML_ADMIN_*.");
    }
}
