package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.dto.ConflictoDto.*;
import bo.edu.uml.colaboracion.repositorio.ConflictoRepositorio;
import bo.edu.uml.colaboracion.repositorio.ConflictoRepositorio.Fila;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;
import bo.edu.uml.modelado.repositorio.*;
import bo.edu.uml.modelado.servicio.EdicionTransaccional.ModeloConfirmado;
import bo.edu.uml.proyectos.repositorio.ActividadRepositorio;
import bo.edu.uml.validacion.modelo.MotorSemantico;

import com.fasterxml.jackson.databind.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ResolucionConflictoTransaccional {
    private final ConflictoRepositorio conflictos;
    private final ComparadorConflicto comparador;
    private final ModeloRepositorio modelos;
    private final ElementoRepositorio elementos;
    private final ColaboracionServicio colaboracion;
    private final ActividadRepositorio actividad;
    private final ApplicationEventPublisher eventos;
    private final ObjectMapper json;

    public ResolucionConflictoTransaccional(
            ConflictoRepositorio conflictos,
            ComparadorConflicto comparador,
            ModeloRepositorio modelos,
            ElementoRepositorio elementos,
            ColaboracionServicio colaboracion,
            ActividadRepositorio actividad,
            ApplicationEventPublisher eventos,
            ObjectMapper json) {
        this.conflictos = conflictos;
        this.comparador = comparador;
        this.modelos = modelos;
        this.elementos = elementos;
        this.colaboracion = colaboracion;
        this.actividad = actividad;
        this.eventos = eventos;
        this.json = json;
    }

    @Transactional
    public Resultado resolver(Identidad actor, UUID modelo, UUID id, Resolver solicitud) {
        return ejecutar(
                actor,
                modelo,
                id,
                solicitud.idSesionEdicion(),
                solicitud.idSolicitudResolucion(),
                solicitud.revisionEsperada(),
                solicitud.tipoResolucion(),
                false);
    }

    @Transactional
    public Resultado descartar(Identidad actor, UUID modelo, UUID id, Descartar solicitud) {
        return ejecutar(
                actor,
                modelo,
                id,
                solicitud.idSesionEdicion(),
                solicitud.idSolicitudResolucion(),
                solicitud.revisionEsperada(),
                null,
                true);
    }

    private Resultado ejecutar(
            Identidad actor,
            UUID modelo,
            UUID id,
            UUID sesion,
            UUID solicitud,
            long revisionEsperada,
            String tipo,
            boolean descarte) {
        var cabecera = modelos.cabecera(modelo);
        autorizarLectura(actor, cabecera.proyecto());
        var repetida = conflictos.porSolicitud(solicitud);
        if (repetida.isPresent())
            return repeticion(actor, modelo, id, tipo, descarte, repetida.get());

        long revisionActual = conflictos.bloquearRevision(modelo);
        cabecera = modelos.cabecera(modelo);
        Fila fila = conflictos.buscar(modelo, id, true);
        if (!"ABIERTO".equals(fila.estado()))
            throw new ApiException(409, "El conflicto ya fue atendido; actualiza la comparación.");
        if (revisionActual != revisionEsperada)
            throw new ApiException(
                    409,
                    "El modelo cambió durante la revisión. Actualiza la comparación antes de"
                            + " decidir.");
        autorizarResolucion(actor, cabecera);
        colaboracion.validarCambio(actor, modelo, sesion);

        JsonNode base = conflictos.instantanea(modelo, fila.revisionBase());
        JsonNode actual = conflictos.instantaneaActual(modelo);
        List<Accion> entrantes = acciones(fila);
        var comparacion = comparador.comparar(base, actual, entrantes);
        UUID cambioResolucion = null;
        UUID version = null;
        Long revision = null;
        JsonNode valorFinal = comparacion.confirmadoActual();

        if (!descarte && !"CONSERVAR_ALMACENADO".equals(tipo)) {
            List<Accion> acciones =
                    "FUSIONAR".equals(tipo)
                            ? comparador.fusionar(base, actual, entrantes)
                            : entrantes;
            acciones = omitirEliminacionesYaAplicadas(acciones, comparacion.confirmadoActual());
            if (!acciones.isEmpty()) {
                revision = modelos.iniciar(modelo, revisionActual, actor.id());
                for (Accion accion : acciones) {
                    modelos.exigirOrganizacion(actor.id(), modelo, accion);
                    if (estaEliminado(comparacion.confirmadoActual(), accion)
                            && !elementos.restaurarSiEliminado(modelo, actor.id(), accion))
                        throw new ApiException(
                                422,
                                "El elemento fue eliminado y no puede reactivarse sin sus"
                                        + " dependencias activas.");
                    elementos.aplicar(modelo, actor.id(), accion);
                }
                JsonNode candidata = conflictos.instantaneaActual(modelo);
                validarConsistencia(actual, candidata);
                String parche = escribir(acciones);
                Accion primera = acciones.getFirst();
                cambioResolucion =
                        modelos.registrar(
                                modelo,
                                sesion,
                                solicitud,
                                actor.id(),
                                revisionActual,
                                revision,
                                primera.operacion(),
                                acciones.size() == 1 ? primera.entidad() : "MODELO_UML",
                                acciones.size() == 1 ? primera.idEntidad() : modelo,
                                parche);
                version =
                        modelos.guardar(
                                modelo, revision, actor.id(), "Resolución de conflicto: " + tipo);
                colaboracion.confirmarActividad(sesion);
                valorFinal = comparador.comparar(base, candidata, entrantes).confirmadoActual();
            }
        }

        String estado = descarte ? "DESCARTADO" : "RESUELTO";
        conflictos.finalizar(
                id,
                solicitud,
                actor.id(),
                estado,
                descarte ? null : tipo,
                valorFinal,
                cambioResolucion);
        conflictos.rechazarEntrante(
                fila.cambioEntrante() == null ? null : fila.cambioEntrante().id());
        actividad.registrar(
                cabecera.proyecto(),
                actor.id(),
                "COLABORACION",
                descarte ? "DESCARTAR_CONFLICTO" : "RESOLVER_CONFLICTO",
                "CONFLICTO_EDICION",
                id,
                modelo,
                version,
                fila.valorAlmacenado().toString(),
                valorFinal.toString(),
                actividad.escribir(
                        new LinkedHashMap<>(
                                Map.of(
                                        "idSolicitudResolucion",
                                        solicitud,
                                        "revisionBase",
                                        fila.revisionBase(),
                                        "revisionEsperada",
                                        revisionEsperada,
                                        "estado",
                                        estado,
                                        "tipoResolucion",
                                        descarte ? "DESCARTAR" : tipo))));
        if (revision != null)
            eventos.publishEvent(
                    new ModeloConfirmado(modelo, sesion, solicitud, actor.id(), revision, version));
        eventos.publishEvent(
                new ConflictoServicio.ConflictoActualizado(
                        modelo, id, estado, revision == null ? revisionActual : revision));
        Fila finalizada = conflictos.buscar(modelo, id, false);
        return new Resultado(
                ConflictoServicio.detalle(
                        actor,
                        finalizada,
                        revision == null ? revisionActual : revision,
                        conflictos.instantanea(modelo, finalizada.revisionBase()),
                        conflictos.instantaneaActual(modelo),
                        comparador,
                        puedeResolver(actor, cabecera)),
                revision,
                version,
                false);
    }

    private Resultado repeticion(
            Identidad actor, UUID modelo, UUID id, String tipo, boolean descarte, Fila fila) {
        boolean coincide =
                fila.id().equals(id)
                        && fila.modelo().equals(modelo)
                        && actor.id().equals(fila.resueltoPor())
                        && (descarte
                                ? "DESCARTADO".equals(fila.estado())
                                : "RESUELTO".equals(fila.estado())
                                        && Objects.equals(tipo, fila.tipoResolucion()));
        if (!coincide)
            throw new ApiException(409, "El UUID de resolución ya identifica otra solicitud.");
        var cabecera = modelos.cabecera(modelo);
        long actual = cabecera.revision();
        return new Resultado(
                ConflictoServicio.detalle(
                        actor,
                        fila,
                        actual,
                        conflictos.instantanea(modelo, fila.revisionBase()),
                        conflictos.instantaneaActual(modelo),
                        comparador,
                        puedeResolver(actor, cabecera)),
                conflictos.revisionCambio(fila.cambioResolucion()),
                conflictos.versionCambio(fila.cambioResolucion()),
                true);
    }

    private void validarConsistencia(JsonNode antes, JsonNode despues) {
        JsonNode reglas = conflictos.reglas();
        var previos = errores(new MotorSemantico().evaluar(antes, reglas));
        var posteriores = errores(new MotorSemantico().evaluar(despues, reglas));
        posteriores.removeAll(previos);
        if (!posteriores.isEmpty())
            throw new ApiException(
                    422,
                    "La resolución introduce inconsistencias UML; revisa la propuesta antes de"
                            + " confirmarla.");
    }

    private Set<String> errores(MotorSemantico.Resultado resultado) {
        Set<String> errores = new HashSet<>();
        resultado.hallazgos().stream()
                .filter(h -> "ERROR".equals(h.severidad()))
                .forEach(h -> errores.add(h.codigo() + "|" + h.entidad() + "|" + h.id()));
        return errores;
    }

    private List<Accion> acciones(Fila fila) {
        return comparador.interpretar(fila.parcheEntrante());
    }

    private List<Accion> omitirEliminacionesYaAplicadas(
            List<Accion> acciones, JsonNode confirmados) {
        List<Accion> resultado = new ArrayList<>();
        for (int i = 0; i < acciones.size(); i++) {
            JsonNode valor = confirmados.path(i).path("valor");
            if (!("ELIMINAR".equals(acciones.get(i).operacion()) && valor.isNull()))
                resultado.add(acciones.get(i));
        }
        return List.copyOf(resultado);
    }

    private boolean estaEliminado(JsonNode confirmados, Accion accion) {
        if (!Set.of("ACTUALIZAR", "MOVER").contains(accion.operacion())) return false;
        for (JsonNode valor : confirmados)
            if (accion.idEntidad().toString().equals(valor.path("idEntidad").asText()))
                return valor.path("valor").isNull();
        return false;
    }

    private String escribir(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar la resolución.", e);
        }
    }

    private void autorizarLectura(Identidad actor, UUID proyecto) {
        if (!actor.esAdministrador() && !modelos.permiso(actor.id(), proyecto, "VER_MODELO"))
            throw new ApiException(404, "Modelo no disponible.");
    }

    private void autorizarResolucion(Identidad actor, ModeloRepositorio.Cabecera c) {
        if (!"ACTIVO".equals(c.estadoProyecto()) || "ARCHIVADO".equals(c.estado()))
            throw new ApiException(409, "El proyecto o el modelo no admite resoluciones.");
        if (!(modelos.permiso(actor.id(), c.proyecto(), "EDITAR_MODELO")
                && modelos.permiso(actor.id(), c.proyecto(), "RESOLVER_CONFLICTO")))
            throw new ApiException(403, "Puedes consultar el conflicto, pero no resolverlo.");
    }

    private boolean puedeResolver(Identidad actor, ModeloRepositorio.Cabecera c) {
        return "ACTIVO".equals(c.estadoProyecto())
                && !"ARCHIVADO".equals(c.estado())
                && modelos.permiso(actor.id(), c.proyecto(), "EDITAR_MODELO")
                && modelos.permiso(actor.id(), c.proyecto(), "RESOLVER_CONFLICTO");
    }
}
