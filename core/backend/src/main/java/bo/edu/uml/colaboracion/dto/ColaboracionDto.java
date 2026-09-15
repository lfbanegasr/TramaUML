package bo.edu.uml.colaboracion.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class ColaboracionDto {
    private ColaboracionDto() {}

    public record Abrir(@NotNull UUID idSolicitudCliente) {}

    public record Participante(
            UUID idUsuario,
            String nombre,
            String rol,
            String modo,
            String estado,
            String tipoEntidadEnfocada,
            UUID idEntidadEnfocada) {}

    public record Sesion(
            UUID id,
            UUID idModelo,
            String modo,
            String estado,
            long revisionBase,
            String modoPropio,
            List<Participante> participantes) {}

    public record Foco(String tipoEntidad, UUID idEntidad) {}

    public record Evento(String tipo, Object datos) {}
}
