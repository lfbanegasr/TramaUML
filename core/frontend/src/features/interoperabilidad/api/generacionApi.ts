import { api } from '../../../shared/api';

export type VersionGeneracion = {
  id: string;
  numeroVersion: number;
  revision: number;
  estadoValidacion: string;
  versionActual: boolean;
};

export type OperacionGeneracion = {
  id: string;
  nombre: string;
  tipoRetorno: string | null;
  seleccionada: boolean;
};

export type ClaseGeneracion = {
  idClase: string;
  nombre: string;
  seleccionada: boolean;
  requerida: boolean;
  requeridaPor: string[];
  esPersistente: boolean;
  exponerRest: boolean;
  nombreTabla: string;
  nombreEntidad: string;
  operaciones: OperacionGeneracion[];
};

export type MapeoTipo = {
  tipoUml: string;
  tipoJava: string;
  tipoPostgresql: string;
  claseConversor: string | null;
};

export type DependenciaGeneracion = {
  idGrupo: string;
  idArtefacto: string;
  version: string | null;
  ambito: 'COMPILACION' | 'EJECUCION' | 'PRUEBA' | 'PROVISTO';
  obligatoria: boolean;
};

export type MensajeConfiguracion = {
  nivel: 'ERROR' | 'ADVERTENCIA' | 'INFORMACION';
  codigo: string;
  mensaje: string;
};

export type ConfiguracionGeneracion = {
  id: string | null;
  idVersionModelo: string;
  numeroVersion: number;
  revisionVersion: number;
  versionActual: boolean;
  estadoVersion: string;
  estado: string;
  nombre: string;
  nombreProyecto: string;
  idGrupo: string;
  idArtefacto: string;
  paqueteBase: string;
  versionInicial: string;
  versionJava: string;
  versionSpringBoot: string;
  herramientaMigracion: 'FLYWAY' | 'LIQUIBASE' | 'NINGUNO';
  generarRepositorios: boolean;
  generarServicios: boolean;
  generarControladores: boolean;
  generarDtos: boolean;
  exponerRest: boolean;
  versionesDisponibles: VersionGeneracion[];
  clases: ClaseGeneracion[];
  mapeosTipo: MapeoTipo[];
  dependencias: DependenciaGeneracion[];
  mensajes: MensajeConfiguracion[];
  puedeGenerar: boolean;
};

export type ActualizarConfiguracion = Pick<
  ConfiguracionGeneracion,
  | 'idVersionModelo'
  | 'nombre'
  | 'nombreProyecto'
  | 'idGrupo'
  | 'idArtefacto'
  | 'paqueteBase'
  | 'versionInicial'
  | 'versionJava'
  | 'versionSpringBoot'
  | 'herramientaMigracion'
  | 'generarRepositorios'
  | 'generarServicios'
  | 'generarControladores'
  | 'generarDtos'
  | 'exponerRest'
> & {
  clases: Array<{
    idClase: string;
    seleccionada: boolean;
    esPersistente: boolean;
    exponerRest: boolean;
    nombreTabla: string;
    nombreEntidad: string;
    operaciones: Array<{ idOperacion: string; seleccionada: boolean }>;
  }>;
  mapeosTipo: MapeoTipo[];
  dependencias: DependenciaGeneracion[];
};

export type ResultadoGeneracion = {
  idGeneracion: string;
  idConfiguracion: string;
  idVersionModelo: string;
  idArchivoSalida: string;
  nombreArchivo: string;
  totalClases: number;
  totalOperaciones: number;
  totalArchivosGenerados: number;
};

export type InformeBackend = {
  id: string;
  idGeneracion: string;
  estado:
    'EN_EJECUCION' | 'VALIDO' | 'VALIDO_CON_ADVERTENCIAS' | 'INVALIDO' | 'FALLIDO' | 'INCOMPLETO';
  comprobacionesAprobadas: number;
  cantidadAdvertencias: number;
  cantidadErrores: number;
  iniciadoEn: string;
  completadoEn: string | null;
  sha256: string | null;
  registroCompilacion: string;
  hallazgos: Array<{
    severidad: string;
    tipoComprobacion: string;
    componente: string | null;
    mensaje: string;
  }>;
  puedeDescargar: boolean;
};

export type GeneracionDisponible = {
  idGeneracion: string;
  idConfiguracion: string;
  idVersionModelo: string;
  numeroVersion: number;
  nombreArchivo: string;
  creadoEn: string;
  validacion: InformeBackend | null;
  puedeValidar: boolean;
};

export const listarGeneraciones = (modelo: string): Promise<GeneracionDisponible[]> =>
  api('/modelos/' + modelo + '/generacion');

export const validarBackend = (modelo: string, generacion: string): Promise<InformeBackend> =>
  api('/modelos/' + modelo + '/generacion/' + generacion + '/validaciones', { method: 'POST' });

export const leerInformeBackend = (modelo: string, generacion: string): Promise<InformeBackend> =>
  api('/modelos/' + modelo + '/generacion/' + generacion + '/validacion');

export const leerConfigGeneracion = (
  modelo: string,
  version?: string,
): Promise<ConfiguracionGeneracion> =>
  api<ConfiguracionGeneracion>(
    '/modelos/' +
      modelo +
      '/generacion/configuracion' +
      (version ? '?version=' + encodeURIComponent(version) : ''),
  );

export const guardarConfigGeneracion = (
  modelo: string,
  cfg: ActualizarConfiguracion,
): Promise<ConfiguracionGeneracion> =>
  api<ConfiguracionGeneracion>('/modelos/' + modelo + '/generacion/configuracion', {
    method: 'PUT',
    body: JSON.stringify(cfg),
  });

export const generarBackend = (
  modelo: string,
  idConfiguracion: string,
): Promise<ResultadoGeneracion> =>
  api<ResultadoGeneracion>('/modelos/' + modelo + '/generacion/generar', {
    method: 'POST',
    body: JSON.stringify({ idConfiguracion }),
  });

export const descargarArtefacto = async (
  modelo: string,
  idGeneracion: string,
  nombreArchivo: string,
): Promise<void> => {
  const r = await fetch('/api/modelos/' + modelo + '/generacion/' + idGeneracion + '/descargar', {
    credentials: 'same-origin',
  });
  if (!r.ok) {
    const error = (await r.json().catch(() => ({}))) as { mensaje?: string };
    throw new Error(error.mensaje ?? 'No se pudo descargar el proyecto generado.');
  }
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
