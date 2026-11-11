package bo.edu.uml.generacion.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.generacion.dto.ValidacionBackendDto.*;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.ContenidoArchivo;
import bo.edu.uml.generacion.repositorio.ValidacionBackendRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ValidacionBackendServicio {
    private final GeneracionRepositorio generaciones;
    private final ValidacionBackendRepositorio repo;

    public ValidacionBackendServicio(
            GeneracionRepositorio generaciones, ValidacionBackendRepositorio repo) {
        this.generaciones = generaciones;
        this.repo = repo;
    }

    private UUID autorizar(Identidad actor, UUID modelo, String permiso) {
        var c = generaciones.cabecera(modelo);
        if (!"ACTIVO".equals(c.estadoProyecto()))
            throw new ApiException(404, "Modelo no disponible.");
        if (!actor.esAdministrador() && !generaciones.permiso(actor.id(), c.proyecto(), permiso))
            throw new ApiException(
                    403, "No tienes permiso para esta operación del backend generado.");
        return c.proyecto();
    }

    @Transactional(readOnly = true)
    public List<GeneracionDisponible> listar(Identidad actor, UUID modelo) {
        UUID proyecto = autorizar(actor, modelo, "VER_MODELO");
        return repo.listar(
                modelo,
                actor.esAdministrador()
                        || generaciones.permiso(actor.id(), proyecto, "VALIDAR_BACKEND"));
    }

    @Transactional
    public Informe solicitar(Identidad actor, UUID modelo, UUID generacion) {
        autorizar(actor, modelo, "VALIDAR_BACKEND");
        repo.artefacto(modelo, generacion, true);
        Informe ultimo = repo.ultimo(generacion);
        if (ultimo != null && "EN_EJECUCION".equals(ultimo.estado())) return ultimo;
        return repo.informe(repo.crear(generacion, actor.id()));
    }

    @Transactional(readOnly = true)
    public Informe informe(Identidad actor, UUID modelo, UUID generacion) {
        autorizar(actor, modelo, "VER_MODELO");
        repo.artefacto(modelo, generacion, false);
        Informe informe = repo.ultimo(generacion);
        if (informe == null)
            throw new ApiException(404, "Este backend todavía no tiene una validación.");
        return informe;
    }

    @Transactional
    public ContenidoArchivo descargar(Identidad actor, UUID modelo, UUID generacion) {
        autorizar(actor, modelo, "DESCARGAR_ARTEFACTO");
        var artefacto = repo.artefacto(modelo, generacion, true);
        var informe = repo.ultimo(generacion);
        if (informe == null || !informe.puedeDescargar())
            throw new ApiException(
                    409,
                    "La descarga requiere que la última validación del backend esté aprobada.");
        String hash = ValidacionBackendRepositorio.sha256(artefacto.contenido());
        if (!hash.equals(informe.sha256()) || !hash.equals(artefacto.sha256()))
            throw new ApiException(
                    409, "El artefacto cambió después de la validación. Debe volver a validarse.");
        repo.registrarDescarga(artefacto, actor.id());
        return new ContenidoArchivo(artefacto.nombre(), artefacto.contenido());
    }
}
