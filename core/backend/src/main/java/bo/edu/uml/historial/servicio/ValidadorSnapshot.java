package bo.edu.uml.historial.servicio;

import bo.edu.uml.compartido.ApiException;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Validador estricto para instantáneas de versión de modelo UML (formato_version: 2). Verifica
 * esquema, modelo correspondiente, presencia y tipos de arreglos, UUIDs, enums, integridad
 * referencial interna, límites y contraste de SHA-256 canónico.
 */
@Component
public class ValidadorSnapshot {

    private static final Set<String> VISIBILIDADES =
            Set.of("PUBLICO", "PROTEGIDO", "PRIVADO", "PAQUETE");

    private static final Set<String> TIPOS_RELACION =
            Set.of(
                    "ASOCIACION",
                    "ASOCIACION_COMPUESTA",
                    "AGREGACION",
                    "COMPOSICION",
                    "HERENCIA",
                    "GENERALIZACION",
                    "IMPLEMENTACION",
                    "DEPENDENCIA");

    private static final Set<String> DIRECCIONES_RELACION =
            Set.of("SIN_DIRECCION", "ORIGEN_A_DESTINO", "DESTINO_A_ORIGEN", "BIDIRECCIONAL");

    private static final Set<String> DIRECCIONES_PARAMETRO =
            Set.of("ENTRADA", "SALIDA", "ENTRADA_SALIDA", "RETORNO");

    private static final List<String> ARREGLOS_REQUERIDOS =
            List.of(
                    "paquetes",
                    "clases",
                    "atributos",
                    "operaciones",
                    "parametros",
                    "relaciones",
                    "posiciones_clases",
                    "posiciones_relaciones");

