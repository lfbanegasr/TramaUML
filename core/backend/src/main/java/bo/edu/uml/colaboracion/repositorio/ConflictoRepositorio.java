package bo.edu.uml.colaboracion.repositorio;

import bo.edu.uml.colaboracion.dto.ConflictoDto.*;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;

import com.fasterxml.jackson.databind.*;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.*;

@Repository
public class ConflictoRepositorio {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ConflictoRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Fila(
            UUID id,
            UUID modelo,
            String tipoEntidad,
            UUID idEntidad,
            long revisionBase,
            String estado,
            String tipoResolucion,
            String detectadoEn,
            UUID resueltoPor,
            String nombreResolutor,
            String resueltoEn,
            UUID solicitudResolucion,
            UUID cambioResolucion,
            JsonNode valorAlmacenado,
            JsonNode valorEntrante,
            JsonNode valorResolucion,
            JsonNode parcheEntrante,
            CambioRelacionado cambioAlmacenado,
            CambioRelacionado cambioEntrante) {}

    private static final String SELECT =
            """
            select f.id,f.id_modelo,f.tipo_entidad,f.id_entidad,f.revision_base,f.estado,
              f.tipo_resolucion,f.detectado_en::text,f.resuelto_por,ur.nombre_completo nombre_resolutor,
              f.resuelto_en::text,f.id_solicitud_resolucion,f.id_cambio_resolucion,
              f.valor_almacenado::text,f.valor_entrante::text,f.valor_resolucion::text,
              ce.parche::text parche_entrante,
              ca.id cambio_almacenado,ca.id_cambio_cliente cliente_almacenado,
              ca.id_usuario_actor actor_almacenado,ua.nombre_completo nombre_almacenado,
              ca.revision_base base_almacenado,ca.revision_aplicada aplicada_almacenado,ca.creado_en::text creado_almacenado,
              ce.id cambio_entrante,ce.id_cambio_cliente cliente_entrante,
              ce.id_usuario_actor actor_entrante,ue.nombre_completo nombre_entrante,
              ce.revision_base base_entrante,ce.revision_aplicada aplicada_entrante,ce.creado_en::text creado_entrante
            from uml_colaborativo.conflicto_edicion f
            left join uml_colaborativo.cambio_modelo ca on ca.id=f.id_cambio_almacenado
            left join uml_colaborativo.usuario ua on ua.id=ca.id_usuario_actor
            left join uml_colaborativo.cambio_modelo ce on ce.id=f.id_cambio_entrante
            left join uml_colaborativo.usuario ue on ue.id=ce.id_usuario_actor
            left join uml_colaborativo.usuario ur on ur.id=f.resuelto_por
            """;

    public List<Fila> listar(UUID modelo, String estado) {
        return jdbc.query(
                SELECT
                        + " where f.id_modelo=:modelo and (:estado='' or f.estado=:estado)"
                        + " order by case when f.estado='ABIERTO' then 0 else 1 end,"
                        + " f.detectado_en desc,f.id desc",
                Map.of("modelo", modelo, "estado", estado),
                this::fila);
    }

    public Fila buscar(UUID modelo, UUID id, boolean bloquear) {
        String cierre = bloquear ? " for update of f" : "";
        return jdbc
                .query(
                        SELECT + " where f.id_modelo=:modelo and f.id=:id" + cierre,
                        Map.of("modelo", modelo, "id", id),
                        this::fila)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Conflicto no disponible."));
    }

    public Optional<Fila> porSolicitud(UUID solicitud) {
        return jdbc
                .query(
                        SELECT + " where f.id_solicitud_resolucion=:solicitud",
                        Map.of("solicitud", solicitud),
                        this::fila)
                .stream()
                .findFirst();
    }

    public long bloquearRevision(UUID modelo) {
        var filas =
                jdbc.queryForList(
                        "select revision_actual from uml_colaborativo.modelo_uml where id=:modelo"
                                + " for update",
                        Map.of("modelo", modelo));
        if (filas.isEmpty()) throw new ApiException(404, "Modelo no disponible.");
        return ((Number) filas.getFirst().get("revision_actual")).longValue();
    }

    public JsonNode instantaneaActual(UUID modelo) {
        return leer(
                jdbc.queryForObject(
                        "select uml_colaborativo.construir_instantanea_modelo(:modelo)::text",
                        Map.of("modelo", modelo),
                        String.class));
    }

