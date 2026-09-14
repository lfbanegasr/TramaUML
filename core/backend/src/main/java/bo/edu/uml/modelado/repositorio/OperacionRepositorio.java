package bo.edu.uml.modelado.repositorio;

import bo.edu.uml.modelado.dto.ModeladoDto.Accion;
import bo.edu.uml.compartido.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static bo.edu.uml.modelado.modelo.ReglasElemento.*;

@Repository
public class OperacionRepositorio {
    private final NamedParameterJdbcTemplate jdbc;
    public OperacionRepositorio(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}

    public void aplicar(Accion a,MapSqlParameterSource p) {
        if(a.operacion().equals("ELIMINAR")) {
            if(a.datos()!=null && !a.datos().isNull() && (!a.datos().isObject() || !a.datos().isEmpty())) throw invalido("La eliminación no acepta datos.");
            exigir(jdbc.update("""
                update uml_colaborativo.operacion_uml o set eliminado_en=clock_timestamp()
                from uml_colaborativo.clase_uml c where o.id=:id and c.id=o.id_clase
                and c.id_modelo=:modelo and c.eliminado_en is null and o.eliminado_en is null
                """,p));
            jdbc.update("update uml_colaborativo.parametro_uml set eliminado_en=clock_timestamp() where id_operacion=:id and eliminado_en is null",p);
            return;
        }
        if(!Set.of("CREAR","ACTUALIZAR").contains(a.operacion())) throw invalido("Operación inválida.");
        JsonNode d=a.datos();
        objeto(d,Set.of("idClase","nombre","tipoRetorno","visibilidad","esEstatico","esAbstracto","parametros"));
        JsonNode parametros=d.path("parametros");
        if(!parametros.isArray() || parametros.size()>100) throw invalido("Define una lista de hasta 100 parámetros.");
        Set<UUID> ids=new HashSet<>();
        for(JsonNode parametro:parametros) {
            objeto(parametro,Set.of("id","nombre","tipoUml","direccion","esObligatorio","valorPredeterminado"));
            if(!ids.add(uuid(parametro,"id"))) throw invalido("Los identificadores de parámetros no pueden repetirse.");
        }
        p.addValue("clase",uuid(d,"idClase")).addValue("nombre",texto(d,"nombre",180))
         .addValue("retorno",opcional(d,"tipoRetorno",180))
         .addValue("visibilidad",enumerado(d,"visibilidad","PUBLICO","PUBLICO","PRIVADO","PROTEGIDO","PAQUETE"))
         .addValue("estatico",bool(d,"esEstatico",false)).addValue("abstracto",bool(d,"esAbstracto",false));
        if(a.operacion().equals("CREAR")) exigir(jdbc.update("""
            insert into uml_colaborativo.operacion_uml(id,id_clase,nombre,tipo_retorno,visibilidad,es_estatico,es_abstracto)
            select :id,id,:nombre,:retorno,:visibilidad,:estatico,:abstracto from uml_colaborativo.clase_uml
            where id=:clase and id_modelo=:modelo and eliminado_en is null
            """,p));
        else exigir(jdbc.update("""
            update uml_colaborativo.operacion_uml o set nombre=:nombre,tipo_retorno=:retorno,
            visibilidad=:visibilidad,es_estatico=:estatico,es_abstracto=:abstracto
            from uml_colaborativo.clase_uml c where o.id=:id and o.id_clase=:clase and c.id=o.id_clase
            and c.id_modelo=:modelo and c.eliminado_en is null and o.eliminado_en is null
            """,p));
        // El lote reemplaza los parámetros y su orden en la misma revisión; conserva sus UUID.
        jdbc.update("update uml_colaborativo.parametro_uml set eliminado_en=clock_timestamp() where id_operacion=:id and eliminado_en is null",p);
        int orden=0;
        for(JsonNode parametro:parametros) {
            p.addValue("parametro",uuid(parametro,"id")).addValue("nombreParametro",texto(parametro,"nombre",180))
             .addValue("tipoParametro",texto(parametro,"tipoUml",180))
             .addValue("direccion",enumerado(parametro,"direccion","ENTRADA","ENTRADA","SALIDA","ENTRADA_SALIDA","RETORNO"))
             .addValue("obligatorio",bool(parametro,"esObligatorio",true))
             .addValue("predeterminado",opcional(parametro,"valorPredeterminado",4000)).addValue("orden",orden++);
            exigir(jdbc.update("""
                insert into uml_colaborativo.parametro_uml(id,id_operacion,nombre,tipo_uml,direccion,es_obligatorio,valor_predeterminado,orden)
                values(:parametro,:id,:nombreParametro,:tipoParametro,:direccion,:obligatorio,:predeterminado,:orden)
                on conflict(id) do update set nombre=excluded.nombre,tipo_uml=excluded.tipo_uml,
                direccion=excluded.direccion,es_obligatorio=excluded.es_obligatorio,
                valor_predeterminado=excluded.valor_predeterminado,orden=excluded.orden,eliminado_en=null
                where parametro_uml.id_operacion=excluded.id_operacion
                """,p));
        }
    }
    private void exigir(int n) {if(n!=1) throw new ApiException(404,"Operación o parámetro no disponible en esta clase.");}
}
