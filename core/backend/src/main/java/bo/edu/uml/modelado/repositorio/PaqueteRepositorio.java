package bo.edu.uml.modelado.repositorio;

import static bo.edu.uml.modelado.modelo.ReglasElemento.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class PaqueteRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public PaqueteRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void aplicar(Accion a, MapSqlParameterSource p) {
        if (a.operacion().equals("ELIMINAR")) {
            if (a.datos() != null
                    && !a.datos().isNull()
                    && (!a.datos().isObject() || !a.datos().isEmpty()))
                throw invalido("La eliminación no acepta datos adicionales.");
            exigir(
                    jdbc.update(
                            "update uml_colaborativo.paquete_uml set"
                                    + " eliminado_en=clock_timestamp(),actualizado_por=:actor where"
                                    + " id=:id and id_modelo=:modelo and eliminado_en is null",
                            p));
            return;
        }
        if (!Set.of("CREAR", "ACTUALIZAR").contains(a.operacion()))
            throw invalido("Operación de paquete no válida.");
        JsonNode d = a.datos();
        objeto(d, Set.of("nombre", "descripcion", "padre"));
        UUID padre = d.hasNonNull("padre") ? uuid(d, "padre") : null;
        p.addValue("nombre", texto(d, "nombre", 180))
                .addValue("descripcion", opcional(d, "descripcion", 4000))
                .addValue("padre", padre);
        if (padre != null) {
            if (padre.equals(a.idEntidad()))
                throw invalido("Un paquete no puede contenerse a sí mismo.");
            exigir(
                    jdbc.queryForObject(
                            "select count(*) from uml_colaborativo.paquete_uml where id=:padre and"
                                    + " id_modelo=:modelo and eliminado_en is null",
                            p,
                            Integer.class));
        }
        if (a.operacion().equals("CREAR"))
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.paquete_uml(id,id_modelo,nombre,descripcion,id_paquete_padre,creado_por,actualizado_por)"
                        + " values(:id,:modelo,:nombre,:descripcion,:padre,:actor,:actor)",
                    p);
        else
            exigir(
                    jdbc.update(
                            "update uml_colaborativo.paquete_uml set"
                                + " nombre=:nombre,descripcion=:descripcion,id_paquete_padre=:padre,actualizado_por=:actor"
                                + " where id=:id and id_modelo=:modelo and eliminado_en is null",
                            p));
    }

    private void exigir(int n) {
        if (n != 1)
            throw new ApiException(
                    422,
                    "Paquete no disponible en este modelo o con contenido que debe reubicarse.");
    }
}
