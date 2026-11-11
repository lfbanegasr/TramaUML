import { useState, useRef, useEffect, type FormEvent, type KeyboardEvent } from 'react';
import {
  enviarMensajeAgente, enviarVozAgente, leerConversacion, leerCapacidadesAgente, confirmarAcuerdo, cancelarEjecucion,
  type MensajeAgente, type AcuerdoAgente, type EjecucionAgente,
  leerPropuesta,
  revisarPropuesta,
  aplicarPropuesta,
  rechazarPropuesta,
  type PropuestaIa,
  type OperacionIa,
} from '../api/iaApi';

const ETIQUETAS_ENTIDAD: Record<string, string> = {
  CLASE: 'Clase',
  ATRIBUTO: 'Atributo',
  OPERACION: 'Operación',
  RELACION: 'Relación',
  PAQUETE: 'Paquete',
};

export type MensajeChat = {
  id: string;
  emisor: 'USUARIO' | 'ASISTENTE';
  texto: string;
  hora: string;
  metodoEntrada?: 'TEXTO' | 'VOZ' | 'IMAGEN';
  audioUrl?: string;
  propuesta?: PropuestaIa | null;
  estadoPropuesta?: 'PENDIENTE' | 'APLICADA' | 'RECHAZADA';
  operacionesSeleccionadas?: Record<string, boolean>;
  error?: boolean;
  autor?: string;
  respuestaANombre?: string;
  fuentes?: string[];
  extraccion?: string | null;
  supuestos?: string[];
};

