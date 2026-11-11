import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import {
  listarProyectos,
  crearProyecto,
  consultarProyecto,
  rolesConsulta,
  consultaInicial,
  type ConsultaProyectos,
  type Pagina,
  type Proyecto,
} from '../api/proyectosApi';
import { useSesion } from '../../acceso/estado/SesionProvider';
import { MiembrosPanel } from '../components/MiembrosPanel';
import { ProyectoResumen } from '../components/ProyectoResumen';
export function ProyectosPage({
  abrir,
  registrarSalida,
  consulta,
  cambiarConsulta,
}: {
  abrir: (p: Proyecto) => void;
  registrarSalida: (g: (() => boolean) | null) => void;
  consulta: ConsultaProyectos;
  cambiarConsulta: (c: ConsultaProyectos) => void;
}) {
  const { sesion } = useSesion();
  const { filtro, estado, rol, desde, hasta, pagina } = consulta;
  const cambiar = (c: Partial<ConsultaProyectos>) =>
    cambiarConsulta({ ...consulta, pagina: 0, ...c });
  const [roles, setRoles] = useState<{ codigo: string; nombre: string }[]>([]);
  const [resumen, setResumen] = useState<string | null>(null);
  const guardiaResumen = useRef<(() => boolean) | null>(null);
  const registrarResumen = useCallback((g: (() => boolean) | null) => {
    guardiaResumen.current = g;
  }, []);
  const [datos, setDatos] = useState<Pagina | null>(null),
    [error, setError] = useState(''),
    [cargando, setCargando] = useState(true);
  const [crear, setCrear] = useState(false),
    [guardando, setGuardando] = useState(false),
    [recarga, setRecarga] = useState(0);
  const [miembros, setMiembros] = useState<string | null>(null);
  const guardiaMiembros = useRef<(() => boolean) | null>(null);
  const registrarMiembros = useCallback((g: (() => boolean) | null) => {
    guardiaMiembros.current = g;
  }, []);
  const salida = useCallback(
    () =>
      !guardando &&
      (!guardiaResumen.current || guardiaResumen.current()) &&
      (guardiaMiembros.current
        ? guardiaMiembros.current()
        : !crear || window.confirm('¿Salir y descartar el proyecto sin guardar?')),
    [guardando, crear],
  );
  useEffect(() => {
    let activo = true;
    rolesConsulta()
      .then((r) => {
        if (activo) setRoles(r);
      })
      .catch((e) => {
        if (activo) setError(e.message);
      });
    return () => {
      activo = false;
    };
  }, [recarga]);
  async function abrirAutorizado(p: Proyecto) {
    setError('');
    try {
      abrir(await consultarProyecto(p.id));
    } catch (e) {
      setError((e as Error).message);
    }
  }
  useEffect(() => {
    registrarSalida(salida);
    return () => registrarSalida(null);
  }, [salida, registrarSalida]);
  useEffect(() => {
    let activo = true;
    setCargando(true);
    setError('');
    const t = setTimeout(
      () =>
        listarProyectos(consulta)
          .then((d) => {
            if (activo) setDatos(d);
          })
          .catch((e) => {
            if (activo) {
              setDatos(null);
              setError(e.message);
            }
          })
          .finally(() => {
            if (activo) setCargando(false);
          }),
      200,
    );
    return () => {
      activo = false;
      clearTimeout(t);
    };
  }, [consulta, recarga]);
  async function guardar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setGuardando(true);
    setError('');
    const f = new FormData(e.currentTarget);
    try {
      const p = await crearProyecto({
        codigo: String(f.get('codigo')),
        nombre: String(f.get('nombre')),
        descripcion: String(f.get('descripcion')),
      });
      setCrear(false);
      abrir(p);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setGuardando(false);
    }
  }
  return (
    <div className="page">
      {resumen && (
        <ProyectoResumen
          proyecto={resumen}
          cerrar={() => {
            setResumen(null);
            setRecarga((r) => r + 1);
          }}
          abrir={abrirAutorizado}
          registrarSalida={registrarResumen}
          actualizado={() => setRecarga((r) => r + 1)}
        />
      )}
      {miembros && (
        <MiembrosPanel
          registrarSalida={registrarMiembros}
          proyecto={miembros}
          cerrar={() => setMiembros(null)}
          actualizado={() => setRecarga((r) => r + 1)}
        />
      )}
      <div className="page-heading">
        <div>
          <span className="eyebrow">ESPACIO DE TRABAJO</span>
          <h1>Tus proyectos</h1>
          <p className="muted">Encuentra tu equipo y continúa dando forma a tus ideas.</p>
        </div>
        {sesion?.puedeCrearProyectos && (
          <button className="primary" onClick={() => setCrear(true)}>
            ＋ Nuevo proyecto
          </button>
        )}
      </div>
      <div className="filter-bar">
        <input
          aria-label="Buscar proyectos"
          placeholder="⌕  Buscar por nombre o código"
          value={filtro}
          onChange={(e) => {
            cambiar({ filtro: e.target.value });
          }}
        />
        <select
          aria-label="Estado de proyectos"
          value={estado}
          onChange={(e) => {
            cambiar({ estado: e.target.value });
          }}
        >
          <option value="">Todos los estados</option>
          <option value="ACTIVO">Activos</option>
          <option value="ARCHIVADO">Archivados</option>
          <option value="SUSPENDIDO">Suspendidos</option>
        </select>
        <select
          aria-label="Rol en proyectos"
          value={rol}
          onChange={(e) => cambiar({ rol: e.target.value })}
        >
          <option value="">Todos mis roles</option>
          {sesion?.usuario.rolesGlobales.includes('SUPERADMINISTRADOR') && (
            <option value="SUPERADMINISTRADOR">Superadministrador</option>
          )}
          {roles.map((r) => (
            <option key={r.codigo} value={r.codigo}>
              {r.nombre}
            </option>
          ))}
        </select>
        <span className="muted">{datos?.total ?? '—'} proyectos</span>
      </div>
      <div className="date-filters">
        <span className="muted">Fecha de creación (UTC)</span>
        <label>
          Desde
          <input
            type="date"
            aria-label="Proyectos creados desde"
            value={desde}
            onChange={(e) => cambiar({ desde: e.target.value })}
          />
        </label>
        <label>
          Hasta
          <input
            type="date"
            aria-label="Proyectos creados hasta"
            value={hasta}
            onChange={(e) => cambiar({ hasta: e.target.value })}
          />
        </label>
        <button onClick={() => cambiarConsulta({ ...consultaInicial })}>Limpiar filtros</button>
      </div>
      {error && (
        <div className="error" role="alert">
          {error}
          <button onClick={() => setRecarga((r) => r + 1)}>Reintentar consulta</button>
        </div>
      )}
      {cargando ? (
        <div className="empty">Consultando tus proyectos…</div>
      ) : datos?.proyectos.length === 0 ? (
        <div className="empty">
          <span className="empty-icon">◇</span>
          <h2>
            {filtro || estado || rol || desde || hasta
              ? 'No hay coincidencias'
              : 'Tu próximo modelo empieza aquí'}
          </h2>
          <p>
            {filtro || estado || rol || desde || hasta
              ? 'Prueba con otros filtros.'
              : 'Crea un proyecto o solicita a su propietario que te incorpore.'}
          </p>
        </div>
      ) : (
        <div className="project-grid">
          {datos?.proyectos.map((p) => (
            <article className="project-card" key={p.id}>
              <div className="card-top">
                <span className="project-symbol">◇</span>
                <span className={`badge ${p.estado === 'ACTIVO' ? 'active' : ''}`}>
                  {p.estado.toLowerCase()}
                </span>
              </div>
              <span className="project-code">{p.codigo}</span>
              <h2>{p.nombre}</h2>
              <p className="project-description">
                {p.descripcion || 'Un espacio compartido para el modelo de tu sistema.'}
              </p>
              <div className="card-member">
                <span className="avatar">{p.propietario.slice(0, 1)}</span>
                <div>
                  <b>{p.propietario}</b>
                  <small>{p.rol}</small>
                </div>
              </div>
              {p.puedeGestionarMiembros && (
                <button className="wide" onClick={() => setMiembros(p.id)}>
                  Miembros del proyecto
                </button>
              )}
              <button className="wide" onClick={() => setResumen(p.id)}>
                Ver resumen y actividad
              </button>
              <div className="card-action">
                <span className="muted">
                  {p.puedeEditar ? 'Edición disponible' : 'Solo lectura'}
                </span>
                <button
                  disabled={!p.puedeVerModelo || !p.idModelo}
                  onClick={() => abrirAutorizado(p)}
                >
                  Abrir modelo ↗
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
      {datos && datos.total > datos.tamano && (
        <div className="pagination">
          <button disabled={pagina === 0} onClick={() => cambiar({ pagina: pagina - 1 })}>
            Anterior
          </button>
          <span>Página {pagina + 1}</span>
          <button
            disabled={(pagina + 1) * datos.tamano >= datos.total}
            onClick={() => cambiar({ pagina: pagina + 1 })}
          >
            Siguiente
          </button>
        </div>
      )}
      {crear && (
        <div className="modal-backdrop">
          <section role="dialog" aria-modal="true" aria-labelledby="crear-titulo" className="modal">
            <span className="eyebrow">UN NUEVO COMIENZO</span>
            <h2 id="crear-titulo">Nuevo proyecto</h2>
            <p className="muted">Será privado. Tú serás su propietario.</p>
            <form onSubmit={guardar}>
              <label>
                Nombre del proyecto
                <input name="nombre" required maxLength={180} autoFocus />
              </label>
              <label>
                Código
                <input
                  name="codigo"
                  required
                  maxLength={80}
                  pattern="[A-Za-z0-9_.-]+"
                  placeholder="Ejemplo: INVENTARIO"
                />
              </label>
              <label>
                Descripción
                <textarea name="descripcion" maxLength={4000} rows={3} />
              </label>
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <div className="form-actions">
                <button type="button" disabled={guardando} onClick={() => setCrear(false)}>
                  Cancelar
                </button>
                <button className="primary" disabled={guardando}>
                  {guardando ? 'Creando…' : 'Crear proyecto'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}
