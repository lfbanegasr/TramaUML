import { useCallback, useEffect, useState } from 'react';
import * as api from '../api/miembrosApi';
import { MiembroForm } from './MiembroForm';
import { useSesion } from '../../acceso/estado/SesionProvider';

export function MiembrosPanel({
  proyecto,
  cerrar,
  actualizado,
  registrarSalida,
}: {
  proyecto: string;
  cerrar: () => void;
  actualizado: () => void;
  registrarSalida: (g: (() => boolean) | null) => void;
}) {
  const { sesion } = useSesion();
  const [datos, setDatos] = useState<api.Equipo | null>(null),
    [error, setError] = useState(''),
    [ocupado, setOcupado] = useState(false);
  const [editar, setEditar] = useState<{ miembro: api.Miembro | null } | null>(null),
    [recarga, setRecarga] = useState(0);
  const [filtro, setFiltro] = useState(''),
    [estado, setEstado] = useState(''),
    [rol, setRol] = useState('');
  const salida = useCallback(
    () => !ocupado && (!editar || window.confirm('¿Cerrar y descartar la edición del miembro?')),
    [ocupado, editar],
  );
  useEffect(() => {
    registrarSalida(salida);
    return () => registrarSalida(null);
  }, [salida, registrarSalida]);
  useEffect(() => {
    let vivo = true;
    api
      .equipo(proyecto)
      .then((d) => {
        if (vivo) setDatos(d);
      })
      .catch((e) => {
        if (vivo) setError(e.message);
      });
    return () => {
      vivo = false;
    };
  }, [proyecto, recarga]);
  async function guardar(d: api.DatosMiembro) {
    setError('');
    setOcupado(true);
    try {
      await api.guardarMiembro(proyecto, editar?.miembro ?? null, d);
      setEditar(null);
      setDatos(await api.equipo(proyecto));
      actualizado();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }
  async function transferir(m: api.Miembro) {
    if (
      !window.confirm(
        'Se transferirá la propiedad a ' +
          m.nombre +
          '. El propietario anterior conserva su membresía y roles hasta que se modifiquen explícitamente. ¿Confirmar?',
      )
    )
      return;
    setError('');
    setOcupado(true);
    try {
      setDatos(await api.transferir(proyecto, m.id));
      actualizado();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }
  const visibles =
    datos?.miembros.filter(
      (m) =>
        (m.nombre + m.correo + m.identificador).toLowerCase().includes(filtro.toLowerCase()) &&
        (!estado || m.estado === estado) &&
        (!rol || (JSON.parse(m.roles_json) as string[]).includes(rol)),
    ) ?? [];
  const puedeTransferir =
    sesion?.usuario.id === datos?.propietario ||
    sesion?.usuario.rolesGlobales.includes('SUPERADMINISTRADOR');
  return (
    <div className="modal-backdrop">
      <section
        className="modal modal-wide"
        role="dialog"
        aria-modal="true"
        aria-label="Miembros del proyecto"
      >
        <div className="panel-heading">
          <h2>Miembros del proyecto</h2>
          <button
            disabled={ocupado}
            onClick={() => {
              if (salida()) cerrar();
            }}
          >
            Cerrar miembros
          </button>
        </div>
        {error && (
          <p className="error" role="alert">
            {error}
            <button onClick={() => setRecarga((r) => r + 1)}>Recargar miembros</button>
          </p>
        )}
        {!datos ? (
          <p>Consultando miembros…</p>
        ) : editar ? (
          <MiembroForm
            key={editar.miembro?.id ?? 'nuevo'}
            proyecto={proyecto}
            miembro={editar.miembro}
            equipo={datos}
            ocupado={ocupado}
            guardar={guardar}
            cancelar={() => setEditar(null)}
          />
        ) : (
          <>
            <div className="filter-bar">
              <input
                aria-label="Filtrar miembros"
                value={filtro}
                onChange={(e) => setFiltro(e.target.value)}
                placeholder="Nombre o correo"
              />
              <select
                aria-label="Filtrar estado de miembros"
                value={estado}
                onChange={(e) => setEstado(e.target.value)}
              >
                <option value="">Todos los estados</option>
                {['ACTIVO', 'SUSPENDIDO', 'RETIRADO'].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
              <select
                aria-label="Filtrar rol de miembros"
                value={rol}
                onChange={(e) => setRol(e.target.value)}
              >
                <option value="">Todos los roles</option>
                {datos.roles.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.nombre}
                  </option>
                ))}
              </select>
              <button
                className="primary"
                onClick={() => {
                  setError('');
                  setEditar({ miembro: null });
                }}
              >
                Agregar miembro
              </button>
            </div>
            <div className="table-scroll">
              <table className="management-table">
                <thead>
                  <tr>
                    <th>Cuenta</th>
                    <th>Rol</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {visibles.map((m) => (
                    <tr key={m.id}>
                      <td>
                        <b>{m.nombre}</b>
                        <small>
                          {m.correo}
                          {m.usuario === datos.propietario ? ' · Propietario actual' : ''}
                        </small>
                      </td>
                      <td>
                        {datos.roles
                          .filter((r) => (JSON.parse(m.roles_json) as string[]).includes(r.id))
                          .map((r) => r.nombre)
                          .join(', ') || 'Sin rol'}
                      </td>
                      <td>{m.estado}</td>
                      <td>
                        <button
                          disabled={ocupado}
                          onClick={() => {
                            setError('');
                            setEditar({ miembro: m });
                          }}
                          aria-label={'Editar miembro ' + m.identificador}
                        >
                          Editar
                        </button>
                        {puedeTransferir &&
                          m.estado === 'ACTIVO' &&
                          m.usuario !== datos.propietario && (
                            <button disabled={ocupado} onClick={() => transferir(m)}>
                              Transferir propiedad a {m.identificador}
                            </button>
                          )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="muted">
              Se conserva el historial de membresías y de asignaciones revocadas. El propietario
              actual requiere una transferencia antes de retirarse.
            </p>
          </>
        )}
      </section>
    </div>
  );
}
