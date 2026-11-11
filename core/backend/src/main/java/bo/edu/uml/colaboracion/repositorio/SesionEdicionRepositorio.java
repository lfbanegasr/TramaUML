package bo.edu.uml.colaboracion.repositorio;

import bo.edu.uml.colaboracion.dto.ColaboracionDto.*;
import bo.edu.uml.compartido.ApiException;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class SesionEdicionRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public SesionEdicionRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ModeloBloqueado(
            UUID modelo,
            UUID proyecto,
            String estadoModelo,
            String estadoProyecto,
            long revision) {}

    public ModeloBloqueado bloquearModelo(UUID modelo) {
        var filas =
                jdbc.query(
                        """
                        select m.id,m.id_proyecto,m.estado estado_modelo,p.estado estado_proyecto,m.revision_actual
                        from uml_colaborativo.modelo_uml m join uml_colaborativo.proyecto p on p.id=m.id_proyecto
                        where m.id=:modelo for share of p nowait
                        """,
                        Map.of("modelo", modelo),
                        (r, n) ->
                                new ModeloBloqueado(
                                        r.getObject("id", UUID.class),
                                        r.getObject("id_proyecto", UUID.class),
                                        r.getString("estado_modelo"),
                                        r.getString("estado_proyecto"),
                                        r.getLong("revision_actual")));
        if (filas.isEmpty()) throw new ApiException(404, "Modelo no disponible.");
        jdbc.query(
                "select id from uml_colaborativo.modelo_uml where id=:modelo for update",
                Map.of("modelo", modelo),
                (r, n) -> r.getObject(1, UUID.class));
        return filas.getFirst();
    }

    public ModeloBloqueado modelo(UUID modelo) {
        return jdbc
                .query(
                        """
                        select m.id,m.id_proyecto,m.estado estado_modelo,p.estado estado_proyecto,m.revision_actual
                        from uml_colaborativo.modelo_uml m join uml_colaborativo.proyecto p on p.id=m.id_proyecto
                        where m.id=:modelo
                        """,
                        Map.of("modelo", modelo),
                        (r, n) ->
                                new ModeloBloqueado(
                                        r.getObject("id", UUID.class),
                                        r.getObject("id_proyecto", UUID.class),
                                        r.getString("estado_modelo"),
                                        r.getString("estado_proyecto"),
                                        r.getLong("revision_actual")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Modelo no disponible."));
    }

    public Optional<UUID> sesionActiva(UUID modelo) {
        var filas =
                jdbc.query(
                        """
                        select id from uml_colaborativo.sesion_edicion_modelo
                        where id_modelo=:modelo and estado='ACTIVO' order by abierto_en,id for update
                        """,
                        Map.of("modelo", modelo),
                        (r, n) -> r.getObject("id", UUID.class));
        if (filas.size() > 1)
            throw new ApiException(
                    409, "El modelo tiene más de una sesión activa y requiere revisión.");
        return filas.stream().findFirst();
    }

    public void crear(UUID sesion, ModeloBloqueado modelo, UUID actor) {
        jdbc.update(
                """
                insert into uml_colaborativo.sesion_edicion_modelo(id,id_modelo,abierto_por,modo,revision_base)
                values(:sesion,:modelo,:actor,'SOLO_LECTURA',:revision)
                """,
                Map.of(
                        "sesion",
                        sesion,
                        "modelo",
                        modelo.modelo(),
                        "actor",
                        actor,
                        "revision",
                        modelo.revision()));
    }

    public boolean incorporar(UUID sesion, UUID actor, String modo) {
        return jdbc.update(
                        """
                        insert into uml_colaborativo.participante_sesion_modelo
                          (id_sesion_edicion,id_usuario,modo_participante,estado_presencia)
                        values(:sesion,:actor,:modo,'AUSENTE')
                        on conflict(id_sesion_edicion,id_usuario) do update set
                          modo_participante=excluded.modo_participante,
                          estado_presencia=case
                            when participante_sesion_modelo.estado_presencia='DESCONECTADO' then 'AUSENTE'
                            else participante_sesion_modelo.estado_presencia end,
                          ultima_presencia_en=clock_timestamp(),
                          salida_en=case
                            when participante_sesion_modelo.estado_presencia='DESCONECTADO' then null
                            else participante_sesion_modelo.salida_en end
                        where participante_sesion_modelo.modo_participante<>excluded.modo_participante
                           or participante_sesion_modelo.estado_presencia='DESCONECTADO'
                        """,
                        Map.of("sesion", sesion, "actor", actor, "modo", modo))
                > 0;
    }

    public void verificarParticipante(UUID sesion, UUID modelo, UUID actor, boolean exigirEdicion) {
        var filas =
                jdbc.queryForList(
                        """
                        select s.estado,s.modo,ps.modo_participante,ps.estado_presencia,ps.salida_en
                        from uml_colaborativo.sesion_edicion_modelo s
                        join uml_colaborativo.participante_sesion_modelo ps on ps.id_sesion_edicion=s.id
                        where s.id=:sesion and s.id_modelo=:modelo and ps.id_usuario=:actor
                        """,
                        Map.of("sesion", sesion, "modelo", modelo, "actor", actor));
        if (filas.isEmpty()) throw new ApiException(404, "Sesión de modelado no disponible.");
        var fila = filas.getFirst();
        if (!"ACTIVO".equals(fila.get("estado")) || fila.get("salida_en") != null)
            throw new ApiException(409, "La sesión de modelado ya no está activa.");
        if (exigirEdicion
                && (!"EDITOR".equals(fila.get("modo_participante"))
                        || !"EN_LINEA".equals(fila.get("estado_presencia"))))
            throw new ApiException(
                    409, "Conecta una sesión colaborativa editable antes de guardar.");
    }

    public void conectar(UUID sesion, UUID actor, String modo) {
        int filas =
                jdbc.update(
                        """
                        update uml_colaborativo.participante_sesion_modelo ps
                        set modo_participante=:modo,estado_presencia='EN_LINEA',ultima_presencia_en=clock_timestamp(),salida_en=null
                        from uml_colaborativo.sesion_edicion_modelo s
                        where ps.id_sesion_edicion=:sesion and ps.id_usuario=:actor
                          and s.id=ps.id_sesion_edicion and s.estado='ACTIVO'
                        """,
                        Map.of("sesion", sesion, "actor", actor, "modo", modo));
        if (filas != 1) throw new ApiException(409, "La sesión de modelado ya no está activa.");
        if ("EDITOR".equals(modo))
            jdbc.update(
                    """
                    update uml_colaborativo.sesion_edicion_modelo
                    set modo='EDICION',ultima_actividad_en=clock_timestamp()
                    where id=:sesion and estado='ACTIVO'
                    """,
                    Map.of("sesion", sesion));
    }

    public void actualizarModo(UUID sesion, UUID actor, String modo) {
        int filas =
                jdbc.update(
                        """
                        update uml_colaborativo.participante_sesion_modelo ps
                        set modo_participante=:modo,ultima_presencia_en=clock_timestamp()
                        from uml_colaborativo.sesion_edicion_modelo s
                        where ps.id_sesion_edicion=:sesion and ps.id_usuario=:actor
                          and ps.salida_en is null and s.id=ps.id_sesion_edicion and s.estado='ACTIVO'
                        """,
                        Map.of("sesion", sesion, "actor", actor, "modo", modo));
        if (filas != 1) throw new ApiException(409, "La sesión de modelado ya no está activa.");
        recalcularModo(sesion);
    }

    public void pulso(UUID sesion, UUID actor) {
        int filas =
                jdbc.update(
                        """
                        update uml_colaborativo.participante_sesion_modelo ps
                        set estado_presencia='EN_LINEA',ultima_presencia_en=clock_timestamp()
                        from uml_colaborativo.sesion_edicion_modelo s
                        where ps.id_sesion_edicion=:sesion and ps.id_usuario=:actor and ps.salida_en is null
                          and s.id=ps.id_sesion_edicion and s.estado='ACTIVO'
                        """,
                        Map.of("sesion", sesion, "actor", actor));
        if (filas != 1) throw new ApiException(409, "La sesión de modelado ya no está activa.");
    }

    public void enfocar(UUID sesion, UUID actor, String tipo, UUID entidad) {
        int filas =
                jdbc.update(
                        """
                        update uml_colaborativo.participante_sesion_modelo ps
                        set tipo_entidad_enfocada=:tipo,id_entidad_enfocada=:entidad,
                            estado_presencia='EN_LINEA',ultima_presencia_en=clock_timestamp()
                        from uml_colaborativo.sesion_edicion_modelo s
                        where ps.id_sesion_edicion=:sesion and ps.id_usuario=:actor and ps.salida_en is null
                          and s.id=ps.id_sesion_edicion and s.estado='ACTIVO'
                        """,
                        new MapSqlParameterSource()
                                .addValue("sesion", sesion)
                                .addValue("actor", actor)
                                .addValue("tipo", tipo)
                                .addValue("entidad", entidad));
        if (filas != 1) throw new ApiException(409, "La sesión de modelado ya no está activa.");
    }

    public boolean entidadPertenece(UUID modelo, String tipo, UUID entidad) {
        String sql =
                switch (tipo) {
                    case "CLASE" ->
                            "select exists(select 1 from uml_colaborativo.clase_uml where id=:id"
                                    + " and id_modelo=:modelo and eliminado_en is null)";
                    case "RELACION" ->
                            "select exists(select 1 from uml_colaborativo.relacion_uml where id=:id"
                                    + " and id_modelo=:modelo and eliminado_en is null)";
                    case "PAQUETE" ->
                            "select exists(select 1 from uml_colaborativo.paquete_uml where id=:id"
                                    + " and id_modelo=:modelo and eliminado_en is null)";
                    case "ATRIBUTO" ->
                            "select exists(select 1 from uml_colaborativo.atributo_uml a join"
                                + " uml_colaborativo.clase_uml c on c.id=a.id_clase where a.id=:id"
                                + " and c.id_modelo=:modelo and a.eliminado_en is null and"
                                + " c.eliminado_en is null)";
                    case "OPERACION" ->
                            "select exists(select 1 from uml_colaborativo.operacion_uml o join"
                                + " uml_colaborativo.clase_uml c on c.id=o.id_clase where o.id=:id"
                                + " and c.id_modelo=:modelo and o.eliminado_en is null and"
                                + " c.eliminado_en is null)";
                    default ->
                            throw new ApiException(400, "El tipo de elemento enfocado no existe.");
                };
        return Boolean.TRUE.equals(
                jdbc.queryForObject(sql, Map.of("id", entidad, "modelo", modelo), Boolean.class));
    }

    public void ausentar(UUID sesion, UUID actor) {
        jdbc.update(
                """
                update uml_colaborativo.participante_sesion_modelo
                set estado_presencia='AUSENTE',ultima_presencia_en=clock_timestamp(),
                    tipo_entidad_enfocada=null,id_entidad_enfocada=null
                where id_sesion_edicion=:sesion and id_usuario=:actor
                  and estado_presencia='EN_LINEA' and salida_en is null
                """,
                Map.of("sesion", sesion, "actor", actor));
    }

    public boolean salir(UUID sesion, UUID actor) {
        int filas =
                jdbc.update(
                        """
                        update uml_colaborativo.participante_sesion_modelo
                        set estado_presencia='DESCONECTADO',ultima_presencia_en=clock_timestamp(),
                            salida_en=clock_timestamp(),tipo_entidad_enfocada=null,id_entidad_enfocada=null
                        where id_sesion_edicion=:sesion and id_usuario=:actor and salida_en is null
                        """,
                        Map.of("sesion", sesion, "actor", actor));
        if (filas == 0) return false;
        long presentes =
                jdbc.queryForObject(
                        """
                        select count(*) from uml_colaborativo.participante_sesion_modelo
                        where id_sesion_edicion=:sesion and salida_en is null
                          and estado_presencia in ('EN_LINEA','AUSENTE')
                        """,
                        Map.of("sesion", sesion),
                        Long.class);
        if (presentes == 0)
            jdbc.update(
                    """
                    update uml_colaborativo.sesion_edicion_modelo
                    set estado='CERRADO',cerrado_en=clock_timestamp(),ultima_actividad_en=clock_timestamp()
                    where id=:sesion and estado='ACTIVO'
                    """,
                    Map.of("sesion", sesion));
        else recalcularModo(sesion);
        return true;
    }

    public void tocar(UUID sesion) {
        jdbc.update(
                "update uml_colaborativo.sesion_edicion_modelo set"
                    + " ultima_actividad_en=clock_timestamp() where id=:sesion and estado='ACTIVO'",
                Map.of("sesion", sesion));
    }

    private void recalcularModo(UUID sesion) {
        jdbc.update(
                """
                update uml_colaborativo.sesion_edicion_modelo s set
                  modo=case when exists(
                    select 1 from uml_colaborativo.participante_sesion_modelo ps
                    where ps.id_sesion_edicion=s.id and ps.salida_en is null
                      and ps.modo_participante='EDITOR' and ps.estado_presencia in ('EN_LINEA','AUSENTE')
                  ) then 'EDICION' else 'SOLO_LECTURA' end,
                  ultima_actividad_en=clock_timestamp()
                where s.id=:sesion and s.estado='ACTIVO'
                """,
                Map.of("sesion", sesion));
    }

    public Sesion obtener(UUID sesion, UUID actor) {
        var datos =
                jdbc
                        .query(
                                """
                                select s.id,s.id_modelo,s.modo,s.estado,s.revision_base,ps.modo_participante
                                from uml_colaborativo.sesion_edicion_modelo s
                                join uml_colaborativo.participante_sesion_modelo ps on ps.id_sesion_edicion=s.id
                                where s.id=:sesion and ps.id_usuario=:actor
                                """,
                                Map.of("sesion", sesion, "actor", actor),
                                (r, n) ->
                                        new Sesion(
                                                r.getObject("id", UUID.class),
                                                r.getObject("id_modelo", UUID.class),
                                                r.getString("modo"),
                                                r.getString("estado"),
                                                r.getLong("revision_base"),
                                                r.getString("modo_participante"),
                                                participantes(sesion)))
                        .stream()
                        .findFirst();
        return datos.orElseThrow(() -> new ApiException(404, "Sesión de modelado no disponible."));
    }

    private List<Participante> participantes(UUID sesion) {
        return jdbc.query(
                """
                select ps.id_usuario,u.nombre_completo,
                  coalesce(string_agg(distinct r.nombre,', ' order by r.nombre)
                    filter(where r.estado='ACTIVO'),
                    case when bool_or(gr.codigo='SUPERADMINISTRADOR') then 'Administrador global' end,
                    'Sin rol vigente') rol,
                  ps.modo_participante,ps.estado_presencia,ps.tipo_entidad_enfocada,ps.id_entidad_enfocada
                from uml_colaborativo.participante_sesion_modelo ps
                join uml_colaborativo.sesion_edicion_modelo s on s.id=ps.id_sesion_edicion
                join uml_colaborativo.modelo_uml mo on mo.id=s.id_modelo
                join uml_colaborativo.usuario u on u.id=ps.id_usuario
                left join uml_colaborativo.miembro_proyecto mp on mp.id_proyecto=mo.id_proyecto
                  and mp.id_usuario=ps.id_usuario and mp.estado='ACTIVO'
                left join uml_colaborativo.rol_miembro_proyecto mr on mr.id_miembro_proyecto=mp.id
                  and mr.revocado_en is null
                left join uml_colaborativo.rol r on r.id=mr.id_rol
                left join uml_colaborativo.usuario_rol ur on ur.id_usuario=ps.id_usuario and ur.revocado_en is null
                left join uml_colaborativo.rol gr on gr.id=ur.id_rol and gr.estado='ACTIVO'
                where ps.id_sesion_edicion=:sesion and ps.salida_en is null
                group by ps.id_usuario,u.nombre_completo,ps.modo_participante,ps.estado_presencia,
                  ps.tipo_entidad_enfocada,ps.id_entidad_enfocada,ps.incorporado_en
                order by ps.incorporado_en,ps.id_usuario
                """,
                Map.of("sesion", sesion),
                (r, n) ->
                        new Participante(
                                r.getObject("id_usuario", UUID.class),
                                r.getString("nombre_completo"),
                                r.getString("rol"),
                                r.getString("modo_participante"),
                                r.getString("estado_presencia"),
                                r.getString("tipo_entidad_enfocada"),
                                r.getObject("id_entidad_enfocada", UUID.class)));
    }
}
