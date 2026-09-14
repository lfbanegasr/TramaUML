import { useState, type FormEvent } from 'react';
import type { Accion, Modelo, Clase, Atributo, Operacion, Relacion } from '../api/modeladoApi';
export type Borrador = {
  entidad: Accion['entidad']; id: string; nuevo: boolean; idClase?: string;
  origen?: string; destino?: string; clase?: Clase; atributo?: Atributo; operacion?: Operacion; relacion?: Relacion;
};
type ParametroEditable = { id: string; nombre: string; tipoUml: string; direccion: string; esObligatorio: boolean; valorPredeterminado: string };
const tipos: Record<string,string> = { CLASE:'clase', ATRIBUTO:'atributo', OPERACION:'operación', RELACION:'relación' };
const maximo = (v: FormDataEntryValue | null) => v === '*' ? null : Number(v);
export function FormularioElemento({ borrador:b, modelo, bloqueado, cancelar, guardar }: {
  borrador:Borrador; modelo:Modelo; bloqueado:boolean; cancelar:()=>void; guardar:(a:Accion)=>Promise<boolean>;
}) {
  const [error, setError] = useState('');
  const [tipoRelacion, setTipoRelacion] = useState(b.relacion?.tipo_relacion ?? 'ASOCIACION');
  const [parametros, setParametros] = useState<ParametroEditable[]>(modelo.parametros.filter(p=>p.id_operacion===b.id).map(p=>({
    id:p.id,nombre:p.nombre,tipoUml:p.tipo_uml,direccion:p.direccion,esObligatorio:p.es_obligatorio,valorPredeterminado:p.valor_predeterminado ?? ''
  })));
  function parametro(id:string, datos:Partial<ParametroEditable>) {setParametros(ps=>ps.map(p=>p.id===id?{...p,...datos}:p));}
  async function enviar(e:FormEvent<HTMLFormElement>) {
    e.preventDefault();setError('');const f=new FormData(e.currentTarget);
    let datos:Record<string,unknown>;
    if(b.entidad==='CLASE') datos={
      nombre:String(f.get('nombre')),descripcion:String(f.get('descripcion')),estereotipo:String(f.get('estereotipo')),
      visibilidad:String(f.get('visibilidad')),esAbstracto:f.get('abstracto')==='on',esPersistente:f.get('persistente')==='on',
      ...(b.nuevo?{x:modelo.clases.length*300,y:100}:{})
    };
    else if(b.entidad==='ATRIBUTO') {
      let restricciones:unknown;
      try {restricciones=JSON.parse(String(f.get('restricciones'))||'{}');}
      catch {setError('Las restricciones deben ser un objeto JSON válido.');return;}
      if(!restricciones || typeof restricciones!=='object' || Array.isArray(restricciones)) {setError('Las restricciones deben ser un objeto JSON.');return;}
      datos={idClase:b.idClase,nombre:String(f.get('nombre')),tipoUml:String(f.get('tipo')),visibilidad:String(f.get('visibilidad')),
        minimo:Number(f.get('minimo')),maximo:maximo(f.get('maximo')),esIdentificador:f.get('identificador')==='on',
        esEstatico:f.get('estatico')==='on',esUnico:f.get('unico')==='on',valorPredeterminado:String(f.get('predeterminado')),restricciones};
    } else if(b.entidad==='OPERACION') datos={
      idClase:b.idClase,nombre:String(f.get('nombre')),tipoRetorno:String(f.get('retorno'))||null,visibilidad:String(f.get('visibilidad')),
      esEstatico:f.get('estatico')==='on',esAbstracto:f.get('abstracto')==='on',parametros
    };
    else datos={origen:String(f.get('origen')),destino:String(f.get('destino')),nombre:String(f.get('nombre')),tipo:tipoRelacion,
      direccion:String(f.get('direccion')),rolOrigen:String(f.get('rolOrigen')),rolDestino:String(f.get('rolDestino')),
      ...(!['GENERALIZACION','DEPENDENCIA'].includes(tipoRelacion)?{
        minimoOrigen:Number(f.get('minimoOrigen')),maximoOrigen:maximo(f.get('maximoOrigen')),
        minimoDestino:Number(f.get('minimoDestino')),maximoDestino:maximo(f.get('maximoDestino'))}:{})
    };
    await guardar({operacion:b.nuevo?'CREAR':'ACTUALIZAR',entidad:b.entidad,idEntidad:b.id,datos});
  }
  return <form onSubmit={enviar} aria-label="Propiedades del elemento">
    <h2>{b.nuevo?'Nueva ':'Editar '}{tipos[b.entidad]}</h2><fieldset disabled={bloqueado}>
      <label>Nombre<input name="nombre" required={b.entidad!=='RELACION'} maxLength={180}
        defaultValue={b.clase?.nombre ?? b.atributo?.nombre ?? b.operacion?.nombre ?? b.relacion?.nombre ?? ''} autoFocus /></label>
      {b.entidad==='CLASE' && <>
        <label>Descripción<textarea name="descripcion" maxLength={4000} defaultValue={b.clase?.descripcion ?? ''} /></label>
        <label>Estereotipo<input name="estereotipo" maxLength={100} defaultValue={b.clase?.estereotipo ?? ''} /></label>
        <Visibilidad valor={b.clase?.visibilidad ?? 'PUBLICO'} />
        <Check nombre="abstracto" label="Clase abstracta" valor={b.clase?.es_abstracto} />
        <Check nombre="persistente" label="Entidad persistente" valor={b.clase?.es_persistente ?? true} />
      </>}
      {b.entidad==='ATRIBUTO' && <>
        <label>Tipo UML<input name="tipo" required maxLength={180} defaultValue={b.atributo?.tipo_uml} placeholder="String, Integer, UUID…" /></label>
        <Visibilidad valor={b.atributo?.visibilidad ?? 'PRIVADO'} />
        <div className="field-pair"><Numero nombre="minimo" label="Mínimo" valor={b.atributo?.multiplicidad_minima ?? 1} />
          <Numero nombre="maximo" label="Máximo (* ilimitado)" ilimitado valor={b.atributo?b.atributo.multiplicidad_maxima:1} /></div>
        <Check nombre="identificador" label="Identificador" valor={b.atributo?.es_identificador} />
        <Check nombre="estatico" label="Estático" valor={b.atributo?.es_estatico} />
        <Check nombre="unico" label="Único" valor={b.atributo?.es_unico} />
        <label>Valor predeterminado<input name="predeterminado" maxLength={4000} defaultValue={b.atributo?.valor_predeterminado ?? ''} /></label>
        <label>Restricciones UML (JSON)<textarea name="restricciones" maxLength={8000} defaultValue={b.atributo?.restricciones_json ?? '{}'} /></label>
      </>}
      {b.entidad==='OPERACION' && <>
        <label>Tipo de retorno<input name="retorno" maxLength={180} defaultValue={b.operacion?.tipo_retorno ?? ''} placeholder="void, String…" /></label>
        <Visibilidad valor={b.operacion?.visibilidad ?? 'PUBLICO'} />
        <Check nombre="estatico" label="Estática" valor={b.operacion?.es_estatico} />
        <Check nombre="abstracto" label="Abstracta" valor={b.operacion?.es_abstracto} /><h3>Parámetros</h3>
        {parametros.map((p,i)=><section className="parameter-card" key={p.id} aria-label={'Parámetro '+(i+1)}>
          <label>Nombre del parámetro<input required maxLength={180} value={p.nombre} onChange={e=>parametro(p.id,{nombre:e.target.value})} /></label>
          <label>Tipo del parámetro<input required maxLength={180} value={p.tipoUml} onChange={e=>parametro(p.id,{tipoUml:e.target.value})} /></label>
          <label>Dirección<select aria-label="Dirección del parámetro" value={p.direccion} onChange={e=>parametro(p.id,{direccion:e.target.value})}>
            <option value="ENTRADA">Entrada</option><option value="SALIDA">Salida</option><option value="ENTRADA_SALIDA">Entrada y salida</option><option value="RETORNO">Retorno</option>
          </select></label>
          <label className="check"><input type="checkbox" checked={p.esObligatorio} onChange={e=>parametro(p.id,{esObligatorio:e.target.checked})} /> Obligatorio</label>
          <label>Valor predeterminado del parámetro<input maxLength={4000} value={p.valorPredeterminado} onChange={e=>parametro(p.id,{valorPredeterminado:e.target.value})} /></label>
          <button type="button" onClick={()=>setParametros(ps=>ps.filter(v=>v.id!==p.id))}>Quitar parámetro {i+1}</button>
        </section>)}
        <button type="button" disabled={parametros.length>=100} onClick={()=>setParametros(ps=>[...ps,{
          id:crypto.randomUUID(),nombre:'',tipoUml:'String',direccion:'ENTRADA',esObligatorio:true,valorPredeterminado:''
        }])}>＋ Añadir parámetro</button>
      </>}
      {b.entidad==='RELACION' && <>
        <label>Clase origen<select aria-label="Clase origen" name="origen" defaultValue={b.relacion?.id_clase_origen ?? b.origen} required>{modelo.clases.map(c=><option key={c.id} value={c.id}>{c.nombre}</option>)}</select></label>
        <label>Clase destino<select aria-label="Clase destino" name="destino" defaultValue={b.relacion?.id_clase_destino ?? b.destino} required>{modelo.clases.map(c=><option key={c.id} value={c.id}>{c.nombre}</option>)}</select></label>
        <label>Tipo de relación<select aria-label="Tipo de relación" value={tipoRelacion} onChange={e=>setTipoRelacion(e.target.value)}>
          <option value="ASOCIACION">Asociación</option><option value="AGREGACION">Agregación</option><option value="COMPOSICION">Composición</option><option value="GENERALIZACION">Generalización</option><option value="DEPENDENCIA">Dependencia</option>
        </select></label>
        <label>Dirección<select aria-label="Dirección de la relación" name="direccion" defaultValue={b.relacion?.direccion ?? 'SIN_DIRECCION'}>
          <option value="SIN_DIRECCION">Sin dirección</option><option value="ORIGEN_A_DESTINO">Origen a destino</option><option value="DESTINO_A_ORIGEN">Destino a origen</option><option value="BIDIRECCIONAL">Bidireccional</option>
        </select></label>
        <label>Rol origen<input name="rolOrigen" maxLength={180} defaultValue={b.relacion?.rol_origen ?? ''} /></label>
        <label>Rol destino<input name="rolDestino" maxLength={180} defaultValue={b.relacion?.rol_destino ?? ''} /></label>
        {!['GENERALIZACION','DEPENDENCIA'].includes(tipoRelacion) && <>
          <div className="field-pair"><Numero nombre="minimoOrigen" label="Mínimo origen" valor={b.relacion?.minimo_origen ?? 1} />
            <Numero nombre="maximoOrigen" label="Máximo origen" ilimitado valor={b.relacion?b.relacion.maximo_origen:1} /></div>
          <div className="field-pair"><Numero nombre="minimoDestino" label="Mínimo destino" valor={b.relacion?.minimo_destino ?? 1} />
            <Numero nombre="maximoDestino" label="Máximo destino" ilimitado valor={b.relacion?b.relacion.maximo_destino:1} /></div>
        </>}
      </>}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="form-actions"><button type="button" onClick={cancelar}>Cancelar</button>
        <button className="primary" disabled={!modelo.puedeEditar}>Guardar elemento</button></div>
    </fieldset>
  </form>;
}
function Visibilidad({valor}:{valor:string}) {return <label>Visibilidad<select aria-label="Visibilidad" name="visibilidad" defaultValue={valor}>
  <option value="PUBLICO">+ Pública</option><option value="PRIVADO">− Privada</option><option value="PROTEGIDO"># Protegida</option><option value="PAQUETE">~ Paquete</option>
</select></label>;}
function Check({nombre,label,valor}:{nombre:string;label:string;valor?:boolean}) {return <label className="check"><input type="checkbox" name={nombre} defaultChecked={valor} /> {label}</label>;}
function Numero({nombre,label,ilimitado=false,valor}:{nombre:string;label:string;ilimitado?:boolean;valor:number|null}) {
  return <label>{label}<input name={nombre} defaultValue={valor===null?'*':String(valor)} required pattern={ilimitado?'[0-9]+|\\*':'[0-9]+'} /></label>;
}
