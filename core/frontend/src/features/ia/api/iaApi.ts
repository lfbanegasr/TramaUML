import { api } from '../../../shared/api';

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

export const solicitarTexto = (modelo: string, prompt: string): Promise<ResultadoSolicitudIa> =>
  api<ResultadoSolicitudIa>('/modelos/' + modelo + '/ia/solicitud/texto', {
    method: 'POST',
    body: JSON.stringify({ prompt }),
  });

export function solicitarVoz(
  modelo: string,
  archivo: Blob,
  texto?: string,
  duracionSegundos?: number,
): Promise<ResultadoSolicitudIa> {
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo, archivo.type.includes('wav') ? 'voz.wav' : 'voz.webm');
  if (texto?.trim()) cuerpo.append('texto', texto.trim());
  if (duracionSegundos) cuerpo.append('duracionSegundos', String(duracionSegundos));
  return api<ResultadoSolicitudIa>('/modelos/' + modelo + '/ia/solicitud/voz', {
    method: 'POST',
    body: cuerpo,
  });
}

export function solicitarImagen(
  modelo: string,
  archivo: File,
  textoDetectado?: string,
): Promise<ResultadoSolicitudIa> {
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo);
  if (textoDetectado?.trim()) cuerpo.append('textoDetectado', textoDetectado.trim());
  return api<ResultadoSolicitudIa>('/modelos/' + modelo + '/ia/solicitud/imagen', {
    method: 'POST',
    body: cuerpo,
  });
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
