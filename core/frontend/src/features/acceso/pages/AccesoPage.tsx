import { useState, type FormEvent } from 'react';
import { useSesion } from '../estado/SesionProvider';
import { api } from '../../../shared/api';
export function AccesoPage() {
  const { entrar, error: estadoError } = useSesion();
  const [error, setError] = useState(''),
    [enviando, setEnviando] = useState(false);
  const [activar, setActivar] = useState(false),
    [mensaje, setMensaje] = useState('');
  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setError('');
    setEnviando(true);
    try {
      if (activar) {
        if (form.get('nueva') !== form.get('confirmacion'))
          throw new Error('Las contraseñas nuevas no coinciden.');
        await api<void>('/acceso/activar', {
          method: 'POST',
          body: JSON.stringify({
            identificador: String(form.get('identificador')),
            temporal: String(form.get('contrasena')),
            nueva: String(form.get('nueva')),
          }),
        });
        setActivar(false);
        setMensaje('Cuenta activada. Ingresa con la contraseña que elegiste.');
      } else await entrar(String(form.get('identificador')), String(form.get('contrasena')));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }
  return (
    <main className="login-layout">
      <section className="login-story">
        <div className="brand">
          <span className="brand-icon">◇</span> trama<span className="brand-tag">UML</span>
        </div>
        <div className="story-copy">
          <span className="eyebrow light">DEL CONCEPTO AL SISTEMA</span>
          <h1>
            Las grandes ideas
            <br />
            empiezan conectadas.
          </h1>
          <p>
            Un espacio para dar forma a tus modelos, conservar tus decisiones y construir en equipo.
          </p>
          <div className="concept-art" aria-hidden="true">
            <div className="art-class">
              <b>Proyecto</b>
              <span>− nombre: String</span>
              <span>− estado: Estado</span>
            </div>
            <div className="art-link">1 ───────── 0..*</div>
            <div className="art-class second">
              <b>Modelo</b>
              <span>− nombre: String</span>
              <span>+ guardar(): void</span>
            </div>
          </div>
        </div>
        <div className="story-footer">Plataforma colaborativa de modelado UML</div>
      </section>
      <section className="login-form">
        <div>
          <span className="eyebrow">TU ESPACIO DE TRABAJO</span>
          <h2>Bienvenido de nuevo</h2>
          <p className="muted">Ingresa con la cuenta de tu plataforma.</p>
          {mensaje && <p role="status">{mensaje}</p>}
          <form key={String(activar)} onSubmit={enviar}>
            <label>
              Correo o usuario
              <input
                name="identificador"
                autoComplete="username"
                required
                maxLength={254}
                placeholder="Tu correo o nombre de usuario"
              />
            </label>
            <label>
              {activar ? 'Credencial temporal' : 'Contraseña'}
              <input
                name="contrasena"
                type="password"
                autoComplete="current-password"
                required
                maxLength={72}
                placeholder="Tu contraseña"
              />
            </label>
            {activar && (
              <>
                <label>
                  Nueva contraseña
                  <input
                    name="nueva"
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={12}
                    maxLength={72}
                  />
                </label>
                <label>
                  Confirmar contraseña nueva
                  <input
                    name="confirmacion"
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={12}
                    maxLength={72}
                  />
                </label>
              </>
            )}
            {(error || estadoError) && (
              <p className="error" role="alert">
                {error || estadoError}
              </p>
            )}
            <button className="primary wide" disabled={enviando}>
              {enviando
                ? 'Procesando…'
                : activar
                  ? 'Confirmar activación'
                  : 'Entrar a la plataforma →'}
            </button>
          </form>
          <button
            className="text-button wide"
            disabled={enviando}
            onClick={() => {
              setActivar((v) => !v);
              setError('');
              setMensaje('');
            }}
          >
            {activar ? 'Volver al inicio de sesión' : 'Activar cuenta con credencial temporal'}
          </button>
          <p className="login-note">
            Tu administrador gestiona el acceso. Si aún no tienes una cuenta, solicita tu
            incorporación.
          </p>
        </div>
      </section>
    </main>
  );
}
