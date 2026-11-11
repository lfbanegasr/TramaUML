import { api } from '../../../shared/api';

export type MensajeAgente = {
  id: string; secuencia: number; autor: string | null; nombreAutor: string;
  emisor: 'USUARIO' | 'ASISTENTE'; texto: string; extraccion?: string | null; modalidad: 'TEXTO' | 'VOZ' | 'IMAGEN';
  respuestaA?: string | null;
  idPropuesta: string | null; tipoResultado: string | null; fuentes: string[];
  supuestos: string[]; creadoEn: string;
};
export type EjecucionAgente = {
  id: string;
  idMensaje: string;
  autor?: string | null;
  nombreAutor?: string;
  textoMensaje?: string;
  modalidad?: 'TEXTO' | 'VOZ' | 'IMAGEN';
  intencion?: string;
  estado: string;
  error: string | null;
  modo: string | null;
};
export type AcuerdoAgente = { id: string; idMensaje: string; contenido: string; nombreAutor: string; vigente: boolean };
export type HistorialAgente = { idConversacion: string; mensajes: MensajeAgente[]; cursor: number;
  hayMas: boolean; ejecuciones: EjecucionAgente[]; acuerdos: AcuerdoAgente[] };
export const leerConversacion = (modelo: string, cursor = 0) =>
  api<HistorialAgente>(`/modelos/${modelo}/ia/conversacion?cursor=${cursor}`);
export const leerCapacidadesAgente = (modelo: string) =>
  api<{ proveedor: string; modelo: string; configurado: boolean; contextoRemoto: boolean }>(`/modelos/${modelo}/ia/conversacion/capacidades`);
export const enviarMensajeAgente = (modelo: string, texto: string, clave: string, intencion = 'AUTO') =>
  api<{ mensaje: MensajeAgente; ejecucion: EjecucionAgente }>(`/modelos/${modelo}/ia/conversacion/mensajes`, {
    method: 'POST', body: JSON.stringify({ texto, clave, intencion }),
  });
export const enviarVozAgente = (
  modelo: string,
  texto: string,
  clave: string,
  intencion = 'AUTO',
  archivo?: Blob | null,
) => {
  const cuerpo = new FormData();
  cuerpo.append('clave', clave);
  cuerpo.append('intencion', intencion);
  if (texto.trim()) {
    cuerpo.append('texto', new Blob([texto.trim()], { type: 'text/plain;charset=UTF-8' }));
  }
  if (archivo && archivo.size > 0) {
    cuerpo.append('archivo', archivo, archivo.type.includes('wav') ? 'voz.wav' : 'voz.webm');
  }
  return api<{ mensaje: MensajeAgente; ejecucion: EjecucionAgente }>(
    `/modelos/${modelo}/ia/conversacion/voz`,
    { method: 'POST', body: cuerpo },
  );
};
export const confirmarAcuerdo = (modelo: string, idMensaje: string, contenido: string, sustituye?: string) =>
  api<AcuerdoAgente[]>(`/modelos/${modelo}/ia/conversacion/acuerdos`, {
    method: 'POST', body: JSON.stringify({ idMensaje, contenido, sustituye }),
  });
export const cancelarEjecucion = (modelo: string, id: string) =>
  api<void>(`/modelos/${modelo}/ia/conversacion/ejecuciones/${id}/cancelar`, { method: 'POST' });

async function enviarAdjunto(
  modelo: string,
  modalidad: 'voz' | 'imagen',
  cuerpo: FormData,
  intencion?: string,
): Promise<ResultadoSolicitudIa> {
  cuerpo.append('clave', crypto.randomUUID());
  cuerpo.append('intencion', intencion ?? (modalidad === 'imagen' ? 'MODIFICAR' : 'AUTO'));
  const aceptado = await api<{ ejecucion: EjecucionAgente }>(`/modelos/${modelo}/ia/conversacion/${modalidad}`, { method: 'POST', body: cuerpo });
  return esperarResultado(modelo, aceptado.ejecucion.id);
}

async function esperarResultado(modelo: string, id: string): Promise<ResultadoSolicitudIa> {
  for (let intento = 0; intento < 180; intento++) {
    const e = await api<EjecucionAgente & { revisionBase: number; resultado: { idPropuesta?: string; texto: string } }>(`/modelos/${modelo}/ia/conversacion/ejecuciones/${id}`);
    if (e.estado === 'COMPLETADO') return { idSolicitud: e.id, idPropuesta: e.resultado.idPropuesta ?? null,
      estadoSolicitud: 'COMPLETADO', resumen: e.resultado.texto, confianzaGeneral: null,
      revisionBase: e.revisionBase, mensaje: e.resultado.texto };
    if (e.estado === 'FALLIDO' || e.estado === 'CANCELADO') throw new Error(e.error ?? 'Solicitud cancelada. El adjunto se conserva.');
    await new Promise(resolve => window.setTimeout(resolve, 1000));
  }
  throw new Error('La solicitud sigue en el servidor. Reabre el chat para recuperar su resultado antes de reenviar.');
}

