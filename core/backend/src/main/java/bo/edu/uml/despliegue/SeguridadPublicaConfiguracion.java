package bo.edu.uml.despliegue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Solo los archivos de la web y la comprobación de salud son públicos. */
@Configuration
@Profile("despliegue")
@ConditionalOnWebApplication
public class SeguridadPublicaConfiguracion {
  @Bean
  @Order(0)
  SecurityFilterChain webPublica(HttpSecurity http) throws Exception {
    return http.securityMatcher("/", "/index.html", "/assets/**", "/favicon.ico", "/salud")
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico", "/salud")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.HEAD, "/", "/index.html", "/assets/**", "/favicon.ico", "/salud")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .requestCache(c -> c.disable())
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'"
                                + " 'unsafe-inline' https://fonts.googleapis.com; img-src 'self'"
                                + " data: blob:; font-src 'self' data: https://fonts.gstatic.com;"
                                + " connect-src 'self'; object-src 'none'; base-uri 'self';"
                                + " frame-ancestors 'none'; form-action 'self'")))
        .build();
  }
}
