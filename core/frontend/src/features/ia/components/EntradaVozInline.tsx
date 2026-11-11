import { useEffect, useRef, useState } from 'react';

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
  onerror: ((evento: Event) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
};

type ConstructorReconocimiento = new () => Reconocimiento;

export type DatosVoz = {
  texto: string;
  audio?: Blob | null;
  duracionSegundos: number;
};

export function EntradaVozInline({
  deshabilitado,
  enviando,
  textoInicial = '',
  alConfirmar,
  alCancelar,
  onCambioTexto,
}: {
  deshabilitado?: boolean;
  enviando?: boolean;
  textoInicial?: string;
  alConfirmar: (datos: DatosVoz) => Promise<void> | void;
  alCancelar: () => void;
  onCambioTexto?: (texto: string) => void;
}) {
  const constructorReconocimiento =
    typeof window !== 'undefined'
      ? (((window as unknown as Record<string, unknown>).SpeechRecognition ??
          (window as unknown as Record<string, unknown>).webkitSpeechRecognition) as
          | ConstructorReconocimiento
          | undefined)
      : undefined;

  const [soportaReconocimiento] = useState(!!constructorReconocimiento);
  const [escuchando, setEscuchando] = useState(false);
  const [grabandoAudio, setGrabandoAudio] = useState(false);
  const [segundos, setSegundos] = useState(0);
  const [texto, setTexto] = useState(textoInicial);
  const [transcripcionEnVivo, setTranscripcionEnVivo] = useState(textoInicial);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [vistaAudio, setVistaAudio] = useState('');
  const [error, setError] = useState('');
  const [modoForzadoAudio, setModoForzadoAudio] = useState(!constructorReconocimiento);

  const reconocimientoRef = useRef<Reconocimiento | null>(null);
  const grabadorRef = useRef<MediaRecorder | null>(null);
  const flujoRef = useRef<MediaStream | null>(null);
  const fragmentosRef = useRef<Blob[]>([]);
  const usuarioDetuvoRef = useRef(false);

  // Temporizador de duración de grabación
  useEffect(() => {
    if (!escuchando && !grabandoAudio) return;
    const reloj = window.setInterval(() => {
      setSegundos((actual) => {
        if (actual + 1 >= DURACION_MAXIMA) {
          detenerTodo();
          setError('Se alcanzó el tiempo máximo de dictado (120 segundos).');
          return DURACION_MAXIMA;
        }
        return actual + 1;
      });
    }, 1000);
    return () => window.clearInterval(reloj);
  }, [escuchando, grabandoAudio]);

  // Gestión de URL del blob de audio
  useEffect(() => {
    if (!audioBlob) {
      setVistaAudio('');
      return;
    }
    const url = URL.createObjectURL(audioBlob);
    setVistaAudio(url);
    return () => URL.revokeObjectURL(url);
  }, [audioBlob]);

  // Iniciar automáticamente el dictado al montar el componente
  useEffect(() => {
    iniciarTodo();
    return () => {
      detenerTodoLimpieza();
    };
  }, []);

  function detenerTodoLimpieza() {
    usuarioDetuvoRef.current = true;
    try {
      reconocimientoRef.current?.abort();
    } catch {}
    try {
      if (grabadorRef.current?.state === 'recording') {
        grabadorRef.current.stop();
      }
    } catch {}
    if (flujoRef.current) {
      flujoRef.current.getTracks().forEach((t) => t.stop());
      flujoRef.current = null;
    }
  }

  async function iniciarTodo() {
    setError('');
    usuarioDetuvoRef.current = false;
    setAudioBlob(null);

    // 1. Iniciar captura de audio para respaldo/visualizador si es posible
    try {
      if (navigator.mediaDevices?.getUserMedia) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        flujoRef.current = stream;
        fragmentosRef.current = [];
        const mime = MediaRecorder.isTypeSupported('audio/webm')
          ? 'audio/webm'
          : MediaRecorder.isTypeSupported('audio/mp4')
            ? 'audio/mp4'
            : '';
        const rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
        rec.ondataavailable = (e) => {
          if (e.data && e.data.size > 0) {
            fragmentosRef.current.push(e.data);
          }
        };
        rec.onstop = () => {
          const blob = new Blob(fragmentosRef.current, {
            type: rec.mimeType || 'audio/webm',
          });
          setAudioBlob(blob);
          if (flujoRef.current) {
            flujoRef.current.getTracks().forEach((t) => t.stop());
            flujoRef.current = null;
          }
          setGrabandoAudio(false);
        };
        grabadorRef.current = rec;
        rec.start(250);
        setGrabandoAudio(true);
      }
    } catch {
      // Si no da permiso de MediaStream, avisar si estamos en modo audio exclusivo
      if (!soportaReconocimiento || modoForzadoAudio) {
        setError('No se pudo acceder al micrófono. Por favor habilita los permisos en tu navegador.');
        return;
      }
    }

    // 2. Iniciar SpeechRecognition si está soportado
    if (soportaReconocimiento && !modoForzadoAudio && constructorReconocimiento) {
      try {
        const rec = new constructorReconocimiento();
        rec.lang = navigator.language || 'es-ES';
        rec.continuous = true;
        rec.interimResults = true;

        rec.onresult = (evento: EventoReconocimiento) => {
          let transcripcionAcumulada = '';
          for (let i = 0; i < evento.results.length; i += 1) {
            transcripcionAcumulada += evento.results[i][0].transcript;
          }
          const limpia = transcripcionAcumulada.trim();
          setTranscripcionEnVivo(limpia);
          setTexto(limpia);
          onCambioTexto?.(limpia);
        };

        rec.onerror = (e) => {
          console.warn('SpeechRecognition error:', e);
          if (!transcripcionEnVivo) {
            setError(
              'No se detectó audio nítido o el servicio de voz del navegador no respondió. Puedes escribir o enviar el audio.',
            );
          }
        };

        rec.onend = () => {
          if (!usuarioDetuvoRef.current && grabandoAudio) {
            try {
              rec.start();
              return;
            } catch {}
          }
          setEscuchando(false);
        };

        reconocimientoRef.current = rec;
        rec.start();
        setEscuchando(true);
      } catch (err) {
        console.warn('Fallo al iniciar reconocimiento:', err);
        setEscuchando(false);
      }
    }
  }

  function detenerTodo() {
    usuarioDetuvoRef.current = true;
    setEscuchando(false);
    if (reconocimientoRef.current) {
      try {
        reconocimientoRef.current.stop();
      } catch {}
    }
    if (grabadorRef.current && grabadorRef.current.state === 'recording') {
      try {
        grabadorRef.current.stop();
      } catch {}
    }
  }

  function reiniciar() {
    detenerTodo();
    setTexto('');
    setTranscripcionEnVivo('');
    setAudioBlob(null);
    setSegundos(0);
    setError('');
    onCambioTexto?.('');
    setTimeout(() => {
      iniciarTodo();
    }, 150);
  }

  async function confirmar() {
    detenerTodo();
    const textoFinal = texto.trim() || transcripcionEnVivo.trim();
    if (!textoFinal && !audioBlob) {
      setError('Por favor dicta o escribe una instrucción antes de enviar.');
      return;
    }
    await alConfirmar({
      texto: textoFinal,
      audio: audioBlob,
      duracionSegundos: Math.max(1, segundos),
    });
  }

  const formatoTiempo = (s: number) => {
    const min = Math.floor(s / 60);
    const seg = s % 60;
    return `${min}:${seg < 10 ? '0' : ''}${seg}`;
  };

  const activo = escuchando || grabandoAudio;

  return (
    <div className="ia-voice-inline-dock" role="region" aria-label="Entrada de datos por voz">
      {/* Barra de estado superior */}
      <div className="ia-voice-status-bar">
        <div className="ia-voice-status-left">
          <span className={`ia-voice-pulse-dot ${activo ? 'live' : 'paused'}`} />
          <span className="ia-voice-status-label">
            {activo
              ? soportaReconocimiento && !modoForzadoAudio
                ? 'Escuchando tu voz en tiempo real…'
                : 'Grabando audio de voz…'
              : 'Dictado pausado'}
          </span>
        </div>

        <div className="ia-voice-status-right">
          <span className="ia-voice-timer-badge">
            ⏱ {formatoTiempo(segundos)} / {formatoTiempo(DURACION_MAXIMA)}
          </span>

          {soportaReconocimiento && (
            <button
              type="button"
              className="ia-voice-mode-toggle"
              title="Cambiar entre transcripción local y grabación de audio"
              onClick={() => {
                detenerTodo();
                setModoForzadoAudio((v) => !v);
                setTimeout(() => iniciarTodo(), 100);
              }}
            >
              {modoForzadoAudio ? '🎙 Modo transcripción' : '📼 Modo audio puro'}
            </button>
          )}
        </div>
      </div>

      {/* Visualizador de onda de sonido animado */}
      <div className={`ia-voice-visualizer ${activo ? 'active' : ''}`}>
        {Array.from({ length: 18 }, (_, i) => (
          <span
            key={i}
            className="ia-voice-bar"
            style={{
              animationDelay: `${(i % 6) * 110}ms`,
              animationDuration: `${550 + (i % 5) * 90}ms`,
            }}
          />
        ))}
      </div>

      {/* 🗣️ CONTENEDOR DESTACADO: TEXTO QUE HABLASTE (VISUALIZACIÓN EN VIVO) */}
      <div className="ia-voice-live-card">
        <div className="ia-voice-live-header">
          <span className="ia-voice-live-kicker">
            🗣️ {activo ? 'Lo que estás diciendo (en vivo):' : 'Texto dictado por voz:'}
          </span>
          {activo && <span className="ia-voice-live-pill">EN VIVO</span>}
        </div>

        <div className="ia-voice-live-display">
          {transcripcionEnVivo ? (
            <p className="ia-voice-live-text">«{transcripcionEnVivo}»</p>
          ) : (
            <p className="ia-voice-live-placeholder">
              {activo
                ? 'Habla claramente; tus palabras se irán escribiendo aquí automáticamente…'
                : 'Presiona "Hablar de nuevo" para comenzar a dictar o edita el texto abajo.'}
            </p>
          )}
        </div>
      </div>

      {/* Área de texto editable para afinar o corregir palabras antes de enviar */}
      <div className="ia-voice-edit-section">
        <label className="ia-voice-edit-label" htmlFor="ia-voice-edit-input">
          ✏️ Puedes editar o completar la instrucción si deseas:
        </label>
        <textarea
          id="ia-voice-edit-input"
          className="ia-voice-edit-textarea"
          rows={2}
          value={texto}
          placeholder="Aquí puedes ajustar o agregar detalles al texto dictado…"
          disabled={enviando || deshabilitado}
          onChange={(e) => {
            setTexto(e.target.value);
            onCambioTexto?.(e.target.value);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              void confirmar();
            }
          }}
        />
      </div>

      {/* Reproductor si hay audio grabado */}
      {vistaAudio && !activo && (
        <div className="ia-voice-preview-wrapper">
          <span className="ia-voice-audio-label">🎧 Escuchar lo que hablaste:</span>
          <audio className="ia-voice-audio-element" controls src={vistaAudio} preload="metadata" />
        </div>
      )}

      {/* Alerta de error si ocurrió alguno */}
      {error && (
        <div className="ia-voice-error-banner" role="alert">
          <span>⚠ {error}</span>
        </div>
      )}

      {/* Barra de Controles y Acciones */}
      <div className="ia-voice-actions-bar">
        <div className="ia-voice-actions-left">
          {activo ? (
            <button
              type="button"
              className="ia-voice-btn stop"
              onClick={detenerTodo}
              title="Detener dictado y conservar el texto"
            >
              ⏹ Detener
            </button>
          ) : (
            <button
              type="button"
              className="ia-voice-btn resume"
              onClick={iniciarTodo}
              title="Continuar dictando"
            >
              🎙 Hablar de nuevo
            </button>
          )}

          <button
            type="button"
            className="ia-voice-btn reset"
            onClick={reiniciar}
            title="Borrar texto y reiniciar dictado"
            disabled={enviando || deshabilitado}
          >
            ↺ Limpiar
          </button>

          <button
            type="button"
            className="ia-voice-btn cancel"
            onClick={() => {
              detenerTodo();
              alCancelar();
            }}
            title="Cerrar el modo de voz y volver a texto"
            disabled={enviando}
          >
            ✕ Salir
          </button>
        </div>

        <div className="ia-voice-actions-right">
          <button
            type="button"
            className="ia-voice-btn primary send"
            disabled={(!texto.trim() && !audioBlob) || enviando || deshabilitado}
            onClick={confirmar}
          >
            {enviando ? 'Interpretando…' : 'Generar propuesta ➤'}
          </button>
        </div>
      </div>
    </div>
  );
}
