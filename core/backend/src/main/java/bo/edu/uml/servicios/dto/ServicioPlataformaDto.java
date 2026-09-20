package bo.edu.uml.servicios.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ServicioPlataformaDto {
    private ServicioPlataformaDto() {}

    public record GuardarServicio(
            @NotBlank @Size(max = 140) String nombre,
            @NotBlank @Pattern(regexp = "LOCAL|OPENAI|GEMINI|OLLAMA") String proveedor,
            @NotBlank @Pattern(regexp = "IA_TEXTO|VOZ_A_TEXTO|IA_IMAGEN") String tipoServicio,
            @Size(max = 2048) String urlServicio,
            @NotNull JsonNode configuracion,
            @Size(max = 4096) String credencial,
            boolean retirarCredencial,
            String revision) {}

    public record CambiarEstado(
            @NotBlank @Pattern(regexp = "ACTIVO|INACTIVO") String estado,
            @NotBlank String revision,
            boolean confirmarImpacto) {}

    public record Comprobacion(
            String estado, Integer latenciaMs, String detalle, Instant comprobadoEn) {}

    public record Servicio(
            UUID id,
            String nombre,
            String proveedor,
            String tipoServicio,
            String urlServicio,
            JsonNode configuracion,
            String estado,
            boolean tieneCredencial,
            UUID creadoPor,
            Instant creadoEn,
            Instant actualizadoEn,
            String revision,
            Comprobacion ultimaComprobacion) {}

    public record PaginaServicios(List<Servicio> servicios, int pagina, int tamano, long total) {}
}
