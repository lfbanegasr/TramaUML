import { api } from '../../../shared/api';

export type SolicitudXmi = {
  idSolicitud: string;
  descripcion?: string;
};

export type ResultadoXmi = {
  idIntercambio: string;
  idArchivoSalida: string;
  nombreArchivo: string;
  totalClases: number;
  totalRelaciones: number;
};

/** CU28 — POST: genera el XMI y persiste el intercambio. */
export const exportarXmi = (modelo: string, solicitud: SolicitudXmi): Promise<ResultadoXmi> =>
  api<ResultadoXmi>('/modelos/' + modelo + '/xmi/exportar', {
    method: 'POST',
    body: JSON.stringify(solicitud),
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
