package bo.edu.uml.historial.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.historial.dto.HistorialDto.*;
import bo.edu.uml.historial.repositorio.HistorialRepositorio;
import bo.edu.uml.historial.repositorio.HistorialRepositorio.Cabecera;
import bo.edu.uml.modelado.servicio.EdicionTransaccional.ModeloConfirmado;
import bo.edu.uml.proyectos.repositorio.ActividadRepositorio;
import bo.edu.uml.validacion.modelo.MotorSemantico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Protocolo completo de restauración (CU20). Dentro de una única transacción: autoriza, bloquea,
 * verifica bloqueadores, reconstruye el modelo desde el snapshot, valida semántica, guarda como
 * nueva versión y publica el evento {@link ModeloConfirmado} después del commit.
 */
@Service
public class RestauradorTransaccional {

    private final HistorialRepositorio historial;
    private final NamedParameterJdbcTemplate jdbc;
    private final ActividadRepositorio actividad;
    private final ApplicationEventPublisher eventos;
    private final ObjectMapper json;
    private final ComparadorVersiones comparador;

    public RestauradorTransaccional(
            HistorialRepositorio historial,
            NamedParameterJdbcTemplate jdbc,
            ActividadRepositorio actividad,
            ApplicationEventPublisher eventos,
            ObjectMapper json,
            ComparadorVersiones comparador) {
        this.historial = historial;
        this.jdbc = jdbc;
        this.actividad = actividad;
        this.eventos = eventos;
        this.json = json;
        this.comparador = comparador;
    }

    /**
     * Genera una vista previa sin escribir nada. Devuelve bloqueadores activos y el número estimado
     * de elementos que cambiarían. Valida el snapshot origen.
     */
    @Transactional(readOnly = true)
    public VistaPrevia previsualizacion(Identidad actor, UUID modelo, UUID idVersionOrigen) {
        Cabecera c = historial.cabecera(modelo);
        autorizarRestauracion(actor, c);
        historial.leerResumen(modelo, idVersionOrigen); // 404 si no pertenece al modelo
        List<String> bloqueadores = new ArrayList<>();
        int sesiones = historial.sesionesEditoras(modelo, actor != null ? actor.id() : null);
        int conflictos = historial.conflictosAbiertos(modelo);
        if (sesiones > 0)
            bloqueadores.add(
                    sesiones
                            + " sesión(es) colaborativa(s) activa(s) con rol EDITOR. Ciérralas"
                            + " antes de restaurar.");
        if (conflictos > 0)
            bloqueadores.add(
                    conflictos
                            + " conflicto(s) de edición ABIERTO(s). Resuélvelos o descártalos"
                            + " antes de restaurar.");
        int estimado = 0;
        // Valida el snapshot origen (lanza 422 si está dañado)
        JsonNode snap = historial.snapshot(modelo, idVersionOrigen);
        for (JsonNode r : snap.path("relaciones")) {
            if (!Set.of("ASOCIACION", "AGREGACION", "COMPOSICION", "GENERALIZACION", "DEPENDENCIA")
                    .contains(r.path("tipo_relacion").asText()))
                bloqueadores.add(
                        "Tipo histórico legible pero fuera del subconjunto restaurable: "
                                + r.path("tipo_relacion").asText());
            if ("COMPOSICION".equals(r.path("tipo_relacion").asText())
                    && (!r.path("maximo_origen").isIntegralNumber()
                            || r.path("maximo_origen").asLong() > 1))
                bloqueadores.add(
                        "La composición histórica exige revisar el máximo del extremo conjunto"
                            + " antes de restaurar; la instantánea original no se modifica.");
        }
        if (bloqueadores.isEmpty()) {
            estimado = contarElementos(snap);
        }
        return new VistaPrevia(c.revision(), c.version(), bloqueadores, estimado);
    }

