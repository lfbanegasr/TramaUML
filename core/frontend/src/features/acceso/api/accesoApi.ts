import { api, renovarCsrf } from '../../../shared/api';
export type Usuario = { id: string; nombreUsuario: string; nombreCompleto: string; rolesGlobales: string[]; permisosGlobales: string[] };
export type Sesion = { usuario: Usuario; puedeCrearProyectos: boolean };
export const consultarSesion = () => api<Sesion>('/acceso/sesion');
export async function entrar(identificador: string, contrasena: string) {
  const result = await api<Sesion>('/acceso/entrar', { method: 'POST', body: JSON.stringify({ identificador, contrasena }) });
  renovarCsrf(); return result;
}
export async function salir() { await api<void>('/acceso/salir', { method: 'POST' }); renovarCsrf(); }
