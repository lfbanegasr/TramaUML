import { api, renovarCsrf } from '../../../shared/api';
export type Usuario = {
  id: string;
  nombreUsuario: string;
  nombreCompleto: string;
  rolesGlobales: string[];
  permisosGlobales: string[];
};
export type Sesion = { usuario: Usuario; puedeCrearProyectos: boolean };
export type SesionResumen = {
  id: string;
  emitidoEn: string;
  expiraEn: string | null;
  revocadoEn: string | null;
  motivoRevocacion: string | null;
  direccionIp: string | null;
  agenteCliente: string | null;
  activa: boolean;
};
export type PaginaSesiones = {
  sesiones: SesionResumen[];
  pagina: number;
  tamano: number;
  total: number;
};
export const consultarSesion = () => api<Sesion>('/acceso/sesion');
export const consultarSesiones = (pagina = 0, tamano = 20) =>
  api<PaginaSesiones>(`/acceso/sesiones?pagina=${pagina}&tamano=${tamano}`);
export async function entrar(identificador: string, contrasena: string) {
  const result = await api<Sesion>('/acceso/entrar', {
    method: 'POST',
    body: JSON.stringify({ identificador, contrasena }),
  });
  renovarCsrf();
  return result;
}
export async function salir() {
  await api<void>('/acceso/salir', { method: 'POST' });
  renovarCsrf();
}
