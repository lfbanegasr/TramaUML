package bo.edu.uml.acceso.servicio;

import bo.edu.uml.acceso.dto.AdministracionDto.*;
import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.*;
import bo.edu.uml.compartido.ApiException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AdministracionServicio {
    private final AdministracionRepositorio repo;
    private final AccesoRepositorio acceso;
    private final PasswordEncoder encoder;

    public AdministracionServicio(
            AdministracionRepositorio repo, AccesoRepositorio acceso, PasswordEncoder encoder) {
        this.repo = repo;
        this.acceso = acceso;
        this.encoder = encoder;
    }

    private void autorizar(Identidad actor, String permiso) {
        var vigente = acceso.identidad(actor.id());
        if (!repo.activa(actor.id())
                || (!vigente.esAdministrador() && !vigente.permisosGlobales().contains(permiso)))
            throw new ApiException(403, "No tienes autorización para esta administración.");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> usuarios(
            Identidad a, String filtro, String estado, String rol, int pagina) {
        autorizar(a, "GESTIONAR_USUARIOS");
        return repo.usuarios(filtro, estado, rol, pagina);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> roles(Identidad a) {
        autorizar(a, "GESTIONAR_ROLES");
        return repo.roles();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> permisos(Identidad a) {
        autorizar(a, "GESTIONAR_ROLES");
        return repo.permisos();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rolesParaUsuario(Identidad a) {
        autorizar(a, "GESTIONAR_USUARIOS");
        return repo.roles();
    }

    @Transactional
    public Credencial crear(Identidad a, Usuario d) {
        repo.bloquearAdministracion();
        autorizar(a, "GESTIONAR_USUARIOS");
        validarRoles(a, d.roles());
        UUID id = UUID.randomUUID();
        String temporal = UUID.randomUUID().toString();
        repo.guardarUsuario(id, a.id(), d, "{temporal}" + encoder.encode(temporal), true);
        auditar(a, "CREAR_USUARIO", id);
        return new Credencial(id, temporal);
    }

    @Transactional
    public Map<String, Object> actualizar(Identidad a, UUID id, Usuario d) {
        repo.bloquearAdministracion();
        autorizar(a, "GESTIONAR_USUARIOS");
        validarRoles(a, d.roles());
        repo.usuario(id);
        if (repo.administrador(id) && !acceso.identidad(a.id()).esAdministrador())
            throw new ApiException(403, "Solo un superadministrador puede modificar esa cuenta.");
        boolean conservarAdmin =
                d.roles().stream()
                                .anyMatch(
                                        r -> "SUPERADMINISTRADOR".equals(repo.rol(r).get("codigo")))
                        && d.estado().equals("ACTIVO");
        if (id.equals(a.id())
                && (!d.estado().equals("ACTIVO") || (repo.administrador(id) && !conservarAdmin)))
            throw new ApiException(
                    422,
                    "No puedes desactivar tu propia cuenta administrativa ni retirar su acceso.");
        if (repo.administrador(id)
                && repo.activa(id)
                && !conservarAdmin
                && repo.administradoresActivos() <= 1)
            throw new ApiException(422, "Debe permanecer un administrador activo.");
        if (repo.requiereActivacion(id)
                && !d.estado().equals("PENDIENTE")
                && !d.estado().equals("INACTIVO"))
            throw new ApiException(
                    422, "La cuenta debe elegir una contraseña propia mediante la activación.");
        repo.guardarUsuario(id, a.id(), d, null, false);
        repo.revocarSesiones(id);
        auditar(a, "ACTUALIZAR_USUARIO", id);
        return repo.usuario(id);
    }

    @Transactional
    public Credencial restablecer(Identidad a, UUID id, String revision) {
        repo.bloquearAdministracion();
        autorizar(a, "GESTIONAR_USUARIOS");
        repo.usuario(id);
        if (repo.administrador(id) && !acceso.identidad(a.id()).esAdministrador())
            throw new ApiException(403, "Solo un superadministrador puede restablecer esa cuenta.");
        if (id.equals(a.id())
                || (repo.administrador(id)
                        && repo.activa(id)
                        && repo.administradoresActivos() <= 1))
            throw new ApiException(
                    422, "No puedes dejar a la plataforma sin administrador activo.");
        String temporal = UUID.randomUUID().toString();
        repo.restablecer(id, revision, "{temporal}" + encoder.encode(temporal));
        repo.revocarSesiones(id);
        auditar(a, "RESTABLECER_CREDENCIAL", id);
        return new Credencial(id, temporal);
    }

    private void validarRoles(Identidad a, List<UUID> roles) {
        if (new HashSet<>(roles).size() != roles.size())
            throw new ApiException(422, "Hay roles repetidos.");
        for (UUID id : roles) {
            var r = repo.rol(id);
            if ("PROYECTO".equals(r.get("ambito")) || !"ACTIVO".equals(r.get("estado")))
                throw new ApiException(422, "Selecciona roles globales activos.");
            if (!acceso.identidad(a.id()).esAdministrador()
                    && "SUPERADMINISTRADOR".equals(r.get("codigo")))
                throw new ApiException(403, "Solo un superadministrador puede conceder ese rol.");
            if (!acceso.identidad(a.id()).esAdministrador())
                for (var permiso : repo.permisosDelRol(id))
                    if (!"PROYECTO".equals(permiso.get("ambito"))
                            && !acceso.identidad(a.id())
                                    .permisosGlobales()
                                    .contains(permiso.get("codigo")))
                        throw new ApiException(
                                403,
                                "No puedes conceder un rol global con permisos superiores a los"
                                        + " tuyos.");
        }
    }

    @Transactional
    public Map<String, Object> guardarRol(Identidad a, UUID id, Rol d, boolean nuevo) {
        repo.bloquearAdministracion();
        autorizar(a, "GESTIONAR_ROLES");
        if (repo.roles().stream()
                .anyMatch(
                        r ->
                                !id.equals(r.get("id"))
                                        && d.nombre()
                                                .trim()
                                                .equalsIgnoreCase((String) r.get("nombre"))))
            throw new ApiException(422, "Ya existe un rol con ese nombre.");
        if (new HashSet<>(d.permisos()).size() != d.permisos().size())
            throw new ApiException(422, "Hay permisos repetidos.");
        if (!nuevo) {
            var r = repo.rol(id);
            if ("SUPERADMINISTRADOR".equals(r.get("codigo")))
                throw new ApiException(422, "El rol Superadministrador está protegido.");
            if (!d.codigo().equals(r.get("codigo")) || !d.ambito().equals(r.get("ambito")))
                throw new ApiException(
                        422,
                        "Código y ámbito permanecen estables para conservar las asignaciones.");
            if (d.estado().equals("INACTIVO") && ((Number) r.get("asignaciones")).longValue() > 0)
                throw new ApiException(
                        422, "Retira o reasigna las asignaciones antes de desactivar el rol.");
        }
        for (UUID idPermiso : d.permisos()) {
            var p =
                    repo.permisos().stream()
                            .filter(v -> idPermiso.equals(v.get("id")))
                            .findFirst()
                            .orElseThrow(() -> new ApiException(422, "Permiso inexistente."));
            if (!d.ambito().equals("AMBOS")
                    && !p.get("ambito").equals("AMBOS")
                    && !p.get("ambito").equals(d.ambito()))
                throw new ApiException(422, "El permiso no corresponde al ámbito del rol.");
            var vigente = acceso.identidad(a.id());
            if (!vigente.esAdministrador()
                    && (!p.get("ambito").equals("GLOBAL")
                            || !vigente.permisosGlobales().contains(p.get("codigo"))))
                throw new ApiException(
                        403, "No puedes conceder permisos superiores a tu autorización.");
        }
        if (!nuevo && "PROPIETARIO_PROYECTO".equals(repo.rol(id).get("codigo"))) {
            var obligatorios =
                    repo.permisos().stream()
                            .filter(
                                    p ->
                                            Set.of(
                                                            "VER_PROYECTOS",
                                                            "VER_MODELO",
                                                            "GESTIONAR_MIEMBROS")
                                                    .contains(p.get("codigo")))
                            .map(p -> (UUID) p.get("id"))
                            .toList();
            if (!d.permisos().containsAll(obligatorios))
                throw new ApiException(
                        422,
                        "El propietario conserva consulta del proyecto, modelo y gestión de"
                                + " miembros.");
        }
        repo.guardarRol(id, d, nuevo);
        auditar(a, nuevo ? "CREAR_ROL" : "ACTUALIZAR_ROL_PERMISOS", id);
        return repo.rol(id);
    }

    private void auditar(Identidad a, String accion, UUID id) {
        bo.edu.uml.compartido.Auditoria.despuesDelCommit(a.id(), accion, id);
    }
}
