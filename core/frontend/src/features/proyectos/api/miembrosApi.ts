import { api } from '../../../shared/api';
import type { Permiso, Rol } from '../../administracion/api/administracionApi';
export type Decision = { permiso: string; decision: 'PERMITIR' | 'DENEGAR' };
export type Miembro = {
  id: string;
  usuario: string;
  identificador: string;
  nombre: string;
  correo: string;
  estado: string;
  motivo: string | null;
  revision: string;
  roles_json: string;
  permisos_json: string;
  sesiones_activas: number;
};
export type Cuenta = { id: string; usuario: string; nombre: string; correo: string };
export type Equipo = {
  propietario: string;
  miembros: Miembro[];
  roles: Rol[];
  permisos: Permiso[];
};
export type DatosMiembro = {
  usuario: string;
  rol: string;
  estado: string;
  motivo: string;
  permisos: Decision[];
  revision?: string;
};
const ruta = (proyecto: string) => '/proyectos/' + proyecto + '/miembros';
export const equipo = (id: string) => api<Equipo>(ruta(id));
export const cuentas = (id: string, filtro: string) =>
  api<Cuenta[]>(ruta(id) + '/cuentas?' + new URLSearchParams({ filtro }));
export const guardarMiembro = (proyecto: string, m: Miembro | null, datos: DatosMiembro) =>
  api<Miembro>(ruta(proyecto) + (m ? '/' + m.id : ''), {
    method: m ? 'PUT' : 'POST',
    body: JSON.stringify(datos),
  });
export const transferir = (proyecto: string, miembro: string) =>
  api<Equipo>(ruta(proyecto) + '/propietario', {
    method: 'POST',
    body: JSON.stringify({ miembro }),
  });
