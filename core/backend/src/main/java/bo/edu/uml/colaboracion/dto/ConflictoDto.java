package bo.edu.uml.colaboracion.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.*;

import java.util.*;

public final class ConflictoDto {
    private ConflictoDto() {}

    public record Autor(UUID id, String nombre) {}

    public record CambioRelacionado(
            UUID id,
            UUID idCambioCliente,
            Autor autor,
            long revisionBase,
            Long revisionAplicada,
            String creadoEn) {}

    public record Diferencia(
            int indiceAccion,
            String operacion,
            String tipoEntidad,
            UUID idEntidad,
            String propiedad,
            JsonNode valorBase,
            JsonNode valorConfirmado,
            JsonNode valorPropuesto,
            boolean incompatible) {}

    public record Conflicto(
            UUID id,
            UUID idModelo,
            String tipoEntidad,
            UUID idEntidad,
            long revisionBase,
            long revisionActual,
            String estado,
            String tipoResolucion,
            String detectadoEn,
            Autor resueltoPor,
            String resueltoEn,
            CambioRelacionado cambioAlmacenado,
            CambioRelacionado cambioEntrante,
            JsonNode valorAlmacenadoDetectado,
            JsonNode valorEntrante,
            JsonNode valorConfirmadoActual,
            JsonNode valorResolucion,
            List<Diferencia> diferencias,
            boolean fusionable,
            boolean elementoEliminado,
            boolean puedeResolver) {}

    public record Lista(
            List<Conflicto> conflictos, long total, long revisionModelo, boolean puedeResolver) {}

    public record Resolver(
            @NotNull UUID idSesionEdicion,
            @NotNull UUID idSolicitudResolucion,
            @Min(0) long revisionEsperada,
            @NotNull @Pattern(regexp = "CONSERVAR_ALMACENADO|APLICAR_ENTRANTE|FUSIONAR")
                    String tipoResolucion,
            @AssertTrue(message = "Debes confirmar la resolución.") boolean confirmar) {}

    public record Descartar(
            @NotNull UUID idSesionEdicion,
            @NotNull UUID idSolicitudResolucion,
            @Min(0) long revisionEsperada,
            @AssertTrue(message = "Debes confirmar el descarte.") boolean confirmar) {}

    public record Resultado(Conflicto conflicto, Long revision, UUID idVersion, boolean repetido) {}
}
