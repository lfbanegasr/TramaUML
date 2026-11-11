import { useState, type FormEvent } from 'react';
import type { DatosRol, Permiso, Rol } from '../api/administracionApi';

export function RolForm({
  rol,
  permisos,
  ocupado,
  guardar,
  cancelar,
}: {
  rol: Rol | null;
  permisos: Permiso[];
  ocupado: boolean;
  guardar: (datos: DatosRol) => Promise<void>;
  cancelar: () => void;
}) {
  const [ambito, setAmbito] = useState(rol?.ambito ?? 'PROYECTO');
  const [seleccion, setSeleccion] = useState<string[]>(rol ? JSON.parse(rol.permisos_json) : []);
  const validos = permisos.filter(
    (p) => ambito === 'AMBOS' || p.ambito === 'AMBOS' || p.ambito === ambito,
  );
  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (
      rol &&
      !window.confirm(
        'Cambiarán los accesos de las cuentas y miembros que usan este rol. ¿Confirmar?',
      )
    )
      return;
    const f = new FormData(e.currentTarget);
    await guardar({
      codigo: String(f.get('codigo')),
      nombre: String(f.get('nombre')),
      descripcion: String(f.get('descripcion')),
      ambito,
      estado: String(f.get('estado')),
      permisos: seleccion.filter((id) => validos.some((p) => p.id === id)),
      revision: rol?.revision,
    });
  }
  return (
    <form onSubmit={enviar} aria-label="Datos del rol">
      <h3>{rol ? 'Editar rol y permisos' : 'Nuevo rol'}</h3>
      <fieldset disabled={ocupado}>
        <label>
          Código del rol
          <input
            name="codigo"
            required
            pattern="[A-Z0-9_]{3,80}"
            readOnly={!!rol}
            defaultValue={rol?.codigo}
          />
        </label>
        <label>
          Nombre del rol
          <input name="nombre" required maxLength={120} defaultValue={rol?.nombre} />
        </label>
        <label>
          Descripción del rol
          <textarea name="descripcion" maxLength={4000} defaultValue={rol?.descripcion ?? ''} />
        </label>
        <label>
          Ámbito del rol
          <select
            value={ambito}
            disabled={!!rol}
            onChange={(e) => {
              setAmbito(e.target.value);
              setSeleccion([]);
            }}
          >
            {['GLOBAL', 'PROYECTO', 'AMBOS'].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
        <label>
          Estado del rol
          <select name="estado" defaultValue={rol?.estado ?? 'ACTIVO'}>
            <option>ACTIVO</option>
            <option>INACTIVO</option>
          </select>
        </label>
        {rol && (
          <p className="muted">
            {rol.asignaciones} asignaciones vigentes. Código y ámbito permanecen estables.
          </p>
        )}
        <fieldset className="permission-matrix">
          <legend>Permisos por módulo</legend>
          {[...new Set(validos.map((p) => p.modulo))].map((modulo) => (
            <section key={modulo}>
              <h4>{modulo}</h4>
              {validos
                .filter((p) => p.modulo === modulo)
                .map((p) => (
                  <label className="check" key={p.id}>
                    <input
                      type="checkbox"
                      checked={seleccion.includes(p.id)}
                      onChange={(e) =>
                        setSeleccion((actual) =>
                          e.target.checked ? [...actual, p.id] : actual.filter((id) => id !== p.id),
                        )
                      }
                    />
                    <span>
                      {p.codigo}
                      <small className="muted">{p.descripcion}</small>
                    </span>
                  </label>
                ))}
            </section>
          ))}
        </fieldset>
        <div className="form-actions">
          <button type="button" onClick={cancelar}>
            Cancelar edición
          </button>
          <button className="primary">{ocupado ? 'Guardando…' : 'Guardar rol y permisos'}</button>
        </div>
      </fieldset>
    </form>
  );
}
