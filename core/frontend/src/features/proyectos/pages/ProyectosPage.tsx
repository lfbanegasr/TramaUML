import { useEffect, useState, type FormEvent } from 'react';
import { listarProyectos, crearProyecto, type Pagina, type Proyecto } from '../api/proyectosApi';
import { useSesion } from '../../acceso/estado/SesionProvider';
export function ProyectosPage({ abrir }: { abrir: (p: Proyecto) => void }) {
  const { sesion } = useSesion();
  const [filtro, setFiltro] = useState(''), [estado, setEstado] = useState(''), [pagina, setPagina] = useState(0);
  const [datos, setDatos] = useState<Pagina | null>(null), [error, setError] = useState(''), [cargando, setCargando] = useState(true);
  const [crear, setCrear] = useState(false), [guardando, setGuardando] = useState(false), [recarga, setRecarga] = useState(0);
  useEffect(() => {
    let activo = true; setCargando(true); setError('');
    const t = setTimeout(() => listarProyectos(filtro, estado, pagina).then(d => { if (activo) setDatos(d); })
      .catch(e => { if (activo) { setDatos(null); setError(e.message); } }).finally(() => { if (activo) setCargando(false); }), 200);
    return () => { activo = false; clearTimeout(t); };
  }, [filtro, estado, pagina, recarga]);
  async function guardar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setGuardando(true); setError(''); const f = new FormData(e.currentTarget);
    try { const p = await crearProyecto({ codigo: String(f.get('codigo')), nombre: String(f.get('nombre')), descripcion: String(f.get('descripcion')) }); setCrear(false); abrir(p); }
    catch (e) { setError((e as Error).message); } finally { setGuardando(false); }
  }
  return <div className="page">
    <div className="page-heading"><div><span className="eyebrow">ESPACIO DE TRABAJO</span><h1>Tus proyectos</h1><p className="muted">Encuentra tu equipo y continúa dando forma a tus ideas.</p></div>
      {sesion?.puedeCrearProyectos && <button className="primary" onClick={() => setCrear(true)}>＋ Nuevo proyecto</button>}</div>
    <div className="filter-bar"><input aria-label="Buscar proyectos" placeholder="⌕  Buscar por nombre o código" value={filtro} onChange={e => { setFiltro(e.target.value); setPagina(0); }} /><select aria-label="Estado de proyectos" value={estado} onChange={e => { setEstado(e.target.value); setPagina(0); }}><option value="">Todos los estados</option><option value="ACTIVO">Activos</option><option value="ARCHIVADO">Archivados</option><option value="SUSPENDIDO">Suspendidos</option></select><span className="muted">{datos?.total ?? '—'} proyectos</span></div>
    {error && <div className="error" role="alert">{error}<button onClick={() => setRecarga(r => r + 1)}>Reintentar consulta</button></div>}
    {cargando ? <div className="empty">Consultando tus proyectos…</div> : datos?.proyectos.length === 0 ? <div className="empty"><span className="empty-icon">◇</span><h2>{filtro || estado ? 'No hay coincidencias' : 'Tu próximo modelo empieza aquí'}</h2><p>{filtro || estado ? 'Prueba con otro nombre o estado.' : 'Crea un proyecto o solicita a su propietario que te incorpore.'}</p></div> :
      <div className="project-grid">{datos?.proyectos.map(p => <article className="project-card" key={p.id}><div className="card-top"><span className="project-symbol">◇</span><span className={`badge ${p.estado === 'ACTIVO' ? 'active' : ''}`}>{p.estado.toLowerCase()}</span></div><span className="project-code">{p.codigo}</span><h2>{p.nombre}</h2><p className="project-description">{p.descripcion || 'Un espacio compartido para el modelo de tu sistema.'}</p><div className="card-member"><span className="avatar">{p.propietario.slice(0, 1)}</span><div><b>{p.propietario}</b><small>{p.rol}</small></div></div><div className="card-action"><span className="muted">{p.puedeEditar ? 'Edición disponible' : 'Solo lectura'}</span><button disabled={!p.puedeVerModelo || !p.idModelo} onClick={() => abrir(p)}>Abrir modelo ↗</button></div></article>)}</div>}
    {datos && datos.total > datos.tamano && <div className="pagination"><button disabled={pagina === 0} onClick={() => setPagina(p => p - 1)}>Anterior</button><span>Página {pagina + 1}</span><button disabled={(pagina + 1) * datos.tamano >= datos.total} onClick={() => setPagina(p => p + 1)}>Siguiente</button></div>}
    {crear && <div className="modal-backdrop"><section role="dialog" aria-modal="true" aria-labelledby="crear-titulo" className="modal"><span className="eyebrow">UN NUEVO COMIENZO</span><h2 id="crear-titulo">Nuevo proyecto</h2><p className="muted">Será privado. Tú serás su propietario.</p><form onSubmit={guardar}><label>Nombre del proyecto<input name="nombre" required maxLength={180} autoFocus /></label><label>Código<input name="codigo" required maxLength={80} pattern="[A-Za-z0-9_.-]+" placeholder="Ejemplo: INVENTARIO" /></label><label>Descripción<textarea name="descripcion" maxLength={4000} rows={3} /></label>{error && <p role="alert" className="error">{error}</p>}<div className="form-actions"><button type="button" disabled={guardando} onClick={() => setCrear(false)}>Cancelar</button><button className="primary" disabled={guardando}>{guardando ? 'Creando…' : 'Crear proyecto'}</button></div></form></section></div>}
  </div>;
}
