import { api } from '../../../shared/api';

export type Participante = {
  idUsuario: string;
  nombre: string;
  rol: string;
  modo: 'EDITOR' | 'LECTOR';
  estado: 'EN_LINEA' | 'AUSENTE' | 'DESCONECTADO';
  tipoEntidadEnfocada: string | null;
  idEntidadEnfocada: string | null;
};

export type SesionColaborativa = {
  id: string;
  idModelo: string;
  modo: 'EDICION' | 'SOLO_LECTURA';
  estado: 'ACTIVO' | 'CERRADO' | 'INTERRUMPIDO';
  revisionBase: number;
  modoPropio: 'EDITOR' | 'LECTOR';
  participantes: Participante[];
};

export type EventoColaborativo = {
  tipo: 'CONECTADO' | 'PRESENCIA' | 'MODELO_CONFIRMADO' | 'ERROR';
  datos: Record<string, unknown> & {
    sesion?: SesionColaborativa | string;
    revision?: number;
    cambio?: string;
    actor?: string;
    mensaje?: string;
  };
};

export const abrirSesion = (modelo: string, idSolicitudCliente: string) =>
  api<SesionColaborativa>('/modelos/' + modelo + '/sesiones', {
    method: 'POST',
    body: JSON.stringify({ idSolicitudCliente }),
  });

export const cerrarSesion = (modelo: string, sesion: string) =>
  api<SesionColaborativa>('/modelos/' + modelo + '/sesiones/' + sesion, {
    method: 'DELETE',
  });

export function abrirCanal(modelo: string, sesion: string) {
  const protocolo = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return new WebSocket(
    protocolo + '//' + window.location.host + '/ws/modelos/' + modelo + '/sesiones/' + sesion,
  );
}
