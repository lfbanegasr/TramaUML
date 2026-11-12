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
export type ComprobacionServicio = {
  estado: 'DISPONIBLE' | 'NO_DISPONIBLE' | 'DEGRADADO';
  latenciaMs: number | null;
  detalle: string;
  comprobadoEn: string;
};
export type ServicioPlataforma = {
  id: string;
  nombre: string;
  proveedor: 'LOCAL' | 'OPENAI' | 'GEMINI' | 'OLLAMA';
  tipoServicio: 'IA_TEXTO' | 'VOZ_A_TEXTO' | 'IA_IMAGEN';
  urlServicio: string | null;
  configuracion: Record<string, unknown>;
  estado: 'ACTIVO' | 'INACTIVO' | 'SIN_VERIFICAR' | 'NO_DISPONIBLE';
  tieneCredencial: boolean;
  creadoEn: string;
  actualizadoEn: string;
  revision: string;
  ultimaComprobacion: ComprobacionServicio | null;
};
export type PaginaServicios = {
  servicios: ServicioPlataforma[];
  pagina: number;
  tamano: number;
  total: number;
};
export type DatosServicio = {
  nombre: string;
  proveedor: ServicioPlataforma['proveedor'];
  tipoServicio: ServicioPlataforma['tipoServicio'];
  urlServicio: string;
  configuracion: Record<string, unknown>;
  credencial?: string;
  retirarCredencial: boolean;
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
export const servicios = (
  nombre: string,
  proveedor: string,
  tipo: string,
  estado: string,
  pagina: number,
) =>
  api<PaginaServicios>(
    '/administracion/servicios?' +
      new URLSearchParams({
        nombre,
        proveedor,
        tipo,
        estado,
        pagina: String(pagina),
        tamano: '20',
      }),
  );
export const guardarServicio = (servicio: ServicioPlataforma | null, datos: DatosServicio) =>
  api<ServicioPlataforma>('/administracion/servicios' + (servicio ? '/' + servicio.id : ''), {
    method: servicio ? 'PUT' : 'POST',
    body: JSON.stringify(datos),
  });
export const verificarServicio = (id: string) =>
  api<ServicioPlataforma>('/administracion/servicios/' + id + '/verificar', {
    method: 'POST',
    body: '{}',
  });
export const activarImagenesGemini = () =>
  api<ServicioPlataforma>('/administracion/servicios/imagenes-gemini/activar', { method: 'POST' });
export const cambiarEstadoServicio = (
  servicio: ServicioPlataforma,
  estado: 'ACTIVO' | 'INACTIVO',
  confirmarImpacto: boolean,
) =>
  api<ServicioPlataforma>('/administracion/servicios/' + servicio.id + '/estado', {
    method: 'POST',
    body: JSON.stringify({ estado, revision: servicio.revision, confirmarImpacto }),
  });
