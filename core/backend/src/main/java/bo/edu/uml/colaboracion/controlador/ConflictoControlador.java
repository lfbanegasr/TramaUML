package bo.edu.uml.colaboracion.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.dto.ConflictoDto.*;
import bo.edu.uml.colaboracion.servicio.ConflictoServicio;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/modelos/{modelo}/conflictos")
public class ConflictoControlador {
    private final ConflictoServicio servicio;

    public ConflictoControlador(ConflictoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Lista listar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestParam(defaultValue = "ABIERTO") String estado) {
        return servicio.listar(actor, modelo, estado);
    }

    @GetMapping("/{id}")
    Conflicto consultar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        return servicio.consultar(actor, modelo, id);
    }

    @PostMapping("/{id}/resoluciones")
    Resultado resolver(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id,
            @Valid @RequestBody Resolver solicitud) {
        return servicio.resolver(actor, modelo, id, solicitud);
    }

    @PostMapping("/{id}/descartes")
    Resultado descartar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id,
            @Valid @RequestBody Descartar solicitud) {
        return servicio.descartar(actor, modelo, id, solicitud);
    }
}
