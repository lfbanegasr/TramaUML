import { useEffect, useState } from 'react';
import { consultarSesiones, type SesionResumen, type PaginaSesiones } from '../api/accesoApi';

function estadoSesion(s: SesionResumen): { etiqueta: string; clase: string } {
  if (s.activa) return { etiqueta: 'Activa', clase: 'badge active' };
  if (s.revocadoEn) {
    const motivo = s.motivoRevocacion ?? 'REVOCADA';
    const etiqueta =
      motivo === 'CIERRE_USUARIO'
        ? 'Cerrada por usuario'
        : motivo === 'CIERRE_ADMINISTRADOR'
          ? 'Cerrada por admin'
          : motivo.replaceAll('_', ' ').toLowerCase();
    return { etiqueta, clase: 'badge' };
  }
  return { etiqueta: 'Expirada', clase: 'badge' };
}

export function SesionesPanel({ cerrar }: { cerrar: () => void }) {
  const [datos, setDatos] = useState<PaginaSesiones | null>(null);
  const [pagina, setPagina] = useState(0);
  const [error, setError] = useState('');
  const [cargando, setCargando] = useState(true);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    let activo = true;
    setCargando(true);
    setError('');
    consultarSesiones(pagina)
      .then((d) => {
        if (activo) setDatos(d);
      })
      .catch((e: Error) => {
        if (activo) setError(e.message);
      })
      .finally(() => {
        if (activo) setCargando(false);
      });
    return () => {
      activo = false;
    };
  }, [pagina, recarga]);

  return (
    <div className="modal-backdrop">
      <section
        className="modal modal-wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sesiones-titulo"
      >
        <div className="panel-heading">
          <div>
            <span className="eyebrow">SEGURIDAD</span>
            <h2 id="sesiones-titulo">Historial de sesiones</h2>
            <p className="muted">Revisa cuándo y desde dónde se ha accedido a tu cuenta.</p>
          </div>
          <button onClick={cerrar}>Cerrar</button>
        </div>
        {error && (
          <div className="error" role="alert">
            {error}
            <button onClick={() => setRecarga((r) => r + 1)}>Reintentar</button>
          </div>
        )}
        {cargando ? (
          <p className="muted">Consultando sesiones…</p>
        ) : (
          datos && (
            <>
              <p className="muted">
                {datos.total} sesión{datos.total !== 1 ? 'es' : ''} registrada
                {datos.total !== 1 ? 's' : ''} · las más recientes primero
              </p>
              {datos.sesiones.length === 0 ? (
                <p className="empty">No hay sesiones registradas.</p>
              ) : (
                <div className="table-scroll">
                  <table className="management-table">
                    <thead>
                      <tr>
                        <th>Inicio</th>
                        <th>Expira / Cerrada</th>
                        <th>Estado</th>
                        <th>IP</th>
                        <th>Agente</th>
                      </tr>
                    </thead>
                    <tbody>
                      {datos.sesiones.map((s) => {
                        const est = estadoSesion(s);
                        return (
                          <tr key={s.id}>
                            <td>{new Date(s.emitidoEn).toLocaleString()}</td>
                            <td>
                              {s.revocadoEn
                                ? new Date(s.revocadoEn).toLocaleString()
                                : s.expiraEn
                                  ? new Date(s.expiraEn).toLocaleString()
                                  : '—'}
                            </td>
                            <td>
                              <span className={est.clase}>{est.etiqueta}</span>
                            </td>
                            <td>{s.direccionIp ?? '—'}</td>
                            <td>
                              <small className="entity-id">
                                {s.agenteCliente
                                  ? s.agenteCliente.slice(0, 60) +
                                    (s.agenteCliente.length > 60 ? '…' : '')
                                  : '—'}
                              </small>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
              {datos.total > datos.tamano && (
                <div className="pagination">
                  <button disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>
                    Anterior
                  </button>
                  <span>Página {pagina + 1}</span>
                  <button
                    disabled={(pagina + 1) * datos.tamano >= datos.total}
                    onClick={() => setPagina((p) => p + 1)}
                  >
                    Siguiente
                  </button>
                </div>
              )}
            </>
          )
        )}
      </section>
    </div>
  );
}
