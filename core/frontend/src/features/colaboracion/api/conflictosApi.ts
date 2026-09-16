import { api } from '../../../shared/api';

export type AutorConflicto = { id: string; nombre: string };
export type CambioRelacionado = {
  id: string;
  idCambioCliente: string;
  autor: AutorConflicto;
  revisionBase: number;
  revisionAplicada: number | null;
  creadoEn: string;
};
export type Diferencia = {
  indiceAccion: number;
  operacion: string;
  tipoEntidad: string;
  idEntidad: string;
  propiedad: string;
  valorBase: unknown;
  valorConfirmado: unknown;
  valorPropuesto: unknown;
  incompatible: boolean;
};
export type Conflicto = {
  id: string;
  idModelo: string;
  tipoEntidad: string;
  idEntidad: string | null;
  revisionBase: number;
  revisionActual: number;
  estado: 'ABIERTO' | 'RESUELTO' | 'DESCARTADO';
  tipoResolucion: string | null;
  detectadoEn: string;
  resueltoPor: AutorConflicto | null;
  resueltoEn: string | null;
  cambioAlmacenado: CambioRelacionado | null;
  cambioEntrante: CambioRelacionado | null;
  valorAlmacenadoDetectado: unknown;
  valorEntrante: unknown;
  valorConfirmadoActual: unknown;
  valorResolucion: unknown;
  diferencias: Diferencia[];
  fusionable: boolean;
  elementoEliminado: boolean;
  puedeResolver: boolean;
};
export type ListaConflictos = {
  conflictos: Conflicto[];
  total: number;
  revisionModelo: number;
  puedeResolver: boolean;
};
export type ResultadoConflicto = {
  conflicto: Conflicto;
  revision: number | null;
  idVersion: string | null;
  repetido: boolean;
};

export const listarConflictos = (modelo: string, estado = 'ABIERTO') =>
  api<ListaConflictos>('/modelos/' + modelo + '/conflictos?estado=' + estado);

export const resolverConflicto = (
  modelo: string,
  conflicto: string,
  solicitud: {
    idSesionEdicion: string;
    idSolicitudResolucion: string;
    revisionEsperada: number;
    tipoResolucion: 'CONSERVAR_ALMACENADO' | 'APLICAR_ENTRANTE' | 'FUSIONAR';
    confirmar: true;
  },
) =>
  api<ResultadoConflicto>('/modelos/' + modelo + '/conflictos/' + conflicto + '/resoluciones', {
    method: 'POST',
    body: JSON.stringify(solicitud),
  });

export const descartarConflicto = (
  modelo: string,
  conflicto: string,
  solicitud: {
    idSesionEdicion: string;
    idSolicitudResolucion: string;
    revisionEsperada: number;
    confirmar: true;
  },
) =>
  api<ResultadoConflicto>('/modelos/' + modelo + '/conflictos/' + conflicto + '/descartes', {
    method: 'POST',
    body: JSON.stringify(solicitud),
  });
