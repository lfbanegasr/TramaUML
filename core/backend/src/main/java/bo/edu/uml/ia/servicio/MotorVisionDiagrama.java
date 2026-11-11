package bo.edu.uml.ia.servicio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Interpretacion;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.OperacionGenerada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class MotorVisionDiagrama {
    private static final Logger LOG = LoggerFactory.getLogger(MotorVisionDiagrama.class);
    private final MotorAsistenciaInteligente motorTexto;
    private final ProveedorIa proveedor;
    private final ObjectMapper json;

    public MotorVisionDiagrama(
            MotorAsistenciaInteligente motorTexto,
            ProveedorIa proveedor,
            ObjectMapper json) {
        this.motorTexto = motorTexto;
        this.proveedor = proveedor;
        this.json = json;
    }

    public Interpretacion interpretar(
            byte[] imagen, String tipoContenido, String textoDetectado, Contexto contexto) {
        Contexto contextoEfectivo = contexto;
        if (textoDetectado != null
                && (textoDetectado.contains("[MODO: NUEVO]")
                        || textoDetectado.contains("[MODO: AISLADO]"))) {
            contextoEfectivo = null;
        }

        String texto = null;
        if (imagen != null && imagen.length > 0) {
            texto =
                    proveedor
                            .reconocerDiagrama(imagen, tipoContenido, textoDetectado)
                            .orElse(null);
        }

        if (texto == null || texto.isBlank()) {
            texto = textoDetectado;
        }

        if (texto == null || texto.isBlank()) {
            throw new ApiException(
                    503,
                    "La imagen se conservó, pero no hay un proveedor OCR o multimodal disponible y"
                            + " no se proporcionó texto guía.");
        }

        // Si la respuesta es una estructura JSON de diagrama, procesar con el motor estructurado
        if (esJsonDiagrama(texto)) {
            try {
                return interpretarEstructuraVisual(texto, contextoEfectivo);
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                LOG.warn(
                        "Fallo al procesar estructura JSON de visión, delegando a motor de texto:"
                            + " {}",
                        e.getMessage());
            }
        }

        return motorTexto.interpretar(texto, contextoEfectivo);
    }

    public Interpretacion interpretarEstructuraVisual(String jsonStr, Contexto contexto) {
        JsonNode raiz;
        try {
            raiz = json.readTree(jsonStr);
        } catch (Exception e) {
            throw new ApiException(422, "La estructura visual no es un JSON válido.");
        }

        JsonNode clasesNode = raiz.path("clases");
        if (!clasesNode.isArray() || clasesNode.isEmpty()) {
            clasesNode = raiz.path("classes");
        }
        if (!clasesNode.isArray() || clasesNode.isEmpty()) {
            throw new ApiException(422, "No se identificaron clases en la imagen analizada.");
        }

        List<OperacionGenerada> operaciones = new ArrayList<>();
        int secuencia = 1;

        Map<String, UUID> mapaClases = new LinkedHashMap<>();
        Map<String, UUID> mapaOperacionesClase = new LinkedHashMap<>();
        List<CandidatoFk> candidatosFk = new ArrayList<>();
        Set<String> relacionesProcesadas = new HashSet<>();
        Set<String> nombresExistentes = new HashSet<>();
        if (contexto != null && contexto.nombresClases() != null) {
            contexto.nombresClases().forEach(n -> nombresExistentes.add(n.toLowerCase(Locale.ROOT)));
        }

        int totalClasesNuevas = 0;
        int totalClasesActualizadas = 0;
        int totalAtributos = 0;
        int totalOperaciones = 0;
        int index = 0;

        for (JsonNode clase : clasesNode) {
            String rawNombreClase =
                    clase.path("nombre").asText(clase.path("name").asText("")).trim();
            if (rawNombreClase.isBlank()) continue;
            String nombreClase = sanitizarNombreClase(rawNombreClase);
            if (nombreClase.isBlank()) continue;
            String lower = nombreClase.toLowerCase(Locale.ROOT);
            String rawLower = rawNombreClase.toLowerCase(Locale.ROOT);

            Optional<ElementoContexto> existente =
                    buscarContexto(contexto, "CLASE", nombreClase, null);
            if (existente.isEmpty()) {
                existente = buscarContexto(contexto, "CLASE", rawNombreClase, null);
            }
            boolean yaExiste = existente.isPresent() || nombresExistentes.contains(lower) || nombresExistentes.contains(rawLower);

            UUID idEntidadClase;
            UUID idOperacionClase = null;

            if (yaExiste) {
                // Modo FUSIÓN / ACTUALIZAR: la clase ya existe en el modelo actual
                idEntidadClase = existente.map(ElementoContexto::id).orElseGet(UUID::randomUUID);
                mapaClases.put(lower, idEntidadClase);
                mapaClases.put(rawLower, idEntidadClase);
                totalClasesActualizadas++;
            } else {
                // Modo CREAR: clase nueva
                idEntidadClase = UUID.randomUUID();
                idOperacionClase = UUID.randomUUID();
                mapaClases.put(lower, idEntidadClase);
                mapaClases.put(rawLower, idEntidadClase);
                mapaOperacionesClase.put(lower, idOperacionClase);
                mapaOperacionesClase.put(rawLower, idOperacionClase);

                int posX = clase.path("posicionX").asInt(clase.path("x").asInt(0));
                int posY = clase.path("posicionY").asInt(clase.path("y").asInt(0));
                int baseCantidad = contexto != null ? contexto.cantidadClases() : 0;
                if (posX <= 0 || posY <= 0) {
                    posX = coordenadaX(baseCantidad + index);
                    posY = coordenadaY(baseCantidad + index);
                } else {
                    posX = Math.max(50, Math.min(posX, 2400));
                    posY = Math.max(50, Math.min(posY, 2400));
                }

                ObjectNode datosClase = json.createObjectNode();
                datosClase.put("nombre", nombreClase);
                datosClase.put("x", posX);
                datosClase.put("y", posY);
                datosClase.put("visibilidad", "PUBLICO");
                datosClase.put("esAbstracto", clase.path("esAbstracto").asBoolean(false));
                datosClase.put("esPersistente", clase.path("esPersistente").asBoolean(false));
                String ester = clase.path("estereotipo").asText(null);
                if (ester != null && !ester.isBlank()) {
                    datosClase.put("estereotipo", ester.replaceAll("[<>]", "").trim());
                }

                operaciones.add(
                        crearOperacion(
                                idOperacionClase,
                                secuencia++,
                                "CREAR",
                                "CLASE",
                                idEntidadClase,
                                null,
                                datosClase,
                                true,
                                null,
                                "Crear clase " + nombreClase + " detectada en la imagen",
                                List.of()));
                totalClasesNuevas++;
            }

            // Procesar atributos de la clase
            JsonNode atributos = clase.path("atributos");
            if (!atributos.isArray()) atributos = clase.path("attributes");
            if (atributos.isArray()) {
                for (JsonNode attr : atributos) {
                    String rawNombre =
                            attr.path("nombre").asText(attr.path("name").asText("")).trim();
                    if (rawNombre.isBlank()) continue;

                    boolean tieneMarcaPk = rawNombre.toUpperCase(Locale.ROOT).contains("PK")
                            || rawNombre.toLowerCase(Locale.ROOT).startsWith("pk_")
                            || rawNombre.toLowerCase(Locale.ROOT).endsWith("_pk");
                    boolean tieneMarcaFk = rawNombre.toUpperCase(Locale.ROOT).contains("FK")
                            || rawNombre.toLowerCase(Locale.ROOT).startsWith("fk_")
                            || rawNombre.toLowerCase(Locale.ROOT).endsWith("_fk");

                    // Limpiar sufijos o prefijos como '(PK)', '[PK]', '(FK)', 'pk_', 'fk_'
                    String nombreAttr = rawNombre.replaceAll("(?i)[\\[\\(]?(pk|fk)[\\]\\)]?", "")
                            .replaceAll("(?i)^(pk_|fk_)", "")
                            .replaceAll("(?i)(_pk|_fk)$", "")
                            .trim();
                    if (nombreAttr.isBlank()) nombreAttr = rawNombre;

                    if (yaExiste
                            && buscarContexto(contexto, "ATRIBUTO", nombreAttr, idEntidadClase)
                                    .isPresent()) {
                        continue;
                    }

                    if (tieneMarcaFk
                            || nombreAttr.toLowerCase(Locale.ROOT).endsWith("_id")
                            || nombreAttr.toLowerCase(Locale.ROOT).startsWith("id_")) {
                        candidatosFk.add(new CandidatoFk(lower, nombreAttr, idEntidadClase));
                    }

                    String tipoAttr = attr.path("tipo").asText(attr.path("type").asText("")).trim();
                    if (tipoAttr.isBlank()) {
                        tipoAttr = MotorAsistenciaInteligente.inferirTipo(nombreAttr);
                    } else {
                        tipoAttr = MotorAsistenciaInteligente.normalizarTipo(tipoAttr);
                    }

                    String vis =
                            attr.path("visibilidad").asText("PRIVADO").toUpperCase(Locale.ROOT);
                    if (!Set.of("PUBLICO", "PRIVADO", "PROTEGIDO", "PAQUETE").contains(vis)) {
                        vis = "PRIVADO";
                    }

                    boolean esId =
                            attr.path("esIdentificador")
                                    .asBoolean(
                                            tieneMarcaPk
                                                    || nombreAttr.equalsIgnoreCase("id")
                                                    || nombreAttr.equalsIgnoreCase("pk"));

                    ObjectNode datosAttr = json.createObjectNode();
                    datosAttr.put("idClase", idEntidadClase.toString());
                    datosAttr.put("nombre", nombreAttr);
                    datosAttr.put("tipoUml", tipoAttr);
                    datosAttr.put("visibilidad", vis);
                    datosAttr.put("minimo", attr.path("minimo").asInt(1));
                    if (attr.has("maximo") && attr.get("maximo").isNull())
                        datosAttr.putNull("maximo");
                    else datosAttr.put("maximo", attr.path("maximo").asInt(1));
                    datosAttr.put("esIdentificador", esId);
                    datosAttr.put("esEstatico", attr.path("esEstatico").asBoolean(false));
                    datosAttr.put("esUnico", attr.path("esUnico").asBoolean(false));
                    datosAttr.put("esOrdenado", attr.path("esOrdenado").asBoolean(false));
                    datosAttr.put("coleccionUnica", attr.path("coleccionUnica").asBoolean(true));
                    for (String key :
                            List.of(
                                    "minimo",
                                    "maximo",
                                    "esIdentificador",
                                    "esEstatico",
                                    "esUnico",
                                    "esOrdenado",
                                    "coleccionUnica",
                                    "valorPredeterminado",
                                    "restricciones"))
                        if (attr.has(key)) datosAttr.set(key, attr.get(key));

                    operaciones.add(
                            crearOperacion(
                                    UUID.randomUUID(),
                                    secuencia++,
                                    "CREAR",
                                    "ATRIBUTO",
                                    UUID.randomUUID(),
                                    null,
                                    datosAttr,
                                    true,
                                    null,
                                    "Crear atributo "
                                            + nombreAttr
                                            + ": "
                                            + tipoAttr
                                            + " en "
                                            + nombreClase,
                                    idOperacionClase != null
                                            ? List.of(idOperacionClase)
                                            : List.of()));
                    totalAtributos++;
                }
            }

            // Procesar operaciones/métodos de la clase
            JsonNode opers = clase.path("operaciones");
            if (!opers.isArray()) opers = clase.path("operations");
            if (!opers.isArray()) opers = clase.path("metodos");
            if (opers.isArray()) {
                for (JsonNode op : opers) {
                    String nombreOp = op.path("nombre").asText(op.path("name").asText("")).trim();
                    if (nombreOp.isBlank()) continue;

                    if (yaExiste
                            && buscarContexto(contexto, "OPERACION", nombreOp, idEntidadClase)
                                    .isPresent()) {
                        continue;
                    }

                    String retorno =
                            op.path("tipoRetorno")
                                    .asText(op.path("returnType").asText("void"))
                                    .trim();
                    if (retorno.isBlank()) retorno = "void";
                    else retorno = MotorAsistenciaInteligente.normalizarTipo(retorno);

                    String vis = op.path("visibilidad").asText("PUBLICO").toUpperCase(Locale.ROOT);
                    if (!Set.of("PUBLICO", "PRIVADO", "PROTEGIDO", "PAQUETE").contains(vis)) {
                        vis = "PUBLICO";
                    }

                    ObjectNode datosOp = json.createObjectNode();
                    datosOp.put("idClase", idEntidadClase.toString());
                    datosOp.put("nombre", nombreOp);
                    datosOp.put("tipoRetorno", retorno);
                    datosOp.put("visibilidad", vis);
                    datosOp.put("esEstatico", op.path("esEstatico").asBoolean(false));
                    datosOp.put("esAbstracto", op.path("esAbstracto").asBoolean(false));
                    ArrayNode params = datosOp.putArray("parametros");
                    for (JsonNode p : op.path("parametros")) {
                        ObjectNode param =
                                params.addObject().put("id", UUID.randomUUID().toString());
                        param.put("nombre", p.path("nombre").asText());
                        param.put(
                                "tipoUml",
                                p.path("tipoUml").asText(p.path("tipo").asText("String")));
                        param.put("direccion", p.path("direccion").asText("ENTRADA"));
                        param.put("esObligatorio", p.path("esObligatorio").asBoolean(true));
                        if (p.has("valorPredeterminado"))
                            param.set("valorPredeterminado", p.get("valorPredeterminado"));
                    }

                    operaciones.add(
                            crearOperacion(
                                    UUID.randomUUID(),
                                    secuencia++,
                                    "CREAR",
                                    "OPERACION",
                                    UUID.randomUUID(),
                                    null,
                                    datosOp,
                                    true,
                                    null,
                                    "Crear operación "
                                            + nombreOp
                                            + "(): "
                                            + retorno
                                            + " en "
                                            + nombreClase,
                                    idOperacionClase != null
                                            ? List.of(idOperacionClase)
                                            : List.of()));
                    totalOperaciones++;
                }
            }

            index++;
        }

        // Procesar relaciones
        JsonNode relacionesNode = raiz.path("relaciones");
        if (!relacionesNode.isArray()) relacionesNode = raiz.path("relationships");
        int totalRelaciones = 0;

        if (relacionesNode.isArray()) {
            for (JsonNode rel : relacionesNode) {
                String origenNom =
                        rel.path("origen")
                                .asText(rel.path("source").asText(""))
                                .trim()
                                .toLowerCase(Locale.ROOT);
                String destinoNom =
                        rel.path("destino")
                                .asText(rel.path("target").asText(""))
                                .trim()
                                .toLowerCase(Locale.ROOT);
                if (origenNom.isBlank() || destinoNom.isBlank()) continue;

                UUID idOrigen = mapaClases.get(origenNom);
                UUID idDestino = mapaClases.get(destinoNom);
                if (idOrigen == null || idDestino == null) {
                    LOG.debug(
                            "Relación descartada por extremos no resueltos: {} -> {}",
                            origenNom,
                            destinoNom);
                    continue;
                }

                String tipoRelacion =
                        normalizarTipoRelacion(
                                rel.path("tipo").asText(rel.path("type").asText("ASOCIACION")));
                String multOrigen =
                        rel.path("multiplicidadOrigen")
                                .asText(rel.path("sourceMultiplicity").asText("1"));
                String multDestino =
                        rel.path("multiplicidadDestino")
                                .asText(rel.path("targetMultiplicity").asText("1"));

                ObjectNode datosRel = json.createObjectNode();
                datosRel.put("origen", idOrigen.toString());
                datosRel.put("destino", idDestino.toString());
                datosRel.put("tipo", tipoRelacion);
                for (String role : List.of("rolOrigen", "rolDestino", "nombre"))
                    if (rel.has(role)) datosRel.set(role, rel.get(role));

                if ("GENERALIZACION".equals(tipoRelacion) || "DEPENDENCIA".equals(tipoRelacion)) {
                    datosRel.put("direccion", "ORIGEN_A_DESTINO");
                } else {
                    String direccion =
                            rel.path("direccion").asText("SIN_DIRECCION").toUpperCase(Locale.ROOT);
                    if (!Set.of(
                                    "SIN_DIRECCION",
                                    "ORIGEN_A_DESTINO",
                                    "DESTINO_A_ORIGEN",
                                    "BIDIRECCIONAL")
                            .contains(direccion)) {
                        direccion = "SIN_DIRECCION";
                    }
                    datosRel.put("direccion", direccion);
                    aplicarCardinalidad(datosRel, multOrigen, multDestino);
                }

                List<UUID> dependencias = new ArrayList<>();
                UUID depOrigen = mapaOperacionesClase.get(origenNom);
                if (depOrigen != null) dependencias.add(depOrigen);
                UUID depDestino = mapaOperacionesClase.get(destinoNom);
                if (depDestino != null) dependencias.add(depDestino);

                operaciones.add(
                        crearOperacion(
                                UUID.randomUUID(),
                                secuencia++,
                                "CREAR",
                                "RELACION",
                                UUID.randomUUID(),
                                null,
                                datosRel,
                                true,
                                null,
                                "Crear relación de tipo "
                                        + tipoRelacion
                                        + " entre "
                                        + origenNom
                                        + " y "
                                        + destinoNom,
                                dependencias));
                totalRelaciones++;
                String oSan =
                        sanitizarNombreClase(origenNom).toLowerCase(Locale.ROOT).replace("_", "");
                String dSan =
                        sanitizarNombreClase(destinoNom).toLowerCase(Locale.ROOT).replace("_", "");
                relacionesProcesadas.add(origenNom + "->" + destinoNom);
                relacionesProcesadas.add(destinoNom + "->" + origenNom);
                relacionesProcesadas.add(oSan + "->" + dSan);
                relacionesProcesadas.add(dSan + "->" + oSan);
            }
        }

        // Detección de relaciones implícitas por claves foráneas (FK) en bocetos de papel y modelos ER
        for (CandidatoFk fk : candidatosFk) {
            String destinoNom = resolverDestinoFk(fk.nombreAtributo(), mapaClases.keySet());
            if (destinoNom != null && !destinoNom.equalsIgnoreCase(fk.claseOrigen())) {
                String origSan = fk.claseOrigen().toLowerCase(Locale.ROOT).replace("_", "");
                String destSan = destinoNom.toLowerCase(Locale.ROOT).replace("_", "");
                String claveRel = origSan + "->" + destSan;
                String claveRelInv = destSan + "->" + origSan;
                if (!relacionesProcesadas.contains(claveRel)
                        && !relacionesProcesadas.contains(claveRelInv)
                        && !relacionesProcesadas.contains(fk.claseOrigen() + "->" + destinoNom)
                        && !relacionesProcesadas.contains(destinoNom + "->" + fk.claseOrigen())) {
                    UUID idOrigen = fk.idOrigen();
                    UUID idDestino = mapaClases.get(destinoNom);
                    if (idOrigen != null && idDestino != null) {
                        ObjectNode datosRel = json.createObjectNode();
                        datosRel.put("origen", idOrigen.toString());
                        datosRel.put("destino", idDestino.toString());
                        datosRel.put("tipo", "ASOCIACION");
                        datosRel.put("direccion", "SIN_DIRECCION");
                        aplicarCardinalidad(datosRel, "*", "1");

                        List<UUID> dependencias = new ArrayList<>();
                        UUID depOrigen = mapaOperacionesClase.get(fk.claseOrigen());
                        if (depOrigen != null) dependencias.add(depOrigen);
                        UUID depDestino = mapaOperacionesClase.get(destinoNom);
                        if (depDestino != null) dependencias.add(depDestino);

                        operaciones.add(
                                crearOperacion(
                                        UUID.randomUUID(),
                                        secuencia++,
                                        "CREAR",
                                        "RELACION",
                                        UUID.randomUUID(),
                                        null,
                                        datosRel,
                                        true,
                                        null,
                                        "Crear relación implícita por FK ("
                                                + fk.nombreAtributo()
                                                + ") entre "
                                                + fk.claseOrigen()
                                                + " y "
                                                + destinoNom,
                                        dependencias));
                        totalRelaciones++;
                        relacionesProcesadas.add(claveRel);
                        relacionesProcesadas.add(claveRelInv);
                        relacionesProcesadas.add(fk.claseOrigen() + "->" + destinoNom);
                        relacionesProcesadas.add(destinoNom + "->" + fk.claseOrigen());
                    }
                }
            }
        }

        String resumen = raiz.path("resumen").asText("").trim();
        if (resumen.isBlank()) {
            resumen =
                    "Se interpretaron "
                            + (totalClasesNuevas + totalClasesActualizadas)
                            + " clase(s) ("
                            + totalClasesNuevas
                            + " nueva(s), "
                            + totalClasesActualizadas
                            + " actualizada(s)), "
                            + totalAtributos
                            + " atributo(s), "
                            + totalOperaciones
                            + " operación(es) y "
                            + totalRelaciones
                            + " relación(es) desde la imagen.";
        }

        return new Interpretacion(resumen, new BigDecimal("0.9500"), List.copyOf(operaciones));
    }

    public boolean esJsonDiagrama(String texto) {
        if (texto == null) return false;
        String t = texto.trim();
        if (!t.startsWith("{")) return false;
        return t.contains("\"clases\"") || t.contains("\"classes\"");
    }

    private Optional<ElementoContexto> buscarContexto(
            Contexto contexto, String tipo, String nombre, UUID contenedor) {
        if (contexto == null || contexto.elementos() == null) return Optional.empty();
        return contexto.elementos().stream()
                .filter(e -> tipo.equals(e.tipo()))
                .filter(e -> e.nombre() != null && e.nombre().equalsIgnoreCase(nombre))
                .filter(e -> contenedor == null || contenedor.equals(e.idContenedor()))
                .findFirst();
    }

    private void aplicarCardinalidad(ObjectNode datosRel, String origen, String destino) {
        CardinalidadExtremo extOrigen = parsearCardinalidad(origen, 1, 1);
        CardinalidadExtremo extDestino = parsearCardinalidad(destino, 0, null);

        datosRel.put("minimoOrigen", extOrigen.minimo());
        if (extOrigen.maximo() == null) datosRel.putNull("maximoOrigen");
        else datosRel.put("maximoOrigen", extOrigen.maximo());

        datosRel.put("minimoDestino", extDestino.minimo());
        if (extDestino.maximo() == null) datosRel.putNull("maximoDestino");
        else datosRel.put("maximoDestino", extDestino.maximo());
    }

    private CardinalidadExtremo parsearCardinalidad(String valor, int defMin, Integer defMax) {
        if (valor == null || valor.isBlank()) return new CardinalidadExtremo(defMin, defMax);
        String v = valor.trim().toLowerCase(Locale.ROOT);
        if (v.equals("*")
                || v.equals("n")
                || v.equals("m")
                || v.startsWith("much")
                || v.startsWith("vari")) {
            return new CardinalidadExtremo(0, null);
        }
        if (v.equals("1") || v.equals("uno") || v.equals("un")) {
            return new CardinalidadExtremo(1, 1);
        }
        if (v.equals("0..1") || v.equals("0-1") || v.equals("0:1")) {
            return new CardinalidadExtremo(0, 1);
        }
        if (v.equals("1..*")
                || v.equals("1..n")
                || v.equals("1-*")
                || v.equals("1:n")
                || v.equals("1:*")) {
            return new CardinalidadExtremo(1, null);
        }
        if (v.equals("0..*")
                || v.equals("0..n")
                || v.equals("0-*")
                || v.equals("0:n")
                || v.equals("0:*")) {
            return new CardinalidadExtremo(0, null);
        }
        if (v.matches("[0-9]+\\.\\.[0-9]+")) {
            String[] parts = v.split("\\.\\.");
            int min = Integer.parseInt(parts[0]), max = Integer.parseInt(parts[1]);
            if (max < min)
                throw new ApiException(422, "Multiplicidad inválida detectada: " + valor);
            return new CardinalidadExtremo(min, max);
        }
        try {
            int num = Integer.parseInt(v);
            if (num < 0) throw new ApiException(422, "Multiplicidad negativa detectada.");
            return new CardinalidadExtremo(num, num);
        } catch (NumberFormatException e) {
            throw new ApiException(
                    422,
                    "Multiplicidad no reconocida: "
                            + valor
                            + ". Revisar la propuesta sin normalizar límites.");
        }
    }

    private String normalizarTipoRelacion(String tipo) {
        if (tipo == null || tipo.isBlank()) return "ASOCIACION";
        String t = tipo.trim().toUpperCase(Locale.ROOT);
        if (t.contains("HEREN")
                || t.contains("GENERALIZ")
                || t.contains("SUBCLASE")
                || t.contains("EXTIEN")) {
            return "GENERALIZACION";
        }
        if (t.contains("COMPOSI")) {
            return "COMPOSICION";
        }
        if (t.contains("AGREGA")) {
            return "AGREGACION";
        }
        if (t.contains("DEPEN")) {
            return "DEPENDENCIA";
        }
        if (t.contains("ASOCIA") || t.equals("ASSOCIATION")) return "ASOCIACION";
        throw new ApiException(422, "Relación fuera del subconjunto UML soportado: " + tipo);
    }

    private int coordenadaX(int indice) {
        return 60 + (indice % 4) * 300;
    }

    private int coordenadaY(int indice) {
        return 60 + (indice / 4) * 230;
    }

    private OperacionGenerada crearOperacion(
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
        ArrayNode mensajes = json.createArrayNode();
        mensajes.addObject().put("tipo", "JUSTIFICACION").put("mensaje", justificacion);
        if (error != null) {
            mensajes.addObject().put("tipo", "ERROR").put("mensaje", error);
        }
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
                mensajes,
                justificacion,
                dependencias);
    }

    private String resolverDestinoFk(String nombreAttr, Set<String> clasesDisponibles) {
        String n = nombreAttr.toLowerCase(Locale.ROOT).trim();
        n = n.replaceAll("(?i)[\\[\\(]?(pk|fk)[\\]\\)]?", "")
                .replaceAll("(?i)^(pk_|fk_)", "")
                .replaceAll("(?i)(_pk|_fk)$", "")
                .trim();
        for (String clase : clasesDisponibles) {
            String c = clase.toLowerCase(Locale.ROOT);
            if (n.equals(c + "_id") || n.equals("id_" + c) || n.equals(c + "id")) {
                return clase;
            }
            String raiz = n.replace("id_", "").replace("_id", "").trim();
            if (!raiz.isBlank() && (c.startsWith(raiz) || c.contains(raiz))) {
                return clase;
            }
        }
        return null;
    }

    private String sanitizarNombreClase(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        String upper = s.toUpperCase(Locale.ROOT);
        // Descartar encabezados o títulos decorativos de pizarra
        if (upper.contains("DIAGRAMA")
                || upper.contains("MODELO CONCEPTUAL")
                || upper.startsWith("SISTEMA DE")
                || s.length() > 50) {
            return "";
        }
        // Quitar diacríticos/acentos: CATEGORÍA -> CATEGORIA
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // Si viene en MAYUSCULAS_CON_GUION como DETALLE_PEDIDO -> DetallePedido
        if (s.contains("_")) {
            String[] partes = s.split("_+");
            StringBuilder sb = new StringBuilder();
            for (String p : partes) {
                if (p.isBlank()) continue;
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) {
                    sb.append(p.substring(1).toLowerCase(Locale.ROOT));
                }
            }
            return sb.toString();
        }
        // Si viene en MAYUSCULAS simples como CLIENTE -> Cliente
        if (s.length() > 1 && s.equals(s.toUpperCase(Locale.ROOT))) {
            return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
        }
        return s;
    }

    private record CandidatoFk(String claseOrigen, String nombreAtributo, UUID idOrigen) {}

    private record CardinalidadExtremo(int minimo, Integer maximo) {}
}
