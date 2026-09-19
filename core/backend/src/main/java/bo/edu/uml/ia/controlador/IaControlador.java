package bo.edu.uml.ia.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.ia.dto.IaDto.*;
import bo.edu.uml.ia.servicio.IaServicio;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/modelos/{modelo}/ia")
public class IaControlador {
    private final IaServicio servicio;

    public IaControlador(IaServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/solicitud/texto")
    ResponseEntity<ResultadoSolicitud> texto(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @Valid @RequestBody SolicitudTexto solicitud) {
        return resultado(servicio.solicitarTexto(actor, modelo, solicitud));
    }

    @PostMapping(value = "/solicitud/voz", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResultadoSolicitud> voz(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart(value = "texto", required = false) String texto) {
        return resultado(servicio.solicitarVoz(actor, modelo, archivo, texto));
    }

    @PostMapping(value = "/solicitud/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResultadoSolicitud> imagen(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart(value = "textoDetectado", required = false) String textoDetectado) {
        return resultado(servicio.solicitarImagen(actor, modelo, archivo, textoDetectado));
    }

    @GetMapping("/propuestas/vigente")
    ResponseEntity<Propuesta> vigente(
            @AuthenticationPrincipal Identidad actor, @PathVariable UUID modelo) {
        return servicio.vigente(actor, modelo)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/propuestas/{propuesta}")
    Propuesta detalle(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID propuesta) {
        return servicio.detalle(actor, modelo, propuesta);
    }

    @PostMapping("/propuestas/{propuesta}/aplicar")
    ResponseEntity<ResultadoAplicacion> aplicar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID propuesta) {
        ResultadoAplicacion resultado = servicio.aplicar(actor, modelo, propuesta);
        return resultado.aplicada()
                ? ResponseEntity.ok(resultado)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(resultado);
    }

    @PostMapping("/propuestas/{propuesta}/rechazar")
    ResultadoRechazo rechazar(
            @AuthenticationPrincipal Identidad actor,
            @PathVariable UUID modelo,
            @PathVariable UUID propuesta,
            @Valid @RequestBody(required = false) Rechazo rechazo) {
        return servicio.rechazar(actor, modelo, propuesta, rechazo);
    }

    private ResponseEntity<ResultadoSolicitud> resultado(ResultadoSolicitud resultado) {
        return resultado.idPropuesta() == null
                ? ResponseEntity.unprocessableEntity().body(resultado)
                : ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }
}
