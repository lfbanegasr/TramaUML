import { useCallback, useEffect, useRef, useState } from 'react';
import {
  aplicarCambio,
  leerModelo,
  type Modelo,
  type Accion,
  type Cambio,
} from '../api/modeladoApi';
import {
  abrirCanal,
  abrirSesion,
  cerrarSesion,
  type EventoColaborativo,
  type SesionColaborativa,
} from '../../colaboracion/api/colaboracionApi';
import { ApiError } from '../../../shared/api';

export function useModelo(id: string) {
  const [modelo, setModelo] = useState<Modelo | null>(null),
    [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false),
    [pendiente, setPendiente] = useState<Cambio | null>(null);
  const [conflicto, setConflicto] = useState(false),
    [mensaje, setMensaje] = useState('');
  const [desactualizado, setDesactualizado] = useState(false);
  const [cursores, setCursores] = useState<
    Record<string, { x: number; y: number; accion: string; instante: number }>
  >({});
  const ultimoCursor = useRef(0);
  const enviarCursor = useCallback(
    (punto: { x: number; y: number } | null, accion = 'EXPLORANDO') => {
      const canal = socket.current;
      if (!canal || canal.readyState !== WebSocket.OPEN) return;
      if (punto && Date.now() - ultimoCursor.current < 80) return;
      ultimoCursor.current = Date.now();
      canal.send(
        JSON.stringify({ tipo: 'CURSOR', x: punto?.x ?? null, y: punto?.y ?? null, accion }),
      );
    },
    [],
  );
  useEffect(() => {
    const timer = window.setInterval(
      () =>
        setCursores((actual) =>
          Object.fromEntries(
            Object.entries(actual).filter(([, c]) => Date.now() - c.instante < 10000),
          ),
        ),
      2000,
    );
    return () => window.clearInterval(timer);
  }, []);
  const [senalConflictos, setSenalConflictos] = useState(0);
  const [sesion, setSesion] = useState<SesionColaborativa | null>(null);
  const [colaboracion, setColaboracion] = useState<'CONECTANDO' | 'CONECTADO' | 'LIMITADO'>(
    'CONECTANDO',
  );
  const ocupado = useRef(false),
    socket = useRef<WebSocket | null>(null),
    sesionRef = useRef<SesionColaborativa | null>(null),
    modeloRef = useRef<Modelo | null>(null),
    pendienteRef = useRef<Cambio | null>(null),
    enviandoRef = useRef(false),
    borradorRef = useRef(false),
    ultimoConfirmadoLocal = useRef<string | null>(null),
    cargaRemota = useRef(false),
    solicitudRef = useRef(crypto.randomUUID()),
    salirRef = useRef<() => Promise<void>>(async () => {}),
    pausarRef = useRef<() => Promise<boolean>>(async () => true),
    reanudarRef = useRef<() => Promise<boolean>>(async () => true),
    enfocarRef = useRef<(tipo: string | null, entidad: string | null) => void>(() => {});

  useEffect(() => {
    modeloRef.current = modelo;
  }, [modelo]);
  useEffect(() => {
    pendienteRef.current = pendiente;
  }, [pendiente]);
  useEffect(() => {
    enviandoRef.current = enviando;
  }, [enviando]);
  useEffect(() => {
    sesionRef.current = sesion;
  }, [sesion]);
  const registrarBorrador = useCallback((activo: boolean) => {
    borradorRef.current = activo;
  }, []);

  useEffect(() => {
    let vivo = true,
      reintentos = 0,
      temporizador: number | undefined,
      pulso: number | undefined;
    const solicitud = solicitudRef.current;

    async function cambioRemoto(revision: number) {
      if (revision <= (modeloRef.current?.revision ?? -1)) return;
      if (
        borradorRef.current ||
        pendienteRef.current ||
        enviandoRef.current ||
        cargaRemota.current
      ) {
        setDesactualizado(true);
        setConflicto(true);
        setError(
          'El modelo cambió mientras editabas. Tu borrador se conserva para que lo revises.',
        );
        setMensaje('Hay una revisión remota. Conservamos tu cambio local para que lo revises.');
        return;
      }
      cargaRemota.current = true;
      try {
        const actual = await leerModelo(id);
        if (!vivo) return;
        modeloRef.current = actual;
        setModelo(actual);
        setDesactualizado(false);
        setMensaje('Modelo sincronizado · revisión ' + actual.revision);
      } catch {
        if (vivo) {
          setDesactualizado(true);
          setError('Hay un cambio confirmado. Recarga para recuperar la revisión reciente.');
        }
      } finally {
        cargaRemota.current = false;
      }
    }

    function conectarCanal(actual: SesionColaborativa) {
      if (!vivo) return;
      setCursores({});
      setColaboracion('CONECTANDO');
      const canal = abrirCanal(id, actual.id);
      socket.current = canal;
      canal.onmessage = (e) => {
        const evento = JSON.parse(e.data) as EventoColaborativo;
        if (evento.tipo === 'CONECTADO') {
          reintentos = 0;
          setColaboracion('CONECTADO');
          setError('');
          window.clearInterval(pulso);
          pulso = window.setInterval(() => {
            if (canal.readyState === WebSocket.OPEN) canal.send(JSON.stringify({ tipo: 'PING' }));
          }, 30000);
        } else if (evento.tipo === 'CURSOR' && evento.datos.actor) {
          const { actor, x, y, accion } = evento.datos;
          setCursores((actual) => {
            const siguiente = { ...actual };
            if (typeof x === 'number' && typeof y === 'number')
              siguiente[actor] = { x, y, accion: accion ?? 'EXPLORANDO', instante: Date.now() };
            else delete siguiente[actor];
            return siguiente;
          });
        } else if (evento.tipo === 'PRESENCIA' && typeof evento.datos.sesion === 'object') {
          const recibida = evento.datos.sesion;
          sesionRef.current = recibida;
          setSesion(recibida);
        } else if (evento.tipo === 'MODELO_CONFIRMADO' && evento.datos.revision !== undefined) {
          if (
            evento.datos.cambio &&
            (pendienteRef.current?.idCambioCliente === evento.datos.cambio ||
              ultimoConfirmadoLocal.current === evento.datos.cambio)
          ) {
            if (ultimoConfirmadoLocal.current === evento.datos.cambio)
              ultimoConfirmadoLocal.current = null;
            return;
          }
          void cambioRemoto(evento.datos.revision);
        } else if (evento.tipo === 'CONFLICTO_ACTUALIZADO') {
          setSenalConflictos((valor) => valor + 1);
          setMensaje(
            evento.datos.estado === 'ABIERTO'
              ? 'Hay una propuesta en conflicto pendiente de revisión.'
              : 'Se actualizó la resolución de un conflicto.',
          );
        } else if (evento.tipo === 'ERROR') {
          setError(evento.datos.mensaje ?? 'El canal colaborativo rechazó la operación.');
        }
      };
      canal.onclose = () => {
        window.clearInterval(pulso);
        if (!vivo || pausado) return;
        setCursores({});
        setColaboracion('LIMITADO');
        setError(
          'Colaboración interrumpida. El modelo queda sin nuevas ediciones mientras reconectamos.',
        );
        if (reintentos < 5) {
          reintentos += 1;
          temporizador = window.setTimeout(
            () => conectarCanal(sesionRef.current ?? actual),
            Math.min(1000 * 2 ** (reintentos - 1), 10000),
          );
        }
      };
      canal.onerror = () => canal.close();
      enfocarRef.current = (tipo, entidad) => {
        if (canal.readyState === WebSocket.OPEN)
          canal.send(JSON.stringify({ tipo: 'FOCO', tipoEntidad: tipo, idEntidad: entidad }));
      };
    }

    let pausado = false;

    async function iniciar() {
      try {
        const abierta = await abrirSesion(id, solicitud);
        if (!vivo || pausado) return;
        sesionRef.current = abierta;
        setSesion(abierta);
        const actual = await leerModelo(id);
        if (!vivo || pausado) return;
        modeloRef.current = actual;
        setModelo(actual);
        conectarCanal(abierta);
      } catch (e) {
        if (vivo && !pausado) {
          setCursores({});
          setColaboracion('LIMITADO');
          setError((e as Error).message);
        }
      }
    }

    pausarRef.current = async () => {
      if (borradorRef.current || pendienteRef.current || enviandoRef.current) {
        return false;
      }
      pausado = true;
      window.clearTimeout(temporizador);
      window.clearInterval(pulso);
      if (socket.current) {
        socket.current.onclose = null;
        socket.current.onerror = null;
        socket.current.close(1000, 'Pausa para restauración');
        socket.current = null;
      }
      const actual = sesionRef.current;
      if (actual && actual.estado === 'ACTIVO') {
        try {
          await cerrarSesion(id, actual.id);
        } catch (e) {
          if (!(e instanceof ApiError) || ![401, 403, 404, 409].includes(e.status)) {
            console.warn('Error al cerrar sesión para restauración:', e);
          }
        }
      }
      sesionRef.current = null;
      setSesion(null);
      setColaboracion('LIMITADO');
      return true;
    };

    reanudarRef.current = async () => {
      if (!vivo) return false;
      pausado = false;
      try {
        solicitudRef.current = crypto.randomUUID();
        const abierta = await abrirSesion(id, solicitudRef.current);
        if (pausado || !vivo) return false;
        sesionRef.current = abierta;
        setSesion(abierta);
        conectarCanal(abierta);
        const actual = await leerModelo(id);
        if (!pausado && vivo) {
          modeloRef.current = actual;
          setModelo(actual);
        }
        return true;
      } catch (e) {
        if (!pausado && vivo) {
          setCursores({});
          setColaboracion('LIMITADO');
          setError((e as Error).message);
        }
        return false;
      }
    };

    salirRef.current = async () => {
      const actual = sesionRef.current;
      if (!actual || actual.estado !== 'ACTIVO') return;
      try {
        await cerrarSesion(id, actual.id);
      } catch (e) {
        if (!(e instanceof ApiError) || ![401, 403, 404, 409].includes(e.status)) throw e;
      } finally {
        vivo = false;
        window.clearTimeout(temporizador);
        window.clearInterval(pulso);
        socket.current?.close(1000, 'Salida explícita');
        socket.current = null;
        sesionRef.current = null;
      }
    };
    void iniciar();
    return () => {
      vivo = false;
      window.clearTimeout(temporizador);
      window.clearInterval(pulso);
      socket.current?.close(1000, 'Vista cerrada');
      socket.current = null;
    };
  }, [id]);

  async function recargar() {
    // Una recarga explícita es la vía de recuperación del conflicto. En ese estado
    // puede liberar un bloqueo defensivo dejado por la solicitud que ya terminó.
    if (ocupado.current && !conflicto) return false;
    ocupado.current = true;
    enviandoRef.current = true;
    setEnviando(true);
    try {
      const actual = await leerModelo(id);
      modeloRef.current = actual;
      setModelo(actual);
      setError('');
      setConflicto(false);
      setPendiente(null);
      setDesactualizado(false);
      setMensaje('Modelo actualizado · revisión ' + actual.revision);
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    } finally {
      ocupado.current = false;
      enviandoRef.current = false;
      setEnviando(false);
    }
  }

  async function enviar(cambio: Cambio) {
    if (ocupado.current || colaboracion !== 'CONECTADO') return false;
    ocupado.current = true;
    setEnviando(true);
    setError('');
    setMensaje('');
    setPendiente(cambio);
    try {
      const c = await aplicarCambio(id, cambio);
      ultimoConfirmadoLocal.current = c.idCambioCliente;
      pendienteRef.current = null;
      setPendiente(null);
      setConflicto(false);
      setMensaje('Guardado · revisión ' + c.revision);
      try {
        const actual = await leerModelo(id);
        modeloRef.current = actual;
        setModelo(actual);
        setDesactualizado(false);
      } catch {
        setDesactualizado(true);
        setError('El cambio se confirmó. Recarga para consultar el modelo actualizado.');
      }
      return true;
    } catch (e) {
      setError((e as Error).message);
      setConflicto(e instanceof ApiError && e.status === 409);
      // Un rechazo conocido permite corregir; una respuesta perdida conserva ambos UUID.
      if (e instanceof ApiError && [400, 401, 403, 404, 413, 422].includes(e.status)) {
        pendienteRef.current = null;
        setPendiente(null);
      }
      return false;
    } finally {
      ocupado.current = false;
      enviandoRef.current = false;
      setEnviando(false);
    }
  }

  async function cambiar(accion: Accion) {
    const sesionActual = sesionRef.current;
    if (
      !modelo ||
      !sesionActual ||
      sesionActual.modoPropio !== 'EDITOR' ||
      colaboracion !== 'CONECTADO' ||
      ocupado.current ||
      pendiente ||
      conflicto ||
      desactualizado
    )
      return false;
    return enviar({
      idSesionEdicion: sesionActual.id,
      idCambioCliente: crypto.randomUUID(),
      revisionBase: modelo.revision,
      acciones: [accion],
    });
  }

  async function cambiarLote(acciones: Accion[]) {
    const sesionActual = sesionRef.current;
    if (
      !modelo ||
      !sesionActual ||
      sesionActual.modoPropio !== 'EDITOR' ||
      colaboracion !== 'CONECTADO' ||
      ocupado.current ||
      pendiente ||
      conflicto ||
      desactualizado ||
      acciones.length === 0
    )
      return false;
    return enviar({
      idSesionEdicion: sesionActual.id,
      idCambioCliente: crypto.randomUUID(),
      revisionBase: modelo.revision,
      acciones,
    });
  }

  return {
    modelo,
    cursores,
    enviarCursor,
    error,
    mensaje,
    enviando,
    pendiente,
    conflicto,
    desactualizado,
    sesion,
    colaboracion,
    recargar,
    cambiar,
    cambiarLote,
    reintentar: () => pendiente && enviar(pendiente),
    salir: () => salirRef.current(),
    pausarSesion: () => pausarRef.current(),
    reanudarSesion: () => reanudarRef.current(),
    enfocar: (tipo: string | null, entidad: string | null) => enfocarRef.current(tipo, entidad),
    registrarBorrador,
    senalConflictos,
  };
}
