import { BaseEdge, getSmoothStepPath, type EdgeProps, type Edge } from '@xyflow/react';
import { multiplicidad, type Relacion } from '../api/modeladoApi';
export type RelacionArista = Edge<{ relacion: Relacion }, 'uml'>;
export function RelacionEdge(props: EdgeProps<RelacionArista>) {
  const r = props.data!.relacion;
  const [path, x, y] = getSmoothStepPath(props);
  const base = 'uml-' + props.id;
  const diamond = r.tipo_relacion === 'AGREGACION' || r.tipo_relacion === 'COMPOSICION';
  const general = r.tipo_relacion === 'GENERALIZACION';
  const forward = general || r.tipo_relacion === 'DEPENDENCIA' || r.direccion === 'ORIGEN_A_DESTINO' || r.direccion === 'BIDIRECCIONAL';
  const backward = r.direccion === 'DESTINO_A_ORIGEN' || r.direccion === 'BIDIRECCIONAL';
  return <g><defs>
    <marker id={base + '-fin'} viewBox="0 0 20 20" refX="19" refY="10" markerWidth="15" markerHeight="15" orient="auto-start-reverse"><path d={general ? 'M1 1 L19 10 L1 19 Z' : 'M2 2 L18 10 L2 18'} fill={general ? 'white' : 'none'} stroke="#4b6660" strokeWidth="1.6" /></marker>
    <marker id={base + '-inicio'} viewBox="0 0 24 20" refX="1" refY="10" markerWidth="18" markerHeight="16" orient="auto"><path d={diamond ? 'M1 10 L12 1 L23 10 L12 19 Z' : 'M20 2 L2 10 L20 18'} fill={r.tipo_relacion === 'COMPOSICION' ? '#4b6660' : 'white'} stroke="#4b6660" strokeWidth="1.6" /></marker>
  </defs><BaseEdge path={path} id={props.id} markerStart={diamond || backward ? 'url(#' + base + '-inicio)' : undefined} markerEnd={forward ? 'url(#' + base + '-fin)' : undefined} style={{ stroke: props.selected ? '#217d65' : '#4b6660', strokeWidth: 1.6, strokeDasharray: r.tipo_relacion === 'DEPENDENCIA' ? '6 4' : undefined }} />
    <text x={x} y={y - 12} textAnchor="middle" className="edge-label">{r.nombre || (r.tipo_relacion === 'DEPENDENCIA' ? '«use»' : '')}</text>
    <text x={props.sourceX + 22} y={props.sourceY - 12} className="edge-label">{multiplicidad(r.minimo_origen, r.maximo_origen)}</text>
    <text x={props.targetX - 22} y={props.targetY - 12} textAnchor="end" className="edge-label">{multiplicidad(r.minimo_destino, r.maximo_destino)}</text>
  </g>;
}