    /** Restauración completa (CU20). */
    @Transactional
    public ResultadoRestauracion restaurar(
            Identidad actor, UUID modelo, SolicitarRestauracion solicitud) {
        if (solicitud.confirmar() == null || !solicitud.confirmar())
            throw new ApiException(400, "El campo 'confirmar' debe ser true para restaurar.");
        if (solicitud.motivo() == null || solicitud.motivo().isBlank())
            throw new ApiException(400, "El motivo de la restauración es obligatorio.");
        if (solicitud.idSolicitudRestauracion() == null)
            throw new ApiException(400, "El idSolicitudRestauracion es obligatorio.");
        if (solicitud.idVersionOrigen() == null)
            throw new ApiException(400, "El idVersionOrigen es obligatorio.");
        if (solicitud.revisionEsperada() == null)
            throw new ApiException(400, "La revisionEsperada es obligatoria.");

        // 1. Autorización del actor antes de devolver cualquier repetición o bloquear
        Cabecera c = historial.cabecera(modelo);
        autorizarRestauracion(actor, c);

        // 2. Idempotencia pre-bloqueo limitada por id_modelo e id_cambio_cliente
        Optional<ResultadoRestauracion> repetida =
                historial.repeticion(modelo, solicitud.idSolicitudRestauracion());
        if (repetida.isPresent()) return repetida.get();

        // 3. Bloqueo exclusivo de fila de modelo
        long revisionActual = historial.bloquearRevision(modelo);
        c = historial.cabecera(modelo); // releer tras bloqueo

        // 4. Revalidar repetición tras adquirir el bloqueo (evita duplicados concurrentes)
        Optional<ResultadoRestauracion> repetidaPost =
                historial.repeticion(modelo, solicitud.idSolicitudRestauracion());
        if (repetidaPost.isPresent()) return repetidaPost.get();

        // 5. Validar revisión esperada
        if (revisionActual != solicitud.revisionEsperada())
            throw new ApiException(
                    409, "El modelo cambió durante la preparación. Recarga e intenta de nuevo.");

        // 6. Bloqueadores
        int sesiones = historial.sesionesEditoras(modelo, actor != null ? actor.id() : null);
        int conflictos = historial.conflictosAbiertos(modelo);
        if (sesiones > 0)
            throw new ApiException(
                    409,
                    "Hay "
                            + sesiones
                            + " sesión(es) colaborativa(s) activa(s). Ciérralas antes de"
                            + " restaurar.");
        if (conflictos > 0)
            throw new ApiException(
                    409,
                    "Hay "
                            + conflictos
                            + " conflicto(s) de edición abiertos. Resuélvelos antes de restaurar.");

        // 7. Snapshot de origen validado (ValidadorSnapshot verifica formato, integridad
        // referencial y SHA-256)
        JsonNode snapOrigen = historial.snapshot(modelo, solicitud.idVersionOrigen());
        JsonNode snapActual = leerInstanteaActual(modelo);

        // 8. Iniciar edición bajo bloqueo
        long revisionNueva = iniciarEdicion(modelo, revisionActual, actor.id());

        // 9. Reconstruir desde snapshot (orden seguro)
        reconstruir(modelo, actor.id(), snapActual, snapOrigen);

        // 10. Validación semántica
        JsonNode snapCandidato = leerInstanteaActual(modelo);
        JsonNode reglas = leerReglas();
        validarConsistencia(snapActual, snapCandidato, reglas);

        // 11. Guardar nueva versión con id_version_restaurada directamente en el INSERT
        String resumen =
                "Restauración a versión "
                        + historial.leerResumen(modelo, solicitud.idVersionOrigen()).numeroVersion()
                        + ": "
                        + solicitud.motivo();
        UUID versionNueva =
                guardarVersion(
                        modelo, revisionNueva, actor.id(), resumen, solicitud.idVersionOrigen());

        // 12. Verificar que el snapshot resultante es equivalente al origen en todos los grupos
        // restaurables
        JsonNode snapFinal = leerInstanteaActual(modelo);
        verificarEquivalenciaSnapshot(snapOrigen, snapFinal);

        // 13. Registrar cambio_modelo tipo RESTAURAR
        UUID cambio =
                registrarCambio(
                        modelo,
                        solicitud.idSolicitudRestauracion(),
                        actor.id(),
                        revisionActual,
                        revisionNueva);

        // 14. Actividad
        actividad.registrar(
                c.proyecto(),
                actor.id(),
                "HISTORIAL",
                "RESTAURAR_MODELO",
                "VERSION_MODELO_UML",
                solicitud.idVersionOrigen(),
                modelo,
                versionNueva,
                String.valueOf(revisionActual),
                String.valueOf(revisionNueva),
                actividad.escribir(
                        Map.of(
                                "idSolicitudRestauracion",
                                solicitud.idSolicitudRestauracion(),
                                "idVersionOrigen",
                                solicitud.idVersionOrigen(),
                                "revisionEsperada",
                                solicitud.revisionEsperada(),
                                "motivo",
                                solicitud.motivo(),
                                "idCambio",
                                cambio)));

        // 15. Evento post-commit
        eventos.publishEvent(
                new ModeloConfirmado(
                        modelo,
                        solicitud.idSolicitudRestauracion(),
                        solicitud.idSolicitudRestauracion(),
                        actor.id(),
                        revisionNueva,
                        versionNueva));

        return new ResultadoRestauracion(revisionNueva, versionNueva, false);
    }

