package bo.edu.uml.historial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class HistorialDto {

    private HistorialDto() {}

    /**
     * Metadatos resumidos de una versión del modelo. Devueltos en la lista paginada y como
     * encabezado del detalle.
     */
    public record ResumenVersion(
            UUID id,
            int numeroVersion,
            long revision,
            UUID idVersionPadre,
            UUID idVersionRestaurada,
            String estadoValidacion,
            String resumen,
            UUID idAutor,
            String nombreAutor,
            String creadoEn,
            String sha256) {}

    /**
     * Resultado de GET /api/modelos/{modelo}/versiones con paginación descendente por cursor
     * (número de versión).
     */
    public record PaginaVersiones(
            List<ResumenVersion> versiones, long total, Integer cursorSiguiente) {}

    /**
     * Resultado de GET /api/modelos/{modelo}/versiones/{version}: metadatos + snapshot validado.
     */
    public record DetalleVersion(ResumenVersion resumen, Object instantanea) {}

    /** Diferencia entre dos versiones del mismo modelo. */
    public record DifVersion(
            ResumenVersion desde, ResumenVersion hasta, List<ElementoDif> diferencias) {}

    /** Diferencia a nivel de un elemento UML concreto. */
    public record ElementoDif(
            UUID id,
            String tipo,
            String nombre,
            String operacion,
            List<PropiedadDif> propiedades) {}

    /** Diferencia a nivel de una propiedad de un elemento. */
    public record PropiedadDif(String clave, Object valorAntes, Object valorDespues) {}

    /**
     * Vista previa de restauración. Devuelve bloqueadores (si los hay) y un estimado de cambios sin
     * escribir nada.
     */
    public record VistaPrevia(
            long revisionVigente,
            UUID versionVigente,
            List<String> bloqueadores,
            int elementosEstimados) {}

    /**
     * Cuerpo del POST /api/modelos/{modelo}/restauraciones/previsualizaciones.
     *
     * @param idVersionOrigen UUID de la versión que se quiere restaurar.
     */
    public record SolicitarVistaPrevia(
            @NotNull(message = "idVersionOrigen es obligatorio") UUID idVersionOrigen) {}

    /**
     * Cuerpo del POST /api/modelos/{modelo}/restauraciones.
     *
     * @param idSolicitudRestauracion UUID idempotente de la solicitud.
     * @param idVersionOrigen UUID de la versión origen.
     * @param revisionEsperada Revisión que el cliente cree que es la actual.
     * @param motivo Texto libre obligatorio que explica la razón de la restauración.
     * @param confirmar Debe ser {@code true}; evita restauraciones accidentales.
     */
    public record SolicitarRestauracion(
            @NotNull(message = "idSolicitudRestauracion es obligatorio")
                    UUID idSolicitudRestauracion,
            @NotNull(message = "idVersionOrigen es obligatorio") UUID idVersionOrigen,
            @NotNull(message = "revisionEsperada es obligatoria") Long revisionEsperada,
            @NotBlank(message = "El motivo de la restauración es obligatorio")
                    @Size(max = 1000, message = "El motivo no puede exceder 1000 caracteres")
                    String motivo,
            @NotNull(message = "El campo confirmar es obligatorio") Boolean confirmar) {}

    /** Resultado de una restauración completada. */
    public record ResultadoRestauracion(long revision, UUID version, boolean repetido) {}
}
