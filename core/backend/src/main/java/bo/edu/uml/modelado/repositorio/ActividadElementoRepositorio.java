package bo.edu.uml.modelado.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ActividadElementoRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public ActividadElementoRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Fragmentos cerrados del servidor; el cliente nunca suministra nombres SQL.
    public String valores(UUID modelo, Accion accion) {
        String consulta =
                switch (accion.entidad()) {
                    case "CLASE" ->
                            "select (to_jsonb(e)||jsonb_build_object('posicion',(select to_jsonb(p)"
                                    + " from uml_colaborativo.posicion_clase_uml p where"
                                    + " p.id_clase=e.id)))::text from uml_colaborativo.clase_uml e"
                                    + " where e.id=:id and e.id_modelo=:modelo";
                    case "ATRIBUTO" ->
                            "select to_jsonb(e)::text from uml_colaborativo.atributo_uml e join"
                                + " uml_colaborativo.clase_uml c on c.id=e.id_clase where e.id=:id"
                                + " and c.id_modelo=:modelo";
                    case "OPERACION" ->
                            "select (to_jsonb(e)||jsonb_build_object('parametros',coalesce((select"
                                + " jsonb_agg(to_jsonb(p) order by p.orden,p.id) from"
                                + " uml_colaborativo.parametro_uml p where p.id_operacion=e.id and"
                                + " p.eliminado_en is null),'[]'::jsonb)))::text from"
                                + " uml_colaborativo.operacion_uml e join"
                                + " uml_colaborativo.clase_uml c on c.id=e.id_clase where e.id=:id"
                                + " and c.id_modelo=:modelo";
                    case "RELACION" ->
                            "select to_jsonb(e)::text from uml_colaborativo.relacion_uml e where"
                                    + " e.id=:id and e.id_modelo=:modelo";
                    case "PAQUETE" ->
                            "select to_jsonb(e)::text from uml_colaborativo.paquete_uml e where"
                                    + " e.id=:id and e.id_modelo=:modelo";
                    default -> throw new ApiException(422, "Elemento no soportado.");
                };
        return jdbc
                .queryForList(
                        consulta, Map.of("id", accion.idEntidad(), "modelo", modelo), String.class)
                .stream()
                .findFirst()
                .orElse(null);
    }
}