    // ─── Reconstrucción desde snapshot ───────────────────────────────────────

    /**
     * Reconstruye el modelo desde el snapshot origen, eliminando en orden inverso y restaurando en
     * orden seguro (modelo → paquetes → clases → atributos/operaciones → parámetros → relaciones →
     * posiciones).
     */
    private void reconstruir(UUID modelo, UUID actor, JsonNode actual, JsonNode origen) {
        // 1. Eliminar en orden inverso lo que ya no existe en el snapshot origen
        eliminarAusentes(
                modelo,
                actor,
                actual.path("relaciones"),
                origen.path("relaciones"),
                "relacion_uml");
        eliminarAusentes(
                modelo,
                actor,
                actual.path("parametros"),
                origen.path("parametros"),
                "parametro_uml");
        eliminarAusentes(
                modelo,
                actor,
                actual.path("operaciones"),
                origen.path("operaciones"),
                "operacion_uml");
        eliminarAusentes(
                modelo, actor, actual.path("atributos"), origen.path("atributos"), "atributo_uml");
        eliminarAusentes(modelo, actor, actual.path("clases"), origen.path("clases"), "clase_uml");
        eliminarAusentes(
                modelo, actor, actual.path("paquetes"), origen.path("paquetes"), "paquete_uml");

        // 2. Restaurar cabecera del modelo (nombre y descripción)
        restaurarModeloCabecera(modelo, origen.path("modelo"));

        // 3. Restaurar/insertar en orden topológico seguro
        restaurarPaquetes(modelo, actor, origen.path("paquetes"));
        restaurarClases(modelo, actor, origen.path("clases"));
        restaurarAtributos(modelo, actor, origen.path("atributos"));
        restaurarOperaciones(modelo, actor, origen.path("operaciones"));
        restaurarParametros(modelo, actor, origen.path("parametros"));
        restaurarRelaciones(modelo, actor, origen.path("relaciones"));
        restaurarPosicionesClases(modelo, origen.path("posiciones_clases"));
        restaurarPosicionesRelaciones(modelo, origen.path("posiciones_relaciones"));
    }

    private void eliminarAusentes(
            UUID modelo, UUID actor, JsonNode actual, JsonNode origen, String tabla) {
        Set<String> idsOrigen = ids(origen, "id");
        for (JsonNode nodo : actual) {
            String id = nodo.path("id").asText(null);
            if (id != null && !idsOrigen.contains(id)) {
                jdbc.update(
                        "update uml_colaborativo."
                                + tabla
                                + " set eliminado_en=clock_timestamp() where id=:id"
                                + " and eliminado_en is null",
                        Map.of("id", UUID.fromString(id)));
            }
        }
    }

    private void restaurarModeloCabecera(UUID modelo, JsonNode nodoModelo) {
        if (nodoModelo != null && nodoModelo.isObject()) {
            String nombre = nodoModelo.path("nombre").asText(null);
            String desc =
                    nodoModelo.hasNonNull("descripcion")
                            ? nodoModelo.path("descripcion").asText()
                            : "";
            if (nombre != null && !nombre.isBlank()) {
                jdbc.update(
                        "update uml_colaborativo.modelo_uml set nombre=:nombre, descripcion=:desc"
                                + " where id=:modelo",
                        Map.of("modelo", modelo, "nombre", nombre, "desc", desc));
            }
        }
    }

