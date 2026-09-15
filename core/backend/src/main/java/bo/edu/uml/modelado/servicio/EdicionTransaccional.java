package bo.edu.uml.modelado.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.*;
import bo.edu.uml.modelado.repositorio.*;
import bo.edu.uml.proyectos.repositorio.ActividadRepositorio;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class EdicionTransaccional {
    private final ModeloRepositorio modelos;
    private final ElementoRepositorio elementos;
    private final ApplicationEventPublisher eventos;
    private final ActividadElementoRepositorio valores;
    private final ActividadRepositorio actividad;

    public EdicionTransaccional(
            ModeloRepositorio modelos,
            ElementoRepositorio elementos,
            ApplicationEventPublisher eventos,
            ActividadElementoRepositorio valores,
            ActividadRepositorio actividad) {
        this.modelos = modelos;
        this.elementos = elementos;
        this.eventos = eventos;
        this.valores = valores;
        this.actividad = actividad;
    }

    public record ModeloConfirmado(UUID modelo, UUID actor, long revision, UUID version) {}

    @Transactional
    public Confirmacion aplicar(Identidad actor, UUID modelo, Cambio cambio, String parche) {
        autorizar(actor, modelo, "EDITAR_MODELO", false);
        var previo =
                modelos.repeticion(
                        modelo,
                        cambio.idCambioCliente(),
                        actor.id(),
                        cambio.revisionBase(),
                        parche);
        if (previo.isPresent()) return previo.get();
        long revision = modelos.iniciar(modelo, cambio.revisionBase(), actor.id());
        UUID proyecto = modelos.cabecera(modelo).proyecto();
        record Registro(Accion accion, String anterior, String posterior) {}
        List<Registro> registros = new ArrayList<>();
        for (Accion a : cambio.acciones()) {
            boolean reubicaClase =
                    a.entidad().equals("CLASE")
                            && a.datos() != null
                            && a.datos().has("idPaquete")
                            && !Objects.equals(
                                    modelos.paqueteClase(modelo, a.idEntidad()),
                                    a.datos().hasNonNull("idPaquete")
                                            ? bo.edu.uml.modelado.modelo.ReglasElemento.uuid(
                                                    a.datos(), "idPaquete")
                                            : null);
            if ((a.operacion().equals("MOVER") || a.entidad().equals("PAQUETE") || reubicaClase)
                    && !modelos.permiso(
                            actor.id(), modelos.cabecera(modelo).proyecto(), "ORGANIZAR_MODELO"))
                throw new ApiException(403, "No tienes permiso para organizar el diagrama.");
            String anterior = valores.valores(modelo, a);
            elementos.aplicar(modelo, actor.id(), a);
            registros.add(new Registro(a, anterior, valores.valores(modelo, a)));
        }
        Accion primera = cambio.acciones().getFirst();
        modelos.registrar(
                modelo,
                cambio.idCambioCliente(),
                actor.id(),
                cambio.revisionBase(),
                revision,
                primera.operacion(),
                cambio.acciones().size() == 1 ? primera.entidad() : "MODELO_UML",
                cambio.acciones().size() == 1 ? primera.idEntidad() : modelo,
                parche);
        // JDBC ejecuta inmediatamente; no existen escrituras JPA pendientes de flush.
        UUID version =
                modelos.guardar(
                        modelo,
                        revision,
                        actor.id(),
                        "Edición manual: " + cambio.acciones().size() + " operación(es)");
        for (int i = 0; i < registros.size(); i++) {
            var r = registros.get(i);
            actividad.registrar(
                    proyecto,
                    actor.id(),
                    "MODELADO",
                    r.accion().operacion(),
                    r.accion().entidad(),
                    r.accion().idEntidad(),
                    modelo,
                    version,
                    r.anterior(),
                    r.posterior(),
                    actividad.escribir(
                            Map.of(
                                    "idCambioCliente",
                                    cambio.idCambioCliente(),
                                    "indiceAccion",
                                    i,
                                    "revisionBase",
                                    cambio.revisionBase(),
                                    "revisionAplicada",
                                    revision)));
        }
        eventos.publishEvent(new ModeloConfirmado(modelo, actor.id(), revision, version));
        return new Confirmacion(cambio.idCambioCliente(), revision, version, false);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Confirmacion> recuperarReenvio(
            Identidad actor, UUID modelo, Cambio cambio, String parche) {
        autorizar(actor, modelo, "EDITAR_MODELO", false);
        return modelos.repeticion(
                modelo, cambio.idCambioCliente(), actor.id(), cambio.revisionBase(), parche);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Modelo leer(Identidad actor, UUID id) {
        autorizar(actor, id, "VER_MODELO", true);
        return modelos.leer(modelos.cabecera(id), actor);
    }

    private void autorizar(Identidad actor, UUID id, String permiso, boolean lectura) {
        var c = modelos.cabecera(id);
        if (!(lectura && actor.esAdministrador())
                && !modelos.permiso(actor.id(), c.proyecto(), permiso))
            throw new ApiException(404, "Modelo no disponible.");
    }
}