    /**
     * Valida exhaustivamente un snapshot JSON.
     *
     * @param nodo Raíz del snapshot parseado.
     * @param modeloEsperado UUID del modelo al que debe pertenecer la versión.
     * @param sha256Esperado Hash SHA-256 registrado en version_modelo_uml (opcional).
     * @param textoRaw Texto exacto de instantanea almacenado en PostgreSQL (opcional).
     */
    public void validar(
            JsonNode nodo, UUID modeloEsperado, String sha256Esperado, String textoRaw) {
        if (nodo == null || !nodo.isObject())
            throw new ApiException(422, "El snapshot debe ser un objeto JSON.");

        int formato = nodo.path("formato_version").asInt(-1);
        if (formato != 2 && formato != 3)
            throw new ApiException(
                    422, "El snapshot usa un formato de versión desconocido (" + formato + ").");

        // 1. Cabecera del modelo
        JsonNode modNode = nodo.path("modelo");
        if (!modNode.isObject())
            throw new ApiException(422, "El snapshot no contiene el objeto 'modelo'.");

        UUID idModelo = parsearUuid(modNode.path("id").asText(null), "modelo.id");
        if (!Objects.equals(idModelo, modeloEsperado))
            throw new ApiException(422, "El snapshot no pertenece al modelo indicado.");

        String nombreModelo = modNode.path("nombre").asText(null);
        if (nombreModelo == null || nombreModelo.isBlank())
            throw new ApiException(
                    422, "El nombre del modelo en el snapshot no puede estar vacío.");

        // 2. Arreglos esperados
        for (String campo : ARREGLOS_REQUERIDOS) {
            JsonNode arr = nodo.path(campo);
            if (!arr.isArray())
                throw new ApiException(422, "El grupo '" + campo + "' debe ser un arreglo JSON.");
        }

        // Identidad única por colección, como las claves primarias del esquema anterior.
        for (String campo :
                List.of(
                        "paquetes",
                        "clases",
                        "atributos",
                        "operaciones",
                        "parametros",
                        "relaciones")) {
            Set<UUID> identificadores = new HashSet<>();
            for (JsonNode e : nodo.path(campo))
                if (!identificadores.add(parsearUuid(e.path("id").asText(null), campo + ".id")))
                    throw new ApiException(
                            422, "UUID duplicado dentro de la colección del snapshot: " + campo);
        }
        // 3. Recolección de identificadores para integridad referencial
        Set<UUID> paquetes = new HashSet<>();
        for (JsonNode p : nodo.path("paquetes")) {
            UUID id = parsearUuid(p.path("id").asText(null), "paquete.id");
            String nombre = p.path("nombre").asText(null);
            if (nombre == null || nombre.isBlank())
                throw new ApiException(422, "El paquete " + id + " no tiene nombre válido.");
            paquetes.add(id);
        }

        Set<UUID> clases = new HashSet<>();
        for (JsonNode c : nodo.path("clases")) {
            UUID id = parsearUuid(c.path("id").asText(null), "clase.id");
            String nombre = c.path("nombre").asText(null);
            if (nombre == null || nombre.isBlank())
                throw new ApiException(422, "La clase " + id + " no tiene nombre válido.");
            String vis = c.path("visibilidad").asText("PUBLICO");
            if (!VISIBILIDADES.contains(vis))
                throw new ApiException(422, "Visibilidad inválida en clase " + id + ": " + vis);
            clases.add(id);
        }

        Set<UUID> operaciones = new HashSet<>();
        for (JsonNode o : nodo.path("operaciones")) {
            UUID id = parsearUuid(o.path("id").asText(null), "operacion.id");
            String nombre = o.path("nombre").asText(null);
            if (nombre == null || nombre.isBlank())
                throw new ApiException(422, "La operación " + id + " no tiene nombre válido.");
            String vis = o.path("visibilidad").asText("PUBLICO");
            if (!VISIBILIDADES.contains(vis))
                throw new ApiException(422, "Visibilidad inválida en operación " + id + ": " + vis);
            operaciones.add(id);
        }

        Set<UUID> relaciones = new HashSet<>();
        for (JsonNode r : nodo.path("relaciones")) {
            UUID id = parsearUuid(r.path("id").asText(null), "relacion.id");
            String tipo = r.path("tipo_relacion").asText("ASOCIACION");
            if (!TIPOS_RELACION.contains(tipo))
                throw new ApiException(
                        422, "Tipo de relación inválido en relación " + id + ": " + tipo);
            String dir = r.path("direccion").asText("SIN_DIRECCION");
            if (!DIRECCIONES_RELACION.contains(dir))
                throw new ApiException(422, "Dirección inválida en relación " + id + ": " + dir);
            relaciones.add(id);
        }

        // 4. Verificación de referencias cruzadas y límites
        for (JsonNode p : nodo.path("paquetes")) {
            if (p.hasNonNull("id_paquete_padre")) {
                UUID padre =
                        parsearUuid(
                                p.path("id_paquete_padre").asText(null),
                                "paquete.id_paquete_padre");
                if (!paquetes.contains(padre))
                    throw new ApiException(
                            422, "El paquete padre " + padre + " no existe en el snapshot.");
            }
        }

        for (JsonNode c : nodo.path("clases")) {
            if (c.hasNonNull("id_paquete")) {
                UUID idPaq = parsearUuid(c.path("id_paquete").asText(null), "clase.id_paquete");
                if (!paquetes.contains(idPaq))
                    throw new ApiException(
                            422, "El paquete " + idPaq + " de la clase no existe en el snapshot.");
            }
        }

        for (JsonNode a : nodo.path("atributos")) {
            UUID id = parsearUuid(a.path("id").asText(null), "atributo.id");
            UUID idClase = parsearUuid(a.path("id_clase").asText(null), "atributo.id_clase");
            if (!clases.contains(idClase))
                throw new ApiException(
                        422,
                        "La clase "
                                + idClase
                                + " del atributo "
                                + id
                                + " no existe en el snapshot.");
            String nombre = a.path("nombre").asText(null);
            if (nombre == null || nombre.isBlank())
                throw new ApiException(422, "El atributo " + id + " no tiene nombre válido.");
            String tipo = a.path("tipo_uml").asText(null);
            if (tipo == null || tipo.isBlank())
                throw new ApiException(422, "El atributo " + id + " no tiene tipo_uml válido.");
            String vis = a.path("visibilidad").asText("PRIVADO");
            if (!VISIBILIDADES.contains(vis))
                throw new ApiException(422, "Visibilidad inválida en atributo " + id + ": " + vis);
            for (String campo : List.of("multiplicidad_minima", "multiplicidad_maxima"))
                if (a.hasNonNull(campo)
                        && (!a.path(campo).isIntegralNumber() || !a.path(campo).canConvertToInt()))
                    throw new ApiException(422, "Límite de multiplicidad no entero en snapshot.");
            int min = a.path("multiplicidad_minima").asInt(1);
            if (min < 0)
                throw new ApiException(422, "Multiplicidad mínima negativa en atributo " + id);
            if (a.hasNonNull("multiplicidad_maxima")) {
                int max = a.path("multiplicidad_maxima").asInt();
                if (max < min)
                    throw new ApiException(
                            422, "Multiplicidad máxima menor que mínima en atributo " + id);
            }
            for (String campo : List.of("es_ordenado", "coleccion_unica"))
                if (a.has(campo) && !a.path(campo).isBoolean())
                    throw new ApiException(422, "Booleano inválido en atributo: " + campo);
            if (a.path("orden").asInt(0) < 0)
                throw new ApiException(422, "Orden negativo en atributo " + id);
        }

        for (JsonNode o : nodo.path("operaciones")) {
            UUID id = parsearUuid(o.path("id").asText(null), "operacion.id");
            UUID idClase = parsearUuid(o.path("id_clase").asText(null), "operacion.id_clase");
            if (!clases.contains(idClase))
                throw new ApiException(
                        422,
                        "La clase "
                                + idClase
                                + " de la operación "
                                + id
                                + " no existe en el snapshot.");
            if (o.path("orden").asInt(0) < 0)
                throw new ApiException(422, "Orden negativo en operación " + id);
        }

        for (JsonNode p : nodo.path("parametros")) {
            UUID id = parsearUuid(p.path("id").asText(null), "parametro.id");
            UUID idOp = parsearUuid(p.path("id_operacion").asText(null), "parametro.id_operacion");
            if (!operaciones.contains(idOp))
                throw new ApiException(
                        422,
                        "La operación "
                                + idOp
                                + " del parámetro "
                                + id
                                + " no existe en el snapshot.");
            String nombre = p.path("nombre").asText(null);
            if (nombre == null || nombre.isBlank())
                throw new ApiException(422, "El parámetro " + id + " no tiene nombre válido.");
            String dir = p.path("direccion").asText("ENTRADA");
            if (!DIRECCIONES_PARAMETRO.contains(dir))
                throw new ApiException(422, "Dirección inválida en parámetro " + id + ": " + dir);
            if (p.path("orden").asInt(0) < 0)
                throw new ApiException(422, "Orden negativo en parámetro " + id);
        }

        for (JsonNode r : nodo.path("relaciones")) {
            UUID id = parsearUuid(r.path("id").asText(null), "relacion.id");
            UUID idOrigen =
                    parsearUuid(r.path("id_clase_origen").asText(null), "relacion.id_clase_origen");
            UUID idDestino =
                    parsearUuid(
                            r.path("id_clase_destino").asText(null), "relacion.id_clase_destino");
            if (!clases.contains(idOrigen))
                throw new ApiException(
                        422,
                        "La clase origen " + idOrigen + " de la relación " + id + " no existe.");
            if (!clases.contains(idDestino))
                throw new ApiException(
                        422,
                        "La clase destino " + idDestino + " de la relación " + id + " no existe.");
        }

        for (JsonNode pos : nodo.path("posiciones_clases")) {
            UUID idClase =
                    parsearUuid(pos.path("id_clase").asText(null), "posicion_clase.id_clase");
            if (!clases.contains(idClase))
                throw new ApiException(
                        422, "Posición de clase " + idClase + " no corresponde a ninguna clase.");
            if (pos.path("ancho").asDouble(0) <= 0 || pos.path("alto").asDouble(0) <= 0)
                throw new ApiException(
                        422, "Dimensiones inválidas en posición de clase " + idClase);
        }

        for (JsonNode pos : nodo.path("posiciones_relaciones")) {
            UUID idRel =
                    parsearUuid(
                            pos.path("id_relacion").asText(null), "posicion_relacion.id_relacion");
            if (!relaciones.contains(idRel))
                throw new ApiException(
                        422,
                        "Posición de relación " + idRel + " no corresponde a ninguna relación.");
            if (!pos.path("puntos_intermedios").isArray())
                throw new ApiException(
                        422,
                        "puntos_intermedios en posición de relación "
                                + idRel
                                + " debe ser un arreglo.");
        }

        // 5. Contraste de hash SHA-256 canónico
        if (sha256Esperado != null
                && !sha256Esperado.isBlank()
                && textoRaw != null
                && !textoRaw.isBlank()) {
            String shaCalculado = calcularSha256(textoRaw);
            if (!shaCalculado.equalsIgnoreCase(sha256Esperado.trim())) {
                throw new ApiException(
                        422,
                        "El hash SHA-256 del snapshot no coincide con el registro de la versión.");
            }
        }
    }

    private UUID parsearUuid(String texto, String campo) {
        if (texto == null || texto.isBlank())
            throw new ApiException(422, "Campo UUID requerido ausente o vacío: " + campo);
        try {
            return UUID.fromString(texto.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(422, "Formato UUID inválido en campo " + campo + ": " + texto);
        }
    }

    private String calcularSha256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
