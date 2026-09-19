package bo.edu.uml.historial.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.historial.dto.HistorialDto.*;
import bo.edu.uml.historial.repositorio.HistorialRepositorio;
import bo.edu.uml.historial.repositorio.HistorialRepositorio.Cabecera;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Servicio de aplicación para CU19 (historial) y CU20 (restauración). Autoriza, oculta modelos
 * ajenos con 404 y delega en {@link HistorialRepositorio}, {@link ComparadorVersiones} y {@link
 * RestauradorTransaccional}.
 */
@Service
public class HistorialServicio {

    private final HistorialRepositorio repo;
    private final ComparadorVersiones comparador;
    private final RestauradorTransaccional restaurador;

    public HistorialServicio(
            HistorialRepositorio repo,
            ComparadorVersiones comparador,
            RestauradorTransaccional restaurador) {
        this.repo = repo;
        this.comparador = comparador;
        this.restaurador = restaurador;
    }

    // ─── CU19 ───────────────────────────────────────────────────────────────

    /**
     * Lista versiones del historial con paginación descendente por cursor. Requiere {@code
     * VER_HISTORIAL_MODELO}.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaginaVersiones listar(
            Identidad actor,
            UUID modelo,
            Integer cursor,
            int limite,
            UUID idAutor,
            LocalDate desde,
            LocalDate hasta) {
        Cabecera c = repo.cabecera(modelo);
        autorizarLectura(actor, c.proyecto());
        if (limite < 1 || limite > 100) limite = 20;
        return repo.listar(modelo, cursor, limite, idAutor, desde, hasta);
    }

    /**
     * Detalle de una versión: metadatos + snapshot validado. Requiere {@code VER_HISTORIAL_MODELO}.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DetalleVersion detalle(Identidad actor, UUID modelo, UUID version) {
        Cabecera c = repo.cabecera(modelo);
        autorizarLectura(actor, c.proyecto());
        return repo.leerDetalle(modelo, version);
    }

    /**
     * Diferencias entre dos versiones del mismo modelo. Requiere {@code VER_HISTORIAL_MODELO}.
     *
     * @param desde UUID de la versión base (más antigua o primera).
     * @param hasta UUID de la versión objetivo (más reciente o segunda).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DifVersion comparar(Identidad actor, UUID modelo, UUID desde, UUID hasta) {
        Cabecera c = repo.cabecera(modelo);
        autorizarLectura(actor, c.proyecto());
        // Ambas deben pertenecer al mismo modelo
        repo.verificarPertenencia(modelo, desde);
        repo.verificarPertenencia(modelo, hasta);
        ResumenVersion resDesde = repo.leerResumen(modelo, desde);
        ResumenVersion resHasta = repo.leerResumen(modelo, hasta);
        JsonNode snapDesde = repo.snapshot(modelo, desde);
        JsonNode snapHasta = repo.snapshot(modelo, hasta);
        return new DifVersion(resDesde, resHasta, comparador.comparar(snapDesde, snapHasta));
    }

    // ─── CU20 ───────────────────────────────────────────────────────────────

    /** Vista previa de restauración sin escribir. Requiere {@code RESTAURAR_MODELO}. */
    @Transactional(readOnly = true)
    public VistaPrevia previsualizacion(
            Identidad actor, UUID modelo, SolicitarVistaPrevia solicitud) {
        return restaurador.previsualizacion(actor, modelo, solicitud.idVersionOrigen());
    }

    /**
     * Confirma la restauración. Requiere {@code RESTAURAR_MODELO}. Idempotente por UUID de
     * solicitud.
     */
    public ResultadoRestauracion restaurar(
            Identidad actor, UUID modelo, SolicitarRestauracion solicitud) {
        if (solicitud.idSolicitudRestauracion() == null)
            throw new ApiException(400, "Se requiere un UUID de solicitud de restauración.");
        return restaurador.restaurar(actor, modelo, solicitud);
    }

    // ─── Autorización ───────────────────────────────────────────────────────

    private void autorizarLectura(Identidad actor, UUID proyecto) {
        if (!actor.esAdministrador() && !repo.permiso(actor.id(), proyecto, "VER_HISTORIAL_MODELO"))
            throw new ApiException(404, "Modelo no disponible.");
    }
}
