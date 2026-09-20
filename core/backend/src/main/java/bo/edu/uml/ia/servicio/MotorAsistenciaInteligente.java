package bo.edu.uml.ia.servicio;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MotorAsistenciaInteligente {
    private static final String ID = "([\\p{L}_][\\p{L}\\p{N}_]*)";
    private static final String TIPO = "([\\p{L}_][\\p{L}\\p{N}_<>.?]*)";
    private static final Pattern CREAR_CLASE =
            Pattern.compile("(?iu)\\b(?:crea|crear|agrega|agregar|añade)\\s+(?:una\\s+)?clase\\b");
    private static final Pattern CLASE =
            Pattern.compile("(?iu)\\b(?:una\\s+|la\\s+)?clase\\s+" + ID);
    private static final Pattern BLOQUE_ATRIBUTOS =
            Pattern.compile("(?iu)\\batributos?\\s+(.+?)(?=\\b(?:con\\s+)?relaci[oó]n\\b|$)");
    private static final Pattern ATRIBUTO = Pattern.compile("(?iu)" + ID + "\\s*:\\s*" + TIPO);
    private static final Pattern CARDINALIDAD =
            Pattern.compile("(?iu)(\\d+|\\*)\\s*(?:a|:|-)\\s*(\\d+|\\*|muchos?)");
    private static final Pattern RENOMBRAR_CLASE =
            Pattern.compile(
                    "(?iu)\\b(?:renombra|cambia\\s+el\\s+nombre\\s+de)\\s+(?:la\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:a|por)\\s+"
                            + ID);
    private static final Pattern ELIMINAR_CLASE =
            Pattern.compile("(?iu)\\b(?:elimina|eliminar|borra)\\s+(?:la\\s+)?clase\\s+" + ID);
    private static final Pattern CREAR_ATRIBUTO =
            Pattern.compile(
                    "(?iu)\\b(?:crea|agrega|añade)\\s+(?:el\\s+|un\\s+)?atributo\\s+"
                            + ID
                            + "\\s*:\\s*"
                            + TIPO
                            + "\\s+(?:a|en)\\s+(?:la\\s+)?clase\\s+"
                            + ID);
    private static final Pattern RENOMBRAR_ATRIBUTO =
            Pattern.compile(
                    "(?iu)\\b(?:renombra|cambia)\\s+(?:el\\s+)?atributo\\s+"
                            + ID
                            + "\\s+(?:de|en)\\s+(?:la\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:a|por)\\s+"
                            + ID
                            + "(?:\\s*:\\s*"
                            + TIPO
                            + ")?");
    private static final Pattern ELIMINAR_ATRIBUTO =
            Pattern.compile(
                    "(?iu)\\b(?:elimina|borra)\\s+(?:el\\s+)?atributo\\s+"
                            + ID
                            + "\\s+(?:de|en)\\s+(?:la\\s+)?clase\\s+"
                            + ID);
    private static final Pattern CREAR_OPERACION =
            Pattern.compile(
                    "(?iu)\\b(?:crea|agrega|añade)\\s+(?:la\\s+|una\\s+)?operaci[oó]n\\s+"
                            + ID
                            + "\\s*\\(\\s*\\)\\s*(?::\\s*"
                            + TIPO
                            + ")?\\s+(?:a|en)\\s+(?:la\\s+)?clase\\s+"
                            + ID);
    private static final Pattern ELIMINAR_OPERACION =
            Pattern.compile(
                    "(?iu)\\b(?:elimina|borra)\\s+(?:la\\s+)?operaci[oó]n\\s+"
                            + ID
                            + "(?:\\s*\\(\\s*\\))?\\s+(?:de|en)\\s+(?:la\\s+)?clase\\s+"
                            + ID);
    private static final Pattern CREAR_RELACION =
            Pattern.compile(
                    "(?iu)\\b(?:crea\\s+(?:una\\s+)?relaci[oó]n\\s+entre|relaciona)\\s+"
                            + ID
                            + "\\s+(?:con|y)\\s+"
                            + ID);
    private static final Pattern ELIMINAR_RELACION =
            Pattern.compile(
                    "(?iu)\\b(?:elimina|borra)\\s+(?:la\\s+)?relaci[oó]n\\s+entre\\s+"
                            + ID
                            + "\\s+(?:y|con)\\s+"
                            + ID);

    private final ObjectMapper json;
    private final ProveedorIa proveedor;

    public MotorAsistenciaInteligente(ObjectMapper json) {
        this(json, new ProveedorIa() {});
    }

    @Autowired
    public MotorAsistenciaInteligente(ObjectMapper json, ProveedorIa proveedor) {
        this.json = json;
        this.proveedor = proveedor;
    }

    public record ElementoContexto(
            UUID id, String tipo, String nombre, UUID idContenedor, JsonNode valor) {}

    public record Contexto(
            int cantidadClases, Set<String> nombresClases, List<ElementoContexto> elementos) {
        public Contexto(int cantidadClases, Set<String> nombresClases) {
            this(cantidadClases, nombresClases, List.of());
        }
    }

    public record OperacionGenerada(
            UUID id,
            int numeroSecuencia,
            String tipoOperacion,
            String tipoEntidad,
            UUID idEntidad,
            JsonNode valorActual,
            JsonNode valorPropuesto,
            BigDecimal confianza,
            boolean seleccionada,
            String estado,
            JsonNode mensajes,
            String justificacion,
            List<UUID> dependencias) {}

    public record Interpretacion(
            String resumen, BigDecimal confianzaGeneral, List<OperacionGenerada> operaciones) {}

    public Interpretacion interpretar(String prompt, Contexto contexto) {
        if (prompt == null || prompt.isBlank())
            throw new ApiException(400, "La instrucción de texto es obligatoria.");
        Optional<String> normalizada = proveedor.normalizar(prompt);
        if (normalizada.isPresent() && !normalizada.get().equals(prompt)) {
            try {
                return interpretarLocal(normalizada.get(), contexto);
            } catch (ApiException ignorada) {
                // Una respuesta remota inutilizable no anula el respaldo determinista local.
            }
        }
        return interpretarLocal(prompt, contexto);
    }

    private Interpretacion interpretarLocal(String prompt, Contexto contexto) {
        if (CREAR_CLASE.matcher(prompt).find()) return crearClases(prompt, contexto);
        return cambiarExistentes(prompt, contexto);
    }

    private Interpretacion crearClases(String prompt, Contexto contexto) {
        Matcher clases = CLASE.matcher(prompt);
        List<CapturaClase> capturas = new ArrayList<>();
        while (clases.find())
            capturas.add(new CapturaClase(clases.group(1), clases.start(), clases.end()));
        if (capturas.isEmpty()) throw noReconocida();

        List<OperacionGenerada> operaciones = new ArrayList<>();
        List<UUID> operacionesClase = new ArrayList<>();
        List<UUID> entidadesClase = new ArrayList<>();
        Set<String> nombres = new HashSet<>();
        contexto.nombresClases().forEach(n -> nombres.add(n.toLowerCase(Locale.ROOT)));
        int secuencia = 1;
        int totalAtributos = 0;
        for (int i = 0; i < capturas.size(); i++) {
            CapturaClase captura = capturas.get(i);
            int fin = i + 1 < capturas.size() ? capturas.get(i + 1).inicio() : prompt.length();
            String segmento = prompt.substring(captura.fin(), fin);
            UUID idEntidad = UUID.randomUUID();
            UUID idOperacion = UUID.randomUUID();
            boolean duplicada = !nombres.add(captura.nombre().toLowerCase(Locale.ROOT));
            ObjectNode datos = json.createObjectNode();
            datos.put("nombre", captura.nombre());
            datos.put("x", coordenadaX(contexto.cantidadClases() + i));
            datos.put("y", coordenadaY(contexto.cantidadClases() + i));
            String justificacion =
                    "Crear la clase mencionada explícitamente como " + captura.nombre();
            operaciones.add(
                    operacion(
                            idOperacion,
                            secuencia++,
                            "CREAR",
                            "CLASE",
                            idEntidad,
                            null,
                            datos,
                            !duplicada,
                            duplicada
                                    ? "Ya existe o se repite una clase con el mismo nombre."
                                    : null,
                            justificacion,
                            List.of()));
            operacionesClase.add(idOperacion);
            entidadesClase.add(idEntidad);

            Matcher bloque = BLOQUE_ATRIBUTOS.matcher(segmento);
            if (!bloque.find()) continue;
            Matcher atributo = ATRIBUTO.matcher(bloque.group(1));
            while (atributo.find()) {
                ObjectNode datosAtributo = json.createObjectNode();
                datosAtributo.put("idClase", idEntidad.toString());
                datosAtributo.put("nombre", atributo.group(1));
                datosAtributo.put("tipoUml", atributo.group(2));
                String justificacionAtributo =
                        "Crear el atributo "
                                + atributo.group(1)
                                + ": "
                                + atributo.group(2)
                                + " en "
                                + captura.nombre();
                operaciones.add(
                        operacion(
                                UUID.randomUUID(),
                                secuencia++,
                                "CREAR",
                                "ATRIBUTO",
                                UUID.randomUUID(),
                                null,
                                datosAtributo,
                                !duplicada,
                                duplicada ? "El atributo depende de una clase inválida." : null,
                                justificacionAtributo,
                                List.of(idOperacion)));
                totalAtributos++;
            }
        }

        int totalRelaciones = 0;
        if (entidadesClase.size() >= 2 && contieneRelacion(prompt)) {
            boolean valida =
                    operaciones.stream()
                            .filter(o -> o.tipoEntidad().equals("CLASE"))
                            .limit(2)
                            .allMatch(OperacionGenerada::seleccionada);
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia,
                            "CREAR",
                            "RELACION",
                            UUID.randomUUID(),
                            null,
                            relacion(entidadesClase.get(0), entidadesClase.get(1), prompt),
                            valida,
                            valida ? null : "La relación depende de clases inválidas.",
                            "Crear la relación y cardinalidad indicadas entre las dos clases",
                            List.of(operacionesClase.get(0), operacionesClase.get(1))));
            totalRelaciones = 1;
        }
        String resumen =
                "Se interpretaron "
                        + capturas.size()
                        + " clase(s), "
                        + totalAtributos
                        + " atributo(s) y "
                        + totalRelaciones
                        + " relación(es).";
        BigDecimal confianza =
                operaciones.stream().anyMatch(o -> !o.seleccionada())
                        ? new BigDecimal("0.6500")
                        : totalRelaciones > 0 ? new BigDecimal("0.9300") : new BigDecimal("0.9500");
        return new Interpretacion(resumen, confianza, List.copyOf(operaciones));
    }

    private Interpretacion cambiarExistentes(String prompt, Contexto contexto) {
        List<OperacionGenerada> operaciones = new ArrayList<>();
        int secuencia = 1;
        Matcher m = RENOMBRAR_CLASE.matcher(prompt);
        while (m.find()) {
            String anterior = m.group(1), nuevo = m.group(2);
            Optional<ElementoContexto> existente = buscar(contexto, "CLASE", anterior, null);
            boolean ocupado = buscar(contexto, "CLASE", nuevo, null).isPresent();
            String error =
                    existente.isEmpty()
                            ? "No existe la clase indicada en el modelo actual."
                            : ocupado ? "Ya existe una clase con el nombre propuesto." : null;
            JsonNode actual = existente.map(ElementoContexto::valor).orElse(null);
            ObjectNode propuesto = copia(actual);
            propuesto.put("nombre", nuevo);
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia++,
                            "ACTUALIZAR",
                            "CLASE",
                            existente.map(ElementoContexto::id).orElseGet(UUID::randomUUID),
                            actual,
                            propuesto,
                            error == null,
                            error,
                            "Renombrar la clase " + anterior + " como " + nuevo,
                            List.of()));
        }
        m = ELIMINAR_CLASE.matcher(prompt);
        while (m.find()) {
            String nombre = m.group(1);
            Optional<ElementoContexto> existente = buscar(contexto, "CLASE", nombre, null);
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia++,
                            "ELIMINAR",
                            "CLASE",
                            existente.map(ElementoContexto::id).orElseGet(UUID::randomUUID),
                            existente.map(ElementoContexto::valor).orElse(null),
                            null,
                            existente.isPresent(),
                            existente.isEmpty()
                                    ? "No existe la clase indicada en el modelo actual."
                                    : null,
                            "Eliminar la clase " + nombre,
                            List.of()));
        }
        m = CREAR_ATRIBUTO.matcher(prompt);
        while (m.find()) {
            String nombre = m.group(1), tipo = m.group(2), clase = m.group(3);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            ObjectNode propuesto = json.createObjectNode();
            contenedor.ifPresent(c -> propuesto.put("idClase", c.id().toString()));
            propuesto.put("nombre", nombre).put("tipoUml", tipo);
            boolean duplicado =
                    contenedor.isPresent()
                            && buscar(contexto, "ATRIBUTO", nombre, contenedor.get().id())
                                    .isPresent();
            String error =
                    contenedor.isEmpty()
                            ? "No existe la clase contenedora indicada."
                            : duplicado ? "La clase ya contiene un atributo con ese nombre." : null;
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia++,
                            "CREAR",
                            "ATRIBUTO",
                            UUID.randomUUID(),
                            null,
                            propuesto,
                            error == null,
                            error,
                            "Crear el atributo " + nombre + ": " + tipo + " en " + clase,
                            List.of()));
        }
        m = RENOMBRAR_ATRIBUTO.matcher(prompt);
        while (m.find()) {
            String anterior = m.group(1), clase = m.group(2), nuevo = m.group(3), tipo = m.group(4);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            Optional<ElementoContexto> existente =
                    contenedor.flatMap(c -> buscar(contexto, "ATRIBUTO", anterior, c.id()));
            JsonNode actual = existente.map(ElementoContexto::valor).orElse(null);
            ObjectNode propuesto = copia(actual);
            propuesto.put("nombre", nuevo);
            if (tipo != null) propuesto.put("tipoUml", tipo);
            String error =
                    contenedor.isEmpty()
                            ? "No existe la clase contenedora indicada."
                            : existente.isEmpty()
                                    ? "No existe el atributo indicado en esa clase."
                                    : null;
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia++,
                            "ACTUALIZAR",
                            "ATRIBUTO",
                            existente.map(ElementoContexto::id).orElseGet(UUID::randomUUID),
                            actual,
                            propuesto,
                            error == null,
                            error,
                            "Actualizar el atributo " + anterior + " de " + clase,
                            List.of()));
        }
        m = ELIMINAR_ATRIBUTO.matcher(prompt);
        while (m.find()) {
            String nombre = m.group(1), clase = m.group(2);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            Optional<ElementoContexto> existente =
                    contenedor.flatMap(c -> buscar(contexto, "ATRIBUTO", nombre, c.id()));
            operaciones.add(eliminar(secuencia++, "ATRIBUTO", nombre, clase, existente));
        }
        m = CREAR_OPERACION.matcher(prompt);
        while (m.find()) {
            String nombre = m.group(1), retorno = m.group(2), clase = m.group(3);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            ObjectNode propuesto = json.createObjectNode();
            contenedor.ifPresent(c -> propuesto.put("idClase", c.id().toString()));
            propuesto.put("nombre", nombre);
            if (retorno == null) propuesto.putNull("tipoRetorno");
            else propuesto.put("tipoRetorno", retorno);
            propuesto.putArray("parametros");
            boolean duplicada =
                    contenedor.isPresent()
                            && buscar(contexto, "OPERACION", nombre, contenedor.get().id())
                                    .isPresent();
            String error =
                    contenedor.isEmpty()
                            ? "No existe la clase contenedora indicada."
                            : duplicada
                                    ? "La clase ya contiene una operación con ese nombre."
                                    : null;
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia++,
                            "CREAR",
                            "OPERACION",
                            UUID.randomUUID(),
                            null,
                            propuesto,
                            error == null,
                            error,
                            "Crear la operación " + nombre + " en " + clase,
                            List.of()));
        }
        m = ELIMINAR_OPERACION.matcher(prompt);
        while (m.find()) {
            String nombre = m.group(1), clase = m.group(2);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            Optional<ElementoContexto> existente =
                    contenedor.flatMap(c -> buscar(contexto, "OPERACION", nombre, c.id()));
            operaciones.add(eliminar(secuencia++, "OPERACION", nombre, clase, existente));
        }
        m = CREAR_RELACION.matcher(prompt);
        while (m.find()) {
            String origen = m.group(1), destino = m.group(2);
            Optional<ElementoContexto> claseOrigen = buscar(contexto, "CLASE", origen, null);
            Optional<ElementoContexto> claseDestino = buscar(contexto, "CLASE", destino, null);
            boolean valida = claseOrigen.isPresent() && claseDestino.isPresent();
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia++,
                            "CREAR",
                            "RELACION",
                            UUID.randomUUID(),
                            null,
                            relacion(
                                    claseOrigen
                                            .map(ElementoContexto::id)
                                            .orElseGet(UUID::randomUUID),
                                    claseDestino
                                            .map(ElementoContexto::id)
                                            .orElseGet(UUID::randomUUID),
                                    prompt.substring(m.start())),
                            valida,
                            valida ? null : "Uno o ambos extremos no existen en el modelo actual.",
                            "Crear la relación entre " + origen + " y " + destino,
                            List.of()));
        }
        m = ELIMINAR_RELACION.matcher(prompt);
        while (m.find()) {
            String origen = m.group(1), destino = m.group(2);
            Optional<ElementoContexto> claseOrigen = buscar(contexto, "CLASE", origen, null);
            Optional<ElementoContexto> claseDestino = buscar(contexto, "CLASE", destino, null);
            Optional<ElementoContexto> existente =
                    buscarRelacion(contexto, claseOrigen.orElse(null), claseDestino.orElse(null));
            operaciones.add(eliminar(secuencia++, "RELACION", origen, destino, existente));
        }
        if (operaciones.isEmpty()) throw noReconocida();
        long invalidas = operaciones.stream().filter(o -> !o.seleccionada()).count();
        String resumen =
                "Se interpretaron "
                        + operaciones.size()
                        + " cambio(s) sobre el modelo"
                        + (invalidas == 0 ? "." : "; " + invalidas + " requiere(n) corrección.");
        return resultado(resumen, operaciones);
    }

    private OperacionGenerada eliminar(
            int secuencia,
            String entidad,
            String nombre,
            String contenedor,
            Optional<ElementoContexto> existente) {
        return operacion(
                UUID.randomUUID(),
                secuencia,
                "ELIMINAR",
                entidad,
                existente.map(ElementoContexto::id).orElseGet(UUID::randomUUID),
                existente.map(ElementoContexto::valor).orElse(null),
                null,
                existente.isPresent(),
                existente.isEmpty() ? "No existe el elemento indicado en el modelo actual." : null,
                "Eliminar " + nombre + " de " + contenedor,
                List.of());
    }

    private Optional<ElementoContexto> buscar(
            Contexto contexto, String tipo, String nombre, UUID contenedor) {
        return contexto.elementos().stream()
                .filter(e -> tipo.equals(e.tipo()))
                .filter(e -> e.nombre() != null && e.nombre().equalsIgnoreCase(nombre))
                .filter(e -> contenedor == null || contenedor.equals(e.idContenedor()))
                .findFirst();
    }

    private Optional<ElementoContexto> buscarRelacion(
            Contexto contexto, ElementoContexto origen, ElementoContexto destino) {
        if (origen == null || destino == null) return Optional.empty();
        return contexto.elementos().stream()
                .filter(e -> "RELACION".equals(e.tipo()))
                .filter(
                        e -> {
                            UUID o = uuid(e.valor(), "origen");
                            UUID d = uuid(e.valor(), "destino");
                            return (origen.id().equals(o) && destino.id().equals(d))
                                    || (origen.id().equals(d) && destino.id().equals(o));
                        })
                .findFirst();
    }

    private UUID uuid(JsonNode nodo, String campo) {
        try {
            return UUID.fromString(nodo.path(campo).asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ObjectNode copia(JsonNode nodo) {
        return nodo != null && nodo.isObject()
                ? ((ObjectNode) nodo).deepCopy()
                : json.createObjectNode();
    }

    private OperacionGenerada operacion(
            UUID id,
            int secuencia,
            String tipoOperacion,
            String tipoEntidad,
            UUID idEntidad,
            JsonNode actual,
            JsonNode propuesto,
            boolean valida,
            String error,
            String justificacion,
            List<UUID> dependencias) {
        return new OperacionGenerada(
                id,
                secuencia,
                tipoOperacion,
                tipoEntidad,
                idEntidad,
                actual,
                propuesto,
                valida ? new BigDecimal("0.9400") : new BigDecimal("0.5000"),
                valida,
                valida ? "VALIDO" : "INVALIDO",
                mensajes(justificacion, error),
                justificacion,
                dependencias);
    }

    private Interpretacion resultado(String resumen, List<OperacionGenerada> operaciones) {
        BigDecimal confianza =
                operaciones.stream().anyMatch(o -> !o.seleccionada())
                        ? new BigDecimal("0.6500")
                        : new BigDecimal("0.9400");
        return new Interpretacion(resumen, confianza, List.copyOf(operaciones));
    }

    private ObjectNode relacion(UUID origen, UUID destino, String prompt) {
        ObjectNode datos = json.createObjectNode();
        datos.put("origen", origen.toString());
        datos.put("destino", destino.toString());
        datos.put("tipo", "ASOCIACION");
        datos.put("direccion", "SIN_DIRECCION");
        Cardinalidad cardinalidad = cardinalidad(prompt);
        datos.put("minimoOrigen", cardinalidad.minimoOrigen());
        if (cardinalidad.maximoOrigen() == null) datos.putNull("maximoOrigen");
        else datos.put("maximoOrigen", cardinalidad.maximoOrigen());
        datos.put("minimoDestino", cardinalidad.minimoDestino());
        if (cardinalidad.maximoDestino() == null) datos.putNull("maximoDestino");
        else datos.put("maximoDestino", cardinalidad.maximoDestino());
        return datos;
    }

    private boolean contieneRelacion(String prompt) {
        String normalizado = prompt.toLowerCase(Locale.ROOT);
        return normalizado.contains("relación")
                || normalizado.contains("relacion")
                || normalizado.contains("asociación")
                || normalizado.contains("asociacion");
    }

    private Cardinalidad cardinalidad(String prompt) {
        Matcher matcher = CARDINALIDAD.matcher(prompt);
        if (!matcher.find()) return new Cardinalidad(1, 1, 1, 1);
        Extremo origen = extremo(matcher.group(1));
        Extremo destino = extremo(matcher.group(2));
        return new Cardinalidad(
                origen.minimo(), origen.maximo(), destino.minimo(), destino.maximo());
    }

    private Extremo extremo(String valor) {
        if (valor.equals("*") || valor.toLowerCase(Locale.ROOT).startsWith("much"))
            return new Extremo(0, null);
        int numero = Integer.parseInt(valor);
        return new Extremo(numero, numero);
    }

    private ArrayNode mensajes(String justificacion, String advertencia) {
        ArrayNode mensajes = json.createArrayNode();
        mensajes.addObject().put("tipo", "JUSTIFICACION").put("mensaje", justificacion);
        if (advertencia != null)
            mensajes.addObject().put("tipo", "ERROR").put("mensaje", advertencia);
        return mensajes;
    }

    private ApiException noReconocida() {
        return new ApiException(
                422,
                "No se identificó una instrucción compatible. Indica crear, renombrar o eliminar"
                        + " una clase, atributo, operación o relación.");
    }

    private int coordenadaX(int indice) {
        return 60 + (indice % 4) * 300;
    }

    private int coordenadaY(int indice) {
        return 60 + (indice / 4) * 230;
    }

    private record CapturaClase(String nombre, int inicio, int fin) {}

    private record Extremo(int minimo, Integer maximo) {}

    private record Cardinalidad(
            int minimoOrigen, Integer maximoOrigen, int minimoDestino, Integer maximoDestino) {}
}
