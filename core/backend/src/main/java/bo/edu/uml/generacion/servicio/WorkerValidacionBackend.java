package bo.edu.uml.generacion.servicio;

import bo.edu.uml.generacion.repositorio.ValidacionBackendRepositorio;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "uml.compilacion.worker", havingValue = "true", matchIfMissing = true)
public class WorkerValidacionBackend {
    private final ValidacionBackendRepositorio repo;
    private final ProcesarValidacionBackend procesador;
    private final AtomicBoolean ocupado = new AtomicBoolean();
    private final ExecutorService pool =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread hilo = new Thread(r, "compilacion-backend");
                        hilo.setDaemon(true);
                        return hilo;
                    });

    public WorkerValidacionBackend(
            ValidacionBackendRepositorio repo, ProcesarValidacionBackend procesador) {
        this.repo = repo;
        this.procesador = procesador;
    }

    @Scheduled(fixedDelay = 1200, initialDelay = 1200)
    public void consultar() {
        repo.expirar();
        if (!ocupado.compareAndSet(false, true)) return;
        try {
            var pendientes = repo.pendientes();
            if (pendientes.isEmpty()) {
                ocupado.set(false);
                return;
            }
            pool.execute(
                    () -> {
                        try {
                            procesador.procesar(pendientes.getFirst());
                        } catch (Exception e) {
                            org.slf4j.LoggerFactory.getLogger(getClass())
                                    .error(
                                            "No se pudo persistir la validación del backend {}.",
                                            pendientes.getFirst(),
                                            e);
                        } finally {
                            ocupado.set(false);
                        }
                    });
        } catch (RuntimeException e) {
            ocupado.set(false);
            throw e;
        }
    }

    @PreDestroy
    public void cerrar() {
        pool.shutdownNow();
    }
}
