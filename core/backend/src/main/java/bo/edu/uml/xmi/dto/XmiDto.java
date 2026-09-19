package bo.edu.uml.xmi.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

/** DTOs inmutables para CU28 — Exportación XMI. */
public final class XmiDto {
    private XmiDto() {}

    /** Cuerpo de la solicitud de exportación. */
    public record SolicitudExportacion(
            @NotNull UUID idSolicitud, @Size(max = 500) String descripcion) {}

    /** Resultado devuelto tras exportar exitosamente. */
    public record ResultadoExportacion(
            UUID idIntercambio,
            UUID idArchivoSalida,
            String nombreArchivo,
            int totalClases,
            int totalRelaciones) {}

    /** Metadatos de un intercambio para la descarga. */
    public record InfoDescarga(UUID id, String nombreArchivo, String tipoMime, long tamanioBytes) {}
}
