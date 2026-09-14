import { useEffect, useRef, useState } from 'react';
import { aplicarCambio, leerModelo, type Modelo, type Accion, type Cambio } from '../api/modeladoApi';
import { ApiError } from '../../../shared/api';
export function useModelo(id: string) {
  const [modelo, setModelo] = useState<Modelo | null>(null), [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false), [pendiente, setPendiente] = useState<Cambio | null>(null);
  const [conflicto, setConflicto] = useState(false), [mensaje, setMensaje] = useState('');
  const [desactualizado, setDesactualizado] = useState(false);
  const ocupado = useRef(false);
  useEffect(() => { let vivo = true; leerModelo(id).then(m => { if (vivo) setModelo(m); }).catch(e => { if (vivo) setError(e.message); }); return () => { vivo = false; }; }, [id]);
  async function recargar() {
    if (ocupado.current) return false;
    ocupado.current = true; setEnviando(true);
    try {
      setModelo(await leerModelo(id)); setError(''); setConflicto(false); setPendiente(null);
      setDesactualizado(false); setMensaje('Modelo actualizado'); return true;
    } catch (e) { setError((e as Error).message); return false; }
    finally { ocupado.current = false; setEnviando(false); }
  }
  async function enviar(cambio: Cambio) {
    if (ocupado.current) return false;
    ocupado.current = true; setEnviando(true); setError(''); setMensaje(''); setPendiente(cambio);
    try {
      const c = await aplicarCambio(id, cambio); setPendiente(null); setConflicto(false);
      setMensaje('Guardado · revisión ' + c.revision);
      try { setModelo(await leerModelo(id)); setDesactualizado(false); }
      catch { setDesactualizado(true); setError('El cambio se confirmó. Recarga para consultar el modelo actualizado.'); }
      return true;
    } catch (e) {
      setError((e as Error).message); setConflicto(e instanceof ApiError && e.status === 409);
      // Un rechazo conocido permite corregir; una respuesta perdida conserva el UUID.
      if (e instanceof ApiError && [400,401,403,404,413,422].includes(e.status)) setPendiente(null);
      return false;
    } finally { ocupado.current = false; setEnviando(false); }
  }
  async function cambiar(accion: Accion) {
    if (!modelo || ocupado.current || pendiente || conflicto || desactualizado) return false;
    return enviar({ idCambioCliente: crypto.randomUUID(), revisionBase: modelo.revision, acciones: [accion] });
  }
  return { modelo, error, mensaje, enviando, pendiente, conflicto, desactualizado, recargar, cambiar, reintentar: () => pendiente && enviar(pendiente) };
}
