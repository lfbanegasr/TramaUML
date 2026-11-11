package bo.edu.uml.acceso.dto;

import jakarta.validation.constraints.*;

import java.util.*;

public final class AdministracionDto {
    private AdministracionDto() {}

    public record Usuario(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_.-]+") String usuario,
            @NotBlank @Email @Size(max = 254) String correo,
            @NotBlank @Size(max = 160) String nombre,
            @NotNull @Pattern(regexp = "ACTIVO|INACTIVO|PENDIENTE|BLOQUEADO") String estado,
            @NotNull @Size(max = 30) List<@NotNull UUID> roles,
            String revision) {}

    public record Rol(
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{3,80}") String codigo,
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 4000) String descripcion,
            @NotNull @Pattern(regexp = "GLOBAL|PROYECTO|AMBOS") String ambito,
            @NotNull @Pattern(regexp = "ACTIVO|INACTIVO") String estado,
            @NotNull @Size(max = 100) List<@NotNull UUID> permisos,
            String revision) {}

    public record Revision(@NotBlank String revision) {}

    public record Activar(
            @NotBlank @Size(max = 254) String identificador,
            @NotBlank @Size(max = 72) String temporal,
            @NotBlank @Size(min = 12, max = 72) String nueva) {}

    public record Credencial(UUID id, String credencialTemporal) {}
}