    private void restaurarPaquetes(UUID modelo, UUID actor, JsonNode arreglo) {
        List<JsonNode> nodos = new ArrayList<>();
        for (JsonNode n : arreglo) nodos.add(n);
        nodos.sort(
                Comparator.comparing(
                        n ->
                                n.path("id_paquete_padre").isNull()
                                                || n.path("id_paquete_padre").isMissingNode()
                                        ? 0
                                        : 1));
        for (JsonNode n : nodos) {
            UUID id = uuid(n, "id");
            UUID padre = n.hasNonNull("id_paquete_padre") ? uuid(n, "id_paquete_padre") : null;
            String nombre = n.path("nombre").asText();
            String descripcion =
                    n.hasNonNull("descripcion") ? n.path("descripcion").asText() : null;
            jdbc.update(
                    "insert into uml_colaborativo.paquete_uml"
                        + " (id,id_modelo,id_paquete_padre,nombre,descripcion,creado_por,actualizado_por)"
                        + " values(:id,:modelo,:padre,:nombre,:descripcion,:actor,:actor) on"
                        + " conflict(id) do update set id_paquete_padre=excluded.id_paquete_padre,"
                        + " nombre=excluded.nombre, descripcion=excluded.descripcion,"
                        + " actualizado_por=excluded.actualizado_por, eliminado_en=null",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("modelo", modelo)
                            .addValue("padre", padre)
                            .addValue("nombre", nombre)
                            .addValue("descripcion", descripcion)
                            .addValue("actor", actor));
        }
    }

    private void restaurarClases(UUID modelo, UUID actor, JsonNode arreglo) {
        for (JsonNode n : arreglo) {
            UUID id = uuid(n, "id");
            UUID paquete = n.hasNonNull("id_paquete") ? uuid(n, "id_paquete") : null;
            jdbc.update(
                    "insert into uml_colaborativo.clase_uml"
                        + " (id,id_modelo,id_paquete,nombre,descripcion,estereotipo,"
                        + " visibilidad,es_abstracto,es_persistente,creado_por,actualizado_por)"
                        + " values(:id,:modelo,:paquete,:nombre,:descripcion,:estereotipo,"
                        + " :visibilidad,:abstracto,:persistente,:actor,:actor) on conflict(id) do"
                        + " update set id_paquete=excluded.id_paquete,nombre=excluded.nombre,"
                        + " descripcion=excluded.descripcion,estereotipo=excluded.estereotipo,"
                        + " visibilidad=excluded.visibilidad,es_abstracto=excluded.es_abstracto,"
                        + " es_persistente=excluded.es_persistente,"
                        + " actualizado_por=excluded.actualizado_por,eliminado_en=null",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("modelo", modelo)
                            .addValue("paquete", paquete)
                            .addValue("nombre", n.path("nombre").asText())
                            .addValue("descripcion", textOpt(n, "descripcion"))
                            .addValue("estereotipo", textOpt(n, "estereotipo"))
                            .addValue("visibilidad", n.path("visibilidad").asText("PUBLICO"))
                            .addValue("abstracto", n.path("es_abstracto").asBoolean(false))
                            .addValue("persistente", n.path("es_persistente").asBoolean(true))
                            .addValue("actor", actor));
        }
    }

