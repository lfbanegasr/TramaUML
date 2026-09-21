import { useEffect, useState } from 'react';
import { exportarXmi, descargarXmi, type ResultadoXmi } from '../api/xmiApi';
import { listarVersiones, type ResumenVersion } from '../../historial/api/historialApi';
import type { Paquete } from '../../modelado/api/modeladoApi';

type Estado = 'idle' | 'exportando' | 'descargando' | 'completado' | 'error';

export function ExportarXmiDialog({
  modelo,
  nombreModelo,
  versionVigente,
  paquetes,
  cerrar,
}: {
  modelo: string;
  nombreModelo: string;
  versionVigente: string;
  paquetes: Paquete[];
  cerrar: () => void;
}) {
  const [descripcion, setDescripcion] = useState('');
  const [estado, setEstado] = useState<Estado>('idle');
  const [resultado, setResultado] = useState<ResultadoXmi | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [versiones, setVersiones] = useState<ResumenVersion[]>([]);
  const [version, setVersion] = useState(versionVigente);
  const [paquete, setPaquete] = useState('');
  const [incluirSubpaquetes, setIncluirSubpaquetes] = useState(true);

  useEffect(() => {
    listarVersiones(modelo, undefined, 100)
      .then((pagina) => setVersiones(pagina.versiones))
      .catch(() => setVersiones([]));
  }, [modelo]);

  async function exportar() {
    setEstado('exportando');
    setError(null);
    try {
      const res = await exportarXmi(modelo, {
        idSolicitud: crypto.randomUUID(),
        descripcion: descripcion || undefined,
        idVersion: version,
        idPaquete: paquete || undefined,
        incluirSubpaquetes,
      });
      setResultado(res);
      setEstado('descargando');
      await descargarXmi(modelo, res.idIntercambio, res.nombreArchivo);
      setEstado('completado');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al exportar XMI.');
      setEstado('error');
    }
  }

  const ocupado = estado === 'exportando' || estado === 'descargando';

  return (
    <div
      className="dialog-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="xmi-dialog-title"
    >
      <div className="dialog">
        <div className="dialog-header">
          <h2 id="xmi-dialog-title">Exportar XMI 2.1</h2>
          <button
            className="dialog-close"
            aria-label="Cerrar diálogo"
            onClick={cerrar}
            disabled={ocupado}
          >
            ×
          </button>
        </div>

        <div className="dialog-body">
          <p className="dialog-desc">
            Genera un archivo <strong>.xmi</strong> 2.1 interoperable para el modelo{' '}
            <em>{nombreModelo}</em>. Puedes congelar una versión y limitar el alcance a un paquete.
          </p>

          {estado === 'idle' || estado === 'error' ? (
            <div className="xmi-export-options">
              <div className="form-group">
                <label htmlFor="xmi-version">Versión del modelo</label>
                <select
                  id="xmi-version"
                  value={version}
                  onChange={(e) => setVersion(e.target.value)}
                >
                  {versiones.length ? (
                    versiones.map((v) => (
                      <option key={v.id} value={v.id}>
                        Versión {v.numeroVersion} · revisión {v.revision}
                      </option>
                    ))
                  ) : (
                    <option value={versionVigente}>Versión vigente</option>
                  )}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="xmi-paquete">Alcance</label>
                <select
                  id="xmi-paquete"
                  value={paquete}
                  onChange={(e) => setPaquete(e.target.value)}
                >
                  <option value="">Modelo completo</option>
                  {paquetes.map((p) => (
                    <option key={p.id} value={p.id}>
                      Paquete {p.nombre}
                    </option>
                  ))}
                </select>
              </div>
              {paquete && (
                <label className="check-row">
                  <input
                    type="checkbox"
                    checked={incluirSubpaquetes}
                    onChange={(e) => setIncluirSubpaquetes(e.target.checked)}
                  />
                  Incluir subpaquetes
                </label>
              )}
              <div className="form-group">
                <label htmlFor="xmi-descripcion">Descripción del intercambio (opcional)</label>
                <input
                  id="xmi-descripcion"
                  type="text"
                  maxLength={500}
                  placeholder="Ej: Versión para revisión del arquitecto"
                  value={descripcion}
                  onChange={(e) => setDescripcion(e.target.value)}
                />
              </div>
            </div>
          ) : null}

          {estado === 'exportando' && (
            <p className="dialog-status">
              <span className="spinner" aria-hidden="true" /> Generando XMI…
            </p>
          )}
          {estado === 'descargando' && (
            <p className="dialog-status">
              <span className="spinner" aria-hidden="true" /> Descargando archivo…
            </p>
          )}
          {estado === 'completado' && resultado && (
            <div className="dialog-success" role="status">
              <p>✓ Exportación completada</p>
              <ul className="dialog-stats">
                <li>
                  <strong>{resultado.totalClases}</strong> clases exportadas
                </li>
                <li>
                  <strong>{resultado.totalRelaciones}</strong> relaciones exportadas
                </li>
                <li>
                  <strong>{resultado.totalPaquetes}</strong> paquetes exportados
                </li>
                <li>
                  Archivo: <code>{resultado.nombreArchivo}</code>
                </li>
              </ul>
              {resultado.advertencias.length > 0 && (
                <div className="xmi-warnings">
                  <strong>Advertencias de interoperabilidad</strong>
                  <ul>
                    {resultado.advertencias.map((advertencia) => (
                      <li key={advertencia}>{advertencia}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
          {estado === 'error' && error && (
            <p className="dialog-error" role="alert">
              {error}
            </p>
          )}
        </div>

        <div className="dialog-footer">
          <button onClick={cerrar} disabled={ocupado}>
            {estado === 'completado' ? 'Cerrar' : 'Cancelar'}
          </button>
          {estado !== 'completado' && (
            <button
              id="btn-confirmar-exportar-xmi"
              className="primary"
              onClick={exportar}
              disabled={ocupado}
            >
              {ocupado ? 'Exportando…' : 'Exportar y descargar'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
