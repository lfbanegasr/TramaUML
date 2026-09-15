package bo.edu.uml.acceso.controlador;

import bo.edu.uml.acceso.dto.AccesoDto.*;
import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.servicio.AccesoServicio;
import bo.edu.uml.compartido.ApiException;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/acceso")
public class AccesoControlador {
    private final AccesoServicio servicio;
    private final CookieCsrfTokenRepository csrf;
    private final boolean secure;

    public AccesoControlador(
            AccesoServicio servicio,
            CookieCsrfTokenRepository csrf,
            @Value("${uml.cookie-secure}") boolean secure) {
        this.servicio = servicio;
        this.csrf = csrf;
        this.secure = secure;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "header", token.getHeaderName());
    }

    @PostMapping("/entrar")
    ResponseEntity<Sesion> entrar(
            @Valid @RequestBody Credenciales datos, HttpServletRequest r, HttpServletResponse s) {
        var entrada =
                servicio.entrar(datos.identificador(), datos.contrasena())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                401,
                                                "Credenciales inválidas o cuenta no disponible."));
        csrf.saveToken(null, r, s);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie(entrada.token(), Duration.ofHours(2)).toString())
                .body(new Sesion(entrada.usuario(), entrada.usuario().puedeCrearProyectos()));
    }

    @GetMapping("/sesion")
    Sesion sesion(@AuthenticationPrincipal Identidad u) {
        return new Sesion(u, u.puedeCrearProyectos());
    }

    @PostMapping("/activar")
    ResponseEntity<Void> activar(
            @Valid @RequestBody bo.edu.uml.acceso.dto.AdministracionDto.Activar d) {
        if (!servicio.activar(d.identificador(), d.temporal(), d.nueva()))
            throw new ApiException(
                    401,
                    "La credencial temporal no es válida o la cuenta no está pendiente; la nueva"
                            + " contraseña debe ser distinta.");
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/salir")
    ResponseEntity<Void> salir(HttpServletRequest r, HttpServletResponse s) {
        servicio.salir(SesionFiltro.token(r));
        csrf.saveToken(null, r, s);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie cookie(String token, Duration duration) {
        return ResponseCookie.from(SesionFiltro.COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                // El mismo token opaco autentica el upgrade de /ws; sigue oculto y SameSite.
                .path("/")
                .maxAge(duration)
                .build();
    }
}
