import { useCallback, useEffect, useId, useState, type FormEvent } from 'react';
import { ApiError } from '../../../shared/api';
import {
  actualizarProyecto,
  cambiarEstadoProyecto,
  consultarProyecto,
  type Proyecto,
  type ActualizacionProyecto,
  type EstadoProyecto,
} from '../api/proyectosApi';

type Solicitud =
  | { accion: 'actualizar'; datos: ActualizacionProyecto }
  | { accion: 'archivar' | 'reactivar'; datos: EstadoProyecto };
export function ProyectoForm({
  proyecto,
  accion,
  cerrar,
  guardado,
  registrarSalida,
}: {
  proyecto: Proyecto;
  accion: Solicitud['accion'];
  cerrar: () => void;
  guardado: (p: Proyecto) => void;
  registrarSalida: (g: (() => boolean) | null) => void;
}) {
  const [actual, setActual] = useState(proyecto);
  const descripcionId = useId();
  const [nombre, setNombre] = useState(proyecto.nombre),
    [codigo, setCodigo] = useState(proyecto.codigo),
    [descripcion, setDescripcion] = useState(proyecto.descripcion ?? '');
  const [confirmar, setConfirmar] = useState(false),
    [enviando, setEnviando] = useState(false),
    [error, setError] = useState('');
  const [pendiente, setPendiente] = useState<Solicitud | null>(null),
    [conflicto, setConflicto] = useState(false);
  const sucio =
    nombre !== proyecto.nombre ||
    codigo !== proyecto.codigo ||
    descripcion !== (proyecto.descripcion ?? '') ||
    confirmar ||
    !!pendiente;
  const salida = useCallback(
    () => !enviando && (!sucio || window.confirm('¿Salir y descartar el borrador del proyecto?')),
    [enviando, sucio],
  );
  useEffect(() => {
    registrarSalida(salida);
    return () => registrarSalida(null);
  }, [registrarSalida, salida]);
  useEffect(() => {
    const proteger = (e: BeforeUnloadEvent) => {
      if (sucio || enviando) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', proteger);
    return () => window.removeEventListener('beforeunload', proteger);
  }, [sucio, enviando]);
  async function enviar(s: Solicitud) {
    setEnviando(true);
    setError('');
    try {
      const p =
        s.accion === 'actualizar'
          ? await actualizarProyecto(actual.id, s.datos)
          : await cambiarEstadoProyecto(actual.id, s.accion, s.datos);
      guardado(p);
    } catch (e) {
      const fallo = e as ApiError;
      setError(fallo.message);
      if (fallo.status !== 0 && fallo.status < 500) {
        setPendiente(null);
        setConflicto(fallo.status === 409 || fallo.status === 403 || fallo.status === 404);
      }
    } finally {
      setEnviando(false);
    }
  }
  function guardar(e: FormEvent) {
    e.preventDefault();
    const s: Solicitud =
      accion === 'actualizar'
        ? {
            accion,
            datos: {
              codigo,
              nombre,
              descripcion,
              revision: actual.revision,
              idSolicitudCliente: crypto.randomUUID(),
            },
          }
        : {
            accion,
            datos: {
              revision: actual.revision,
              idSolicitudCliente: crypto.randomUUID(),
              confirmar,
            },
          };
    setPendiente(s);
    void enviar(s);
  }
  async function recargar() {
    setEnviando(true);
    try {
      const p = await consultarProyecto(actual.id);
      setActual(p);
      setConflicto(false);
      setError(
        'Revisión recargada. Compara el estado actual con tu borrador antes de confirmar de nuevo.',
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }
  const permite =
    actual.puedeGestionarProyecto &&
    (accion === 'reactivar' ? actual.estado === 'ARCHIVADO' : actual.estado === 'ACTIVO');
  return (
    <form onSubmit={guardar} className="project-edit">
      <h3>
        {accion === 'actualizar'
          ? 'Actualizar proyecto'
          : accion === 'archivar'
            ? 'Archivar proyecto'
            : 'Reactivar proyecto'}
      </h3>
      {accion === 'actualizar' ? (
        <>
          <label>
            Nombre del proyecto
            <input
              required
              maxLength={180}
              value={nombre}
              disabled={enviando || !!pendiente}
              onChange={(e) => setNombre(e.target.value)}
              autoFocus
            />
          </label>
          <label>
            Código
            <input
              required
              pattern="[A-Za-z0-9_.-]+"
              maxLength={80}
              value={codigo}
              disabled={enviando || !!pendiente}
              onChange={(e) => setCodigo(e.target.value)}
            />
          </label>
          <div className="project-description-field">
            <label htmlFor={descripcionId}>Descripción</label>
            <textarea
              id={descripcionId}
              maxLength={4000}
              rows={3}
              value={descripcion}
              disabled={enviando || !!pendiente}
              onChange={(e) => setDescripcion(e.target.value)}
            />
          </div>
        </>
      ) : (
        <>
          <p>
            {accion === 'archivar'
              ? 'Los datos del proyecto y la edición UML quedarán en solo lectura. La gestión de accesos sigue disponible. Las sesiones de edición activas deben finalizar antes del archivo.'
              : 'El proyecto volverá a estar activo; la edición seguirá dependiendo de los permisos vigentes de cada miembro.'}
          </p>
          <label className="check-label">
            <input
              type="checkbox"
              checked={confirmar}
              disabled={enviando || !!pendiente}
              onChange={(e) => setConfirmar(e.target.checked)}
              required
            />
            Comprendo y confirmo el cambio de estado
          </label>
        </>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {conflicto && (
        <button type="button" disabled={enviando} onClick={recargar}>
          Recargar revisión y conservar borrador
        </button>
      )}
      {actual.revision !== proyecto.revision && (
        <div className="current-project">
          <b>Datos actuales consultados</b>
          <p>
            {actual.codigo} · {actual.nombre} · {actual.estado}
          </p>
          <p>{actual.descripcion || 'Sin descripción'}</p>
        </div>
      )}
      {!permite && (
        <p className="muted">
          El estado o los permisos actuales impiden confirmar. Tu borrador se conserva.
        </p>
      )}
      <div className="form-actions">
        <button
          type="button"
          disabled={enviando}
          onClick={() => {
            if (salida()) cerrar();
          }}
        >
          Cancelar edición
        </button>
        {pendiente ? (
          <button
            type="button"
            className="primary"
            disabled={enviando}
            onClick={() => enviar(pendiente)}
          >
            Reintentar la misma solicitud
          </button>
        ) : (
          <button className="primary" disabled={enviando || conflicto || !permite}>
            {enviando
              ? 'Confirmando…'
              : accion === 'actualizar'
                ? 'Guardar proyecto'
                : 'Confirmar cambio de estado'}
          </button>
        )}
      </div>
    </form>
  );
}
