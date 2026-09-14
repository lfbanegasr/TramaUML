import { useState, type FormEvent } from 'react';
import { useSesion } from '../estado/SesionProvider';
export function AccesoPage() {
  const { entrar, error: estadoError } = useSesion();
  const [error, setError] = useState(''), [enviando, setEnviando] = useState(false);
  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = new FormData(e.currentTarget); setError(''); setEnviando(true);
    try { await entrar(String(form.get('identificador')), String(form.get('contrasena'))); }
    catch (e) { setError((e as Error).message); } finally { setEnviando(false); }
  }
  return <main className="login-layout">
    <section className="login-story"><div className="brand"><span className="brand-icon">◇</span> trama<span className="brand-tag">UML</span></div>
      <div className="story-copy"><span className="eyebrow light">DEL CONCEPTO AL SISTEMA</span><h1>Las grandes ideas<br />empiezan conectadas.</h1>
        <p>Un espacio para dar forma a tus modelos, conservar tus decisiones y construir en equipo.</p>
        <div className="concept-art" aria-hidden="true"><div className="art-class"><b>Proyecto</b><span>− nombre: String</span><span>− estado: Estado</span></div><div className="art-link">1 ───────── 0..*</div><div className="art-class second"><b>Modelo</b><span>− nombre: String</span><span>+ guardar(): void</span></div></div>
      </div><div className="story-footer">Plataforma colaborativa de modelado UML</div>
    </section>
    <section className="login-form"><div><span className="eyebrow">TU ESPACIO DE TRABAJO</span><h2>Bienvenido de nuevo</h2><p className="muted">Ingresa con la cuenta de tu plataforma.</p>
      <form onSubmit={enviar}><label>Correo o usuario<input name="identificador" autoComplete="username" required maxLength={254} placeholder="Tu correo o nombre de usuario" /></label>
        <label>Contraseña<input name="contrasena" type="password" autoComplete="current-password" required maxLength={72} placeholder="Tu contraseña" /></label>
        {(error || estadoError) && <p className="error" role="alert">{error || estadoError}</p>}
        <button className="primary wide" disabled={enviando}>{enviando ? 'Ingresando…' : 'Entrar a la plataforma →'}</button></form>
      <p className="login-note">Tu administrador gestiona el acceso. Si aún no tienes una cuenta, solicita tu incorporación.</p>
    </div></section>
  </main>;
}
