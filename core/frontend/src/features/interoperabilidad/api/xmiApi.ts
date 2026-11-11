import { api } from '../../../shared/api';

export type SolicitudXmi = {
  idSolicitud: string;
  descripcion?: string;
  idVersion?: string;
  estadoActual?: boolean;
  idPaquete?: string;
  incluirSubpaquetes?: boolean;
  formato?: 'XMI_251_EA' | 'XMI_251_OMG' | 'XMI_EA' | 'SCRIPT_VBS_EA' | 'XMI_OMG';
};

export type ResultadoXmi = {
  idIntercambio: string;
  idArchivoSalida: string;
  nombreArchivo: string;
  idVersion: string | null;
  idPaquete: string | null;
  totalPaquetes: number;
  totalClases: number;
  totalRelaciones: number;
  advertencias: string[];
};

export type OperacionImportacionXmi = {
  orden: number;
  operacion: 'CREAR' | 'ACTUALIZAR' | 'ELIMINAR';
  entidad: 'PAQUETE' | 'CLASE' | 'ATRIBUTO' | 'OPERACION' | 'RELACION';
  idEntidad: string;
  nombre: string;
  datos: Record<string, unknown> | null;
};

export type ImportacionXmi = {
  idIntercambio: string;
  idArchivoEntrada: string;
  nombreArchivo: string;
  estado: 'ANALIZANDO' | 'LISTO_PARA_REVISION' | 'COMPLETADO' | 'FALLIDO' | 'CANCELADO';
  estrategia: 'FUSIONAR' | 'REEMPLAZAR';
  varianteXmi: string;
  versionEnterpriseArchitect: string | null;
  revisionBase: number;
  revisionActual: number;
  totalPaquetes: number;
  totalClases: number;
  totalAtributos: number;
  totalOperaciones: number;
  totalRelaciones: number;
  advertencias: string[];
  bloqueadores: string[];
  operaciones: OperacionImportacionXmi[];
  puedeAplicar: boolean;
  detalleError: string | null;
};

export type ResultadoAplicacionXmi = {
  idIntercambio: string;
  estado: string;
  revision: number;
  idVersion: string;
  repetido: boolean;
};

/** CU28 — POST: genera el XMI y persiste el intercambio. */
export const exportarXmi = (modelo: string, solicitud: SolicitudXmi): Promise<ResultadoXmi> =>
  api<ResultadoXmi>('/modelos/' + modelo + '/xmi/exportar', {
    method: 'POST',
    body: JSON.stringify(solicitud),
  });

export const importarXmi = (
  modelo: string,
  archivo: File,
  estrategia: 'FUSIONAR' | 'REEMPLAZAR',
  idSolicitud: string,
): Promise<ImportacionXmi> => {
  const form = new FormData();
  form.append('archivo', archivo);
  const query = new URLSearchParams({ idSolicitud, estrategia });
  return api<ImportacionXmi>(`/modelos/${modelo}/xmi/importaciones?${query.toString()}`, {
    method: 'POST',
    body: form,
  });
};

export const consultarImportacionXmi = (
  modelo: string,
  intercambio: string,
): Promise<ImportacionXmi> =>
  api<ImportacionXmi>(`/modelos/${modelo}/xmi/importaciones/${intercambio}`);

export const aplicarImportacionXmi = (
  modelo: string,
  intercambio: string,
  sesion: string,
  revisionBase: number,
  idSolicitud: string,
): Promise<ResultadoAplicacionXmi> =>
  api<ResultadoAplicacionXmi>(`/modelos/${modelo}/xmi/importaciones/${intercambio}/aplicar`, {
    method: 'POST',
    body: JSON.stringify({
      idSolicitud,
      idSesionEdicion: sesion,
      revisionBase,
      confirmar: true,
    }),
  });

export const cancelarImportacionXmi = (
  modelo: string,
  intercambio: string,
  motivo?: string,
): Promise<ImportacionXmi> =>
  api<ImportacionXmi>(`/modelos/${modelo}/xmi/importaciones/${intercambio}/cancelar`, {
    method: 'POST',
    body: JSON.stringify({ motivo: motivo || undefined }),
  });

/**
 * CU28 — GET: descarga el archivo XMI directamente como blob y lo ofrece al navegador.
 * Crea un objeto URL temporal y lo revoca después de la descarga.
 */
export const descargarXmi = async (
  modelo: string,
  idIntercambio: string,
  nombreArchivo: string,
): Promise<void> => {
  const r = await fetch('/api/modelos/' + modelo + '/xmi/' + idIntercambio + '/descargar', {
    credentials: 'same-origin',
  });
  if (!r.ok) throw new Error('No se pudo descargar el archivo XMI.');
  const blob = await r.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombreArchivo;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};