    private void restaurarAtributos(UUID modelo, UUID actor, JsonNode arreglo) {
        for (JsonNode n : arreglo) {
            UUID id = uuid(n, "id");
            jdbc.update(
                    "insert into uml_colaborativo.atributo_uml"
                        + " (id,id_clase,nombre,tipo_uml,visibilidad,multiplicidad_minima,"
                        + " multiplicidad_maxima,es_identificador,es_estatico,es_unico,es_ordenado,coleccion_unica,"
                        + " valor_predeterminado,restricciones_json,orden) select"
                        + " :id,id,:nombre,:tipo,:vis,:min,:max,:ident,:est,:unico,:ordenado,:coleccionUnica,:pred,"
                        + " cast(:rest as jsonb),:orden from uml_colaborativo.clase_uml where"
                        + " id=:clase and id_modelo=:modelo on conflict(id) do update set"
                        + " id_clase=excluded.id_clase,nombre=excluded.nombre,"
                        + " tipo_uml=excluded.tipo_uml,visibilidad=excluded.visibilidad,"
                        + " multiplicidad_minima=excluded.multiplicidad_minima,"
                        + " multiplicidad_maxima=excluded.multiplicidad_maxima,"
                        + " es_identificador=excluded.es_identificador,es_estatico=excluded.es_estatico,"
                        + " es_unico=excluded.es_unico,es_ordenado=excluded.es_ordenado,coleccion_unica=excluded.coleccion_unica,valor_predeterminado=excluded.valor_predeterminado,"
                        + " restricciones_json=excluded.restricciones_json,orden=excluded.orden,eliminado_en=null",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("clase", uuid(n, "id_clase"))
                            .addValue("modelo", modelo)
                            .addValue("nombre", n.path("nombre").asText())
                            .addValue("tipo", n.path("tipo_uml").asText())
                            .addValue("vis", n.path("visibilidad").asText("PRIVADO"))
                            .addValue("min", n.path("multiplicidad_minima").asInt(1))
                            .addValue(
                                    "max",
                                    n.path("multiplicidad_maxima").isNull()
                                            ? null
                                            : n.path("multiplicidad_maxima").asInt())
                            .addValue("ident", n.path("es_identificador").asBoolean(false))
                            .addValue("est", n.path("es_estatico").asBoolean(false))
                            .addValue("unico", n.path("es_unico").asBoolean(false))
                            .addValue("ordenado", n.path("es_ordenado").asBoolean(false))
                            .addValue("coleccionUnica", n.path("coleccion_unica").asBoolean(true))
                            .addValue("pred", textOpt(n, "valor_predeterminado"))
                            .addValue(
                                    "rest",
                                    n.hasNonNull("restricciones_json")
                                            ? n.path("restricciones_json").toString()
                                            : "{}")
                            .addValue("orden", n.path("orden").asInt(0)));
        }
    }

    private void restaurarOperaciones(UUID modelo, UUID actor, JsonNode arreglo) {
        for (JsonNode n : arreglo) {
            UUID id = uuid(n, "id");
            jdbc.update(
                    "insert into uml_colaborativo.operacion_uml"
                        + " (id,id_clase,nombre,visibilidad,tipo_retorno,es_estatico,es_abstracto,orden)"
                        + " select :id,id,:nombre,:vis,:retorno,:est,:abst,:orden from"
                        + " uml_colaborativo.clase_uml where id=:clase and id_modelo=:modelo on"
                        + " conflict(id) do update set"
                        + " id_clase=excluded.id_clase,nombre=excluded.nombre,"
                        + " visibilidad=excluded.visibilidad,tipo_retorno=excluded.tipo_retorno,"
                        + " es_estatico=excluded.es_estatico,es_abstracto=excluded.es_abstracto,"
                        + " orden=excluded.orden,eliminado_en=null",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("clase", uuid(n, "id_clase"))
                            .addValue("modelo", modelo)
                            .addValue("nombre", n.path("nombre").asText())
                            .addValue("vis", n.path("visibilidad").asText("PUBLICO"))
                            .addValue("retorno", textOpt(n, "tipo_retorno"))
                            .addValue("est", n.path("es_estatico").asBoolean(false))
                            .addValue("abst", n.path("es_abstracto").asBoolean(false))
                            .addValue("orden", n.path("orden").asInt(0)));
        }
    }

