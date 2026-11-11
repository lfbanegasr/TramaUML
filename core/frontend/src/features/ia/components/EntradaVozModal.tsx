import { useEffect, useRef, useState } from 'react';
import { solicitarTexto, solicitarVoz } from '../api/iaApi';

const DURACION_MAXIMA = 120;

type ResultadoReconocimiento = {
  0: { transcript: string };
  isFinal: boolean;
};

type EventoReconocimiento = Event & {
  results: ArrayLike<ResultadoReconocimiento>;
};

type Reconocimiento = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((evento: EventoReconocimiento) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
};

type ConstructorReconocimiento = new () => Reconocimiento;

declare global {
  interface Window {
    SpeechRecognition?: ConstructorReconocimiento;
    webkitSpeechRecognition?: ConstructorReconocimiento;
  }
}

export function EntradaVozModal({
  modelo,
  cerrar,
  alGenerar,
}: {
  modelo: string;
  cerrar: () => void;
  alGenerar: (id: string) => Promise<void>;
}) {
  const constructor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  const [modoGrabacion, setModoGrabacion] = useState(!constructor);
  const [escuchando, setEscuchando] = useState(false);
  const [grabando, setGrabando] = useState(false);
  const [segundos, setSegundos] = useState(0);
  const [texto, setTexto] = useState('');
  const [audio, setAudio] = useState<Blob | null>(null);
  const [vistaAudio, setVistaAudio] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const reconocimiento = useRef<Reconocimiento | null>(null);
  const grabador = useRef<MediaRecorder | null>(null);
  const flujo = useRef<MediaStream | null>(null);
  const fragmentos = useRef<Blob[]>([]);

  useEffect(() => {
    if (!grabando) return;
    const reloj = window.setInterval(() => setSegundos((actual) => actual + 1), 1000);
    return () => window.clearInterval(reloj);
  }, [grabando]);

  useEffect(() => {
    if (!grabando || segundos < DURACION_MAXIMA) return;
    if (grabador.current?.state === 'recording') grabador.current.stop();
    setError('La grabación se detuvo al alcanzar el límite de 120 segundos.');
  }, [grabando, segundos]);

  useEffect(() => {
    if (!audio) {
      setVistaAudio('');
      return;
    }
    const url = URL.createObjectURL(audio);
    setVistaAudio(url);
    return () => URL.revokeObjectURL(url);
  }, [audio]);

  useEffect(
    () => () => {
      reconocimiento.current?.abort();
      if (grabador.current?.state === 'recording') grabador.current.stop();
      flujo.current?.getTracks().forEach((pista) => pista.stop());
    },
    [],
  );

  function iniciarReconocimiento() {
    if (!constructor || escuchando) return;
    setError('');
    const instancia = new constructor();
    instancia.lang = 'es-BO';
    instancia.continuous = false;
    instancia.interimResults = true;
    instancia.onresult = (evento) => {
      let transcripcion = '';
      for (let i = 0; i < evento.results.length; i += 1)
        transcripcion += evento.results[i][0].transcript;
      setTexto(transcripcion.trim());
    };
    instancia.onerror = () => {
      setError('No se pudo reconocer la voz. Puedes usar la grabación de audio como respaldo.');
      setEscuchando(false);
    };
    instancia.onend = () => setEscuchando(false);
    reconocimiento.current = instancia;
    setEscuchando(true);
    instancia.start();
  }

  async function iniciarGrabacion() {
    setError('');
    setAudio(null);
    setSegundos(0);
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: true });
      flujo.current = media;
      fragmentos.current = [];
      const tipo = MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '';
      const instancia = new MediaRecorder(media, tipo ? { mimeType: tipo } : undefined);
      instancia.ondataavailable = (evento) => {
        if (evento.data.size) fragmentos.current.push(evento.data);
      };
      instancia.onstop = () => {
        setAudio(new Blob(fragmentos.current, { type: instancia.mimeType || 'audio/webm' }));
        flujo.current?.getTracks().forEach((pista) => pista.stop());
        flujo.current = null;
        setGrabando(false);
      };
      grabador.current = instancia;
      instancia.start();
      setGrabando(true);
    } catch {
      setError('No fue posible acceder al micrófono. Revisa el permiso del navegador.');
    }
  }

  function detenerGrabacion() {
    if (grabador.current?.state === 'recording') grabador.current.stop();
  }

  async function enviar() {
    if (enviando) return;
    setEnviando(true);
    setError('');
    try {
      const resultado = modoGrabacion
        ? audio
          ? await solicitarVoz(modelo, audio, texto, Math.max(1, segundos))
          : null
        : texto.trim()
          ? await solicitarTexto(modelo, texto.trim())
          : null;
      if (!resultado) throw new Error('Graba audio o dicta una instrucción antes de continuar.');
      if (!resultado.idPropuesta) throw new Error(resultado.mensaje);
      await alGenerar(resultado.idPropuesta);
      cerrar();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="dialog ia-voice-dialog" role="dialog" aria-modal="true">
        <div className="dialog-header">
          <h2>Modificar mediante voz</h2>
          <button className="dialog-close" aria-label="Cerrar voz" onClick={cerrar}>
            ×
          </button>
        </div>
        <div className="dialog-body">
          {!modoGrabacion && constructor ? (
            <>
              <p className="dialog-desc">
                El navegador transcribirá localmente tu instrucción. Podrás corregirla antes de
                generar la propuesta.
              </p>
              <button
                className={'ia-microphone' + (escuchando ? ' listening' : '')}
                disabled={escuchando || enviando}
                onClick={iniciarReconocimiento}
              >
                <span>◉</span>
                {escuchando ? 'Escuchando…' : 'Iniciar dictado'}
              </button>
            </>
          ) : (
            <>
              <p className="dialog-desc">
                La grabación se almacenará como evidencia. Si no hay transcriptor configurado, añade
                una transcripción manual o el servidor registrará el fallo sin inventarla.
              </p>
              <div className={'ia-audio-visualizer' + (grabando ? ' active' : '')}>
                {Array.from({ length: 14 }, (_, i) => (
                  <span key={i} style={{ animationDelay: i * 45 + 'ms' }} />
                ))}
              </div>
              <p className="ia-recording-time">
                {audio
                  ? `Audio listo · ${segundos} s`
                  : grabando
                    ? `Grabando · ${segundos} / ${DURACION_MAXIMA} s`
                    : 'Sin grabación'}
              </p>
              <button
                className={grabando ? 'danger' : 'primary'}
                disabled={enviando}
                onClick={grabando ? detenerGrabacion : iniciarGrabacion}
              >
                {grabando ? 'Detener grabación' : 'Grabar audio'}
              </button>
              {vistaAudio && (
                <audio className="ia-audio-preview" controls preload="metadata" src={vistaAudio}>
                  Tu navegador no permite reproducir esta grabación.
                </audio>
              )}
            </>
          )}
          <label className="ia-prompt-label" htmlFor="ia-voice-text">
            Transcripción
          </label>
          <textarea
            id="ia-voice-text"
            rows={4}
            value={texto}
            maxLength={12000}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Aquí aparecerá el dictado; también puedes escribir o corregir el texto."
          />
          {constructor && (
            <button
              className="text-button ia-mode-switch"
              onClick={() => {
                reconocimiento.current?.abort();
                setEscuchando(false);
                setModoGrabacion((actual) => !actual);
              }}
            >
              {modoGrabacion ? 'Usar reconocimiento del navegador' : 'Usar grabación de audio'}
            </button>
          )}
          {error && (
            <p className="dialog-error" role="alert">
              {error}
            </p>
          )}
        </div>
        <div className="dialog-footer">
          <button disabled={enviando || grabando} onClick={cerrar}>
            Cancelar
          </button>
          <button
            className="primary"
            disabled={enviando || grabando || (modoGrabacion ? !audio : !texto.trim())}
            onClick={enviar}
          >
            {enviando ? 'Procesando…' : 'Generar propuesta'}
          </button>
        </div>
      </section>
    </div>
  );
}
