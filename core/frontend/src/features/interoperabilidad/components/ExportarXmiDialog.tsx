import { useState } from 'react';
import { exportarXmi, descargarXmi, type ResultadoXmi } from '../api/xmiApi';

type Estado = 'idle' | 'exportando' | 'descargando' | 'completado' | 'error';

export function ExportarXmiDialog({
  modelo,
  nombreModelo,
  cerrar,
}: {
  modelo: string;
  nombreModelo: string;
  cerrar: () => void;
}) {
  const [descripcion, setDescripcion] = useState('');
  const [estado, setEstado] = useState<Estado>('idle');
  const [resultado, setResultado] = useState<ResultadoXmi | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function exportar() {
    setEstado('exportando');
    setError(null);
    try {
      const res = await exportarXmi(modelo, {
        idSolicitud: crypto.randomUUID(),
        descripcion: descripcion || undefined,
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
            Genera un archivo <strong>.xmi</strong> compatible con Enterprise Architect 12-16+ para
            el modelo <em>{nombreModelo}</em>. El archivo incluye clases, atributos, operaciones,
            relaciones y paquetes.
          </p>

          {estado === 'idle' || estado === 'error' ? (
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
                  Archivo: <code>{resultado.nombreArchivo}</code>
                </li>
              </ul>
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
