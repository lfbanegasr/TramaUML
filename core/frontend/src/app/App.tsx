import { useCallback, useRef, useState } from 'react';
import { useSesion } from '../features/acceso/estado/SesionProvider';
import { AccesoPage } from '../features/acceso/pages/AccesoPage';
import { ProyectosPage } from '../features/proyectos/pages/ProyectosPage';
import { EditorPage } from '../features/modelado/pages/EditorPage';
import type { Proyecto } from '../features/proyectos/api/proyectosApi';
import { AdministracionPage } from '../features/administracion/pages/AdministracionPage';
export function App() {
  const { sesion, cargando, salir } = useSesion();
  const [proyecto, setProyecto] = useState<Proyecto | null>(null),
    [error, setError] = useState('');
  const [administracion, setAdministracion] = useState(false);
  const guardia = useRef<(() => boolean) | null>(null);
  const registrarSalida = useCallback((g: (() => boolean) | null) => {
    guardia.current = g;
  }, []);
  function puedeSalir() {
    if (guardia.current && !guardia.current()) {
      setError('Conservamos tu edición. Termina el guardado o revisa el borrador antes de salir.');
      return false;
    }
    setError('');
    return true;
  }
  function volver() {
    if (puedeSalir()) {
      setProyecto(null);
      setAdministracion(false);
    }
  }
  if (cargando) return <div className="empty">Abriendo tu espacio…</div>;
  if (!sesion) return <AccesoPage />;
  async function cerrar() {
    if (!puedeSalir()) return;
    try {
      await salir();
      setProyecto(null);
      setAdministracion(false);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <div className="workspace">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon">◇</span> trama<span className="brand-tag">UML</span>
        </div>
        <span className="nav-label">TU PLATAFORMA</span>
        <button
          className={!proyecto && !administracion ? 'nav-item selected' : 'nav-item'}
          onClick={volver}
        >
          ▦ <span>Proyectos</span>
        </button>
        {(sesion.usuario.rolesGlobales.includes('SUPERADMINISTRADOR') ||
          sesion.usuario.permisosGlobales.some((p) =>
            ['GESTIONAR_USUARIOS', 'GESTIONAR_ROLES'].includes(p),
          )) && (
          <button
            className={administracion ? 'nav-item selected' : 'nav-item'}
            onClick={() => {
              if (puedeSalir()) {
                setProyecto(null);
                setAdministracion(true);
              }
            }}
          >
            ⚙ <span>Administración</span>
          </button>
        )}
        {proyecto && (
          <div className="nav-item selected">
            ◇ <span>Editor UML</span>
          </div>
        )}
        <div className="sidebar-bottom">
          <div className="user-card">
            <span className="avatar">{sesion.usuario.nombreCompleto.slice(0, 1)}</span>
            <div>
              <b>{sesion.usuario.nombreCompleto}</b>
              <small>
                {sesion.usuario.rolesGlobales.includes('SUPERADMINISTRADOR')
                  ? 'Administrador'
                  : 'Miembro del equipo'}
              </small>
            </div>
          </div>
          <button className="nav-item" onClick={cerrar}>
            ↪ Cerrar sesión
          </button>
        </div>
      </aside>
      <section className="workspace-main">
        <header className="topbar">
          <span>
            Espacio de trabajo{' '}
            <span className="breadcrumb">
              / {administracion ? 'Administración' : (proyecto?.nombre ?? 'Proyectos')}
            </span>
          </span>
          <span className="topbar-note">Diseña. Conecta. Construye.</span>
        </header>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {administracion ? (
          <AdministracionPage registrarSalida={registrarSalida} />
        ) : proyecto ? (
          <EditorPage
            key={proyecto.id}
            proyecto={proyecto}
            volver={volver}
            registrarSalida={registrarSalida}
          />
        ) : (
          <ProyectosPage abrir={setProyecto} registrarSalida={registrarSalida} />
        )}
      </section>
    </div>
  );
}
