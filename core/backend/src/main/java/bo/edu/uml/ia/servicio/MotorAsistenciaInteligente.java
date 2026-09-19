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
    private static final Pattern CLASE =
            Pattern.compile("(?iu)\\b(?:una\\s+|la\\s+)?clase\\s+([\\p{L}_][\\p{L}\\p{N}_]*)");
    private static final Pattern BLOQUE_ATRIBUTOS =
            Pattern.compile("(?iu)\\batributos?\\s+(.+?)(?=\\b(?:con\\s+)?relaci[oó]n\\b|$)");
    private static final Pattern ATRIBUTO =
            Pattern.compile(
                    "(?iu)([\\p{L}_][\\p{L}\\p{N}_]*)\\s*:\\s*([\\p{L}_][\\p{L}\\p{N}_<>.?]*)");
    private static final Pattern CARDINALIDAD =
            Pattern.compile("(?iu)(\\d+|\\*)\\s*(?:a|:|-)\\s*(\\d+|\\*|muchos?)");

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

    public record Contexto(int cantidadClases, Set<String> nombresClases) {}

    public record OperacionGenerada(
            UUID id,
            int numeroSecuencia,
            String tipoOperacion,
            String tipoEntidad,
            UUID idEntidad,
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

        Matcher clases = CLASE.matcher(prompt);
        List<CapturaClase> capturas = new ArrayList<>();
        while (clases.find())
            capturas.add(new CapturaClase(clases.group(1), clases.start(), clases.end()));
        if (capturas.isEmpty())
            throw new ApiException(
                    422,
                    "No se identificó ninguna clase. Reformula la instrucción indicando 'clase"
                            + " Nombre'.");

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
                    new OperacionGenerada(
                            idOperacion,
                            secuencia++,
                            "CREAR",
                            "CLASE",
                            idEntidad,
                            datos,
                            duplicada ? new BigDecimal("0.5000") : new BigDecimal("0.9800"),
                            !duplicada,
                            duplicada ? "INVALIDO" : "VALIDO",
                            mensajes(
                                    justificacion,
                                    duplicada
                                            ? "Ya existe o se repite una clase con el mismo nombre."
                                            : null),
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
                        new OperacionGenerada(
                                UUID.randomUUID(),
                                secuencia++,
                                "CREAR",
                                "ATRIBUTO",
                                UUID.randomUUID(),
                                datosAtributo,
                                new BigDecimal("0.9600"),
                                !duplicada,
                                duplicada ? "INVALIDO" : "VALIDO",
                                mensajes(
                                        justificacionAtributo,
                                        duplicada
                                                ? "El atributo depende de una clase inválida."
                                                : null),
                                justificacionAtributo,
                                List.of(idOperacion)));
                totalAtributos++;
            }
        }

        int totalRelaciones = 0;
        if (entidadesClase.size() >= 2 && contieneRelacion(prompt)) {
            ObjectNode datos = json.createObjectNode();
            datos.put("origen", entidadesClase.get(0).toString());
            datos.put("destino", entidadesClase.get(1).toString());
            datos.put("tipo", "ASOCIACION");
            datos.put("direccion", "SIN_DIRECCION");
            Cardinalidad cardinalidad = cardinalidad(prompt);
            datos.put("minimoOrigen", cardinalidad.minimoOrigen());
            if (cardinalidad.maximoOrigen() == null) datos.putNull("maximoOrigen");
            else datos.put("maximoOrigen", cardinalidad.maximoOrigen());
            datos.put("minimoDestino", cardinalidad.minimoDestino());
            if (cardinalidad.maximoDestino() == null) datos.putNull("maximoDestino");
            else datos.put("maximoDestino", cardinalidad.maximoDestino());
            String justificacion =
                    "Crear la relación y cardinalidad indicadas entre las dos clases";
            boolean valida =
                    operaciones.stream()
                            .filter(o -> o.tipoEntidad().equals("CLASE"))
                            .limit(2)
                            .allMatch(OperacionGenerada::seleccionada);
            operaciones.add(
                    new OperacionGenerada(
                            UUID.randomUUID(),
                            secuencia,
                            "CREAR",
                            "RELACION",
                            UUID.randomUUID(),
                            datos,
                            new BigDecimal("0.9000"),
                            valida,
                            valida ? "VALIDO" : "INVALIDO",
                            mensajes(
                                    justificacion,
                                    valida ? null : "La relación depende de clases inválidas."),
                            justificacion,
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
