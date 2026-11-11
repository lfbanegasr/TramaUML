package bo.edu.uml.generacion.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.generacion.dto.GeneracionDto.*;
import bo.edu.uml.generacion.dto.ValidacionBackendDto.*;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.ContenidoArchivo;
import bo.edu.uml.generacion.servicio.GeneracionServicio;
import bo.edu.uml.generacion.servicio.ValidacionBackendServicio;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para CU29/CU30/CU32 — Generación de backend Spring Boot.
 *
 * <ul>
 *   <li>GET {@code /api/modelos/{modelo}/generacion/configuracion} — lee la configuración vigente.
 *   <li>PUT {@code /api/modelos/{modelo}/generacion/configuracion} — guarda la configuración.
 *   <li>POST {@code /api/modelos/{modelo}/generacion/generar} — genera el proyecto ZIP.
 *   <li>GET {@code /api/modelos/{modelo}/generacion/{id}/descargar} — descarga el ZIP.
 * </ul>
 */
@RestController
@RequestMapping("/api/modelos/{modelo}/generacion")
public class GeneracionControlador {

    private final GeneracionServicio servicio;
    private final ValidacionBackendServicio validaciones;

    public GeneracionControlador(
            GeneracionServicio servicio, ValidacionBackendServicio validaciones) {
        this.servicio = servicio;
        this.validaciones = validaciones;
    }

    @GetMapping
    public List<GeneracionDisponible> listar(
            @AuthenticationPrincipal Identidad actor, @PathVariable UUID modelo) {
        return validaciones.listar(actor, modelo);
    }

    @PostMapping("/{id}/validaciones")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public Informe validar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        return validaciones.solicitar(actor, modelo, id);
    }

    @GetMapping("/{id}/validacion")
    public Informe informe(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        return validaciones.informe(actor, modelo, id);
    }

    /** CU29 — Consulta la configuración de generación vigente (o devuelve defaults). */
    @GetMapping("/configuracion")
    public ConfiguracionGeneracion leerConfiguracion(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestParam(required = false) UUID version) {
        return servicio.leerConfiguracion(actor, modelo, version);
    }

    /** CU29 — Guarda o actualiza la configuración de generación del modelo. */
    @PutMapping("/configuracion")
    public ConfiguracionGeneracion guardarConfiguracion(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @Valid @RequestBody ActualizarConfiguracion configuracion) {
        return servicio.guardarConfiguracion(actor, modelo, configuracion);
    }

    /** CU30 — Genera el proyecto Spring Boot completo en 4 capas y devuelve metadatos. */
    @PostMapping("/generar")
    public ResultadoGeneracion generar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestBody(required = false) SolicitudGeneracion solicitud) {
        return servicio.generar(actor, modelo, solicitud);
    }

    /** CU32 — Descarga el ZIP del proyecto generado como attachment. */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID id) {
        ContenidoArchivo archivo = servicio.descargar(actor, modelo, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo.nombre() + "\"")
                .body(archivo.contenido());
    }
}
