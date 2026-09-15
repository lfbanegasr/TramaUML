import { api } from '../../../shared/api';
export type Evento = {
  id: string;
  actor: string | null;
  responsable: string;
  categoria: string;
  accion: string;
  tipoEntidad: string | null;
  idEntidad: string | null;
  ocurridoEn: string;
  idModelo: string | null;
  idVersionModelo: string | null;
  revisionModelo: number | null;
};
export type ConsultaActividad = {
  responsable: string;
  accion: string;
  tipo: string;
  entidad: string;
  desde: string;
  hasta: string;
  pagina: number;
  tamano: number;
};
export const actividadInicial: ConsultaActividad = {
  responsable: '',
  accion: '',
  tipo: '',
  entidad: '',
  desde: '',
  hasta: '',
  pagina: 0,
  tamano: 20,
};
export type PaginaActividad = { eventos: Evento[]; total: number; pagina: number; tamano: number };
export type DetalleActividad = {
  evento: Evento;
  valorAnterior: unknown;
  valorPosterior: unknown;
  detalle: unknown;
};
export type FiltrosActividad = {
  actores: { id: string; nombre: string }[];
  acciones: string[];
  tipos: string[];
};
export function consultarActividad(proyecto: string, consulta: ConsultaActividad) {
  const params = new URLSearchParams();
  Object.entries(consulta).forEach(([k, v]) => {
    if (v !== '') params.set(k, String(v));
  });
  return api<PaginaActividad>(`/proyectos/${proyecto}/actividad?${params}`);
}
export const filtrosActividad = (id: string) =>
  api<FiltrosActividad>(`/proyectos/${id}/actividad/filtros`);
export const detalleActividad = (proyecto: string, id: string) =>
  api<DetalleActividad>(`/proyectos/${proyecto}/actividad/${id}`);
