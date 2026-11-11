package bo.edu.uml.ia.servicio;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "uml.ia.worker", havingValue = "true", matchIfMissing = true)
public class WorkerAgente {
  private final AgenteColaborativoServicio agente;
  private final java.util.concurrent.atomic.AtomicInteger enCurso =
      new java.util.concurrent.atomic.AtomicInteger();
  private final java.util.concurrent.atomic.AtomicInteger indiceHilo =
      new java.util.concurrent.atomic.AtomicInteger(1);
  private final ExecutorService pool =
      Executors.newFixedThreadPool(
          4,
          r -> {
            var h = new Thread(r, "agente-colaborativo-" + indiceHilo.getAndIncrement());
            h.setDaemon(true);
            return h;
          });

  public WorkerAgente(AgenteColaborativoServicio agente) {
    this.agente = agente;
  }

  @Scheduled(fixedDelay = 600, initialDelay = 1500)
  public void consultar() {
    while (enCurso.get() < 4) {
      var reclamo = agente.reclamar();
      if (reclamo.isEmpty()) break;
      var trabajo = reclamo.get();
      enCurso.incrementAndGet();
      try {
        pool.execute(
            () -> {
              try {
                agente.procesar(trabajo);
              } finally {
                enCurso.decrementAndGet();
              }
            });
      } catch (RuntimeException e) {
        enCurso.decrementAndGet();
        throw e;
      }
    }
  }

  @PreDestroy
  public void cerrar() {
    pool.shutdownNow();
  }
}
