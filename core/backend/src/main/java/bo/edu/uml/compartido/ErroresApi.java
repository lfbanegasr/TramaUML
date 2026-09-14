package bo.edu.uml.compartido;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.Map;

@RestControllerAdvice
public class ErroresApi {
    public static String sqlState(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause())
            if (t instanceof SQLException s) return s.getSQLState();
        return "";
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> negocio(ApiException e) {
        return error(e.status(), e.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<?> entrada(Exception e) {
        return error(400, "Revisa los datos enviados.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> persistencia(DataAccessException e) {
        return switch (sqlState(e)) {
            case "40001", "40P01", "55P03", "57014" ->
                    error(409, "La operación no se confirmó. Recarga y vuelve a revisar.");
            case "42501" -> error(403, "No tienes permiso para realizar esta operación.");
            case "23505" -> error(409, "Ya existe un elemento con esos datos.");
            case "23514", "23503", "22003" ->
                    error(422, "Los datos no cumplen las reglas del modelo.");
            default -> {
                LoggerFactory.getLogger(getClass())
                        .error("Fallo de persistencia; SQLSTATE={}", sqlState(e));
                yield error(503, "La base de datos no está disponible para esta operación.");
            }
        };
    }

    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("mensaje", message));
    }
}
