package bo.edu.uml.generacion.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ValidacionBackendDto {
    private ValidacionBackendDto() {}

    public record Hallazgo(
            String severidad,
            String tipoComprobacion,
            String componente,
            String mensaje,
            Map<String, Object> detalle) {
        public static Hallazgo error(String tipo, String mensaje) {
            return new Hallazgo("ERROR", tipo, null, mensaje, Map.of());
        }
    }

    public record Informe(
            UUID id,
            UUID idGeneracion,
            String estado,
            int comprobacionesAprobadas,
            int cantidadAdvertencias,
            int cantidadErrores,
            Instant iniciadoEn,
            Instant completadoEn,
            String sha256,
            String registroCompilacion,
            List<Hallazgo> hallazgos,
            boolean puedeDescargar) {
        public Informe conDescarga(boolean disponible) {
            return new Informe(id, idGeneracion, estado, comprobacionesAprobadas, cantidadAdvertencias,
                    cantidadErrores, iniciadoEn, completadoEn, sha256, registroCompilacion, hallazgos, disponible);
        }
    }

    public record GeneracionDisponible(
            UUID idGeneracion,
            UUID idConfiguracion,
            UUID idVersionModelo,
            int numeroVersion,
            String nombreArchivo,
            Instant creadoEn,
            Informe validacion,
            boolean puedeValidar) {}

    public record Resultado(
            String estado,
            int aprobadas,
            List<Hallazgo> hallazgos,
            String registro,
            String sha256) {}
}
