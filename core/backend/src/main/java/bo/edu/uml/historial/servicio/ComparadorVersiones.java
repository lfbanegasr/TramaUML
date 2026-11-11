package bo.edu.uml.historial.servicio;

import bo.edu.uml.historial.dto.HistorialDto.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Comparación pura de dos snapshots formato_version=2. Sin acceso a base de datos ni dependencias
 * de Spring. Clasifica cada elemento UML en AGREGADO, ELIMINADO o MODIFICADO y detalla las
 * propiedades que difieren.
 */
@Component
public class ComparadorVersiones {

    /** Campos técnicos que no representan diferencias semánticas entre versiones. */
    private static final Set<String> EXCLUIR =
            Set.of(
                    "eliminado_en",
                    "creado_en",
                    "creado_por",
                    "actualizado_por",
                    "actualizado_en",
                    "version_fila");

    /**
     * Compara dos snapshots y devuelve la lista de diferencias por elemento. El orden de los
     * arreglos no se considera diferencia, pero el atributo 'orden' de los elementos sí es
     * semántico.
     *
     * @param desde Snapshot de la versión origen (más antigua o primera seleccionada).
     * @param hasta Snapshot de la versión destino (más reciente o segunda seleccionada).
     */
    public List<ElementoDif> comparar(JsonNode desde, JsonNode hasta) {
        List<ElementoDif> resultado = new ArrayList<>();
        for (Grupo grupo : Grupo.values()) {
            Map<String, JsonNode> mapaDesde = indexar(desde.path(grupo.campo), grupo.claveId);
            Map<String, JsonNode> mapaHasta = indexar(hasta.path(grupo.campo), grupo.claveId);

            // Eliminados: estaban en "desde" y no están en "hasta".
            for (var entrada : mapaDesde.entrySet()) {
                if (!mapaHasta.containsKey(entrada.getKey())) {
                    resultado.add(
                            new ElementoDif(
                                    uuid(entrada.getKey()),
                                    grupo.tipo,
                                    nombre(entrada.getValue(), grupo),
                                    "ELIMINADO",
                                    List.of()));
                }
            }
            // Agregados: están en "hasta" y no en "desde".
            for (var entrada : mapaHasta.entrySet()) {
                if (!mapaDesde.containsKey(entrada.getKey())) {
                    resultado.add(
                            new ElementoDif(
                                    uuid(entrada.getKey()),
                                    grupo.tipo,
                                    nombre(entrada.getValue(), grupo),
                                    "AGREGADO",
                                    List.of()));
                }
            }
            // Modificados: están en ambos, con propiedades distintas.
            for (var entrada : mapaDesde.entrySet()) {
                JsonNode nodoHasta = mapaHasta.get(entrada.getKey());
                if (nodoHasta == null) continue;
                List<PropiedadDif> diffs =
                        propiedades(entrada.getValue(), nodoHasta, grupo.claveId);
                if (!diffs.isEmpty()) {
                    resultado.add(
                            new ElementoDif(
                                    uuid(entrada.getKey()),
                                    grupo.tipo,
                                    nombre(nodoHasta, grupo),
                                    "MODIFICADO",
                                    diffs));
                }
            }
        }
        return resultado;
    }

    private Map<String, JsonNode> indexar(JsonNode arreglo, String claveId) {
        Map<String, JsonNode> mapa = new LinkedHashMap<>();
        if (arreglo.isArray()) {
            for (JsonNode nodo : arreglo) {
                String id = nodo.path(claveId).asText(null);
                if (id != null) mapa.put(id, nodo);
            }
        }
        return mapa;
    }

    private List<PropiedadDif> propiedades(JsonNode antes, JsonNode despues, String claveId) {
        List<PropiedadDif> diffs = new ArrayList<>();
        Set<String> claves = new LinkedHashSet<>();
        antes.fieldNames().forEachRemaining(claves::add);
        despues.fieldNames().forEachRemaining(claves::add);
        claves.remove(claveId);
        claves.remove("id");
        claves.removeAll(EXCLUIR);
        for (String clave : claves) {
            Object valorAntes = valor(antes.path(clave));
            Object valorDespues = valor(despues.path(clave));
            // Defaults UML: comparar sin modificar los documentos históricos ni sus hashes.
            if (clave.equals("es_ordenado")) {
                if (!antes.has(clave)) valorAntes = false;
                if (!despues.has(clave)) valorDespues = false;
            }
            if (clave.equals("coleccion_unica")) {
                if (!antes.has(clave)) valorAntes = true;
                if (!despues.has(clave)) valorDespues = true;
            }
            if (!Objects.equals(normalizar(valorAntes), normalizar(valorDespues)))
                diffs.add(new PropiedadDif(clave, valorAntes, valorDespues));
        }
        return diffs;
    }

    /**
     * Normaliza valores para comparación: null, "null" y nodo ausente son equivalentes. Booleanos y
     * números se comparan por valor canónico.
     */
    private Object normalizar(Object valor) {
        if (valor == null || "null".equals(valor) || "".equals(valor)) return null;
        return valor.toString().trim();
    }

    private Object valor(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode() || nodo.isNull()) return null;
        if (nodo.isBoolean()) return nodo.booleanValue();
        if (nodo.isIntegralNumber()) return nodo.longValue();
        if (nodo.isFloatingPointNumber()) return nodo.doubleValue();
        if (nodo.isTextual()) return nodo.textValue();
        return nodo.toString();
    }

    private String nombre(JsonNode nodo, Grupo grupo) {
        if (nodo.hasNonNull("nombre")) return nodo.path("nombre").asText();
        if (grupo == Grupo.POSICIONES_CLASES && nodo.hasNonNull("id_clase"))
            return "Posición clase " + nodo.path("id_clase").asText();
        if (grupo == Grupo.POSICIONES_RELACIONES && nodo.hasNonNull("id_relacion"))
            return "Posición relación " + nodo.path("id_relacion").asText();
        return null;
    }

    private UUID uuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Grupos de elementos en el snapshot formato_version=2, su tipo UML visible y clave ID. */
    private enum Grupo {
        PAQUETES("paquetes", "PAQUETE", "id"),
        CLASES("clases", "CLASE", "id"),
        ATRIBUTOS("atributos", "ATRIBUTO", "id"),
        OPERACIONES("operaciones", "OPERACION", "id"),
        PARAMETROS("parametros", "PARAMETRO", "id"),
        RELACIONES("relaciones", "RELACION", "id"),
        POSICIONES_CLASES("posiciones_clases", "VISUAL", "id_clase"),
        POSICIONES_RELACIONES("posiciones_relaciones", "VISUAL", "id_relacion");

        final String campo;
        final String tipo;
        final String claveId;

        Grupo(String campo, String tipo, String claveId) {
            this.campo = campo;
            this.tipo = tipo;
            this.claveId = claveId;
        }
    }
}
