package bo.edu.uml.proyectos.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.*;
import bo.edu.uml.colaboracion.servicio.ColaboracionServicio.AccesoProyectoCambiado;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio;
import bo.edu.uml.proyectos.dto.MiembroDto.*;
import bo.edu.uml.proyectos.repositorio.MiembroRepositorio;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MiembroServicio {
    private final MiembroRepositorio repo;
    private final ModeloRepositorio modelos;
    private final AdministracionRepositorio admin;
    private final AccesoRepositorio acceso;
    private final ApplicationEventPublisher eventos;

    public MiembroServicio(
            MiembroRepositorio repo,
            ModeloRepositorio modelos,
            AdministracionRepositorio admin,
            AccesoRepositorio acceso,
            ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.modelos = modelos;
        this.admin = admin;
        this.acceso = acceso;
        this.eventos = eventos;
    }

    private UUID autorizar(Identidad a, UUID proyecto, boolean bloquear) {
        UUID propietario = repo.propietario(proyecto, bloquear);
        if (!admin.activa(a.id())
                || (!acceso.identidad(a.id()).esAdministrador()
                        && !modelos.permiso(a.id(), proyecto, "GESTIONAR_MIEMBROS")))
            throw new ApiException(404, "Proyecto no disponible para administrar miembros.");
        return propietario;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(Identidad a, UUID proyecto) {
        UUID propietario = autorizar(a, proyecto, false);
        return Map.of(
                "propietario",
                propietario,
                "miembros",
                repo.listar(proyecto),
                "roles",
                admin.roles().stream()
                        .filter(
                                r ->
                                        !"GLOBAL".equals(r.get("ambito"))
                                                && "ACTIVO".equals(r.get("estado")))
                        .toList(),
                "permisos",
                admin.permisos().stream().filter(p -> !"GLOBAL".equals(p.get("ambito"))).toList());
    }

    @Transactional(readOnly = true)
    public Object buscar(Identidad a, UUID proyecto, String filtro) {
        autorizar(a, proyecto, false);
        return repo.buscar(proyecto, filtro);
    }

    @Transactional
    public Map<String, Object> guardar(
            Identidad a, UUID proyecto, UUID id, Guardar d, boolean nuevo) {
        admin.bloquearLecturaAdministracion();
        UUID propietario = autorizar(a, proyecto, true);
        if (!repo.activa(d.usuario())) throw new ApiException(422, "La cuenta debe estar activa.");
        if (nuevo && !d.estado().equals("ACTIVO"))
            throw new ApiException(422, "Una incorporación comienza activa.");
        if (!d.estado().equals("ACTIVO") && (d.motivo() == null || d.motivo().isBlank()))
            throw new ApiException(422, "Indica el motivo de suspensión o retiro.");
        if (!nuevo && !d.usuario().equals(repo.obtener(proyecto, id).get("usuario")))
            throw new ApiException(422, "La cuenta de una membresía no se cambia.");
        var rol = admin.rol(d.rol());
        if ("GLOBAL".equals(rol.get("ambito")) || !"ACTIVO".equals(rol.get("estado")))
            throw new ApiException(422, "Selecciona un rol de proyecto activo.");
        if (d.usuario().equals(propietario)
                && (!d.estado().equals("ACTIVO")
                        || !"PROPIETARIO_PROYECTO".equals(rol.get("codigo"))
                        || d.permisos().stream().anyMatch(p -> p.decision().equals("DENEGAR"))))
            throw new ApiException(
                    422,
                    "Transfiere la propiedad antes de retirar o restringir al propietario actual.");
        if ("PROPIETARIO_PROYECTO".equals(rol.get("codigo")) && !d.usuario().equals(propietario))
            throw new ApiException(422, "Usa la transferencia explícita de propiedad.");
        boolean superadmin = acceso.identidad(a.id()).esAdministrador();
        var heredados = admin.permisosDelRol(d.rol());
        if (!superadmin)
            for (var permiso : heredados)
                if (!modelos.permiso(a.id(), proyecto, (String) permiso.get("codigo")))
                    throw new ApiException(
                            403, "No puedes conceder un rol con permisos superiores a los tuyos.");
        Set<UUID> ids = new HashSet<>();
        for (Decision decision : d.permisos()) {
            if (!ids.add(decision.permiso()))
                throw new ApiException(422, "Hay excepciones de permiso repetidas.");
            var permiso =
                    admin.permisos().stream()
                            .filter(
                                    p ->
                                            decision.permiso().equals(p.get("id"))
                                                    && !"GLOBAL".equals(p.get("ambito")))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    422, "Permiso fuera del ámbito del proyecto."));
            if (!superadmin
                    && decision.decision().equals("PERMITIR")
                    && !modelos.permiso(a.id(), proyecto, (String) permiso.get("codigo")))
                throw new ApiException(403, "No puedes conceder ese permiso.");
        }
        repo.guardar(proyecto, id, a.id(), d, nuevo);
        eventos.publishEvent(new AccesoProyectoCambiado(proyecto));
        return repo.obtener(proyecto, id);
    }

    @Transactional
    public Object transferir(Identidad a, UUID proyecto, UUID miembro) {
        admin.bloquearLecturaAdministracion();
        UUID propietario = autorizar(a, proyecto, true);
        if (!a.id().equals(propietario) && !acceso.identidad(a.id()).esAdministrador())
            throw new ApiException(
                    403,
                    "Solo el propietario actual o un superadministrador puede transferir la"
                            + " propiedad.");
        repo.transferir(proyecto, miembro, a.id());
        eventos.publishEvent(new AccesoProyectoCambiado(proyecto));
        return listar(a, proyecto);
    }
}
