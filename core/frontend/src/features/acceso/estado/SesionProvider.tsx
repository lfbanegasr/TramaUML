import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import * as acceso from '../api/accesoApi';
import { ApiError } from '../../../shared/api';
type Estado = { sesion: acceso.Sesion | null; cargando: boolean; error: string; entrar: typeof acceso.entrar; salir: () => Promise<void> };
const Contexto = createContext<Estado | null>(null);
export function SesionProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<acceso.Sesion | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    acceso.consultarSesion().then(setSesion).catch(e => {
      if (!(e instanceof ApiError) || e.status !== 401) setError(e.message);
    }).finally(() => setCargando(false));
    const vencida = () => setSesion(null);
    window.addEventListener('sesion-vencida', vencida);
    return () => window.removeEventListener('sesion-vencida', vencida);
  }, []);
  return <Contexto.Provider value={{
    sesion, cargando, error,
    entrar: async (i, c) => { const s = await acceso.entrar(i, c); setSesion(s); setError(''); return s; },
    salir: async () => { await acceso.salir(); setSesion(null); },
  }}>{children}</Contexto.Provider>;
}
export function useSesion() { const c = useContext(Contexto); if (!c) throw new Error('Falta el contexto de sesión'); return c; }
