package bo.edu.uml.proyectos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.*;

public final class MiembroDto {
    private MiembroDto() {}

    public record Decision(
            @NotNull UUID permiso,
            @NotNull @Pattern(regexp = "PERMITIR|DENEGAR") String decision) {}

    public record Guardar(
            @NotNull UUID usuario,
            @NotNull UUID rol,
            @NotNull @Pattern(regexp = "ACTIVO|SUSPENDIDO|RETIRADO") String estado,
            @Size(max = 2000) String motivo,
            @NotNull @Size(max = 100) List<@Valid Decision> permisos,
            String revision) {}

    public record Transferir(@NotNull UUID miembro) {}
}
