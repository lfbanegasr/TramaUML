import { useEffect, useRef, useState } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  applyNodeChanges,
  type Connection,
  type ReactFlowInstance,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { Proyecto } from '../../proyectos/api/proyectosApi';
import { useModelo } from '../estado/useModelo';
import { ClaseNode, type ClaseNodo } from '../components/ClaseNode';
import { RelacionEdge, type RelacionArista } from '../components/RelacionEdge';
import { FormularioElemento, type Borrador } from '../components/FormularioElemento';
import { simbolo, multiplicidad, type Accion } from '../api/modeladoApi';
import { InformeValidacion } from '../../validacion/components/InformeValidacion';
const nodeTypes = { clase: ClaseNode },
  edgeTypes = { uml: RelacionEdge };
export function EditorPage({
  proyecto,
  volver,
  registrarSalida,
}: {
  proyecto: Proyecto;
  volver: () => void;
  registrarSalida: (g: (() => boolean) | null) => void;
}) {
  const m = useModelo(proyecto.idModelo);
  const [nodes, setNodes] = useState<ClaseNodo[]>([]),
    [seleccion, setSeleccion] = useState<string | null>(null);
  const [borrador, setBorrador] = useState<Borrador | null>(null);
  const [informe, setInforme] = useState(false);
  const flujo = useRef<ReactFlowInstance<ClaseNodo, RelacionArista> | null>(null);
  useEffect(() => {
    if (m.modelo)
      setNodes(
        m.modelo.clases.map((c) => ({
          id: c.id,
          type: 'clase',
          position: { x: c.x, y: c.y },
          selected: c.id === seleccion,
          data: {
            clase: c,
            atributos: m.modelo!.atributos.filter((a) => a.id_clase === c.id),
            operaciones: m.modelo!.operaciones.filter((o) => o.id_clase === c.id),
            parametros: m.modelo!.parametros,
          },
        })),
      );
  }, [m.modelo, seleccion]);
  useEffect(() => {
    registrarSalida(() => {
      if (m.enviando) return false;
      if (borrador || m.pendiente)
        return window.confirm(
          'Hay una edición sin terminar. ¿Salir y descartar el borrador local? Si un envío no fue confirmado, podría haberse guardado en el servidor.',
        );
      return true;
    });
    function antesDeSalir(e: BeforeUnloadEvent) {
      if (borrador || m.pendiente || m.enviando) {
        e.preventDefault();
        e.returnValue = '';
      }
    }
    window.addEventListener('beforeunload', antesDeSalir);
    return () => {
      registrarSalida(null);
      window.removeEventListener('beforeunload', antesDeSalir);
    };
  }, [borrador, m.enviando, m.pendiente, registrarSalida]);
  const clase = m.modelo?.clases.find((c) => c.id === seleccion);
  const relacion = m.modelo?.relaciones.find((r) => r.id === seleccion);
  const paquete = m.modelo?.paquetes.find((p) => p.id === seleccion);
  const bloqueado = m.enviando || !!m.pendiente || m.conflicto || m.desactualizado;
  const edges: RelacionArista[] =
    m.modelo?.relaciones.map((r) => ({
      id: r.id,
      type: 'uml',
      source: r.id_clase_origen,
      target: r.id_clase_destino,
      sourceHandle: 'salida',
      targetHandle: 'entrada',
      data: { relacion: r },
      selected: r.id === seleccion,
    })) ?? [];
  async function guardar(a: Accion) {
    const ok = await m.cambiar(a);
    if (ok) {
      setBorrador(null);
      if (a.entidad === 'CLASE' || a.entidad === 'RELACION' || a.entidad === 'PAQUETE')
        setSeleccion(a.idEntidad);
    }
    return ok;
  }
  async function reintentar() {
    const accion = m.pendiente?.acciones[0];
    if (await m.reintentar()) {
      setBorrador(null);
      if (
        accion &&
        (accion.entidad === 'CLASE' ||
          accion.entidad === 'RELACION' ||
          accion.entidad === 'PAQUETE')
      )
        setSeleccion(accion.idEntidad);
    }
  }
  async function eliminar(entidad: Accion['entidad'], id: string) {
    let impacto = 'Se conservarán las versiones anteriores.';
    if (entidad === 'CLASE' && m.modelo) {
      const operaciones = m.modelo.operaciones.filter((o) => o.id_clase === id);
      impacto =
        'También se eliminarán ' +
        m.modelo.atributos.filter((a) => a.id_clase === id).length +
        ' atributos, ' +
        operaciones.length +
        ' operaciones, ' +
        m.modelo.parametros.filter((p) => operaciones.some((o) => o.id === p.id_operacion)).length +
        ' parámetros y ' +
        m.modelo.relaciones.filter((r) => r.id_clase_origen === id || r.id_clase_destino === id)
          .length +
        ' relaciones. ' +
        impacto;
    }
    if (entidad === 'PAQUETE')
      impacto = 'El paquete debe estar vacío; primero reubica sus clases y subpaquetes. ' + impacto;
    if (!window.confirm('¿Eliminar este elemento? ' + impacto)) return;
    if (await m.cambiar({ operacion: 'ELIMINAR', entidad, idEntidad: id })) setBorrador(null);
  }
  function conectar(c: Connection) {
    if (m.modelo?.puedeEditar && !bloqueado && !borrador && c.source && c.target)
      setBorrador({
        entidad: 'RELACION',
        nuevo: true,
        id: crypto.randomUUID(),
        origen: c.source,
        destino: c.target,
      });
  }
  if (!m.modelo)
    return (
      <div className="empty">
        {m.error || 'Abriendo el modelo…'}
        {m.error && <button onClick={m.recargar}>Reintentar consulta</button>}
      </div>
    );
  return (
    <div className="editor">
      <div className="editor-heading">
        <div>
          <button className="text-button" onClick={volver}>
            ← Proyectos
          </button>
          <h1>
            {proyecto.nombre} <span className="badge active">modelo de clases</span>
          </h1>
        </div>
        <span className="save-state" role="status">
          {m.enviando ? 'Guardando…' : m.mensaje || 'Revisión ' + m.modelo.revision}
        </span>
      </div>
      <div className="editor-toolbar">
        <span>◇ {m.modelo.nombre}</span>
        <div>
          <button onClick={() => setInforme((v) => !v)}>Informe de validación</button>
          {m.modelo.puedeOrganizar && (
            <button
              disabled={bloqueado || !!borrador}
              onClick={() =>
                setBorrador({ entidad: 'PAQUETE', nuevo: true, id: crypto.randomUUID() })
              }
            >
              Nuevo paquete
            </button>
          )}
          <button onClick={m.recargar} disabled={m.enviando || (!!m.pendiente && !m.conflicto)}>
            ↻ Recargar modelo
          </button>
          {m.modelo.puedeEditar && (
            <>
              <button
                disabled={bloqueado || !!borrador || !m.modelo.clases.length}
                onClick={() =>
                  setBorrador({ entidad: 'RELACION', nuevo: true, id: crypto.randomUUID() })
                }
              >
                ↔ Nueva relación
              </button>
              <button
                className="primary"
                disabled={bloqueado || !!borrador}
                onClick={() =>
                  setBorrador({ entidad: 'CLASE', nuevo: true, id: crypto.randomUUID() })
                }
              >
                ＋ Nueva clase
              </button>
            </>
          )}
        </div>
      </div>
      {m.error && (
        <div className="error editor-error" role="alert">
          {m.error}
          {m.pendiente && !m.conflicto && (
            <button disabled={m.enviando} onClick={reintentar}>
              Reintentar el mismo envío
            </button>
          )}
          {m.conflicto && (
            <span> Tu borrador se conserva; recarga y revísalo antes de guardar.</span>
          )}
        </div>
      )}
      {informe && (
        <InformeValidacion
          modelo={m.modelo.id}
          revision={m.modelo.revision}
          puedeValidar={m.modelo.puedeValidar}
          bloqueado={bloqueado || !!borrador}
          cerrar={() => setInforme(false)}
          localizar={(elemento, clase) => {
            setSeleccion(
              m.modelo!.relaciones.some((r) => r.id === elemento) ? elemento : clase || elemento,
            );
            void flujo.current?.fitView({
              nodes: [{ id: clase || elemento }],
              maxZoom: 1,
              padding: 0.6,
              duration: 300,
            });
          }}
        />
      )}
      <div className="editor-body">
        <aside className="outline">
          <span className="eyebrow">EXPLORADOR</span>
          <div className="outline-title">
            Clases <span>{m.modelo.clases.length}</span>
          </div>
          {m.modelo.clases.map((c) => (
            <button
              className={'outline-item' + (seleccion === c.id ? ' current' : '')}
              key={c.id}
              disabled={!!borrador}
              onClick={() => setSeleccion(c.id)}
            >
              ◇ {c.nombre}
            </button>
          ))}
          <div className="outline-title">
            Relaciones <span>{m.modelo.relaciones.length}</span>
          </div>
          {m.modelo.relaciones.map((r) => (
            <button
              className="outline-item"
              key={r.id}
              disabled={!!borrador}
              onClick={() => setSeleccion(r.id)}
            >
              ↔ {r.nombre || r.tipo_relacion.toLowerCase()}
            </button>
          ))}
          {m.modelo.paquetes.length > 0 && (
            <>
              <div className="outline-title">Paquetes</div>
              {m.modelo.paquetes.map((p) => (
                <button
                  className="outline-item"
                  disabled={!!borrador}
                  key={p.id}
                  onClick={() => setSeleccion(p.id)}
                >
                  ▱{' '}
                  {p.id_paquete_padre
                    ? (m.modelo!.paquetes.find((padre) => padre.id === p.id_paquete_padre)
                        ?.nombre ?? '') + ' / '
                    : ''}
                  {p.nombre}
                </button>
              ))}
            </>
          )}
          <p className="outline-note">
            {m.modelo.puedeEditar
              ? 'Arrastra las clases para organizarlas. Conecta sus puntos para proponer una relación.'
              : 'Tienes acceso de solo lectura a este modelo.'}
          </p>
        </aside>
        <div className="canvas">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            fitView
            fitViewOptions={{ maxZoom: 1 }}
            onInit={(instancia) => {
              flujo.current = instancia;
            }}
            nodesDraggable={m.modelo.puedeOrganizar && !bloqueado && !borrador}
            nodesConnectable={m.modelo.puedeEditar && !bloqueado && !borrador}
            deleteKeyCode={null}
            onNodesChange={(changes) => setNodes((ns) => applyNodeChanges(changes, ns))}
            onNodeClick={(_, n) => {
              if (!borrador) setSeleccion(n.id);
            }}
            onEdgeClick={(_, e) => {
              if (!borrador) setSeleccion(e.id);
            }}
            onConnect={conectar}
            onNodeDragStop={async (_, n) => {
              const ok = await m.cambiar({
                operacion: 'MOVER',
                entidad: 'CLASE',
                idEntidad: n.id,
                datos: n.position,
              });
              if (!ok)
                setNodes((ns) =>
                  ns.map((node) =>
                    node.id === n.id
                      ? { ...node, position: { x: node.data.clase.x, y: node.data.clase.y } }
                      : node,
                  ),
                );
            }}
          >
            <Background color="#cdd8d4" gap={22} />
            <Controls showInteractive={false} />
            <MiniMap nodeColor="#cedfd8" />
          </ReactFlow>
          {nodes.length === 0 && (
            <div className="canvas-empty">
              <span>◇</span>
              <h2>Un lienzo para tu sistema</h2>
              <p>
                {m.modelo.puedeEditar
                  ? 'Añade tu primera clase para empezar a modelar.'
                  : 'Este modelo todavía no tiene clases.'}
              </p>
            </div>
          )}
        </div>
        <aside className="inspector">
          <span className="eyebrow">PROPIEDADES</span>
          {borrador ? (
            <FormularioElemento
              key={borrador.id}
              borrador={borrador}
              modelo={m.modelo}
              bloqueado={bloqueado}
              cancelar={() => setBorrador(null)}
              guardar={guardar}
            />
          ) : clase ? (
            <>
              <h2>{clase.nombre}</h2>
              <p className="muted">{clase.descripcion || 'Sin descripción'}</p>
              <span className="badge">
                {clase.es_persistente ? 'Persistente' : 'No persistente'}
              </span>
              <h3>Atributos</h3>
              {m.modelo.atributos
                .filter((a) => a.id_clase === clase.id)
                .map((a) => (
                  <div className="attribute-row" key={a.id}>
                    <span>
                      {simbolo(a.visibilidad)} {a.nombre}: {a.tipo_uml}
                      {a.es_identificador ? ' {id}' : ''}
                    </span>
                    {m.modelo!.puedeEditar && (
                      <div>
                        <button
                          disabled={bloqueado}
                          aria-label={'Editar atributo ' + a.nombre}
                          onClick={() =>
                            setBorrador({
                              entidad: 'ATRIBUTO',
                              id: a.id,
                              nuevo: false,
                              idClase: clase.id,
                              atributo: a,
                            })
                          }
                        >
                          Editar
                        </button>
                        <button
                          disabled={bloqueado}
                          aria-label={'Eliminar atributo ' + a.nombre}
                          onClick={() => eliminar('ATRIBUTO', a.id)}
                        >
                          ×
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              {m.modelo.puedeEditar && (
                <button
                  className="wide"
                  disabled={bloqueado}
                  onClick={() =>
                    setBorrador({
                      entidad: 'ATRIBUTO',
                      nuevo: true,
                      id: crypto.randomUUID(),
                      idClase: clase.id,
                    })
                  }
                >
                  ＋ Añadir atributo
                </button>
              )}
              <h3>Operaciones</h3>
              {m.modelo.operaciones
                .filter((o) => o.id_clase === clase.id)
                .map((o) => (
                  <div className="attribute-row" key={o.id}>
                    <span>
                      {o.nombre}(): {o.tipo_retorno ?? 'void'}
                    </span>
                    {m.modelo!.puedeEditar && (
                      <div>
                        <button
                          disabled={bloqueado}
                          aria-label={'Editar operación ' + o.nombre}
                          onClick={() =>
                            setBorrador({
                              entidad: 'OPERACION',
                              id: o.id,
                              nuevo: false,
                              idClase: clase.id,
                              operacion: o,
                            })
                          }
                        >
                          Editar
                        </button>
                        <button
                          disabled={bloqueado}
                          aria-label={'Eliminar operación ' + o.nombre}
                          onClick={() => eliminar('OPERACION', o.id)}
                        >
                          ×
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              {m.modelo.puedeEditar && (
                <>
                  <button
                    className="wide"
                    disabled={bloqueado}
                    onClick={() =>
                      setBorrador({
                        entidad: 'OPERACION',
                        nuevo: true,
                        id: crypto.randomUUID(),
                        idClase: clase.id,
                      })
                    }
                  >
                    ＋ Añadir operación
                  </button>
                  <hr />
                  <button
                    className="wide"
                    disabled={bloqueado}
                    onClick={() =>
                      setBorrador({ entidad: 'CLASE', id: clase.id, nuevo: false, clase })
                    }
                  >
                    Editar propiedades
                  </button>
                  <button
                    className="danger wide"
                    disabled={bloqueado}
                    onClick={() => eliminar('CLASE', clase.id)}
                  >
                    Eliminar clase
                  </button>
                </>
              )}
            </>
          ) : relacion ? (
            <>
              <h2>{relacion.nombre || 'Relación'}</h2>
              <p>{relacion.tipo_relacion.toLowerCase()}</p>
              <p className="muted">
                {multiplicidad(relacion.minimo_origen, relacion.maximo_origen)} →{' '}
                {multiplicidad(relacion.minimo_destino, relacion.maximo_destino)}
              </p>
              {m.modelo.puedeEditar && (
                <>
                  <button
                    disabled={bloqueado}
                    onClick={() =>
                      setBorrador({ entidad: 'RELACION', id: relacion.id, nuevo: false, relacion })
                    }
                  >
                    Editar relación
                  </button>
                  <button
                    className="danger"
                    disabled={bloqueado}
                    onClick={() => eliminar('RELACION', relacion.id)}
                  >
                    Eliminar relación
                  </button>
                </>
              )}
            </>
          ) : paquete ? (
            <>
              <h2>{paquete.nombre}</h2>
              <p>{paquete.descripcion || 'Sin descripción'}</p>
              <p className="muted">
                {m.modelo.clases.filter((c) => c.id_paquete === paquete.id).length} clases ·{' '}
                {m.modelo.paquetes.filter((p) => p.id_paquete_padre === paquete.id).length}{' '}
                subpaquetes
              </p>
              {m.modelo.puedeOrganizar && (
                <>
                  <button
                    disabled={bloqueado}
                    onClick={() =>
                      setBorrador({ entidad: 'PAQUETE', id: paquete.id, nuevo: false, paquete })
                    }
                  >
                    Editar paquete
                  </button>
                  <button
                    className="danger"
                    disabled={bloqueado}
                    onClick={() => eliminar('PAQUETE', paquete.id)}
                  >
                    Eliminar paquete
                  </button>
                </>
              )}
            </>
          ) : (
            <div className="inspector-empty">
              Selecciona una clase, relación o paquete para consultar sus propiedades.
            </div>
          )}
        </aside>
      </div>
      <footer className="editor-footer">
        <span>
          {m.modelo.clases.length} clases · {m.modelo.atributos.length} atributos ·{' '}
          {m.modelo.operaciones.length} operaciones · {m.modelo.relaciones.length} relaciones
        </span>
        <span>{m.modelo.puedeEditar ? 'Permiso de edición' : 'Solo lectura'}</span>
      </footer>
    </div>
  );
}
