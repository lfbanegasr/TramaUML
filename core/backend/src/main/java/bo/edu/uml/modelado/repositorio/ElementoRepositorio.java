package bo.edu.uml.modelado.repositorio;

import bo.edu.uml.modelado.dto.ModeladoDto.Accion;
import bo.edu.uml.modelado.modelo.ReglasElemento;
import bo.edu.uml.compartido.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static bo.edu.uml.modelado.modelo.ReglasElemento.*;

@Repository
public class ElementoRepositorio {
    private final NamedParameterJdbcTemplate jdbc;
    private final OperacionRepositorio operaciones;
    public ElementoRepositorio(NamedParameterJdbcTemplate jdbc,OperacionRepositorio operaciones) {this.jdbc=jdbc;this.operaciones=operaciones;}
    public void aplicar(UUID modelo,UUID actor,Accion a) {
        var p=new MapSqlParameterSource().addValue("modelo",modelo).addValue("actor",actor).addValue("id",a.idEntidad());
        switch(a.entidad()) {
            case "CLASE" -> clase(a,p);
            case "ATRIBUTO" -> atributo(a,p);
            case "OPERACION" -> operaciones.aplicar(a,p);
            case "RELACION" -> relacion(a,p);
            default -> throw invalido("Elemento no soportado.");
        }
    }
    private void clase(Accion a,MapSqlParameterSource p) {
        JsonNode d=a.datos();
        switch(a.operacion()) {
            case "CREAR","ACTUALIZAR" -> {
                objeto(d,Set.of("nombre","descripcion","estereotipo","visibilidad","esAbstracto","esPersistente","x","y"));
                p.addValue("nombre",texto(d,"nombre",180)).addValue("descripcion",opcional(d,"descripcion",4000))
                 .addValue("estereotipo",opcional(d,"estereotipo",100))
                 .addValue("visibilidad",enumerado(d,"visibilidad","PUBLICO","PUBLICO","PRIVADO","PROTEGIDO","PAQUETE"))
                 .addValue("abstracto",bool(d,"esAbstracto",false)).addValue("persistente",bool(d,"esPersistente",true));
                if(a.operacion().equals("CREAR")) {
                    jdbc.update("insert into uml_colaborativo.clase_uml(id,id_modelo,nombre,descripcion,estereotipo,visibilidad,es_abstracto,es_persistente,creado_por,actualizado_por) values(:id,:modelo,:nombre,:descripcion,:estereotipo,:visibilidad,:abstracto,:persistente,:actor,:actor)",p);
                    p.addValue("x",coordenada(d,"x")).addValue("y",coordenada(d,"y"));
                    jdbc.update("insert into uml_colaborativo.posicion_clase_uml(id_clase,x,y,ancho,alto) values(:id,:x,:y,240,160)",p);
                } else exigir(jdbc.update("update uml_colaborativo.clase_uml set nombre=:nombre,descripcion=:descripcion,estereotipo=:estereotipo,visibilidad=:visibilidad,es_abstracto=:abstracto,es_persistente=:persistente,actualizado_por=:actor where id=:id and id_modelo=:modelo and eliminado_en is null",p));
            }
            case "ELIMINAR" -> {
                sinDatos(d);
                exigir(jdbc.update("update uml_colaborativo.clase_uml set eliminado_en=clock_timestamp(),actualizado_por=:actor where id=:id and id_modelo=:modelo and eliminado_en is null",p));
            }
            case "MOVER" -> {
                objeto(d,Set.of("x","y"));p.addValue("x",coordenada(d,"x")).addValue("y",coordenada(d,"y"));
                exigir(jdbc.update("insert into uml_colaborativo.posicion_clase_uml(id_clase,x,y,ancho,alto) select id,:x,:y,240,160 from uml_colaborativo.clase_uml where id=:id and id_modelo=:modelo and eliminado_en is null on conflict(id_clase) do update set x=excluded.x,y=excluded.y",p));
            }
            default -> throw invalido("Operación inválida.");
        }
    }
    private void atributo(Accion a,MapSqlParameterSource p) {
        if(a.operacion().equals("ELIMINAR")) {
            sinDatos(a.datos());
            exigir(jdbc.update("update uml_colaborativo.atributo_uml a set eliminado_en=clock_timestamp() from uml_colaborativo.clase_uml c where a.id=:id and a.id_clase=c.id and c.id_modelo=:modelo and c.eliminado_en is null and a.eliminado_en is null",p));return;
        }
        if(!Set.of("CREAR","ACTUALIZAR").contains(a.operacion())) throw invalido("Operación inválida.");
        JsonNode d=a.datos();objeto(d,Set.of("idClase","nombre","tipoUml","visibilidad","minimo","maximo","esIdentificador","esEstatico","esUnico","valorPredeterminado","restricciones"));
        JsonNode restricciones=d.path("restricciones");
        if(!restricciones.isMissingNode() && (!restricciones.isObject() || restricciones.toString().length()>8000)) throw invalido("Las restricciones deben ser un objeto JSON de hasta 8000 caracteres.");
        int min=entero(d,"minimo",1);Integer max=maximo(d,"maximo",1);
        boolean id=bool(d,"esIdentificador",false),estatico=bool(d,"esEstatico",false);
        if((max!=null && max<min) || (id && (min!=1 || !Integer.valueOf(1).equals(max) || estatico))) throw invalido("Multiplicidad inválida; un identificador requiere 1..1 y no ser estático.");
        p.addValue("clase",uuid(d,"idClase")).addValue("nombre",texto(d,"nombre",180)).addValue("tipo",texto(d,"tipoUml",180))
         .addValue("visibilidad",enumerado(d,"visibilidad","PRIVADO","PUBLICO","PRIVADO","PROTEGIDO","PAQUETE"))
         .addValue("min",min).addValue("max",max).addValue("identificador",id).addValue("estatico",estatico)
         .addValue("unico",bool(d,"esUnico",false)).addValue("predeterminado",opcional(d,"valorPredeterminado",4000))
         .addValue("restricciones",restricciones.isMissingNode()?"{}":restricciones.toString());
        if(a.operacion().equals("CREAR"))
            exigir(jdbc.update("insert into uml_colaborativo.atributo_uml(id,id_clase,nombre,tipo_uml,visibilidad,multiplicidad_minima,multiplicidad_maxima,es_identificador,es_estatico,es_unico,valor_predeterminado,restricciones_json) select :id,id,:nombre,:tipo,:visibilidad,:min,:max,:identificador,:estatico,:unico,:predeterminado,cast(:restricciones as jsonb) from uml_colaborativo.clase_uml where id=:clase and id_modelo=:modelo and eliminado_en is null",p));
        else exigir(jdbc.update("update uml_colaborativo.atributo_uml a set nombre=:nombre,tipo_uml=:tipo,visibilidad=:visibilidad,multiplicidad_minima=:min,multiplicidad_maxima=:max,es_identificador=:identificador,es_estatico=:estatico,es_unico=:unico,valor_predeterminado=:predeterminado,restricciones_json=cast(:restricciones as jsonb) from uml_colaborativo.clase_uml c where a.id=:id and a.id_clase=:clase and c.id=a.id_clase and c.id_modelo=:modelo and c.eliminado_en is null and a.eliminado_en is null",p));
    }
    private void relacion(Accion a,MapSqlParameterSource p) {
        if(a.operacion().equals("ELIMINAR")) {
            sinDatos(a.datos());
            exigir(jdbc.update("update uml_colaborativo.relacion_uml set eliminado_en=clock_timestamp(),actualizado_por=:actor where id=:id and id_modelo=:modelo and eliminado_en is null",p));return;
        }
        if(!Set.of("CREAR","ACTUALIZAR").contains(a.operacion())) throw invalido("Operación inválida.");
        JsonNode d=a.datos();objeto(d,Set.of("origen","destino","nombre","tipo","direccion","rolOrigen","rolDestino","minimoOrigen","maximoOrigen","minimoDestino","maximoDestino"));
        UUID origen=uuid(d,"origen"),destino=uuid(d,"destino");
        String tipo=enumerado(d,"tipo","ASOCIACION","ASOCIACION","AGREGACION","COMPOSICION","GENERALIZACION","DEPENDENCIA");
        boolean estructural=Set.of("ASOCIACION","AGREGACION","COMPOSICION").contains(tipo);
        Integer minO=estructural?entero(d,"minimoOrigen",1):null,maxO=estructural?maximo(d,"maximoOrigen",1):null;
        Integer minD=estructural?entero(d,"minimoDestino",1):null,maxD=estructural?maximo(d,"maximoDestino",1):null;
        if(estructural && ((maxO!=null && maxO<minO) || (maxD!=null && maxD<minD))) throw invalido("Multiplicidad inválida.");
        if(!estructural && (d.hasNonNull("minimoOrigen") || d.hasNonNull("maximoOrigen") || d.hasNonNull("minimoDestino") || d.hasNonNull("maximoDestino"))) throw invalido("Esta relación no utiliza cardinalidades.");
        p.addValue("origen",origen).addValue("destino",destino).addValue("tipo",tipo)
         .addValue("nombre",opcional(d,"nombre",180)).addValue("direccion",enumerado(d,"direccion","SIN_DIRECCION","SIN_DIRECCION","ORIGEN_A_DESTINO","DESTINO_A_ORIGEN","BIDIRECCIONAL"))
         .addValue("rolO",opcional(d,"rolOrigen",180)).addValue("rolD",opcional(d,"rolDestino",180))
         .addValue("minO",minO).addValue("maxO",maxO).addValue("minD",minD).addValue("maxD",maxD);
        long extremos=jdbc.queryForObject("select count(*) from uml_colaborativo.clase_uml where id in (:origen,:destino) and id_modelo=:modelo and eliminado_en is null",p,Long.class);
        if(extremos!=(origen.equals(destino)?1:2)) throw invalido("Ambos extremos deben ser clases activas de este modelo.");
        if(tipo.equals("GENERALIZACION")) {
            if(origen.equals(destino)) throw invalido("Una clase no puede heredar de sí misma.");
            boolean ciclo=Boolean.TRUE.equals(jdbc.queryForObject("""
                with recursive padres(id) as (
                  select :destino::uuid union
                  select r.id_clase_destino from uml_colaborativo.relacion_uml r join padres p on r.id_clase_origen=p.id
                  where r.id_modelo=:modelo and r.tipo_relacion='GENERALIZACION' and r.eliminado_en is null and r.id<>:id
                ) select exists(select 1 from padres where id=:origen)
                """,p,Boolean.class));
            if(ciclo) throw invalido("La generalización produciría un ciclo.");
        }
        if(tipo.equals("COMPOSICION") && Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from uml_colaborativo.relacion_uml where id_modelo=:modelo and tipo_relacion='COMPOSICION' and id_clase_destino=:destino and eliminado_en is null and id<>:id)",p,Boolean.class)))
            throw invalido("La parte ya pertenece a una composición; revisa su propietario.");
        if(a.operacion().equals("CREAR")) jdbc.update("insert into uml_colaborativo.relacion_uml(id,id_modelo,id_clase_origen,id_clase_destino,nombre,tipo_relacion,direccion,rol_origen,rol_destino,minimo_origen,maximo_origen,minimo_destino,maximo_destino,creado_por,actualizado_por) values(:id,:modelo,:origen,:destino,:nombre,:tipo,:direccion,:rolO,:rolD,:minO,:maxO,:minD,:maxD,:actor,:actor)",p);
        else exigir(jdbc.update("update uml_colaborativo.relacion_uml set id_clase_origen=:origen,id_clase_destino=:destino,nombre=:nombre,tipo_relacion=:tipo,direccion=:direccion,rol_origen=:rolO,rol_destino=:rolD,minimo_origen=:minO,maximo_origen=:maxO,minimo_destino=:minD,maximo_destino=:maxD,actualizado_por=:actor where id=:id and id_modelo=:modelo and eliminado_en is null",p));
    }
    private void exigir(int n) {if(n!=1) throw new ApiException(404,"Elemento no disponible en este modelo.");}
    private void sinDatos(JsonNode d) {if(d!=null && !d.isNull() && (!d.isObject() || !d.isEmpty())) throw invalido("La eliminación no acepta datos adicionales.");}
}
