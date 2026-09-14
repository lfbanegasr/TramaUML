import { api } from '../../../shared/api';
export type Proyecto = { id: string; codigo: string; nombre: string; descripcion: string | null; estado: string; propietario: string; rol: string; idModelo: string; puedeVerModelo: boolean; puedeEditar: boolean };
export type Pagina = { proyectos: Proyecto[]; pagina: number; tamano: number; total: number };
export const listarProyectos = (filtro: string, estado: string, pagina: number) =>
  api<Pagina>(`/proyectos?${new URLSearchParams({ filtro, estado, pagina: String(pagina) })}`);
export const crearProyecto = (datos: { codigo: string; nombre: string; descripcion: string }) =>
  api<Proyecto>('/proyectos', { method: 'POST', body: JSON.stringify(datos) });
