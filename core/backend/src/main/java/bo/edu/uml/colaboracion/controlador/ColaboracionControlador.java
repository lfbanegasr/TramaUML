package bo.edu.uml.colaboracion.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.dto.ColaboracionDto.*;
import bo.edu.uml.colaboracion.servicio.ColaboracionServicio;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/modelos/{modelo}/sesiones")
public class ColaboracionControlador {
    private final ColaboracionServicio servicio;

    public ColaboracionControlador(ColaboracionServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Sesion abrir(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @Valid @RequestBody Abrir solicitud) {
        return servicio.abrir(actor, modelo, solicitud);
    }

    @GetMapping("/{sesion}")
    Sesion consultar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID sesion) {
        return servicio.consultar(actor, modelo, sesion);
    }

    @DeleteMapping("/{sesion}")
    Sesion salir(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID sesion) {
        return servicio.salir(actor, modelo, sesion);
    }
}
