export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
let csrf: { token: string; header: string } | null = null;
export function renovarCsrf() { csrf = null; }
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method ?? 'GET';
  if (!['GET', 'HEAD'].includes(method) && !csrf) {
    const r = await fetch('/api/acceso/csrf', { credentials: 'same-origin' });
    if (!r.ok) throw new ApiError(r.status, 'No se pudo proteger la solicitud.');
    csrf = await r.json();
  }
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (csrf && !['GET', 'HEAD'].includes(method)) headers.set(csrf.header, csrf.token);
  let r: Response;
  try { r = await fetch('/api' + path, { ...init, headers, credentials: 'same-origin' }); }
  catch { throw new ApiError(0, 'No se pudo confirmar la operación. Comprueba la conexión antes de reintentar.'); }
  if (!r.ok) {
    if (r.status === 403) renovarCsrf();
    const error = await r.json().catch(() => ({})) as { mensaje?: string };
    if (r.status === 401) window.dispatchEvent(new Event('sesion-vencida'));
    throw new ApiError(r.status, error.mensaje ?? 'No se pudo completar la operación.');
  }
  return r.status === 204 ? undefined as T : r.json() as Promise<T>;
}
