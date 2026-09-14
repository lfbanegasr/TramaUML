package bo.edu.uml.proyectos.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.proyectos.dto.ProyectoDto.*;
import bo.edu.uml.proyectos.servicio.ProyectoServicio;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoControlador {
    private final ProyectoServicio servicio;

    public ProyectoControlador(ProyectoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina listar(
            @AuthenticationPrincipal Identidad actor,
            @RequestParam(defaultValue = "") String filtro,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(actor, filtro, estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    Resumen obtener(@AuthenticationPrincipal Identidad actor, @PathVariable UUID id) {
        return servicio.obtener(actor, id);
    }

    @PostMapping
    ResponseEntity<Resumen> crear(
            @AuthenticationPrincipal Identidad actor, @Valid @RequestBody Crear datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(actor, datos));
    }
}
