package bo.edu.uml.modelado.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.*;

public final class ModeladoDto {
    private ModeladoDto() {}

    public record Cambio(
            @NotNull UUID idSesionEdicion,
            @NotNull UUID idCambioCliente,
            @Min(0) long revisionBase,
            @NotEmpty @Size(max = 50) List<@Valid Accion> acciones) {}

    public record Accion(
            @NotNull @Pattern(regexp = "CREAR|ACTUALIZAR|ELIMINAR|MOVER") String operacion,
            @NotNull @Pattern(regexp = "CLASE|ATRIBUTO|OPERACION|RELACION|PAQUETE") String entidad,
            @NotNull UUID idEntidad,
            JsonNode datos) {}

    public record Confirmacion(
            UUID idCambioCliente, long revision, UUID idVersion, boolean repetido) {}

    public record Modelo(
            UUID id,
            UUID idProyecto,
            String nombre,
            long revision,
            UUID idVersion,
            boolean puedeEditar,
            boolean puedeOrganizar,
            boolean puedeValidar,
            boolean puedeRestaurar,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> paquetes) {}
}
