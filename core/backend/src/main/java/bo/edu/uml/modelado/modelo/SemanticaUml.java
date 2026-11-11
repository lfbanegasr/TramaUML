package bo.edu.uml.modelado.modelo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/** Reglas del subconjunto de clases UML 2.5.1 compartidas por edición y propuestas. */
public final class SemanticaUml {
    private SemanticaUml() {}

    /**
     * Los extremos fijan general/específico y cliente/proveedor; dirección solo navega
     * asociaciones.
     */
    public static void direccion(String tipo, String direccion) {
        if (Set.of("GENERALIZACION", "DEPENDENCIA").contains(tipo)
                && !Set.of("SIN_DIRECCION", "ORIGEN_A_DESTINO").contains(direccion))
            throw ReglasElemento.invalido(
                    "La relación dirigida va de origen (específico/cliente) a destino"
                        + " (general/proveedor). Invierte sus extremos para cambiarla.");
    }

    /**
     * Operation::at_most_one_return. tipoRetorno conserva el contrato anterior de retorno escalar.
     */
    public static String errorRetorno(
            String retorno, Iterable<JsonNode> parametros, String campoTipo) {
        int retornos = 0;
        for (JsonNode p : parametros) {
            if (!"RETORNO".equals(p.path("direccion").asText())) continue;
            if (++retornos > 1) return "Una operación admite como máximo un parámetro de retorno.";
            if (retorno != null
                    && !retorno.isBlank()
                    && !retorno.equals(p.path(campoTipo).asText()))
                return "El tipo de retorno de la operación contradice su parámetro RETORNO.";
        }
        return null;
    }
}
