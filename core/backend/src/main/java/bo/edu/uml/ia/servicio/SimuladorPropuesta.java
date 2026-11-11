package bo.edu.uml.ia.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.OperacionGenerada;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;
import bo.edu.uml.modelado.repositorio.*;
import bo.edu.uml.validacion.modelo.MotorSemantico;
import bo.edu.uml.validacion.modelo.MotorSemantico.Hallazgo;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ensayo con las reglas reales del editor, siempre revertido: sin revisión, historial ni eventos.
 */
@Service
public class SimuladorPropuesta {
  private final TransactionTemplate ensayo;
  private final NamedParameterJdbcTemplate db;
  private final ElementoRepositorio elementos;
  private final ModeloRepositorio modelos;
  private final ObjectMapper json;

  public SimuladorPropuesta(
      PlatformTransactionManager tm,
      NamedParameterJdbcTemplate db,
      ElementoRepositorio elementos,
      ModeloRepositorio modelos,
      ObjectMapper json) {
    this.ensayo = new TransactionTemplate(tm);
    ensayo.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    ensayo.setTimeout(15);
    this.db = db;
    this.elementos = elementos;
    this.modelos = modelos;
    this.json = json;
  }

  public record Informe(
      List<Hallazgo> nuevos, List<Hallazgo> anteriores, List<Hallazgo> generacion) {}

  private String clave(Hallazgo h) {
    return h.codigo() + ":" + h.id() + ":" + h.mensaje();
  }

  public Informe validar(
      Identidad actor, UUID modelo, long revision, List<OperacionGenerada> operaciones) {
    try {
      return ensayo.execute(
          s -> {
            s.setRollbackOnly();
            long actual =
                db.queryForObject(
                    "select revision_actual from uml_colaborativo.modelo_uml where id=:m for"
                        + " update",
                    Map.of("m", modelo),
                    Long.class);
            if (actual != revision)
              throw new ApiException(
                  409, "El modelo cambió durante el diseño. Envía una solicitud nueva.");
            JsonNode reglas =
                json.valueToTree(
                    db.queryForList(
                        "select id,codigo::text codigo,severidad,clave_implementacion from"
                            + " uml_colaborativo.regla_validacion where esta_activo",
                        Map.of()));
            var antes = new MotorSemantico().evaluar(instantanea(modelo), reglas).hallazgos();
            // Los triggers exigen un contexto de edición en ESTA transacción. La reserva
            // y todas sus escrituras quedan dentro del rollback/savepoint del ensayo.
            modelos.iniciar(modelo, revision, actor.id());
            for (var o : operaciones) {
              if (!o.seleccionada()) continue;
              var accion =
                  new Accion(o.tipoOperacion(), o.tipoEntidad(), o.idEntidad(), o.valorPropuesto());
              modelos.exigirOrganizacion(actor.id(), modelo, accion);
              elementos.aplicar(modelo, actor.id(), accion);
            }
            var despues = new MotorSemantico().evaluar(instantanea(modelo), reglas).hallazgos();
            Set<String> previos = new HashSet<>();
            antes.forEach(h -> previos.add(clave(h)));
            return new Informe(
                despues.stream()
                    .filter(
                        h ->
                            "ERROR".equals(h.severidad())
                                && !previos.contains(clave(h))
                                && !generacion(h))
                    .toList(),
                antes,
                despues.stream().filter(this::generacion).toList());
          });
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      String estado =
          e.getMostSpecificCause() instanceof java.sql.SQLException sql ? sql.getSQLState() : "";
      throw new ApiException(
          422,
          switch (estado) {
            case "23505" ->
                "La propuesta duplica un nombre, firma o identificador en su contenedor.";
            case "23503" -> "La propuesta contiene referencias inexistentes o incompatibles.";
            default ->
                "La propuesta incumple una restricción del modelo: revisa multiplicidades,"
                    + " composición, ciclos y campos obligatorios.";
          });
    }
  }

  private boolean generacion(Hallazgo h) {
    return h.codigo().startsWith("GENERACION_")
        || h.codigo().contains("PERSIST")
        || h.codigo().contains("IDENTIFICADOR");
  }

  private JsonNode instantanea(UUID modelo) {
    try {
      return json.readTree(
          db.queryForObject(
              "select uml_colaborativo.construir_instantanea_modelo(:m)::text",
              Map.of("m", modelo),
              String.class));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
