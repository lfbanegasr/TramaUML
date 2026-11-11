import { useCallback, useEffect, useRef, useState, type DragEvent } from 'react';
import {
  ReactFlow,
  ViewportPortal,
  Background,
  Controls,
  MiniMap,
  applyNodeChanges,
  type Connection,
  type Edge,
  type ReactFlowInstance,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { Proyecto } from '../../proyectos/api/proyectosApi';
import { useModelo } from '../estado/useModelo';
import { ClaseNode, type ClaseNodo } from '../components/ClaseNode';
import {
  RelacionEdge,
  type RelacionArista,
  type LadoConexion,
  detectarLado,
} from '../components/RelacionEdge';
import { FormularioElemento, type Borrador } from '../components/FormularioElemento';
import { CajaHerramientas } from '../components/CajaHerramientas';
import { simbolo, multiplicidad, type Accion, type Clase, type Relacion } from '../api/modeladoApi';
import { InformeValidacion } from '../../validacion/components/InformeValidacion';
import { ConflictosPanel } from '../../colaboracion/components/ConflictosPanel';
import { listarConflictos } from '../../colaboracion/api/conflictosApi';
import { HistorialPage } from '../../historial/pages/HistorialPage';
import { ExportarXmiDialog } from '../../interoperabilidad/components/ExportarXmiDialog';
import { ImportarXmiDialog } from '../../interoperabilidad/components/ImportarXmiDialog';
import { GenerarBackendDialog } from '../../interoperabilidad/components/GenerarBackendDialog';
import { PanelAsistenteIa } from '../../ia/components/PanelAsistenteIa';
import { SubirFotoPizarraModal } from '../../ia/components/SubirFotoPizarraModal';
import { RevisarPropuestaDialog } from '../../ia/components/RevisarPropuestaDialog';
import { leerPropuesta, leerPropuestaVigente, type PropuestaIa } from '../../ia/api/iaApi';

import { useSesion } from '../../acceso/estado/SesionProvider';

function colorParticipante(id: string, participantes: { idUsuario: string }[]) {
  const ids = participantes.map((p) => p.idUsuario).sort();
  const asignados = new Map<string, number>();
  for (const usuario of ids) {
    let tono = Array.from(usuario).reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 0) % 360;
    while (
      [...asignados.values()].some(
        (t) =>
          Math.min(Math.abs(t - tono), 360 - Math.abs(t - tono)) < Math.min(30, 150 / ids.length),
      )
    )
      tono = (tono + 37) % 360;
    asignados.set(usuario, tono);
  }
  return `hsl(${asignados.get(id) ?? 215} 65% 36%)`;
}
const accionesPresencia: Record<string, string> = {
  EXPLORANDO: 'explorando',
  MOVIENDO: 'moviendo',
  CONECTANDO: 'conectando',
  EDITANDO: 'editando',
};

const nodeTypes = { clase: ClaseNode },
  edgeTypes = { uml: RelacionEdge };

function calcularLadoPorDefecto(
  origen?: Clase,
  destino?: Clase,
): { sourceLado: LadoConexion; targetLado: LadoConexion } {
  if (!origen || !destino) return { sourceLado: 'RIGHT', targetLado: 'LEFT' };
  const dx = destino.x - origen.x;
  const dy = destino.y - origen.y;
  if (Math.abs(dx) >= Math.abs(dy)) {
    return dx >= 0
      ? { sourceLado: 'RIGHT', targetLado: 'LEFT' }
      : { sourceLado: 'LEFT', targetLado: 'RIGHT' };
  } else {
    return dy >= 0
      ? { sourceLado: 'BOTTOM', targetLado: 'TOP' }
      : { sourceLado: 'TOP', targetLado: 'BOTTOM' };
  }
}

function obtenerHandleId(
  lado: LadoConexion,
  puertoIndex: number,
  tipo: 'source' | 'target',
): string {
  if (puertoIndex === 3) {
    if (lado === 'TOP') return tipo === 'source' ? 'top-source' : 'top-target';
    if (lado === 'RIGHT') return tipo === 'source' ? 'salida' : 'right-target';
    if (lado === 'BOTTOM') return tipo === 'source' ? 'bottom-source' : 'bottom-target';
    if (lado === 'LEFT') return tipo === 'source' ? 'left-source' : 'entrada';
  }
  const prefijo = lado.toLowerCase();
  return `${prefijo}-${puertoIndex}-${tipo}`;
}

const PATRON_PUERTOS: Record<number, number[]> = {
  1: [3],
  2: [2, 4],
  3: [2, 3, 4],
  4: [1, 2, 4, 5],
  5: [1, 2, 3, 4, 5],
};

function resolverHandlesRelaciones(
  relaciones: Relacion[],
  clases: Clase[],
  manejadoresGuardados: Record<string, { sourceHandle?: string; targetHandle?: string }>,
): Record<string, { sourceHandle: string; targetHandle: string }> {
  const resultado: Record<string, { sourceHandle: string; targetHandle: string }> = {};
  const salidasPorGrupo: Record<string, string[]> = {};
  const entradasPorGrupo: Record<string, string[]> = {};

  for (const r of relaciones) {
    const guardado = manejadoresGuardados[r.id];
    const origen = clases.find((c) => c.id === r.id_clase_origen);
    const destino = clases.find((c) => c.id === r.id_clase_destino);
    const ladosDefecto = calcularLadoPorDefecto(origen, destino);

    const sourceLado = detectarLado(guardado?.sourceHandle) ?? ladosDefecto.sourceLado;
    const targetLado = detectarLado(guardado?.targetHandle) ?? ladosDefecto.targetLado;

    if (!guardado?.sourceHandle) {
      const clave = `${r.id_clase_origen}_${sourceLado}`;
      if (!salidasPorGrupo[clave]) salidasPorGrupo[clave] = [];
      salidasPorGrupo[clave].push(r.id);
    }

    if (!guardado?.targetHandle) {
      const clave = `${r.id_clase_destino}_${targetLado}`;
      if (!entradasPorGrupo[clave]) entradasPorGrupo[clave] = [];
      entradasPorGrupo[clave].push(r.id);
    }
  }

  for (const r of relaciones) {
    const guardado = manejadoresGuardados[r.id];
    const origen = clases.find((c) => c.id === r.id_clase_origen);
    const destino = clases.find((c) => c.id === r.id_clase_destino);
    const ladosDefecto = calcularLadoPorDefecto(origen, destino);

    const sourceLado = detectarLado(guardado?.sourceHandle) ?? ladosDefecto.sourceLado;
    const targetLado = detectarLado(guardado?.targetHandle) ?? ladosDefecto.targetLado;

    let sourceHandle = guardado?.sourceHandle;
    if (!sourceHandle) {
      const clave = `${r.id_clase_origen}_${sourceLado}`;
      const grupo = salidasPorGrupo[clave] ?? [r.id];
      const idxEnGrupo = grupo.indexOf(r.id);
      const total = Math.min(grupo.length, 5);
      const patron = PATRON_PUERTOS[total] ?? [1, 2, 3, 4, 5];
      const puerto = patron[idxEnGrupo % patron.length] ?? 3;
      sourceHandle = obtenerHandleId(sourceLado, puerto, 'source');
    }

    let targetHandle = guardado?.targetHandle;
    if (!targetHandle) {
      const clave = `${r.id_clase_destino}_${targetLado}`;
      const grupo = entradasPorGrupo[clave] ?? [r.id];
      const idxEnGrupo = grupo.indexOf(r.id);
      const total = Math.min(grupo.length, 5);
      const patron = PATRON_PUERTOS[total] ?? [1, 2, 3, 4, 5];
      const puerto = patron[idxEnGrupo % patron.length] ?? 3;
      targetHandle = obtenerHandleId(targetLado, puerto, 'target');
    }

    resultado[r.id] = { sourceHandle, targetHandle };
  }

  return resultado;
}

