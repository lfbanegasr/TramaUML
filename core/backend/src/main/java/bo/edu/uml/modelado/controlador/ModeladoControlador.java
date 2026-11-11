package bo.edu.uml.modelado.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.modelado.dto.ModeladoDto.*;
import bo.edu.uml.modelado.servicio.ModeladoServicio;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/modelos")
public class ModeladoControlador {
    private final ModeladoServicio servicio;

    public ModeladoControlador(ModeladoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{id}")
    Modelo leer(@AuthenticationPrincipal Identidad actor, @PathVariable UUID id) {
        return servicio.leer(actor, id);
    }

    @PostMapping("/{id}/cambios")
    Confirmacion aplicar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID id,
            @Valid @RequestBody Cambio cambio) {
        return servicio.aplicar(actor, id, cambio);
    }
}
