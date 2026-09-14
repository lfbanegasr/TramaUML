import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import {
  simbolo,
  multiplicidad,
  type Clase,
  type Atributo,
  type Operacion,
  type Parametro,
} from '../api/modeladoApi';
export type ClaseNodo = Node<
  { clase: Clase; atributos: Atributo[]; operaciones: Operacion[]; parametros: Parametro[] },
  'clase'
>;
export function ClaseNode({ data, selected }: NodeProps<ClaseNodo>) {
  const { clase, atributos, operaciones, parametros } = data;
  return (
    <div className={'uml-class' + (selected ? ' selected-class' : '')} data-testid="clase-uml">
      <Handle type="target" position={Position.Left} id="entrada" />
      <Handle type="source" position={Position.Right} id="salida" />
      <div className="uml-class-title">
        <small>
          {clase.estereotipo
            ? '«' + clase.estereotipo + '»'
            : clase.es_abstracto
              ? '«abstract»'
              : 'CLASE'}
        </small>
        <b style={{ fontStyle: clase.es_abstracto ? 'italic' : 'normal' }}>{clase.nombre}</b>
      </div>
      <div className="uml-compartment">
        {atributos.length ? (
          atributos.map((a) => (
            <div key={a.id}>
              <span style={{ textDecoration: a.es_estatico ? 'underline' : 'none' }}>
                {simbolo(a.visibilidad)} {a.nombre}: {a.tipo_uml}
              </span>
              {a.multiplicidad_minima !== 1 || a.multiplicidad_maxima !== 1
                ? ' [' + multiplicidad(a.multiplicidad_minima, a.multiplicidad_maxima) + ']'
                : ''}
              {a.es_identificador ? ' {id}' : ''}
            </div>
          ))
        ) : (
          <span className="uml-placeholder">Sin atributos</span>
        )}
      </div>
      <div className="uml-compartment">
        {operaciones.length ? (
          operaciones.map((o) => (
            <div key={o.id}>
              {simbolo(o.visibilidad)} {o.nombre}(
              {parametros
                .filter((p) => p.id_operacion === o.id)
                .map((p) => p.nombre + ': ' + p.tipo_uml)
                .join(', ')}
              ): {o.tipo_retorno ?? 'void'}
            </div>
          ))
        ) : (
          <span className="uml-placeholder">Sin operaciones</span>
        )}
      </div>
    </div>
  );
}
