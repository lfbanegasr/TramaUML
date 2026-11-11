import { useEffect, useState, type FormEvent } from 'react';
import * as api from '../api/miembrosApi';

export function MiembroForm({
  proyecto,
  miembro,
  equipo,
  ocupado,
  guardar,
  cancelar,
}: {
  proyecto: string;
  miembro: api.Miembro | null;
  equipo: api.Equipo;
  ocupado: boolean;
  guardar: (d: api.DatosMiembro) => Promise<void>;
  cancelar: () => void;
}) {
  const [buscar, setBuscar] = useState(''),
    [cuentas, setCuentas] = useState<api.Cuenta[]>([]),
    [error, setError] = useState('');
  const esPropietario = miembro?.usuario === equipo.propietario;
  const [rol, setRol] = useState(
    esPropietario
      ? (equipo.roles.find((r) => r.codigo === 'PROPIETARIO_PROYECTO')?.id ?? '')
      : miembro
        ? (JSON.parse(miembro.roles_json)[0] ?? '')
        : '',
  );
  const [decisiones, setDecisiones] = useState<api.Decision[]>(
    miembro ? JSON.parse(miembro.permisos_json) : [],
  );
  useEffect(() => {
    if (miembro) return;
    let vivo = true;
    const t = setTimeout(
      () =>
        api
          .cuentas(proyecto, buscar)
          .then((c) => {
            if (vivo) {
              setCuentas(c);
              setError('');
            }
          })
          .catch((e) => {
            if (vivo) setError(e.message);
          }),
      200,
    );
    return () => {
      vivo = false;
      clearTimeout(t);
    };
  }, [buscar, proyecto, miembro]);
  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (
      !window.confirm(
        'Se confirmará la membresía y sus accesos. Se cerrarán sus sesiones de modelado actuales (' +
          (miembro?.sesiones_activas ?? 0) +
          '). ¿Continuar?',
      )
    )
      return;
    const f = new FormData(e.currentTarget);
    await guardar({
      usuario: miembro?.usuario ?? String(f.get('usuario')),
      rol,
      estado: String(f.get('estado') ?? 'ACTIVO'),
      motivo: String(f.get('motivo') ?? ''),
      permisos: decisiones,
      revision: miembro?.revision,
    });
  }
  function decision(permiso: string, valor: string) {
    setDecisiones((actual) => [
      ...actual.filter((p) => p.permiso !== permiso),
      ...(valor ? [{ permiso, decision: valor as api.Decision['decision'] }] : []),
    ]);
  }
  const seleccionado = equipo.roles.find((r) => r.id === rol);
  const heredados: string[] = seleccionado ? JSON.parse(seleccionado.permisos_json) : [];
  return (
    <form onSubmit={enviar} aria-label="Datos del miembro">
      <h3>{miembro ? 'Editar miembro' : 'Agregar miembro'}</h3>
      <fieldset disabled={ocupado}>
        {miembro ? (
          <p>
            <b>{miembro.nombre}</b> · {miembro.correo}
          </p>
        ) : (
          <>
            <label>
              Buscar cuenta activa
              <input
                value={buscar}
                onChange={(e) => setBuscar(e.target.value)}
                placeholder="Al menos dos caracteres"
              />
            </label>
            <label>
              Cuenta para incorporar
              <select name="usuario" required defaultValue="">
                <option value="">Selecciona una cuenta</option>
                {cuentas.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre} · {c.correo}
                  </option>
                ))}
              </select>
            </label>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
          </>
        )}
        <label>
          Rol del proyecto
          <select
            required
            value={rol}
            onChange={(e) => setRol(e.target.value)}
            disabled={esPropietario}
          >
            <option value="">Selecciona un rol</option>
            {equipo.roles
              .filter((r) => r.codigo !== 'PROPIETARIO_PROYECTO' || esPropietario)
              .map((r) => (
                <option key={r.id} value={r.id}>
                  {r.nombre}
                </option>
              ))}
          </select>
        </label>
        <p className="muted">
          Permisos heredados:{' '}
          {equipo.permisos
            .filter((p) => heredados.includes(p.id))
            .map((p) => p.codigo)
            .join(', ') || 'Ninguno'}
        </p>
        {miembro && (
          <>
            <label>
              Estado del miembro
              <select name="estado" defaultValue={miembro.estado} disabled={esPropietario}>
                {['ACTIVO', 'SUSPENDIDO', 'RETIRADO'].map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
            </label>
            <label>
              Motivo del estado
              <textarea name="motivo" maxLength={2000} defaultValue={miembro.motivo ?? ''} />
            </label>
          </>
        )}
        <fieldset className="member-permissions">
          <legend>Excepciones por miembro</legend>
          <p className="muted">
            Denegar prevalece sobre permitir. Heredar usa los permisos del rol.
          </p>
          {equipo.permisos.map((p) => (
            <label key={p.id}>
              {p.codigo}
              <select
                aria-label={'Excepción ' + p.codigo}
                disabled={esPropietario}
                value={decisiones.find((d) => d.permiso === p.id)?.decision ?? ''}
                onChange={(e) => decision(p.id, e.target.value)}
              >
                <option value="">Heredar</option>
                <option value="PERMITIR">Permitir</option>
                <option value="DENEGAR">Denegar</option>
              </select>
            </label>
          ))}
        </fieldset>
        <div className="form-actions">
          <button type="button" onClick={cancelar}>
            Cancelar edición
          </button>
          <button className="primary">{ocupado ? 'Guardando…' : 'Confirmar miembro'}</button>
        </div>
      </fieldset>
    </form>
  );
}
