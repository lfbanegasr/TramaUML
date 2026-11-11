package bo.edu.uml.validacion.servicio;

import bo.edu.uml.validacion.repositorio.ValidacionRepositorio;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "uml.validacion.worker", havingValue = "true", matchIfMissing = true)
public class WorkerValidacion {
    private final ValidacionRepositorio repo;
    private final ProcesarValidacion procesador;
    private final Set<UUID> activos = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor pool =
            new ThreadPoolExecutor(
                    2,
                    2,
                    0,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(4),
                    r -> {
                        var h = new Thread(r, "validacion-uml");
                        h.setDaemon(true);
                        return h;
                    });

    public WorkerValidacion(ValidacionRepositorio repo, ProcesarValidacion procesador) {
        this.repo = repo;
        this.procesador = procesador;
    }

    @Scheduled(fixedDelay = 1500, initialDelay = 1500)
    public void consultar() {
        for (UUID id : repo.pendientes())
            if (activos.add(id)) {
                try {
                    pool.execute(
                            () -> {
                                try {
                                    procesador.procesar(id);
                                } catch (Exception e) {
                                    org.slf4j.LoggerFactory.getLogger(getClass())
                                            .error("Falló validación id={}", id);
                                    try {
                                        procesador.fallar(id);
                                    } catch (Exception pendiente) {
                                        org.slf4j.LoggerFactory.getLogger(getClass())
                                                .error("No se pudo cerrar validación id={}", id);
                                    }
                                } finally {
                                    activos.remove(id);
                                }
                            });
                } catch (RejectedExecutionException e) {
                    activos.remove(id);
                }
            }
    }

    @PreDestroy
    public void cerrar() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
