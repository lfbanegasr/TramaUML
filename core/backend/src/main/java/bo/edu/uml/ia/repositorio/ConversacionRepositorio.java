package bo.edu.uml.ia.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.dto.AgenteDto.*;
import com.fasterxml.jackson.databind.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class ConversacionRepositorio {
    private final NamedParameterJdbcTemplate db;
    private final ObjectMapper json;

    public ConversacionRepositorio(NamedParameterJdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public UUID hilo(UUID modelo, UUID autor) {
        db.update(
                "insert into uml_colaborativo.conversacion_agente(id,id_modelo,creado_por)"
                        + " values(:id,:modelo,:autor) on conflict(id_modelo) do nothing",
                Map.of("id", UUID.randomUUID(), "modelo", modelo, "autor", autor));
        return db.queryForObject(
                "select id from uml_colaborativo.conversacion_agente where id_modelo=:modelo",
                Map.of("modelo", modelo),
                UUID.class);
    }

    public void bloquearHilo(UUID hilo) {
        db.queryForObject(
                "select id from uml_colaborativo.conversacion_agente where id=:id for update",
                Map.of("id", hilo),
                UUID.class);
    }

    public Aceptado recibir(
            UUID hilo,
            UUID autor,
            UUID clave,
            String texto,
            String modalidad,
            UUID archivo,
            String intencion) {
        bloquearHilo(hilo);
        var anterior = db.queryForList(
                "select m.id,m.texto,m.modalidad,e.intencion from uml_colaborativo.mensaje_agente m"
                        + " join uml_colaborativo.ejecucion_agente e on e.id_mensaje=m.id"
                        + " where m.id_conversacion=:h and m.autor=:a and m.clave=:c",
                Map.of("h", hilo, "a", autor, "c", clave));
        UUID id;
        if (!anterior.isEmpty()) {
            var row = anterior.getFirst();
            if (!texto.equals(row.get("texto"))
                    || !modalidad.equals(row.get("modalidad"))
                    || !intencion.equals(row.get("intencion")))
                throw new ApiException(409, "La clave de envío ya corresponde a otra entrada.");
            id = (UUID) row.get("id");
        } else {
            int pendientes = db.queryForObject(
                    "select count(*) from uml_colaborativo.ejecucion_agente e join"
                            + " uml_colaborativo.mensaje_agente m on m.id=e.id_mensaje where"
                            + " m.id_conversacion=:h and e.estado in ('PENDIENTE','PROCESANDO')",
                    Map.of("h", hilo),
                    Integer.class);
            if (pendientes >= 10)
                throw new ApiException(
                        429, "Hay diez solicitudes pendientes en este modelo. Espera a que finalicen.");
            id = UUID.randomUUID();
            db.update(
                    "insert into"
                            + " uml_colaborativo.mensaje_agente(id,id_conversacion,autor,emisor,texto,modalidad,clave,id_archivo)"
                            + " values(:id,:h,:a,'USUARIO',:t,:m,:c,:f)",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("h", hilo)
                            .addValue("a", autor)
                            .addValue("t", texto)
                            .addValue("m", modalidad)
                            .addValue("c", clave)
                            .addValue("f", archivo));
            db.update(
                    "insert into uml_colaborativo.ejecucion_agente(id,id_mensaje,estado,intencion)"
                            + " values(:id,:m,'PENDIENTE',:i)",
                    Map.of("id", UUID.randomUUID(), "m", id, "i", intencion));
        }
        UUID mensajeId = id;
        return new Aceptado(
                db.queryForObject(
                        "select m.*,u.nombre_completo nombre_autor from uml_colaborativo.mensaje_agente m join"
                                + " uml_colaborativo.usuario u on u.id=m.autor where m.id=:id",
                        Map.of("id", mensajeId),
                        this::mensaje),
                db.queryForObject(
                        "select e.*,m.autor,coalesce(u.nombre_completo,'Integrante') nombre_autor,m.texto"
                                + " texto_mensaje,m.modalidad from uml_colaborativo.ejecucion_agente e join"
                                + " uml_colaborativo.mensaje_agente m on m.id=e.id_mensaje left join"
                                + " uml_colaborativo.usuario u on u.id=m.autor where e.id_mensaje=:m",
                        Map.of("m", id),
                        this::ejecucion));
    }

    private Mensaje mensaje(ResultSet r, int n) throws SQLException {
        return new Mensaje(
                r.getObject("id", UUID.class),
                r.getLong("secuencia"),
                r.getObject("autor", UUID.class),
                r.getString("nombre_autor"),
                r.getString("emisor"),
                r.getString("texto"),
                r.getString("extraccion"),
                r.getString("modalidad"),
                r.getObject("id_archivo", UUID.class),
                r.getObject("respuesta_a", UUID.class),
                r.getObject("id_propuesta", UUID.class),
                r.getString("tipo_resultado"),
                nodo(r.getString("fuentes")),
                nodo(r.getString("supuestos")),
                r.getTimestamp("creado_en").toInstant());
    }

    public List<Mensaje> mensajes(UUID hilo, long cursor, int limite) {
        return db.query(
                "select m.*,coalesce(u.nombre_completo,'Asistente UML') nombre_autor from"
                        + " uml_colaborativo.mensaje_agente m left join uml_colaborativo.usuario u on"
                        + " u.id=m.autor where m.id_conversacion=:h and m.secuencia>:c order by m.secuencia"
                        + " limit :l",
                Map.of("h", hilo, "c", cursor, "l", limite),
                this::mensaje);
    }

    public List<Mensaje> recientes(UUID hilo, long corte) {
        var datos = db.query(
                "select m.*,coalesce(u.nombre_completo,'Asistente UML') nombre_autor from"
                        + " uml_colaborativo.mensaje_agente m left join uml_colaborativo.usuario u on"
                        + " u.id=m.autor where m.id_conversacion=:h and (m.secuencia<=:c or"
                        + " (m.emisor='ASISTENTE' and exists(select 1 from uml_colaborativo.mensaje_agente"
                        + " p where p.id=m.respuesta_a and p.secuencia<=:c))) order by m.secuencia desc"
                        + " limit 24",
                Map.of("h", hilo, "c", corte),
                this::mensaje);
        Collections.reverse(datos);
        return datos;
    }

    private Ejecucion ejecucion(ResultSet r, int n) throws SQLException {
        return new Ejecucion(
                r.getObject("id", UUID.class),
                r.getObject("id_mensaje", UUID.class),
                r.getObject("autor", UUID.class),
                r.getString("nombre_autor"),
                r.getString("texto_mensaje"),
                r.getString("modalidad"),
                r.getString("intencion"),
                r.getString("estado"),
                r.getObject("revision_base", Long.class),
                r.getObject("corte_contexto", Long.class),
                r.getString("modo"),
                r.getString("error"),
                nodo(r.getString("resultado")));
    }

    public Ejecucion ejecucion(UUID hilo, UUID id) {
        return db
                .query(
                        "select e.*,m.autor,coalesce(u.nombre_completo,'Integrante') nombre_autor,m.texto"
                                + " texto_mensaje,m.modalidad from uml_colaborativo.ejecucion_agente e join"
                                + " uml_colaborativo.mensaje_agente m on m.id=e.id_mensaje left join"
                                + " uml_colaborativo.usuario u on u.id=m.autor where e.id=:id and"
                                + " m.id_conversacion=:h",
                        Map.of("h", hilo, "id", id),
                        this::ejecucion)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Ejecución no disponible."));
    }

    public List<Ejecucion> activas(UUID hilo) {
        return db.query(
                "select e.*,m.autor,coalesce(u.nombre_completo,'Integrante') nombre_autor,m.texto"
                        + " texto_mensaje,m.modalidad from uml_colaborativo.ejecucion_agente e join"
                        + " uml_colaborativo.mensaje_agente m on m.id=e.id_mensaje left join"
                        + " uml_colaborativo.usuario u on u.id=m.autor where m.id_conversacion=:h order by"
                        + " m.secuencia desc limit 30",
                Map.of("h", hilo),
                this::ejecucion);
    }

    public record Trabajo(
            UUID id,
            UUID token,
            UUID mensaje,
            UUID hilo,
            UUID modelo,
            UUID autor,
            String texto,
            String modalidad,
            UUID archivo,
            String intencion,
            long corte) {
    }

    public Optional<Trabajo> reclamar() {
        // El arrendamiento excede la espera máxima de una llamada y el procesamiento
        // local.
        db.update(
                "update uml_colaborativo.ejecucion_agente set estado=case when intentos<3 then 'PENDIENTE'"
                        + " else 'FALLIDO' end,error='La ejecución fue interrumpida; puede reenviarse con una"
                        + " clave nueva.',token_trabajo=null where estado='PROCESANDO' and"
                        + " iniciado_en<clock_timestamp()-interval '5 minutes'",
                Map.of());
        var filas = db.queryForList(
                """
                        select e.id,m.id mensaje,m.id_conversacion hilo,c.id_modelo,m.autor,m.texto,m.modalidad,m.id_archivo,e.intencion,m.secuencia
                        from uml_colaborativo.ejecucion_agente e join uml_colaborativo.mensaje_agente m on m.id=e.id_mensaje
                        join uml_colaborativo.conversacion_agente c on c.id=m.id_conversacion
                        where e.estado='PENDIENTE' and not exists (
                          select 1 from uml_colaborativo.ejecucion_agente x join uml_colaborativo.mensaje_agente y on y.id=x.id_mensaje
                          where y.id_conversacion=m.id_conversacion and x.estado in ('PENDIENTE','PROCESANDO') and y.secuencia<m.secuencia
                          and (e.intencion<>'CONSULTAR' or x.intencion<>'CONSULTAR'))
                        order by m.secuencia for update of e skip locked limit 1
                        """,
                Map.of());
        if (filas.isEmpty())
            return Optional.empty();
        var r = filas.getFirst();
        UUID id = (UUID) r.get("id"), token = UUID.randomUUID();
        db.update(
                "update uml_colaborativo.ejecucion_agente set"
                        + " estado='PROCESANDO',token_trabajo=:t,intentos=intentos+1,iniciado_en=clock_timestamp(),error=null"
                        + " where id=:id",
                Map.of("t", token, "id", id));
        return Optional.of(
                new Trabajo(
                        id,
                        token,
                        (UUID) r.get("mensaje"),
                        (UUID) r.get("hilo"),
                        (UUID) r.get("id_modelo"),
                        (UUID) r.get("autor"),
                        (String) r.get("texto"),
                        (String) r.get("modalidad"),
                        (UUID) r.get("id_archivo"),
                        (String) r.get("intencion"),
                        ((Number) r.get("secuencia")).longValue()));
    }

    public boolean vigente(Trabajo t) {
        return !db.queryForList(
                "select id from uml_colaborativo.ejecucion_agente where id=:id and token_trabajo=:t and"
                        + " estado='PROCESANDO' for update",
                Map.of("id", t.id(), "t", t.token()))
                .isEmpty();
    }

    public void contexto(Trabajo t, long revision, JsonNode contexto) {
        db.update(
                "update uml_colaborativo.ejecucion_agente set"
                        + " revision_base=:r,corte_contexto=:c,contexto=cast(:j as jsonb) where id=:id and"
                        + " token_trabajo=:t",
                Map.of(
                        "r", revision, "c", t.corte(), "j", contexto.toString(), "id", t.id(), "t", t.token()));
    }

    public void completar(
            Trabajo t,
            String tipo,
            String texto,
            UUID propuesta,
            JsonNode fuentes,
            JsonNode supuestos,
            JsonNode resultado,
            String modo,
            long ms) {
        bloquearHilo(t.hilo());
        db.update(
                "insert into"
                        + " uml_colaborativo.mensaje_agente(id,id_conversacion,emisor,texto,extraccion,modalidad,respuesta_a,id_propuesta,tipo_resultado,fuentes,supuestos)"
                        + " values(:id,:h,'ASISTENTE',:x,:extraccion,:m,:a,:p,:tipo,cast(:f as jsonb),cast(:s"
                        + " as jsonb)) on conflict(respuesta_a) do nothing",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("h", t.hilo())
                        .addValue("x", texto)
                        .addValue("extraccion", resultado.path("extraccionAdjunto").asText(null))
                        .addValue("m", t.modalidad())
                        .addValue("a", t.mensaje())
                        .addValue("p", propuesta)
                        .addValue("tipo", tipo)
                        .addValue("f", fuentes.toString())
                        .addValue("s", supuestos.toString()));
        db.update(
                "update uml_colaborativo.ejecucion_agente set estado='COMPLETADO',resultado=cast(:r as"
                        + " jsonb),modo=:m,duracion_ms=:ms,completado_en=clock_timestamp() where id=:id and"
                        + " token_trabajo=:t",
                Map.of("r", resultado.toString(), "m", modo, "ms", ms, "id", t.id(), "t", t.token()));
    }

    public void fallar(Trabajo t, String error) {
        db.update(
                "update uml_colaborativo.ejecucion_agente set"
                        + " estado='FALLIDO',error=:e,completado_en=clock_timestamp() where id=:id and"
                        + " token_trabajo=:t and estado='PROCESANDO'",
                Map.of("id", t.id(), "t", t.token(), "e", error));
    }

    public void cancelar(UUID hilo, UUID id) {
        db.update(
                "update uml_colaborativo.ejecucion_agente set"
                        + " estado='CANCELADO',completado_en=clock_timestamp() where id=:id and id_mensaje"
                        + " in(select id from uml_colaborativo.mensaje_agente where id_conversacion=:h) and"
                        + " estado in ('PENDIENTE','PROCESANDO')",
                Map.of("id", id, "h", hilo));
    }

    public List<Acuerdo> acuerdos(UUID hilo) {
        return db.query(
                "select a.*,u.nombre_completo,not exists(select 1 from uml_colaborativo.acuerdo_agente b"
                        + " where b.sustituye=a.id) vigente from uml_colaborativo.acuerdo_agente a join"
                        + " uml_colaborativo.usuario u on u.id=a.confirmado_por where a.id_conversacion=:h"
                        + " order by a.confirmado_en,a.id",
                Map.of("h", hilo),
                (r, n) -> new Acuerdo(
                        r.getObject("id", UUID.class),
                        r.getObject("id_mensaje", UUID.class),
                        r.getString("contenido"),
                        r.getObject("confirmado_por", UUID.class),
                        r.getString("nombre_completo"),
                        r.getObject("sustituye", UUID.class),
                        r.getBoolean("vigente"),
                        r.getTimestamp("confirmado_en").toInstant()));
    }

    public void acuerdo(UUID hilo, UUID autor, ConfirmarAcuerdo a) {
        bloquearHilo(hilo);
        if (db.queryForObject(
                "select count(*) from uml_colaborativo.mensaje_agente where id=:id and"
                        + " id_conversacion=:h",
                Map.of("id", a.idMensaje(), "h", hilo),
                Integer.class) != 1)
            throw new ApiException(422, "El mensaje fuente debe pertenecer a esta conversación.");
        if (a.sustituye() != null
                && acuerdos(hilo).stream().noneMatch(x -> x.id().equals(a.sustituye()) && x.vigente()))
            throw new ApiException(409, "El acuerdo que se sustituye no está vigente en este modelo.");
        db.update(
                "insert into"
                        + " uml_colaborativo.acuerdo_agente(id,id_conversacion,id_mensaje,contenido,confirmado_por,sustituye)"
                        + " values(:id,:h,:m,:c,:a,:s)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("h", hilo)
                        .addValue("m", a.idMensaje())
                        .addValue("c", a.contenido().trim())
                        .addValue("a", autor)
                        .addValue("s", a.sustituye()));
    }

    public JsonNode nodo(String texto) {
        try {
            return texto == null ? json.nullNode() : json.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException("Registro de conversación ilegible.", e);
        }
    }
}
