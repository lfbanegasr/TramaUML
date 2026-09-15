package bo.edu.uml.proyectos.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.*;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio;
import bo.edu.uml.proyectos.dto.ProyectoDto.*;
import bo.edu.uml.proyectos.repositorio.ActividadRepositorio;
import bo.edu.uml.proyectos.repositorio.ProyectoRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
public class ProyectoServicio {
    private final ProyectoRepositorio repositorio;
    private final ModeloRepositorio modelos;
    private final AdministracionRepositorio admin;
    private final AccesoRepositorio acceso;
    private final ActividadRepositorio actividad;

    public ProyectoServicio(
            ProyectoRepositorio repositorio,
            ModeloRepositorio modelos,
            AdministracionRepositorio admin,
            AccesoRepositorio acceso,
            ActividadRepositorio actividad) {
        this.repositorio = repositorio;
        this.modelos = modelos;
        this.admin = admin;
        this.acceso = acceso;
        this.actividad = actividad;
    }

    private Identidad vigente(Identidad actor) {
        if (!admin.activa(actor.id())) throw new ApiException(401, "La cuenta no está activa.");
        return acceso.identidad(actor.id());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Pagina listar(
            Identidad actor,
            String filtro,
            String estado,
            String rol,
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamano) {
        FiltrosConsulta.validar(pagina, tamano, desde, hasta);
        if (filtro.length() > 180
                || rol.length() > 80
                || !rol.matches("[A-Za-z0-9_]*")
                || !Set.of("", "ACTIVO", "ARCHIVADO", "SUSPENDIDO").contains(estado))
            throw new ApiException(400, "Filtros inválidos.");
        return repositorio.listar(
                vigente(actor), filtro, estado, rol, desde, hasta, pagina, tamano);
    }

    @Transactional(readOnly = true)
    public Resumen obtener(Identidad actor, UUID id) {
        return repositorio
                .obtener(vigente(actor), id)
                .orElseThrow(() -> new ApiException(404, "Proyecto no disponible."));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rolesConsulta(Identidad actor) {
        vigente(actor);
        return repositorio.rolesConsulta();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Map<String, Object>> equipo(Identidad actor, UUID id) {
        obtener(actor, id);
        return repositorio.equipo(id);
    }

    private Identidad autorizarGestion(Identidad actor, UUID id) {
        admin.bloquearLecturaAdministracion();
        repositorio.bloquear(id);
        var actual = vigente(actor);
        var resumen =
                repositorio
                        .obtener(actual, id)
                        .orElseThrow(() -> new ApiException(404, "Proyecto no disponible."));
        if (!resumen.puedeGestionarProyecto())
            throw new ApiException(403, "No tienes permiso para gestionar el proyecto.");
        return actual;
    }

    private void revisar(Resumen actual, OffsetDateTime revision) {
        if (!OffsetDateTime.parse(actual.revision()).toInstant().equals(revision.toInstant()))
            throw new ApiException(
                    409,
                    "El proyecto cambió. Conserva el borrador, recarga el resumen y revisa antes de"
                            + " confirmar.");
    }

    private void registrar(
            UUID id,
            Identidad actor,
            String accion,
            UUID solicitud,
            Object peticion,
            String anterior) {
        actividad.registrar(
                id,
                actor.id(),
                "PROYECTO",
                accion,
                "PROYECTO",
                id,
                null,
                null,
                anterior,
                repositorio.valores(id),
                actividad.escribir(Map.of("idSolicitudCliente", solicitud, "peticion", peticion)));
    }

    @Transactional
    public Resumen actualizar(Identidad actor, UUID id, Actualizar datos) {
        var actual = autorizarGestion(actor, id);
        if (actividad.repeticion(
                id,
                actual.id(),
                datos.idSolicitudCliente(),
                "ACTUALIZAR",
                actividad.escribir(datos))) return repositorio.obtener(actual, id).orElseThrow();
        var resumen = repositorio.obtener(actual, id).orElseThrow();
        revisar(resumen, datos.revision());
        if (!resumen.estado().equals("ACTIVO"))
            throw new ApiException(409, "Reactiva el proyecto antes de modificar sus datos.");
        String anterior = repositorio.valores(id);
        repositorio.actualizar(id, datos);
        registrar(id, actual, "ACTUALIZAR", datos.idSolicitudCliente(), datos, anterior);
        return repositorio.obtener(actual, id).orElseThrow();
    }

    @Transactional
    public Resumen cambiarEstado(Identidad actor, UUID id, CambiarEstado datos, boolean archivar) {
        var actual = autorizarGestion(actor, id);
        String accion = archivar ? "ARCHIVAR" : "REACTIVAR";
        if (actividad.repeticion(
                id, actual.id(), datos.idSolicitudCliente(), accion, actividad.escribir(datos)))
            return repositorio.obtener(actual, id).orElseThrow();
        var resumen = repositorio.obtener(actual, id).orElseThrow();
        revisar(resumen, datos.revision());
        if (!datos.confirmar())
            throw new ApiException(422, "Confirma las consecuencias del cambio de estado.");
        if (!resumen.estado().equals(archivar ? "ACTIVO" : "ARCHIVADO"))
            throw new ApiException(409, "El estado actual no permite esta transición.");
        if (archivar && resumen.sesionesEdicionActivas() > 0)
            throw new ApiException(
                    409,
                    "Hay sesiones de edición activas. Deben finalizar antes de archivar el"
                            + " proyecto.");
        String anterior = repositorio.valores(id);
        repositorio.estado(id, archivar);
        registrar(id, actual, accion, datos.idSolicitudCliente(), datos, anterior);
        return repositorio.obtener(actual, id).orElseThrow();
    }

    @Transactional
    public Resumen crear(Identidad actor, Crear datos) {
        admin.bloquearLecturaAdministracion();
        actor = vigente(actor);
        if (!actor.puedeCrearProyectos())
            throw new ApiException(403, "No tienes permiso para crear proyectos.");
        UUID proyecto = UUID.randomUUID(), modelo = UUID.randomUUID();
        repositorio.crear(proyecto, actor, datos, modelo);
        // El modelo vacío también se confirma mediante el contrato; no se fabrica una instantánea.
        long revision = modelos.iniciar(modelo, 0, actor.id());
        modelos.registrar(
                modelo,
                null,
                UUID.randomUUID(),
                actor.id(),
                0,
                revision,
                "CREAR",
                "MODELO_UML",
                modelo,
                "{\"inicializacion\":true}");
        modelos.guardar(modelo, revision, actor.id(), "Modelo inicial vacío");
        return repositorio.obtener(actor, proyecto).orElseThrow();
    }
}
