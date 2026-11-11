package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.*;
import bo.edu.uml.colaboracion.dto.ColaboracionDto.*;
import bo.edu.uml.colaboracion.repositorio.SesionEdicionRepositorio;
import bo.edu.uml.colaboracion.repositorio.SesionEdicionRepositorio.ModeloBloqueado;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio;
import bo.edu.uml.proyectos.repositorio.ActividadRepositorio;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Map;
import java.util.UUID;

@Service
public class ColaboracionServicio {
    private final SesionEdicionRepositorio sesiones;
    private final ModeloRepositorio modelos;
    private final AdministracionRepositorio administracion;
    private final AccesoRepositorio acceso;
    private final ActividadRepositorio actividad;
    private final ApplicationEventPublisher eventos;

    public ColaboracionServicio(
            SesionEdicionRepositorio sesiones,
            ModeloRepositorio modelos,
            AdministracionRepositorio administracion,
            AccesoRepositorio acceso,
            ActividadRepositorio actividad,
            ApplicationEventPublisher eventos) {
        this.sesiones = sesiones;
        this.modelos = modelos;
        this.administracion = administracion;
        this.acceso = acceso;
        this.actividad = actividad;
        this.eventos = eventos;
    }

    public record PresenciaConfirmada(Sesion sesion) {}

    public record AccesoProyectoCambiado(UUID proyecto) {}

    public record AccesoCanal(Identidad actor, UUID proyecto, String modo) {}

    private Identidad vigente(Identidad actor) {
        if (actor == null || !administracion.activa(actor.id()))
            throw new ApiException(401, "La cuenta no está activa.");
        return acceso.identidad(actor.id());
    }

    private String modo(Identidad actor, ModeloBloqueado modelo) {
        boolean puedeEditar =
                "ACTIVO".equals(modelo.estadoProyecto())
                        && !"ARCHIVADO".equals(modelo.estadoModelo())
                        && modelos.permiso(actor.id(), modelo.proyecto(), "EDITAR_MODELO");
        return puedeEditar ? "EDITOR" : "LECTOR";
    }

    private Identidad autorizarLectura(Identidad actor, ModeloBloqueado modelo) {
        actor = vigente(actor);
        if (!actor.esAdministrador()
                && !modelos.permiso(actor.id(), modelo.proyecto(), "VER_MODELO"))
            throw new ApiException(404, "Modelo no disponible.");
        return actor;
    }

    @Transactional
    public Sesion abrir(Identidad actor, UUID modelo, Abrir solicitud) {
        administracion.bloquearLecturaAdministracion();
        var cabecera = sesiones.bloquearModelo(modelo);
        actor = autorizarLectura(actor, cabecera);
        String modo = modo(actor, cabecera);
        var activa = sesiones.sesionActiva(modelo);
        boolean nueva = activa.isEmpty();
        UUID sesion = activa.orElse(solicitud.idSolicitudCliente());
        if (nueva) sesiones.crear(sesion, cabecera, actor.id());
        boolean incorporado = sesiones.incorporar(sesion, actor.id(), modo);
        if (nueva || incorporado)
            actividad.registrar(
                    cabecera.proyecto(),
                    actor.id(),
                    "COLABORACION",
                    nueva ? "ABRIR_SESION" : "INCORPORARSE",
                    "SESION_EDICION_MODELO",
                    sesion,
                    modelo,
                    null,
                    null,
                    actividad.escribir(Map.of("sesion", sesion, "modo", modo, "estado", "AUSENTE")),
                    actividad.escribir(
                            Map.of("idSolicitudCliente", solicitud.idSolicitudCliente())));
        return publicar(sesiones.obtener(sesion, actor.id()));
    }

    @Transactional(readOnly = true)
    public Sesion consultar(Identidad actor, UUID modelo, UUID sesion) {
        var cabecera = sesiones.modelo(modelo);
        actor = autorizarLectura(actor, cabecera);
        sesiones.verificarParticipante(sesion, modelo, actor.id(), false);
        return sesiones.obtener(sesion, actor.id());
    }

