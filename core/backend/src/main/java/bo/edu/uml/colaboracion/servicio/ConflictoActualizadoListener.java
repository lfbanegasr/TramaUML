package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.colaboracion.controlador.ColaboracionWebSocketHandler;
import bo.edu.uml.colaboracion.servicio.ConflictoServicio.ConflictoActualizado;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class ConflictoActualizadoListener {
    private final ColaboracionWebSocketHandler canal;

    public ConflictoActualizadoListener(ColaboracionWebSocketHandler canal) {
        this.canal = canal;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void confirmado(ConflictoActualizado conflicto) {
        canal.publicarConflicto(conflicto);
    }
}
