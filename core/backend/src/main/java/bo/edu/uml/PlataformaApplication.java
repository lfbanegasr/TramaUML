package bo.edu.uml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        exclude =
                org.springframework.boot.autoconfigure.security.servlet
                        .UserDetailsServiceAutoConfiguration.class)
public class PlataformaApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlataformaApplication.class);
        if ("true".equalsIgnoreCase(System.getenv("UML_BOOTSTRAP_ENABLED"))) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        var context = app.run(args);
        if (context.getEnvironment().getProperty("uml.bootstrap.enabled", Boolean.class, false))
            context.close();
    }
}
