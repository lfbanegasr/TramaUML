package bo.edu.uml.modelado.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.servicio.ConflictoServicio;
import bo.edu.uml.compartido.*;
import bo.edu.uml.modelado.dto.ModeladoDto.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Frontera fuera de la transacción para poder consultar un reenvío después del rollback. */
@Service
public class ModeladoServicio {
    private final EdicionTransaccional edicion;
    private final ObjectMapper json;
    private final ConflictoServicio conflictos;

    public ModeladoServicio(
            EdicionTransaccional edicion, ObjectMapper json, ConflictoServicio conflictos) {
        this.edicion = edicion;
        this.json = json;
        this.conflictos = conflictos;
    }

    public Modelo leer(Identidad actor, UUID modelo) {
        return edicion.leer(actor, modelo);
    }

    public Confirmacion aplicar(Identidad actor, UUID modelo, Cambio cambio) {
        String parche;
        try {
            parche = json.writeValueAsString(cambio.acciones());
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "Cambio inválido.");
        }
        if (parche.length() > 200000)
            throw new ApiException(413, "El cambio supera el tamaño permitido.");
        try {
            return edicion.aplicar(actor, modelo, cambio, parche);
        } catch (DataAccessException e) {
            if (java.util.Set.of("40001", "23505").contains(ErroresApi.sqlState(e))) {
                var previo = edicion.recuperarReenvio(actor, modelo, cambio, parche);
                if (previo.isPresent()) return previo.get();
                if ("40001".equals(ErroresApi.sqlState(e))) {
                    UUID idConflicto = conflictos.registrar(actor, modelo, cambio, parche);
                    throw new ApiException(
                            409,
                            "El modelo cambió y la propuesta se conservó como conflicto "
                                    + idConflicto
                                    + ". Revisa las diferencias antes de decidir.");
                }
            }
            throw e;
        }
    }
}
