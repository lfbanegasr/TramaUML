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
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Controlador REST para importación y exportación XMI.
 *
 * <ul>
 *   <li>POST {@code /api/modelos/{modelo}/xmi/importaciones} — analiza sin aplicar.
 *   <li>GET {@code /api/modelos/{modelo}/xmi/importaciones/{id}} — previsualiza.
 *   <li>POST {@code /api/modelos/{modelo}/xmi/importaciones/{id}/aplicar} — aplica.
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

    /** Carga y analiza un XMI. El modelo permanece intacto hasta la confirmación posterior. */
    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Importacion importar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestParam UUID idSolicitud,
            @RequestParam(defaultValue = "FUSIONAR") String estrategia,
            @RequestPart("archivo") MultipartFile archivo) {
        return servicio.importar(actor, modelo, idSolicitud, estrategia, archivo);
    }

    /** Consulta la previsualización persistida y su vigencia frente al modelo. */
    @GetMapping("/importaciones/{id}")
    public Importacion consultarImportacion(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        return servicio.consultar(actor, modelo, id);
    }

    /** Aplica toda la previsualización como una sola revisión y versión. */
    @PostMapping("/importaciones/{id}/aplicar")
    public ResultadoAplicacionImportacion aplicarImportacion(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id,
            @Valid @RequestBody SolicitudAplicacionImportacion solicitud) {
        return servicio.aplicar(actor, modelo, id, solicitud);
    }

    /** Cancela una previsualización sin modificar el modelo. */
    @PostMapping("/importaciones/{id}/cancelar")
    public Importacion cancelarImportacion(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SolicitudCancelacion solicitud) {
        return servicio.cancelar(actor, modelo, id, solicitud);
    }

    /** CU28 — Descarga el archivo XMI previamente generado como attachment. */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        ContenidoArchivo archivo = servicio.descargar(actor, modelo, id);
        MediaType tipo = archivo.nombre().endsWith(".vbs")
                ? MediaType.parseMediaType("text/plain; charset=utf-8")
                : MediaType.parseMediaType("application/xml");
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo.nombre() + "\"")
                .body(archivo.contenido());
    }
}
