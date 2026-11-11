import { useCallback, useEffect, useRef, useState } from 'react';
import { consultarProyecto, consultarEquipo, type Proyecto } from '../api/proyectosApi';
import { ProyectoForm } from './ProyectoForm';
import { ActividadPanel } from './ActividadPanel';

export function ProyectoResumen({
  proyecto,
  cerrar,
  abrir,
  actualizado,
  registrarSalida,
}: {
  proyecto: string;
  cerrar: () => void;
  abrir: (p: Proyecto) => void;
  actualizado: () => void;
  registrarSalida: (g: (() => boolean) | null) => void;
}) {
  const [datos, setDatos] = useState<Proyecto | null>(null),
    [error, setError] = useState(''),
    [cargando, setCargando] = useState(true),
    [recarga, setRecarga] = useState(0);
  const [pestana, setPestana] = useState<'resumen' | 'equipo' | 'actividad'>('resumen');
  const [equipo, setEquipo] = useState<Awaited<ReturnType<typeof consultarEquipo>> | null>(null);
  const [accion, setAccion] = useState<'actualizar' | 'archivar' | 'reactivar' | null>(null);
  const guardia = useRef<(() => boolean) | null>(null);
  const registrarForm = useCallback((g: (() => boolean) | null) => {
    guardia.current = g;
  }, []);
  const salida = useCallback(() => !guardia.current || guardia.current(), []);
  useEffect(() => {
    registrarSalida(salida);
    return () => registrarSalida(null);
  }, [registrarSalida, salida]);
  useEffect(() => {
    let activo = true;
    setCargando(true);
    setError('');
    consultarProyecto(proyecto)
      .then((p) => {
        if (activo) setDatos(p);
      })
      .catch((e) => {
        if (activo) {
          setDatos(null);
          setError(e.message);
        }
      })
      .finally(() => {
        if (activo) setCargando(false);
      });
    return () => {
      activo = false;
    };
  }, [proyecto, recarga]);
  useEffect(() => {
    if (pestana !== 'equipo') return;
    let activo = true;
    setEquipo(null);
    setError('');
    consultarEquipo(proyecto)
      .then((r) => {
        if (activo) setEquipo(r);
      })
      .catch((e) => {
        if (activo) setError(e.message);
      });
    return () => {
      activo = false;
    };
  }, [proyecto, pestana, recarga]);
  function cambiar(p: typeof pestana) {
    if (salida()) {
      setAccion(null);
      setPestana(p);
      setError('');
    }
  }
  return (
    <div className="modal-backdrop">
      <section
        className="modal modal-wide project-summary"
        role="dialog"
        aria-modal="true"
        aria-labelledby="resumen-titulo"
      >
        <div className="panel-heading">
          <div>
            <span className="eyebrow">PROYECTO</span>
            <h2 id="resumen-titulo">{datos?.nombre ?? 'Resumen del proyecto'}</h2>
          </div>
          <button
            onClick={() => {
              if (salida()) cerrar();
            }}
          >
            Volver a mis proyectos
          </button>
        </div>
        {cargando ? (
          <p className="muted">Consultando permisos y resumen…</p>
        ) : (
          datos && (
            <>
              <nav className="section-tabs" aria-label="Consulta del proyecto">
                <button
                  className={pestana === 'resumen' ? 'primary' : ''}
                  onClick={() => cambiar('resumen')}
                >
                  Resumen
                </button>
                <button
                  className={pestana === 'equipo' ? 'primary' : ''}
                  onClick={() => cambiar('equipo')}
                >
                  Equipo
                </button>
                {datos.puedeVerActividad && (
                  <button
                    className={pestana === 'actividad' ? 'primary' : ''}
                    onClick={() => cambiar('actividad')}
                  >
                    Actividad
                  </button>
                )}
              </nav>
              {pestana === 'resumen' && (
                <>
                  <dl className="project-facts">
                    <div>
                      <dt>Código</dt>
                      <dd>{datos.codigo}</dd>
                    </div>
                    <div>
                      <dt>Estado</dt>
                      <dd>{datos.estado}</dd>
                    </div>
                    <div>
                      <dt>Responsable</dt>
                      <dd>{datos.propietario}</dd>
                    </div>
                    <div>
                      <dt>Tu rol</dt>
                      <dd>{datos.rol}</dd>
                    </div>
                    <div>
                      <dt>Creación</dt>
                      <dd>{new Date(datos.creadoEn).toLocaleString()}</dd>
                    </div>
                    <div>
                      <dt>Miembros activos</dt>
                      <dd>{datos.miembrosActivos}</dd>
                    </div>
                    <div>
                      <dt>Modelo</dt>
                      <dd>
                        {datos.puedeVerModelo
                          ? `${datos.nombreModelo ?? 'Sin modelo'} · revisión ${datos.revisionModelo}`
                          : 'Sin permiso de consulta'}
                      </dd>
                    </div>
                    <div>
                      <dt>Versiones en el historial</dt>
                      <dd>
                        {datos.puedeVerModelo
                          ? `${datos.totalVersiones} versión${datos.totalVersiones !== 1 ? 'es' : ''}`
                          : '—'}
                      </dd>
                    </div>
                    <div>
                      <dt>Sesiones de edición registradas</dt>
                      <dd>{datos.sesionesEdicionActivas}</dd>
                    </div>
                  </dl>
                  <p className="project-description">{datos.descripcion || 'Sin descripción'}</p>
                  {!accion && (
                    <div className="project-tools">
                      <button
                        disabled={!datos.puedeVerModelo || !datos.idModelo}
                        onClick={() => abrir(datos)}
                      >
                        Abrir modelo ↗
                      </button>
                      {datos.puedeGestionarProyecto && datos.estado === 'ACTIVO' && (
                        <>
                          <button onClick={() => setAccion('actualizar')}>Actualizar datos</button>
                          <button onClick={() => setAccion('archivar')}>Archivar proyecto</button>
                        </>
                      )}
                      {datos.puedeGestionarProyecto && datos.estado === 'ARCHIVADO' && (
                        <button onClick={() => setAccion('reactivar')}>Reactivar proyecto</button>
                      )}
                    </div>
                  )}
                  {accion && (
                    <ProyectoForm
                      key={accion}
                      proyecto={datos}
                      accion={accion}
                      cerrar={() => setAccion(null)}
                      registrarSalida={registrarForm}
                      guardado={(p) => {
                        setDatos(p);
                        setAccion(null);
                        actualizado();
                      }}
                    />
                  )}
                </>
              )}
              {pestana === 'equipo' && (
                <div className="table-scroll">
                  <table className="management-table">
                    <thead>
                      <tr>
                        <th>Miembro</th>
                        <th>Roles vigentes</th>
                        <th>Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {equipo?.map((m) => (
                        <tr key={m.id}>
                          <td>{m.nombre}</td>
                          <td>{m.roles}</td>
                          <td>{m.estado}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {equipo === null && !error && <p>Consultando equipo…</p>}
                  {equipo?.length === 0 && <p>No hay miembros registrados.</p>}
                </div>
              )}
              {pestana === 'actividad' && datos.puedeVerActividad && (
                <ActividadPanel proyecto={proyecto} />
              )}
            </>
          )
        )}
        {error && (
          <div className="error" role="alert">
            {error}
            <button onClick={() => setRecarga((r) => r + 1)}>Reintentar resumen</button>
          </div>
        )}
      </section>
    </div>
  );
}
