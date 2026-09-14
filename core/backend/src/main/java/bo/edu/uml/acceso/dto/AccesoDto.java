package bo.edu.uml.acceso.dto;

import bo.edu.uml.acceso.modelo.Identidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccesoDto {
    private AccesoDto() {}
    public record Credenciales(@NotBlank @Size(max=254) String identificador, @NotBlank @Size(max=72) String contrasena) {}
    public record Sesion(Identidad usuario, boolean puedeCrearProyectos) {}
}
