package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.colaboracion.controlador.ColaboracionWebSocketHandler;
import bo.edu.uml.colaboracion.servicio.ColaboracionServicio.AccesoProyectoCambiado;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class AccesoProyectoCambiadoListener {
    private final ColaboracionWebSocketHandler canal;

    public AccesoProyectoCambiadoListener(ColaboracionWebSocketHandler canal) {
        this.canal = canal;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cambiado(AccesoProyectoCambiado evento) {
        canal.revisarAccesos(evento.proyecto());
    }
}
