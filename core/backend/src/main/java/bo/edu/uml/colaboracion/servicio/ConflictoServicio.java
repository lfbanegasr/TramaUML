package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.dto.ConflictoDto.*;
import bo.edu.uml.colaboracion.repositorio.ConflictoRepositorio;
import bo.edu.uml.colaboracion.repositorio.ConflictoRepositorio.Fila;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Cambio;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class ConflictoServicio {
    private final ConflictoRepositorio conflictos;
    private final ComparadorConflicto comparador;
    private final ModeloRepositorio modelos;
    private final ColaboracionServicio colaboracion;
    private final ResolucionConflictoTransaccional resoluciones;
    private final ApplicationEventPublisher eventos;

    public ConflictoServicio(
            ConflictoRepositorio conflictos,
            ComparadorConflicto comparador,
            ModeloRepositorio modelos,
            ColaboracionServicio colaboracion,
            ResolucionConflictoTransaccional resoluciones,
            ApplicationEventPublisher eventos) {
        this.conflictos = conflictos;
        this.comparador = comparador;
        this.modelos = modelos;
        this.colaboracion = colaboracion;
        this.resoluciones = resoluciones;
        this.eventos = eventos;
    }

    public record ConflictoActualizado(UUID modelo, UUID conflicto, String estado, long revision) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Lista listar(Identidad actor, UUID modelo, String estado) {
        String filtro = estado == null ? "ABIERTO" : estado.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("", "ABIERTO", "RESUELTO", "DESCARTADO").contains(filtro))
            throw new ApiException(400, "Estado de conflicto no reconocido.");
        var cabecera = modelos.cabecera(modelo);
        autorizarLectura(actor, cabecera.proyecto());
        boolean resolver = puedeResolver(actor, cabecera);
        JsonNode actual = conflictos.instantaneaActual(modelo);
        Map<Long, JsonNode> bases = new HashMap<>();
        List<Conflicto> lista =
                conflictos.listar(modelo, filtro).stream()
                        .map(
                                f ->
                                        detalle(
                                                actor,
                                                f,
                                                cabecera.revision(),
                                                bases.computeIfAbsent(
                                                        f.revisionBase(),
                                                        r -> conflictos.instantanea(modelo, r)),
                                                actual,
                                                comparador,
                                                resolver))
                        .toList();
        return new Lista(lista, lista.size(), cabecera.revision(), resolver);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Conflicto consultar(Identidad actor, UUID modelo, UUID id) {
        var cabecera = modelos.cabecera(modelo);
        autorizarLectura(actor, cabecera.proyecto());
        Fila fila = conflictos.buscar(modelo, id, false);
        return detalle(
                actor,
                fila,
                cabecera.revision(),
                conflictos.instantanea(modelo, fila.revisionBase()),
                conflictos.instantaneaActual(modelo),
                comparador,
                puedeResolver(actor, cabecera));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID registrar(Identidad actor, UUID modelo, Cambio cambio, String parche) {
        var cabecera = modelos.cabecera(modelo);
        if (!modelos.permiso(actor.id(), cabecera.proyecto(), "EDITAR_MODELO"))
            throw new ApiException(404, "Modelo no disponible.");
        if (!"ACTIVO".equals(cabecera.estadoProyecto()) || "ARCHIVADO".equals(cabecera.estado()))
            throw new ApiException(409, "El proyecto o el modelo no admite cambios.");
        colaboracion.validarCambio(actor, modelo, cambio.idSesionEdicion());
        JsonNode base = conflictos.instantanea(modelo, cambio.revisionBase());
        JsonNode actual = conflictos.instantaneaActual(modelo);
        var comparacion = comparador.comparar(base, actual, cambio.acciones());
        Fila fila =
                conflictos.registrar(
                        modelo,
                        cambio.idSesionEdicion(),
                        cambio.idCambioCliente(),
                        actor.id(),
                        cambio.revisionBase(),
                        cambio.acciones(),
                        parche,
                        comparacion.almacenado().toString(),
                        comparacion.entrante().toString());
        eventos.publishEvent(
                new ConflictoActualizado(modelo, fila.id(), fila.estado(), cabecera.revision()));
        return fila.id();
    }

    public Resultado resolver(Identidad actor, UUID modelo, UUID id, Resolver solicitud) {
        return resoluciones.resolver(actor, modelo, id, solicitud);
    }

    public Resultado descartar(Identidad actor, UUID modelo, UUID id, Descartar solicitud) {
        return resoluciones.descartar(actor, modelo, id, solicitud);
    }

    static Conflicto detalle(
            Identidad actor,
            Fila fila,
            long revisionActual,
            JsonNode base,
            JsonNode actual,
            ComparadorConflicto comparador,
            boolean puedeResolver) {
        var acciones = comparador.interpretar(fila.parcheEntrante());
        var comparacion = comparador.comparar(base, actual, acciones);
        return new Conflicto(
                fila.id(),
                fila.modelo(),
                fila.tipoEntidad(),
                fila.idEntidad(),
                fila.revisionBase(),
                revisionActual,
                fila.estado(),
                fila.tipoResolucion(),
                fila.detectadoEn(),
                fila.resueltoPor() == null
                        ? null
                        : new Autor(fila.resueltoPor(), fila.nombreResolutor()),
                fila.resueltoEn(),
                fila.cambioAlmacenado(),
                fila.cambioEntrante(),
                fila.valorAlmacenado(),
                fila.valorEntrante(),
                comparacion.confirmadoActual(),
                fila.valorResolucion(),
                comparacion.diferencias(),
                comparacion.fusionable(),
                comparacion.elementoEliminado(),
                puedeResolver && "ABIERTO".equals(fila.estado()));
    }

    private void autorizarLectura(Identidad actor, UUID proyecto) {
        if (!actor.esAdministrador() && !modelos.permiso(actor.id(), proyecto, "VER_MODELO"))
            throw new ApiException(404, "Modelo no disponible.");
    }

    private boolean puedeResolver(Identidad actor, ModeloRepositorio.Cabecera c) {
        return "ACTIVO".equals(c.estadoProyecto())
                && !"ARCHIVADO".equals(c.estado())
                && modelos.permiso(actor.id(), c.proyecto(), "EDITAR_MODELO")
                && modelos.permiso(actor.id(), c.proyecto(), "RESOLVER_CONFLICTO");
    }
}