export type EstadoPropuestaIa =
  'BORRADOR' | 'LISTO' | 'APLICADO' | 'RECHAZADO' | 'DESACTUALIZADO' | 'FALLIDO';

export type OperacionIa = {
  id: string;
  numeroSecuencia: number;
  tipoOperacion: 'CREAR' | 'ACTUALIZAR' | 'ELIMINAR';
  tipoEntidad: 'CLASE' | 'ATRIBUTO' | 'OPERACION' | 'RELACION' | 'PAQUETE';
  idEntidadDestino: string | null;
  valorActual: Record<string, unknown> | null;
  valorPropuesto: Record<string, unknown> | null;
  confianza: number;
  seleccionada: boolean;
  estado: string;
  mensajesValidacion: unknown;
  justificacion: string;
  dependencias: string[];
};

export type PropuestaIa = {
  id: string;
  idSolicitud: string;
  idModelo: string;
  tipoEntrada: 'TEXTO' | 'VOZ' | 'IMAGEN';
  textoOriginal: string | null;
  revisionBase: number;
  revisionActual: number;
  estado: EstadoPropuestaIa;
  resumen: string;
  confianzaGeneral: number;
  creadoEn: string;
  revisionEdicion: string;
  puedeAplicar: boolean;
  operaciones: OperacionIa[];
};

export type ResultadoSolicitudIa = {
  idSolicitud: string;
  idPropuesta: string | null;
  estadoSolicitud: string;
  resumen: string | null;
  confianzaGeneral: number | null;
  revisionBase: number;
  mensaje: string;
};

export type ResultadoAplicacionIa = {
  idPropuesta: string;
  estado: EstadoPropuestaIa;
  aplicada: boolean;
  revision: number | null;
  idVersion: string | null;
  mensaje: string;
};

export const solicitarTexto = async (modelo: string, prompt: string): Promise<ResultadoSolicitudIa> => {
  const entrada = await enviarMensajeAgente(modelo,prompt,crypto.randomUUID());
  return esperarResultado(modelo,entrada.ejecucion.id);
};

export function solicitarVoz(
  modelo: string,
  archivo: Blob,
  texto?: string,
  duracionSegundos?: number,
  intencion = 'AUTO',
): Promise<ResultadoSolicitudIa> {
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo, archivo.type.includes('wav') ? 'voz.wav' : 'voz.webm');
  if (texto?.trim()) cuerpo.append('texto', texto.trim());
  if (duracionSegundos) cuerpo.append('duracionSegundos', String(duracionSegundos));
  return enviarAdjunto(modelo, 'voz', cuerpo, intencion);
}

export function solicitarImagen(
  modelo: string,
  archivo: File,
  textoDetectado?: string,
  intencion = 'MODIFICAR',
): Promise<ResultadoSolicitudIa> {
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo);
  if (textoDetectado?.trim()) cuerpo.append('textoDetectado', textoDetectado.trim());
  return enviarAdjunto(modelo, 'imagen', cuerpo, intencion);
}

export const leerPropuestaVigente = (modelo: string): Promise<PropuestaIa | undefined> =>
  api<PropuestaIa | undefined>('/modelos/' + modelo + '/ia/propuestas/vigente');

export const leerPropuesta = (modelo: string, propuesta: string): Promise<PropuestaIa> =>
  api<PropuestaIa>('/modelos/' + modelo + '/ia/propuestas/' + propuesta);

export const revisarPropuesta = (
  modelo: string,
  propuesta: PropuestaIa,
  operaciones: Array<{
    id: string;
    seleccionada: boolean;
    valorPropuesto: Record<string, unknown> | null;
  }>,
): Promise<PropuestaIa> =>
  api<PropuestaIa>('/modelos/' + modelo + '/ia/propuestas/' + propuesta.id, {
    method: 'PUT',
    body: JSON.stringify({ revision: propuesta.revisionEdicion, operaciones }),
  });

export const aplicarPropuesta = (
  modelo: string,
  propuesta: string,
): Promise<ResultadoAplicacionIa> =>
  api<ResultadoAplicacionIa>('/modelos/' + modelo + '/ia/propuestas/' + propuesta + '/aplicar', {
    method: 'POST',
  });

export const rechazarPropuesta = (
  modelo: string,
  propuesta: string,
  motivo: string,
): Promise<{ idPropuesta: string; estado: EstadoPropuestaIa; motivo: string }> =>
  api('/modelos/' + modelo + '/ia/propuestas/' + propuesta + '/rechazar', {
    method: 'POST',
    body: JSON.stringify({ motivo }),
  });
