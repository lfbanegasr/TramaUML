package bo.edu.uml.historial.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.historial.dto.HistorialDto.*;
import bo.edu.uml.historial.servicio.HistorialServicio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/modelos/{modelo}")
public class HistorialControlador {

    private final HistorialServicio servicio;

    public HistorialControlador(HistorialServicio servicio) {
        this.servicio = servicio;
    }

    /** CU19 — Listado paginado descendente con filtros opcionales. */
    @GetMapping("/versiones")
    PaginaVersiones listar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestParam(required = false) Integer cursor,
            @RequestParam(defaultValue = "20") int limite,
            @RequestParam(required = false) UUID autor,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return servicio.listar(actor, modelo, cursor, limite, autor, desde, hasta);
    }

    /** CU19 — Detalle de una versión con snapshot validado. */
    @GetMapping("/versiones/{version}")
    DetalleVersion detalle(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID version) {
        return servicio.detalle(actor, modelo, version);
    }

    /** CU19 — Diferencias entre dos versiones del mismo modelo. */
    @GetMapping("/comparaciones")
    DifVersion comparar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestParam @NotNull UUID desde,
            @RequestParam @NotNull UUID hasta) {
        return servicio.comparar(actor, modelo, desde, hasta);
    }

    /** CU20 — Vista previa sin escribir: detecta bloqueadores y estima cambios. */
    @PostMapping("/restauraciones/previsualizaciones")
    VistaPrevia previsualizacion(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @Valid @RequestBody SolicitarVistaPrevia solicitud) {
        return servicio.previsualizacion(actor, modelo, solicitud);
    }

    /** CU20 — Confirmación de la restauración con UUID idempotente. Requiere CSRF. */
    @PostMapping("/restauraciones")
    ResultadoRestauracion restaurar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @Valid @RequestBody SolicitarRestauracion solicitud) {
        return servicio.restaurar(actor, modelo, solicitud);
    }
}
