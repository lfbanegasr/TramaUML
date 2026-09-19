package bo.edu.uml.xmi.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.xmi.dto.XmiDto.*;
import bo.edu.uml.xmi.repositorio.XmiRepositorio.ContenidoArchivo;
import bo.edu.uml.xmi.servicio.XmiServicio;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controlador REST para CU28 — Exportación XMI.
 *
 * <ul>
 *   <li>POST {@code /api/modelos/{modelo}/xmi/exportar} — genera y registra el XMI.
 *   <li>GET {@code /api/modelos/{modelo}/xmi/{id}/descargar} — descarga el archivo generado.
 * </ul>
 */
@RestController
@RequestMapping("/api/modelos/{modelo}/xmi")
public class XmiControlador {

    private final XmiServicio servicio;

    public XmiControlador(XmiServicio servicio) {
        this.servicio = servicio;
    }

    /** CU28 — Genera el XMI del modelo y devuelve metadatos del intercambio creado. */
    @PostMapping("/exportar")
    public ResultadoExportacion exportar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @Valid @RequestBody SolicitudExportacion solicitud) {
        return servicio.exportar(actor, modelo, solicitud);
    }

    /** CU28 — Descarga el archivo XMI previamente generado como attachment. */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        ContenidoArchivo archivo = servicio.descargar(actor, modelo, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/xml"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo.nombre() + "\"")
                .body(archivo.contenido());
    }
}
