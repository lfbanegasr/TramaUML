import { useState, useEffect, Fragment, type KeyboardEvent, type MouseEvent } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import {
  simbolo,
  multiplicidad,
  type Clase,
  type Atributo,
  type Operacion,
  type Parametro,
} from '../api/modeladoApi';
import { TODOS_LOS_TIPOS_BASICOS } from '../constants/CatalogoTipos';

export type ClaseNodoData = {
  clase: Clase;
  atributos: Atributo[];
  operaciones: Operacion[];
  parametros: Parametro[];
  puedeEditar?: boolean;
  alEditarNombreClase?: (idClase: string, nuevoNombre: string) => Promise<boolean>;
  alAgregarAtributoDirecto?: (
    idClase: string,
    datos: { nombre: string; tipoUml: string; visibilidad: string },
  ) => Promise<boolean>;
  alEditarAtributoDirecto?: (
    idAtributo: string,
    idClase: string,
    datos: { nombre: string; tipoUml: string; visibilidad: string },
  ) => Promise<boolean>;
  alEliminarAtributoDirecto?: (idAtributo: string) => Promise<void>;
  esOrigenConector?: boolean;
};

export type ClaseNodo = Node<ClaseNodoData, 'clase'>;

export function ClaseNode({ data, selected }: NodeProps<ClaseNodo>) {
  const {
    clase,
    atributos,
    operaciones,
    parametros,
    puedeEditar,
    alEditarNombreClase,
    alAgregarAtributoDirecto,
    alEditarAtributoDirecto,
    alEliminarAtributoDirecto,
    esOrigenConector,
  } = data;

  // Estado para edición inline del nombre de la clase
  const [editandoNombre, setEditandoNombre] = useState(false);
  const [nombreClase, setNombreClase] = useState(clase.nombre);

  // Estado para añadir atributo inline
  const [agregandoAtributo, setAgregandoAtributo] = useState(false);
  const [nuevoNombreAtrib, setNuevoNombreAtrib] = useState('');
  const [nuevoTipoAtrib, setNuevoTipoAtrib] = useState('String');
  const [nuevaVisAtrib, setNuevaVisAtrib] = useState('PRIVADO');

  // Estado para editar un atributo existente inline
  const [editandoAtributoId, setEditandoAtributoId] = useState<string | null>(null);
  const [editNombreAtrib, setEditNombreAtrib] = useState('');
  const [editTipoAtrib, setEditTipoAtrib] = useState('String');
  const [editVisAtrib, setEditVisAtrib] = useState('PRIVADO');

  useEffect(() => {
    setNombreClase(clase.nombre);
  }, [clase.nombre]);

  // Guardar nombre de clase
  async function guardarNombreClase() {
    const trimmed = nombreClase.trim();
    if (!trimmed || trimmed === clase.nombre || !alEditarNombreClase) {
      setNombreClase(clase.nombre);
      setEditandoNombre(false);
      return;
    }
    const ok = await alEditarNombreClase(clase.id, trimmed);
    if (!ok) {
      setNombreClase(clase.nombre);
    }
    setEditandoNombre(false);
  }

  function manejarKeyDownNombre(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      e.preventDefault();
      void guardarNombreClase();
    } else if (e.key === 'Escape') {
      setNombreClase(clase.nombre);
      setEditandoNombre(false);
    }
  }

  // Guardar nuevo atributo inline
  async function confirmarNuevoAtributo() {
    const trimmed = nuevoNombreAtrib.trim();
    if (!trimmed || !alAgregarAtributoDirecto) return;
    const ok = await alAgregarAtributoDirecto(clase.id, {
      nombre: trimmed,
      tipoUml: nuevoTipoAtrib,
      visibilidad: nuevaVisAtrib,
    });
    if (ok) {
      setNuevoNombreAtrib('');
      setAgregandoAtributo(false);
    }
  }

  // Iniciar edición de un atributo existente
  function iniciarEdicionAtributo(e: MouseEvent, a: Atributo) {
    e.stopPropagation();
    if (!puedeEditar) return;
    setEditandoAtributoId(a.id);
    setEditNombreAtrib(a.nombre);
    setEditTipoAtrib(a.tipo_uml);
    setEditVisAtrib(a.visibilidad);
  }

  // Guardar edición de atributo existente
  async function confirmarEdicionAtributo(idAtributo: string) {
    const trimmed = editNombreAtrib.trim();
    if (!trimmed || !alEditarAtributoDirecto) return;
    const ok = await alEditarAtributoDirecto(idAtributo, clase.id, {
      nombre: trimmed,
      tipoUml: editTipoAtrib,
      visibilidad: editVisAtrib,
    });
    if (ok) {
      setEditandoAtributoId(null);
    }
  }

  const esAsociacion = clase.estereotipo === 'association' || clase.estereotipo === 'asociacion';

  return (
    <div
      className={`uml-class ${selected ? 'selected-class' : ''} ${clase.es_abstracto ? 'abstract-class' : ''} ${esAsociacion ? 'association-class' : ''} ${esOrigenConector ? 'class-connecting-source' : ''}`}
      data-testid="clase-uml"
      tabIndex={0}
      aria-label={`Clase UML ${clase.nombre}`}
    >
      {/* 20 Handles distribuidos en 5 puertos por cada lado (Superior, Derecho, Inferior, Izquierdo) */}
      {/* Superior (Top) */}
      {[
        { index: 1, pct: '16%' },
        { index: 2, pct: '33%' },
        { index: 3, pct: '50%' },
        { index: 4, pct: '67%' },
        { index: 5, pct: '84%' },
      ].map((p) => {
        const idTarget = p.index === 3 ? 'top-target' : `top-${p.index}-target`;
        const idSource = p.index === 3 ? 'top-source' : `top-${p.index}-source`;
        return (
          <Fragment key={`top-${p.index}`}>
            <Handle
              type="target"
              position={Position.Top}
              id={idTarget}
              style={{ left: p.pct }}
              className="uml-handle uml-handle-top"
              title={`Conexión superior puerto ${p.index} (Entrada)`}
            />
            <Handle
              type="source"
              position={Position.Top}
              id={idSource}
              style={{ left: p.pct }}
              className="uml-handle uml-handle-top"
              title={`Conexión superior puerto ${p.index} (Salida)`}
            />
            {p.index === 3 && (
              <>
                <Handle
                  type="target"
                  position={Position.Top}
                  id="top-3-target"
                  style={{ left: p.pct, display: 'none' }}
                />
                <Handle
                  type="source"
                  position={Position.Top}
                  id="top-3-source"
                  style={{ left: p.pct, display: 'none' }}
                />
              </>
            )}
          </Fragment>
        );
      })}

      {/* Derecho (Right) */}
      {[
        { index: 1, pct: '16%' },
        { index: 2, pct: '33%' },
        { index: 3, pct: '50%' },
        { index: 4, pct: '67%' },
        { index: 5, pct: '84%' },
      ].map((p) => {
        const idTarget = p.index === 3 ? 'right-target' : `right-${p.index}-target`;
        const idSource = p.index === 3 ? 'salida' : `right-${p.index}-source`;
        return (
          <Fragment key={`right-${p.index}`}>
            <Handle
              type="target"
              position={Position.Right}
              id={idTarget}
              style={{ top: p.pct }}
              className="uml-handle uml-handle-right"
              title={`Conexión derecha puerto ${p.index} (Entrada)`}
            />
            <Handle
              type="source"
              position={Position.Right}
              id={idSource}
              style={{ top: p.pct }}
              className="uml-handle uml-handle-right"
              title={`Conexión derecha puerto ${p.index} (Salida)`}
            />
            {p.index === 3 && (
              <>
                <Handle
                  type="target"
                  position={Position.Right}
                  id="right-3-target"
                  style={{ top: p.pct, display: 'none' }}
                />
                <Handle
                  type="source"
                  position={Position.Right}
                  id="right-3-source"
                  style={{ top: p.pct, display: 'none' }}
                />
              </>
            )}
          </Fragment>
        );
      })}

      {/* Inferior (Bottom) */}
      {[
        { index: 1, pct: '16%' },
        { index: 2, pct: '33%' },
        { index: 3, pct: '50%' },
        { index: 4, pct: '67%' },
        { index: 5, pct: '84%' },
      ].map((p) => {
        const idTarget = p.index === 3 ? 'bottom-target' : `bottom-${p.index}-target`;
        const idSource = p.index === 3 ? 'bottom-source' : `bottom-${p.index}-source`;
        return (
          <Fragment key={`bottom-${p.index}`}>
            <Handle
              type="target"
              position={Position.Bottom}
              id={idTarget}
              style={{ left: p.pct }}
              className="uml-handle uml-handle-bottom"
              title={`Conexión inferior puerto ${p.index} (Entrada)`}
            />
            <Handle
              type="source"
              position={Position.Bottom}
              id={idSource}
              style={{ left: p.pct }}
              className="uml-handle uml-handle-bottom"
              title={`Conexión inferior puerto ${p.index} (Salida)`}
            />
            {p.index === 3 && (
              <>
                <Handle
                  type="target"
                  position={Position.Bottom}
                  id="bottom-3-target"
                  style={{ left: p.pct, display: 'none' }}
                />
                <Handle
                  type="source"
                  position={Position.Bottom}
                  id="bottom-3-source"
                  style={{ left: p.pct, display: 'none' }}
                />
              </>
            )}
          </Fragment>
        );
      })}

      {/* Izquierdo (Left) */}
      {[
        { index: 1, pct: '16%' },
        { index: 2, pct: '33%' },
        { index: 3, pct: '50%' },
        { index: 4, pct: '67%' },
        { index: 5, pct: '84%' },
      ].map((p) => {
        const idTarget = p.index === 3 ? 'entrada' : `left-${p.index}-target`;
        const idSource = p.index === 3 ? 'left-source' : `left-${p.index}-source`;
        return (
          <Fragment key={`left-${p.index}`}>
            <Handle
              type="target"
              position={Position.Left}
              id={idTarget}
              style={{ top: p.pct }}
              className="uml-handle uml-handle-left"
              title={`Conexión izquierda puerto ${p.index} (Entrada)`}
            />
            <Handle
              type="source"
              position={Position.Left}
              id={idSource}
              style={{ top: p.pct }}
              className="uml-handle uml-handle-left"
              title={`Conexión izquierda puerto ${p.index} (Salida)`}
            />
            {p.index === 3 && (
              <>
                <Handle
                  type="target"
                  position={Position.Left}
                  id="left-3-target"
                  style={{ top: p.pct, display: 'none' }}
                />
                <Handle
                  type="source"
                  position={Position.Left}
                  id="left-3-source"
                  style={{ top: p.pct, display: 'none' }}
                />
              </>
            )}
          </Fragment>
        );
      })}

      {/* Encabezado de la clase con edición inline al hacer doble clic */}
      <div
        className="uml-class-title"
        onDoubleClick={(e) => {
          e.stopPropagation();
          if (puedeEditar) setEditandoNombre(true);
        }}
        title={puedeEditar ? 'Doble clic para cambiar el nombre de la clase' : undefined}
      >
        <small className="uml-class-stereotype">
          {clase.estereotipo
            ? '«' + clase.estereotipo + '»'
            : clase.es_abstracto
              ? '{abstract}'
              : '«class»'}
        </small>

        {editandoNombre ? (
          <div className="inline-title-edit-box" onClick={(e) => e.stopPropagation()}>
            <input
              autoFocus
              className="inline-class-name-input"
              value={nombreClase}
              onChange={(e) => setNombreClase(e.target.value)}
              onBlur={() => void guardarNombreClase()}
              onKeyDown={manejarKeyDownNombre}
            />
            <button
              type="button"
              className="inline-btn-check"
              onClick={() => void guardarNombreClase()}
              title="Guardar nombre"
            >
              ✓
            </button>
          </div>
        ) : (
          <b
            className="uml-class-name"
            style={{ fontStyle: clase.es_abstracto ? 'italic' : 'normal' }}
          >
            {clase.nombre}
          </b>
        )}
      </div>

      {/* Compartimento de Atributos */}
      <div className="uml-compartment">
        <div className="uml-compartment-header">
          <span className="uml-compartment-label">Atributos</span>
          {puedeEditar && (
            <button
              className="uml-quick-add-btn"
              title="Añadir nuevo atributo directamente en el lienzo"
              onClick={(e) => {
                e.stopPropagation();
                setAgregandoAtributo(true);
              }}
            >
              +
            </button>
          )}
        </div>

        {/* Formulario inline para añadir nuevo atributo */}
        {agregandoAtributo && (
          <div
            className="inline-attr-editor nodrag nopan"
            onClick={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <div className="inline-attr-row">
              <select
                value={nuevaVisAtrib}
                onChange={(e) => setNuevaVisAtrib(e.target.value)}
                className="inline-vis-select"
                title="Visibilidad"
              >
                <option value="PRIVADO">−</option>
                <option value="PUBLICO">+</option>
                <option value="PROTEGIDO">#</option>
                <option value="PAQUETE">~</option>
              </select>
              <input
                autoFocus
                placeholder="nombre"
                value={nuevoNombreAtrib}
                onChange={(e) => setNuevoNombreAtrib(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void confirmarNuevoAtributo();
                  if (e.key === 'Escape') setAgregandoAtributo(false);
                }}
                className="inline-name-input"
              />
              <select
                value={nuevoTipoAtrib}
                onChange={(e) => setNuevoTipoAtrib(e.target.value)}
                className="inline-type-select"
                title="Tipo de dato"
              >
                {TODOS_LOS_TIPOS_BASICOS.map((tipo) => (
                  <option key={tipo} value={tipo}>
                    {tipo}
                  </option>
                ))}
              </select>
            </div>
            <div className="inline-attr-actions">
              <button
                type="button"
                className="inline-btn-save"
                disabled={!nuevoNombreAtrib.trim()}
                onClick={() => void confirmarNuevoAtributo()}
                title="Guardar atributo"
              >
                ✓ Añadir
              </button>
              <button
                type="button"
                className="inline-btn-cancel"
                onClick={() => setAgregandoAtributo(false)}
                title="Cancelar"
              >
                ✕
              </button>
            </div>
          </div>
        )}

        {/* Lista de atributos */}
        {atributos.length ? (
          atributos.map((a) =>
            editandoAtributoId === a.id ? (
              /* Editor inline para atributo existente */
              <div
                key={a.id}
                className="inline-attr-editor nodrag nopan"
                onClick={(e) => e.stopPropagation()}
                onMouseDown={(e) => e.stopPropagation()}
              >
                <div className="inline-attr-row">
                  <select
                    value={editVisAtrib}
                    onChange={(e) => setEditVisAtrib(e.target.value)}
                    className="inline-vis-select"
                    title="Visibilidad"
                  >
                    <option value="PRIVADO">−</option>
                    <option value="PUBLICO">+</option>
                    <option value="PROTEGIDO">#</option>
                    <option value="PAQUETE">~</option>
                  </select>
                  <input
                    autoFocus
                    value={editNombreAtrib}
                    onChange={(e) => setEditNombreAtrib(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') void confirmarEdicionAtributo(a.id);
                      if (e.key === 'Escape') setEditandoAtributoId(null);
                    }}
                    className="inline-name-input"
                  />
                  <select
                    value={editTipoAtrib}
                    onChange={(e) => setEditTipoAtrib(e.target.value)}
                    className="inline-type-select"
                    title="Tipo de dato"
                  >
                    {TODOS_LOS_TIPOS_BASICOS.map((tipo) => (
                      <option key={tipo} value={tipo}>
                        {tipo}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="inline-attr-actions">
                  <button
                    type="button"
                    className="inline-btn-save"
                    disabled={!editNombreAtrib.trim()}
                    onClick={() => void confirmarEdicionAtributo(a.id)}
                    title="Guardar cambios"
                  >
                    ✓ Guardar
                  </button>
                  {alEliminarAtributoDirecto && (
                    <button
                      type="button"
                      className="inline-btn-delete"
                      onClick={() => {
                        void alEliminarAtributoDirecto(a.id);
                        setEditandoAtributoId(null);
                      }}
                      title="Eliminar atributo"
                    >
                      🗑
                    </button>
                  )}
                  <button
                    type="button"
                    className="inline-btn-cancel"
                    onClick={() => setEditandoAtributoId(null)}
                    title="Cancelar"
                  >
                    ✕
                  </button>
                </div>
              </div>
            ) : (
              /* Fila estándar del atributo */
              <div
                key={a.id}
                className="uml-member-row clickable"
                onDoubleClick={(e) => iniciarEdicionAtributo(e, a)}
                title={
                  puedeEditar ? 'Doble clic para editar este atributo en el lienzo' : undefined
                }
              >
                <span
                  className="uml-visibility"
                  title={`Visibilidad: ${a.visibilidad.toLowerCase()}`}
                >
                  {simbolo(a.visibilidad)}
                </span>
                <span
                  className="uml-member-text"
                  style={{ textDecoration: a.es_estatico ? 'underline' : 'none' }}
                >
                  {a.nombre}: <span className="uml-type-highlight">{a.tipo_uml}</span>
                </span>
                {a.multiplicidad_minima !== 1 || a.multiplicidad_maxima !== 1
                  ? ' [' + multiplicidad(a.multiplicidad_minima, a.multiplicidad_maxima) + ']'
                  : ''}
                {a.es_ordenado ? ' {ordered}' : ''}{a.coleccion_unica === false ? ' {nonunique}' : ''}
                {a.es_identificador ? <span className="uml-id-badge"> {'{id}'}</span> : ''}
              </div>
            ),
          )
        ) : !agregandoAtributo ? (
          <span
            className="uml-placeholder clickable"
            onClick={(e) => {
              e.stopPropagation();
              if (puedeEditar) setAgregandoAtributo(true);
            }}
            title={puedeEditar ? 'Clic para añadir atributo' : undefined}
          >
            + Añadir atributo
          </span>
        ) : null}
      </div>

      {/* Compartimento de Operaciones */}
      <div className="uml-compartment">
        <div className="uml-compartment-header">
          <span className="uml-compartment-label">Operaciones</span>
        </div>
        {operaciones.length ? (
          operaciones.map((o) => (
            <div key={o.id} className="uml-member-row">
              <span
                className="uml-visibility"
                title={`Visibilidad: ${o.visibilidad.toLowerCase()}`}
              >
                {simbolo(o.visibilidad)}
              </span>
              <span
                className="uml-member-text"
                style={{
                  textDecoration: o.es_estatico ? 'underline' : 'none',
                  fontStyle: o.es_abstracto ? 'italic' : 'normal',
                }}
              >
                {o.nombre}(
                {parametros
                  .filter((p) => p.id_operacion === o.id && p.direccion !== 'RETORNO')
                  .map((p) => `${p.direccion === 'SALIDA' ? 'out ' : p.direccion === 'ENTRADA_SALIDA' ? 'inout ' : 'in '}${p.nombre}: ${p.tipo_uml}${p.es_obligatorio ? '' : ' [0..1]'}`)
                  .join(', ')}
                ): <span className="uml-type-highlight">{o.tipo_retorno ?? parametros.find((p) => p.id_operacion === o.id && p.direccion === 'RETORNO')?.tipo_uml ?? 'void'}</span>
              </span>
            </div>
          ))
        ) : (
          <span className="uml-placeholder">Sin operaciones</span>
        )}
      </div>
    </div>
  );
}
