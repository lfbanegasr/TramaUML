package bo.edu.uml.validacion.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.validacion.servicio.ValidacionServicio;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/modelos/{modelo}/validaciones")
public class ValidacionControlador {
    private final ValidacionServicio servicio;

    public ValidacionControlador(ValidacionServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    Object solicitar(@AuthenticationPrincipal Identidad a, @PathVariable UUID modelo) {
        return servicio.solicitar(a, modelo);
    }

    @GetMapping(path = {"", "/reciente"})
    Object ultima(@AuthenticationPrincipal Identidad a, @PathVariable UUID modelo) {
        return servicio.informe(a, modelo, null);
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]+}")
    Object informe(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        return servicio.informe(a, modelo, id);
    }
}
