package bo.edu.uml.validacion.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio;
import bo.edu.uml.validacion.repositorio.ValidacionRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class ValidacionServicio {
    private final ModeloRepositorio modelos;
    private final ValidacionRepositorio repo;

    public ValidacionServicio(ModeloRepositorio modelos, ValidacionRepositorio repo) {
        this.modelos = modelos;
        this.repo = repo;
    }

    private ModeloRepositorio.Cabecera autorizar(Identidad a, UUID modelo, boolean validar) {
        var c = modelos.cabecera(modelo);
        if (!(a.esAdministrador() && !validar)
                && !modelos.permiso(
                        a.id(), c.proyecto(), validar ? "VALIDAR_MODELO" : "VER_MODELO"))
            throw new ApiException(404, "Modelo no disponible para esta operación.");
        return c;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Object solicitar(Identidad a, UUID modelo) {
        return solicitar(a, modelo, null);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Object solicitar(Identidad a, UUID modelo, UUID version) {
        var c = autorizar(a, modelo, true);
        var seleccionada = repo.version(c, version);
        UUID id = repo.crear(seleccionada, a.id());
        return Map.of("id", id, "revision", seleccionada.revision(),
                "version", seleccionada.version(), "estado", "EN_EJECUCION");
    }

    @Transactional(readOnly = true)
    public Object informe(Identidad a, UUID modelo, UUID id) {
        autorizar(a, modelo, false);
        return repo.informe(modelo, id);
    }
}
