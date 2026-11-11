package bo.edu.uml.acceso.controlador;

import bo.edu.uml.acceso.servicio.AccesoServicio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.*;

@Configuration
public class SeguridadConfiguracion {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CookieCsrfTokenRepository csrfRepository(@Value("${uml.cookie-secure}") boolean secure) {
        var repo = new CookieCsrfTokenRepository();
        repo.setCookieCustomizer(c -> c.path("/").httpOnly(true).secure(secure).sameSite("Strict"));
        return repo;
    }

    @Bean
    @ConditionalOnWebApplication
    SecurityFilterChain seguridad(
            HttpSecurity http, AccesoServicio acceso, CookieCsrfTokenRepository csrf)
            throws Exception {
        return http
                // El filtro autentica cada petición desde PostgreSQL. Solo el login rota CSRF.
                .sessionManagement(s -> s.disable())
                .securityContext(
                        s -> s.securityContextRepository(new NullSecurityContextRepository()))
                .csrf(
                        c ->
                                c.csrfTokenRepository(csrf)
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler()))
                .requestCache(c -> c.disable())
                .formLogin(c -> c.disable())
                .httpBasic(c -> c.disable())
                .logout(c -> c.disable())
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/api/acceso/entrar",
                                                "/api/acceso/csrf",
                                                "/api/acceso/activar")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (r, s, x) -> {
                                                    s.setStatus(401);
                                                    s.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    s.getWriter()
                                                            .write(
                                                                    "{\"mensaje\":\"Inicia sesión"
                                                                        + " para continuar.\"}");
                                                })
                                        .accessDeniedHandler(
                                                (r, s, x) -> {
                                                    s.setStatus(403);
                                                    s.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    s.getWriter()
                                                            .write(
                                                                    "{\"mensaje\":\"La solicitud no"
                                                                        + " está autorizada.\"}");
                                                }))
                .headers(
                        h ->
                                h.contentSecurityPolicy(
                                        c ->
                                                c.policyDirectives(
                                                        "default-src 'none'; frame-ancestors"
                                                                + " 'none'")))
                .addFilterBefore(
                        new SesionFiltro(acceso), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
