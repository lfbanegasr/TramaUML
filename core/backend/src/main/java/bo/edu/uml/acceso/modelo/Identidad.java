package bo.edu.uml.acceso.modelo;

import java.util.List;
import java.util.UUID;

public record Identidad(UUID id, String nombreUsuario, String nombreCompleto, List<String> rolesGlobales, List<String> permisosGlobales) {
    public boolean esAdministrador() { return rolesGlobales.contains("SUPERADMINISTRADOR"); }
    public boolean puedeCrearProyectos() { return esAdministrador() || permisosGlobales.contains("CREAR_PROYECTOS"); }
}
