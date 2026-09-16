package bo.edu.uml.colaboracion.servicio;

import bo.edu.uml.colaboracion.dto.ConflictoDto.Diferencia;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ComparadorConflicto {
    private final ObjectMapper json;

    public ComparadorConflicto(ObjectMapper json) {
        this.json = json;
    }

    public record Comparacion(
            ArrayNode almacenado,
            ArrayNode entrante,
            ArrayNode confirmadoActual,
            List<Diferencia> diferencias,
            boolean fusionable,
            boolean elementoEliminado) {}

    public List<Accion> interpretar(JsonNode parche) {
        try {
            return json.convertValue(parche, new TypeReference<>() {});
        } catch (IllegalArgumentException e) {
            throw new ApiException(422, "La propuesta guardada no puede interpretarse.");
        }
    }

    public Comparacion comparar(JsonNode base, JsonNode actual, List<Accion> acciones) {
        ArrayNode almacenado = json.createArrayNode();
        ArrayNode entrante = json.createArrayNode();
        ArrayNode confirmado = json.createArrayNode();
        List<Diferencia> diferencias = new ArrayList<>();
        boolean fusionable = true;
        boolean eliminado = false;
        for (int i = 0; i < acciones.size(); i++) {
            Accion accion = acciones.get(i);
            JsonNode valorBase = valor(base, accion);
            JsonNode valorActual = valor(actual, accion);
            JsonNode valorPropuesto = propuesto(accion);
            almacenado.add(registro(accion, valorActual));
            entrante.add(registro(accion, valorPropuesto));
            confirmado.add(registro(accion, valorActual));
            if (!valorBase.isMissingNode() && valorActual.isMissingNode()) eliminado = true;

            Set<String> cambiadasActual = cambios(valorBase, valorActual);
            Set<String> cambiadasPropuesta = cambios(valorBase, valorPropuesto);
            Set<String> propiedades = new TreeSet<>(cambiadasActual);
            propiedades.addAll(cambiadasPropuesta);
            if (propiedades.isEmpty()) propiedades.add("$sin_cambios");
            for (String propiedad : propiedades) {
                JsonNode b = propiedad(valorBase, propiedad);
                JsonNode a = propiedad(valorActual, propiedad);
                JsonNode p = propiedad(valorPropuesto, propiedad);
                boolean incompatible =
                        cambiadasActual.contains(propiedad)
                                && cambiadasPropuesta.contains(propiedad)
                                && !Objects.equals(a, p);
                diferencias.add(
                        new Diferencia(
                                i,
                                accion.operacion(),
                                accion.entidad(),
                                accion.idEntidad(),
                                propiedad,
                                visible(b),
                                visible(a),
                                visible(p),
                                incompatible));
            }
            fusionable &= fusionable(accion, valorBase, valorActual, valorPropuesto);
        }
        return new Comparacion(
                almacenado, entrante, confirmado, List.copyOf(diferencias), fusionable, eliminado);
    }

    public List<Accion> fusionar(JsonNode base, JsonNode actual, List<Accion> acciones) {
        if (!comparar(base, actual, acciones).fusionable())
            throw new ApiException(
                    422,
                    "Las propiedades se solapan o el elemento cambió de forma incompatible; la"
                            + " fusión no es válida.");
        List<Accion> resultado = new ArrayList<>();
        for (Accion accion : acciones) {
            if (!"ACTUALIZAR".equals(accion.operacion()) && !"MOVER".equals(accion.operacion())) {
                resultado.add(accion);
                continue;
            }
            JsonNode valorBase = valor(base, accion);
            ObjectNode combinado = (ObjectNode) valor(actual, accion).deepCopy();
            for (String propiedad : cambios(valorBase, propuesto(accion))) {
                JsonNode valor = propiedad(propuesto(accion), propiedad);
                if (valor.isMissingNode()) combinado.remove(propiedad);
                else combinado.set(propiedad, valor);
            }
            combinado.remove("id");
            resultado.add(
                    new Accion(
                            accion.operacion(), accion.entidad(), accion.idEntidad(), combinado));
        }
        return List.copyOf(resultado);
    }

    private boolean fusionable(Accion a, JsonNode base, JsonNode actual, JsonNode propuesta) {
        return switch (a.operacion()) {
            case "ACTUALIZAR", "MOVER" ->
                    !base.isMissingNode()
                            && !actual.isMissingNode()
                            && Collections.disjoint(
                                    cambios(base, actual), cambios(base, propuesta));
            case "CREAR" -> actual.isMissingNode();
            case "ELIMINAR" -> Objects.equals(base, actual);
            default -> false;
        };
    }

    private ObjectNode registro(Accion a, JsonNode valor) {
        ObjectNode registro = json.createObjectNode();
        registro.put("operacion", a.operacion());
        registro.put("entidad", a.entidad());
        registro.put("idEntidad", a.idEntidad().toString());
        registro.set("valor", visible(valor));
        return registro;
    }

    private JsonNode propuesto(Accion a) {
        if ("ELIMINAR".equals(a.operacion())) return MissingNode.getInstance();
        ObjectNode n =
                a.datos() != null && a.datos().isObject()
                        ? (ObjectNode) a.datos().deepCopy()
                        : json.createObjectNode();
        n.put("id", a.idEntidad().toString());
        return n;
    }

    private JsonNode valor(JsonNode snapshot, Accion a) {
        String grupo =
                switch (a.entidad()) {
                    case "CLASE" -> "clases";
                    case "ATRIBUTO" -> "atributos";
                    case "OPERACION" -> "operaciones";
                    case "RELACION" -> "relaciones";
                    case "PAQUETE" -> "paquetes";
                    default -> throw new ApiException(422, "Tipo de elemento no compatible.");
                };
        for (JsonNode item : snapshot.path(grupo))
            if (a.idEntidad().toString().equals(item.path("id").asText()))
                return canonico(snapshot, a, item);
        return MissingNode.getInstance();
    }

    private ObjectNode canonico(JsonNode snapshot, Accion a, JsonNode item) {
        ObjectNode n = json.createObjectNode().put("id", a.idEntidad().toString());
        switch (a.entidad()) {
            case "CLASE" -> {
                copiar(n, item, "nombre", "nombre");
                copiar(n, item, "descripcion", "descripcion");
                copiar(n, item, "estereotipo", "estereotipo");
                copiar(n, item, "visibilidad", "visibilidad");
                copiar(n, item, "es_abstracto", "esAbstracto");
                copiar(n, item, "es_persistente", "esPersistente");
                copiar(n, item, "id_paquete", "idPaquete");
                if ("MOVER".equals(a.operacion()) || "CREAR".equals(a.operacion())) {
                    JsonNode p =
                            buscar(snapshot.path("posiciones_clases"), "id_clase", a.idEntidad());
                    copiar(n, p, "x", "x");
                    copiar(n, p, "y", "y");
                }
            }
            case "ATRIBUTO" -> {
                copiar(n, item, "id_clase", "idClase");
                copiar(n, item, "nombre", "nombre");
                copiar(n, item, "tipo_uml", "tipoUml");
                copiar(n, item, "visibilidad", "visibilidad");
                copiar(n, item, "multiplicidad_minima", "minimo");
                copiar(n, item, "multiplicidad_maxima", "maximo");
                copiar(n, item, "es_identificador", "esIdentificador");
                copiar(n, item, "es_estatico", "esEstatico");
                copiar(n, item, "es_unico", "esUnico");
                copiar(n, item, "valor_predeterminado", "valorPredeterminado");
                copiar(n, item, "restricciones_json", "restricciones");
            }
            case "OPERACION" -> {
                copiar(n, item, "id_clase", "idClase");
                copiar(n, item, "nombre", "nombre");
                copiar(n, item, "tipo_retorno", "tipoRetorno");
                copiar(n, item, "visibilidad", "visibilidad");
                copiar(n, item, "es_estatico", "esEstatico");
                copiar(n, item, "es_abstracto", "esAbstracto");
                ArrayNode parametros = n.putArray("parametros");
                for (JsonNode p : snapshot.path("parametros"))
                    if (item.path("id").asText().equals(p.path("id_operacion").asText())) {
                        ObjectNode q = json.createObjectNode();
                        copiar(q, p, "id", "id");
                        copiar(q, p, "nombre", "nombre");
                        copiar(q, p, "tipo_uml", "tipoUml");
                        copiar(q, p, "direccion", "direccion");
                        copiar(q, p, "es_obligatorio", "esObligatorio");
                        copiar(q, p, "valor_predeterminado", "valorPredeterminado");
                        parametros.add(q);
                    }
            }
            case "RELACION" -> {
                copiar(n, item, "id_clase_origen", "origen");
                copiar(n, item, "id_clase_destino", "destino");
                copiar(n, item, "nombre", "nombre");
                copiar(n, item, "tipo_relacion", "tipo");
                copiar(n, item, "direccion", "direccion");
                copiar(n, item, "rol_origen", "rolOrigen");
                copiar(n, item, "rol_destino", "rolDestino");
                copiar(n, item, "minimo_origen", "minimoOrigen");
                copiar(n, item, "maximo_origen", "maximoOrigen");
                copiar(n, item, "minimo_destino", "minimoDestino");
                copiar(n, item, "maximo_destino", "maximoDestino");
            }
            case "PAQUETE" -> {
                copiar(n, item, "nombre", "nombre");
                copiar(n, item, "descripcion", "descripcion");
                copiar(n, item, "id_paquete_padre", "padre");
            }
            default -> throw new ApiException(422, "Tipo de elemento no compatible.");
        }
        return n;
    }

    private JsonNode buscar(JsonNode grupo, String campo, UUID id) {
        for (JsonNode item : grupo)
            if (id.toString().equals(item.path(campo).asText())) return item;
        return MissingNode.getInstance();
    }

    private void copiar(ObjectNode destino, JsonNode origen, String desde, String hacia) {
        if (origen.has(desde)) destino.set(hacia, origen.get(desde));
    }

    private Set<String> cambios(JsonNode base, JsonNode valor) {
        if (base.isMissingNode() || valor.isMissingNode()) return Set.of("$existencia");
        Set<String> claves = new HashSet<>();
        base.fieldNames().forEachRemaining(claves::add);
        valor.fieldNames().forEachRemaining(claves::add);
        claves.remove("id");
        claves.removeIf(k -> equivalentes(base.path(k), valor.path(k)));
        return claves;
    }

    private boolean equivalentes(JsonNode izquierdo, JsonNode derecho) {
        boolean izquierdoVacio = izquierdo.isMissingNode() || izquierdo.isNull();
        boolean derechoVacio = derecho.isMissingNode() || derecho.isNull();
        return (izquierdoVacio && derechoVacio) || Objects.equals(izquierdo, derecho);
    }

    private JsonNode propiedad(JsonNode objeto, String propiedad) {
        if ("$sin_cambios".equals(propiedad)) return NullNode.getInstance();
        if ("$existencia".equals(propiedad)) return BooleanNode.valueOf(!objeto.isMissingNode());
        return objeto.path(propiedad);
    }

    private JsonNode visible(JsonNode valor) {
        return valor == null || valor.isMissingNode() ? NullNode.getInstance() : valor;
    }
}
