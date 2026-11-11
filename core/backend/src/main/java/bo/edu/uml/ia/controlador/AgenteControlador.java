package bo.edu.uml.ia.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.ia.dto.AgenteDto.*;
import bo.edu.uml.ia.servicio.AgenteColaborativoServicio;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/modelos/{modelo}/ia/conversacion")
public class AgenteControlador {
  private final AgenteColaborativoServicio agente;

  public AgenteControlador(AgenteColaborativoServicio agente) {
    this.agente = agente;
  }

  @GetMapping
  public Historial historial(
      @AuthenticationPrincipal Identidad actor,
      @PathVariable UUID modelo,
      @RequestParam(defaultValue = "0") long cursor) {
    return agente.historial(actor, modelo, cursor);
  }

  @GetMapping("/capacidades")
  public Map<String, Object> capacidades(
      @AuthenticationPrincipal Identidad actor, @PathVariable UUID modelo) {
    return agente.capacidades(actor, modelo);
  }

  @PostMapping("/mensajes")
  public ResponseEntity<Aceptado> enviar(
      @AuthenticationPrincipal Identidad actor,
      @PathVariable UUID modelo,
      @Valid @RequestBody Entrada entrada) {
    return ResponseEntity.accepted().body(agente.enviar(actor, modelo, entrada));
  }

  @PostMapping(value = "/voz", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Aceptado> voz(
      @AuthenticationPrincipal Identidad actor,
      @PathVariable UUID modelo,
      @RequestParam UUID clave,
      @RequestPart(required = false) MultipartFile archivo,
      @RequestPart(required = false) String texto,
      @RequestParam(defaultValue = "AUTO") String intencion) {
    return ResponseEntity.accepted()
        .body(agente.adjuntar(actor, modelo, clave, archivo, texto, "VOZ", intencion));
  }

  @PostMapping(value = "/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Aceptado> imagen(
      @AuthenticationPrincipal Identidad actor,
      @PathVariable UUID modelo,
      @RequestParam UUID clave,
      @RequestPart MultipartFile archivo,
      @RequestPart(value = "textoDetectado", required = false) String texto,
      @RequestParam(defaultValue = "MODIFICAR") String intencion) {
    return ResponseEntity.accepted()
        .body(agente.adjuntar(actor, modelo, clave, archivo, texto, "IMAGEN", intencion));
  }

  @GetMapping("/ejecuciones/{id}")
  public Ejecucion ejecucion(
      @AuthenticationPrincipal Identidad actor, @PathVariable UUID modelo, @PathVariable UUID id) {
    return agente.ejecucion(actor, modelo, id);
  }

  @PostMapping("/ejecuciones/{id}/cancelar")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelar(
      @AuthenticationPrincipal Identidad actor, @PathVariable UUID modelo, @PathVariable UUID id) {
    agente.cancelar(actor, modelo, id);
  }

  @PostMapping("/acuerdos")
  public List<Acuerdo> confirmar(
      @AuthenticationPrincipal Identidad actor,
      @PathVariable UUID modelo,
      @Valid @RequestBody ConfirmarAcuerdo acuerdo) {
    return agente.confirmar(actor, modelo, acuerdo);
  }
}
