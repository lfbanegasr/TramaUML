import { useCallback, useEffect, useState } from 'react';
import * as api from '../api/administracionApi';
import { UsuarioForm } from '../components/UsuarioForm';
import { RolForm } from '../components/RolForm';
import { useSesion } from '../../acceso/estado/SesionProvider';

export function AdministracionPage({
  registrarSalida,
}: {
  registrarSalida: (g: (() => boolean) | null) => void;
}) {
  const { sesion } = useSesion();
  const globales = sesion?.usuario.permisosGlobales ?? [];
  const superadmin = sesion?.usuario.rolesGlobales.includes('SUPERADMINISTRADOR');
  const puedeUsuarios = superadmin || globales.includes('GESTIONAR_USUARIOS');
  const puedeRoles = superadmin || globales.includes('GESTIONAR_ROLES');
  const [tab, setTab] = useState(puedeUsuarios ? 'usuarios' : 'roles');
  const [usuarios, setUsuarios] = useState<api.Usuario[]>([]),
    [roles, setRoles] = useState<api.Rol[]>([]),
    [permisos, setPermisos] = useState<api.Permiso[]>([]);
  const [filtro, setFiltro] = useState(''),
    [estado, setEstado] = useState(''),
    [rolFiltro, setRolFiltro] = useState(''),
    [pagina, setPagina] = useState(0);
  const [ambitoRol, setAmbitoRol] = useState(''),
    [estadoRol, setEstadoRol] = useState('');
  const [error, setError] = useState(''),
    [ocupado, setOcupado] = useState(false),
    [recarga, setRecarga] = useState(0);
  const [editar, setEditar] = useState<{ usuario?: api.Usuario; rol?: api.Rol } | null>(null);
  const [credencial, setCredencial] = useState<api.Credencial | null>(null);
  const salida = useCallback(
    () =>
      !ocupado &&
      (!(editar || credencial) ||
        window.confirm(
          'Hay datos sin terminar o una credencial temporal visible. ¿Salir y descartarlos de esta pantalla?',
        )),
    [ocupado, editar, credencial],
  );
  useEffect(() => {
    registrarSalida(salida);
    return () => registrarSalida(null);
  }, [registrarSalida, salida]);
  useEffect(() => {
    let vigente = true;
    const cargar = async () => {
      try {
        const r = await (tab === 'usuarios' ? api.rolesUsuario() : api.roles());
        if (vigente) setRoles(r);
        if (tab === 'usuarios') {
          const u = await api.usuarios(filtro, estado, rolFiltro, pagina);
          if (vigente) setUsuarios(u);
        } else {
          const p = await api.permisos();
          if (vigente) setPermisos(p);
        }
      } catch (e) {
        if (vigente) setError((e as Error).message);
      }
    };
    const t = setTimeout(cargar, 180);
    return () => {
      vigente = false;
      clearTimeout(t);
    };
  }, [tab, filtro, estado, rolFiltro, pagina, recarga]);
  function cambiarTab(t: string) {
    if (salida()) {
      setEditar(null);
      setCredencial(null);
      setError('');
      setFiltro('');
      setTab(t);
    }
  }
  async function guardarUsuario(datos: api.DatosUsuario) {
    setOcupado(true);
    setError('');
    try {
      if (editar?.usuario) await api.guardarUsuario(editar.usuario.id, datos);
      else setCredencial(await api.crearUsuario(datos));
      setEditar(null);
      setRecarga((r) => r + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }
  async function guardarRol(datos: api.DatosRol) {
    setOcupado(true);
    setError('');
    try {
      await api.guardarRol(editar?.rol ?? null, datos);
      setEditar(null);
      setRecarga((r) => r + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }
  async function restablecer(u: api.Usuario) {
    if (
      !window.confirm(
        'La cuenta deberá activar una credencial nueva y sus sesiones se cerrarán. ¿Restablecer?',
      )
    )
      return;
    setOcupado(true);
    setError('');
    try {
      setCredencial(await api.restablecer(u));
      setRecarga((r) => r + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }
  return (
    <div className="page administration">
      <div className="page-heading">
        <div>
          <span className="eyebrow">ACCESO Y RESPONSABILIDADES</span>
          <h1>Administración</h1>
          <p className="muted">Gestiona cuentas y configura los accesos de tu equipo.</p>
        </div>
      </div>
      <div className="section-tabs">
        {puedeUsuarios && (
          <button
            className={tab === 'usuarios' ? 'primary' : ''}
            onClick={() => cambiarTab('usuarios')}
          >
            Usuarios
          </button>
        )}
        {puedeRoles && (
          <button className={tab === 'roles' ? 'primary' : ''} onClick={() => cambiarTab('roles')}>
            Roles y permisos
          </button>
        )}
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
          <button onClick={() => setRecarga((r) => r + 1)}>Recargar datos</button>
        </p>
      )}
      {editar ? (
        <div className="management-form">
          {tab === 'usuarios' ? (
            <UsuarioForm
              key={editar.usuario?.id ?? 'nuevo'}
              usuario={editar.usuario ?? null}
              roles={roles}
              ocupado={ocupado}
              guardar={guardarUsuario}
              cancelar={() => setEditar(null)}
            />
          ) : (
            <RolForm
              key={editar.rol?.id ?? 'nuevo'}
              rol={editar.rol ?? null}
              permisos={permisos}
              ocupado={ocupado}
              guardar={guardarRol}
              cancelar={() => setEditar(null)}
            />
          )}
        </div>
      ) : (
        <>
          <div className="filter-bar">
            <input
              aria-label="Buscar en administración"
              placeholder="Buscar por nombre, correo o código"
              value={filtro}
              onChange={(e) => {
                setFiltro(e.target.value);
                setPagina(0);
              }}
            />
            {tab === 'usuarios' && (
              <>
                <select
                  aria-label="Filtrar estado de usuarios"
                  value={estado}
                  onChange={(e) => {
                    setEstado(e.target.value);
                    setPagina(0);
                  }}
                >
                  <option value="">Todos los estados</option>
                  {['ACTIVO', 'PENDIENTE', 'INACTIVO', 'BLOQUEADO'].map((s) => (
                    <option key={s}>{s}</option>
                  ))}
                </select>
                <select
                  aria-label="Filtrar rol global"
                  value={rolFiltro}
                  onChange={(e) => {
                    setRolFiltro(e.target.value);
                    setPagina(0);
                  }}
                >
                  <option value="">Todos los roles</option>
                  {roles
                    .filter((r) => r.ambito !== 'PROYECTO')
                    .map((r) => (
                      <option key={r.id} value={r.codigo}>
                        {r.nombre}
                      </option>
                    ))}
                </select>
              </>
            )}
            {tab === 'roles' && (
              <>
                <select
                  aria-label="Filtrar ámbito de roles"
                  value={ambitoRol}
                  onChange={(e) => setAmbitoRol(e.target.value)}
                >
                  <option value="">Todos los ámbitos</option>
                  {['GLOBAL', 'PROYECTO', 'AMBOS'].map((a) => (
                    <option key={a}>{a}</option>
                  ))}
                </select>
                <select
                  aria-label="Filtrar estado de roles"
                  value={estadoRol}
                  onChange={(e) => setEstadoRol(e.target.value)}
                >
                  <option value="">Todos los estados</option>
                  <option>ACTIVO</option>
                  <option>INACTIVO</option>
                </select>
              </>
            )}
            <button
              className="primary"
              disabled={ocupado || !!credencial}
              onClick={() => {
                setError('');
                setEditar({});
              }}
            >
              {tab === 'usuarios' ? 'Nuevo usuario' : 'Nuevo rol'}
            </button>
          </div>
          <div className="table-scroll">
            <table className="management-table">
              <thead>
                <tr>
                  <th>{tab === 'usuarios' ? 'Cuenta' : 'Rol'}</th>
                  <th>Estado</th>
                  <th>Accesos</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {tab === 'usuarios'
                  ? usuarios.map((u) => (
                      <tr key={u.id}>
                        <td>
                          <b>{u.nombre}</b>
                          <small>
                            {u.usuario} · {u.correo}
                          </small>
                        </td>
                        <td>{u.estado}</td>
                        <td>
                          {roles
                            .filter((r) => (JSON.parse(u.roles_json) as string[]).includes(r.id))
                            .map((r) => r.nombre)
                            .join(', ') || 'Sin rol global'}
                        </td>
                        <td>
                          <button
                            disabled={ocupado || !!credencial}
                            onClick={() => {
                              setError('');
                              setEditar({ usuario: u });
                            }}
                            aria-label={'Editar usuario ' + u.usuario}
                          >
                            Editar
                          </button>
                          <button
                            disabled={ocupado || !!credencial || u.id === sesion?.usuario.id}
                            onClick={() => restablecer(u)}
                            aria-label={'Restablecer usuario ' + u.usuario}
                          >
                            Restablecer
                          </button>
                        </td>
                      </tr>
                    ))
                  : roles
                      .filter(
                        (r) =>
                          (r.nombre + r.codigo).toLowerCase().includes(filtro.toLowerCase()) &&
                          (!ambitoRol || r.ambito === ambitoRol) &&
                          (!estadoRol || r.estado === estadoRol),
                      )
                      .map((r) => (
                        <tr key={r.id}>
                          <td>
                            <b>{r.nombre}</b>
                            <small>
                              {r.codigo} · {r.ambito}
                            </small>
                          </td>
                          <td>{r.estado}</td>
                          <td>
                            {JSON.parse(r.permisos_json).length} permisos · {r.asignaciones}{' '}
                            asignaciones
                          </td>
                          <td>
                            <button
                              disabled={r.codigo === 'SUPERADMINISTRADOR'}
                              onClick={() => {
                                setError('');
                                setEditar({ rol: r });
                              }}
                            >
                              Editar {r.nombre}
                            </button>
                          </td>
                        </tr>
                      ))}
              </tbody>
            </table>
          </div>
          {tab === 'usuarios' && (
            <div className="pagination">
              <button disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>
                Página anterior
              </button>
              <span>Página {pagina + 1} · hasta 50 cuentas</span>
              <button disabled={usuarios.length < 50} onClick={() => setPagina((p) => p + 1)}>
                Página siguiente
              </button>
            </div>
          )}
        </>
      )}
      {credencial && (
        <div className="modal-backdrop">
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label="Credencial temporal creada"
          >
            <h2>Credencial temporal</h2>
            <p>
              Entrégala personalmente a la cuenta correspondiente. Para elegir su contraseña debe
              usar “Activar cuenta” en la pantalla de acceso.
            </p>
            <output className="temporary-secret" aria-label="Credencial temporal">
              {credencial.credencialTemporal}
            </output>
            <p className="muted">
              Se muestra solo en esta respuesta. No se guarda en el navegador ni se recupera desde
              el listado.
            </p>
            <button className="primary" onClick={() => setCredencial(null)}>
              Ya conservé la credencial
            </button>
          </section>
        </div>
      )}
    </div>
  );
}
