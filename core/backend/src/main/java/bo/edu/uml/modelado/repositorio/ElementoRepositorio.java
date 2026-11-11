package bo.edu.uml.modelado.repositorio;

import static bo.edu.uml.modelado.modelo.ReglasElemento.*;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ElementoRepositorio {
    private final NamedParameterJdbcTemplate jdbc;
    private final OperacionRepositorio operaciones;
    private final PaqueteRepositorio paquetes;

    public ElementoRepositorio(
            NamedParameterJdbcTemplate jdbc,
            OperacionRepositorio operaciones,
            PaqueteRepositorio paquetes) {
        this.jdbc = jdbc;
        this.operaciones = operaciones;
        this.paquetes = paquetes;
    }

    public void aplicar(UUID modelo, UUID actor, Accion a) {
        var p =
                new MapSqlParameterSource()
                        .addValue("modelo", modelo)
                        .addValue("actor", actor)
                        .addValue("id", a.idEntidad());
        switch (a.entidad()) {
            case "CLASE" -> clase(a, p);
            case "ATRIBUTO" -> atributo(a, p);
            case "OPERACION" -> operaciones.aplicar(a, p);
            case "RELACION" -> relacion(a, p);
            case "PAQUETE" -> paquetes.aplicar(a, p);
            default -> throw invalido("Elemento no soportado.");
        }
    }

    /** Reactiva solo el elemento objetivo de una propuesta aceptada explícitamente. */
    public boolean restaurarSiEliminado(UUID modelo, UUID actor, Accion a) {
        if (!Set.of("ACTUALIZAR", "MOVER").contains(a.operacion())) return false;
        var p =
                new MapSqlParameterSource()
                        .addValue("modelo", modelo)
                        .addValue("actor", actor)
                        .addValue("id", a.idEntidad());
        int restaurados =
                switch (a.entidad()) {
                    case "CLASE" ->
                            jdbc.update(
                                    "update uml_colaborativo.clase_uml set"
                                        + " eliminado_en=null,actualizado_por=:actor where id=:id"
                                        + " and id_modelo=:modelo and eliminado_en is not null",
                                    p);
                    case "ATRIBUTO" ->
                            jdbc.update(
                                    "update uml_colaborativo.atributo_uml a set eliminado_en=null"
                                        + " from uml_colaborativo.clase_uml c where a.id=:id and"
                                        + " a.id_clase=c.id and c.id_modelo=:modelo and"
                                        + " c.eliminado_en is null and a.eliminado_en is not null",
                                    p);
                    case "OPERACION" ->
                            jdbc.update(
                                    "update uml_colaborativo.operacion_uml o set eliminado_en=null"
                                        + " from uml_colaborativo.clase_uml c where o.id=:id and"
                                        + " o.id_clase=c.id and c.id_modelo=:modelo and"
                                        + " c.eliminado_en is null and o.eliminado_en is not null",
                                    p);
                    case "RELACION" ->
                            jdbc.update(
                                    "update uml_colaborativo.relacion_uml r set"
                                        + " eliminado_en=null,actualizado_por=:actor from"
                                        + " uml_colaborativo.clase_uml o,uml_colaborativo.clase_uml"
                                        + " d where r.id=:id and r.id_modelo=:modelo and"
                                        + " r.eliminado_en is not null and o.id=r.id_clase_origen"
                                        + " and d.id=r.id_clase_destino and o.eliminado_en is null"
                                        + " and d.eliminado_en is null",
                                    p);
                    case "PAQUETE" ->
                            jdbc.update(
                                    "update uml_colaborativo.paquete_uml set"
                                        + " eliminado_en=null,actualizado_por=:actor where id=:id"
                                        + " and id_modelo=:modelo and eliminado_en is not null",
                                    p);
                    default -> 0;
                };
        return restaurados == 1;
    }

    private void clase(Accion a, MapSqlParameterSource p) {
        JsonNode d = a.datos();
        switch (a.operacion()) {
            case "CREAR", "ACTUALIZAR" -> {
                objeto(
                        d,
                        Set.of(
                                "nombre",
                                "descripcion",
                                "estereotipo",
                                "visibilidad",
                                "esAbstracto",
                                "esPersistente",
                                "x",
                                "y",
                                "idPaquete",
                                "ancho",
                                "alto"));
                UUID paquete = d.hasNonNull("idPaquete") ? uuid(d, "idPaquete") : null;
                if (!d.has("idPaquete") && a.operacion().equals("ACTUALIZAR")) {
                    var existentes =
                            jdbc.queryForList(
                                    "select id_paquete from uml_colaborativo.clase_uml where id=:id"
                                            + " and id_modelo=:modelo and eliminado_en is null",
                                    p);
                    if (!existentes.isEmpty())
                        paquete = (UUID) existentes.getFirst().get("id_paquete");
                }
                if (paquete != null
                        && jdbc.queryForObject(
                                        "select count(*) from uml_colaborativo.paquete_uml where"
                                            + " id=:paquete and id_modelo=:modelo and eliminado_en"
                                            + " is null",
                                        p.addValue("paquete", paquete),
                                        Integer.class)
                                != 1)
                    throw invalido("El paquete debe estar activo en este modelo.");
                p.addValue("paquete", paquete);
                p.addValue("nombre", texto(d, "nombre", 180))
                        .addValue("descripcion", opcional(d, "descripcion", 4000))
                        .addValue("estereotipo", opcional(d, "estereotipo", 100))
                        .addValue(
                                "visibilidad",
                                enumerado(
                                        d,
                                        "visibilidad",
                                        "PUBLICO",
                                        "PUBLICO",
                                        "PRIVADO",
                                        "PROTEGIDO",
                                        "PAQUETE"))
                        .addValue("abstracto", bool(d, "esAbstracto", false))
                        .addValue("persistente", bool(d, "esPersistente", true));
                if (a.operacion().equals("CREAR")) {
                    jdbc.update(
                            "insert into"
                                + " uml_colaborativo.clase_uml(id,id_modelo,id_paquete,nombre,descripcion,estereotipo,visibilidad,es_abstracto,es_persistente,creado_por,actualizado_por)"
                                + " values(:id,:modelo,:paquete,:nombre,:descripcion,:estereotipo,:visibilidad,:abstracto,:persistente,:actor,:actor)",
                            p);
                    p.addValue("x", coordenada(d, "x"))
                            .addValue("y", coordenada(d, "y"))
                            .addValue("ancho", dimension(d, "ancho", 240))
                            .addValue("alto", dimension(d, "alto", 160));
                    jdbc.update(
                            "insert into"
                                + " uml_colaborativo.posicion_clase_uml(id_clase,x,y,ancho,alto)"
                                + " values(:id,:x,:y,:ancho,:alto)",
                            p);
                } else
                    exigir(
                            jdbc.update(
                                    "update uml_colaborativo.clase_uml set"
                                        + " id_paquete=:paquete,nombre=:nombre,descripcion=:descripcion,estereotipo=:estereotipo,visibilidad=:visibilidad,es_abstracto=:abstracto,es_persistente=:persistente,actualizado_por=:actor"
                                        + " where id=:id and id_modelo=:modelo and eliminado_en is"
                                        + " null",
                                    p));
                if (a.operacion().equals("ACTUALIZAR")
                        && (d.has("x") || d.has("y") || d.has("ancho") || d.has("alto"))) {
                    p.addValue("px", d.has("x") ? coordenada(d, "x") : null)
                            .addValue("py", d.has("y") ? coordenada(d, "y") : null)
                            .addValue("ancho", d.has("ancho") ? dimension(d, "ancho", 240) : null)
                            .addValue("alto", d.has("alto") ? dimension(d, "alto", 160) : null);
                    jdbc.update(
                            "insert into"
                                + " uml_colaborativo.posicion_clase_uml(id_clase,x,y,ancho,alto)"
                                + " values(:id,coalesce(cast(:px as numeric),0),coalesce(cast(:py"
                                + " as numeric),0),coalesce(cast(:ancho as"
                                + " numeric),240),coalesce(cast(:alto as numeric),160)) on"
                                + " conflict(id_clase) do update set x=coalesce(cast(:px as"
                                + " numeric),posicion_clase_uml.x),y=coalesce(cast(:py as"
                                + " numeric),posicion_clase_uml.y),ancho=coalesce(cast(:ancho as"
                                + " numeric),posicion_clase_uml.ancho),alto=coalesce(cast(:alto as"
                                + " numeric),posicion_clase_uml.alto)",
                            p);
                }
            }
            case "ELIMINAR" -> {
                sinDatos(d);
                exigir(
                        jdbc.update(
                                "update uml_colaborativo.clase_uml set"
                                    + " eliminado_en=clock_timestamp(),actualizado_por=:actor where"
                                    + " id=:id and id_modelo=:modelo and eliminado_en is null",
                                p));
            }
            case "MOVER" -> {
                objeto(d, Set.of("x", "y"));
                p.addValue("x", coordenada(d, "x")).addValue("y", coordenada(d, "y"));
                exigir(
                        jdbc.update(
                                "insert into"
                                    + " uml_colaborativo.posicion_clase_uml(id_clase,x,y,ancho,alto)"
                                    + " select id,:x,:y,240,160 from uml_colaborativo.clase_uml"
                                    + " where id=:id and id_modelo=:modelo and eliminado_en is null"
                                    + " on conflict(id_clase) do update set"
                                    + " x=excluded.x,y=excluded.y",
                                p));
            }
            default -> throw invalido("Operación inválida.");
        }
    }

    private void atributo(Accion a, MapSqlParameterSource p) {
        if (a.operacion().equals("ELIMINAR")) {
            sinDatos(a.datos());
            exigir(
                    jdbc.update(
                            "update uml_colaborativo.atributo_uml a set"
                                + " eliminado_en=clock_timestamp() from uml_colaborativo.clase_uml"
                                + " c where a.id=:id and a.id_clase=c.id and c.id_modelo=:modelo"
                                + " and c.eliminado_en is null and a.eliminado_en is null",
                            p));
            return;
        }
        if (!Set.of("CREAR", "ACTUALIZAR").contains(a.operacion()))
            throw invalido("Operación inválida.");
        JsonNode d = a.datos();
        objeto(
                d,
                Set.of(
                        "idClase",
                        "nombre",
                        "tipoUml",
                        "visibilidad",
                        "minimo",
                        "maximo",
                        "esIdentificador",
                        "esEstatico",
                        "esUnico",
                        "esOrdenado",
                        "coleccionUnica",
                        "valorPredeterminado",
                        "restricciones"));
        JsonNode restricciones = d.path("restricciones");
        if (!restricciones.isMissingNode()
                && (!restricciones.isObject() || restricciones.toString().length() > 8000))
            throw invalido("Las restricciones deben ser un objeto JSON de hasta 8000 caracteres.");
        boolean ordenadoAnterior = false, unicaAnterior = true;
        if (a.operacion().equals("ACTUALIZAR")
                && (!d.has("esOrdenado") || !d.has("coleccionUnica"))) {
            var anteriores =
                    jdbc.queryForList(
                            "select a.es_ordenado,a.coleccion_unica from"
                                + " uml_colaborativo.atributo_uml a join uml_colaborativo.clase_uml"
                                + " c on c.id=a.id_clase where a.id=:id and c.id_modelo=:modelo and"
                                + " a.eliminado_en is null",
                            p);
            if (!anteriores.isEmpty()) {
                ordenadoAnterior = Boolean.TRUE.equals(anteriores.getFirst().get("es_ordenado"));
                unicaAnterior = Boolean.TRUE.equals(anteriores.getFirst().get("coleccion_unica"));
            }
        }
        int min = entero(d, "minimo", 1);
        Integer max = maximo(d, "maximo", 1);
        boolean id = bool(d, "esIdentificador", false), estatico = bool(d, "esEstatico", false);
        if (max != null && max < min)
            throw invalido(
                    "Multiplicidad inválida; un identificador requiere 1..1 y no ser estático.");
        p.addValue("clase", uuid(d, "idClase"))
                .addValue("nombre", texto(d, "nombre", 180))
                .addValue("tipo", texto(d, "tipoUml", 180))
                .addValue(
                        "visibilidad",
                        enumerado(
                                d,
                                "visibilidad",
                                "PRIVADO",
                                "PUBLICO",
                                "PRIVADO",
                                "PROTEGIDO",
                                "PAQUETE"))
                .addValue("min", min)
                .addValue("max", max)
                .addValue("identificador", id)
                .addValue("estatico", estatico)
                .addValue("unico", bool(d, "esUnico", false))
                .addValue("ordenado", bool(d, "esOrdenado", ordenadoAnterior))
                .addValue("coleccionUnica", bool(d, "coleccionUnica", unicaAnterior))
                .addValue("predeterminado", opcional(d, "valorPredeterminado", 4000))
                .addValue(
                        "restricciones",
                        restricciones.isMissingNode() ? "{}" : restricciones.toString());
        if (a.operacion().equals("CREAR"))
            exigir(
                    jdbc.update(
                            "insert into"
                                + " uml_colaborativo.atributo_uml(id,id_clase,nombre,tipo_uml,visibilidad,multiplicidad_minima,multiplicidad_maxima,es_identificador,es_estatico,es_unico,es_ordenado,coleccion_unica,valor_predeterminado,restricciones_json)"
                                + " select"
                                + " :id,id,:nombre,:tipo,:visibilidad,:min,:max,:identificador,:estatico,:unico,:ordenado,:coleccionUnica,:predeterminado,cast(:restricciones"
                                + " as jsonb) from uml_colaborativo.clase_uml where id=:clase and"
                                + " id_modelo=:modelo and eliminado_en is null",
                            p));
        else
            exigir(
                    jdbc.update(
                            "update uml_colaborativo.atributo_uml a set"
                                + " nombre=:nombre,tipo_uml=:tipo,visibilidad=:visibilidad,multiplicidad_minima=:min,multiplicidad_maxima=:max,es_identificador=:identificador,es_estatico=:estatico,es_unico=:unico,es_ordenado=:ordenado,coleccion_unica=:coleccionUnica,valor_predeterminado=:predeterminado,restricciones_json=cast(:restricciones"
                                + " as jsonb) from uml_colaborativo.clase_uml c where a.id=:id and"
                                + " a.id_clase=:clase and c.id=a.id_clase and c.id_modelo=:modelo"
                                + " and c.eliminado_en is null and a.eliminado_en is null",
                            p));
    }

    private void relacion(Accion a, MapSqlParameterSource p) {
        if (a.operacion().equals("ELIMINAR")) {
            sinDatos(a.datos());
            exigir(
                    jdbc.update(
                            "update uml_colaborativo.relacion_uml set"
                                    + " eliminado_en=clock_timestamp(),actualizado_por=:actor where"
                                    + " id=:id and id_modelo=:modelo and eliminado_en is null",
                            p));
            return;
        }
        if (!Set.of("CREAR", "ACTUALIZAR").contains(a.operacion()))
            throw invalido("Operación inválida.");
        JsonNode d = a.datos();
        objeto(
                d,
                Set.of(
                        "origen",
                        "destino",
                        "nombre",
                        "tipo",
                        "direccion",
                        "rolOrigen",
                        "rolDestino",
                        "minimoOrigen",
                        "maximoOrigen",
                        "minimoDestino",
                        "maximoDestino"));
        UUID origen = uuid(d, "origen"), destino = uuid(d, "destino");
        String tipo =
                enumerado(
                        d,
                        "tipo",
                        "ASOCIACION",
                        "ASOCIACION",
                        "AGREGACION",
                        "COMPOSICION",
                        "GENERALIZACION",
                        "DEPENDENCIA");
        boolean estructural = Set.of("ASOCIACION", "AGREGACION", "COMPOSICION").contains(tipo);
        Integer minO = estructural ? entero(d, "minimoOrigen", 1) : null,
                maxO = estructural ? maximo(d, "maximoOrigen", 1) : null;
        Integer minD = estructural ? entero(d, "minimoDestino", 1) : null,
                maxD = estructural ? maximo(d, "maximoDestino", 1) : null;
        if (estructural && ((maxO != null && maxO < minO) || (maxD != null && maxD < minD)))
            throw invalido("Multiplicidad inválida.");
        if (!estructural
                && (d.hasNonNull("minimoOrigen")
                        || d.hasNonNull("maximoOrigen")
                        || d.hasNonNull("minimoDestino")
                        || d.hasNonNull("maximoDestino")))
            throw invalido("Esta relación no utiliza cardinalidades.");
        p.addValue("origen", origen)
                .addValue("destino", destino)
                .addValue("tipo", tipo)
                .addValue("nombre", opcional(d, "nombre", 180))
                .addValue(
                        "direccion",
                        enumerado(
                                d,
                                "direccion",
                                "SIN_DIRECCION",
                                "SIN_DIRECCION",
                                "ORIGEN_A_DESTINO",
                                "DESTINO_A_ORIGEN",
                                "BIDIRECCIONAL"))
                .addValue("rolO", opcional(d, "rolOrigen", 180))
                .addValue("rolD", opcional(d, "rolDestino", 180))
                .addValue("minO", minO)
                .addValue("maxO", maxO)
                .addValue("minD", minD)
                .addValue("maxD", maxD);
        long extremos =
                jdbc.queryForObject(
                        "select count(*) from uml_colaborativo.clase_uml where id in"
                            + " (:origen,:destino) and id_modelo=:modelo and eliminado_en is null",
                        p,
                        Long.class);
        if (extremos != (origen.equals(destino) ? 1 : 2))
            throw invalido("Ambos extremos deben ser clases activas de este modelo.");
        if (tipo.equals("GENERALIZACION")) {
            if (origen.equals(destino)) throw invalido("Una clase no puede heredar de sí misma.");
            boolean ciclo =
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
                                    with recursive padres(id) as (
                                      select :destino::uuid union
                                      select r.id_clase_destino from uml_colaborativo.relacion_uml r join padres p on r.id_clase_origen=p.id
                                      where r.id_modelo=:modelo and r.tipo_relacion='GENERALIZACION' and r.eliminado_en is null and r.id<>:id
                                    ) select exists(select 1 from padres where id=:origen)
                                    """,
                                    p,
                                    Boolean.class));
            if (ciclo) throw invalido("La generalización produciría un ciclo.");
        }
        if (tipo.equals("COMPOSICION") && (maxO == null || maxO > 1))
            throw invalido(
                    "El extremo conjunto (origen) de una composición tiene máximo 1; la"
                        + " exclusividad se exige por instancia, no por clase.");
        bo.edu.uml.modelado.modelo.SemanticaUml.direccion(
                tipo, d.path("direccion").asText("SIN_DIRECCION"));
        if (a.operacion().equals("CREAR"))
            jdbc.update(
                    "insert into"
                        + " uml_colaborativo.relacion_uml(id,id_modelo,id_clase_origen,id_clase_destino,nombre,tipo_relacion,direccion,rol_origen,rol_destino,minimo_origen,maximo_origen,minimo_destino,maximo_destino,creado_por,actualizado_por)"
                        + " values(:id,:modelo,:origen,:destino,:nombre,:tipo,:direccion,:rolO,:rolD,:minO,:maxO,:minD,:maxD,:actor,:actor)",
                    p);
        else
            exigir(
                    jdbc.update(
                            "update uml_colaborativo.relacion_uml set"
                                + " id_clase_origen=:origen,id_clase_destino=:destino,nombre=:nombre,tipo_relacion=:tipo,direccion=:direccion,rol_origen=:rolO,rol_destino=:rolD,minimo_origen=:minO,maximo_origen=:maxO,minimo_destino=:minD,maximo_destino=:maxD,actualizado_por=:actor"
                                + " where id=:id and id_modelo=:modelo and eliminado_en is null",
                            p));
    }

    private void exigir(int n) {
        if (n != 1) throw new ApiException(404, "Elemento no disponible en este modelo.");
    }

    private void sinDatos(JsonNode d) {
        if (d != null && !d.isNull() && (!d.isObject() || !d.isEmpty()))
            throw invalido("La eliminación no acepta datos adicionales.");
    }
}
