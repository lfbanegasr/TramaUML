package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.colaboracion.controlador.ColaboracionWebSocketHandler;
import bo.edu.uml.colaboracion.servicio.ColaboracionServicio.PresenciaConfirmada;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class PresenciaConfirmadaListener {
    private final ColaboracionWebSocketHandler canal;

    public PresenciaConfirmadaListener(ColaboracionWebSocketHandler canal) {
        this.canal = canal;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void confirmada(PresenciaConfirmada evento) {
        canal.publicarPresencia(evento.sesion());
    }
}
