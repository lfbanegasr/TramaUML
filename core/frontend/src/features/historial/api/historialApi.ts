import { api } from '../../../shared/api';

export interface ResumenVersion {
  id: string;
  numeroVersion: number;
  revision: number;
  idVersionPadre: string | null;
  idVersionRestaurada: string | null;
  estadoValidacion: string | null;
  resumen: string | null;
  idAutor: string;
  nombreAutor: string;
  creadoEn: string;
  sha256: string | null;
}

export interface PaginaVersiones {
  versiones: ResumenVersion[];
  total: number;
  cursorSiguiente: number | null;
}

export interface PropiedadDif {
  clave: string;
  valorAntes: unknown;
  valorDespues: unknown;
}

export interface ElementoDif {
  id: string | null;
  tipo: string;
  nombre: string | null;
  operacion: 'AGREGADO' | 'ELIMINADO' | 'MODIFICADO';
  propiedades: PropiedadDif[];
}

export interface DifVersion {
  desde: ResumenVersion;
  hasta: ResumenVersion;
  diferencias: ElementoDif[];
}

export interface DetalleVersion {
  resumen: ResumenVersion;
  instantanea: unknown;
}

export interface VistaPrevia {
  revisionVigente: number;
  versionVigente: string;
  bloqueadores: string[];
  elementosEstimados: number;
}

export interface ResultadoRestauracion {
  revision: number;
  version: string;
  repetido: boolean;
}

export const listarVersiones = (
  modelo: string,
  cursor?: number,
  limite = 20,
  autor?: string,
  desde?: string,
  hasta?: string,
): Promise<PaginaVersiones> => {
  const p = new URLSearchParams({ limite: String(limite) });
  if (cursor != null) p.set('cursor', String(cursor));
  if (autor) p.set('autor', autor);
  if (desde) p.set('desde', desde);
  if (hasta) p.set('hasta', hasta);
  return api<PaginaVersiones>(`/modelos/${modelo}/versiones?${p.toString()}`);
};

export const consultarVersion = (modelo: string, version: string): Promise<DetalleVersion> =>
  api<DetalleVersion>(`/modelos/${modelo}/versiones/${version}`);

export const compararVersiones = (
  modelo: string,
  desde: string,
  hasta: string,
): Promise<DifVersion> =>
  api<DifVersion>(`/modelos/${modelo}/comparaciones?desde=${desde}&hasta=${hasta}`);

export const previsualizarRestauracion = (
  modelo: string,
  idVersionOrigen: string,
): Promise<VistaPrevia> =>
  api<VistaPrevia>(`/modelos/${modelo}/restauraciones/previsualizaciones`, {
    method: 'POST',
    body: JSON.stringify({ idVersionOrigen }),
  });

export const restaurarVersion = (
  modelo: string,
  idSolicitudRestauracion: string,
  idVersionOrigen: string,
  revisionEsperada: number,
  motivo: string,
): Promise<ResultadoRestauracion> =>
  api<ResultadoRestauracion>(`/modelos/${modelo}/restauraciones`, {
    method: 'POST',
    body: JSON.stringify({
      idSolicitudRestauracion,
      idVersionOrigen,
      revisionEsperada,
      motivo,
      confirmar: true,
    }),
  });
