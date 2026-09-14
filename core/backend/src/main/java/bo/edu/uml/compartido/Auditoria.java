package bo.edu.uml.compartido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Registra identificadores y acciones confirmadas; excluye credenciales y datos personales. */
public final class Auditoria {
    private static final Logger LOG = LoggerFactory.getLogger("auditoria");

    private Auditoria() {}

    public static void despuesDelCommit(UUID actor, String accion, UUID entidad) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        LOG.info("actor={} accion={} entidad={}", actor, accion, entidad);
                    }
                });
    }
}
