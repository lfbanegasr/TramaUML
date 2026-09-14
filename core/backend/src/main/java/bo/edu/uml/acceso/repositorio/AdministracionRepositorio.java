package bo.edu.uml.acceso.repositorio;

import bo.edu.uml.acceso.dto.AdministracionDto.*;
import bo.edu.uml.compartido.ApiException;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class AdministracionRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public AdministracionRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void bloquearAdministracion() {
        jdbc.queryForObject("select pg_advisory_xact_lock(1920260914)", Map.of(), Object.class);
    }

    public void bloquearLecturaAdministracion() {
        jdbc.queryForObject(
                "select pg_advisory_xact_lock_shared(1920260914)", Map.of(), Object.class);
    }

    public boolean requiereActivacion(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select strpos(hash_contrasena,'{temporal}')=1 from"
                                + " uml_colaborativo.usuario where id=:id",
                        Map.of("id", id),
                        Boolean.class));
    }

    public boolean activa(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists(select 1 from uml_colaborativo.usuario where id=:id and"
                                + " estado='ACTIVO')",
                        Map.of("id", id),
                        Boolean.class));
    }

    private static final String USUARIOS =
            """
            select u.id,u.nombre_usuario::text usuario,u.correo_electronico::text correo,u.nombre_completo nombre,
            u.estado,u.actualizado_en::text revision,u.ultimo_acceso_en,
            coalesce((select jsonb_agg(r.id) from uml_colaborativo.usuario_rol ur join uml_colaborativo.rol r on r.id=ur.id_rol
              where ur.id_usuario=u.id and ur.revocado_en is null),'[]')::text roles_json
            from uml_colaborativo.usuario u
            """;

    public List<Map<String, Object>> usuarios(
            String filtro, String estado, String rol, int pagina) {
        return jdbc.queryForList(
                USUARIOS
                        + """
                        where (:estado='' or u.estado=:estado) and (:filtro='' or strpos(lower(u.nombre_completo),lower(:filtro))>0
                          or strpos(lower(u.correo_electronico::text),lower(:filtro))>0 or strpos(lower(u.nombre_usuario::text),lower(:filtro))>0)
                        and (:rol='' or exists(select 1 from uml_colaborativo.usuario_rol ur join uml_colaborativo.rol r on r.id=ur.id_rol
                          where ur.id_usuario=u.id and ur.revocado_en is null and r.codigo::text=:rol))
                        order by u.nombre_completo,u.id limit 50 offset :offset
                        """,
                Map.of("filtro", filtro, "estado", estado, "rol", rol, "offset", pagina * 50));
    }

    public Map<String, Object> usuario(UUID id) {
        return uno(jdbc.queryForList(USUARIOS + " where u.id=:id", Map.of("id", id)));
    }

    public List<Map<String, Object>> roles() {
        return jdbc.queryForList(
                """
                select r.id,r.codigo::text codigo,r.nombre,r.descripcion,r.ambito,r.estado,r.es_del_sistema,
                r.actualizado_en::text revision,
                (select count(*) from uml_colaborativo.usuario_rol ur where ur.id_rol=r.id and ur.revocado_en is null)
                  +(select count(*) from uml_colaborativo.rol_miembro_proyecto mr where mr.id_rol=r.id and mr.revocado_en is null) asignaciones,
                coalesce((select jsonb_agg(id_permiso) from uml_colaborativo.rol_permiso where id_rol=r.id),'[]')::text permisos_json
                from uml_colaborativo.rol r order by r.nombre
                """,
                Map.of());
    }

    public Map<String, Object> rol(UUID id) {
        return roles().stream()
                .filter(r -> id.equals(r.get("id")))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Rol no disponible."));
    }

    public List<Map<String, Object>> permisos() {
        return jdbc.queryForList(
                "select id,codigo::text codigo,modulo,accion,descripcion,ambito from"
                        + " uml_colaborativo.permiso order by modulo,codigo",
                Map.of());
    }

    public List<Map<String, Object>> permisosDelRol(UUID id) {
        return jdbc.queryForList(
                "select p.id,p.codigo::text codigo,p.ambito from uml_colaborativo.rol_permiso rp"
                    + " join uml_colaborativo.permiso p on p.id=rp.id_permiso where rp.id_rol=:id",
                Map.of("id", id));
    }

    public boolean administrador(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        select exists(select 1 from uml_colaborativo.usuario_rol ur join uml_colaborativo.rol r on r.id=ur.id_rol
                        where ur.id_usuario=:id and ur.revocado_en is null and r.codigo='SUPERADMINISTRADOR' and r.estado='ACTIVO')
                        """,
                        Map.of("id", id),
                        Boolean.class));
    }

    public long administradoresActivos() {
        return jdbc.queryForObject(
                """
                select count(distinct u.id) from uml_colaborativo.usuario u join uml_colaborativo.usuario_rol ur on ur.id_usuario=u.id
                join uml_colaborativo.rol r on r.id=ur.id_rol where u.estado='ACTIVO' and ur.revocado_en is null
                and r.codigo='SUPERADMINISTRADOR' and r.estado='ACTIVO'
                """,
                Map.of(),
                Long.class);
    }

    public void guardarUsuario(UUID id, UUID actor, Usuario d, String hash, boolean nuevo) {
        var p =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("actor", actor)
                        .addValue("usuario", d.usuario().trim())
                        .addValue("correo", d.correo().trim())
                        .addValue("nombre", d.nombre().trim())
                        .addValue("estado", nuevo ? "PENDIENTE" : d.estado())
                        .addValue("hash", hash)
                        .addValue("revision", d.revision());
        if (nuevo)
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.usuario(id,nombre_usuario,correo_electronico,nombre_completo,hash_contrasena,estado)"
                        + " values(:id,:usuario,:correo,:nombre,:hash,'PENDIENTE')",
                    p);
        else
            exigir(
                    jdbc.update(
                            """
                            update uml_colaborativo.usuario set nombre_usuario=:usuario,correo_electronico=:correo,nombre_completo=:nombre,estado=:estado,
                            desactivado_en=case when :estado='INACTIVO' then clock_timestamp() else null end,
                            intentos_fallidos_acceso=case when :estado='ACTIVO' then 0 else intentos_fallidos_acceso end,
                            bloqueado_hasta=case when :estado='ACTIVO' then null else bloqueado_hasta end
                            where id=:id and actualizado_en=cast(:revision as timestamptz)
                            """,
                            p));
        jdbc.update(
                "update uml_colaborativo.usuario_rol set revocado_en=clock_timestamp() where"
                        + " id_usuario=:id and revocado_en is null",
                p);
        for (UUID rol : d.roles())
            jdbc.update(
                    "insert into uml_colaborativo.usuario_rol(id_usuario,id_rol,asignado_por)"
                            + " values(:id,:rol,:actor)",
                    p.addValue("rol", rol));
    }

    public void revocarSesiones(UUID id) {
        jdbc.update(
                "update uml_colaborativo.sesion_autenticacion set"
                        + " revocado_en=clock_timestamp(),motivo_revocacion='ADMINISTRACION' where"
                        + " id_usuario=:id and revocado_en is null",
                Map.of("id", id));
    }

    public void restablecer(UUID id, String revision, String hash) {
        exigir(
                jdbc.update(
                        """
                        update uml_colaborativo.usuario set hash_contrasena=:hash,estado='PENDIENTE',intentos_fallidos_acceso=0,bloqueado_hasta=null
                        where id=:id and actualizado_en=cast(:revision as timestamptz)
                        """,
                        Map.of("id", id, "revision", revision, "hash", hash)));
    }

    public void guardarRol(UUID id, Rol d, boolean nuevo) {
        var p =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("codigo", d.codigo())
                        .addValue("nombre", d.nombre().trim())
                        .addValue("descripcion", d.descripcion())
                        .addValue("ambito", d.ambito())
                        .addValue("estado", d.estado())
                        .addValue("revision", d.revision());
        if (nuevo)
            jdbc.update(
                    "insert into uml_colaborativo.rol(id,codigo,nombre,descripcion,ambito,estado)"
                            + " values(:id,:codigo,:nombre,:descripcion,:ambito,:estado)",
                    p);
        else
            exigir(
                    jdbc.update(
                            "update uml_colaborativo.rol set"
                                + " nombre=:nombre,descripcion=:descripcion,estado=:estado where"
                                + " id=:id and actualizado_en=cast(:revision as timestamptz)",
                            p));
        jdbc.update("delete from uml_colaborativo.rol_permiso where id_rol=:id", p);
        for (UUID permiso : d.permisos())
            jdbc.update(
                    "insert into uml_colaborativo.rol_permiso(id_rol,id_permiso)"
                            + " values(:id,:permiso)",
                    p.addValue("permiso", permiso));
    }

    private Map<String, Object> uno(List<Map<String, Object>> filas) {
        if (filas.isEmpty()) throw new ApiException(404, "Usuario no disponible.");
        return filas.getFirst();
    }

    private void exigir(int n) {
        if (n != 1)
            throw new ApiException(
                    409, "Los datos cambiaron. Recarga y revisa antes de confirmar.");
    }
}
