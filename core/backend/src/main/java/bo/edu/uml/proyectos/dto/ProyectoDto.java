package bo.edu.uml.proyectos.dto;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.*;

public final class ProyectoDto {
    private ProyectoDto() {}

    public record Crear(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_.-]+") String codigo,
            @NotBlank @Size(max = 180) String nombre,
            @Size(max = 4000) String descripcion) {}

    public record Resumen(
            UUID id,
            String codigo,
            String nombre,
            String descripcion,
            String estado,
            String propietario,
            String rol,
            UUID idModelo,
            boolean puedeVerModelo,
            boolean puedeEditar,
            boolean puedeGestionarMiembros,
            boolean puedeGestionarProyecto,
            boolean puedeVerActividad,
            String revision,
            String creadoEn,
            String archivadoEn,
            String nombreModelo,
            long revisionModelo,
            int miembrosActivos,
            int sesionesEdicionActivas,
            long totalVersiones) {}

    public record Actualizar(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_.-]+") String codigo,
            @NotBlank @Size(max = 180) String nombre,
            @Size(max = 4000) String descripcion,
            @NotNull OffsetDateTime revision,
            @NotNull UUID idSolicitudCliente) {}

    public record CambiarEstado(
            @NotNull OffsetDateTime revision,
            @NotNull UUID idSolicitudCliente,
            @AssertTrue boolean confirmar) {}

    public record Pagina(List<Resumen> proyectos, int pagina, int tamano, long total) {}
}
