package bo.edu.uml.validacion.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio.Cabecera;
import bo.edu.uml.validacion.modelo.MotorSemantico.*;

import com.fasterxml.jackson.databind.*;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ValidacionRepositorio {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ValidacionRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public UUID crear(Cabecera c, UUID actor) {
        if (c.version() == null)
            throw new ApiException(422, "Primero guarda una versión del modelo.");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.validacion_modelo(id,id_modelo,id_version_modelo,revision_validada,solicitado_por,estado)"
                    + " values(:id,:modelo,:version,:revision,:actor,'EN_EJECUCION')",
                Map.of(
                        "id",
                        id,
                        "modelo",
                        c.id(),
                        "version",
                        c.version(),
                        "revision",
                        c.revision(),
                        "actor",
                        actor));
        return id;
    }

    public List<UUID> pendientes() {
        return jdbc.queryForList(
                "select id from uml_colaborativo.validacion_modelo where estado='EN_EJECUCION'"
                        + " order by iniciado_en,id limit 6",
                Map.of(),
                UUID.class);
    }

    public record Trabajo(
            UUID id,
            UUID modelo,
            UUID version,
            long revision,
            JsonNode instantanea,
            JsonNode reglas) {}

    public Trabajo trabajo(UUID id) {
        var fila =
                jdbc.queryForMap(
                        "select"
                            + " v.id_modelo,v.id_version_modelo,v.revision_validada,m.instantanea::text"
                            + " instantanea from uml_colaborativo.validacion_modelo v join"
                            + " uml_colaborativo.version_modelo_uml m on m.id=v.id_version_modelo"
                            + " where v.id=:id",
                        Map.of("id", id));
        try {
            JsonNode reglas =
                    json.valueToTree(
                            jdbc.queryForList(
                                    "select id,codigo::text codigo,severidad,clave_implementacion"
                                            + " from uml_colaborativo.regla_validacion where"
                                            + " esta_activo order by codigo",
                                    Map.of()));
            String instantanea = (String) fila.get("instantanea");
            if (instantanea.length() > 10000000)
                throw new ApiException(
                        413, "El modelo supera el límite de esta fase de validación.");
            return new Trabajo(
                    id,
                    (UUID) fila.get("id_modelo"),
                    (UUID) fila.get("id_version_modelo"),
                    ((Number) fila.get("revision_validada")).longValue(),
                    json.readTree(instantanea),
                    reglas);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Instantánea no interpretable.", e);
        }
    }

    public Map<String, Object> informe(UUID modelo, UUID id) {
        var p = new MapSqlParameterSource().addValue("modelo", modelo).addValue("id", id);
        String where = id == null ? "" : " and v.id=:id";
        var filas =
                jdbc.queryForList(
                        """
                        select v.id,v.id_version_modelo version,v.revision_validada revision,
                        case when v.estado not in ('EN_EJECUCION','FALLIDO') and v.revision_validada<>m.revision_actual then 'DESACTUALIZADO' else v.estado end estado,
                        v.estado estado_resultado,v.cantidad_errores errores,v.cantidad_advertencias advertencias,
                        v.iniciado_en::text iniciado,v.completado_en::text completado,v.resumen::text resumen_json
                        from uml_colaborativo.validacion_modelo v join uml_colaborativo.modelo_uml m on m.id=v.id_modelo
                        where v.id_modelo=:modelo
                        """
                                + where
                                + " order by v.iniciado_en desc,v.id desc limit 1",
                        p);
        if (filas.isEmpty()) return Map.of("estado", "SIN_VALIDAR", "hallazgos", List.of());
        var fila = filas.getFirst();
        fila.put(
                "hallazgos",
                jdbc.queryForList(
                        """
                        select h.id,h.severidad,h.tipo_entidad entidad,h.id_entidad elemento,h.mensaje,h.recomendacion,h.ubicacion::text ubicacion_json,
                        coalesce(r.codigo::text,h.ubicacion->>'regla') regla
                        from uml_colaborativo.hallazgo_validacion_modelo h left join uml_colaborativo.regla_validacion r on r.id=h.id_regla
                        where h.id_validacion=:validacion order by case h.severidad when 'ERROR' then 0 when 'ADVERTENCIA' then 1 else 2 end,h.id
                        """,
                        Map.of("validacion", fila.get("id"))));
        return fila;
    }

    public void completar(Trabajo t, Resultado resultado) {
        var p =
                new MapSqlParameterSource()
                        .addValue("id", t.id())
                        .addValue("modelo", t.modelo())
                        .addValue("version", t.version());
        String estado =
                jdbc.queryForObject(
                        "select estado from uml_colaborativo.validacion_modelo where id=:id for"
                                + " update",
                        p,
                        String.class);
        if (!"EN_EJECUCION".equals(estado)) return;
        long actual =
                jdbc.queryForObject(
                        "select revision_actual from uml_colaborativo.modelo_uml where id=:modelo"
                                + " for update",
                        p,
                        Long.class);
        int
                errores =
                        (int)
                                resultado.hallazgos().stream()
                                        .filter(h -> h.severidad().equals("ERROR"))
                                        .count(),
                advertencias =
                        (int)
                                resultado.hallazgos().stream()
                                        .filter(h -> h.severidad().equals("ADVERTENCIA"))
                                        .count();
        String conclusion =
                resultado.incompleto()
                        ? "FALLIDO"
                        : errores > 0
                                ? "INVALIDO"
                                : advertencias > 0 ? "VALIDO_CON_ADVERTENCIAS" : "VALIDO";
        String finalEstado =
                actual != t.revision() && !resultado.incompleto() ? "DESACTUALIZADO" : conclusion;
        for (Hallazgo h : resultado.hallazgos()) {
            var ubicacion = json.createObjectNode().put("regla", h.codigo());
            if (h.clase() != null) ubicacion.put("clase", h.clase().toString());
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.hallazgo_validacion_modelo(id_validacion,id_regla,severidad,tipo_entidad,id_entidad,mensaje,recomendacion,ubicacion)"
                        + " values(:id,:regla,:severidad,:entidad,:elemento,:mensaje,:recomendacion,cast(:ubicacion"
                        + " as jsonb))",
                    p.addValue("regla", h.regla())
                            .addValue("severidad", h.severidad())
                            .addValue("entidad", h.entidad())
                            .addValue("elemento", h.id())
                            .addValue("mensaje", h.mensaje())
                            .addValue("recomendacion", h.recomendacion())
                            .addValue("ubicacion", ubicacion.toString()));
        }
        p.addValue("estado", finalEstado)
                .addValue("errores", errores)
                .addValue("advertencias", advertencias)
                .addValue(
                        "resumen",
                        json.createObjectNode()
                                .put("motor", "uml-semantico-1")
                                .put("resultado", conclusion)
                                .set("reglasEvaluadas", t.reglas())
                                .toString());
        jdbc.update(
                "update uml_colaborativo.validacion_modelo set"
                    + " estado=:estado,cantidad_errores=:errores,cantidad_advertencias=:advertencias,completado_en=clock_timestamp(),resumen=cast(:resumen"
                    + " as jsonb) where id=:id",
                p);
        // Solo metadatos de calidad; la instantánea y los punteros/revisiones son inmutables para
        // la aplicación.
        jdbc.update(
                """
                update uml_colaborativo.version_modelo_uml set estado_validacion=:calidad where id=:version
                and not exists(select 1 from uml_colaborativo.validacion_modelo posterior,uml_colaborativo.validacion_modelo actual
                  where actual.id=:id and posterior.id_version_modelo=:version and posterior.iniciado_en>actual.iniciado_en and posterior.estado<>'EN_EJECUCION')
                """,
                p.addValue("calidad", resultado.incompleto() ? "SIN_VALIDAR" : conclusion));
    }

    public void fallar(UUID id) {
        jdbc.update(
                "update uml_colaborativo.validacion_modelo set"
                    + " estado='FALLIDO',completado_en=clock_timestamp(),resumen='{\"motor\":\"uml-semantico-1\",\"error\":\"No"
                    + " se pudo procesar el informe\"}'::jsonb where id=:id and"
                    + " estado='EN_EJECUCION'",
                Map.of("id", id));
    }
}