    public JsonNode instantanea(UUID modelo, long revision) {
        if (revision == 0) {
            var vacia = json.createObjectNode();
            vacia.put("formato_version", 2);
            for (String grupo :
                    List.of(
                            "paquetes",
                            "clases",
                            "atributos",
                            "operaciones",
                            "parametros",
                            "relaciones",
                            "posiciones_clases",
                            "posiciones_relaciones")) vacia.putArray(grupo);
            return vacia;
        }
        return jdbc
                .query(
                        "select instantanea::text from uml_colaborativo.version_modelo_uml where"
                                + " id_modelo=:modelo and revision=:revision",
                        Map.of("modelo", modelo, "revision", revision),
                        (r, n) -> leer(r.getString(1)))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        409,
                                        "La revisión base no tiene una versión histórica"
                                                + " disponible para comparar."));
    }

    public JsonNode reglas() {
        return json.valueToTree(
                jdbc.queryForList(
                        "select id,codigo::text codigo,severidad,clave_implementacion from"
                                + " uml_colaborativo.regla_validacion where esta_activo order by"
                                + " codigo",
                        Map.of()));
    }

    public Fila registrar(
            UUID modelo,
            UUID sesion,
            UUID cliente,
            UUID actor,
            long revisionBase,
            List<Accion> acciones,
            String parche,
            String almacenado,
            String entrante) {
        var existente =
                jdbc.query(
                        SELECT
                                + " where ce.id_modelo=:modelo and"
                                + " ce.id_cambio_cliente=:cliente",
                        Map.of("modelo", modelo, "cliente", cliente),
                        this::fila);
        if (!existente.isEmpty()) {
            Fila f = existente.getFirst();
            validarReenvio(f, sesion, actor, revisionBase, leer(parche));
            return f;
        }
        Accion primera = acciones.getFirst();
        UUID cambioEntrante = UUID.randomUUID();
        var p =
                new MapSqlParameterSource()
                        .addValue("modelo", modelo)
                        .addValue("sesion", sesion)
                        .addValue("cliente", cliente)
                        .addValue("actor", actor)
                        .addValue("base", revisionBase)
                        .addValue("operacion", primera.operacion())
                        .addValue(
                                "entidad", acciones.size() == 1 ? primera.entidad() : "MODELO_UML")
                        .addValue("elemento", acciones.size() == 1 ? primera.idEntidad() : null)
                        .addValue("parche", parche)
                        .addValue("cambioEntrante", cambioEntrante)
                        .addValue("almacenado", almacenado)
                        .addValue("entrante", entrante);
        jdbc.update(
                """
                insert into uml_colaborativo.cambio_modelo(id,id_modelo,id_sesion_edicion,id_cambio_cliente,
                  id_usuario_actor,revision_base,tipo_operacion,tipo_entidad,id_entidad,parche,estado)
                values(:cambioEntrante,:modelo,:sesion,:cliente,:actor,:base,:operacion,:entidad,
                  :elemento,cast(:parche as jsonb),'CONFLICTO')
                """,
                p);
        UUID conflicto =
                jdbc.queryForObject(
                        """
                        insert into uml_colaborativo.conflicto_edicion(id_modelo,tipo_entidad,id_entidad,
                          revision_base,id_cambio_almacenado,id_cambio_entrante,valor_almacenado,valor_entrante)
                        values(:modelo,:entidad,:elemento,:base,
                          (select id from uml_colaborativo.cambio_modelo where id_modelo=:modelo
                           and estado='APLICADO' and revision_aplicada>:base order by revision_aplicada desc limit 1),
                          :cambioEntrante,cast(:almacenado as jsonb),cast(:entrante as jsonb)) returning id
                        """,
                        p,
                        UUID.class);
        return buscar(modelo, conflicto, false);
    }

    private void validarReenvio(Fila f, UUID sesion, UUID actor, long base, JsonNode parche) {
        CambioRelacionado c = f.cambioEntrante();
        if (c == null
                || !actor.equals(c.autor().id())
                || c.revisionBase() != base
                || !Objects.equals(f.parcheEntrante(), parche))
            throw new ApiException(
                    409, "El identificador del cambio ya se utilizó con otra solicitud.");
        UUID sesionGuardada =
                jdbc.queryForObject(
                        "select id_sesion_edicion from uml_colaborativo.cambio_modelo where id=:id",
                        Map.of("id", c.id()),
                        UUID.class);
        if (!Objects.equals(sesion, sesionGuardada))
            throw new ApiException(
                    409, "El identificador del cambio ya se utilizó con otra solicitud.");
    }

    public void finalizar(
            UUID conflicto,
            UUID solicitud,
            UUID actor,
            String estado,
            String tipo,
            JsonNode valor,
            UUID cambioResolucion) {
        int n =
                jdbc.update(
                        """
                        update uml_colaborativo.conflicto_edicion set estado=:estado,tipo_resolucion=:tipo,
                          valor_resolucion=cast(:valor as jsonb),resuelto_por=:actor,resuelto_en=clock_timestamp(),
                          id_solicitud_resolucion=:solicitud,id_cambio_resolucion=:cambio
                        where id=:id and estado='ABIERTO'
                        """,
                        new MapSqlParameterSource()
                                .addValue("id", conflicto)
                                .addValue("solicitud", solicitud)
                                .addValue("actor", actor)
                                .addValue("estado", estado)
                                .addValue("tipo", tipo)
                                .addValue("valor", valor == null ? null : valor.toString())
                                .addValue("cambio", cambioResolucion));
        if (n != 1)
            throw new ApiException(409, "El conflicto ya fue atendido; actualiza la comparación.");
    }

    public void rechazarEntrante(UUID cambio) {
        if (cambio != null)
            jdbc.update(
                    "update uml_colaborativo.cambio_modelo set estado='RECHAZADO' where id=:id"
                            + " and estado='CONFLICTO'",
                    Map.of("id", cambio));
    }

    public Long revisionCambio(UUID cambio) {
        if (cambio == null) return null;
        return jdbc.queryForObject(
                "select revision_aplicada from uml_colaborativo.cambio_modelo where id=:id",
                Map.of("id", cambio),
                Long.class);
    }

    public UUID versionCambio(UUID cambio) {
        if (cambio == null) return null;
        return jdbc
                .query(
                        "select v.id from uml_colaborativo.cambio_modelo c join"
                            + " uml_colaborativo.version_modelo_uml v on v.id_modelo=c.id_modelo"
                            + " and v.revision=c.revision_aplicada where c.id=:id",
                        Map.of("id", cambio),
                        (r, n) -> r.getObject(1, UUID.class))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Fila fila(ResultSet r, int n) throws java.sql.SQLException {
        return new Fila(
                r.getObject("id", UUID.class),
                r.getObject("id_modelo", UUID.class),
                r.getString("tipo_entidad"),
                r.getObject("id_entidad", UUID.class),
                r.getLong("revision_base"),
                r.getString("estado"),
                r.getString("tipo_resolucion"),
                r.getString("detectado_en"),
                r.getObject("resuelto_por", UUID.class),
                r.getString("nombre_resolutor"),
                r.getString("resuelto_en"),
                r.getObject("id_solicitud_resolucion", UUID.class),
                r.getObject("id_cambio_resolucion", UUID.class),
                leer(r.getString("valor_almacenado")),
                leer(r.getString("valor_entrante")),
                leer(r.getString("valor_resolucion")),
                leer(r.getString("parche_entrante")),
                cambio(r, "almacenado"),
                cambio(r, "entrante"));
    }

    private CambioRelacionado cambio(ResultSet r, String sufijo) throws java.sql.SQLException {
        UUID id = r.getObject("cambio_" + sufijo, UUID.class);
        if (id == null) return null;
        UUID autor = r.getObject("actor_" + sufijo, UUID.class);
        return new CambioRelacionado(
                id,
                r.getObject("cliente_" + sufijo, UUID.class),
                new Autor(autor, r.getString("nombre_" + sufijo)),
                r.getLong("base_" + sufijo),
                r.getObject("aplicada_" + sufijo, Long.class),
                r.getString("creado_" + sufijo));
    }

    private JsonNode leer(String valor) {
        if (valor == null) return null;
        try {
            return json.readTree(valor);
        } catch (Exception e) {
            throw new IllegalStateException("JSON de conflicto no interpretable.", e);
        }
    }
}
