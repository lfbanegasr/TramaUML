package bo.edu.uml.validacion.servicio;

import bo.edu.uml.validacion.modelo.MotorSemantico;
import bo.edu.uml.validacion.repositorio.ValidacionRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProcesarValidacion {
    private final ValidacionRepositorio repo;

    public ProcesarValidacion(ValidacionRepositorio repo) {
        this.repo = repo;
    }

    @Transactional
    public void procesar(UUID id) {
        var trabajo = repo.trabajo(id);
        var resultado = new MotorSemantico().evaluar(trabajo.instantanea(), trabajo.reglas());
        repo.completar(trabajo, resultado);
    }

    @Transactional
    public void fallar(UUID id) {
        repo.fallar(id);
    }
}