    private void restaurarParametros(UUID modelo, UUID actor, JsonNode arreglo) {
        for (JsonNode n : arreglo) {
            UUID id = uuid(n, "id");
            jdbc.update(
                    "insert into uml_colaborativo.parametro_uml"
                        + " (id,id_operacion,nombre,tipo_uml,direccion,es_obligatorio,"
                        + " valor_predeterminado,orden) select"
                        + " :id,o.id,:nombre,:tipo,:dir,:oblig,:pred,:orden from"
                        + " uml_colaborativo.operacion_uml o join uml_colaborativo.clase_uml c on"
                        + " c.id=o.id_clase where o.id=:operacion and c.id_modelo=:modelo on"
                        + " conflict(id) do update set"
                        + " id_operacion=excluded.id_operacion,nombre=excluded.nombre,"
                        + " tipo_uml=excluded.tipo_uml,direccion=excluded.direccion,"
                        + " es_obligatorio=excluded.es_obligatorio,"
                        + " valor_predeterminado=excluded.valor_predeterminado,"
                        + " orden=excluded.orden,eliminado_en=null",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("operacion", uuid(n, "id_operacion"))
                            .addValue("modelo", modelo)
                            .addValue("nombre", n.path("nombre").asText())
                            .addValue("tipo", n.path("tipo_uml").asText())
                            .addValue("dir", n.path("direccion").asText("ENTRADA"))
                            .addValue("oblig", n.path("es_obligatorio").asBoolean(true))
                            .addValue("pred", textOpt(n, "valor_predeterminado"))
                            .addValue("orden", n.path("orden").asInt(0)));
        }
    }

    private void restaurarRelaciones(UUID modelo, UUID actor, JsonNode arreglo) {
        for (JsonNode n : arreglo) {
            UUID id = uuid(n, "id");
            jdbc.update(
                    "insert into uml_colaborativo.relacion_uml"
                        + " (id,id_modelo,id_clase_origen,id_clase_destino,nombre,tipo_relacion,"
                        + " direccion,rol_origen,rol_destino,minimo_origen,maximo_origen,"
                        + " minimo_destino,maximo_destino,creado_por,actualizado_por)"
                        + " values(:id,:modelo,:origen,:destino,:nombre,:tipo,:dir,:rolO,:rolD,"
                        + " :minO,:maxO,:minD,:maxD,:actor,:actor) on conflict(id) do update set"
                        + " id_clase_origen=excluded.id_clase_origen,"
                        + " id_clase_destino=excluded.id_clase_destino,"
                        + " nombre=excluded.nombre,tipo_relacion=excluded.tipo_relacion,"
                        + " direccion=excluded.direccion,rol_origen=excluded.rol_origen,"
                        + " rol_destino=excluded.rol_destino,"
                        + " minimo_origen=excluded.minimo_origen,maximo_origen=excluded.maximo_origen,"
                        + " minimo_destino=excluded.minimo_destino,maximo_destino=excluded.maximo_destino,"
                        + " actualizado_por=excluded.actualizado_por,eliminado_en=null",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("modelo", modelo)
                            .addValue("origen", uuid(n, "id_clase_origen"))
                            .addValue("destino", uuid(n, "id_clase_destino"))
                            .addValue("nombre", textOpt(n, "nombre"))
                            .addValue("tipo", n.path("tipo_relacion").asText("ASOCIACION"))
                            .addValue("dir", n.path("direccion").asText("SIN_DIRECCION"))
                            .addValue("rolO", textOpt(n, "rol_origen"))
                            .addValue("rolD", textOpt(n, "rol_destino"))
                            .addValue("minO", intOpt(n, "minimo_origen"))
                            .addValue("maxO", intOpt(n, "maximo_origen"))
                            .addValue("minD", intOpt(n, "minimo_destino"))
                            .addValue("maxD", intOpt(n, "maximo_destino"))
                            .addValue("actor", actor));
        }
    }

