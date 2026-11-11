package bo.edu.uml.proyectos.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.proyectos.dto.MiembroDto.*;
import bo.edu.uml.proyectos.servicio.MiembroServicio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/proyectos/{proyecto}/miembros")
public class MiembroControlador {
    private final MiembroServicio servicio;

    public MiembroControlador(MiembroServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Object listar(@AuthenticationPrincipal Identidad a, @PathVariable UUID proyecto) {
        return servicio.listar(a, proyecto);
    }

    @GetMapping("/cuentas")
    Object buscar(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID proyecto,
            @RequestParam @Size(max = 180) String filtro) {
        return servicio.buscar(a, proyecto, filtro);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Object crear(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID proyecto,
            @Valid @RequestBody Guardar d) {
        return servicio.guardar(a, proyecto, UUID.randomUUID(), d, true);
    }

    @PutMapping("/{id}")
    Object guardar(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID proyecto,
            @PathVariable UUID id,
            @Valid @RequestBody Guardar d) {
        return servicio.guardar(a, proyecto, id, d, false);
    }

    @PostMapping("/propietario")
    Object transferir(
            @AuthenticationPrincipal Identidad a,
            @PathVariable UUID proyecto,
            @Valid @RequestBody Transferir d) {
        return servicio.transferir(a, proyecto, d.miembro());
    }
}
