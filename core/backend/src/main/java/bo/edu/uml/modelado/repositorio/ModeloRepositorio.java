package bo.edu.uml.modelado.repositorio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.*;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ModeloRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public ModeloRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Cabecera(
            UUID id,
            UUID proyecto,
            String nombre,
            long revision,
            UUID version,
            String estado,
            String estadoProyecto) {}

    public Cabecera cabecera(UUID id) {
        return jdbc
                .query(
                        "select"
                            + " m.id,m.id_proyecto,m.nombre,m.revision_actual,m.id_version_actual,m.estado,p.estado"
                            + " estado_proyecto from uml_colaborativo.modelo_uml m join"
                            + " uml_colaborativo.proyecto p on p.id=m.id_proyecto where m.id=:id",
                        Map.of("id", id),
                        (r, n) ->
                                new Cabecera(
                                        id,
                                        r.getObject("id_proyecto", UUID.class),
                                        r.getString("nombre"),
                                        r.getLong("revision_actual"),
                                        r.getObject("id_version_actual", UUID.class),
                                        r.getString("estado"),
                                        r.getString("estado_proyecto")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Modelo no disponible."));
    }

    public boolean permiso(UUID actor, UUID proyecto, String permiso) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select uml_colaborativo.tiene_permiso_proyecto(:actor,:proyecto,:permiso)",
                        Map.of("actor", actor, "proyecto", proyecto, "permiso", permiso),
                        Boolean.class));
    }

    public UUID paqueteClase(UUID modelo, UUID clase) {
        var filas =
                jdbc.queryForList(
                        "select id_paquete from uml_colaborativo.clase_uml where id=:clase and"
                                + " id_modelo=:modelo and eliminado_en is null",
                        Map.of("clase", clase, "modelo", modelo));
        return filas.isEmpty() ? null : (UUID) filas.getFirst().get("id_paquete");
    }

    public long iniciar(UUID modelo, long base, UUID actor) {
        return jdbc.queryForObject(
                "select uml_colaborativo.iniciar_edicion_modelo(:modelo,:base,:actor)",
                Map.of("modelo", modelo, "base", base, "actor", actor),
                Long.class);
    }

    public UUID guardar(UUID modelo, long revision, UUID actor, String resumen) {
        return jdbc.queryForObject(
                "select uml_colaborativo.guardar_version_modelo(:modelo,:revision,:actor,:resumen)",
                Map.of("modelo", modelo, "revision", revision, "actor", actor, "resumen", resumen),
                UUID.class);
    }

    public void registrar(
            UUID modelo,
            UUID sesion,
            UUID cliente,
            UUID actor,
            long base,
            long revision,
            String tipo,
            String entidad,
            UUID id,
            String parche) {
        jdbc.update(
                """
                insert into uml_colaborativo.cambio_modelo(id_modelo,id_sesion_edicion,id_cambio_cliente,id_usuario_actor,revision_base,
                revision_aplicada,tipo_operacion,tipo_entidad,id_entidad,parche,estado,aplicado_en)
                values(:modelo,:sesion,:cliente,:actor,:base,:revision,:tipo,:entidad,:id,cast(:parche as jsonb),'APLICADO',clock_timestamp())
                """,
                new MapSqlParameterSource()
                        .addValue("modelo", modelo)
                        .addValue("sesion", sesion)
                        .addValue("cliente", cliente)
                        .addValue("actor", actor)
                        .addValue("base", base)
                        .addValue("revision", revision)
                        .addValue("tipo", tipo)
                        .addValue("entidad", entidad)
                        .addValue("id", id)
                        .addValue("parche", parche));
    }

    public Optional<Confirmacion> repeticion(
            UUID modelo, UUID sesion, UUID cliente, UUID actor, long base, String parche) {
        var rows =
                jdbc.query(
                        """
                        select c.id_sesion_edicion,c.id_usuario_actor,c.revision_base,c.revision_aplicada,
                        c.parche=cast(:parche as jsonb) igual,v.id id_version
                        from uml_colaborativo.cambio_modelo c join uml_colaborativo.version_modelo_uml v
                          on v.id_modelo=c.id_modelo and v.revision=c.revision_aplicada
                        where c.id_modelo=:modelo and c.id_cambio_cliente=:cliente and c.estado='APLICADO'
                        """,
                        Map.of("modelo", modelo, "cliente", cliente, "parche", parche),
                        (r, n) -> {
                            if (!Objects.equals(
                                            sesion, r.getObject("id_sesion_edicion", UUID.class))
                                    || !actor.equals(r.getObject("id_usuario_actor", UUID.class))
                                    || base != r.getLong("revision_base")
                                    || !r.getBoolean("igual"))
                                throw new ApiException(
                                        409,
                                        "El identificador del cambio ya se utilizó con otra"
                                                + " solicitud.");
                            return new Confirmacion(
                                    cliente,
                                    r.getLong("revision_aplicada"),
                                    r.getObject("id_version", UUID.class),
                                    true);
                        });
        return rows.stream().findFirst();
    }

    public Modelo leer(Cabecera c, Identidad actor) {
        var p = Map.of("modelo", c.id());
        var clases =
                jdbc.queryForList(
                        """
                        select c.id,c.nombre,c.descripcion,c.estereotipo,c.visibilidad,c.es_abstracto,c.es_persistente,c.id_paquete,
                        coalesce(p.x,0) x,coalesce(p.y,0) y,coalesce(p.ancho,240) ancho,coalesce(p.alto,160) alto
                        from uml_colaborativo.clase_uml c left join uml_colaborativo.posicion_clase_uml p on p.id_clase=c.id
                        where c.id_modelo=:modelo and c.eliminado_en is null order by c.nombre,c.id
                        """,
                        p);
        var atributos =
                jdbc.queryForList(
                        """
                        select a.id,a.id_clase,a.nombre,a.tipo_uml,a.visibilidad,a.multiplicidad_minima,a.multiplicidad_maxima,a.es_identificador,a.es_estatico,a.es_unico,a.valor_predeterminado,a.restricciones_json::text restricciones_json,a.orden
                        from uml_colaborativo.atributo_uml a join uml_colaborativo.clase_uml c on c.id=a.id_clase
                        where c.id_modelo=:modelo and c.eliminado_en is null and a.eliminado_en is null order by a.orden,a.nombre
                        """,
                        p);
        var operaciones =
                jdbc.queryForList(
                        """
                        select o.id,o.id_clase,o.nombre,o.visibilidad,o.tipo_retorno,o.es_estatico,o.es_abstracto,o.orden
                        from uml_colaborativo.operacion_uml o join uml_colaborativo.clase_uml c on c.id=o.id_clase
                        where c.id_modelo=:modelo and c.eliminado_en is null and o.eliminado_en is null order by o.orden,o.nombre
                        """,
                        p);
        var parametros =
                jdbc.queryForList(
                        """
                        select a.id,a.id_operacion,a.nombre,a.tipo_uml,a.direccion,a.es_obligatorio,a.valor_predeterminado,a.orden from uml_colaborativo.parametro_uml a
                        join uml_colaborativo.operacion_uml o on o.id=a.id_operacion join uml_colaborativo.clase_uml c on c.id=o.id_clase
                        where c.id_modelo=:modelo and c.eliminado_en is null and o.eliminado_en is null and a.eliminado_en is null order by a.orden
                        """,
                        p);
        var relaciones =
                jdbc.queryForList(
                        """
                        select id,id_clase_origen,id_clase_destino,nombre,tipo_relacion,direccion,rol_origen,rol_destino,
                        minimo_origen,maximo_origen,minimo_destino,maximo_destino from uml_colaborativo.relacion_uml
                        where id_modelo=:modelo and eliminado_en is null order by id
                        """,
                        p);
        var paquetes =
                jdbc.queryForList(
                        "select id,id_paquete_padre,nombre,descripcion from"
                                + " uml_colaborativo.paquete_uml where id_modelo=:modelo and"
                                + " eliminado_en is null order by nombre",
                        p);
        boolean editable =
                "ACTIVO".equals(c.estadoProyecto())
                        && !"ARCHIVADO".equals(c.estado())
                        && permiso(actor.id(), c.proyecto(), "EDITAR_MODELO");
        return new Modelo(
                c.id(),
                c.proyecto(),
                c.nombre(),
                c.revision(),
                c.version(),
                editable,
                editable && permiso(actor.id(), c.proyecto(), "ORGANIZAR_MODELO"),
                permiso(actor.id(), c.proyecto(), "VALIDAR_MODELO"),
                clases,
                atributos,
                operaciones,
                parametros,
                relaciones,
                paquetes);
    }
}