    private void restaurarPosicionesClases(UUID modelo, JsonNode arreglo) {
        for (JsonNode n : arreglo) {
            UUID clase = uuid(n, "id_clase");
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.posicion_clase_uml(id_clase,x,y,ancho,alto,orden_capa,estilo)"
                        + " select :clase,cast(:x as numeric),cast(:y as numeric), cast(:ancho as"
                        + " numeric),cast(:alto as numeric),:capa,cast(:estilo as jsonb) from"
                        + " uml_colaborativo.clase_uml where id=:clase and id_modelo=:modelo on"
                        + " conflict(id_clase) do update set x=excluded.x,y=excluded.y,"
                        + " ancho=excluded.ancho,alto=excluded.alto,orden_capa=excluded.orden_capa,estilo=excluded.estilo",
                    new MapSqlParameterSource()
                            .addValue("clase", clase)
                            .addValue("modelo", modelo)
                            .addValue("x", n.path("x").asDouble(0))
                            .addValue("y", n.path("y").asDouble(0))
                            .addValue("ancho", n.path("ancho").asDouble(240))
                            .addValue("alto", n.path("alto").asDouble(160))
                            .addValue("capa", n.path("orden_capa").asInt(0))
                            .addValue(
                                    "estilo",
                                    n.hasNonNull("estilo") ? n.path("estilo").toString() : "{}"));
        }
    }

    private void restaurarPosicionesRelaciones(UUID modelo, JsonNode arreglo) {
        for (JsonNode n : arreglo) {
            UUID rel = uuid(n, "id_relacion");
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.posicion_relacion_uml(id_relacion,puntos_intermedios,etiqueta_x,etiqueta_y,estilo)"
                        + " select :rel,cast(:puntos as jsonb),cast(:etiquetaX as"
                        + " numeric),cast(:etiquetaY as numeric),cast(:estilo as jsonb) from"
                        + " uml_colaborativo.relacion_uml where id=:rel and id_modelo=:modelo on"
                        + " conflict(id_relacion) do update set"
                        + " puntos_intermedios=excluded.puntos_intermedios,"
                        + " etiqueta_x=excluded.etiqueta_x,etiqueta_y=excluded.etiqueta_y,estilo=excluded.estilo",
                    new MapSqlParameterSource()
                            .addValue("rel", rel)
                            .addValue("modelo", modelo)
                            .addValue(
                                    "puntos",
                                    n.hasNonNull("puntos_intermedios")
                                            ? n.path("puntos_intermedios").toString()
                                            : "[]")
                            .addValue(
                                    "etiquetaX",
                                    n.hasNonNull("etiqueta_x")
                                            ? n.path("etiqueta_x").asDouble()
                                            : null)
                            .addValue(
                                    "etiquetaY",
                                    n.hasNonNull("etiqueta_y")
                                            ? n.path("etiqueta_y").asDouble()
                                            : null)
                            .addValue(
                                    "estilo",
                                    n.hasNonNull("estilo") ? n.path("estilo").toString() : "{}"));
        }
    }

    private void verificarEquivalenciaSnapshot(JsonNode origen, JsonNode destino) {
        if (!Objects.equals(
                origen.path("modelo").path("nombre").asText(""),
                destino.path("modelo").path("nombre").asText(""))) {
            throw new ApiException(
                    500, "Inconsistencia: nombre del modelo no coincide tras restaurar.");
        }
        List<ElementoDif> diffs = comparador.comparar(origen, destino);
        if (!diffs.isEmpty()) {
            throw new ApiException(
                    500,
                    "Inconsistencia: se detectaron diferencias inesperadas tras restaurar ("
                            + diffs.size()
                            + ").");
        }
    }

    // ─── Helpers de BD ───────────────────────────────────────────────────────

    private long iniciarEdicion(UUID modelo, long base, UUID actor) {
        return Optional.ofNullable(
                        jdbc.queryForObject(
                                "select"
                                    + " uml_colaborativo.iniciar_edicion_modelo(:modelo,:base,:actor)",
                                Map.of("modelo", modelo, "base", base, "actor", actor),
                                Long.class))
                .orElseThrow(() -> new ApiException(500, "No se pudo iniciar la edición."));
    }

    private UUID guardarVersion(
            UUID modelo, long revision, UUID actor, String resumen, UUID idOrigen) {
        return jdbc.queryForObject(
                "select"
                    + " uml_colaborativo.guardar_version_modelo_restaurada(:modelo,:revision,:actor,:resumen,:origen)",
                new MapSqlParameterSource()
                        .addValue("modelo", modelo)
                        .addValue("revision", revision)
                        .addValue("actor", actor)
                        .addValue("resumen", resumen)
                        .addValue("origen", idOrigen),
                UUID.class);
    }

    private UUID registrarCambio(
            UUID modelo, UUID solicitud, UUID actor, long base, long revision) {
        UUID cambio = UUID.randomUUID();
        jdbc.update(
                "insert into uml_colaborativo.cambio_modelo"
                    + " (id,id_modelo,id_sesion_edicion,id_cambio_cliente,id_usuario_actor,"
                    + " revision_base,revision_aplicada,tipo_operacion,tipo_entidad,id_entidad,"
                    + " parche,estado,aplicado_en)"
                    + " values(:cambio,:modelo,null,:solicitud,:actor,:base,:revision,'RESTAURAR',"
                    + " 'MODELO_UML',:modelo,cast('{}' as jsonb),'APLICADO',clock_timestamp())",
                new MapSqlParameterSource()
                        .addValue("cambio", cambio)
                        .addValue("modelo", modelo)
                        .addValue("solicitud", solicitud)
                        .addValue("actor", actor)
                        .addValue("base", base)
                        .addValue("revision", revision));
        return cambio;
    }

    private JsonNode leerInstanteaActual(UUID modelo) {
        String texto =
                jdbc.queryForObject(
                        "select uml_colaborativo.construir_instantanea_modelo(:modelo)::text",
                        Map.of("modelo", modelo),
                        String.class);
        try {
            return json.readTree(texto);
        } catch (Exception e) {
            throw new ApiException(500, "No se pudo leer el estado actual del modelo.");
        }
    }

    private JsonNode leerReglas() {
        return json.valueToTree(
                jdbc.queryForList(
                        "select id,codigo::text codigo,severidad,clave_implementacion from"
                                + " uml_colaborativo.regla_validacion where esta_activo order by"
                                + " codigo",
                        Map.of()));
    }

    private void validarConsistencia(JsonNode antes, JsonNode despues, JsonNode reglas) {
        var motor = new MotorSemantico();
        var previos = errores(motor.evaluar(antes, reglas));
        var posteriores = errores(new MotorSemantico().evaluar(despues, reglas));
        posteriores.removeAll(previos);
        if (!posteriores.isEmpty())
            throw new ApiException(
                    422,
                    "La restauración introduce inconsistencias UML. Revisa el snapshot antes de"
                            + " confirmar.");
    }

    private Set<String> errores(MotorSemantico.Resultado r) {
        Set<String> errores = new HashSet<>();
        r.hallazgos().stream()
                .filter(h -> "ERROR".equals(h.severidad()))
                .filter(h -> !"MODELO_VACIO".equals(h.codigo()))
                .forEach(h -> errores.add(h.codigo() + "|" + h.entidad() + "|" + h.id()));
        return errores;
    }

    private void autorizarRestauracion(Identidad actor, Cabecera c) {
        if (!actor.esAdministrador()
                && !historial.permiso(actor.id(), c.proyecto(), "VER_HISTORIAL_MODELO"))
            throw new ApiException(404, "Modelo no disponible.");
        if (!"ACTIVO".equals(c.estadoProyecto()) || "ARCHIVADO".equals(c.estado()))
            throw new ApiException(409, "El proyecto o el modelo no admite restauraciones.");
        if (!actor.esAdministrador()
                && !historial.permiso(actor.id(), c.proyecto(), "RESTAURAR_MODELO"))
            throw new ApiException(403, "No tienes permiso para restaurar este modelo.");
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private Set<String> ids(JsonNode arreglo, String claveId) {
        Set<String> set = new LinkedHashSet<>();
        if (arreglo.isArray())
            for (JsonNode n : arreglo) {
                String id = n.path(claveId).asText(null);
                if (id != null) set.add(id);
            }
        return set;
    }

    private int contarElementos(JsonNode snap) {
        int total = 0;
        for (String campo :
                List.of(
                        "paquetes",
                        "clases",
                        "atributos",
                        "operaciones",
                        "parametros",
                        "relaciones")) {
            JsonNode arr = snap.path(campo);
            if (arr.isArray()) total += arr.size();
        }
        return total;
    }

    private UUID uuid(JsonNode n, String campo) {
        return UUID.fromString(n.path(campo).asText());
    }

    private String textOpt(JsonNode n, String campo) {
        if (!n.hasNonNull(campo)) return null;
        String v = n.path(campo).asText(null);
        return (v == null || v.isBlank()) ? null : v;
    }

    private Integer intOpt(JsonNode n, String campo) {
        if (!n.hasNonNull(campo)) return null;
        return n.path(campo).asInt();
    }
}
