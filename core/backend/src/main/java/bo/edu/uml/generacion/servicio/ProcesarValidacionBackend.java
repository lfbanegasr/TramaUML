package bo.edu.uml.generacion.servicio;

import bo.edu.uml.generacion.dto.ValidacionBackendDto.*;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio;
import bo.edu.uml.generacion.repositorio.ValidacionBackendRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
public class ProcesarValidacionBackend {
    private final ValidacionBackendRepositorio repo;
    private final GeneracionRepositorio generaciones;
    private final CompiladorBackendAislado compilador;
    private final TransactionTemplate tx;

    public ProcesarValidacionBackend(
            ValidacionBackendRepositorio repo,
            GeneracionRepositorio generaciones,
            CompiladorBackendAislado compilador,
            PlatformTransactionManager transacciones) {
        this.repo = repo;
        this.generaciones = generaciones;
        this.compilador = compilador;
        this.tx = new TransactionTemplate(transacciones);
    }

    public void procesar(UUID id) {
        var trabajo = tx.execute(s -> repo.tomar(id));
        if (trabajo == null) return;
        Resultado resultado;
        try {
            var entrada =
                    tx.execute(
                            s -> {
                                var a = trabajo.artefacto();
                                var cfg =
                                        generaciones.leerConfiguracion(
                                                a.modelo(), a.configuracion(), false);
                                var snapshot =
                                        generaciones
                                                .version(
                                                        a.modelo(),
                                                        cfg.idVersionModelo(),
                                                        cfg.idVersionModelo())
                                                .instantanea();
                                return new CompiladorBackendAislado.Entrada(
                                        a,
                                        cfg,
                                        snapshot,
                                        generaciones.clasesConfiguracion(cfg.id()),
                                        generaciones.mapeosTipo(cfg.id()),
                                        generaciones.dependencias(cfg.id()));
                            });
            // La compilación larga ocurre fuera de la transacción y de las conexiones JDBC.
            resultado = compilador.validar(entrada);
        } catch (Exception e) {
            resultado =
                    new Resultado(
                            "FALLIDO",
                            0,
                            List.of(
                                    Hallazgo.error(
                                            "COMPILACION",
                                            "No se pudo preparar la validación. Puede volver a"
                                                + " solicitarse.")),
                            "",
                            ValidacionBackendRepositorio.sha256(trabajo.artefacto().contenido()));
        }
        Resultado fin = resultado;
        tx.executeWithoutResult(s -> repo.completar(trabajo, fin));
    }
}
