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
    private static final String VERBO_CREAR =
            "(?:crea|crear|creá|agrega|agregar|agregá|añade|añadir|añadí|anade|anadir|inserta|insertar|insertá|genera|generar|generá|definir|define|incorporar|incorpora|incluir|incluye|registrar|registra|nueva|nuevo)";
    private static final String VERBO_ELIMINAR =
            "(?:elimina|eliminar|eliminá|borra|borrar|borrá|quita|quitar|quitá|suprime|suprimir|suprimí|remueve|remover|remové|descarta|descartar|descartá)";
    private static final String VERBO_ACTUALIZAR =
            "(?:renombra|renombrar|renombrá|cambia|cambiar|cambiá|modifica|modificar|modificá|actualiza|actualizar|actualizá|edita|editar|editá)";
    private static final String VERBO_RELACIONAR =
            "(?:relaciona|relacionar|relaciónala|relacionala|relaciónalo|relacionalo|relaciónalos|relacionalos|relaciónalas|relacionalas|asocia|asociar|asóciala|asociala|asócialo|asocialo|conecta|conectar|conectala|conéctala|conectalo|conéctalo|vincula|vincular|vinculala|vincúlala|vinculalo|vincúlalo|une|unir|unela|únela|unelo|únelo|enlaza|enlazar|enlazala|enlázala|enlazalo|enlázalo)";

    private static final Pattern CREAR_CLASE =
            Pattern.compile(
                    "(?iu)\\b(?:"
                            + VERBO_CREAR
                            + "\\s+(?:una\\s+|la\\s+)?(?:nueva\\s+)?clase|(?:una\\s+)?nueva\\s+clase)\\b");
    private static final Pattern CLASE =
            Pattern.compile(
                    "(?iu)\\b(?:(?:una|la|nueva)\\s+)?clase\\s+(?:llamada\\s+|de\\s+nombre\\s+)?"
                            + ID);
    private static final Pattern BLOQUE_ATRIBUTOS =
            Pattern.compile(
                    "(?iu)\\b(?:con\\s+)?(?:sus\\s+|los\\s+|las\\s+)?(?:atributos?|campos?|propiedades?)\\s+(.+?)(?=\\b(?:y\\s+)?(?:con\\s+)?(?:relaci[oó]n|herencia|generalizaci[oó]n|asociaci[oó]n|composici[oó]n|agregaci[oó]n|dependencia|relaciona|asocia|conecta|vincula|une|enlaza)\\b|$)");
    private static final Pattern ATRIBUTO = Pattern.compile("(?iu)" + ID + "\\s*:\\s*" + TIPO);
    private static final Pattern ATRIBUTO_DE_TIPO =
            Pattern.compile("(?iu)" + ID + "\\s+de\\s+tipo\\s+" + TIPO);
    private static final Pattern ATRIBUTO_JAVA = Pattern.compile("(?iu)" + TIPO + "\\s+" + ID);
    private static final Pattern PATRON_SOLO_ID = Pattern.compile("(?iu)^" + ID + "$");
    private static final Pattern CARDINALIDAD =
            Pattern.compile(
                    "(?iu)(\\d+|\\*|muchos?|muchas?|varios?|varias?|uno|un|cero)\\s*(?:a|:|\\.\\.|-)\\s*(\\d+|\\*|muchos?|muchas?|varios?|varias?|uno|un|cero|[nN])");
    private static final Pattern RENOMBRAR_CLASE =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ACTUALIZAR
                            + "\\s+(?:el\\s+nombre\\s+de\\s+)?(?:(?:la|una)\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:a|por)\\s+"
                            + ID);
    private static final Pattern ELIMINAR_CLASE =
            Pattern.compile("(?iu)\\b" + VERBO_ELIMINAR + "\\s+(?:(?:la|una)\\s+)?clase\\s+" + ID);
    private static final Pattern CREAR_ATRIBUTO =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_CREAR
                            + "\\s+(?:el\\s+|un\\s+|la\\s+|una\\s+)?(?:atributo|campo|propiedad)\\s+"
                            + ID
                            + "(?:\\s*:\\s*"
                            + TIPO
                            + "|\\s+de\\s+tipo\\s+"
                            + TIPO
                            + ")?\\s+(?:a|en|para)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID);
    private static final Pattern CREAR_ATRIBUTO_EN_CLASE =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_CREAR
                            + "\\s+(?:a|en|para)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:el\\s+|un\\s+|la\\s+|una\\s+)?(?:atributo|campo|propiedad)\\s+"
                            + ID
                            + "(?:\\s*:\\s*"
                            + TIPO
                            + "|\\s+de\\s+tipo\\s+"
                            + TIPO
                            + ")?");
    private static final Pattern CREAR_ATRIBUTO_PREFIJO =
            Pattern.compile(
                    "(?iu)\\ben\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID
                            + "\\s+"
                            + VERBO_CREAR
                            + "\\s+(?:el\\s+|un\\s+|la\\s+|una\\s+)?(?:atributo|campo|propiedad)\\s+"
                            + ID
                            + "(?:\\s*:\\s*"
                            + TIPO
                            + "|\\s+de\\s+tipo\\s+"
                            + TIPO
                            + ")?");
    private static final Pattern CREAR_VARIOS_ATRIBUTOS =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_CREAR
                            + "\\s+(?:los\\s+|las\\s+|sus\\s+)?(?:atributos|campos|propiedades)\\s+(.+?)\\s+(?:a|en|para)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID);
    private static final Pattern RENOMBRAR_ATRIBUTO =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ACTUALIZAR
                            + "\\s+(?:el\\s+|un\\s+|la\\s+|una\\s+)?(?:atributo|campo|propiedad)\\s+"
                            + ID
                            + "\\s+(?:de|en)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:a|por)\\s+"
                            + ID
                            + "(?:\\s*:\\s*"
                            + TIPO
                            + "|\\s+de\\s+tipo\\s+"
                            + TIPO
                            + ")?");
    private static final Pattern RENOMBRAR_ATRIBUTO_EN_CLASE =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ACTUALIZAR
                            + "\\s+(?:de|en)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:el\\s+|un\\s+|la\\s+|una\\s+)?(?:atributo|campo|propiedad)\\s+"
                            + ID
                            + "\\s+(?:a|por)\\s+"
                            + ID
                            + "(?:\\s*:\\s*"
                            + TIPO
                            + "|\\s+de\\s+tipo\\s+"
                            + TIPO
                            + ")?");
    private static final Pattern ELIMINAR_ATRIBUTO =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ELIMINAR
                            + "\\s+(?:el\\s+|un\\s+|la\\s+|una\\s+)?(?:atributo|campo|propiedad)\\s+"
                            + ID
                            + "\\s+(?:de|en)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID);
    private static final Pattern ELIMINAR_ATRIBUTO_EN_CLASE =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ELIMINAR
                            + "\\s+(?:de|en)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:el\\s+|un\\s+|la\\s+|una\\s+)?(?:atributo|campo|propiedad)\\s+"
                            + ID);
    private static final Pattern CREAR_OPERACION =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_CREAR
                            + "\\s+(?:la\\s+|una\\s+|el\\s+|un\\s+)?(?:operaci[oó]n|m[eé]todo|funci[oó]n)\\s+"
                            + ID
                            + "\\s*\\(\\s*\\)\\s*(?::\\s*"
                            + TIPO
                            + ")?\\s+(?:a|en)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID);
    private static final Pattern ELIMINAR_OPERACION =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ELIMINAR
                            + "\\s+(?:la\\s+|una\\s+|el\\s+|un\\s+)?(?:operaci[oó]n|m[eé]todo|funci[oó]n)\\s+"
                            + ID
                            + "(?:\\s*\\(\\s*\\))?\\s+(?:de|en)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID);
    private static final Pattern ELIMINAR_OPERACION_EN_CLASE =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ELIMINAR
                            + "\\s+(?:de|en)\\s+(?:(?:la|una)\\s+)?clase\\s+"
                            + ID
                            + "\\s+(?:la\\s+|una\\s+|el\\s+|un\\s+)?(?:operaci[oó]n|m[eé]todo|funci[oó]n)\\s+"
                            + ID
                            + "(?:\\s*\\(\\s*\\))?");
    private static final Pattern CREAR_RELACION =
            Pattern.compile(
                    "(?iu)\\b(?:"
                            + VERBO_CREAR
                            + "\\s+(?:una\\s+)?(?:relaci[oó]n|herencia|generalizaci[oó]n|asociaci[oó]n|composici[oó]n|agregaci[oó]n|dependencia)?\\s*(?:entre|de)?|"
                            + VERBO_RELACIONAR
                            + ")\\s+(?:(?:la|una)\\s+clase\\s+)?"
                            + ID
                            + "\\s+(?:con|y|a)\\s+(?:(?:la|una)\\s+clase\\s+)?"
                            + ID);
    private static final Pattern RELACION_HERENCIA_DIRECTA =
            Pattern.compile(
                    "(?iu)\\b(?:(?:la|una)\\s+clase\\s+)?"
                            + ID
                            + "\\s+(?:hereda\\s+de|extiende\\s+(?:de\\s+)?|es\\s+subclase\\s+de|es\\s+hija?\\s+de)\\s+(?:(?:la|una)\\s+clase\\s+)?"
                            + ID);
    private static final Pattern RELACION_HERENCIA_INVERSA =
            Pattern.compile(
                    "(?iu)\\b(?:(?:la|una)\\s+clase\\s+)?"
                            + ID
                            + "\\s+(?:hereda\\s+a|herada\\s+a|es\\s+padre\\s+de|es\\s+superclase\\s+de)\\s+(?:(?:la|una)\\s+clase\\s+)?"
                            + ID);
    private static final Pattern ELIMINAR_RELACION =
            Pattern.compile(
                    "(?iu)\\b"
                            + VERBO_ELIMINAR
                            + "\\s+(?:la\\s+)?(?:relaci[oó]n|asociaci[oó]n|herencia)?\\s*(?:entre|de)?\\s*(?:(?:la|una)\\s+clase\\s+)?"
                            + ID
                            + "\\s+(?:y|con)\\s+(?:(?:la|una)\\s+clase\\s+)?"
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

    public Interpretacion interpretarLocal(String prompt, Contexto contexto) {
        if (CREAR_CLASE.matcher(prompt).find()) return crearClases(prompt, contexto);
        return cambiarExistentes(prompt, contexto);
    }

    private Interpretacion crearClases(String prompt, Contexto contexto) {
        Matcher clases = CLASE.matcher(prompt);
        List<CapturaClase> capturas = new ArrayList<>();
        while (clases.find())
            capturas.add(new CapturaClase(clases.group(1), clases.start(), clases.end()));
        if (capturas.isEmpty()) throw noReconocida();

        // Agrupar menciones únicas de clases preservando el orden de primera aparición
        Map<String, CapturaClase> capturasUnicas = new LinkedHashMap<>();
        for (CapturaClase c : capturas) {
            capturasUnicas.putIfAbsent(c.nombre().toLowerCase(Locale.ROOT), c);
        }
        List<CapturaClase> listaUnica = new ArrayList<>(capturasUnicas.values());

        List<OperacionGenerada> operaciones = new ArrayList<>();
        List<UUID> operacionesClase = new ArrayList<>();
        List<UUID> entidadesClase = new ArrayList<>();
        List<String> nombresClaseLista = new ArrayList<>();
        boolean hayRelacion = contieneRelacion(prompt);

        Set<String> nombresExistentesContexto = new HashSet<>();
        contexto.nombresClases().forEach(n -> nombresExistentesContexto.add(n.toLowerCase(Locale.ROOT)));

        int secuencia = 1;
        int totalAtributos = 0;
        int index = 0;

        for (CapturaClase captura : listaUnica) {
            String nombreClase = captura.nombre();
            String nombreLower = nombreClase.toLowerCase(Locale.ROOT);
            nombresClaseLista.add(nombreClase);

            Optional<ElementoContexto> existente = buscar(contexto, "CLASE", nombreClase, null);
            boolean yaExiste = existente.isPresent() || nombresExistentesContexto.contains(nombreLower);

            // Si ya existe en el contexto pero es parte de una relación con otra clase nueva, se enlaza sin duplicar
            boolean enlazarExistente = yaExiste && hayRelacion && listaUnica.size() >= 2 && listaUnica.stream().anyMatch(c -> !nombresExistentesContexto.contains(c.nombre().toLowerCase(Locale.ROOT)));

            UUID idEntidad;
            UUID idOperacion = null;

            if (enlazarExistente) {
                idEntidad = existente.map(ElementoContexto::id).orElseGet(UUID::randomUUID);
                entidadesClase.add(idEntidad);
            } else {
                idEntidad = UUID.randomUUID();
                idOperacion = UUID.randomUUID();
                boolean duplicada = yaExiste;
                ObjectNode datos = json.createObjectNode();
                datos.put("nombre", nombreClase);
                datos.put("esPersistente", false);
                datos.put("x", coordenadaX(contexto.cantidadClases() + index));
                datos.put("y", coordenadaY(contexto.cantidadClases() + index));
                String justificacion =
                        "Crear la clase mencionada explícitamente como " + nombreClase;
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
            }

            int finSegmento = prompt.length();
            for (CapturaClase otra : capturas) {
                if (otra.inicio() > captura.fin() && otra.inicio() < finSegmento) {
                    finSegmento = otra.inicio();
                }
            }
            String segmento = prompt.substring(captura.fin(), finSegmento);
            Matcher bloque = BLOQUE_ATRIBUTOS.matcher(segmento);
            if (bloque.find()) {
                String contenidoBloque = bloque.group(1).trim();
                String[] items =
                        contenidoBloque.split("(?iu)(?:\\s*,\\s*|\\s*;\\s*|\\s+y\\s+|\\s+e\\s+)");
                for (String item : items) {
                    String itemLimpio = item.trim();
                    if (itemLimpio.isBlank()) continue;
                    String nombreAtributo = null;
                    String tipoAtributo = null;
                    Matcher mTipo = ATRIBUTO.matcher(itemLimpio);
                    if (mTipo.find()) {
                        nombreAtributo = mTipo.group(1);
                        tipoAtributo = normalizarTipo(mTipo.group(2));
                    } else {
                        Matcher mDeTipo = ATRIBUTO_DE_TIPO.matcher(itemLimpio);
                        if (mDeTipo.find()) {
                            nombreAtributo = mDeTipo.group(1);
                            tipoAtributo = normalizarTipo(mDeTipo.group(2));
                        } else {
                            Matcher mJava = ATRIBUTO_JAVA.matcher(itemLimpio);
                            if (mJava.find()) {
                                tipoAtributo = normalizarTipo(mJava.group(1));
                                nombreAtributo = mJava.group(2);
                            } else {
                                Matcher mSoloId = PATRON_SOLO_ID.matcher(itemLimpio);
                                if (mSoloId.find()) {
                                    nombreAtributo = mSoloId.group(1);
                                    tipoAtributo = inferirTipo(nombreAtributo);
                                }
                            }
                        }
                    }
                    if (nombreAtributo == null) continue;

                    ObjectNode datosAtributo = json.createObjectNode();
                    datosAtributo.put("idClase", idEntidad.toString());
                    datosAtributo.put("nombre", nombreAtributo);
                    datosAtributo.put("tipoUml", tipoAtributo);
                    String justificacionAtributo =
                            "Crear el atributo "
                                    + nombreAtributo
                                    + ": "
                                    + tipoAtributo
                                    + " en "
                                    + nombreClase;
                    operaciones.add(
                            operacion(
                                    UUID.randomUUID(),
                                    secuencia++,
                                    "CREAR",
                                    "ATRIBUTO",
                                    UUID.randomUUID(),
                                    null,
                                    datosAtributo,
                                    !enlazarExistente || !yaExiste,
                                    null,
                                    justificacionAtributo,
                                    idOperacion != null ? List.of(idOperacion) : List.of()));
                    totalAtributos++;
                }
            }
            index++;
        }

        int totalRelaciones = 0;
        if (entidadesClase.size() >= 2 && hayRelacion) {
            boolean clasesValidas =
                    operaciones.stream()
                            .filter(o -> o.tipoEntidad().equals("CLASE"))
                            .allMatch(OperacionGenerada::seleccionada);

            Matcher mRel = CREAR_RELACION.matcher(prompt);
            while (mRel.find()) {
                String origen = mRel.group(1);
                String destino = mRel.group(2);
                UUID idOrigen = null;
                UUID idDestino = null;
                for (int i = 0; i < nombresClaseLista.size(); i++) {
                    if (nombresClaseLista.get(i).equalsIgnoreCase(origen)) idOrigen = entidadesClase.get(i);
                    if (nombresClaseLista.get(i).equalsIgnoreCase(destino)) idDestino = entidadesClase.get(i);
                }
                if (idOrigen == null) {
                    Optional<ElementoContexto> co = buscar(contexto, "CLASE", origen, null);
                    if (co.isPresent()) idOrigen = co.get().id();
                }
                if (idDestino == null) {
                    Optional<ElementoContexto> cd = buscar(contexto, "CLASE", destino, null);
                    if (cd.isPresent()) idDestino = cd.get().id();
                }
                if (idOrigen != null && idDestino != null) {
                    String subPrompt = prompt.substring(mRel.start());
                    int finSub = subPrompt.indexOf('\n');
                    if (finSub > 0) subPrompt = subPrompt.substring(0, finSub);
                    String tipoRelacion = inferirTipoRelacion(subPrompt);
                    ExtremosRelacion extremos = resolverExtremos(idOrigen, origen, idDestino, destino, tipoRelacion, subPrompt);
                    ObjectNode datosRelacion = relacion(extremos.origen(), extremos.destino(), subPrompt);
                    String justificacion =
                            "GENERALIZACION".equals(tipoRelacion)
                                    ? "Crear la relación de herencia entre "
                                            + origen
                                            + " y "
                                            + destino
                                    : "Crear la relación y cardinalidad indicadas entre "
                                            + origen
                                            + " y "
                                            + destino;
                    operaciones.add(
                            operacion(
                                    UUID.randomUUID(),
                                    secuencia++,
                                    "CREAR",
                                    "RELACION",
                                    UUID.randomUUID(),
                                    null,
                                    datosRelacion,
                                    clasesValidas,
                                    clasesValidas
                                            ? null
                                            : "La relación depende de clases inválidas.",
                                    justificacion,
                                    List.copyOf(operacionesClase)));
                    totalRelaciones++;
                }
            }

            if (totalRelaciones == 0) {
                String tipoRelacion = inferirTipoRelacion(prompt);
                UUID id1 = entidadesClase.get(0);
                String n1 = nombresClaseLista.get(0);
                UUID id2 = entidadesClase.get(1);
                String n2 = nombresClaseLista.get(1);

                ExtremosRelacion extremos = resolverExtremos(id1, n1, id2, n2, tipoRelacion, prompt);
                ObjectNode datosRelacion = relacion(extremos.origen(), extremos.destino(), prompt);

                String justificacion =
                        "GENERALIZACION".equals(tipoRelacion)
                                ? "Crear la relación de herencia entre las clases"
                                : "Crear la relación y cardinalidad indicadas entre las dos clases";

                operaciones.add(
                        operacion(
                                UUID.randomUUID(),
                                secuencia++,
                                "CREAR",
                                "RELACION",
                                UUID.randomUUID(),
                                null,
                                datosRelacion,
                                clasesValidas,
                                clasesValidas ? null : "La relación depende de clases inválidas.",
                                justificacion,
                                List.copyOf(operacionesClase)));
                totalRelaciones = 1;
            }
        }

        String resumen =
                "Se interpretaron "
                        + listaUnica.size()
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

    public static String inferirTipo(String nombre) {
        if (nombre == null || nombre.isBlank()) return "String";
        String n = nombre.toLowerCase(Locale.ROOT).trim();

        // Identificadores y UUIDs
        if (n.equals("id")
                || n.endsWith("id")
                || n.equals("identificador")
                || n.equals("uuid")
                || n.equals("guid")
                || n.equals("pk")
                || n.endsWith("_id")
                || n.startsWith("id_")
                || n.startsWith("pk_")) {
            return (n.equals("uuid") || n.equals("guid")) ? "UUID" : "Long";
        }

        // Decimales / Moneda / Mediciones
        if (n.matches(
                ".*(precio|price|total|costo|cost|subtotal|monto|amount|saldo|balance|descuento|discount|tarifa|tasa|sueldo|salario|peso|weight|altura|height|longitud|latitud|promedio|porcentaje|interes|iva|valor).*")) {
            return "Double";
        }

        // Enteros / Cantidades / Contadores
        if (n.matches(
                ".*(edad|age|cantidad|quantity|cant|stock|numero|number|num|contador|count|orden|order|mes|dia|anio|año|year|puntos|points|secuencia|intentos|prioridad|nivel|limite|version|minimo|maximo|tamano|tamaño|posicion).*")) {
            return "Integer";
        }

        // Booleanos
        if (n.matches("(es|tiene|esta|has|is)[A-Z_].*")
                || n.matches(
                        ".*(activo|active|habilitado|enabled|disponible|available|pagado|paid|valido|valid|eliminado|deleted|visible|verificado|confirmado|bloqueado|editable).*")) {
            return "Boolean";
        }

        // Fechas y horas
        if (n.matches(".*(fecha|date|nacimiento|vencimiento|creacion|emision|expiracion).*")) {
            return "LocalDate";
        }
        if (n.matches(".*(hora|time|timestamp).*") || n.endsWith("at")) {
            return "LocalDateTime";
        }

        // Por defecto texto
        return "String";
    }

    private void agregarOperacionCrearAtributo(
            List<OperacionGenerada> operaciones,
            Contexto contexto,
            int[] secuencia,
            String clase,
            String nombre,
            String tipo) {
        String tipoFinal = (tipo != null && !tipo.isBlank()) ? normalizarTipo(tipo) : inferirTipo(nombre);
        Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
        ObjectNode propuesto = json.createObjectNode();
        contenedor.ifPresent(c -> propuesto.put("idClase", c.id().toString()));
        propuesto.put("nombre", nombre).put("tipoUml", tipoFinal);
        boolean duplicado =
                contenedor.isPresent()
                        && (buscar(contexto, "ATRIBUTO", nombre, contenedor.get().id()).isPresent()
                                || operaciones.stream()
                                        .anyMatch(
                                                o ->
                                                        "CREAR".equals(o.tipoOperacion())
                                                                && "ATRIBUTO"
                                                                        .equals(o.tipoEntidad())
                                                                && o.valorPropuesto() != null
                                                                && nombre.equalsIgnoreCase(
                                                                        o.valorPropuesto()
                                                                                .path("nombre")
                                                                                .asText())
                                                                && contenedor
                                                                        .get()
                                                                        .id()
                                                                        .toString()
                                                                        .equals(
                                                                                o.valorPropuesto()
                                                                                        .path(
                                                                                                "idClase")
                                                                                        .asText())));
        String error =
                contenedor.isEmpty()
                        ? "No existe la clase contenedora indicada."
                        : duplicado ? "La clase ya contiene un atributo con ese nombre." : null;
        operaciones.add(
                operacion(
                        UUID.randomUUID(),
                        secuencia[0]++,
                        "CREAR",
                        "ATRIBUTO",
                        UUID.randomUUID(),
                        null,
                        propuesto,
                        error == null,
                        error,
                        "Crear el atributo " + nombre + ": " + tipoFinal + " en " + clase,
                        List.of()));
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

        int[] secuenciaArr = {secuencia};
        Matcher mAtributo = CREAR_ATRIBUTO.matcher(prompt);
        while (mAtributo.find()) {
            String nombre = mAtributo.group(1);
            String tipo = mAtributo.group(2) != null ? mAtributo.group(2) : mAtributo.group(3);
            String clase = mAtributo.group(4);
            agregarOperacionCrearAtributo(operaciones, contexto, secuenciaArr, clase, nombre, tipo);
        }
        Matcher mEnClase = CREAR_ATRIBUTO_EN_CLASE.matcher(prompt);
        while (mEnClase.find()) {
            String clase = mEnClase.group(1);
            String nombre = mEnClase.group(2);
            String tipo = mEnClase.group(3) != null ? mEnClase.group(3) : mEnClase.group(4);
            agregarOperacionCrearAtributo(operaciones, contexto, secuenciaArr, clase, nombre, tipo);
        }
        Matcher mPrefijo = CREAR_ATRIBUTO_PREFIJO.matcher(prompt);
        while (mPrefijo.find()) {
            String clase = mPrefijo.group(1);
            String nombre = mPrefijo.group(2);
            String tipo = mPrefijo.group(3) != null ? mPrefijo.group(3) : mPrefijo.group(4);
            agregarOperacionCrearAtributo(operaciones, contexto, secuenciaArr, clase, nombre, tipo);
        }
        Matcher mVarios = CREAR_VARIOS_ATRIBUTOS.matcher(prompt);
        while (mVarios.find()) {
            String bloqueAttrs = mVarios.group(1);
            String clase = mVarios.group(2);
            String[] items = bloqueAttrs.split("(?iu)(?:\\s*,\\s*|\\s*;\\s*|\\s+y\\s+|\\s+e\\s+)");
            for (String item : items) {
                String itemLimpio = item.trim();
                if (itemLimpio.isBlank()) continue;
                String nombreAtributo = null;
                String tipoAtributo = null;
                Matcher mTipo = ATRIBUTO.matcher(itemLimpio);
                if (mTipo.find()) {
                    nombreAtributo = mTipo.group(1);
                    tipoAtributo = mTipo.group(2);
                } else {
                    Matcher mDeTipo = ATRIBUTO_DE_TIPO.matcher(itemLimpio);
                    if (mDeTipo.find()) {
                        nombreAtributo = mDeTipo.group(1);
                        tipoAtributo = mDeTipo.group(2);
                    } else {
                        Matcher mSoloId = PATRON_SOLO_ID.matcher(itemLimpio);
                        if (mSoloId.find()) {
                            nombreAtributo = mSoloId.group(1);
                            tipoAtributo = inferirTipo(nombreAtributo);
                        }
                    }
                }
                if (nombreAtributo != null) {
                    agregarOperacionCrearAtributo(
                            operaciones, contexto, secuenciaArr, clase, nombreAtributo, tipoAtributo);
                }
            }
        }
        secuencia = secuenciaArr[0];
        m = RENOMBRAR_ATRIBUTO.matcher(prompt);
        while (m.find()) {
            String anterior = m.group(1), clase = m.group(2), nuevo = m.group(3), tipo = m.group(4);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            Optional<ElementoContexto> existente =
                    contenedor.flatMap(c -> buscar(contexto, "ATRIBUTO", anterior, c.id()));
            JsonNode actual = existente.map(ElementoContexto::valor).orElse(null);
            ObjectNode propuesto = copia(actual);
            String nombreFinal = nuevo;
            String tipoFinal = tipo;
            if (tipoFinal == null && esTipoConocido(nuevo)) {
                nombreFinal = anterior;
                tipoFinal = nuevo;
            }
            propuesto.put("nombre", nombreFinal);
            if (tipoFinal != null) propuesto.put("tipoUml", normalizarTipo(tipoFinal));
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
        m = RENOMBRAR_ATRIBUTO_EN_CLASE.matcher(prompt);
        while (m.find()) {
            String clase = m.group(1), anterior = m.group(2), nuevo = m.group(3), tipo = m.group(4);
            if (operaciones.stream().noneMatch(o -> "ACTUALIZAR".equals(o.tipoOperacion()) && "ATRIBUTO".equals(o.tipoEntidad()))) {
                Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
                Optional<ElementoContexto> existente =
                        contenedor.flatMap(c -> buscar(contexto, "ATRIBUTO", anterior, c.id()));
                JsonNode actual = existente.map(ElementoContexto::valor).orElse(null);
                ObjectNode propuesto = copia(actual);
                String nombreFinal = nuevo;
                String tipoFinal = tipo;
                if (tipoFinal == null && esTipoConocido(nuevo)) {
                    nombreFinal = anterior;
                    tipoFinal = nuevo;
                }
                propuesto.put("nombre", nombreFinal);
                if (tipoFinal != null) propuesto.put("tipoUml", normalizarTipo(tipoFinal));
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
        }
        m = ELIMINAR_ATRIBUTO.matcher(prompt);
        while (m.find()) {
            String nombre = m.group(1), clase = m.group(2);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            Optional<ElementoContexto> existente =
                    contenedor.flatMap(c -> buscar(contexto, "ATRIBUTO", nombre, c.id()));
            operaciones.add(eliminar(secuencia++, "ATRIBUTO", nombre, clase, existente));
        }
        m = ELIMINAR_ATRIBUTO_EN_CLASE.matcher(prompt);
        while (m.find()) {
            String clase = m.group(1), nombre = m.group(2);
            if (operaciones.stream().noneMatch(o -> "ELIMINAR".equals(o.tipoOperacion()) && "ATRIBUTO".equals(o.tipoEntidad()))) {
                Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
                Optional<ElementoContexto> existente =
                        contenedor.flatMap(c -> buscar(contexto, "ATRIBUTO", nombre, c.id()));
                operaciones.add(eliminar(secuencia++, "ATRIBUTO", nombre, clase, existente));
            }
        }
        m = CREAR_OPERACION.matcher(prompt);
        while (m.find()) {
            String nombre = m.group(1), retorno = m.group(2), clase = m.group(3);
            Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
            ObjectNode propuesto = json.createObjectNode();
            contenedor.ifPresent(c -> propuesto.put("idClase", c.id().toString()));
            propuesto.put("nombre", nombre);
            if (retorno == null) propuesto.putNull("tipoRetorno");
            else propuesto.put("tipoRetorno", normalizarTipo(retorno));
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
        m = ELIMINAR_OPERACION_EN_CLASE.matcher(prompt);
        while (m.find()) {
            String clase = m.group(1), nombre = m.group(2);
            if (operaciones.stream().noneMatch(o -> "ELIMINAR".equals(o.tipoOperacion()) && "OPERACION".equals(o.tipoEntidad()))) {
                Optional<ElementoContexto> contenedor = buscar(contexto, "CLASE", clase, null);
                Optional<ElementoContexto> existente =
                        contenedor.flatMap(c -> buscar(contexto, "OPERACION", nombre, c.id()));
                operaciones.add(eliminar(secuencia++, "OPERACION", nombre, clase, existente));
            }
        }
        m = CREAR_RELACION.matcher(prompt);
        while (m.find()) {
            String origen = m.group(1), destino = m.group(2);
            Optional<ElementoContexto> claseOrigen = buscar(contexto, "CLASE", origen, null);
            Optional<ElementoContexto> claseDestino = buscar(contexto, "CLASE", destino, null);
            boolean valida = claseOrigen.isPresent() && claseDestino.isPresent();
            String tipoRelacion = inferirTipoRelacion(prompt.substring(m.start()));
            UUID id1 = claseOrigen.map(ElementoContexto::id).orElseGet(UUID::randomUUID);
            UUID id2 = claseDestino.map(ElementoContexto::id).orElseGet(UUID::randomUUID);
            ExtremosRelacion extremos = resolverExtremos(id1, origen, id2, destino, tipoRelacion, prompt);
            operaciones.add(
                    operacion(
                            UUID.randomUUID(),
                            secuencia++,
                            "CREAR",
                            "RELACION",
                            UUID.randomUUID(),
                            null,
                            relacion(
                                    extremos.origen(),
                                    extremos.destino(),
                                    prompt.substring(m.start())),
                            valida,
                            valida ? null : "Uno o ambos extremos no existen en el modelo actual.",
                            "Crear la relación entre " + origen + " y " + destino,
                            List.of()));
        }
        Matcher mHerenciaDir = RELACION_HERENCIA_DIRECTA.matcher(prompt);
        while (mHerenciaDir.find()) {
            String hijo = mHerenciaDir.group(1), padre = mHerenciaDir.group(2);
            if (operaciones.stream().noneMatch(o -> "RELACION".equals(o.tipoEntidad()))) {
                Optional<ElementoContexto> claseHijo = buscar(contexto, "CLASE", hijo, null);
                Optional<ElementoContexto> clasePadre = buscar(contexto, "CLASE", padre, null);
                boolean valida = claseHijo.isPresent() && clasePadre.isPresent();
                operaciones.add(
                        operacion(
                                UUID.randomUUID(),
                                secuencia++,
                                "CREAR",
                                "RELACION",
                                UUID.randomUUID(),
                                null,
                                relacion(
                                        claseHijo
                                                .map(ElementoContexto::id)
                                                .orElseGet(UUID::randomUUID),
                                        clasePadre
                                                .map(ElementoContexto::id)
                                                .orElseGet(UUID::randomUUID),
                                        "herencia"),
                                valida,
                                valida
                                        ? null
                                        : "Uno o ambos extremos no existen en el modelo actual.",
                                "Crear la relación de herencia entre " + hijo + " y " + padre,
                                List.of()));
            }
        }
        Matcher mHerenciaInv = RELACION_HERENCIA_INVERSA.matcher(prompt);
        while (mHerenciaInv.find()) {
            String padre = mHerenciaInv.group(1), hijo = mHerenciaInv.group(2);
            if (operaciones.stream().noneMatch(o -> "RELACION".equals(o.tipoEntidad()))) {
                Optional<ElementoContexto> clasePadre = buscar(contexto, "CLASE", padre, null);
                Optional<ElementoContexto> claseHijo = buscar(contexto, "CLASE", hijo, null);
                boolean valida = claseHijo.isPresent() && clasePadre.isPresent();
                operaciones.add(
                        operacion(
                                UUID.randomUUID(),
                                secuencia++,
                                "CREAR",
                                "RELACION",
                                UUID.randomUUID(),
                                null,
                                relacion(
                                        claseHijo
                                                .map(ElementoContexto::id)
                                                .orElseGet(UUID::randomUUID),
                                        clasePadre
                                                .map(ElementoContexto::id)
                                                .orElseGet(UUID::randomUUID),
                                        "herencia"),
                                valida,
                                valida
                                        ? null
                                        : "Uno o ambos extremos no existen en el modelo actual.",
                                "Crear la relación de herencia entre " + hijo + " y " + padre,
                                List.of()));
            }
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

    private record ExtremosRelacion(UUID origen, UUID destino) {}

    private ExtremosRelacion resolverExtremos(
            UUID id1, String nombre1, UUID id2, String nombre2, String tipo, String prompt) {
        if (!"GENERALIZACION".equals(tipo)) {
            return new ExtremosRelacion(id1, id2);
        }
        String p = prompt.toLowerCase(Locale.ROOT);
        String n1 = nombre1.toLowerCase(Locale.ROOT);
        String n2 = nombre2.toLowerCase(Locale.ROOT);

        // n2 hereda a n1 / n2 herada a n1 / n2 es padre/superclase de n1 -> n2 es padre (destino),
        // n1 es hijo (origen)
        if (p.matches(
                ".*\\b"
                        + Pattern.quote(n2)
                        + "\\s+(?:hereda\\s+a|herada\\s+a|es\\s+padre\\s+de|es\\s+superclase\\s+de)\\s+(?:(?:la|una)\\s+clase\\s+)?"
                        + Pattern.quote(n1)
                        + "\\b.*")) {
            return new ExtremosRelacion(id1, id2);
        }

        // n1 hereda a n2 / n1 herada a n2 / n1 es padre/superclase de n2 -> n1 es padre (destino),
        // n2 es hijo (origen)
        if (p.matches(
                ".*\\b"
                        + Pattern.quote(n1)
                        + "\\s+(?:hereda\\s+a|herada\\s+a|es\\s+padre\\s+de|es\\s+superclase\\s+de)\\s+(?:(?:la|una)\\s+clase\\s+)?"
                        + Pattern.quote(n2)
                        + "\\b.*")) {
            return new ExtremosRelacion(id2, id1);
        }

        // n1 hereda de n2 / n1 extiende de n2 / n1 es subclase de n2 / n1 es hija de n2 -> n1 es
        // hijo (origen), n2 es padre (destino)
        if (p.matches(
                ".*\\b"
                        + Pattern.quote(n1)
                        + "\\s+(?:hereda\\s+de|extiende\\s+(?:de\\s+)?|es\\s+subclase\\s+de|es\\s+hija?\\s+de)\\s+(?:(?:la|una)\\s+clase\\s+)?"
                        + Pattern.quote(n2)
                        + "\\b.*")) {
            return new ExtremosRelacion(id1, id2);
        }

        // n2 hereda de n1 / n2 extiende de n1 / n2 es subclase de n1 / n2 es hija de n1 -> n2 es
        // hijo (origen), n1 es padre (destino)
        if (p.matches(
                ".*\\b"
                        + Pattern.quote(n2)
                        + "\\s+(?:hereda\\s+de|extiende\\s+(?:de\\s+)?|es\\s+subclase\\s+de|es\\s+hija?\\s+de)\\s+(?:(?:la|una)\\s+clase\\s+)?"
                        + Pattern.quote(n1)
                        + "\\b.*")) {
            return new ExtremosRelacion(id2, id1);
        }

        // Por defecto: id1 origen (hijo/subclase), id2 destino (padre/superclase)
        return new ExtremosRelacion(id1, id2);
    }

    private String inferirTipoRelacion(String prompt) {
        String p = prompt.toLowerCase(Locale.ROOT);
        if (p.matches(
                ".*\\b(heren[c|s]ia|hereda|herada|generalizac[ií][oó]n|subclase|superclase|extiende)\\b.*")) {
            return "GENERALIZACION";
        }
        if (p.matches(".*\\b(composic[ií][oó]n|compone|compuesta)\\b.*")) {
            return "COMPOSICION";
        }
        if (p.matches(".*\\b(agregac[ií][oó]n|agrega\\s+a|agregada)\\b.*")) {
            return "AGREGACION";
        }
        if (p.matches(".*\\b(dependencia|depende|usa|utiliza)\\b.*")) {
            return "DEPENDENCIA";
        }
        return "ASOCIACION";
    }

    private ObjectNode relacion(UUID origen, UUID destino, String prompt) {
        ObjectNode datos = json.createObjectNode();
        String tipo = inferirTipoRelacion(prompt);
        datos.put("origen", origen.toString());
        datos.put("destino", destino.toString());
        datos.put("tipo", tipo);

        if ("GENERALIZACION".equals(tipo) || "DEPENDENCIA".equals(tipo)) {
            datos.put("direccion", "ORIGEN_A_DESTINO");
        } else {
            datos.put("direccion", "SIN_DIRECCION");
            Cardinalidad cardinalidad = cardinalidad(prompt);
            datos.put("minimoOrigen", cardinalidad.minimoOrigen());
            if (cardinalidad.maximoOrigen() == null) datos.putNull("maximoOrigen");
            else datos.put("maximoOrigen", cardinalidad.maximoOrigen());
            datos.put("minimoDestino", cardinalidad.minimoDestino());
            if (cardinalidad.maximoDestino() == null) datos.putNull("maximoDestino");
            else datos.put("maximoDestino", cardinalidad.maximoDestino());
        }
        return datos;
    }

    private boolean contieneRelacion(String prompt) {
        String normalizado = prompt.toLowerCase(Locale.ROOT);
        return normalizado.contains("relación")
                || normalizado.contains("relacion")
                || normalizado.contains("asociación")
                || normalizado.contains("asociacion")
                || normalizado.contains("herencia")
                || normalizado.contains("hereda")
                || normalizado.contains("herada")
                || normalizado.contains("generalización")
                || normalizado.contains("generalizacion")
                || normalizado.contains("subclase")
                || normalizado.contains("superclase")
                || normalizado.contains("extiende")
                || normalizado.contains("composición")
                || normalizado.contains("composicion")
                || normalizado.contains("compone")
                || normalizado.contains("agregación")
                || normalizado.contains("agregacion")
                || normalizado.contains("dependencia")
                || normalizado.contains("depende")
                || normalizado.contains("conecta")
                || normalizado.contains("conectar")
                || normalizado.contains("vincula")
                || normalizado.contains("vincular")
                || normalizado.contains("une")
                || normalizado.contains("unir")
                || normalizado.contains("enlaza")
                || normalizado.contains("enlazar");
    }

    public static String normalizarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) return "String";
        String t = tipo.trim();
        String lower = t.toLowerCase(Locale.ROOT);

        if (lower.contains("(")) {
            lower = lower.substring(0, lower.indexOf('(')).trim();
        }

        return switch (lower) {
            case "int", "integer", "entero", "smallint", "tinyint" -> "Integer";
            case "string", "str", "texto", "cadena", "varchar", "nvarchar", "text", "char" ->
                    "String";
            case "bool", "boolean", "booleano", "bit" -> "Boolean";
            case "float" -> "Float";
            case "double", "decimal", "numeric", "real", "money" -> "Double";
            case "long", "bigint", "serial", "bigserial" -> "Long";
            case "date", "fecha" -> "LocalDate";
            case "datetime", "fechahora", "timestamp" -> "LocalDateTime";
            case "uuid", "guid" -> "UUID";
            case "void" -> "void";
            default -> {
                if (t.length() > 1 && Character.isLowerCase(t.charAt(0))) {
                    yield Character.toUpperCase(t.charAt(0)) + t.substring(1);
                }
                yield t;
            }
        };
    }

    public static boolean esTipoConocido(String tipo) {
        if (tipo == null || tipo.isBlank()) return false;
        String lower = tipo.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("(")) {
            lower = lower.substring(0, lower.indexOf('(')).trim();
        }
        return switch (lower) {
            case "int", "integer", "entero", "smallint", "tinyint",
                 "string", "str", "texto", "cadena", "varchar", "nvarchar", "text", "char",
                 "bool", "boolean", "booleano", "bit",
                 "float",
                 "double", "decimal", "numeric", "real", "money",
                 "long", "bigint", "serial", "bigserial",
                 "date", "fecha",
                 "datetime", "fechahora", "timestamp",
                 "uuid", "guid" -> true;
            default -> false;
        };
    }

    private Cardinalidad cardinalidad(String prompt) {
        Matcher matcher = CARDINALIDAD.matcher(prompt);
        if (!matcher.find()) return new Cardinalidad(1, 1, 1, 1);

        Extremo primero = extremo(matcher.group(1));
        Extremo segundo = extremo(matcher.group(2));

        if (matcher.find()) {
            Extremo tercero = extremo(matcher.group(1));
            Extremo cuarto = extremo(matcher.group(2));
            return new Cardinalidad(
                    primero.minimo(), segundo.maximo() != null ? segundo.maximo() : segundo.minimo(),
                    tercero.minimo(), cuarto.maximo());
        }

        return new Cardinalidad(
                primero.minimo(), primero.maximo(), segundo.minimo(), segundo.maximo());
    }

    private Extremo extremo(String valor) {
        if (valor == null) return new Extremo(1, 1);
        String v = valor.trim().toLowerCase(Locale.ROOT);
        if (v.equals("*") || v.startsWith("much") || v.startsWith("vari") || v.equals("n"))
            return new Extremo(0, null);
        if (v.equals("uno") || v.equals("un") || v.equals("1"))
            return new Extremo(1, 1);
        if (v.equals("cero") || v.equals("0"))
            return new Extremo(0, 0);
        try {
            int numero = Integer.parseInt(v);
            return new Extremo(numero, numero);
        } catch (NumberFormatException e) {
            return new Extremo(1, 1);
        }
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
