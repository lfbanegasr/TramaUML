package bo.edu.uml.despliegue;

import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("despliegue")
public class SaludControlador {
  private final JdbcTemplate jdbc;
  private final ApplicationAvailability disponibilidad;

  public SaludControlador(JdbcTemplate jdbc, ApplicationAvailability disponibilidad) {
    this.jdbc = jdbc;
    this.disponibilidad = disponibilidad;
  }

  @GetMapping("/salud")
  public ResponseEntity<Map<String, String>> salud() {
    if (disponibilidad.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC)
      return respuesta(503, "INICIANDO");
    try {
      return Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class))
          ? respuesta(200, "DISPONIBLE")
          : respuesta(503, "NO_DISPONIBLE");
    } catch (DataAccessException e) {
      return respuesta(503, "NO_DISPONIBLE");
    }
  }

  private ResponseEntity<Map<String, String>> respuesta(int codigo, String estado) {
    return ResponseEntity.status(codigo)
        .header("Cache-Control", "no-store")
        .body(Map.of("estado", estado));
  }
}
