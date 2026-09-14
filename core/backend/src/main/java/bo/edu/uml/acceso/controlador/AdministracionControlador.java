package bo.edu.uml.acceso.controlador;

import bo.edu.uml.acceso.dto.AdministracionDto.*;
import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.servicio.AdministracionServicio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@Validated
@RequestMapping("/api/administracion")
public class AdministracionControlador {
    private final AdministracionServicio servicio;

    public AdministracionControlador(AdministracionServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/usuarios")
    Object usuarios(
            @AuthenticationPrincipal Identidad a,
            @RequestParam(defaultValue = "") @Size(max = 180) String filtro,
            @RequestParam(defaultValue = "") String estado,
            @RequestParam(defaultValue = "") String rol,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int pagina) {
        return servicio.usuarios(a, filtro, estado, rol, pagina);
    }

    @PostMapping("/usuarios")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Credencial crear(@AuthenticationPrincipal Identidad a, @Valid @RequestBody Usuario d) {
        return servicio.crear(a, d);
    }

    @PutMapping("/usuarios/{id}")
    Object usuario(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID id,
            @Valid @RequestBody Usuario d) {
        return servicio.actualizar(a, id, d);
    }

    @PostMapping("/usuarios/{id}/restablecer")
    Credencial restablecer(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID id,
            @Valid @RequestBody Revision d) {
        return servicio.restablecer(a, id, d.revision());
    }

    @GetMapping("/roles")
    Object roles(@AuthenticationPrincipal Identidad a) {
        return servicio.roles(a);
    }

    @GetMapping("/roles-usuario")
    Object rolesUsuario(@AuthenticationPrincipal Identidad a) {
        return servicio.rolesParaUsuario(a);
    }

    @GetMapping("/permisos")
    Object permisos(@AuthenticationPrincipal Identidad a) {
        return servicio.permisos(a);
    }

    @PostMapping("/roles")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Object crearRol(@AuthenticationPrincipal Identidad a, @Valid @RequestBody Rol d) {
        return servicio.guardarRol(a, UUID.randomUUID(), d, true);
    }

    @PutMapping("/roles/{id}")
    Object rol(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID id,
            @Valid @RequestBody Rol d) {
        return servicio.guardarRol(a, id, d, false);
    }
}
