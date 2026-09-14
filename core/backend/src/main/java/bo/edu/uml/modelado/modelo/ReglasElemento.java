package bo.edu.uml.modelado.modelo;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/** Valida el comando antes de enviar parámetros a SQL. No interpreta SQL ni decisiones de IA. */
public final class ReglasElemento {
    private ReglasElemento() {}

    public static void objeto(JsonNode d, Set<String> campos) {
        if (d == null || !d.isObject()) throw invalido("Faltan los datos del elemento.");
        d.fieldNames()
                .forEachRemaining(
                        k -> {
                            if (!campos.contains(k)) throw invalido("Campo no permitido: " + k);
                        });
    }

    public static String texto(JsonNode d, String k, int max) {
        if (!d.hasNonNull(k) || !d.get(k).isTextual()) throw invalido("Falta " + k + ".");
        String t = d.get(k).textValue().trim();
        if (t.isEmpty() || t.length() > max) throw invalido("Revisa " + k + ".");
        return t;
    }

    public static String opcional(JsonNode d, String k, int max) {
        if (!d.hasNonNull(k)) return null;
        if (!d.get(k).isTextual() || d.get(k).textValue().length() > max)
            throw invalido("Revisa " + k + ".");
        return d.get(k).textValue();
    }

    public static String enumerado(JsonNode d, String k, String predeterminado, String... valores) {
        String v = d.hasNonNull(k) ? texto(d, k, 64) : predeterminado;
        if (!Arrays.asList(valores).contains(v)) throw invalido("Valor inválido para " + k + ".");
        return v;
    }

    public static boolean bool(JsonNode d, String k, boolean predeterminado) {
        if (!d.has(k)) return predeterminado;
        if (!d.get(k).isBoolean()) throw invalido("Revisa " + k + ".");
        return d.get(k).booleanValue();
    }

    public static int entero(JsonNode d, String k, int predeterminado) {
        if (!d.has(k)) return predeterminado;
        if (!d.get(k).isIntegralNumber() || !d.get(k).canConvertToInt() || d.get(k).intValue() < 0)
            throw invalido("Revisa " + k + ".");
        return d.get(k).intValue();
    }

    public static Integer maximo(JsonNode d, String k, int predeterminado) {
        if (d.has(k) && d.get(k).isNull()) return null;
        return entero(d, k, predeterminado);
    }

    public static UUID uuid(JsonNode d, String k) {
        try {
            return UUID.fromString(texto(d, k, 36));
        } catch (IllegalArgumentException e) {
            throw invalido("Identificador inválido.");
        }
    }

    public static double coordenada(JsonNode d, String k) {
        if (!d.has(k) || !d.get(k).isNumber()) throw invalido("Falta la posición.");
        double v = d.get(k).doubleValue();
        if (!Double.isFinite(v) || Math.abs(v) > 1000000)
            throw invalido("Posición fuera de límites.");
        return v;
    }

    public static ApiException invalido(String m) {
        return new ApiException(422, m);
    }
}
