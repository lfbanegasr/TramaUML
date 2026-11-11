package bo.edu.uml.proyectos.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.proyectos.dto.ProyectoDto.*;
import bo.edu.uml.proyectos.servicio.ProyectoServicio;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
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
            @RequestParam(defaultValue = "") String rol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(actor, filtro, estado, rol, desde, hasta, pagina, tamano);
    }

    @GetMapping("/roles-consulta")
    List<Map<String, Object>> roles(@AuthenticationPrincipal Identidad actor) {
        return servicio.rolesConsulta(actor);
    }

    @GetMapping("/{id}/equipo")
    List<Map<String, Object>> equipo(
            @AuthenticationPrincipal Identidad actor, @PathVariable UUID id) {
        return servicio.equipo(actor, id);
    }

    @PutMapping("/{id}")
    Resumen actualizar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID id,
            @Valid @RequestBody Actualizar datos) {
        return servicio.actualizar(actor, id, datos);
    }

    @PostMapping("/{id}/archivar")
    Resumen archivar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstado datos) {
        return servicio.cambiarEstado(actor, id, datos, true);
    }

    @PostMapping("/{id}/reactivar")
    Resumen reactivar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstado datos) {
        return servicio.cambiarEstado(actor, id, datos, false);
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