    @Transactional
    public Sesion salir(Identidad actor, UUID modelo, UUID sesion) {
        administracion.bloquearLecturaAdministracion();
        var cabecera = sesiones.bloquearModelo(modelo);
        actor = autorizarLectura(actor, cabecera);
        sesiones.verificarParticipante(sesion, modelo, actor.id(), false);
        if (sesiones.salir(sesion, actor.id()))
            actividad.registrar(
                    cabecera.proyecto(),
                    actor.id(),
                    "COLABORACION",
                    "SALIR_SESION",
                    "SESION_EDICION_MODELO",
                    sesion,
                    modelo,
                    null,
                    null,
                    actividad.escribir(Map.of("sesion", sesion, "estado", "DESCONECTADO")),
                    actividad.escribir(Map.of("salidaExplicita", true)));
        return publicar(sesiones.obtener(sesion, actor.id()));
    }

    @Transactional(readOnly = true)
    public AccesoCanal autorizarCanal(Identidad actor, UUID modelo, UUID sesion) {
        var cabecera = sesiones.modelo(modelo);
        actor = autorizarLectura(actor, cabecera);
        sesiones.verificarParticipante(sesion, modelo, actor.id(), false);
        return new AccesoCanal(actor, cabecera.proyecto(), modo(actor, cabecera));
    }

    @Transactional
    public Sesion conectar(Identidad actor, UUID modelo, UUID sesion) {
        administracion.bloquearLecturaAdministracion();
        var cabecera = sesiones.bloquearModelo(modelo);
        actor = autorizarLectura(actor, cabecera);
        sesiones.verificarParticipante(sesion, modelo, actor.id(), false);
        sesiones.conectar(sesion, actor.id(), modo(actor, cabecera));
        return publicar(sesiones.obtener(sesion, actor.id()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Sesion pulso(Identidad actor, UUID modelo, UUID sesion) {
        var cabecera = sesiones.modelo(modelo);
        actor = autorizarLectura(actor, cabecera);
        sesiones.verificarParticipante(sesion, modelo, actor.id(), false);
        sesiones.actualizarModo(sesion, actor.id(), modo(actor, cabecera));
        sesiones.pulso(sesion, actor.id());
        return publicar(sesiones.obtener(sesion, actor.id()));
    }

    @Transactional
    public Sesion enfocar(Identidad actor, UUID modelo, UUID sesion, Foco foco) {
        var cabecera = sesiones.modelo(modelo);
        actor = autorizarLectura(actor, cabecera);
        sesiones.verificarParticipante(sesion, modelo, actor.id(), false);
        if ((foco.tipoEntidad() == null) != (foco.idEntidad() == null))
            throw new ApiException(400, "Indica el tipo y el elemento enfocado.");
        if (foco.tipoEntidad() != null
                && !sesiones.entidadPertenece(modelo, foco.tipoEntidad(), foco.idEntidad()))
            throw new ApiException(404, "El elemento enfocado no pertenece al modelo.");
        sesiones.actualizarModo(sesion, actor.id(), modo(actor, cabecera));
        sesiones.enfocar(sesion, actor.id(), foco.tipoEntidad(), foco.idEntidad());
        return publicar(sesiones.obtener(sesion, actor.id()));
    }

    @Transactional
    public void ausentar(Identidad actor, UUID modelo, UUID sesion) {
        try {
            var acceso = autorizarCanal(actor, modelo, sesion);
            sesiones.ausentar(sesion, acceso.actor().id());
            publicar(sesiones.obtener(sesion, acceso.actor().id()));
        } catch (ApiException ignored) {
            // El retiro de acceso ya desconecta la presencia de forma transaccional.
        }
    }

    public void validarCambio(Identidad actor, UUID modelo, UUID sesion) {
        if (sesion == null)
            throw new ApiException(422, "Abre una sesión de modelado antes de guardar.");
        sesiones.verificarParticipante(sesion, modelo, actor.id(), true);
    }

    public void confirmarActividad(UUID sesion) {
        sesiones.tocar(sesion);
    }

    private Sesion publicar(Sesion sesion) {
        eventos.publishEvent(new PresenciaConfirmada(sesion));
        return sesion;
    }
}
