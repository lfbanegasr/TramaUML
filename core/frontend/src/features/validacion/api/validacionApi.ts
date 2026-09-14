import { api } from '../../../shared/api';
export type Hallazgo = {
  id: string;
  severidad: string;
  entidad: string;
  elemento: string | null;
  regla: string;
  mensaje: string;
  recomendacion: string;
  ubicacion_json: string;
};
export type Informe = {
  id?: string;
  revision?: number;
  estado: string;
  estado_resultado?: string;
  errores?: number;
  advertencias?: number;
  hallazgos: Hallazgo[];
};
export const solicitar = (modelo: string) =>
  api<{ id: string; revision: number }>('/modelos/' + modelo + '/validaciones', { method: 'POST' });
export const informe = (modelo: string, id?: string) =>
  api<Informe>('/modelos/' + modelo + '/validaciones' + (id ? '/' + id : ''));
