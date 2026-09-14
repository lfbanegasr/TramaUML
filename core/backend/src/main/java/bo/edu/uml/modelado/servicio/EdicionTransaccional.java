package bo.edu.uml.modelado.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.modelado.dto.ModeladoDto.*;
import bo.edu.uml.modelado.repositorio.*;
import bo.edu.uml.compartido.ApiException;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EdicionTransaccional {
    private final ModeloRepositorio modelos;
    private final ElementoRepositorio elementos;
    private final ApplicationEventPublisher eventos;
    public EdicionTransaccional(ModeloRepositorio modelos,ElementoRepositorio elementos,ApplicationEventPublisher eventos) {this.modelos=modelos;this.elementos=elementos;this.eventos=eventos;}
    public record ModeloConfirmado(UUID modelo,UUID actor,long revision,UUID version) {}
    @Transactional
    public Confirmacion aplicar(Identidad actor,UUID modelo,Cambio cambio,String parche) {
        autorizar(actor,modelo,"EDITAR_MODELO",false);
        var previo=modelos.repeticion(modelo,cambio.idCambioCliente(),actor.id(),cambio.revisionBase(),parche);
        if(previo.isPresent()) return previo.get();
        long revision=modelos.iniciar(modelo,cambio.revisionBase(),actor.id());
        for(Accion a:cambio.acciones()) {
            if(a.operacion().equals("MOVER") && !modelos.permiso(actor.id(),modelos.cabecera(modelo).proyecto(),"ORGANIZAR_MODELO"))
                throw new ApiException(403,"No tienes permiso para organizar el diagrama.");
            elementos.aplicar(modelo,actor.id(),a);
        }
        Accion primera=cambio.acciones().getFirst();
        modelos.registrar(modelo,cambio.idCambioCliente(),actor.id(),cambio.revisionBase(),revision,primera.operacion(),
            cambio.acciones().size()==1?primera.entidad():"MODELO_UML",cambio.acciones().size()==1?primera.idEntidad():modelo,parche);
        // JDBC ejecuta inmediatamente; no existen escrituras JPA pendientes de flush.
        UUID version=modelos.guardar(modelo,revision,actor.id(),"Edición manual: "+cambio.acciones().size()+" operación(es)");
        eventos.publishEvent(new ModeloConfirmado(modelo,actor.id(),revision,version));
        return new Confirmacion(cambio.idCambioCliente(),revision,version,false);
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public Optional<Confirmacion> recuperarReenvio(Identidad actor,UUID modelo,Cambio cambio,String parche) {
        autorizar(actor,modelo,"EDITAR_MODELO",false);
        return modelos.repeticion(modelo,cambio.idCambioCliente(),actor.id(),cambio.revisionBase(),parche);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Modelo leer(Identidad actor,UUID id) {
        autorizar(actor,id,"VER_MODELO",true);return modelos.leer(modelos.cabecera(id),actor);
    }
    private void autorizar(Identidad actor,UUID id,String permiso,boolean lectura) {
        var c=modelos.cabecera(id);
        if(!(lectura && actor.esAdministrador()) && !modelos.permiso(actor.id(),c.proyecto(),permiso))
            throw new ApiException(404,"Modelo no disponible.");
    }
}
