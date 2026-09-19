import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api';
import { previsualizarRestauracion, restaurarVersion, type VistaPrevia } from '../api/historialApi';

export function RestauracionDialog({
  modelo,
  idVersionOrigen,
  numeroVersion,
  revisionVigente,
  cerrar,
  exito,
}: {
  modelo: string;
  idVersionOrigen: string;
  numeroVersion: number;
  revisionVigente: number;
  cerrar: () => void;
  exito: (revision: number, version: string) => void;
}) {
  const [vista, setVista] = useState<VistaPrevia | null>(null);
  const [error, setError] = useState('');
  const [motivo, setMotivo] = useState('');
  const [confirmado, setConfirmado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  // UUID idempotente persistido para reintentos
  const idSolicitud = useRef<string>(crypto.randomUUID());

  useEffect(() => {
    let activo = true;
    // Recuperar UUID guardado de intento previo
    const guardado = sessionStorage.getItem('restauracion_solicitud_' + idVersionOrigen);
    if (guardado) idSolicitud.current = guardado;
    previsualizarRestauracion(modelo, idVersionOrigen)
      .then((v) => {
        if (activo) setVista(v);
      })
      .catch((e: ApiError) => {
        if (activo) setError(e.message);
      });
    return () => {
      activo = false;
    };
  }, [modelo, idVersionOrigen]);

  async function confirmar() {
    if (!motivo.trim()) {
      setError('El motivo es obligatorio.');
      return;
    }
    setEnviando(true);
    setError('');
    // Persistir UUID para idempotencia ante recarga
    sessionStorage.setItem('restauracion_solicitud_' + idVersionOrigen, idSolicitud.current);
    try {
      const r = await restaurarVersion(
        modelo,
        idSolicitud.current,
        idVersionOrigen,
        revisionVigente,
        motivo,
      );
      sessionStorage.removeItem('restauracion_solicitud_' + idVersionOrigen);
      exito(r.revision, r.version);
    } catch (e: unknown) {
      setError((e as ApiError).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="panel-overlay" role="dialog" aria-modal="true" aria-label="Restaurar versión">
      <div className="panel-box">
        <div className="panel-head">
          <h2>Restaurar versión {numeroVersion}</h2>
          <button className="text-button" onClick={cerrar} disabled={enviando}>
            ×
          </button>
        </div>
        {!vista ? (
          error ? (
            <p className="error">{error}</p>
          ) : (
            <p className="muted">Comprobando bloqueadores…</p>
          )
        ) : (
          <>
            {vista.bloqueadores.length > 0 ? (
              <div className="error" role="alert">
                <b>No es posible restaurar ahora:</b>
                <ul>
                  {vista.bloqueadores.map((b, i) => (
                    <li key={i}>{b}</li>
                  ))}
                </ul>
              </div>
            ) : (
              <>
                <p>
                  Se creará una nueva versión del modelo replicando el estado de la{' '}
                  <b>versión {numeroVersion}</b> ({vista.elementosEstimados} elementos). La versión
                  actual (rev. {vista.revisionVigente}) quedará en el historial.
                </p>
                {error && (
                  <p className="error" role="alert">
                    {error}
                  </p>
                )}
                <label htmlFor="motivo-restauracion">
                  Motivo de la restauración <span className="required">*</span>
                  <textarea
                    id="motivo-restauracion"
                    rows={3}
                    value={motivo}
                    disabled={enviando}
                    onChange={(e) => setMotivo(e.target.value)}
                    placeholder="Describe brevemente por qué restauras esta versión… Explica por qué restauras el modelo."
                  />
                </label>
                <label className="checkbox-row" htmlFor="confirmar-restauracion">
                  <input
                    type="checkbox"
                    id="confirmar-restauracion"
                    checked={confirmado}
                    disabled={enviando}
                    onChange={(e) => setConfirmado(e.target.checked)}
                  />
                  Entiendo que esta acción reemplazará el modelo actual y quedará registrada.
                </label>
                <div className="panel-actions">
                  <button onClick={cerrar} disabled={enviando}>
                    Cancelar
                  </button>
                  <button
                    id="btn-confirmar-restauracion"
                    className="primary"
                    disabled={!confirmado || enviando || !motivo.trim()}
                    onClick={confirmar}
                    aria-label="Confirmar restauración"
                  >
                    {enviando ? 'Restaurando…' : 'Restaurar'}
                  </button>
                </div>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
