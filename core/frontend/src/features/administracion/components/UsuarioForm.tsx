import { useState, type FormEvent } from 'react';
import type { DatosUsuario, Rol, Usuario } from '../api/administracionApi';

export function UsuarioForm({
  usuario,
  roles,
  ocupado,
  guardar,
  cancelar,
}: {
  usuario: Usuario | null;
  roles: Rol[];
  ocupado: boolean;
  guardar: (datos: DatosUsuario) => Promise<void>;
  cancelar: () => void;
}) {
  const [seleccion, setSeleccion] = useState<string[]>(
    usuario ? JSON.parse(usuario.roles_json) : [],
  );
  function elegir(id: string, checked: boolean) {
    setSeleccion((actual) => (checked ? [...actual, id] : actual.filter((r) => r !== id)));
  }
  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (
      usuario &&
      !window.confirm('Se actualizará la cuenta y se cerrarán sus sesiones actuales. ¿Confirmar?')
    )
      return;
    const f = new FormData(e.currentTarget);
    await guardar({
      usuario: String(f.get('usuario')),
      correo: String(f.get('correo')),
      nombre: String(f.get('nombre')),
      estado: usuario ? String(f.get('estado')) : 'PENDIENTE',
      roles: seleccion,
      revision: usuario?.revision,
    });
  }
  return (
    <form onSubmit={enviar} aria-label="Datos del usuario">
      <h3>{usuario ? 'Editar usuario' : 'Nuevo usuario'}</h3>
      <fieldset disabled={ocupado}>
        <label>
          Nombre de usuario
          <input
            name="usuario"
            required
            maxLength={80}
            pattern="[A-Za-z0-9_.-]+"
            defaultValue={usuario?.usuario}
          />
        </label>
        <label>
          Correo electrónico
          <input
            name="correo"
            type="email"
            required
            maxLength={254}
            defaultValue={usuario?.correo}
          />
        </label>
        <label>
          Nombre completo
          <input name="nombre" required maxLength={160} defaultValue={usuario?.nombre} />
        </label>
        {usuario && (
          <label>
            Estado de la cuenta
            <select name="estado" defaultValue={usuario.estado}>
              {['ACTIVO', 'PENDIENTE', 'INACTIVO', 'BLOQUEADO'].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
        )}
        <p className="muted">
          La cuenta nueva debe activar su credencial y elegir una contraseña propia.
        </p>
        <fieldset>
          <legend>Roles globales</legend>
          {roles
            .filter((r) => r.ambito !== 'PROYECTO' && r.estado === 'ACTIVO')
            .map((r) => (
              <label className="check" key={r.id}>
                <input
                  type="checkbox"
                  checked={seleccion.includes(r.id)}
                  onChange={(e) => elegir(r.id, e.target.checked)}
                />
                {r.nombre}
              </label>
            ))}
          {!roles.some((r) => r.ambito !== 'PROYECTO' && r.estado === 'ACTIVO') && (
            <p>No hay roles globales activos.</p>
          )}
        </fieldset>
        <div className="form-actions">
          <button type="button" onClick={cancelar}>
            Cancelar edición
          </button>
          <button className="primary">{ocupado ? 'Guardando…' : 'Guardar usuario'}</button>
        </div>
      </fieldset>
    </form>
  );
}
