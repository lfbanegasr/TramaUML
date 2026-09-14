import { api } from '../../../shared/api';

export type Usuario = {
  id: string;
  usuario: string;
  correo: string;
  nombre: string;
  estado: string;
  revision: string;
  roles_json: string;
};
export type Rol = {
  id: string;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  ambito: string;
  estado: string;
  es_del_sistema: boolean;
  revision: string;
  asignaciones: number;
  permisos_json: string;
};
export type Permiso = {
  id: string;
  codigo: string;
  modulo: string;
  accion: string;
  descripcion: string;
  ambito: string;
};
export type Credencial = { id: string; credencialTemporal: string };
export type DatosUsuario = {
  usuario: string;
  correo: string;
  nombre: string;
  estado: string;
  roles: string[];
  revision?: string;
};
export type DatosRol = {
  codigo: string;
  nombre: string;
  descripcion: string;
  ambito: string;
  estado: string;
  permisos: string[];
  revision?: string;
};

export const usuarios = (filtro: string, estado: string, rol: string, pagina: number) =>
  api<Usuario[]>(
    '/administracion/usuarios?' +
      new URLSearchParams({ filtro, estado, rol, pagina: String(pagina) }),
  );
export const roles = () => api<Rol[]>('/administracion/roles');
export const rolesUsuario = () => api<Rol[]>('/administracion/roles-usuario');
export const permisos = () => api<Permiso[]>('/administracion/permisos');
export const crearUsuario = (datos: DatosUsuario) =>
  api<Credencial>('/administracion/usuarios', { method: 'POST', body: JSON.stringify(datos) });
export const guardarUsuario = (id: string, datos: DatosUsuario) =>
  api<Usuario>('/administracion/usuarios/' + id, { method: 'PUT', body: JSON.stringify(datos) });
export const restablecer = (u: Usuario) =>
  api<Credencial>('/administracion/usuarios/' + u.id + '/restablecer', {
    method: 'POST',
    body: JSON.stringify({ revision: u.revision }),
  });
export const guardarRol = (r: Rol | null, datos: DatosRol) =>
  api<Rol>('/administracion/roles' + (r ? '/' + r.id : ''), {
    method: r ? 'PUT' : 'POST',
    body: JSON.stringify(datos),
  });
