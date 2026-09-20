package bo.edu.uml.servicios.controlador;

import static bo.edu.uml.servicios.dto.ServicioPlataformaDto.*;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.servicios.servicio.ServicioPlataformaServicio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/administracion/servicios")
public class ServicioPlataformaControlador {
    private final ServicioPlataformaServicio servicio;

    public ServicioPlataformaControlador(ServicioPlataformaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    PaginaServicios listar(
            @AuthenticationPrincipal Identidad actor,
            @RequestParam(defaultValue = "") @Size(max = 140) String nombre,
            @RequestParam(defaultValue = "") @Pattern(regexp = "|LOCAL|OPENAI|GEMINI|OLLAMA")
                    String proveedor,
            @RequestParam(defaultValue = "") @Pattern(regexp = "|IA_TEXTO|VOZ_A_TEXTO|IA_IMAGEN")
                    String tipo,
            @RequestParam(defaultValue = "")
                    @Pattern(regexp = "|ACTIVO|INACTIVO|SIN_VERIFICAR|NO_DISPONIBLE")
                    String estado,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(actor, nombre, proveedor, tipo, estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    Servicio detalle(@AuthenticationPrincipal Identidad actor, @PathVariable UUID id) {
        return servicio.detalle(actor, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Servicio crear(
            @AuthenticationPrincipal Identidad actor,
            @Valid @RequestBody GuardarServicio solicitud) {
        return servicio.crear(actor, solicitud);
    }

    @PutMapping("/{id}")
    Servicio actualizar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID id,
            @Valid @RequestBody GuardarServicio solicitud) {
        return servicio.actualizar(actor, id, solicitud);
    }

    @PostMapping("/{id}/verificar")
    Servicio verificar(@AuthenticationPrincipal Identidad actor, @PathVariable UUID id) {
        return servicio.verificar(actor, id);
    }

    @PostMapping("/{id}/estado")
    Servicio cambiarEstado(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstado solicitud) {
        return servicio.cambiarEstado(actor, id, solicitud);
    }
}
