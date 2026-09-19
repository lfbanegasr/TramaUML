package bo.edu.uml.generacion.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

/** DTOs inmutables para CU29/CU30/CU32 — Configuración y generación de backend Spring Boot. */
public final class GeneracionDto {
    private GeneracionDto() {}

    /** Configuración guardada en {@code configuracion_generacion_backend}. */
    public record ConfiguracionGeneracion(
            UUID id,
            @NotBlank @Size(max = 200) String nombre,
            @NotBlank @Size(max = 200) String nombreProyecto,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._\\-]+") @Size(max = 200) String idGrupo,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._\\-]+") @Size(max = 200) String idArtefacto,
            @NotBlank @Size(max = 50) String paqueteBase,
            @NotBlank @Size(max = 20) String versionInicial,
            boolean generarRepositorios,
            boolean generarControladores,
            boolean generarDtos,
            boolean exponerRest) {}

    /** Cuerpo de la solicitud PUT para actualizar la configuración. */
    public record ActualizarConfiguracion(
            @NotBlank @Size(max = 200) String nombre,
            @NotBlank @Size(max = 200) String nombreProyecto,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._\\-]+") @Size(max = 200) String idGrupo,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._\\-]+") @Size(max = 200) String idArtefacto,
            @NotBlank @Size(max = 50) String paqueteBase,
            @NotBlank @Size(max = 20) String versionInicial,
            boolean generarRepositorios,
            boolean generarControladores,
            boolean generarDtos,
            boolean exponerRest) {}

    /** Resultado de una generación exitosa. */
    public record ResultadoGeneracion(
            UUID idGeneracion,
            UUID idArchivoSalida,
            String nombreArchivo,
            int totalClases,
            int totalArchivosGenerados) {}
}
