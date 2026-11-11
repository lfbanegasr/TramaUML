package bo.edu.uml.ia.repositorio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.dto.IaDto.*;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.ElementoContexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.OperacionGenerada;
import bo.edu.uml.modelado.dto.ModeladoDto.Accion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IaRepositorio {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  public IaRepositorio(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public record CabeceraModelo(
      UUID id, UUID proyecto, long revision, String estadoModelo, String estadoProyecto) {}

  public record PropuestaBloqueada(
      UUID id,
      UUID modelo,
      UUID proyecto,
      long revisionBase,
      long revisionActual,
      String estado,
      String revisionEdicion) {}

  public CabeceraModelo cabecera(UUID modelo) {
    return jdbc
        .query(
            """
            select m.id,m.id_proyecto,m.revision_actual,m.estado estado_modelo,
                   p.estado estado_proyecto
            from uml_colaborativo.modelo_uml m
            join uml_colaborativo.proyecto p on p.id=m.id_proyecto
            where m.id=:modelo
            """,
            Map.of("modelo", modelo),
            (r, n) ->
                new CabeceraModelo(
                    r.getObject("id", UUID.class),
                    r.getObject("id_proyecto", UUID.class),
                    r.getLong("revision_actual"),
                    r.getString("estado_modelo"),
                    r.getString("estado_proyecto")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "Modelo no disponible."));
  }

  public boolean permiso(UUID actor, UUID proyecto, String codigo) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select uml_colaborativo.tiene_permiso_proyecto(:actor,:proyecto,:codigo)",
            Map.of("actor", actor, "proyecto", proyecto, "codigo", codigo),
            Boolean.class));
  }

  public Set<String> nombresClases(UUID modelo) {
    return new HashSet<>(
        jdbc.query(
            "select nombre from uml_colaborativo.clase_uml where id_modelo=:modelo"
                + " and eliminado_en is null",
            Map.of("modelo", modelo),
            (r, n) -> r.getString("nombre")));
  }

  public int cantidadClases(UUID modelo) {
    return jdbc.queryForObject(
        "select count(*) from uml_colaborativo.clase_uml where id_modelo=:modelo"
            + " and eliminado_en is null",
        Map.of("modelo", modelo),
        Integer.class);
  }

  public List<ElementoContexto> elementosContexto(UUID modelo) {
    return jdbc.query(
        """
        select id,tipo,nombre,id_contenedor,valor::text from (
          select p.id,'PAQUETE' tipo,p.nombre,p.id_paquete_padre id_contenedor,
            jsonb_build_object('nombre',p.nombre,'descripcion',p.descripcion,'padre',p.id_paquete_padre) valor
          from uml_colaborativo.paquete_uml p where p.id_modelo=:modelo and p.eliminado_en is null
          union all
          select c.id,'CLASE' tipo,c.nombre,null::uuid id_contenedor,
            jsonb_build_object('nombre',c.nombre,'descripcion',c.descripcion,
              'estereotipo',c.estereotipo,'visibilidad',c.visibilidad,
              'esAbstracto',c.es_abstracto,'esPersistente',c.es_persistente,
              'idPaquete',c.id_paquete) valor
          from uml_colaborativo.clase_uml c
          where c.id_modelo=:modelo and c.eliminado_en is null
          union all
          select a.id,'ATRIBUTO',a.nombre,a.id_clase,
            jsonb_build_object('idClase',a.id_clase,'nombre',a.nombre,'tipoUml',a.tipo_uml,
              'visibilidad',a.visibilidad,'minimo',a.multiplicidad_minima,
              'maximo',a.multiplicidad_maxima,'esIdentificador',a.es_identificador,
              'esEstatico',a.es_estatico,'esUnico',a.es_unico,'esOrdenado',a.es_ordenado,'coleccionUnica',a.coleccion_unica,
              'valorPredeterminado',a.valor_predeterminado,
              'restricciones',a.restricciones_json)
          from uml_colaborativo.atributo_uml a
          join uml_colaborativo.clase_uml c on c.id=a.id_clase
          where c.id_modelo=:modelo and c.eliminado_en is null and a.eliminado_en is null
          union all
          select o.id,'OPERACION',o.nombre,o.id_clase,
            jsonb_build_object('idClase',o.id_clase,'nombre',o.nombre,
              'tipoRetorno',o.tipo_retorno,'visibilidad',o.visibilidad,
              'esEstatico',o.es_estatico,'esAbstracto',o.es_abstracto,
              'parametros',coalesce((select jsonb_agg(jsonb_build_object(
                'id',p.id,'nombre',p.nombre,'tipoUml',p.tipo_uml,'direccion',p.direccion,
                'esObligatorio',p.es_obligatorio,'valorPredeterminado',p.valor_predeterminado)
                order by p.orden) from uml_colaborativo.parametro_uml p
                where p.id_operacion=o.id and p.eliminado_en is null),'[]'::jsonb))
          from uml_colaborativo.operacion_uml o
          join uml_colaborativo.clase_uml c on c.id=o.id_clase
          where c.id_modelo=:modelo and c.eliminado_en is null and o.eliminado_en is null
          union all
          select r.id,'RELACION',r.nombre,null::uuid,
            jsonb_build_object('origen',r.id_clase_origen,'destino',r.id_clase_destino,
              'nombre',r.nombre,'tipo',r.tipo_relacion,'direccion',r.direccion,
              'rolOrigen',r.rol_origen,'rolDestino',r.rol_destino,
              'minimoOrigen',r.minimo_origen,'maximoOrigen',r.maximo_origen,
              'minimoDestino',r.minimo_destino,'maximoDestino',r.maximo_destino)
          from uml_colaborativo.relacion_uml r
          where r.id_modelo=:modelo and r.eliminado_en is null
        ) elementos order by tipo,nombre,id
        """,
        Map.of("modelo", modelo),
        (r, n) ->
            new ElementoContexto(
                r.getObject("id", UUID.class),
                r.getString("tipo"),
                r.getString("nombre"),
                r.getObject("id_contenedor", UUID.class),
                nodo(r.getString("valor"))));
  }

  public UUID guardarArchivo(
      UUID proyecto,
      UUID actor,
      String categoria,
      String nombre,
      String tipoContenido,
      byte[] contenido) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into uml_colaborativo.archivo_almacenado
          (id,id_proyecto,cargado_por,categoria,proveedor_almacenamiento,
           clave_almacenamiento,nombre_original,tipo_contenido,tamano_bytes,sha256,contenido)
        values(:id,:proyecto,:actor,:categoria,'BASE_DATOS',:clave,:nombre,:tipo,
               :tamano,:sha,:contenido)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("proyecto", proyecto)
            .addValue("actor", actor)
            .addValue("categoria", categoria)
            .addValue("clave", "ia/" + categoria.toLowerCase(Locale.ROOT) + "/" + id)
            .addValue("nombre", nombre)
            .addValue("tipo", tipoContenido)
            .addValue("tamano", (long) contenido.length)
            .addValue("sha", sha256(contenido))
            .addValue("contenido", contenido));
    return id;
  }

  public UUID crearSolicitud(
      UUID modelo, long revision, UUID actor, String tipoEntrada, String texto, UUID archivo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into uml_colaborativo.solicitud_inteligente
          (id,id_modelo,revision_base,tipo_entrada,texto_original,id_archivo_origen,
           estado,solicitado_por)
        values(:id,:modelo,:revision,:tipo,:texto,:archivo,'RECIBIDO',:actor)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("modelo", modelo)
            .addValue("revision", revision)
            .addValue("tipo", tipoEntrada)
            .addValue("texto", texto)
            .addValue("archivo", archivo)
            .addValue("actor", actor));
    return id;
  }

  public void procesando(UUID solicitud) {
    jdbc.update(
        "update uml_colaborativo.solicitud_inteligente set estado='PROCESANDO' where"
            + " id=:id and estado='RECIBIDO'",
        Map.of("id", solicitud));
  }

  public void completarSolicitud(UUID solicitud, String textoProcesado) {
    jdbc.update(
        """
        update uml_colaborativo.solicitud_inteligente
        set estado='COMPLETADO',texto_transcrito=case when tipo_entrada='VOZ' then :texto else texto_transcrito end,
            texto_corregido=case when tipo_entrada='IMAGEN' then :texto else texto_corregido end,
            completado_en=clock_timestamp(),detalle_error=null
        where id=:id
        """,
        new MapSqlParameterSource().addValue("id", solicitud).addValue("texto", textoProcesado));
  }

  public void fallarSolicitud(UUID solicitud, String detalle) {
    jdbc.update(
        """
        update uml_colaborativo.solicitud_inteligente
        set estado='FALLIDO',detalle_error=:detalle,completado_en=clock_timestamp()
        where id=:id and estado in ('RECIBIDO','PROCESANDO')
        """,
        Map.of("id", solicitud, "detalle", detalle));
  }

  public UUID guardarPropuesta(
      UUID solicitud, String resumen, BigDecimal confianza, List<OperacionGenerada> operaciones) {
    UUID propuesta = UUID.randomUUID();
    jdbc.update(
        """
        insert into uml_colaborativo.propuesta_inteligente
          (id,id_solicitud,estado,resumen,confianza_general)
        values(:id,:solicitud,'BORRADOR',:resumen,:confianza)
        """,
        Map.of(
            "id", propuesta,
            "solicitud", solicitud,
            "resumen", resumen,
            "confianza", confianza));
    for (OperacionGenerada operacion : operaciones) guardarOperacion(propuesta, operacion);
    for (OperacionGenerada operacion : operaciones)
      for (UUID requerida : operacion.dependencias())
        jdbc.update(
            "insert into"
                + " uml_colaborativo.dependencia_operacion_propuesta(id_operacion,id_operacion_requerida)"
                + " values(:operacion,:requerida)",
            Map.of("operacion", operacion.id(), "requerida", requerida));
    jdbc.update(
        "update uml_colaborativo.propuesta_inteligente set estado='LISTO' where id=:id",
        Map.of("id", propuesta));
    return propuesta;
  }

  private void guardarOperacion(UUID propuesta, OperacionGenerada operacion) {
    jdbc.update(
        """
        insert into uml_colaborativo.operacion_propuesta
          (id,id_propuesta,numero_secuencia,tipo_operacion,tipo_entidad,id_entidad_destino,
           valor_actual,valor_propuesto,confianza,esta_seleccionado,estado,mensajes_validacion)
        values(:id,:propuesta,:secuencia,:operacion,:entidad,:destino,cast(:actual as jsonb),
               cast(:propuesto as jsonb),:confianza,:seleccionada,:estado,cast(:mensajes as jsonb))
        """,
        new MapSqlParameterSource()
            .addValue("id", operacion.id())
            .addValue("propuesta", propuesta)
            .addValue("secuencia", operacion.numeroSecuencia())
            .addValue("operacion", operacion.tipoOperacion())
            .addValue("entidad", operacion.tipoEntidad())
            .addValue("destino", operacion.idEntidad())
            .addValue("actual", textoJson(operacion.valorActual()))
            .addValue("propuesto", textoJson(operacion.valorPropuesto()))
            .addValue("confianza", operacion.confianza())
            .addValue("seleccionada", operacion.seleccionada())
            .addValue("estado", operacion.estado())
            .addValue("mensajes", operacion.mensajes().toString()));
  }

  public Optional<UUID> propuestaVigente(UUID modelo) {
    return jdbc
        .query(
            """
            select p.id from uml_colaborativo.propuesta_inteligente p
            join uml_colaborativo.solicitud_inteligente s on s.id=p.id_solicitud
            where s.id_modelo=:modelo and p.estado='LISTO'
            order by p.creado_en desc,p.id desc limit 1
            """,
            Map.of("modelo", modelo),
            (r, n) -> r.getObject("id", UUID.class))
        .stream()
        .findFirst();
  }

  public Propuesta detalle(UUID modelo, UUID propuesta) {
    record Cabecera(
        UUID id,
        UUID solicitud,
        String tipo,
        String texto,
        long base,
        long actual,
        String estado,
        String resumen,
        BigDecimal confianza,
        Timestamp creada,
        Timestamp actualizada) {}
    Cabecera c =
        jdbc
            .query(
                """
                select p.id,p.id_solicitud,s.tipo_entrada,
                  coalesce(s.texto_corregido,s.texto_transcrito,s.texto_original) texto,
                  s.revision_base,m.revision_actual,p.estado,p.resumen,p.confianza_general,
                  p.creado_en,p.actualizado_en
                from uml_colaborativo.propuesta_inteligente p
                join uml_colaborativo.solicitud_inteligente s on s.id=p.id_solicitud
                join uml_colaborativo.modelo_uml m on m.id=s.id_modelo
                where p.id=:propuesta and s.id_modelo=:modelo
                """,
                Map.of("propuesta", propuesta, "modelo", modelo),
                (r, n) ->
                    new Cabecera(
                        r.getObject("id", UUID.class),
                        r.getObject("id_solicitud", UUID.class),
                        r.getString("tipo_entrada"),
                        r.getString("texto"),
                        r.getLong("revision_base"),
                        r.getLong("revision_actual"),
                        r.getString("estado"),
                        r.getString("resumen"),
                        r.getBigDecimal("confianza_general"),
                        r.getTimestamp("creado_en"),
                        r.getTimestamp("actualizado_en")))
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(404, "Propuesta no disponible."));
    List<Operacion> operaciones = operaciones(propuesta);
    boolean seleccionValida =
        operaciones.stream().anyMatch(Operacion::seleccionada)
            && operaciones.stream()
                .filter(Operacion::seleccionada)
                .allMatch(o -> "VALIDO".equals(o.estado()));
    return new Propuesta(
        c.id(),
        c.solicitud(),
        modelo,
        c.tipo(),
        c.texto(),
        c.base(),
        c.actual(),
        c.estado(),
        c.resumen(),
        c.confianza(),
        c.creada().toInstant(),
        c.actualizada().toInstant().toString(),
        "LISTO".equals(c.estado())
            && c.base() == c.actual()
            && seleccionValida
            && !dependenciasInvalidas(propuesta),
        operaciones);
  }

  private List<Operacion> operaciones(UUID propuesta) {
    Map<UUID, List<UUID>> dependencias = new HashMap<>();
    for (Map<String, Object> fila :
        jdbc.queryForList(
            """
            select d.id_operacion,d.id_operacion_requerida
            from uml_colaborativo.dependencia_operacion_propuesta d
            join uml_colaborativo.operacion_propuesta o on o.id=d.id_operacion
            where o.id_propuesta=:propuesta
            order by d.id_operacion,d.id_operacion_requerida
            """,
            Map.of("propuesta", propuesta))) {
      UUID operacion = (UUID) fila.get("id_operacion");
      dependencias
          .computeIfAbsent(operacion, k -> new ArrayList<>())
          .add((UUID) fila.get("id_operacion_requerida"));
    }
    return jdbc.query(
        """
        select id,numero_secuencia,tipo_operacion,tipo_entidad,id_entidad_destino,
               valor_actual::text,valor_propuesto::text,confianza,esta_seleccionado,
               estado,mensajes_validacion::text
        from uml_colaborativo.operacion_propuesta
        where id_propuesta=:propuesta order by numero_secuencia,id
        """,
        Map.of("propuesta", propuesta),
        (r, n) -> {
          UUID id = r.getObject("id", UUID.class);
          JsonNode mensajes = nodo(r.getString("mensajes_validacion"));
          return new Operacion(
              id,
              r.getInt("numero_secuencia"),
              r.getString("tipo_operacion"),
              r.getString("tipo_entidad"),
              r.getObject("id_entidad_destino", UUID.class),
              nodo(r.getString("valor_actual")),
              nodo(r.getString("valor_propuesto")),
              r.getBigDecimal("confianza"),
              r.getBoolean("esta_seleccionado"),
              r.getString("estado"),
              mensajes,
              justificacion(mensajes),
              List.copyOf(dependencias.getOrDefault(id, List.of())));
        });
  }

  public void actualizarOperacion(
      UUID propuesta,
      UUID operacion,
      boolean seleccionada,
      JsonNode valorPropuesto,
      String estado,
      JsonNode mensajes) {
    int filas =
        jdbc.update(
            """
            update uml_colaborativo.operacion_propuesta o
            set esta_seleccionado=:seleccionada,valor_propuesto=cast(:valor as jsonb),
                estado=:estado,mensajes_validacion=cast(:mensajes as jsonb)
            from uml_colaborativo.propuesta_inteligente p
            where o.id=:operacion and o.id_propuesta=:propuesta
              and p.id=o.id_propuesta and p.estado='LISTO'
            """,
            new MapSqlParameterSource()
                .addValue("propuesta", propuesta)
                .addValue("operacion", operacion)
                .addValue("seleccionada", seleccionada)
                .addValue("valor", textoJson(valorPropuesto))
                .addValue("estado", estado)
                .addValue("mensajes", mensajes.toString()));
    if (filas != 1) throw new ApiException(409, "La propuesta cambió durante la revisión.");
  }

  public void registrarRevision(UUID propuesta, UUID actor, String revision) {
    int filas =
        jdbc.update(
            """
            update uml_colaborativo.propuesta_inteligente
            set revisado_por=:actor,revisado_en=clock_timestamp(),
                actualizado_en=clock_timestamp()
            where id=:propuesta and estado='LISTO'
              and actualizado_en=cast(:revision as timestamptz)
            """,
            Map.of(
                "propuesta", propuesta,
                "actor", actor,
                "revision", revision));
    if (filas != 1)
      throw new ApiException(
          409, "La propuesta fue modificada por otra persona. Vuelve a cargarla.");
  }

  public PropuestaBloqueada bloquear(UUID modelo, UUID propuesta) {
    return jdbc
        .query(
            """
            select p.id,s.id_modelo,m.id_proyecto,s.revision_base,m.revision_actual,
                   p.estado,p.actualizado_en::text revision_edicion
            from uml_colaborativo.propuesta_inteligente p
            join uml_colaborativo.solicitud_inteligente s on s.id=p.id_solicitud
            join uml_colaborativo.modelo_uml m on m.id=s.id_modelo
            where p.id=:propuesta and s.id_modelo=:modelo for update of p
            """,
            Map.of("propuesta", propuesta, "modelo", modelo),
            (r, n) ->
                new PropuestaBloqueada(
                    r.getObject("id", UUID.class),
                    r.getObject("id_modelo", UUID.class),
                    r.getObject("id_proyecto", UUID.class),
                    r.getLong("revision_base"),
                    r.getLong("revision_actual"),
                    r.getString("estado"),
                    r.getString("revision_edicion")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "Propuesta no disponible."));
  }

  public Optional<UUID> sesionEditable(UUID modelo, UUID actor) {
    return jdbc
        .query(
            """
            select s.id from uml_colaborativo.sesion_edicion_modelo s
            join uml_colaborativo.participante_sesion_modelo p on p.id_sesion_edicion=s.id
            where s.id_modelo=:modelo and s.estado='ACTIVO' and p.id_usuario=:actor
              and p.modo_participante='EDITOR' and p.estado_presencia='EN_LINEA'
              and p.salida_en is null
            order by s.abierto_en desc limit 1
            """,
            Map.of("modelo", modelo, "actor", actor),
            (r, n) -> r.getObject("id", UUID.class))
        .stream()
        .findFirst();
  }

  public List<Accion> accionesAplicables(UUID propuesta) {
    return jdbc.query(
        """
        select tipo_operacion,tipo_entidad,id_entidad_destino,valor_propuesto::text
        from uml_colaborativo.operacion_propuesta
        where id_propuesta=:propuesta and esta_seleccionado and estado='VALIDO'
        order by numero_secuencia,id
        """,
        Map.of("propuesta", propuesta),
        (r, n) ->
            new Accion(
                r.getString("tipo_operacion"),
                r.getString("tipo_entidad"),
                r.getObject("id_entidad_destino", UUID.class),
                nodo(r.getString("valor_propuesto"))));
  }

  public boolean dependenciasInvalidas(UUID propuesta) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            select exists(
              select 1 from uml_colaborativo.dependencia_operacion_propuesta d
              join uml_colaborativo.operacion_propuesta o on o.id=d.id_operacion
              join uml_colaborativo.operacion_propuesta requerida on requerida.id=d.id_operacion_requerida
              where o.id_propuesta=:propuesta and o.esta_seleccionado
                and (not requerida.esta_seleccionado or requerida.estado<>'VALIDO')
            )
            """,
            Map.of("propuesta", propuesta),
            Boolean.class));
  }

  public boolean operacionesSeleccionadasInvalidas(UUID propuesta) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            select exists(
              select 1 from uml_colaborativo.operacion_propuesta
              where id_propuesta=:propuesta and esta_seleccionado and estado<>'VALIDO'
            )
            """,
            Map.of("propuesta", propuesta),
            Boolean.class));
  }

  public void desactualizar(UUID propuesta) {
    jdbc.update(
        "update uml_colaborativo.propuesta_inteligente set estado='DESACTUALIZADO'"
            + " where id=:id and estado='LISTO'",
        Map.of("id", propuesta));
  }

  public void marcarAplicada(UUID propuesta, UUID actor, UUID version) {
    jdbc.update(
        "update uml_colaborativo.operacion_propuesta set estado=case when"
            + " esta_seleccionado then 'APLICADO' else 'RECHAZADO' end where"
            + " id_propuesta=:id",
        Map.of("id", propuesta));
    int filas =
        jdbc.update(
            """
            update uml_colaborativo.propuesta_inteligente
            set estado='APLICADO',revisado_por=:actor,revisado_en=clock_timestamp(),
                aplicado_por=:actor,id_version_aplicada=:version,aplicado_en=clock_timestamp()
            where id=:id and estado='LISTO'
            """,
            Map.of("id", propuesta, "actor", actor, "version", version));
    if (filas != 1) throw new ApiException(409, "La propuesta ya fue procesada.");
  }

  public void rechazar(UUID propuesta, UUID actor, String motivo) {
    jdbc.update(
        "update uml_colaborativo.operacion_propuesta set estado='RECHAZADO' where"
            + " id_propuesta=:id",
        Map.of("id", propuesta));
    int filas =
        jdbc.update(
            """
            update uml_colaborativo.propuesta_inteligente
            set estado='RECHAZADO',revisado_por=:actor,revisado_en=clock_timestamp(),
                rechazado_por=:actor,rechazado_en=clock_timestamp(),motivo_rechazo=:motivo
            where id=:id and estado='LISTO'
            """,
            new MapSqlParameterSource()
                .addValue("id", propuesta)
                .addValue("actor", actor)
                .addValue("motivo", motivo));
    if (filas != 1) throw new ApiException(409, "La propuesta ya fue procesada.");
  }

  private JsonNode nodo(String valor) {
    if (valor == null) return null;
    try {
      return json.readTree(valor);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON persistido inválido", e);
    }
  }

  private String textoJson(JsonNode valor) {
    return valor == null || valor.isNull() ? null : valor.toString();
  }

  private String justificacion(JsonNode mensajes) {
    if (mensajes == null || !mensajes.isArray()) return null;
    for (JsonNode mensaje : mensajes)
      if ("JUSTIFICACION".equals(mensaje.path("tipo").asText()))
        return mensaje.path("mensaje").asText(null);
    return null;
  }

  private String sha256(byte[] contenido) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no está disponible", e);
    }
  }
}
