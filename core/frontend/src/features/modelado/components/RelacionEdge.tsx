import { useState, useEffect, type KeyboardEvent } from 'react';
import {
  BaseEdge,
  getSmoothStepPath,
  EdgeLabelRenderer,
  Position,
  type EdgeProps,
  type Edge,
} from '@xyflow/react';
import { multiplicidad, type Relacion } from '../api/modeladoApi';
import {
  PRESETS_MULTIPLICIDAD,
  detectarPreset,
  detectarExtremo,
} from '../constants/CatalogoMultiplicidades';

export type LadoConexion = 'TOP' | 'RIGHT' | 'BOTTOM' | 'LEFT';

export function detectarLado(handleId?: string | null): LadoConexion | null {
  if (!handleId) return null;
  if (handleId.startsWith('top')) return 'TOP';
  if (handleId.startsWith('right') || handleId === 'salida') return 'RIGHT';
  if (handleId.startsWith('bottom')) return 'BOTTOM';
  if (handleId.startsWith('left') || handleId === 'entrada') return 'LEFT';
  return null;
}

export function detectarPuerto(handleId?: string | null): number {
  if (!handleId) return 3;
  if (
    handleId === 'salida' ||
    handleId === 'entrada' ||
    handleId === 'top-source' ||
    handleId === 'top-target' ||
    handleId === 'bottom-source' ||
    handleId === 'bottom-target'
  ) {
    return 3;
  }
  const match = handleId.match(/-(1|2|3|4|5)-/);
  return match ? parseInt(match[1], 10) : 3;
}

export type RelacionAristaData = {
  relacion: Relacion;
  puedeEditar?: boolean;
  alCambiarTipo?: (id: string, tipo: string, nombre?: string | null) => void;
  alCambiarNombre?: (id: string, nuevoNombre: string | null) => void;
  alCambiarMultiplicidad?: (
    id: string,
    minO: number,
    maxO: number | null,
    minD: number,
    maxD: number | null,
  ) => void;
  alCrearClaseIntermedia?: (idRelacion: string) => void;
  alEliminar?: (id: string) => void;
  alCambiarPuerto?: (
    idRelacion: string,
    extremo: 'ORIGEN' | 'DESTINO',
    lado: LadoConexion,
    puertoIndex?: number,
  ) => void;
  sourceHandleActual?: string;
  targetHandleActual?: string;
};

export type RelacionArista = Edge<RelacionAristaData, 'uml'>;

function calcularOffsetExtremo(x: number, y: number, pos?: Position) {
  switch (pos) {
    case Position.Right:
      return { x: x + 42, y: y - 14 };
    case Position.Left:
      return { x: x - 42, y: y - 14 };
    case Position.Bottom:
      return { x: x + 20, y: y + 26 };
    case Position.Top:
      return { x: x + 20, y: y - 26 };
    default:
      return { x: x + 35, y: y - 14 };
  }
}