export function EditorPage({
  proyecto,
  volver,
  registrarSalida,
  registrarFinalizador,
}: {
  proyecto: Proyecto;
  volver: () => void;
  registrarSalida: (g: (() => boolean) | null) => void;
  registrarFinalizador: (f: (() => Promise<void>) | null) => void;
}) {
  const { sesion: identidad } = useSesion();
  const m = useModelo(proyecto.idModelo);
  const [nodes, setNodes] = useState<ClaseNodo[]>([]),
    [seleccion, setSeleccion] = useState<string | null>(null);
  const [borrador, setBorrador] = useState<Borrador | null>(null);
  const [tabIzquierda, setTabIzquierda] = useState<'PALETA' | 'EXPLORADOR'>('PALETA');
  const [verPanelIzquierdo, setVerPanelIzquierdo] = useState(true);

  // Pestaña activa del panel lateral derecho (exclusión mutua para preservar espacio del lienzo)
  type TabDerecha = 'PROPIEDADES' | 'ASISTENTE_IA' | 'VALIDACION';
  const [tabDerecha, setTabDerecha] = useState<TabDerecha | null>('PROPIEDADES');

  const verInspector = tabDerecha === 'PROPIEDADES';
  const setVerInspector = useCallback((v: boolean | ((prev: boolean) => boolean)) => {
    setTabDerecha((actual) => {
      const nuevo = typeof v === 'function' ? v(actual === 'PROPIEDADES') : v;
      return nuevo ? 'PROPIEDADES' : null;
    });
  }, []);

  // Estados para menús desplegables de la barra de herramientas (Ribbon)
  const [dropdownInteroperabilidad, setDropdownInteroperabilidad] = useState(false);
  const [dropdownCalidad, setDropdownCalidad] = useState(false);
  const dropdownInteroperabilidadRef = useRef<HTMLDivElement | null>(null);
  const dropdownCalidadRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    function manejarClickAfuera(e: MouseEvent) {
      if (
        dropdownInteroperabilidadRef.current &&
        !dropdownInteroperabilidadRef.current.contains(e.target as Node)
      ) {
        setDropdownInteroperabilidad(false);
      }
      if (dropdownCalidadRef.current && !dropdownCalidadRef.current.contains(e.target as Node)) {
        setDropdownCalidad(false);
      }
    }
    document.addEventListener('mousedown', manejarClickAfuera);
    return () => document.removeEventListener('mousedown', manejarClickAfuera);
  }, []);

  // Estado para agregado rápido de atributos
  const [rapidoNombre, setRapidoNombre] = useState('');
  const [rapidoTipo, setRapidoTipo] = useState('String');
  const [rapidoVisibilidad, setRapidoVisibilidad] = useState('PRIVADO');

  // Modo de conexión por clics desde la paleta de conectores
  const [conectorActivo, setConectorActivo] = useState<string | null>(null);
  const [origenConector, setOrigenConector] = useState<string | null>(null);

  // Manejadores / puertos personalizados de conexión para relaciones (persisten en localStorage)
  const [manejadoresAristas, setManejadoresAristas] = useState<
    Record<string, { sourceHandle?: string; targetHandle?: string }>
  >(() => {
    try {
      const guardado = localStorage.getItem(`uml_handles_${proyecto.idModelo}`);
      return guardado ? JSON.parse(guardado) : {};
    } catch {
      return {};
    }
  });

  const actualizarManejadores = useCallback(
    (
      actualizador: (
        prev: Record<string, { sourceHandle?: string; targetHandle?: string }>,
      ) => Record<string, { sourceHandle?: string; targetHandle?: string }>,
    ) => {
      setManejadoresAristas((prev) => {
        const nuevo = actualizador(prev);
        try {
          localStorage.setItem(`uml_handles_${proyecto.idModelo}`, JSON.stringify(nuevo));
        } catch {}
        return nuevo;
      });
    },
    [proyecto.idModelo],
  );

  const informe = tabDerecha === 'VALIDACION';
  const setInforme = useCallback((v: boolean | ((prev: boolean) => boolean)) => {
    setTabDerecha((actual) => {
      const nuevo = typeof v === 'function' ? v(actual === 'VALIDACION') : v;
      return nuevo ? 'VALIDACION' : null;
    });
  }, []);
  const [mostrarConflictos, setMostrarConflictos] = useState(false);
  const [totalConflictos, setTotalConflictos] = useState(0);
  const cambiarTotalConflictos = useCallback((total: number) => setTotalConflictos(total), []);
  const [verHistorial, setVerHistorial] = useState(false);
  const [verExportarXmi, setVerExportarXmi] = useState(false);
  const [verImportarXmi, setVerImportarXmi] = useState(false);
  const [verGenerarBackend, setVerGenerarBackend] = useState(false);
  const [verImagenIa, setVerImagenIa] = useState(false);
  const [verPropuestaIa, setVerPropuestaIa] = useState(false);
  const [propuestaIa, setPropuestaIa] = useState<PropuestaIa | null>(null);
  const verAsistenteIa = tabDerecha === 'ASISTENTE_IA';
  const setVerAsistenteIa = useCallback((v: boolean | ((prev: boolean) => boolean)) => {
    setTabDerecha((actual) => {
      const nuevo = typeof v === 'function' ? v(actual === 'ASISTENTE_IA') : v;
      return nuevo ? 'ASISTENTE_IA' : null;
    });
  }, []);
  const [promptIa, setPromptIa] = useState<string | null>(null);
  const abrirIaConPrompt = useCallback((prompt: string, claseId?: string) => {
    if (claseId) {
      setSeleccion(claseId);
    }
    setPromptIa(prompt);
    setTabDerecha('ASISTENTE_IA');
  }, []);
  const flujo = useRef<ReactFlowInstance<ClaseNodo, RelacionArista> | null>(null);
  const finalizarRef = useRef(m.salir),
    enfocarRef = useRef(m.enfocar);
  finalizarRef.current = m.salir;
  enfocarRef.current = m.enfocar;

  const puedeEditar =
    m.modelo?.puedeEditar && m.sesion?.modoPropio === 'EDITOR' && m.colaboracion === 'CONECTADO';
  const puedeOrganizar = puedeEditar && m.modelo?.puedeOrganizar;
  const bloqueado =
    m.enviando ||
    !!m.pendiente ||
    m.conflicto ||
    m.desactualizado ||
    m.colaboracion !== 'CONECTADO';

  const cambiarPuertoRelacion = useCallback(
    (
      idRelacion: string,
      extremo: 'ORIGEN' | 'DESTINO',
      lado: LadoConexion,
      puertoIndex: number = 3,
    ) => {
      actualizarManejadores((prev) => {
        const handleId = obtenerHandleId(
          lado,
          puertoIndex,
          extremo === 'ORIGEN' ? 'source' : 'target',
        );
        if (extremo === 'ORIGEN') {
          return {
            ...prev,
            [idRelacion]: {
              ...prev[idRelacion],
              sourceHandle: handleId,
            },
          };
        } else {
          return {
            ...prev,
            [idRelacion]: {
              ...prev[idRelacion],
              targetHandle: handleId,
            },
          };
        }
      });
    },
    [actualizarManejadores],
  );

  const manejarReconectar = useCallback(
    async (oldEdge: Edge, newConnection: Connection) => {
      if (!puedeEditar || bloqueado) return;
      const rel = m.modelo?.relaciones.find((r) => r.id === oldEdge.id);
      if (!rel) return;

      const origenCambio = newConnection.source && newConnection.source !== rel.id_clase_origen;
      const destinoCambio = newConnection.target && newConnection.target !== rel.id_clase_destino;

      if (origenCambio || destinoCambio) {
        await guardarRef.current({
          operacion: 'ACTUALIZAR',
          entidad: 'RELACION',
          idEntidad: oldEdge.id,
          datos: {
            origen: newConnection.source || rel.id_clase_origen,
            destino: newConnection.target || rel.id_clase_destino,
            nombre: rel.nombre,
            tipo: rel.tipo_relacion,
            direccion: rel.direccion,
            rolOrigen: rel.rol_origen,
            rolDestino: rel.rol_destino,
            ...(!['GENERALIZACION', 'DEPENDENCIA'].includes(rel.tipo_relacion)
              ? {
                  minimoOrigen: rel.minimo_origen ?? 1,
                  maximoOrigen: rel.maximo_origen ?? 1,
                  minimoDestino: rel.minimo_destino ?? 1,
                  maximoDestino: rel.maximo_destino ?? 1,
                }
              : {}),
          },
        });
      }

      actualizarManejadores((prev) => ({
        ...prev,
        [oldEdge.id]: {
          sourceHandle: newConnection.sourceHandle ?? prev[oldEdge.id]?.sourceHandle,
          targetHandle: newConnection.targetHandle ?? prev[oldEdge.id]?.targetHandle,
        },
      }));

      setSeleccion(oldEdge.id);
    },
    [actualizarManejadores, bloqueado, m.modelo?.relaciones, puedeEditar],
  );

  useEffect(() => {
    let activo = true;
    listarConflictos(proyecto.idModelo)
      .then((lista) => {
        if (activo) setTotalConflictos(lista.total);
      })
      .catch(() => undefined);
    return () => {
      activo = false;
    };
  }, [proyecto.idModelo, m.senalConflictos]);

  const clase = m.modelo?.clases.find((c) => c.id === seleccion);
  const relacion = m.modelo?.relaciones.find((r) => r.id === seleccion);
  const paquete = m.modelo?.paquetes.find((p) => p.id === seleccion);

  async function guardar(a: Accion) {
    const ok = await m.cambiar(a);
    if (ok) {
      setBorrador(null);
      if (a.entidad === 'CLASE' || a.entidad === 'RELACION' || a.entidad === 'PAQUETE')
        setSeleccion(a.idEntidad);
    }
    return ok;
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

  const guardarRef = useRef(guardar);
  guardarRef.current = guardar;

  const eliminarRef = useRef(eliminar);
  eliminarRef.current = eliminar;

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
            esOrigenConector: c.id === origenConector,
            atributos: m.modelo!.atributos.filter((a) => a.id_clase === c.id),
            operaciones: m.modelo!.operaciones.filter((o) => o.id_clase === c.id),
            parametros: m.modelo!.parametros,
            puedeEditar: !!puedeEditar,
            alEditarNombreClase: async (idClase, nuevoNombre) => {
              const claseActual = m.modelo?.clases.find((item) => item.id === idClase);
              if (!claseActual) return false;
              return await guardarRef.current({
                operacion: 'ACTUALIZAR',
                entidad: 'CLASE',
                idEntidad: idClase,
                datos: {
                  nombre: nuevoNombre,
                  descripcion: claseActual.descripcion,
                  estereotipo: claseActual.estereotipo,
                  visibilidad: claseActual.visibilidad,
                  esAbstracto: claseActual.es_abstracto,
                  esPersistente: claseActual.es_persistente,
                },
              });
            },
            alAgregarAtributoDirecto: async (idClase, datos) => {
              return await guardarRef.current({
                operacion: 'CREAR',
                entidad: 'ATRIBUTO',
                idEntidad: crypto.randomUUID(),
                datos: {
                  idClase,
                  nombre: datos.nombre,
                  tipoUml: datos.tipoUml,
                  visibilidad: datos.visibilidad,
                  minimo: 1,
                  maximo: 1,
                  esIdentificador: false,
                  esEstatico: false,
                  esUnico: false,
                  valorPredeterminado: '',
                  restricciones: {},
                },
              });
            },
            alEditarAtributoDirecto: async (idAtributo, idClase, datos) => {
              const atribActual = m.modelo?.atributos.find((item) => item.id === idAtributo);
              if (!atribActual) return false;
              let restricciones = {};
              try {
                restricciones = JSON.parse(atribActual.restricciones_json || '{}');
              } catch {
                restricciones = {};
              }
              return await guardarRef.current({
                operacion: 'ACTUALIZAR',
                entidad: 'ATRIBUTO',
                idEntidad: idAtributo,
                datos: {
                  idClase,
                  nombre: datos.nombre,
                  tipoUml: datos.tipoUml,
                  visibilidad: datos.visibilidad,
                  minimo: atribActual.multiplicidad_minima,
                  maximo: atribActual.multiplicidad_maxima,
                  esIdentificador: atribActual.es_identificador,
                  esEstatico: atribActual.es_estatico,
                  esUnico: atribActual.es_unico,
                  esOrdenado: atribActual.es_ordenado ?? false,
                  coleccionUnica: atribActual.coleccion_unica ?? true,
                  valorPredeterminado: atribActual.valor_predeterminado ?? '',
                  restricciones,
                },
              });
            },
            alEliminarAtributoDirecto: async (idAtributo) => {
              await eliminarRef.current('ATRIBUTO', idAtributo);
            },
          },
        })),
      );
  }, [m.modelo, seleccion, puedeEditar, origenConector]);

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

  useEffect(() => {
    registrarFinalizador(() => finalizarRef.current());
    return () => registrarFinalizador(null);
  }, [registrarFinalizador]);

  useEffect(() => {
    m.registrarBorrador(!!borrador);
    return () => m.registrarBorrador(false);
  }, [borrador, m.registrarBorrador]);

  useEffect(() => {
    if (!seleccion) enfocarRef.current(null, null);
    else if (m.modelo?.clases.some((c) => c.id === seleccion))
      enfocarRef.current('CLASE', seleccion);
    else if (m.modelo?.relaciones.some((r) => r.id === seleccion))
      enfocarRef.current('RELACION', seleccion);
    else if (m.modelo?.paquetes.some((p) => p.id === seleccion))
      enfocarRef.current('PAQUETE', seleccion);
  }, [seleccion, m.modelo?.clases, m.modelo?.relaciones, m.modelo?.paquetes]);

  useEffect(() => {
    let activo = true;
    leerPropuestaVigente(proyecto.idModelo)
      .then((propuesta) => {
        if (activo) setPropuestaIa(propuesta ?? null);
      })
      .catch(() => undefined);
    return () => {
      activo = false;
    };
  }, [proyecto.idModelo, m.modelo?.revision]);

  async function cambiarRelacionEnLienzo(
    id: string,
    nuevoTipo: string,
    nuevoNombre?: string | null,
  ) {
    const rel = m.modelo?.relaciones.find((r) => r.id === id);
    if (!rel || bloqueado || !puedeEditar) return;
    let nombreFinal = nuevoNombre !== undefined ? nuevoNombre : rel.nombre;
    if (nuevoTipo === 'GENERALIZACION' && (!nombreFinal || !nombreFinal.includes('{'))) {
      nombreFinal = null;
    }
    await guardarRef.current({
      operacion: 'ACTUALIZAR',
      entidad: 'RELACION',
      idEntidad: id,
      datos: {
        origen: rel.id_clase_origen,
        destino: rel.id_clase_destino,
        nombre: nombreFinal,
        tipo: nuevoTipo,
        direccion: rel.direccion,
        rolOrigen: rel.rol_origen,
        rolDestino: rel.rol_destino,
        ...(!['GENERALIZACION', 'DEPENDENCIA'].includes(nuevoTipo)
          ? {
              minimoOrigen: rel.minimo_origen ?? 1,
              maximoOrigen: rel.maximo_origen ?? 1,
              minimoDestino: rel.minimo_destino ?? 1,
              maximoDestino: rel.maximo_destino ?? 1,
            }
          : {}),
      },
    });
  }

  const cambiarRelacionRef = useRef(cambiarRelacionEnLienzo);
  cambiarRelacionRef.current = cambiarRelacionEnLienzo;

  async function agregarAtributoRapido() {
    if (!clase || !rapidoNombre.trim() || bloqueado || !puedeEditar) return;
    const ok = await guardar({
      operacion: 'CREAR',
      entidad: 'ATRIBUTO',
      idEntidad: crypto.randomUUID(),
      datos: {
        idClase: clase.id,
        nombre: rapidoNombre.trim(),
        tipoUml: rapidoTipo.trim() || 'String',
        visibilidad: rapidoVisibilidad,
        minimo: 1,
        maximo: 1,
        esIdentificador: false,
        esEstatico: false,
        esUnico: false,
        valorPredeterminado: '',
        restricciones: {},
      },
    });
    if (ok) {
      setRapidoNombre('');
    }
  }

  async function cambiarMultiplicidadEnLienzo(
    id: string,
    minO: number,
    maxO: number | null,
    minD: number,
    maxD: number | null,
  ) {
    const rel = m.modelo?.relaciones.find((r) => r.id === id);
    if (!rel || bloqueado || !puedeEditar) return;
    await guardarRef.current({
      operacion: 'ACTUALIZAR',
      entidad: 'RELACION',
      idEntidad: id,
      datos: {
        origen: rel.id_clase_origen,
        destino: rel.id_clase_destino,
        nombre: rel.nombre,
        tipo: rel.tipo_relacion,
        direccion: rel.direccion,
        rolOrigen: rel.rol_origen,
        rolDestino: rel.rol_destino,
        minimoOrigen: minO,
        maximoOrigen: maxO,
        minimoDestino: minD,
        maximoDestino: maxD,
      },
    });
  }

  async function crearClaseIntermedia(idRelacion: string) {
    if (!m.modelo || !puedeEditar || bloqueado) return;
    const rel = m.modelo.relaciones.find((r) => r.id === idRelacion);
    if (!rel) return;

    const origenClase = m.modelo.clases.find((c) => c.id === rel.id_clase_origen);
    const destinoClase = m.modelo.clases.find((c) => c.id === rel.id_clase_destino);
    if (!origenClase || !destinoClase) return;

    const sugerenciaNombre = `${origenClase.nombre}${destinoClase.nombre}`;
    const nombre = window.prompt(
      `La relación ${rel.tipo_relacion} entre ${origenClase.nombre} y ${destinoClase.nombre} se sustituirá por una clase ordinaria y dos asociaciones bidireccionales 1 a 0..*. El nombre, los roles y los límites del vínculo original no se transfieren. No crea una AssociationClass UML. Nombre de la clase:`,
      sugerenciaNombre,
    );
    if (!nombre || !nombre.trim()) return;

    const idClaseIntermedia = crypto.randomUUID();
    const idRelacion1 = crypto.randomUUID();
    const idRelacion2 = crypto.randomUUID();

    const posX = Math.round((origenClase.x + destinoClase.x) / 2);
    const posY = Math.round((origenClase.y + destinoClase.y) / 2) + 80;

    const acciones: Accion[] = [
      {
        operacion: 'CREAR',
        entidad: 'CLASE',
        idEntidad: idClaseIntermedia,
        datos: {
          nombre: nombre.trim(),
          descripcion: `Clase intermedia ordinaria entre ${origenClase.nombre} y ${destinoClase.nombre}`,
          estereotipo: '',
          visibilidad: 'PUBLICO',
          esAbstracto: false,
          esPersistente: false,
          x: posX,
          y: posY,
        },
      },
      {
        operacion: 'CREAR',
        entidad: 'RELACION',
        idEntidad: idRelacion1,
        datos: {
          origen: origenClase.id,
          destino: idClaseIntermedia,
          nombre: null,
          tipo: 'ASOCIACION',
          direccion: 'BIDIRECCIONAL',
          rolOrigen: '',
          rolDestino: '',
          minimoOrigen: 1,
          maximoOrigen: 1,
          minimoDestino: 0,
          maximoDestino: null,
        },
      },
      {
        operacion: 'CREAR',
        entidad: 'RELACION',
        idEntidad: idRelacion2,
        datos: {
          origen: destinoClase.id,
          destino: idClaseIntermedia,
          nombre: null,
          tipo: 'ASOCIACION',
          direccion: 'BIDIRECCIONAL',
          rolOrigen: '',
          rolDestino: '',
          minimoOrigen: 1,
          maximoOrigen: 1,
          minimoDestino: 0,
          maximoDestino: null,
        },
      },
      {
        operacion: 'ELIMINAR',
        entidad: 'RELACION',
        idEntidad: idRelacion,
      },
    ];

    const ok = await m.cambiarLote(acciones);
    if (ok) {
      setBorrador(null);
      setSeleccion(idClaseIntermedia);
    }
  }

  async function cambiarNombreRelacionEnLienzo(id: string, nuevoNombre: string | null) {
    const rel = m.modelo?.relaciones.find((r) => r.id === id);
    if (!rel || bloqueado || !puedeEditar) return;
    await guardarRef.current({
      operacion: 'ACTUALIZAR',
      entidad: 'RELACION',
      idEntidad: id,
      datos: {
        origen: rel.id_clase_origen,
        destino: rel.id_clase_destino,
        nombre: nuevoNombre && nuevoNombre.trim() ? nuevoNombre.trim() : null,
        tipo: rel.tipo_relacion,
        direccion: rel.direccion,
        rolOrigen: rel.rol_origen,
        rolDestino: rel.rol_destino,
        ...(!['GENERALIZACION', 'DEPENDENCIA'].includes(rel.tipo_relacion)
          ? {
              minimoOrigen: rel.minimo_origen ?? 1,
              maximoOrigen: rel.maximo_origen ?? 1,
              minimoDestino: rel.minimo_destino ?? 1,
              maximoDestino: rel.maximo_destino ?? 1,
            }
          : {}),
      },
    });
  }

  const mapaHandles = resolverHandlesRelaciones(
    m.modelo?.relaciones ?? [],
    m.modelo?.clases ?? [],
    manejadoresAristas,
  );

  const edges: RelacionArista[] =
    m.modelo?.relaciones.map((r) => {
      const handles = mapaHandles[r.id] ?? {
        sourceHandle: 'salida',
        targetHandle: 'entrada',
      };
      return {
        id: r.id,
        type: 'uml',
        source: r.id_clase_origen,
        target: r.id_clase_destino,
        sourceHandle: handles.sourceHandle,
        targetHandle: handles.targetHandle,
        data: {
          relacion: r,
          puedeEditar: !!puedeEditar,
          alCambiarTipo: cambiarRelacionEnLienzo,
          alCambiarNombre: cambiarNombreRelacionEnLienzo,
          alCambiarMultiplicidad: cambiarMultiplicidadEnLienzo,
          alCrearClaseIntermedia: crearClaseIntermedia,
          alEliminar: (idRel) => eliminar('RELACION', idRel),
          alCambiarPuerto: cambiarPuertoRelacion,
          sourceHandleActual: handles.sourceHandle,
          targetHandleActual: handles.targetHandle,
        },
        selected: r.id === seleccion,
      };
    }) ?? [];

  async function abrirPropuestaGenerada(id: string) {
    const propuesta = await leerPropuesta(proyecto.idModelo, id);
    setPropuestaIa(propuesta);
    setVerAsistenteIa(true);
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

  // Cancelar modo conector con Escape
  useEffect(() => {
    function manejarTeclasGlobales(e: globalThis.KeyboardEvent) {
      if (e.key === 'Escape' && conectorActivo) {
        setConectorActivo(null);
        setOrigenConector(null);
      }
    }
    window.addEventListener('keydown', manejarTeclasGlobales);
    return () => window.removeEventListener('keydown', manejarTeclasGlobales);
  }, [conectorActivo]);

  async function conectar(c: Connection) {
    if (puedeEditar && !bloqueado && c.source && c.target) {
      const idNuevaRelacion = crypto.randomUUID();
      const tipo = conectorActivo || 'ASOCIACION';
      const nombreRelacion = null;

      const ok = await guardarRef.current({
        operacion: 'CREAR',
        entidad: 'RELACION',
        idEntidad: idNuevaRelacion,
        datos: {
          origen: c.source,
          destino: c.target,
          nombre: nombreRelacion,
          tipo,
          direccion:
            tipo === 'GENERALIZACION' || tipo === 'DEPENDENCIA'
              ? 'ORIGEN_A_DESTINO'
              : 'BIDIRECCIONAL',
          rolOrigen: '',
          rolDestino: '',
          ...(!['GENERALIZACION', 'DEPENDENCIA'].includes(tipo)
            ? {
                minimoOrigen: 1,
                maximoOrigen: 1,
                minimoDestino: tipo === 'COMPOSICION' ? 1 : 0,
                maximoDestino: null,
              }
            : {}),
        },
      });

      if (ok) {
        if (c.sourceHandle || c.targetHandle) {
          actualizarManejadores((prev) => ({
            ...prev,
            [idNuevaRelacion]: {
              sourceHandle: c.sourceHandle ?? undefined,
              targetHandle: c.targetHandle ?? undefined,
            },
          }));
        }
        setSeleccion(idNuevaRelacion);
        setBorrador(null);
      }
      setConectorActivo(null);
      setOrigenConector(null);
    }
  }

  async function manejarSoltarLienzo(e: DragEvent<HTMLDivElement>) {
    const tipo = e.dataTransfer.getData('application/reactflow-tipo');
    if (!tipo || !flujo.current || !puedeEditar || bloqueado || !m.modelo) return;
    e.preventDefault();

    const rect = flujo.current.screenToFlowPosition({ x: e.clientX, y: e.clientY });
    const numClases = m.modelo.clases.length;
    const idNueva = crypto.randomUUID();

    if (tipo === 'CLASE') {
      await guardar({
        operacion: 'CREAR',
        entidad: 'CLASE',
        idEntidad: idNueva,
        datos: {
          nombre: `Clase${numClases + 1}`,
          descripcion: '',
          estereotipo: '',
          visibilidad: 'PUBLICO',
          esAbstracto: false,
          esPersistente: false,
          x: Math.round(rect.x),
          y: Math.round(rect.y),
        },
      });
    } else if (tipo === 'CLASE_ABSTRACTA') {
      await guardar({
        operacion: 'CREAR',
        entidad: 'CLASE',
        idEntidad: idNueva,
        datos: {
          nombre: `ClaseAbstracta${numClases + 1}`,
          descripcion: '',
          estereotipo: '',
          visibilidad: 'PUBLICO',
          esAbstracto: true,
          esPersistente: false,
          x: Math.round(rect.x),
          y: Math.round(rect.y),
        },
      });
    } else if (tipo === 'CLASE_ASOCIACION') {
      await guardar({
        operacion: 'CREAR',
        entidad: 'CLASE',
        idEntidad: idNueva,
        datos: {
          nombre: `ClaseIntermedia${numClases + 1}`,
          descripcion: 'Clase intermedia ordinaria',
          estereotipo: '',
          visibilidad: 'PUBLICO',
          esAbstracto: false,
          esPersistente: false,
          x: Math.round(rect.x),
          y: Math.round(rect.y),
        },
      });
    } else if (tipo === 'PAQUETE') {
      setBorrador({
        entidad: 'PAQUETE',
        nuevo: true,
        id: crypto.randomUUID(),
      });
    }
  }

  if (!m.modelo)
    return (
      <div className="empty">
        {m.error || 'Abriendo el modelo…'}
        {m.error && <button onClick={m.recargar}>Reintentar consulta</button>}
      </div>
    );

  if (verHistorial && m.modelo) {
    return (
      <HistorialPage
        modelo={m.modelo.id}
        revisionVigente={m.modelo.revision}
        versionVigente={m.modelo.idVersion}
        puedeRestaurar={m.modelo.puedeRestaurar}
        volver={async () => {
          setVerHistorial(false);
          await m.reanudarSesion();
        }}
        prepararRestauracion={async () => {
          return await m.pausarSesion();
        }}
        alRestaurar={async () => {
          setVerHistorial(false);
          await m.reanudarSesion();
          await m.recargar();
        }}
      />
    );
  }

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
        {/* Sección Izquierda: Paneles laterales y Nombre del modelo */}
        <div className="toolbar-section toolbar-section-left">
          <div className="toolbar-group panels-group" aria-label="Paneles laterales">
            <button
              type="button"
              className={`toolbar-panel-toggle ${verPanelIzquierdo ? 'active' : ''}`}
              onClick={() => setVerPanelIzquierdo((v) => !v)}
              title={
                verPanelIzquierdo ? 'Ocultar Paleta y Explorador' : 'Mostrar Paleta y Explorador'
              }
            >
              <span className="toolbar-icon">🧰</span>
              <span className="toolbar-label">Herramientas</span>
            </button>
            <button
              type="button"
              className={`toolbar-panel-toggle ${verInspector ? 'active' : ''}`}
              onClick={() => setVerInspector((v) => !v)}
              title={verInspector ? 'Ocultar panel de propiedades' : 'Mostrar panel de propiedades'}
            >
              <span className="toolbar-icon">🎛</span>
              <span className="toolbar-label">Propiedades</span>
            </button>
            {puedeEditar && (
              <button
                id="btn-asistente-ia"
                type="button"
                className={`toolbar-panel-toggle ia-toggle ${verAsistenteIa ? 'active' : ''}`}
                disabled={bloqueado || !!borrador}
                onClick={() => setVerAsistenteIa((v) => !v)}
                title="Abrir u ocultar el Agente Asistente IA lateral"
              >
                <span className="toolbar-icon">✦</span>
                <span className="toolbar-label">Asistente IA</span>
                {propuestaIa && <span className="toolbar-badge-dot" title="Propuesta pendiente" />}
              </button>
            )}
            {!puedeEditar && propuestaIa && (
              <button
                type="button"
                className="toolbar-panel-toggle ia-toggle"
                disabled={m.enviando}
                onClick={() => setVerAsistenteIa(true)}
                title="Ver propuesta de IA pendiente"
              >
                <span className="toolbar-icon">✦</span>
                <span className="toolbar-label">Propuesta IA</span>
              </button>
            )}
          </div>

          <div className="toolbar-divider" />

          <div className="toolbar-model-badge" title={`Modelo UML: ${m.modelo.nombre}`}>
            <span className="model-dot">◇</span>
            <span className="model-name">{m.modelo.nombre}</span>
          </div>
        </div>

        {/* Sección Derecha: Interoperabilidad, Calidad, Creación y Sincronización */}
        <div className="toolbar-section toolbar-section-right">
          {/* Menú de Interoperabilidad */}
          <div className="toolbar-dropdown" ref={dropdownInteroperabilidadRef}>
            <button
              type="button"
              className={`toolbar-dropdown-trigger ${dropdownInteroperabilidad ? 'open' : ''}`}
              onClick={() => {
                setDropdownInteroperabilidad((v) => !v);
                setDropdownCalidad(false);
              }}
              title="Opciones de exportación, importación y generación de backend"
            >
              <span className="toolbar-icon">📁</span>
              <span className="toolbar-label">Interoperabilidad</span>
              <span className="dropdown-arrow">▾</span>
            </button>
            {dropdownInteroperabilidad && (
              <div className="toolbar-dropdown-menu">
                <button
                  type="button"
                  id="btn-exportar-xmi"
                  className="toolbar-dropdown-item"
                  onClick={() => {
                    setDropdownInteroperabilidad(false);
                    setVerExportarXmi(true);
                  }}
                  disabled={!!borrador}
                >
                  <span className="item-icon">📤</span>
                  <div>
                    <b>Exportar XMI</b>
                    <small>UML 2.5.1 / perfiles XMI y extensión EA</small>
                  </div>
                </button>
                {puedeEditar && (
                  <button
                    type="button"
                    id="btn-importar-xmi"
                    className="toolbar-dropdown-item"
                    onClick={() => {
                      setDropdownInteroperabilidad(false);
                      setVerImportarXmi(true);
                    }}
                    disabled={bloqueado || !!borrador}
                  >
                    <span className="item-icon">📥</span>
                    <div>
                      <b>Importar XMI</b>
                      <small>Cargar modelo desde archivo XMI externo</small>
                    </div>
                  </button>
                )}
                <button
                  type="button"
                  id="btn-generar-backend"
                  className="toolbar-dropdown-item"
                  onClick={() => {
                    setDropdownInteroperabilidad(false);
                    setVerGenerarBackend(true);
                  }}
                  disabled={!!borrador}
                >
                  <span className="item-icon">⚡</span>
                  <div>
                    <b>Generar Backend</b>
                    <small>Crear proyecto Spring Boot con entidades JPA</small>
                  </div>
                </button>
              </div>
            )}
          </div>

          {/* Menú de Calidad & Versiones */}
          <div className="toolbar-dropdown" ref={dropdownCalidadRef}>
            <button
              type="button"
              className={`toolbar-dropdown-trigger ${dropdownCalidad ? 'open' : ''} ${totalConflictos ? 'has-warning' : ''}`}
              onClick={() => {
                setDropdownCalidad((v) => !v);
                setDropdownInteroperabilidad(false);
              }}
              title="Validación UML, historial de versiones y resolución de conflictos"
            >
              <span className="toolbar-icon">🛡</span>
              <span className="toolbar-label">Calidad & Control</span>
              {totalConflictos > 0 && (
                <span className="toolbar-conflict-pill">{totalConflictos}</span>
              )}
              <span className="dropdown-arrow">▾</span>
            </button>
            {dropdownCalidad && (
              <div className="toolbar-dropdown-menu">
                <button
                  type="button"
                  className="toolbar-dropdown-item"
                  onClick={() => {
                    setDropdownCalidad(false);
                    setInforme((v) => !v);
                  }}
                >
                  <span className="item-icon">✓</span>
                  <div>
                    <b>Informe de validación</b>
                    <small>Revisar consistencia, ciclos y buenas prácticas UML</small>
                  </div>
                </button>
                <button
                  type="button"
                  id="btn-historial"
                  className="toolbar-dropdown-item"
                  onClick={() => {
                    setDropdownCalidad(false);
                    setVerHistorial(true);
                  }}
                >
                  <span className="item-icon">⏱</span>
                  <div>
                    <b>Historial de versiones</b>
                    <small>Ver auditoría de revisiones y restaurar estados</small>
                  </div>
                </button>
                <button
                  type="button"
                  className="toolbar-dropdown-item"
                  onClick={() => {
                    setDropdownCalidad(false);
                    setMostrarConflictos(true);
                  }}
                >
                  <span className="item-icon">⚠</span>
                  <div>
                    <b>Conflictos de concurrencia</b>
                    <small>
                      {totalConflictos
                        ? `${totalConflictos} conflictos detectados pendientes de revisión`
                        : 'Sin conflictos de edición en este momento'}
                    </small>
                  </div>
                </button>
              </div>
            )}
          </div>

          <div className="toolbar-divider" />

          {/* Acciones de Creación rápida en el modelo */}
          <div className="toolbar-group creation-group" aria-label="Crear elementos">
            {puedeOrganizar && (
              <button
                type="button"
                className="toolbar-btn"
                disabled={bloqueado || !!borrador}
                onClick={() =>
                  setBorrador({ entidad: 'PAQUETE', nuevo: true, id: crypto.randomUUID() })
                }
                title="Crear un nuevo paquete contenedor"
              >
                <span className="toolbar-icon">▱</span>
                <span className="toolbar-label">Paquete</span>
              </button>
            )}
            {puedeEditar && (
              <>
                <button
                  type="button"
                  className="toolbar-btn"
                  disabled={bloqueado || !!borrador || !m.modelo.clases.length}
                  onClick={() =>
                    setBorrador({ entidad: 'RELACION', nuevo: true, id: crypto.randomUUID() })
                  }
                  title="Conectar dos clases con una relación"
                >
                  <span className="toolbar-icon">↔</span>
                  <span className="toolbar-label">Relación</span>
                </button>
                <button
                  type="button"
                  className="toolbar-btn primary"
                  disabled={bloqueado || !!borrador}
                  onClick={() =>
                    setBorrador({ entidad: 'CLASE', nuevo: true, id: crypto.randomUUID() })
                  }
                  title="Crear una nueva clase en el modelo"
                >
                  <span className="toolbar-icon">＋</span>
                  <span className="toolbar-label">Nueva clase</span>
                </button>
              </>
            )}
          </div>

          {/* Sincronización */}
          <button
            type="button"
            className="toolbar-btn reload-btn"
            onClick={m.recargar}
            disabled={m.enviando || (!!m.pendiente && !m.conflicto)}
            title="Sincronizar y recargar modelo desde el servidor"
          >
            ↻
          </button>
        </div>
      </div>

      <div className="presence-strip" aria-label="Participantes conectados">
        <span className={'connection-state ' + m.colaboracion.toLowerCase()}>
          {m.colaboracion === 'CONECTADO'
            ? 'Colaboración conectada'
            : m.colaboracion === 'CONECTANDO'
              ? 'Conectando colaboración…'
              : 'Colaboración limitada'}
        </span>
        <div>
          {m.sesion?.participantes.map((p) => (
            <span
              className={'participant ' + p.estado.toLowerCase()}
              key={p.idUsuario}
              style={{
                color: colorParticipante(p.idUsuario, m.sesion?.participantes ?? []),
                borderColor: 'currentColor',
              }}
            >
              <b>{p.nombre}</b> · {p.modo === 'EDITOR' ? 'editor' : 'lector'} ·{' '}
              {p.estado === 'EN_LINEA' ? 'en línea' : 'ausente'}
              {p.tipoEntidadEnfocada ? ' · ' + p.tipoEntidadEnfocada.toLowerCase() : ''}
            </span>
          ))}
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

      {mostrarConflictos && (
        <ConflictosPanel
          modelo={m.modelo.id}
          sesion={m.sesion?.id ?? null}
          conectado={m.colaboracion === 'CONECTADO'}
          senal={m.senalConflictos}
          cerrar={() => setMostrarConflictos(false)}
          cambiarTotal={cambiarTotalConflictos}
          resuelto={m.recargar}
        />
      )}

      {verExportarXmi && (
        <ExportarXmiDialog
          modelo={m.modelo.id}
          nombreModelo={m.modelo.nombre}
          versionVigente={m.modelo.idVersion}
          paquetes={m.modelo.paquetes}
          cerrar={() => setVerExportarXmi(false)}
        />
      )}

      {verImportarXmi && m.sesion && (
        <ImportarXmiDialog
          modelo={m.modelo.id}
          sesion={m.sesion.id}
          cerrar={() => setVerImportarXmi(false)}
          alAplicar={async () => {
            await m.recargar();
          }}
        />
      )}

      {verGenerarBackend && (
        <GenerarBackendDialog
          modelo={m.modelo.id}
          nombreModelo={m.modelo.nombre}
          cerrar={() => setVerGenerarBackend(false)}
          modeloObj={m.modelo}
          onCambiar={m.cambiar}
          onCambiarLote={m.cambiarLote}
          onAbrirIa={abrirIaConPrompt}
          onAbrirValidacion={() => setInforme(true)}
          onLocalizar={(elemento, claseId) => {
            setSeleccion(
              m.modelo!.relaciones.some((r) => r.id === elemento) ? elemento : claseId || elemento,
            );
            void flujo.current?.fitView({
              nodes: [{ id: claseId || elemento }],
              maxZoom: 1,
              padding: 0.6,
              duration: 300,
            });
          }}
        />
      )}

      {verImagenIa && (
        <SubirFotoPizarraModal
          modelo={m.modelo.id}
          cerrar={() => setVerImagenIa(false)}
          alGenerar={abrirPropuestaGenerada}
        />
      )}

      {verPropuestaIa && propuestaIa && (
        <RevisarPropuestaDialog
          modelo={m.modelo.id}
          propuesta={propuestaIa}
          soloLectura={!puedeEditar}
          cerrar={() => setVerPropuestaIa(false)}
          alActualizar={setPropuestaIa}
          alAplicar={async () => {
            setVerPropuestaIa(false);
            setPropuestaIa(null);
            await m.recargar();
          }}
          alRechazar={async () => {
            setVerPropuestaIa(false);
            setPropuestaIa((await leerPropuestaVigente(m.modelo!.id)) ?? null);
          }}
        />
      )}

      <div className="editor-body">
        {/* Panel izquierdo estilo Enterprise Architect: Paleta (Toolbox) y Explorador (Tree) */}
        {verPanelIzquierdo && (
          <aside className="editor-sidebar-left">
            <div className="sidebar-tabs" role="tablist">
              <button
                className={`sidebar-tab ${tabIzquierda === 'PALETA' ? 'active' : ''}`}
                onClick={() => setTabIzquierda('PALETA')}
                role="tab"
                aria-selected={tabIzquierda === 'PALETA'}
                title="Caja de Herramientas UML (Arrastrar elementos)"
              >
                🧰 Paleta
              </button>
              <button
                className={`sidebar-tab ${tabIzquierda === 'EXPLORADOR' ? 'active' : ''}`}
                onClick={() => setTabIzquierda('EXPLORADOR')}
                role="tab"
                aria-selected={tabIzquierda === 'EXPLORADOR'}
                title="Explorador de elementos del modelo"
              >
                🗂 Explorador ({m.modelo.clases.length})
              </button>
              <button
                type="button"
                className="sidebar-tab-close"
                onClick={() => setVerPanelIzquierdo(false)}
                title="Ocultar panel lateral"
              >
                ◀
              </button>
            </div>

            {tabIzquierda === 'PALETA' ? (
              <CajaHerramientas
                puedeEditar={!!puedeEditar}
                bloqueado={bloqueado}
                conectorActivo={conectorActivo}
                alIniciarConector={(tipo) => {
                  if (conectorActivo === tipo) {
                    setConectorActivo(null);
                    setOrigenConector(null);
                  } else {
                    setConectorActivo(tipo);
                    setOrigenConector(null);
                  }
                }}
              />
            ) : (
              <div className="outline-content">
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
              </div>
            )}
            <p className="outline-note">
              {puedeEditar
                ? 'Arrastra elementos desde la Paleta al lienzo. Conecta los 4 puntos de cualquier clase para relacionarlas.'
                : 'Tienes acceso de solo lectura a este modelo.'}
            </p>
          </aside>
        )}

        {/* Lienzo de modelado interactivo */}
        <div
          className="canvas"
          onPointerMove={(e) => {
            if (!flujo.current) return;
            m.enviarCursor(
              flujo.current.screenToFlowPosition({ x: e.clientX, y: e.clientY }),
              conectorActivo
                ? 'CONECTANDO'
                : borrador
                  ? 'EDITANDO'
                  : e.buttons === 1 && (e.target as HTMLElement).closest('.react-flow__node')
                    ? 'MOVIENDO'
                    : 'EXPLORANDO',
            );
          }}
          onPointerLeave={() => m.enviarCursor(null)}
          style={{ cursor: conectorActivo ? 'crosshair' : 'default' }}
          onDragOver={(e) => {
            if (puedeEditar && !bloqueado) {
              e.preventDefault();
              e.dataTransfer.dropEffect = 'move';
            }
          }}
          onDrop={manejarSoltarLienzo}
        >
          {conectorActivo && (
            <div className="canvas-connector-banner" role="status">
              <span className="banner-icon">⚡</span>
              <span>
                {!origenConector ? (
                  <>
                    Modo conector <b>{conectorActivo}</b> activo: Haz clic en la clase <b>ORIGEN</b>
                    .
                  </>
                ) : (
                  <>
                    Origen: <b>{m.modelo?.clases.find((c) => c.id === origenConector)?.nombre}</b>.
                    Ahora haz clic en la clase <b>DESTINO</b>.
                  </>
                )}
              </span>
              <button
                type="button"
                className="banner-cancel-btn"
                onClick={() => {
                  setConectorActivo(null);
                  setOrigenConector(null);
                }}
                title="Cancelar modo conector (Escape)"
              >
                Cancelar (Esc)
              </button>
            </div>
          )}

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
            nodesDraggable={!!puedeOrganizar && !bloqueado && !borrador}
            nodesConnectable={!!puedeEditar && !bloqueado && !borrador}
            edgesReconnectable={!!puedeEditar && !bloqueado && !borrador}
            reconnectRadius={20}
            onReconnect={manejarReconectar}
            deleteKeyCode={null}
            onNodesChange={(changes) => setNodes((ns) => applyNodeChanges(changes, ns))}
            onNodeClick={async (_, n) => {
              if (conectorActivo) {
                if (!origenConector) {
                  // Primer clic: seleccionar clase origen
                  setOrigenConector(n.id);
                  setSeleccion(n.id);
                } else if (origenConector !== n.id) {
                  // Segundo clic: clase destino seleccionada -> crear relación
                  const idNuevaRelacion = crypto.randomUUID();
                  const tipo = conectorActivo;
                  const nombreRelacion = null;

                  const ok = await guardarRef.current({
                    operacion: 'CREAR',
                    entidad: 'RELACION',
                    idEntidad: idNuevaRelacion,
                    datos: {
                      origen: origenConector,
                      destino: n.id,
                      nombre: nombreRelacion,
                      tipo,
                      direccion:
                        tipo === 'GENERALIZACION' || tipo === 'DEPENDENCIA'
                          ? 'ORIGEN_A_DESTINO'
                          : 'BIDIRECCIONAL',
                      rolOrigen: '',
                      rolDestino: '',
                      ...(!['GENERALIZACION', 'DEPENDENCIA'].includes(tipo)
                        ? {
                            minimoOrigen: 1,
                            maximoOrigen: 1,
                            minimoDestino: tipo === 'COMPOSICION' ? 1 : 0,
                            maximoDestino: null,
                          }
                        : {}),
                    },
                  });

                  if (ok) {
                    setSeleccion(idNuevaRelacion);
                    setBorrador(null);
                  }
                  setConectorActivo(null);
                  setOrigenConector(null);
                }
                return;
              }
              if (!borrador) setSeleccion(n.id);
            }}
            onEdgeClick={(_, e) => {
              if (!borrador && !conectorActivo) setSeleccion(e.id);
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
            <ViewportPortal>
              {m.sesion?.participantes
                .filter(
                  (p) =>
                    p.idUsuario !== identidad?.usuario.id &&
                    p.estado === 'EN_LINEA' &&
                    m.cursores[p.idUsuario],
                )
                .map((p) => {
                  const cursor = m.cursores[p.idUsuario];
                  const color = colorParticipante(p.idUsuario, m.sesion?.participantes ?? []);
                  return (
                    <div
                      key={p.idUsuario}
                      className="remote-cursor"
                      style={{ transform: `translate(${cursor.x}px, ${cursor.y}px)`, color }}
                    >
                      <svg width="18" height="22" viewBox="0 0 18 22" aria-hidden="true">
                        <path
                          d="M1 1 L1 18 L5 14 L9 21 L12 19 L8 12 L16 12 Z"
                          fill="currentColor"
                          stroke="white"
                          strokeWidth="1.5"
                        />
                      </svg>
                      <span style={{ backgroundColor: color }}>
                        {p.nombre}
                        <small>{accionesPresencia[cursor.accion] ?? 'explorando'}</small>
                      </span>
                    </div>
                  );
                })}
            </ViewportPortal>
            <Background color="#cdd8d4" gap={22} />
            <Controls showInteractive={false} />
            <MiniMap nodeColor="#cedfd8" />
          </ReactFlow>

          {nodes.length === 0 && (
            <div className="canvas-empty">
              <span>◇</span>
              <h2>Lienzo de Modelado de Clases</h2>
              <p>
                {puedeEditar
                  ? 'Arrastra una Clase desde la Paleta lateral o pulsa "+ Nueva clase" para comenzar.'
                  : 'Este modelo todavía no tiene clases.'}
              </p>
            </div>
          )}
        </div>

        {/* Panel lateral derecho unificado con pestañas (Exclusión mutua: Propiedades / Asistente IA / Validación) */}
        {tabDerecha && (
          <aside className="editor-sidebar-right">
            <div className="sidebar-tabs sidebar-tabs-right" role="tablist">
              <button
                className={`sidebar-tab ${tabDerecha === 'PROPIEDADES' ? 'active' : ''}`}
                onClick={() => setTabDerecha('PROPIEDADES')}
                role="tab"
                aria-selected={tabDerecha === 'PROPIEDADES'}
                title="Propiedades del elemento seleccionado"
              >
                🎛 Propiedades
              </button>
              <button
                className={`sidebar-tab ${tabDerecha === 'ASISTENTE_IA' ? 'active' : ''}`}
                onClick={() => setTabDerecha('ASISTENTE_IA')}
                role="tab"
                aria-selected={tabDerecha === 'ASISTENTE_IA'}
                title="Agente Asistente IA"
              >
                ✦ Asistente IA
                {propuestaIa && <span className="toolbar-badge-dot" title="Propuesta pendiente" />}
              </button>
              <button
                className={`sidebar-tab ${tabDerecha === 'VALIDACION' ? 'active' : ''}`}
                onClick={() => setTabDerecha('VALIDACION')}
                role="tab"
                aria-selected={tabDerecha === 'VALIDACION'}
                title="Informe de Validación del modelo"
              >
                🛡 Validación
              </button>
              <button
                type="button"
                className="sidebar-tab-close"
                onClick={() => setTabDerecha(null)}
                title="Ocultar panel derecho"
              >
                ▶
              </button>
            </div>

            <div className="sidebar-right-body">
              {tabDerecha === 'ASISTENTE_IA' && (
                <PanelAsistenteIa
                  modelo={m.modelo.id}
                  revision={m.modelo.revision}
                  disabled={bloqueado || !puedeEditar}
                  propuestaPendiente={propuestaIa}
                  cerrar={() => {
                    setTabDerecha(null);
                    setPromptIa(null);
                  }}
                  alAplicarPropuesta={async () => {
                    setPropuestaIa(null);
                    await m.recargar();
                  }}
                  abrirImagen={() => setVerImagenIa(true)}
                  nombreProyecto={proyecto.nombre}
                  clasesExistentes={m.modelo.clases}
                  claseSeleccionada={clase?.nombre ?? null}
                  promptInicial={promptIa}
                />
              )}

              {tabDerecha === 'VALIDACION' && (
                <div className="validation-sidebar-container">
                  <InformeValidacion
                    modelo={m.modelo.id}
                    revision={m.modelo.revision}
                    puedeValidar={m.modelo.puedeValidar}
                    bloqueado={bloqueado || !!borrador}
                    cerrar={() => setTabDerecha(null)}
                    modeloObj={m.modelo}
                    onCambiar={m.cambiar}
                    onCambiarLote={m.cambiarLote}
                    onAbrirIa={abrirIaConPrompt}
                    onAbrirGenerarBackend={() => setVerGenerarBackend(true)}
                    localizar={(elemento, claseId) => {
                      setSeleccion(
                        m.modelo!.relaciones.some((r) => r.id === elemento)
                          ? elemento
                          : claseId || elemento,
                      );
                      void flujo.current?.fitView({
                        nodes: [{ id: claseId || elemento }],
                        maxZoom: 1,
                        padding: 0.6,
                        duration: 300,
                      });
                    }}
                  />
                </div>
              )}

              {tabDerecha === 'PROPIEDADES' && (
                <div className="inspector-panel-wrapper">
                  <div className="inspector-header">
                    <span className="eyebrow">PROPIEDADES</span>
                    <button
                      type="button"
                      className="inspector-close-btn"
                      onClick={() => setTabDerecha(null)}
                      title="Ocultar panel de propiedades"
                    >
                      ×
                    </button>
                  </div>
                  {borrador ? (
                    <FormularioElemento
                      key={borrador.id}
                      borrador={borrador}
                      modelo={m.modelo}
                      bloqueado={bloqueado}
                      cancelar={() => setBorrador(null)}
                      guardar={guardar}
                      alCrearClaseIntermedia={crearClaseIntermedia}
                    />
                  ) : clase ? (
                    <>
                      <h2>{clase.nombre}</h2>
                      <p className="muted">{clase.descripcion || 'Sin descripción'}</p>
                      <span className="badge">
                        {clase.es_persistente ? 'Persistente' : 'No persistente'}
                      </span>

                      {/* Inserción rápida de atributo */}
                      {puedeEditar && (
                        <div className="quick-attribute-box">
                          <span className="quick-attribute-title">AÑADIR ATRIBUTO RÁPIDO</span>
                          <div className="quick-attribute-form">
                            <input
                              placeholder="nombreAtributo"
                              value={rapidoNombre}
                              onChange={(e) => setRapidoNombre(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') {
                                  e.preventDefault();
                                  void agregarAtributoRapido();
                                }
                              }}
                              className="quick-attr-name"
                            />
                            <div className="quick-attr-type-row">
                              <input
                                placeholder="Tipo (int, String…)"
                                value={rapidoTipo}
                                list="lista-tipos-rapidos"
                                onChange={(e) => setRapidoTipo(e.target.value)}
                                className="quick-attr-type"
                              />
                              <datalist id="lista-tipos-rapidos">
                                <option value="String" />
                                <option value="int" />
                                <option value="float" />
                                <option value="Float" />
                                <option value="double" />
                                <option value="boolean" />
                                <option value="LocalDate" />
                                <option value="LocalDateTime" />
                                <option value="UUID" />
                              </datalist>
                              <select
                                value={rapidoVisibilidad}
                                onChange={(e) => setRapidoVisibilidad(e.target.value)}
                                className="quick-attr-visibility"
                              >
                                <option value="PRIVADO">- Privado</option>
                                <option value="PUBLICO">+ Público</option>
                                <option value="PROTEGIDO"># Protegido</option>
                                <option value="PAQUETE">~ Paquete</option>
                              </select>
                              <button
                                type="button"
                                className="primary quick-attr-btn"
                                disabled={!rapidoNombre.trim() || bloqueado}
                                onClick={agregarAtributoRapido}
                                title="Añadir atributo inmediatamente"
                              >
                                ＋
                              </button>
                            </div>
                          </div>
                        </div>
                      )}

                      <div className="outline-title">
                        Atributos{' '}
                        <span>
                          {m.modelo.atributos.filter((a) => a.id_clase === clase.id).length}
                        </span>
                      </div>
                      {m.modelo.atributos
                        .filter((a) => a.id_clase === clase.id)
                        .map((a) => (
                          <div className="attribute-row" key={a.id}>
                            <span>
                              {simbolo(a.visibilidad)} {a.nombre}: {a.tipo_uml}
                            </span>
                            {puedeEditar && (
                              <div>
                                <button
                                  disabled={bloqueado}
                                  onClick={() =>
                                    setBorrador({
                                      entidad: 'ATRIBUTO',
                                      id: a.id,
                                      idClase: a.id_clase,
                                      nuevo: false,
                                      atributo: a,
                                    })
                                  }
                                >
                                  Editar
                                </button>
                                <button
                                  className="danger"
                                  disabled={bloqueado}
                                  onClick={() => eliminar('ATRIBUTO', a.id)}
                                >
                                  ×
                                </button>
                              </div>
                            )}
                          </div>
                        ))}
                      {puedeEditar && (
                        <button
                          disabled={bloqueado}
                          onClick={() =>
                            setBorrador({
                              entidad: 'ATRIBUTO',
                              idClase: clase.id,
                              nuevo: true,
                              id: crypto.randomUUID(),
                            })
                          }
                        >
                          ＋ Añadir atributo (avanzado)
                        </button>
                      )}
                      <div className="outline-title">
                        Operaciones{' '}
                        <span>
                          {m.modelo.operaciones.filter((o) => o.id_clase === clase.id).length}
                        </span>
                      </div>
                      {m.modelo.operaciones
                        .filter((o) => o.id_clase === clase.id)
                        .map((o) => (
                          <div className="attribute-row" key={o.id}>
                            <span>
                              {simbolo(o.visibilidad)} {o.nombre}(): {o.tipo_retorno}
                            </span>
                            {puedeEditar && (
                              <div>
                                <button
                                  disabled={bloqueado}
                                  onClick={() =>
                                    setBorrador({
                                      entidad: 'OPERACION',
                                      id: o.id,
                                      idClase: o.id_clase,
                                      nuevo: false,
                                      operacion: o,
                                    })
                                  }
                                >
                                  Editar
                                </button>
                                <button
                                  className="danger"
                                  disabled={bloqueado}
                                  onClick={() => eliminar('OPERACION', o.id)}
                                >
                                  ×
                                </button>
                              </div>
                            )}
                          </div>
                        ))}
                      {puedeEditar && (
                        <button
                          disabled={bloqueado}
                          onClick={() =>
                            setBorrador({
                              entidad: 'OPERACION',
                              idClase: clase.id,
                              nuevo: true,
                              id: crypto.randomUUID(),
                            })
                          }
                        >
                          ＋ Añadir operación
                        </button>
                      )}
                      {puedeEditar && (
                        <>
                          <button
                            className="primary wide"
                            disabled={bloqueado}
                            onClick={() =>
                              setBorrador({ entidad: 'CLASE', id: clase.id, nuevo: false, clase })
                            }
                          >
                            Editar propiedades de la clase
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
                      <div className="relation-inspector-card">
                        <span className="relation-tag">{relacion.tipo_relacion}</span>
                        {relacion.tipo_relacion === 'COMPOSICION' && (
                          <p className="relation-note">
                            ◆ <b>Composición:</b> Rombo negro en origen. Pertenencia fuerte
                            existencial.
                          </p>
                        )}
                        {relacion.tipo_relacion === 'AGREGACION' && (
                          <p className="relation-note">
                            ◇ <b>Agregación:</b> Rombo blanco en origen. Pertenencia débil
                            compartida.
                          </p>
                        )}
                        {relacion.tipo_relacion === 'GENERALIZACION' && (
                          <p className="relation-note">
                            ▷ <b>Herencia:</b> Flecha triangular en destino.{' '}
                            La subclase est? en origen y la superclase en destino. El nombre no define conjuntos de generalizaci?n.
                          </p>
                        )}
                      </div>
                      <p className="muted">
                        {multiplicidad(relacion.minimo_origen, relacion.maximo_origen)} →{' '}
                        {multiplicidad(relacion.minimo_destino, relacion.maximo_destino)}
                      </p>
                      {puedeEditar && (
                        <>
                          <button
                            disabled={bloqueado}
                            onClick={() =>
                              setBorrador({
                                entidad: 'RELACION',
                                id: relacion.id,
                                nuevo: false,
                                relacion,
                              })
                            }
                          >
                            Editar relación completa
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
                      {puedeOrganizar && (
                        <>
                          <button
                            disabled={bloqueado}
                            onClick={() =>
                              setBorrador({
                                entidad: 'PAQUETE',
                                id: paquete.id,
                                nuevo: false,
                                paquete,
                              })
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
                      Selecciona una clase, relación o paquete para consultar sus propiedades o
                      arrastra una clase desde la Paleta al lienzo.
                    </div>
                  )}
                </div>
              )}
            </div>
          </aside>
        )}
      </div>

      <footer className="editor-footer">
        <span>
          {m.modelo.clases.length} clases · {m.modelo.atributos.length} atributos ·{' '}
          {m.modelo.operaciones.length} operaciones · {m.modelo.relaciones.length} relaciones
        </span>
        <span>{puedeEditar ? 'Edición colaborativa activa' : 'Solo lectura'}</span>
      </footer>
    </div>
  );
}
