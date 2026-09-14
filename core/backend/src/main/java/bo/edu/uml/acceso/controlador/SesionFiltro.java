package bo.edu.uml.acceso.controlador;

import bo.edu.uml.acceso.servicio.AccesoServicio;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

public class SesionFiltro extends OncePerRequestFilter {
    public static final String COOKIE = "UML_SESION";
    private final AccesoServicio servicio;

    public SesionFiltro(AccesoServicio servicio) {
        this.servicio = servicio;
    }

    public static String token(HttpServletRequest r) {
        return r.getCookies() == null
                ? null
                : Arrays.stream(r.getCookies())
                        .filter(c -> COOKIE.equals(c.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
            throws ServletException, IOException {
        try {
            servicio.autenticar(token(r))
                    .ifPresent(
                            u ->
                                    SecurityContextHolder.getContext()
                                            .setAuthentication(
                                                    new UsernamePasswordAuthenticationToken(
                                                            u, null, java.util.List.of())));
        } catch (org.springframework.dao.DataAccessException e) {
            s.setStatus(503);
            s.setContentType("application/json;charset=UTF-8");
            s.getWriter().write("{\"mensaje\":\"El servicio de acceso no está disponible.\"}");
            return;
        }
        chain.doFilter(r, s);
    }
}
