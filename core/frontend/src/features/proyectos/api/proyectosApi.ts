import { api } from '../../../shared/api';
export type Proyecto = {
  id: string;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  estado: string;
  propietario: string;
  rol: string;
  idModelo: string;
  puedeVerModelo: boolean;
  puedeEditar: boolean;
  puedeGestionarMiembros: boolean;
  puedeGestionarProyecto: boolean;
  puedeVerActividad: boolean;
  revision: string;
  creadoEn: string;
  archivadoEn: string | null;
  nombreModelo: string | null;
  revisionModelo: number;
  miembrosActivos: number;
  sesionesEdicionActivas: number;
};
export type Pagina = { proyectos: Proyecto[]; pagina: number; tamano: number; total: number };
export type ConsultaProyectos = {
  filtro: string;
  estado: string;
  rol: string;
  desde: string;
  hasta: string;
  pagina: number;
};
export const consultaInicial: ConsultaProyectos = {
  filtro: '',
  estado: '',
  rol: '',
  desde: '',
  hasta: '',
  pagina: 0,
};
export const listarProyectos = (consulta: ConsultaProyectos) => {
  const p = new URLSearchParams({
    filtro: consulta.filtro,
    estado: consulta.estado,
    rol: consulta.rol,
    pagina: String(consulta.pagina),
  });
  if (consulta.desde) p.set('desde', consulta.desde);
  if (consulta.hasta) p.set('hasta', consulta.hasta);
  return api<Pagina>(`/proyectos?${p}`);
};
export const consultarProyecto = (id: string) => api<Proyecto>(`/proyectos/${id}`);
export const rolesConsulta = () =>
  api<{ codigo: string; nombre: string }[]>('/proyectos/roles-consulta');
export const consultarEquipo = (id: string) =>
  api<{ id: string; nombre: string; estado: string; roles: string }[]>(`/proyectos/${id}/equipo`);
export type ActualizacionProyecto = {
  codigo: string;
  nombre: string;
  descripcion: string;
  revision: string;
  idSolicitudCliente: string;
};
export type EstadoProyecto = { revision: string; idSolicitudCliente: string; confirmar: boolean };
export const actualizarProyecto = (id: string, datos: ActualizacionProyecto) =>
  api<Proyecto>(`/proyectos/${id}`, { method: 'PUT', body: JSON.stringify(datos) });
export const cambiarEstadoProyecto = (
  id: string,
  accion: 'archivar' | 'reactivar',
  datos: EstadoProyecto,
) => api<Proyecto>(`/proyectos/${id}/${accion}`, { method: 'POST', body: JSON.stringify(datos) });
export const crearProyecto = (datos: { codigo: string; nombre: string; descripcion: string }) =>
  api<Proyecto>('/proyectos', { method: 'POST', body: JSON.stringify(datos) });
