package bo.edu.uml.ia.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IaDto {
    private IaDto() {}

    public record SolicitudTexto(
            @NotBlank(message = "El prompt es obligatorio")
                    @Size(max = 12000, message = "El prompt supera el tamaño permitido")
                    String prompt) {}

    public record Rechazo(
            @Size(max = 1000, message = "El motivo supera el tamaño permitido") String motivo) {}

    public record Operacion(
            UUID id,
            int numeroSecuencia,
            String tipoOperacion,
            String tipoEntidad,
            UUID idEntidadDestino,
            JsonNode valorActual,
            JsonNode valorPropuesto,
            BigDecimal confianza,
            boolean seleccionada,
            String estado,
            JsonNode mensajesValidacion,
            String justificacion,
            List<UUID> dependencias) {}

    public record Propuesta(
            UUID id,
            UUID idSolicitud,
            UUID idModelo,
            String tipoEntrada,
            String textoOriginal,
            long revisionBase,
            long revisionActual,
            String estado,
            String resumen,
            BigDecimal confianzaGeneral,
            Instant creadoEn,
            List<Operacion> operaciones) {}

    public record ResultadoSolicitud(
            UUID idSolicitud,
            UUID idPropuesta,
            String estadoSolicitud,
            String resumen,
            BigDecimal confianzaGeneral,
            long revisionBase,
            String mensaje) {}

    public record ResultadoAplicacion(
            UUID idPropuesta,
            String estado,
            boolean aplicada,
            Long revision,
            UUID idVersion,
            String mensaje) {}

    public record ResultadoRechazo(UUID idPropuesta, String estado, String motivo) {}
}
