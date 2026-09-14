package bo.edu.uml.acceso.repositorio;

import bo.edu.uml.acceso.modelo.Identidad;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class AccesoRepositorio {
    private final NamedParameterJdbcTemplate jdbc;

    public AccesoRepositorio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Cuenta(
            UUID id, String hash, String estado, boolean bloqueada, boolean requiereActivacion) {}

    public Optional<Cuenta> bloquearCuenta(String identificador) {
        var cuentas =
                jdbc.query(
                        """
                        select id, hash_contrasena, estado, coalesce(bloqueado_hasta>clock_timestamp(),false) bloqueada
                        from uml_colaborativo.usuario where nombre_usuario=:identificador or correo_electronico=:identificador
                        order by id for update
                        """,
                        Map.of("identificador", identificador),
                        (r, n) -> {
                            String almacenado = r.getString("hash_contrasena");
                            boolean temporal = almacenado.startsWith("{temporal}");
                            return new Cuenta(
                                    r.getObject("id", UUID.class),
                                    temporal ? almacenado.substring(10) : almacenado,
                                    r.getString("estado"),
                                    r.getBoolean("bloqueada"),
                                    temporal);
                        });
        return cuentas.size() == 1 ? Optional.of(cuentas.getFirst()) : Optional.empty();
    }

    public void accesoFallido(UUID id) {
        jdbc.update(
                """
                update uml_colaborativo.usuario set intentos_fallidos_acceso=least(intentos_fallidos_acceso+1,5),
                bloqueado_hasta=case when intentos_fallidos_acceso+1>=5 then clock_timestamp()+interval '15 minutes' else bloqueado_hasta end
                where id=:id
                """,
                Map.of("id", id));
    }

    public void crearSesion(UUID id, byte[] hash) {
        jdbc.update(
                "update uml_colaborativo.usuario set intentos_fallidos_acceso=0,"
                        + " bloqueado_hasta=null, ultimo_acceso_en=clock_timestamp() where id=:id",
                Map.of("id", id));
        jdbc.update(
                "insert into uml_colaborativo.sesion_autenticacion(id_usuario,hash_token,expira_en)"
                        + " values(:id,:hash,clock_timestamp()+interval '2 hours')",
                Map.of("id", id, "hash", hash));
    }

    public Optional<Identidad> autenticar(byte[] hash) {
        var ids =
                jdbc.query(
                        """
                        select u.id from uml_colaborativo.sesion_autenticacion s join uml_colaborativo.usuario u on u.id=s.id_usuario
                        where s.hash_token=:hash and s.revocado_en is null and s.expira_en>clock_timestamp()
                        and u.estado='ACTIVO' and (u.bloqueado_hasta is null or u.bloqueado_hasta<=clock_timestamp())
                        """,
                        Map.of("hash", hash),
                        (r, n) -> r.getObject(1, UUID.class));
        return ids.stream().findFirst().map(this::identidad);
    }

    public Identidad identidad(UUID id) {
        var p = Map.of("id", id);
        var roles =
                jdbc.queryForList(
                        "select r.codigo::text from uml_colaborativo.usuario_rol ur join"
                            + " uml_colaborativo.rol r on r.id=ur.id_rol where ur.id_usuario=:id"
                            + " and ur.revocado_en is null and r.estado='ACTIVO' and r.ambito in"
                            + " ('GLOBAL','AMBOS') order by r.codigo",
                        p,
                        String.class);
        var permisos =
                jdbc.queryForList(
                        "select distinct p.codigo::text from uml_colaborativo.usuario_rol ur join"
                            + " uml_colaborativo.rol r on r.id=ur.id_rol join"
                            + " uml_colaborativo.rol_permiso rp on rp.id_rol=r.id join"
                            + " uml_colaborativo.permiso p on p.id=rp.id_permiso where"
                            + " ur.id_usuario=:id and ur.revocado_en is null and r.estado='ACTIVO'"
                            + " and r.ambito in ('GLOBAL','AMBOS') and p.ambito in"
                            + " ('GLOBAL','AMBOS') order by p.codigo::text",
                        p,
                        String.class);
        return jdbc.queryForObject(
                "select nombre_usuario,nombre_completo from uml_colaborativo.usuario where id=:id",
                p,
                (r, n) -> new Identidad(id, r.getString(1), r.getString(2), roles, permisos));
    }

    public void revocar(byte[] hash) {
        jdbc.update(
                "update uml_colaborativo.sesion_autenticacion set revocado_en=clock_timestamp(),"
                    + " motivo_revocacion='CIERRE_USUARIO' where hash_token=:hash and revocado_en"
                    + " is null",
                Map.of("hash", hash));
    }

    public boolean hayUsuarios() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists(select 1 from uml_colaborativo.usuario)",
                        Map.of(),
                        Boolean.class));
    }

    public void activar(UUID id, String hash) {
        jdbc.update(
                "update uml_colaborativo.usuario set"
                    + " hash_contrasena=:hash,estado='ACTIVO',intentos_fallidos_acceso=0,bloqueado_hasta=null,desactivado_en=null"
                    + " where id=:id and estado='PENDIENTE'",
                Map.of("id", id, "hash", hash));
    }

    public UUID registrarAdministrador(String usuario, String correo, String nombre, String hash) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " uml_colaborativo.usuario(id,nombre_usuario,correo_electronico,nombre_completo,hash_contrasena)"
                    + " values(:id,:usuario,:correo,:nombre,:hash)",
                Map.of(
                        "id", id, "usuario", usuario, "correo", correo, "nombre", nombre, "hash",
                        hash));
        int roles =
                jdbc.update(
                        "insert into uml_colaborativo.usuario_rol(id_usuario,id_rol,asignado_por)"
                                + " select :id,id,:id from uml_colaborativo.rol where"
                                + " codigo='SUPERADMINISTRADOR' and estado='ACTIVO' and"
                                + " ambito='GLOBAL'",
                        Map.of("id", id));
        if (roles != 1) throw new IllegalStateException("Falta el rol vigente SUPERADMINISTRADOR.");
        return id;
    }
}