export function RelacionEdge(props: EdgeProps<RelacionArista>) {
  const r = props.data!.relacion;
  const puedeEditar = props.data?.puedeEditar ?? false;
  const alCambiarTipo = props.data?.alCambiarTipo;
  const alCambiarNombre = props.data?.alCambiarNombre;
  const alCambiarMultiplicidad = props.data?.alCambiarMultiplicidad;
  const alCrearClaseIntermedia = props.data?.alCrearClaseIntermedia;
  const alEliminar = props.data?.alEliminar;
  const alCambiarPuerto = props.data?.alCambiarPuerto;
  const sourceHandleActual = props.data?.sourceHandleActual ?? props.sourceHandleId;
  const targetHandleActual = props.data?.targetHandleActual ?? props.targetHandleId;

  const [editandoExtremo, setEditandoExtremo] = useState<'ORIGEN' | 'DESTINO' | null>(null);
  const [valorExtremo, setValorExtremo] = useState<string>('1..1');

  // Estado para edición interactiva del nombre de la relación
  const [editandoNombre, setEditandoNombre] = useState(false);
  const [nombreRel, setNombreRel] = useState(r.nombre ?? '');

  useEffect(() => {
    setNombreRel(r.nombre ?? '');
  }, [r.nombre]);

  const [path, x, y] = getSmoothStepPath({
    ...props,
    borderRadius: 4,
  });
  const base = 'uml-' + props.id;

  const posOrigen = calcularOffsetExtremo(props.sourceX, props.sourceY, props.sourcePosition);
  const posDestino = calcularOffsetExtremo(props.targetX, props.targetY, props.targetPosition);

  const diamond = r.tipo_relacion === 'AGREGACION' || r.tipo_relacion === 'COMPOSICION';
  const general = r.tipo_relacion === 'GENERALIZACION';
  const depend = r.tipo_relacion === 'DEPENDENCIA';
  const estructural = !general && !depend;

  const forward =
    general || depend || r.direccion === 'ORIGEN_A_DESTINO' || r.direccion === 'BIDIRECCIONAL';
  const backward = estructural && (r.direccion === 'DESTINO_A_ORIGEN' || r.direccion === 'BIDIRECCIONAL');


  // Detectar preset de multiplicidad y si es N:M
  const presetActual = detectarPreset(
    r.minimo_origen,
    r.maximo_origen,
    r.minimo_destino,
    r.maximo_destino,
  );
  const esMuchosAMuchos =
    estructural &&
    (r.maximo_origen === null || (r.maximo_origen !== null && r.maximo_origen > 1)) &&
    (r.maximo_destino === null || (r.maximo_destino !== null && r.maximo_destino > 1));

  function iniciarEdicion(extremo: 'ORIGEN' | 'DESTINO') {
    setEditandoExtremo(extremo);
    if (extremo === 'ORIGEN') {
      setValorExtremo(detectarExtremo(r.minimo_origen, r.maximo_origen));
    } else {
      setValorExtremo(detectarExtremo(r.minimo_destino, r.maximo_destino));
    }
  }

  function confirmarCambioExtremo() {
    if (!alCambiarMultiplicidad || !editandoExtremo) {
      setEditandoExtremo(null);
      return;
    }

    let min = 1;
    let max: number | null = 1;

    if (valorExtremo === '0..1') {
      min = 0;
      max = 1;
    } else if (valorExtremo === '1..1') {
      min = 1;
      max = 1;
    } else if (valorExtremo === '0..*') {
      min = 0;
      max = null;
    } else if (valorExtremo === '1..*') {
      min = 1;
      max = null;
    } else {
      setEditandoExtremo(null);
      return;
    }

    if (editandoExtremo === 'ORIGEN') {
      alCambiarMultiplicidad(r.id, min, max, r.minimo_destino ?? 1, r.maximo_destino);
    } else if (editandoExtremo === 'DESTINO') {
      alCambiarMultiplicidad(r.id, r.minimo_origen ?? 1, r.maximo_origen, min, max);
    }
    setEditandoExtremo(null);
  }

  function cancelarCambioExtremo() {
    setEditandoExtremo(null);
  }

  function manejarKeyDown(e: KeyboardEvent<HTMLElement>) {
    if (e.key === 'Enter') {
      e.preventDefault();
      e.stopPropagation();
      confirmarCambioExtremo();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      cancelarCambioExtremo();
    }
  }

  function confirmarNombre() {
    const trimmed = nombreRel.trim();
    if (alCambiarNombre) {
      alCambiarNombre(r.id, trimmed || null);
    } else if (alCambiarTipo) {
      alCambiarTipo(r.id, r.tipo_relacion, trimmed || null);
    }
    setEditandoNombre(false);
  }

  function cancelarNombre() {
    setNombreRel(r.nombre ?? '');
    setEditandoNombre(false);
  }

  function manejarKeyDownNombre(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      e.preventDefault();
      e.stopPropagation();
      confirmarNombre();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      cancelarNombre();
    }
  }

  return (
    <>
      <g>
        <defs>
          {/* Marcador de Generalización / Dependencia / Flecha de navegación */}
          <marker
            id={base + '-fin'}
            viewBox="0 0 20 20"
            refX="19"
            refY="10"
            markerWidth="16"
            markerHeight="16"
            orient="auto-start-reverse"
          >
            <path
              d={general ? 'M1 2 L19 10 L1 18 Z' : 'M2 2 L18 10 L2 18'}
              fill={general ? '#ffffff' : 'none'}
              stroke={props.selected ? '#1b5d4c' : '#1e293b'}
              strokeWidth={general ? '1.8' : '2'}
            />
          </marker>

          {/* Marcador de Composición (Rombo negro) o Agregación (Rombo blanco) */}
          <marker
            id={base + '-inicio'}
            viewBox="0 0 26 20"
            refX="1"
            refY="10"
            markerWidth="19"
            markerHeight="17"
            orient="auto"
          >
            <path
              d={diamond ? 'M1 10 L13 2 L25 10 L13 18 Z' : 'M20 2 L2 10 L20 18'}
              fill={
                diamond
                  ? r.tipo_relacion === 'COMPOSICION'
                    ? '#0f172a' // Rombo negro sólido para composición
                    : '#ffffff' // Rombo blanco hueco para agregación
                  : 'none'
              }
              stroke={props.selected ? '#1b5d4c' : '#0f172a'}
              strokeWidth="1.8"
            />
          </marker>
        </defs>

        {/* Línea de la relación */}
        <BaseEdge
          path={path}
          id={props.id}
          markerStart={diamond || backward ? 'url(#' + base + '-inicio)' : undefined}
          markerEnd={forward ? 'url(#' + base + '-fin)' : undefined}
          style={{
            stroke: props.selected ? '#1b5d4c' : '#334155',
            strokeWidth: props.selected ? 2.4 : 1.8,
            strokeDasharray: r.tipo_relacion === 'DEPENDENCIA' ? '6 4' : undefined,
          }}
        />

        {/* Estereotipo para dependencia si no tiene nombre */}
        {r.tipo_relacion === 'DEPENDENCIA' && !r.nombre && (
          <text x={x} y={y - 12} textAnchor="middle" className="edge-label">
            dependencia
          </text>
        )}
      </g>

      {/* Renderizado de distintivo de herencia, barras de herramientas y cardinalidades interactivas en extremos */}
      <EdgeLabelRenderer>
        {/* Cardinalidad en Extremo Origen */}
        {estructural && (
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${posOrigen.x}px, ${posOrigen.y}px)`,
              pointerEvents: 'all',
              zIndex: editandoExtremo === 'ORIGEN' ? 10002 : 1001,
            }}
            className="nodrag nopan"
          >
            {editandoExtremo === 'ORIGEN' ? (
              <div className="endpoint-multiplicity-editor" onClick={(e) => e.stopPropagation()}>
                <select
                  autoFocus
                  className="endpoint-select"
                  value={valorExtremo}
                  onChange={(e) => setValorExtremo(e.target.value)}
                  onKeyDown={manejarKeyDown}
                  title="Seleccionar cardinalidad origen (Enter para guardar)"
                >
                  <option value="0..1">0..1</option>
                  <option value="1..1">1..1</option>
                  <option value="0..*">0..* (*)</option>
                  <option value="1..*">1..*</option>
                  {valorExtremo === 'custom' && (
                    <option value="custom">
                      {multiplicidad(r.minimo_origen, r.maximo_origen)}
                    </option>
                  )}
                </select>
                <button
                  type="button"
                  className="endpoint-btn-check"
                  onClick={(e) => {
                    e.stopPropagation();
                    confirmarCambioExtremo();
                  }}
                  title="Guardar cardinalidad origen (Enter)"
                >
                  ✓
                </button>
                <button
                  type="button"
                  className="endpoint-btn-cancel"
                  onClick={(e) => {
                    e.stopPropagation();
                    cancelarCambioExtremo();
                  }}
                  title="Descartar (Escape)"
                >
                  ✕
                </button>
              </div>
            ) : (
              <button
                type="button"
                className={`endpoint-multiplicity-badge ${props.selected ? 'badge-selected' : ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  if (puedeEditar) iniciarEdicion('ORIGEN');
                }}
                title={
                  puedeEditar
                    ? 'Clic para cambiar cardinalidad en origen'
                    : `Cardinalidad origen: ${multiplicidad(r.minimo_origen, r.maximo_origen)}`
                }
              >
                {r.rol_origen ? r.rol_origen + ' ' : ''}{multiplicidad(r.minimo_origen, r.maximo_origen)}
                {puedeEditar && <span className="endpoint-edit-hint">✎</span>}
              </button>
            )}
          </div>
        )}

        {/* Cardinalidad en Extremo Destino */}
        {estructural && (
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${posDestino.x}px, ${posDestino.y}px)`,
              pointerEvents: 'all',
              zIndex: editandoExtremo === 'DESTINO' ? 10002 : 1001,
            }}
            className="nodrag nopan"
          >
            {editandoExtremo === 'DESTINO' ? (
              <div className="endpoint-multiplicity-editor" onClick={(e) => e.stopPropagation()}>
                <select
                  autoFocus
                  className="endpoint-select"
                  value={valorExtremo}
                  onChange={(e) => setValorExtremo(e.target.value)}
                  onKeyDown={manejarKeyDown}
                  title="Seleccionar cardinalidad destino (Enter para guardar)"
                >
                  <option value="0..1">0..1</option>
                  <option value="1..1">1..1</option>
                  <option value="0..*">0..* (*)</option>
                  <option value="1..*">1..*</option>
                  {valorExtremo === 'custom' && (
                    <option value="custom">
                      {multiplicidad(r.minimo_destino, r.maximo_destino)}
                    </option>
                  )}
                </select>
                <button
                  type="button"
                  className="endpoint-btn-check"
                  onClick={(e) => {
                    e.stopPropagation();
                    confirmarCambioExtremo();
                  }}
                  title="Guardar cardinalidad destino (Enter)"
                >
                  ✓
                </button>
                <button
                  type="button"
                  className="endpoint-btn-cancel"
                  onClick={(e) => {
                    e.stopPropagation();
                    cancelarCambioExtremo();
                  }}
                  title="Descartar (Escape)"
                >
                  ✕
                </button>
              </div>
            ) : (
              <button
                type="button"
                className={`endpoint-multiplicity-badge ${props.selected ? 'badge-selected' : ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  if (puedeEditar) iniciarEdicion('DESTINO');
                }}
                title={
                  puedeEditar
                    ? 'Clic para cambiar cardinalidad en destino'
                    : `Cardinalidad destino: ${multiplicidad(r.minimo_destino, r.maximo_destino)}`
                }
              >
                {r.rol_destino ? r.rol_destino + ' ' : ''}{multiplicidad(r.minimo_destino, r.maximo_destino)}
                {puedeEditar && <span className="endpoint-edit-hint">✎</span>}
              </button>
            )}
          </div>
        )}

        {/* Menú central, nombre editable y distintivo de herencia */}
        <div
          style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${x}px,${y}px)`,
            pointerEvents: 'all',
            zIndex: props.selected || editandoNombre ? 10002 : 1001,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
          }}
          className="nodrag nopan"
        >
          {/* Editor inline o Badge interactivo del Nombre de la Relación */}
          {editandoNombre ? (
            <div className="edge-name-editor" onClick={(e) => e.stopPropagation()}>
              <input
                autoFocus
                className="edge-name-input"
                value={nombreRel}
                placeholder="Nombre de relación…"
                onChange={(e) => setNombreRel(e.target.value)}
                onKeyDown={manejarKeyDownNombre}
              />
              <button
                type="button"
                className="endpoint-btn-check"
                onClick={(e) => {
                  e.stopPropagation();
                  confirmarNombre();
                }}
                title="Guardar nombre (Enter)"
              >
                ✓
              </button>
              <button
                type="button"
                className="endpoint-btn-cancel"
                onClick={(e) => {
                  e.stopPropagation();
                  cancelarNombre();
                }}
                title="Descartar (Escape)"
              >
                ✕
              </button>
            </div>
          ) : r.nombre ? (
            <button
              type="button"
              className="edge-name-badge"
              onClick={(e) => {
                e.stopPropagation();
                if (puedeEditar) {
                  setNombreRel(r.nombre || '');
                  setEditandoNombre(true);
                }
              }}
              title={puedeEditar ? 'Clic para editar el nombre de la relación' : undefined}
            >
              {r.nombre}
              {puedeEditar && <span className="endpoint-edit-hint">✎</span>}
            </button>
          ) : null}

          {/* Menú flotante cuando la arista está seleccionada */}
          {props.selected && puedeEditar && (
            <div className="edge-quick-toolbar" aria-label="Edición rápida de relación">
              {/* Selector de Tipo de Relación */}
              <select
                className="edge-type-select"
                value={r.tipo_relacion}
                onChange={(e) => {
                  alCambiarTipo?.(r.id, e.target.value, r.nombre);
                }}
                title="Cambiar tipo de relación"
              >
                <option value="ASOCIACION">— Asociación</option>
                <option value="COMPOSICION">◆ Composición (Rombo negro)</option>
                <option value="AGREGACION">◇ Agregación (Rombo blanco)</option>
                <option value="GENERALIZACION">▷ Generalización / Herencia</option>
                <option value="DEPENDENCIA">⇢ Dependencia</option>
              </select>

              {/* Botón para editar / añadir nombre de la relación */}
              {!editandoNombre && (
                <button
                  type="button"
                  className="edge-name-quick-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    setNombreRel(r.nombre || '');
                    setEditandoNombre(true);
                  }}
                  title="Editar nombre de la relación"
                >
                  {r.nombre ? (
                    <>
                      Nombre: <b>{r.nombre}</b> ✎
                    </>
                  ) : (
                    '＋ Nombre'
                  )}
                </button>
              )}

              {/* Botones para editar cardinalidad en cada extremo */}
              {estructural && (
                <div className="edge-endpoints-quick">
                  <button
                    type="button"
                    className="edge-endpoint-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      iniciarEdicion('ORIGEN');
                    }}
                    title="Asignar cardinalidad en origen"
                  >
                    Origen: <b>{multiplicidad(r.minimo_origen, r.maximo_origen)}</b> ✎
                  </button>
                  <button
                    type="button"
                    className="edge-endpoint-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      iniciarEdicion('DESTINO');
                    }}
                    title="Asignar cardinalidad en destino"
                  >
                    Destino: <b>{multiplicidad(r.minimo_destino, r.maximo_destino)}</b> ✎
                  </button>
                </div>
              )}

              {/* Selector de Multiplicidad rápida (preset global) */}
              {estructural && (
                <select
                  className="edge-multiplicity-select"
                  value={presetActual}
                  onChange={(e) => {
                    const preset = PRESETS_MULTIPLICIDAD.find((p) => p.id === e.target.value);
                    if (preset && alCambiarMultiplicidad) {
                      alCambiarMultiplicidad(
                        r.id,
                        preset.minO,
                        preset.maxO,
                        preset.minD,
                        preset.maxD,
                      );
                    }
                  }}
                  title="Seleccionar esquema global (1:1, 1:N, N:M...)"
                >
                  {PRESETS_MULTIPLICIDAD.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.label}
                    </option>
                  ))}
                  {presetActual === 'custom' && <option value="custom">Personalizada</option>}
                </select>
              )}

              {/* Botón para crear clase intermedia si es relación N:M o asociación */}
              {estructural && esMuchosAMuchos && alCrearClaseIntermedia && (
                <button
                  type="button"
                  className="edge-assoc-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    alCrearClaseIntermedia(r.id);
                  }}
                  title="Crear una Clase Intermedia / de Asociación para resolver esta relación Muchos a Muchos"
                >
                  ⚡ Clase Intermedia
                </button>
              )}

              {/* Selector de lados / puertos para mover la relación alrededor de la tabla */}
              <div
                className="edge-endpoints-sides"
                title="Mover extremos de la relación alrededor de la tabla (o arrastra el extremo de la línea)"
              >
                <span className="edge-side-label">⤹ Origen:</span>
                <select
                  className="edge-side-select"
                  value={detectarLado(sourceHandleActual) ?? 'RIGHT'}
                  disabled={!puedeEditar}
                  onChange={(e) => {
                    const nuevoLado = e.target.value as LadoConexion;
                    const puertoActual = detectarPuerto(sourceHandleActual);
                    alCambiarPuerto?.(r.id, 'ORIGEN', nuevoLado, puertoActual);
                  }}
                  title="Lado de conexión de la clase origen"
                >
                  <option value="TOP">Arriba</option>
                  <option value="RIGHT">Derecha</option>
                  <option value="BOTTOM">Abajo</option>
                  <option value="LEFT">Izquierda</option>
                </select>
                <select
                  className="edge-port-select"
                  value={detectarPuerto(sourceHandleActual)}
                  disabled={!puedeEditar}
                  onChange={(e) => {
                    const ladoActual = detectarLado(sourceHandleActual) ?? 'RIGHT';
                    alCambiarPuerto?.(r.id, 'ORIGEN', ladoActual, parseInt(e.target.value, 10));
                  }}
                  title="Posición a lo largo del borde origen (1 a 5)"
                >
                  <option value={1}>P1 (16%)</option>
                  <option value={2}>P2 (33%)</option>
                  <option value={3}>P3 (50%)</option>
                  <option value={4}>P4 (67%)</option>
                  <option value={5}>P5 (84%)</option>
                </select>

                <span className="edge-side-label">Destino ⤸:</span>
                <select
                  className="edge-side-select"
                  value={detectarLado(targetHandleActual) ?? 'LEFT'}
                  disabled={!puedeEditar}
                  onChange={(e) => {
                    const nuevoLado = e.target.value as LadoConexion;
                    const puertoActual = detectarPuerto(targetHandleActual);
                    alCambiarPuerto?.(r.id, 'DESTINO', nuevoLado, puertoActual);
                  }}
                  title="Lado de conexión de la clase destino"
                >
                  <option value="TOP">Arriba</option>
                  <option value="RIGHT">Derecha</option>
                  <option value="BOTTOM">Abajo</option>
                  <option value="LEFT">Izquierda</option>
                </select>
                <select
                  className="edge-port-select"
                  value={detectarPuerto(targetHandleActual)}
                  disabled={!puedeEditar}
                  onChange={(e) => {
                    const ladoActual = detectarLado(targetHandleActual) ?? 'LEFT';
                    alCambiarPuerto?.(r.id, 'DESTINO', ladoActual, parseInt(e.target.value, 10));
                  }}
                  title="Posición a lo largo del borde destino (1 a 5)"
                >
                  <option value={1}>P1 (16%)</option>
                  <option value={2}>P2 (33%)</option>
                  <option value={3}>P3 (50%)</option>
                  <option value={4}>P4 (67%)</option>
                  <option value={5}>P5 (84%)</option>
                </select>
              </div>

              {alEliminar && (
                <button
                  type="button"
                  className="edge-delete-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    alEliminar(r.id);
                  }}
                  title="Eliminar relación"
                >
                  ×
                </button>
              )}
            </div>
          )}
        </div>
      </EdgeLabelRenderer>
    </>
  );
}
