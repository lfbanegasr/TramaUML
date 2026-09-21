package bo.edu.uml.generacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Contratos inmutables para la configuración y generación avanzada del backend. */
public final class GeneracionDto {
    private GeneracionDto() {}

    public record VersionGeneracion(
            UUID id,
            int numeroVersion,
            long revision,
            String estadoValidacion,
            boolean versionActual) {}

    public record OperacionGeneracion(
            UUID id, String nombre, String tipoRetorno, boolean seleccionada) {}

    public record ClaseGeneracion(
            UUID idClase,
            String nombre,
            boolean seleccionada,
            boolean requerida,
            List<UUID> requeridaPor,
            boolean esPersistente,
            boolean exponerRest,
            String nombreTabla,
            String nombreEntidad,
            List<OperacionGeneracion> operaciones) {}

    public record MapeoTipo(
            @NotBlank @Size(max = 180) String tipoUml,
            @NotBlank @Pattern(regexp = "[A-Za-z_$][A-Za-z0-9_$.]*") @Size(max = 255)
                    String tipoJava,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_(), ]*") @Size(max = 120)
                    String tipoPostgresql,
            @Pattern(regexp = "[A-Za-z_$][A-Za-z0-9_$.]*") @Size(max = 255)
                    String claseConversor) {}

    public record Dependencia(
            @NotBlank @Size(max = 255) String idGrupo,
            @NotBlank @Size(max = 255) String idArtefacto,
            @Size(max = 80) String version,
            @Pattern(regexp = "COMPILACION|EJECUCION|PRUEBA|PROVISTO") String ambito,
            boolean obligatoria) {}

    public record MensajeConfiguracion(String nivel, String codigo, String mensaje) {}

    /** Vista completa de una configuración, incluida su instantánea y diagnóstico. */
    public record ConfiguracionGeneracion(
            UUID id,
            UUID idVersionModelo,
            int numeroVersion,
            long revisionVersion,
            boolean versionActual,
            String estadoVersion,
            String estado,
            @NotBlank @Size(max = 180) String nombre,
            @NotBlank @Size(max = 180) String nombreProyecto,
            @NotBlank @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.]*") @Size(max = 255) String idGrupo,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.\\-]+") @Size(max = 255) String idArtefacto,
            @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_.]*") @Size(max = 255) String paqueteBase,
            @NotBlank @Size(max = 60) String versionInicial,
            String versionJava,
            String versionSpringBoot,
            String herramientaMigracion,
            boolean generarRepositorios,
            boolean generarServicios,
            boolean generarControladores,
            boolean generarDtos,
            boolean exponerRest,
            List<VersionGeneracion> versionesDisponibles,
            List<ClaseGeneracion> clases,
            List<MapeoTipo> mapeosTipo,
            List<Dependencia> dependencias,
            List<MensajeConfiguracion> mensajes,
            boolean puedeGenerar) {}

    public record SeleccionOperacion(@NotNull UUID idOperacion, boolean seleccionada) {}

    public record SeleccionClase(
            @NotNull UUID idClase,
            boolean seleccionada,
            boolean esPersistente,
            boolean exponerRest,
            @Pattern(regexp = "[a-z_][a-z0-9_]*") @Size(max = 63) String nombreTabla,
            @Pattern(regexp = "[A-Za-z_$][A-Za-z0-9_$]*") @Size(max = 180) String nombreEntidad,
            List<@Valid SeleccionOperacion> operaciones) {}

    /** Cuerpo del PUT; las listas ausentes conservan defaults seguros de la instantánea. */
    public record ActualizarConfiguracion(
            UUID idVersionModelo,
            @NotBlank @Size(max = 180) String nombre,
            @NotBlank @Size(max = 180) String nombreProyecto,
            @NotBlank @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.]*") @Size(max = 255) String idGrupo,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.\\-]+") @Size(max = 255) String idArtefacto,
            @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_.]*") @Size(max = 255) String paqueteBase,
            @NotBlank @Size(max = 60) String versionInicial,
            @Pattern(regexp = "17|21") String versionJava,
            @Size(max = 30) String versionSpringBoot,
            @Pattern(regexp = "FLYWAY|LIQUIBASE|NINGUNO") String herramientaMigracion,
            boolean generarRepositorios,
            boolean generarServicios,
            boolean generarControladores,
            boolean generarDtos,
            boolean exponerRest,
            List<@Valid SeleccionClase> clases,
            List<@Valid MapeoTipo> mapeosTipo,
            List<@Valid Dependencia> dependencias) {}

    public record SolicitudGeneracion(UUID idConfiguracion) {}

    /** Resultado de una generación exitosa. */
    public record ResultadoGeneracion(
            UUID idGeneracion,
            UUID idConfiguracion,
            UUID idVersionModelo,
            UUID idArchivoSalida,
            String nombreArchivo,
            int totalClases,
            int totalOperaciones,
            int totalArchivosGenerados) {}
}