function horaActual(): string {
  const d = new Date();
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function PanelAsistenteIa({
  modelo,
  revision,
  disabled,
  propuestaPendiente,
  cerrar,
  alAplicarPropuesta,
  abrirVoz,
  abrirImagen,
  nombreProyecto,
  clasesExistentes = [],
  claseSeleccionada,
  promptInicial,
}: {
  modelo: string;
  revision: number;
  disabled: boolean;
  propuestaPendiente: PropuestaIa | null;
  cerrar: () => void;
  alAplicarPropuesta: () => Promise<void>;
  abrirVoz?: () => void;
  abrirImagen: () => void;
  nombreProyecto?: string;
  clasesExistentes?: { id: string; nombre: string }[];
  claseSeleccionada?: string | null;
  promptInicial?: string | null;
}) {
  const [mensajes, setMensajes] = useState<MensajeChat[]>(() => {
    const iniciales: MensajeChat[] = [
      {
        id: 'bienvenida',
        emisor: 'ASISTENTE',
        texto:
          '¡Hola! Soy tu agente asistente para el modelo UML. Puedes pedirme en lenguaje natural crear clases, atributos con o sin tipo (los infiero automáticamente) o relaciones entre elementos.',
        hora: horaActual(),
      },
    ];
    if (propuestaPendiente) {
      const selec: Record<string, boolean> = {};
      propuestaPendiente.operaciones.forEach((o) => {
        selec[o.id] = o.seleccionada;
      });
      iniciales.push({
        id: 'pendiente-' + propuestaPendiente.id,
        emisor: 'ASISTENTE',
        texto: `Tienes una propuesta pendiente de la revisión ${propuestaPendiente.revisionBase}:`,
        hora: horaActual(),
        propuesta: propuestaPendiente,
        estadoPropuesta: 'PENDIENTE',
        operacionesSeleccionadas: selec,
      });
    }
    return iniciales;
  });

  const [prompt, setPrompt] = useState(promptInicial ?? '');
  const [enviando, setEnviando] = useState(false);
  const [intencion, setIntencion] = useState('AUTO');
  const [errorChat, setErrorChat] = useState('');
  const [modoProveedor, setModoProveedor] = useState('Memoria en PostgreSQL');
  const [acuerdos, setAcuerdos] = useState<AcuerdoAgente[]>([]);
  const [ejecuciones, setEjecuciones] = useState<EjecucionAgente[]>([]);
  const cursorChat = useRef(0);
  const entradasChat = useRef(new Map<string, MensajeAgente>());
  const reintento = useRef<{ texto: string; clave: string } | null>(null);
  const sincronizarChat = useRef<() => Promise<void>>(async () => {});
  const [escuchandoVoz, setEscuchandoVoz] = useState(false);
  const [segundosVoz, setSegundosVoz] = useState(0);
  const [errorVoz, setErrorVoz] = useState('');
  const [procesandoPropuesta, setProcesandoPropuesta] = useState<string | null>(null);
  const [verIdeas, setVerIdeas] = useState(false);
  const [contextoClase, setContextoClase] = useState<string | null>(claseSeleccionada ?? null);

  const listaRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
  const reconocimientoRef = useRef<any>(null);
  const grabadorRef = useRef<MediaRecorder | null>(null);
  const flujoRef = useRef<MediaStream | null>(null);
  const audioBlobRef = useRef<Blob | null>(null);
  const usuarioDetuvoRef = useRef(false);
  const textoDictadoRef = useRef('');
  const origenVozRef = useRef(false);
  const silencioTimerRef = useRef<number | null>(null);
  const [autoEnviarVoz, setAutoEnviarVoz] = useState(true);
  const autoEnviarVozRef = useRef(true);

  useEffect(() => {
    let vigente = true, ocupado = false;
    cursorChat.current = 0; entradasChat.current.clear();
    reintento.current = null;
    setMensajes([{ id: 'bienvenida', emisor: 'ASISTENTE', texto: 'Chat compartido de este modelo. Soy un desarrollador más del equipo: pregúntame qué le falta al diagrama, cómo relacionar clases o cómo desarrollar un módulo (por ejemplo inventarios). Tengo en cuenta lo que dicen los demás integrantes y los acuerdos confirmados. También puedo proponer cambios, que revisas antes de aplicar.', hora: horaActual() }]);
    void leerCapacidadesAgente(modelo).then(c => { if (vigente) setModoProveedor(
      c.configurado && c.contextoRemoto ? `Contexto con ${c.proveedor} · ${c.modelo}` : 'Modo local · Memoria en PostgreSQL',
    ); }).catch(() => {});
    const sincronizar = async () => {
      if (ocupado || !vigente) return;
      ocupado = true;
      try {
        let pagina;
        do {
          pagina = await leerConversacion(modelo, cursorChat.current);
          if (!vigente) return;
          pagina.mensajes.forEach(m => entradasChat.current.set(m.id, m));
          cursorChat.current = pagina.cursor;
        } while (pagina.hayMas);
        setAcuerdos(pagina.acuerdos); setEjecuciones(pagina.ejecuciones);
        const anteriores = Array.from(entradasChat.current.values());
        const propuestas = new Map<string, PropuestaIa>();
        await Promise.all(anteriores.filter(m => m.idPropuesta).slice(-20).map(async m => {
          propuestas.set(m.idPropuesta!, await leerPropuesta(modelo, m.idPropuesta!));
        }));
        if (!vigente) return;
        setMensajes(prev => {
          const porId = new Map(prev.map(m => [m.id, m]));
          const nuevos = anteriores.map(m => {
            const p = m.idPropuesta ? propuestas.get(m.idPropuesta) : undefined;
            const anterior = porId.get(m.id);
            const origen = m.respuestaA ? entradasChat.current.get(m.respuestaA) : undefined;
            return {
              id: m.id, emisor: m.emisor, texto: m.texto, autor: m.nombreAutor,
              respuestaANombre: origen?.nombreAutor,
              hora: new Date(m.creadoEn).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
              metodoEntrada: m.modalidad, fuentes: m.fuentes, supuestos: m.supuestos,
              extraccion: m.extraccion,
              propuesta: p, estadoPropuesta: p?.estado === 'APLICADO' ? 'APLICADA' as const : p?.estado === 'RECHAZADO' ? 'RECHAZADA' as const : 'PENDIENTE' as const,
              operacionesSeleccionadas: anterior?.operacionesSeleccionadas ?? (p ? Object.fromEntries(p.operaciones.map(o => [o.id, o.seleccionada])) : undefined),
            };
          });
          // Conserva entradas antiguas de voz/imagen hasta que el servidor las incorpore.
          const externas = prev.filter(m => m.id !== 'bienvenida' && !entradasChat.current.has(m.id)
            && !m.id.startsWith('pendiente-') && (!m.propuesta || !anteriores.some(x => x.idPropuesta === m.propuesta!.id)));
          return nuevos.length ? [...nuevos, ...externas] : prev;
        });
        setErrorChat('');
      } catch (e) { if (vigente) setErrorChat((e as Error).message); }
      finally { ocupado = false; }
    };
    sincronizarChat.current = sincronizar;
    void sincronizar();
    const timer = window.setInterval(() => void sincronizar(), 2000);
    return () => { vigente = false; window.clearInterval(timer); };
  }, [modelo]);

  // Temporizador para voz en vivo
  useEffect(() => {
    if (!escuchandoVoz) return;
    const reloj = window.setInterval(() => {
      setSegundosVoz((s) => {
        if (s + 1 >= 120) {
          detenerVoz(true);
          return 120;
        }
        return s + 1;
      });
    }, 1000);
    return () => window.clearInterval(reloj);
  }, [escuchandoVoz]);

  // Limpieza al desmontar
  useEffect(() => {
    return () => {
      detenerVozLimpieza();
    };
  }, []);

  function limpiarTimerSilencio() {
    if (silencioTimerRef.current !== null) {
      window.clearTimeout(silencioTimerRef.current);
      silencioTimerRef.current = null;
    }
  }

  function detenerVozLimpieza() {
    usuarioDetuvoRef.current = true;
    limpiarTimerSilencio();
    try {
      reconocimientoRef.current?.abort();
    } catch { }
    try {
      if (grabadorRef.current?.state === 'recording') grabadorRef.current.stop();
    } catch { }
    if (flujoRef.current) {
      flujoRef.current.getTracks().forEach((t) => t.stop());
      flujoRef.current = null;
    }
  }

  function detenerVoz(mantenerTexto = true) {
    usuarioDetuvoRef.current = true;
    limpiarTimerSilencio();
    setEscuchandoVoz(false);
    try {
      reconocimientoRef.current?.stop();
    } catch { }
    try {
      if (grabadorRef.current?.state === 'recording') grabadorRef.current.stop();
    } catch { }
    if (flujoRef.current) {
      flujoRef.current.getTracks().forEach((t) => t.stop());
      flujoRef.current = null;
    }
    if (!mantenerTexto) {
      textoDictadoRef.current = '';
      origenVozRef.current = false;
      setPrompt('');
    }
  }

  async function iniciarVoz() {
    setErrorVoz('');
    limpiarTimerSilencio();
    usuarioDetuvoRef.current = false;
    textoDictadoRef.current = '';
    origenVozRef.current = true;
    setSegundosVoz(0);

    const constructorReconocimiento =
      typeof window !== 'undefined'
        ? (((window as unknown as Record<string, unknown>).SpeechRecognition ??
          (window as unknown as Record<string, unknown>).webkitSpeechRecognition) as
          | (new () => any)
          | undefined)
        : undefined;

    // Capturar stream de audio de respaldo si está disponible
    try {
      if (navigator.mediaDevices?.getUserMedia) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        flujoRef.current = stream;
        const fragmentos: Blob[] = [];
        const mime = MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '';
        const rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
        rec.ondataavailable = (e) => {
          if (e.data && e.data.size > 0) fragmentos.push(e.data);
        };
        rec.onstop = () => {
          audioBlobRef.current = new Blob(fragmentos, { type: rec.mimeType || 'audio/webm' });
        };
        grabadorRef.current = rec;
        rec.start(250);
      }
    } catch (err) {
      console.warn('MediaStream error:', err);
    }

    if (constructorReconocimiento) {
      try {
        const rec = new constructorReconocimiento();
        rec.lang = navigator.language?.startsWith('es') ? navigator.language : 'es-BO';
        rec.continuous = true;
        rec.interimResults = true;

        rec.onresult = (evento: any) => {
          let transcripcion = '';
          for (let i = 0; i < evento.results.length; i++) {
            transcripcion += evento.results[i][0].transcript;
          }
          const limpia = transcripcion.trim();
          if (limpia) {
            textoDictadoRef.current = limpia;
            origenVozRef.current = true;
            setPrompt(limpia);
            setErrorVoz('');
            limpiarTimerSilencio();
            if (autoEnviarVozRef.current) {
              silencioTimerRef.current = window.setTimeout(() => {
                const textoFinal = textoDictadoRef.current.trim();
                if (textoFinal && !usuarioDetuvoRef.current) {
                  detenerVoz(true);
                  void enviar(textoFinal, true);
                }
              }, 1750);
            }
          }
        };

        rec.onerror = (e: any) => {
          console.warn('SpeechRecognition error:', e);
          if (!textoDictadoRef.current) {
            setErrorVoz('No se pudo captar el audio con claridad. Puedes hablar de nuevo o escribir.');
          }
        };

        rec.onend = () => {
          if (!usuarioDetuvoRef.current) {
            const capturado = textoDictadoRef.current.trim();
            if (capturado && autoEnviarVozRef.current) {
              detenerVoz(true);
              void enviar(capturado, true);
              return;
            }
            try {
              rec.start();
              return;
            } catch { }
          }
          setEscuchandoVoz(false);
        };

        reconocimientoRef.current = rec;
        rec.start();
        setEscuchandoVoz(true);
      } catch (err) {
        console.warn('Fallo speech recognition:', err);
        setErrorVoz('Error al iniciar dictado. Puedes escribir directamente.');
        setEscuchandoVoz(false);
      }
    } else {
      setEscuchandoVoz(true);
    }
  }

  function regrabarVoz() {
    detenerVoz(false);
    setErrorVoz('');
    setTimeout(() => {
      void iniciarVoz();
    }, 120);
  }

  function alternarVoz() {
    if (escuchandoVoz) {
      const capturado = (textoDictadoRef.current || prompt).trim();
      detenerVoz(true);
      if (capturado) {
        void enviar(capturado, true);
      }
    } else {
      void iniciarVoz();
    }
  }

  // Sincronizar prompt inicial cuando se abre desde un error o diagnóstico
  useEffect(() => {
    if (promptInicial) {
      setPrompt(promptInicial);
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
    }
  }, [promptInicial]);

  // Sincronizar contexto cuando cambia la clase seleccionada en el lienzo
  useEffect(() => {
    if (claseSeleccionada) {
      setContextoClase(claseSeleccionada);
    }
  }, [claseSeleccionada]);

  // Scroll al final al agregar mensajes
  useEffect(() => {
    if (listaRef.current) {
      listaRef.current.scrollTop = listaRef.current.scrollHeight;
    }
  }, [mensajes, enviando]);

  // Si cambia la propuesta pendiente exterior
  useEffect(() => {
    if (propuestaPendiente) {
      setMensajes((prev) => {
        if (prev.some((m) => m.propuesta?.id === propuestaPendiente.id)) return prev;
        const selec: Record<string, boolean> = {};
        propuestaPendiente.operaciones.forEach((o) => {
          selec[o.id] = o.seleccionada;
        });
        return [
          ...prev,
          {
            id: 'pendiente-' + propuestaPendiente.id,
            emisor: 'ASISTENTE',
            texto: `Se cargó la propuesta pendiente:`,
            hora: horaActual(),
            propuesta: propuestaPendiente,
            estadoPropuesta: 'PENDIENTE',
            operacionesSeleccionadas: selec,
          },
        ];
      });
    }
  }, [propuestaPendiente]);

  // Generar sugerencias dinámicas basadas en el proyecto y las clases existentes
  // Preguntas de mentoría: no modifican el modelo; el agente responde con el contexto del equipo.
  function obtenerSugerenciasDinamicas(): string[] {
    const lista = clasesExistentes || [];
    const mentoria: string[] = [];
    if (contextoClase) {
      mentoria.push(`¿Qué le falta a la clase ${contextoClase}?`);
      mentoria.push(`¿Cómo se relaciona ${contextoClase} con el resto del modelo?`);
    } else if (lista.length === 0) {
      mentoria.push(`¿Cómo desarrollo el módulo principal de ${nombreProyecto ?? 'este sistema'}?`);
    } else {
      mentoria.push('¿Qué le falta a este diagrama para ser adecuado?');
      if (lista.length >= 2) {
        mentoria.push(`¿Cómo procedo con la relación entre ${lista[0].nombre} y ${lista[1].nombre}?`);
      }
    }
    return [...mentoria, ...sugerenciasDeCambio()].slice(0, 5);
  }

  function sugerenciasDeCambio(): string[] {
    const sugerencias: string[] = [];
    const lista = clasesExistentes || [];

    if (contextoClase) {
      sugerencias.push(`Añadir atributos fechaCreacion y estado a ${contextoClase}`);
      sugerencias.push(`Crear operación obtenerDetalle() en ${contextoClase}`);
      if (lista.length > 1) {
        const otra = lista.find((c) => c.nombre !== contextoClase);
        if (otra) sugerencias.push(`Relacionar ${contextoClase} con ${otra.nombre}`);
      }
      return sugerencias;
    }

    if (lista.length === 0) {
      if (nombreProyecto) {
        sugerencias.push(`Crear clase principal para ${nombreProyecto}`);
      }
      sugerencias.push('Crear clase Usuario con nombre, correo y estado');
      sugerencias.push('Crear clase Rol con nombre y descripcion');
    } else if (lista.length === 1) {
      const c = lista[0].nombre;
      sugerencias.push(`Añadir atributos fechaCreacion y estado a ${c}`);
      sugerencias.push(`Crear operaciones obtenerDetalle y actualizar en ${c}`);
      sugerencias.push(`Crear clase Detalle para relacionar con ${c}`);
    } else {
      const c1 = lista[0].nombre;
      const c2 = lista[1].nombre;
      sugerencias.push(`Relacionar ${c1} con ${c2}`);
      sugerencias.push(`Añadir atributos a ${c1}`);
      if (lista.length > 2) {
        const c3 = lista[2].nombre;
        sugerencias.push(`Relacionar ${c2} con ${c3}`);
      } else {
        sugerencias.push(`Crear clase Detalle para ${c1}`);
      }
    }
    return sugerencias.slice(0, 3);
  }

  // Resolver nombre de clase por su ID para visualización
  function resolverNombreClase(idClase: unknown, prop?: PropuestaIa): string {
    if (!idClase || typeof idClase !== 'string') return '';
    const existente = (clasesExistentes || []).find((c) => c.id === idClase);
    if (existente) return existente.nombre;
    if (prop) {
      const opClase = prop.operaciones.find(
        (o) => o.tipoEntidad === 'CLASE' && (o.idEntidadDestino === idClase || o.id === idClase),
      );
      if (opClase?.valorPropuesto && typeof opClase.valorPropuesto.nombre === 'string') {
        return opClase.valorPropuesto.nombre;
      }
    }
    return '';
  }

  async function enviar(textoAEnviar?: string, forzarModoVoz = false) {
    const estabaEscuchando = escuchandoVoz;
    if (escuchandoVoz) {
      detenerVoz(true);
    }
    const textoBase = (textoAEnviar ?? prompt).trim();
    if (!textoBase || enviando || disabled) return;

    // Enriquecer el prompt si hay una clase en contexto y no se especificó clase en el texto
    let texto = textoBase;
    if (
      textoBase &&
      contextoClase &&
      !textoBase.toLowerCase().includes('clase') &&
      (textoBase.toLowerCase().includes('atributo') ||
        textoBase.toLowerCase().includes('agrega') ||
        textoBase.toLowerCase().includes('añade') ||
        textoBase.toLowerCase().includes('añadí') ||
        textoBase.toLowerCase().includes('anade') ||
        textoBase.toLowerCase().includes('inserta') ||
        textoBase.toLowerCase().includes('crea') ||
        textoBase.toLowerCase().includes('operacion') ||
        textoBase.toLowerCase().includes('operación'))
    ) {
      texto = `${textoBase} a la clase ${contextoClase}`;
    }

    // Una pregunta sin nombrar la clase en foco se centra en ella: el agente prioriza sus relaciones.
    if (
      contextoClase &&
      texto === textoBase &&
      textoBase.includes('?') &&
      !textoBase.toLowerCase().includes(contextoClase.toLowerCase())
    ) {
      texto = `${textoBase} (sobre la clase ${contextoClase})`;
    }

    const fuePorVoz = Boolean(forzarModoVoz || origenVozRef.current || estabaEscuchando);
    origenVozRef.current = false;
    textoDictadoRef.current = '';
    setPrompt('');
    setVerIdeas(false);
    setEnviando(true);
    audioBlobRef.current = null;

    try {
      const entrada = reintento.current?.texto === texto ? reintento.current : { texto, clave: crypto.randomUUID() };
      reintento.current = entrada;
      if (fuePorVoz) {
        // Envía la transcripción bajo modalidad VOZ para que se procese con la velocidad de texto
        // pero conservando la trazabilidad y etiqueta de comando por voz.
        await enviarVozAgente(modelo, texto, entrada.clave, intencion);
      } else {
        await enviarMensajeAgente(modelo, texto, entrada.clave, intencion);
      }
      reintento.current = null;
      await sincronizarChat.current();
      return;
    } catch (err) {
      setPrompt(textoBase);
      setMensajes((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          emisor: 'ASISTENTE',
          texto: (err as Error).message || 'Ocurrió un error al consultar el asistente inteligente.',
          hora: horaActual(),
          error: true,
        },
      ]);
    } finally {
      setEnviando(false);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }

  function alternarSeleccionOperacion(idMensaje: string, idOperacion: string) {
    setMensajes((prev) =>
      prev.map((m) => {
        if (m.id !== idMensaje || !m.operacionesSeleccionadas) return m;
        return {
          ...m,
          operacionesSeleccionadas: {
            ...m.operacionesSeleccionadas,
            [idOperacion]: !m.operacionesSeleccionadas[idOperacion],
          },
        };
      }),
    );
  }

  function alternarTodasOperaciones(idMensaje: string, seleccionar: boolean) {
    setMensajes((prev) =>
      prev.map((m) => {
        if (m.id !== idMensaje || !m.propuesta) return m;
        const nuevo: Record<string, boolean> = {};
        m.propuesta.operaciones.forEach((o) => {
          nuevo[o.id] = seleccionar;
        });
        return {
          ...m,
          operacionesSeleccionadas: nuevo,
        };
      }),
    );
  }

  async function aplicar(idMensaje: string, prop: PropuestaIa, selec: Record<string, boolean>) {
    setProcesandoPropuesta(idMensaje);
    try {
      const operacionesParaRevisar = prop.operaciones.map((o) => ({
        id: o.id,
        seleccionada: selec[o.id] ?? o.seleccionada,
        valorPropuesto: o.valorPropuesto,
      }));

      await revisarPropuesta(modelo, prop, operacionesParaRevisar);
      const res = await aplicarPropuesta(modelo, prop.id);

      setMensajes((prev) =>
        prev.map((m) => (m.id === idMensaje ? { ...m, estadoPropuesta: 'APLICADA' } : m)),
      );

      // Notificar al editor para recargar modelo en el lienzo
      await alAplicarPropuesta();

      setMensajes((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          emisor: 'ASISTENTE',
          texto: `✓ Propuesta aplicada exitosamente. El modelo avanzó a la revisión ${res.revision ?? 'siguiente'}.`,
          hora: horaActual(),
        },
      ]);
    } catch (err) {
      setMensajes((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          emisor: 'ASISTENTE',
          texto: `Error al aplicar propuesta: ${(err as Error).message}`,
          hora: horaActual(),
          error: true,
        },
      ]);
    } finally {
      setProcesandoPropuesta(null);
    }
  }

  async function descartar(idMensaje: string, prop: PropuestaIa) {
    setProcesandoPropuesta(idMensaje);
    try {
      await rechazarPropuesta(modelo, prop.id, 'Descartada por el usuario desde el panel lateral');
      setMensajes((prev) =>
        prev.map((m) => (m.id === idMensaje ? { ...m, estadoPropuesta: 'RECHAZADA' } : m)),
      );
    } catch (err) {
      setMensajes((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          emisor: 'ASISTENTE',
          texto: `Error al descartar propuesta: ${(err as Error).message}`,
          hora: horaActual(),
          error: true,
        },
      ]);
    } finally {
      setProcesandoPropuesta(null);
    }
  }

  function limpiarChat() {
    void sincronizarChat.current();
  }

  function manejarKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void enviar();
    }
  }

  const sugerenciasDinamicas = obtenerSugerenciasDinamicas();

  return (
    <aside className="ia-agent-sidebar" aria-label="Asistente Inteligente de Modelado">
      {/* Cabecera del Agente */}
      <div className="ia-agent-header">
        <div className="ia-agent-title-row">
          <div className="ia-agent-avatar">✦</div>
          <div>
            <h2 className="ia-agent-title">Agente Asistente IA</h2>
            <div className="ia-agent-status">
              <span className="ia-agent-dot" />
              <span>Chat compartido · Revisión {revision}</span>
            </div>
            <small>{modoProveedor}</small>
          </div>
        </div>
        <div className="ia-agent-header-actions">
          <button
            type="button"
            className="ia-agent-icon-btn"
            title="Actualizar historial compartido"
            onClick={limpiarChat}
            disabled={enviando || !!procesandoPropuesta}
          >
            🧹
          </button>
          <button
            type="button"
            className="ia-agent-icon-btn"
            title="Cerrar panel de asistente"
            onClick={cerrar}
          >
            ×
          </button>
        </div>
      </div>

      {/* Lista de Mensajes del Chat */}
      <div className="ia-agent-messages" ref={listaRef}>
        {errorChat && <p role="alert">No se pudo sincronizar el chat: {errorChat}</p>}
        {acuerdos.length > 0 && <details className="ia-chat-bubble asistente">
          <summary>Acuerdos del equipo ({acuerdos.filter(a => a.vigente).length} vigentes)</summary>
          {acuerdos.map(a => <p key={a.id} style={{ opacity: a.vigente ? 1 : 0.55 }}>
            {a.contenido} — {a.nombreAutor}{!a.vigente && ' (sustituido)'}
            {a.vigente && !disabled && <button type="button" onClick={async () => {
              const contenido = window.prompt('Nuevo acuerdo que sustituye al anterior:', a.contenido);
              if (!contenido?.trim()) return;
              try { setAcuerdos(await confirmarAcuerdo(modelo, a.idMensaje, contenido, a.id)); }
              catch (e) { setErrorChat((e as Error).message); }
            }}>Sustituir</button>}
          </p>)}
        </details>}
        {ejecuciones.filter(e => e.estado === 'PENDIENTE' || e.estado === 'PROCESANDO').map(e => {
          const autorEj = e.nombreAutor || 'Integrante';
          const modEj = e.modalidad === 'VOZ' ? '🎙️ Voz' : e.modalidad === 'IMAGEN' ? '🖼️ Imagen' : '💬 Texto';
          const textoEj = e.textoMensaje ? (e.textoMensaje.length > 60 ? e.textoMensaje.slice(0, 60) + '…' : e.textoMensaje) : '';
          return (
            <p key={e.id} role="status">
              {e.estado === 'PENDIENTE' ? 'Solicitud en cola' : 'Preparando respuesta…'} · <b>{autorEj}</b> ({modEj}){textoEj ? `: «${textoEj}»` : ''}{' '}
              <button type="button" disabled={disabled} onClick={async () => {
                try { await cancelarEjecucion(modelo, e.id); await sincronizarChat.current(); }
                catch (err) { setErrorChat((err as Error).message); }
              }}>Cancelar</button>
            </p>
          );
        })}
        {ejecuciones.filter(e => e.estado === 'FALLIDO').map(e => (
          <p key={e.id} role="alert">
            {e.nombreAutor ? `${e.nombreAutor}: ` : ''}{e.error}
          </p>
        ))}
        {mensajes.map((m) => (
          <div key={m.id} className={`ia-chat-bubble-wrapper ${m.emisor.toLowerCase()}`}>
            <div className="ia-chat-meta">
              <span className="ia-chat-author">
                {m.autor ?? (m.emisor === 'ASISTENTE' ? '✦ Agente IA' : 'Tú')}
                {m.emisor === 'ASISTENTE' && m.respuestaANombre && (
                  <small style={{ marginLeft: '6px', opacity: 0.78, fontWeight: 400 }}>
                    ↪ para {m.respuestaANombre}
                  </small>
                )}
              </span>
              {m.metodoEntrada === 'VOZ' && (
                <span className="ia-chat-voice-chip" title="Instrucción o consulta por voz">
                  🎙️ Voz
                </span>
              )}
              {m.metodoEntrada === 'IMAGEN' && (
                <span className="ia-chat-voice-chip" title="Instrucción o consulta desde imagen">
                  🖼️ Imagen
                </span>
              )}
              <span className="ia-chat-time">{m.hora}</span>
            </div>

            <div
              className={`ia-chat-bubble ${m.emisor.toLowerCase()}${m.error ? ' ia-chat-error' : ''}`}
            >
              <p className="ia-chat-text">{m.texto}</p>
              {m.extraccion && <details><summary>{m.metodoEntrada === 'VOZ' ? 'Transcripción utilizada' : 'Lectura de la imagen (revisar inferencias)'}</summary><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{m.extraccion}</pre></details>}
              {!!m.supuestos?.length && <p>Supuestos: {m.supuestos.join('; ')}</p>}
              {!!m.fuentes?.length && <details><summary>Fuentes del contexto ({m.fuentes.length})</summary>
                {m.fuentes.map(f => <p key={f}>{acuerdos.find(a => a.id === f)?.contenido ?? mensajes.find(x => x.id === f)?.texto ?? f}</p>)}
              </details>}
              {m.emisor === 'USUARIO' && entradasChat.current.has(m.id) && !disabled &&
                <button type="button" onClick={async () => {
                  const contenido = window.prompt('Confirmar como acuerdo del equipo (puedes corregir el texto):', m.texto);
                  if (!contenido?.trim()) return;
                  try { setAcuerdos(await confirmarAcuerdo(modelo, m.id, contenido)); }
                  catch (e) { setErrorChat((e as Error).message); }
                }}>Confirmar acuerdo</button>}

              {m.audioUrl && (
                <div className="ia-chat-audio-wrapper">
                  <audio className="ia-chat-audio-player" controls src={m.audioUrl} preload="metadata" />
                </div>
              )}

              {/* Tarjeta de bienvenida con sugerencias dinámicas contextuales si es el mensaje inicial */}
              {m.id === 'bienvenida' && mensajes.length === 1 && sugerenciasDinamicas.length > 0 && (
                <div className="ia-welcome-suggestions">
                  <span className="ia-suggestions-label">💡 Ideas para este modelo:</span>
                  <div className="ia-suggestions-chips">
                    {sugerenciasDinamicas.map((sug) => (
                      <button
                        key={sug}
                        type="button"
                        className="ia-suggestion-chip"
                        disabled={enviando || disabled}
                        onClick={() => void enviar(sug)}
                      >
                        ✦ {sug}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Tarjeta de cambios detectados / propuesta embebida */}
              {m.propuesta && (
                <div className="ia-inline-proposal-card">
                  {/* Cabecera de la propuesta */}
                  <div className="ia-inline-proposal-header">
                    <div className="ia-proposal-headline">
                      <div className="ia-proposal-title-row">
                        <span className="ia-proposal-title">
                          Cambios detectados ({m.propuesta.operaciones.length})
                        </span>
                        <span className="ia-inline-confidence-badge">
                          Requiere revisión
                        </span>
                      </div>
                      <p className="ia-inline-proposal-summary">{m.propuesta.resumen}</p>
                    </div>

                    {m.estadoPropuesta === 'PENDIENTE' && (
                      <div className="ia-proposal-toggle-all">
                        <button
                          type="button"
                          className="ia-text-action-btn"
                          disabled={disabled || !!procesandoPropuesta}
                          onClick={() => {
                            const todasSeleccionadas = m.propuesta!.operaciones.every(
                              (o) => m.operacionesSeleccionadas?.[o.id] ?? o.seleccionada,
                            );
                            alternarTodasOperaciones(m.id, !todasSeleccionadas);
                          }}
                        >
                          {m.propuesta.operaciones.every(
                            (o) => m.operacionesSeleccionadas?.[o.id] ?? o.seleccionada,
                          )
                            ? 'Deseleccionar todas'
                            : 'Seleccionar todas'}
                        </button>
                      </div>
                    )}
                  </div>

                  {/* Lista de operaciones rediseñada y visualmente limpia */}
                  <div className="ia-inline-operations-list">
                    {m.propuesta.operaciones.map((op) => {
                      const seleccionada =
                        m.operacionesSeleccionadas?.[op.id] ?? op.seleccionada;
                      const esPendiente = m.estadoPropuesta === 'PENDIENTE';
                      const val = op.valorPropuesto ?? op.valorActual ?? {};
                      const nombreEntidad = typeof val.nombre === 'string' ? val.nombre : '';
                      const tipoUml = typeof val.tipoUml === 'string' ? val.tipoUml : '';
                      const nombrePadre = resolverNombreClase(val.idClase, m.propuesta ?? undefined);

                      return (
                        <div
                          key={op.id}
                          className={`ia-op-card ${op.tipoOperacion.toLowerCase()} ${!seleccionada ? 'deselected' : ''} ${op.estado === 'INVALIDO' ? 'invalid' : ''}`}
                        >
                          <div className="ia-op-card-top">
                            <label className="ia-op-checkbox-label">
                              <input
                                type="checkbox"
                                checked={seleccionada}
                                disabled={!esPendiente || disabled || !!procesandoPropuesta}
                                onChange={() => alternarSeleccionOperacion(m.id, op.id)}
                              />
                              <span className={`ia-op-badge ${op.tipoOperacion.toLowerCase()}`}>
                                {op.tipoOperacion === 'CREAR'
                                  ? '＋ CREAR'
                                  : op.tipoOperacion === 'ACTUALIZAR'
                                    ? '✎ MODIFICAR'
                                    : '✕ ELIMINAR'}
                              </span>
                              <span className="ia-entity-badge">
                                {ETIQUETAS_ENTIDAD[op.tipoEntidad] ?? op.tipoEntidad}
                              </span>
                            </label>
                            <span className="ia-op-confidence">
                              Heurística
                            </span>
                          </div>

                          <div className="ia-op-card-content">
                            {/* Información específica del objetivo */}
                            <div className="ia-op-target-info">
                              {op.tipoEntidad === 'CLASE' && (
                                <div className="ia-op-highlight">
                                  <span className="ia-op-symbol">◇</span>
                                  <b className="ia-op-name">{nombreEntidad}</b>
                                </div>
                              )}
                              {op.tipoEntidad === 'ATRIBUTO' && (
                                <div className="ia-op-highlight">
                                  <span className="ia-op-symbol">•</span>
                                  <b className="ia-op-name">{nombreEntidad}</b>
                                  {tipoUml && <span className="ia-type-tag">{tipoUml}</span>}
                                  {nombrePadre && (
                                    <span className="ia-op-parent-ref">
                                      en <i>{nombrePadre}</i>
                                    </span>
                                  )}
                                </div>
                              )}
                              {op.tipoEntidad === 'RELACION' && (
                                <div className="ia-op-highlight">
                                  <span className="ia-op-symbol">↔</span>
                                  <b className="ia-op-name">
                                    {String(val.origen || 'Origen')} ➔ {String(val.destino || 'Destino')}
                                  </b>
                                  <span className="ia-type-tag">
                                    {String(val.tipo || 'ASOCIACION')}
                                  </span>
                                </div>
                              )}
                              {op.tipoEntidad === 'OPERACION' && (
                                <div className="ia-op-highlight">
                                  <span className="ia-op-symbol">ƒ</span>
                                  <b className="ia-op-name">{nombreEntidad}()</b>
                                  <span className="ia-type-tag">
                                    {String(val.tipoRetorno || 'void')}
                                  </span>
                                </div>
                              )}
                              {op.tipoEntidad === 'PAQUETE' && (
                                <div className="ia-op-highlight">
                                  <span className="ia-op-symbol">▱</span>
                                  <b className="ia-op-name">{nombreEntidad}</b>
                                </div>
                              )}
                            </div>

                            {/* Justificación clara sin summary toggle */}
                            {op.justificacion && (
                              <p className="ia-op-justification">{op.justificacion}</p>
                            )}

                            {/* Advertencia si no es válida */}
                            {op.estado === 'INVALIDO' && (
                              <div className="ia-op-warning-callout">
                                ⚠ Advertencia: Contiene advertencias o dependencias no resueltas.
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  {/* Acciones de la propuesta */}
                  <div className="ia-inline-proposal-actions">
                    {m.estadoPropuesta === 'PENDIENTE' ? (
                      <>
                        <button
                          type="button"
                          className="ia-inline-btn-reject"
                          disabled={disabled || procesandoPropuesta === m.id}
                          onClick={() => descartar(m.id, m.propuesta!)}
                        >
                          Descartar propuesta
                        </button>
                        <button
                          type="button"
                          className="ia-inline-btn-apply primary"
                          disabled={
                            disabled ||
                            procesandoPropuesta === m.id ||
                            !m.propuesta.operaciones.some(
                              (o) => m.operacionesSeleccionadas?.[o.id] ?? o.seleccionada,
                            )
                          }
                          onClick={() =>
                            aplicar(
                              m.id,
                              m.propuesta!,
                              m.operacionesSeleccionadas ?? {},
                            )
                          }
                        >
                          {procesandoPropuesta === m.id
                            ? 'Aplicando…'
                            : `✓ Aplicar cambios (${m.propuesta.operaciones.filter(
                              (o) => m.operacionesSeleccionadas?.[o.id] ?? o.seleccionada,
                            ).length
                            })`}
                        </button>
                      </>
                    ) : m.estadoPropuesta === 'APLICADA' ? (
                      <span className="ia-inline-state-badge applied">
                        ✓ Propuesta aplicada al modelo
                      </span>
                    ) : (
                      <span className="ia-inline-state-badge rejected">
                        ✕ Propuesta descartada
                      </span>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        ))}

        {/* Indicador de pensando / generando */}
        {enviando && (
          <div className="ia-chat-bubble-wrapper asistente">
            <div className="ia-chat-meta">
              <span className="ia-chat-author">✦ Agente IA</span>
              <span className="ia-chat-time">{horaActual()}</span>
            </div>
            <div className="ia-chat-bubble asistente ia-typing-bubble">
              <span className="ia-typing-dot" />
              <span className="ia-typing-dot" />
              <span className="ia-typing-dot" />
              <span className="ia-typing-text">Interpretando instrucción…</span>
            </div>
          </div>
        )}
      </div>

      {/* Menú flotante de ideas desplegable (opcional bajo demanda, no ocupa espacio fijo) */}
      {verIdeas && (
        <div className="ia-ideas-popover">
          <div className="ia-ideas-popover-header">
            <span className="ia-ideas-title">💡 Sugerencias contextuales</span>
            <button
              type="button"
              className="ia-ideas-close"
              onClick={() => setVerIdeas(false)}
            >
              ×
            </button>
          </div>
          <div className="ia-ideas-list">
            {sugerenciasDinamicas.map((sug) => (
              <button
                key={sug}
                type="button"
                className="ia-idea-item"
                onClick={() => {
                  setVerIdeas(false);
                  void enviar(sug);
                }}
              >
                <span>✦</span> {sug}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Chip de clase activa seleccionada */}
      {contextoClase && (
        <div className="ia-context-chip">
          <span className="ia-context-label">
            🎯 Clase en foco: <b>{contextoClase}</b>
          </span>
          <button
            type="button"
            className="ia-context-clear"
            onClick={() => setContextoClase(null)}
            title="Quitar clase en foco"
          >
            ×
          </button>
        </div>
      )}

      {/* Formulario de Entrada del Chatbot: siempre presente sin superponerse */}
      <form
        className="ia-agent-input-form"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          void enviar();
        }}
      >
        {/* Barra compacta de dictado cuando la voz está activa */}
        {escuchandoVoz && (
          <div className="ia-voice-compact-bar">
            <div className="ia-voice-compact-status">
              <span className="ia-voice-pulse-dot live" />
              <span className="ia-voice-compact-text">
                Escuchando ({Math.floor(segundosVoz / 60)}:{(segundosVoz % 60).toString().padStart(2, '0')}) ·{' '}
                {prompt.trim()
                  ? `🗣️ «${prompt.trim()}» (se enviará al terminar de hablar…)`
                  : 'Habla ahora; al terminar se enviará automáticamente'}
              </span>
            </div>
            <div className="ia-voice-compact-actions">
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={autoEnviarVoz}
                  onChange={(e) => {
                    setAutoEnviarVoz(e.target.checked);
                    autoEnviarVozRef.current = e.target.checked;
                    if (!e.target.checked) limpiarTimerSilencio();
                  }}
                />
                Auto-enviar
              </label>
              <button
                type="button"
                className="ia-voice-compact-btn stop"
                onClick={() => detenerVoz(true)}
                title="Pausar escucha y editar el texto antes de enviar"
              >
                ⏸ Editar
              </button>
              <button
                type="button"
                className="ia-voice-compact-btn retry"
                onClick={regrabarVoz}
                title="Limpiar y regrabar desde cero"
              >
                ↺ Regrabar
              </button>
              <button
                type="button"
                className="ia-voice-compact-btn cancel"
                onClick={() => detenerVoz(false)}
                title="Cancelar dictado y borrar texto"
              >
                ✕
              </button>
            </div>
          </div>
        )}

        {/* Mensaje de error no invasivo con botón para regrabar */}
        {errorVoz && (
          <div className="ia-voice-mini-error">
            <span>⚠ {errorVoz}</span>
            <button type="button" onClick={regrabarVoz}>
              Reintentar
            </button>
          </div>
        )}

        {/* Textarea nativo donde se escribe el texto en tiempo real */}
        <label>Intención <select aria-label="Intención del mensaje" value={intencion} onChange={e => setIntencion(e.target.value)} disabled={enviando || disabled}>
          <option value="AUTO">Automática</option><option value="CONSULTAR">Consultar sin cambios</option><option value="MODIFICAR">Proponer cambios</option>
        </select></label>
        <textarea
          ref={inputRef}
          aria-label="Mensaje al asistente"
          className={`ia-agent-textarea ${escuchandoVoz ? 'listening' : ''}`}
          rows={2}
          value={prompt}
          disabled={enviando || disabled}
          placeholder={
            escuchandoVoz
              ? 'Escuchando tu voz... Lo que hables se escribirá aquí en tiempo real.'
              : contextoClase
                ? `Pregunta o pide un cambio sobre ${contextoClase}...`
                : 'Pregunta (¿qué le falta a este diagrama?) o pide un cambio (agrega la clase Cliente con nombre y correo)...'
          }
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={manejarKeyDown}
        />

        <div className="ia-agent-input-tools">
          <div className="ia-agent-secondary-tools">
            <button
              type="button"
              className={`ia-tool-btn ${verIdeas ? 'active' : ''}`}
              title="Ver ideas de prompts contextuales"
              onClick={() => setVerIdeas((v) => !v)}
              disabled={disabled}
            >
              💡 Ideas
            </button>

            <button
              type="button"
              className={`ia-tool-btn ia-tool-btn-voice ${escuchandoVoz ? 'live' : ''}`}
              title={escuchandoVoz ? 'Detener dictado de voz' : 'Hablar o dictar instrucción por voz'}
              onClick={alternarVoz}
              disabled={disabled || enviando}
            >
              {escuchandoVoz ? '🔴 Escuchando…' : '🎙 Voz'}
            </button>

            <button
              type="button"
              className="ia-tool-btn"
              title="Imagen"
              onClick={abrirImagen}
              disabled={disabled}
            >
              📷 Imagen
            </button>
          </div>

          <button
            type="submit"
            className="ia-agent-send-btn primary"
            disabled={!prompt.trim() || enviando || disabled}
          >
            {enviando ? '…' : 'Enviar ➤'}
          </button>
        </div>
      </form>
    </aside>
  );
}
