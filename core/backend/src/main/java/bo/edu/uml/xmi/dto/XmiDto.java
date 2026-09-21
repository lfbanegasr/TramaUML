package bo.edu.uml.xmi.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/** DTOs inmutables para importación y exportación XMI 2.1. */
public final class XmiDto {
    private XmiDto() {}

    /** Cuerpo de la solicitud de exportación. Una versión nula selecciona la vigente. */
    public record SolicitudExportacion(
            @NotNull UUID idSolicitud,
            @Size(max = 500) String descripcion,
            UUID idVersion,
            UUID idPaquete,
            Boolean incluirSubpaquetes) {}

    /** Resultado devuelto tras exportar exitosamente. */
    public record ResultadoExportacion(
            UUID idIntercambio,
            UUID idArchivoSalida,
            String nombreArchivo,
            UUID idVersion,
            UUID idPaquete,
            int totalPaquetes,
            int totalClases,
            int totalRelaciones,
            List<String> advertencias) {}

    /** Una operación atómica que se aplicará si se confirma una importación. */
    public record OperacionImportacion(
            int orden,
            String operacion,
            String entidad,
            UUID idEntidad,
            String nombre,
            JsonNode datos) {}

    /** Previsualización persistida del intercambio de entrada. */
    public record Importacion(
            UUID idIntercambio,
            UUID idArchivoEntrada,
            String nombreArchivo,
            String estado,
            String estrategia,
            String varianteXmi,
            String versionEnterpriseArchitect,
            long revisionBase,
            long revisionActual,
            int totalPaquetes,
            int totalClases,
            int totalAtributos,
            int totalOperaciones,
            int totalRelaciones,
            List<String> advertencias,
            List<String> bloqueadores,
            List<OperacionImportacion> operaciones,
            boolean puedeAplicar,
            String detalleError) {}

    /** Confirmación explícita necesaria para aplicar una previsualización. */
    public record SolicitudAplicacionImportacion(
            @NotNull UUID idSolicitud,
            @NotNull UUID idSesionEdicion,
            @Min(0) long revisionBase,
            @AssertTrue(message = "Debes confirmar la importación") boolean confirmar) {}

    /** Resultado de aplicar todas las operaciones en una única revisión. */
    public record ResultadoAplicacionImportacion(
            UUID idIntercambio, String estado, long revision, UUID idVersion, boolean repetido) {}

    /** Motivo opcional al cancelar una importación todavía no aplicada. */
    public record SolicitudCancelacion(@Size(max = 500) String motivo) {}

    /** Metadatos de un intercambio para la descarga. */
    public record InfoDescarga(UUID id, String nombreArchivo, String tipoMime, long tamanioBytes) {}
}
