package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.modelado.servicio.EdicionTransaccional.ModeloConfirmado;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class CambioConfirmadoListener {
    // La integración WebSocket se añadirá aquí con autorización de suscripciones.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void confirmado(ModeloConfirmado cambio) {
        LoggerFactory.getLogger(getClass())
                .debug(
                        "Cambio confirmado: modelo={} revisión={}",
                        cambio.modelo(),
                        cambio.revision());
    }
}
