import { useState, type FormEvent, type MouseEvent } from 'react';
import { solicitarTexto } from '../api/iaApi';

export function BotonAsistenteIa({
  modelo,
  disabled,
  hayPropuesta,
  alGenerar,
  abrirVoz,
  abrirImagen,
  abrirRevision,
}: {
  modelo: string;
  disabled: boolean;
  hayPropuesta: boolean;
  alGenerar: (id: string) => Promise<void>;
  abrirVoz: () => void;
  abrirImagen: () => void;
  abrirRevision: () => void;
}) {
  const [abierto, setAbierto] = useState(false);
  const [prompt, setPrompt] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');

  async function enviar(e: FormEvent) {
    e.preventDefault();
    if (!prompt.trim() || enviando) return;
    setEnviando(true);
    setError('');
    try {
      const resultado = await solicitarTexto(modelo, prompt.trim());
      if (!resultado.idPropuesta) throw new Error(resultado.mensaje);
      await alGenerar(resultado.idPropuesta);
      setPrompt('');
      setAbierto(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  function cerrarDesdeFondo(e: MouseEvent<HTMLDivElement>) {
    if (e.currentTarget === e.target && !enviando) setAbierto(false);
  }

  return (
    <>
      <button
        id="btn-asistente-ia"
        className="ia-toolbar-button"
        disabled={disabled}
        onClick={() => setAbierto(true)}
      >
        ✦ Asistente IA{hayPropuesta ? ' · pendiente' : ''}
      </button>
      {abierto && (
        <div className="dialog-backdrop" role="presentation" onMouseDown={cerrarDesdeFondo}>
          <section
            className="dialog dialog-wide ia-entry-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="ia-entry-title"
          >
            <div className="dialog-header">
              <div>
                <span className="ia-kicker">ASISTENCIA INTELIGENTE</span>
                <h2 id="ia-entry-title">Describe el cambio que necesitas</h2>
              </div>
              <button
                className="dialog-close"
                aria-label="Cerrar asistente"
                disabled={enviando}
                onClick={() => setAbierto(false)}
              >
                ×
              </button>
            </div>
            <form onSubmit={enviar}>
              <div className="dialog-body">
                <p className="dialog-desc">
                  El asistente preparará una propuesta. Podrás revisar cada operación antes de
                  aplicarla al modelo.
                </p>
                <label className="ia-prompt-label" htmlFor="ia-prompt">
                  Instrucción
                </label>
                <textarea
                  id="ia-prompt"
                  rows={6}
                  maxLength={12000}
                  autoFocus
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  placeholder="Ej.: Crea una clase Cliente con atributos id: Long y nombre: String, y relaciónala con Pedido de 1 a muchos."
                />
                <div className="ia-input-methods">
                  <button
                    type="button"
                    onClick={() => {
                      setAbierto(false);
                      abrirVoz();
                    }}
                  >
                    ◉ Usar voz
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setAbierto(false);
                      abrirImagen();
                    }}
                  >
                    ▧ Subir foto de pizarra
                  </button>
                  {hayPropuesta && (
                    <button
                      type="button"
                      onClick={() => {
                        setAbierto(false);
                        abrirRevision();
                      }}
                    >
                      Revisar propuesta pendiente
                    </button>
                  )}
                </div>
                {error && (
                  <p className="dialog-error" role="alert">
                    {error}
                  </p>
                )}
              </div>
              <div className="dialog-footer">
                <button type="button" disabled={enviando} onClick={() => setAbierto(false)}>
                  Cancelar
                </button>
                <button className="primary" disabled={enviando || !prompt.trim()}>
                  {enviando ? 'Interpretando…' : 'Generar propuesta'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </>
  );
}
