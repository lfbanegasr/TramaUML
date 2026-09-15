package bo.edu.uml.proyectos.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

public final class ActividadDto {
    private ActividadDto() {}

    public record Evento(
            String id,
            UUID actor,
            String responsable,
            String categoria,
            String accion,
            String tipoEntidad,
            UUID idEntidad,
            String ocurridoEn,
            UUID idModelo,
            UUID idVersionModelo,
            Long revisionModelo) {}

    public record Pagina(List<Evento> eventos, int pagina, int tamano, long total) {}

    public record Detalle(
            Evento evento, JsonNode valorAnterior, JsonNode valorPosterior, JsonNode detalle) {}
}
