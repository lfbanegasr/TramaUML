package bo.edu.uml.proyectos.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.repositorio.ModeloRepositorio;
import bo.edu.uml.proyectos.dto.ProyectoDto.*;
import bo.edu.uml.proyectos.repositorio.ProyectoRepositorio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class ProyectoServicio {
    private final ProyectoRepositorio repositorio;
    private final ModeloRepositorio modelos;

    public ProyectoServicio(ProyectoRepositorio repositorio, ModeloRepositorio modelos) {
        this.repositorio = repositorio;
        this.modelos = modelos;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Pagina listar(Identidad actor, String filtro, String estado, int pagina, int tamano) {
        if (pagina < 0
                || pagina > 100000
                || tamano < 1
                || tamano > 100
                || filtro.length() > 180
                || !Set.of("", "ACTIVO", "ARCHIVADO", "SUSPENDIDO").contains(estado))
            throw new ApiException(400, "Filtros inválidos.");
        return repositorio.listar(actor, filtro, estado, pagina, tamano);
    }

    @Transactional(readOnly = true)
    public Resumen obtener(Identidad actor, UUID id) {
        return repositorio
                .obtener(actor, id)
                .orElseThrow(() -> new ApiException(404, "Proyecto no disponible."));
    }

    @Transactional
    public Resumen crear(Identidad actor, Crear datos) {
        if (!actor.puedeCrearProyectos())
            throw new ApiException(403, "No tienes permiso para crear proyectos.");
        UUID proyecto = UUID.randomUUID(), modelo = UUID.randomUUID();
        repositorio.crear(proyecto, actor, datos, modelo);
        // El modelo vacío también se confirma mediante el contrato; no se fabrica una instantánea.
        long revision = modelos.iniciar(modelo, 0, actor.id());
        modelos.registrar(
                modelo,
                UUID.randomUUID(),
                actor.id(),
                0,
                revision,
                "CREAR",
                "MODELO_UML",
                modelo,
                "{\"inicializacion\":true}");
        modelos.guardar(modelo, revision, actor.id(), "Modelo inicial vacío");
        return repositorio.obtener(actor, proyecto).orElseThrow();
    }
}
