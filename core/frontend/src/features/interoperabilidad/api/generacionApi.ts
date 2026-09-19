import { api } from '../../../shared/api';

export type ConfiguracionGeneracion = {
  id: string | null;
  nombre: string;
  nombreProyecto: string;
  idGrupo: string;
  idArtefacto: string;
  paqueteBase: string;
  versionInicial: string;
  generarRepositorios: boolean;
  generarControladores: boolean;
  generarDtos: boolean;
  exponerRest: boolean;
};

export type ActualizarConfiguracion = Omit<ConfiguracionGeneracion, 'id'>;

export type ResultadoGeneracion = {
  idGeneracion: string;
  idArchivoSalida: string;
  nombreArchivo: string;
  totalClases: number;
  totalArchivosGenerados: number;
};

/** CU29 — Lee la configuración de generación vigente o defaults. */
export const leerConfigGeneracion = (modelo: string): Promise<ConfiguracionGeneracion> =>
  api<ConfiguracionGeneracion>('/modelos/' + modelo + '/generacion/configuracion');

/** CU29 — Guarda o actualiza la configuración de generación. */
export const guardarConfigGeneracion = (
  modelo: string,
  cfg: ActualizarConfiguracion,
): Promise<ConfiguracionGeneracion> =>
  api<ConfiguracionGeneracion>('/modelos/' + modelo + '/generacion/configuracion', {
    method: 'PUT',
    body: JSON.stringify(cfg),
  });

/** CU30 — Genera el proyecto Spring Boot y devuelve metadatos. */
export const generarBackend = (modelo: string): Promise<ResultadoGeneracion> =>
  api<ResultadoGeneracion>('/modelos/' + modelo + '/generacion/generar', {
    method: 'POST',
  });

/**
 * CU32 — Descarga el ZIP del proyecto generado directamente como blob.
 */
export const descargarArtefacto = async (
  modelo: string,
  idGeneracion: string,
  nombreArchivo: string,
): Promise<void> => {
  const r = await fetch('/api/modelos/' + modelo + '/generacion/' + idGeneracion + '/descargar', {
    credentials: 'same-origin',
  });
  if (!r.ok) throw new Error('No se pudo descargar el proyecto generado.');
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
