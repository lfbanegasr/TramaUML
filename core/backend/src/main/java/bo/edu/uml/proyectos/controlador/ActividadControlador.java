package bo.edu.uml.proyectos.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.proyectos.dto.ActividadDto.*;
import bo.edu.uml.proyectos.servicio.ActividadServicio;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/proyectos/{proyecto}/actividad")
public class ActividadControlador {
    private final ActividadServicio servicio;

    public ActividadControlador(ActividadServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina listar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID proyecto,
            @RequestParam(required = false) UUID responsable,
            @RequestParam(defaultValue = "") String accion,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(required = false) UUID entidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(
                actor, proyecto, responsable, accion, tipo, entidad, desde, hasta, pagina, tamano);
    }

    @GetMapping("/filtros")
    Map<String, Object> filtros(
            @AuthenticationPrincipal Identidad actor, @PathVariable UUID proyecto) {
        return servicio.filtros(actor, proyecto);
    }

    @GetMapping("/{id}")
    Detalle detalle(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID proyecto,
            @PathVariable long id) {
        return servicio.detalle(actor, proyecto, id);
    }
}
