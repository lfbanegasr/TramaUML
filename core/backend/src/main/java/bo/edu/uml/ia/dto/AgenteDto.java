package bo.edu.uml.ia.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AgenteDto {
  private AgenteDto() {}

  public record Entrada(
      @NotNull UUID clave,
      @NotBlank @Size(max = 12000) String texto,
      @Pattern(regexp = "AUTO|CONSULTAR|MODIFICAR") String intencion) {}

  public record Mensaje(
      UUID id,
      long secuencia,
      UUID autor,
      String nombreAutor,
      String emisor,
      String texto,
      String extraccion,
      String modalidad,
      UUID idArchivo,
      UUID respuestaA,
      UUID idPropuesta,
      String tipoResultado,
      JsonNode fuentes,
      JsonNode supuestos,
      Instant creadoEn) {}

  public record Ejecucion(
      UUID id,
      UUID idMensaje,
      UUID autor,
      String nombreAutor,
      String textoMensaje,
      String modalidad,
      String intencion,
      String estado,
      Long revisionBase,
      Long corteContexto,
      String modo,
      String error,
      JsonNode resultado) {}

  public record Aceptado(Mensaje mensaje, Ejecucion ejecucion) {}

  public record Historial(
      UUID idConversacion,
      List<Mensaje> mensajes,
      long cursor,
      boolean hayMas,
      List<Ejecucion> ejecuciones,
      List<Acuerdo> acuerdos) {}

  public record Acuerdo(
      UUID id,
      UUID idMensaje,
      String contenido,
      UUID confirmadoPor,
      String nombreAutor,
      UUID sustituye,
      boolean vigente,
      Instant confirmadoEn) {}

  public record ConfirmarAcuerdo(
      @NotNull UUID idMensaje, @NotBlank @Size(max = 2000) String contenido, UUID sustituye) {}
}
