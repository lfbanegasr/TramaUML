package bo.edu.uml.proyectos.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.proyectos.dto.ActividadDto.*;
import bo.edu.uml.proyectos.repositorio.ActividadRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Service
public class ActividadServicio {
    private final ProyectoServicio proyectos;
    private final ActividadRepositorio repo;

    public ActividadServicio(ProyectoServicio proyectos, ActividadRepositorio repo) {
        this.proyectos = proyectos;
        this.repo = repo;
    }

    private void autorizar(Identidad actor, UUID id) {
        if (!proyectos.obtener(actor, id).puedeVerActividad())
            throw new ApiException(403, "No tienes permiso para consultar la actividad.");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Pagina listar(
            Identidad actor,
            UUID proyecto,
            UUID responsable,
            String accion,
            String tipo,
            UUID entidad,
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamano) {
        autorizar(actor, proyecto);
        FiltrosConsulta.validar(pagina, tamano, desde, hasta);
        if (accion.length() > 80 || tipo.length() > 64)
            throw new ApiException(400, "Filtros inválidos.");
        return repo.listar(
                proyecto, responsable, accion, tipo, entidad, desde, hasta, pagina, tamano);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detalle detalle(Identidad actor, UUID proyecto, long id) {
        autorizar(actor, proyecto);
        if (id < 1) throw new ApiException(400, "Identificador inválido.");
        return repo.detalle(proyecto, id);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> filtros(Identidad actor, UUID proyecto) {
        autorizar(actor, proyecto);
        return repo.filtros(proyecto);
    }
}
