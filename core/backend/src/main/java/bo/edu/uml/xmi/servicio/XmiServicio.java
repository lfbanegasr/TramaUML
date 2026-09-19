package bo.edu.uml.xmi.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.xmi.dto.XmiDto.*;
import bo.edu.uml.xmi.repositorio.XmiRepositorio;
import bo.edu.uml.xmi.repositorio.XmiRepositorio.CabeceraModelo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Servicio de aplicación para CU28 — Exportación XMI. Autoriza, oculta modelos ajenos con 404,
 * delega la generación en {@link GeneradorXmi21Service} y persiste el resultado.
 */
@Service
public class XmiServicio {

    private final XmiRepositorio repo;
    private final GeneradorXmi21Service generador;

    public XmiServicio(XmiRepositorio repo, GeneradorXmi21Service generador) {
        this.repo = repo;
        this.generador = generador;
    }

    // ─── CU28: Exportar XMI ─────────────────────────────────────────────────

    /**
     * Genera el archivo XMI del modelo, lo persiste y devuelve el resultado de exportación.
     * Requiere el permiso {@code EXPORTAR_XMI} sobre el proyecto.
     */
    @Transactional
    public ResultadoExportacion exportar(
            Identidad actor, UUID modelo, SolicitudExportacion solicitud) {
        CabeceraModelo c = repo.cabecera(modelo);
        autorizarExportacion(actor, c);

        var clases = repo.leerClases(modelo);
        var atributos = repo.leerAtributos(modelo);
        var operaciones = repo.leerOperaciones(modelo);
        var parametros = repo.leerParametros(modelo);
        var relaciones = repo.leerRelaciones(modelo);
        var paquetes = repo.leerPaquetes(modelo);

        byte[] contenidoXmi =
                generador.generar(
                        c.nombre(),
                        paquetes,
                        clases,
                        atributos,
                        operaciones,
                        parametros,
                        relaciones);

        String nombreArchivo = nombreArchivo(c.nombre());
        UUID idArchivo = repo.guardarArchivo(c.proyecto(), actor.id(), nombreArchivo, contenidoXmi);
        UUID idIntercambio =
                repo.registrarIntercambio(
                        modelo, c.idVersionActual(), actor.id(), idArchivo, clases.size());

        return new ResultadoExportacion(
                idIntercambio, idArchivo, nombreArchivo, clases.size(), relaciones.size());
    }

    /**
     * Descarga el archivo XMI de un intercambio previamente exportado. Requiere el permiso {@code
     * EXPORTAR_XMI}.
     */
    @Transactional(readOnly = true)
    public XmiRepositorio.ContenidoArchivo descargar(
            Identidad actor, UUID modelo, UUID idIntercambio) {
        CabeceraModelo c = repo.cabecera(modelo);
        autorizarExportacion(actor, c);
        return repo.descargarArchivoPorIntercambio(modelo, idIntercambio);
    }

    // ─── Autorización ────────────────────────────────────────────────────────

    private void autorizarExportacion(Identidad actor, CabeceraModelo c) {
        if (!"ACTIVO".equals(c.estadoProyecto()))
            throw new ApiException(404, "Modelo no disponible.");
        if (actor.esAdministrador()) return;
        if (!repo.permiso(actor.id(), c.proyecto(), "EXPORTAR_XMI"))
            throw new ApiException(403, "No tienes permiso para exportar XMI de este modelo.");
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private String nombreArchivo(String nombreModelo) {
        String seguro = nombreModelo.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return seguro + "_xmi21.xmi";
    }
}
