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
  const [version, setVersion] = useState(versionVigente || '__ACTUAL__');
  const [paquete, setPaquete] = useState('');
  const [incluirSubpaquetes, setIncluirSubpaquetes] = useState(true);
  const [formato, setFormato] = useState<'XMI_251_EA' | 'XMI_251_OMG' | 'XMI_EA' | 'SCRIPT_VBS_EA' | 'XMI_OMG'>('XMI_251_EA');

  useEffect(() => {
    listarVersiones(modelo, undefined, 100)
      .then((pagina) => setVersiones(pagina.versiones))
      .catch(() => setVersiones([]));
  }, [modelo]);

  async function exportar() {
    setEstado('exportando');
    setError(null);
    try {
      const esActual = version === '__ACTUAL__' || !version;
      const res = await exportarXmi(modelo, {
        idSolicitud: crypto.randomUUID(),
        descripcion: descripcion || undefined,
        idVersion: esActual ? undefined : version,
        estadoActual: esActual,
        idPaquete: paquete || undefined,
        incluirSubpaquetes,
        formato,
      });
      setResultado(res);
      setEstado('descargando');
      await descargarXmi(modelo, res.idIntercambio, res.nombreArchivo);
      setEstado('completado');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al exportar.');
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
      <div className="dialog dialog-wide">
        <div className="dialog-header">
          <h2 id="xmi-dialog-title">Exportar para Enterprise Architect y UML</h2>
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
            Exporta el modelo <em>{nombreModelo}</em> para Enterprise Architect con clases,
            atributos, métodos, relaciones, multiplicidades y diagramas organizados.
          </p>

          {estado === 'idle' || estado === 'error' ? (
            <div className="xmi-export-options">
              <div className="form-group">
                <label htmlFor="xmi-formato">Formato de exportación</label>
                <select
                  id="xmi-formato"
                  value={formato}
                  onChange={(e) =>
                    setFormato(e.target.value as 'XMI_251_EA' | 'XMI_251_OMG' | 'XMI_EA' | 'SCRIPT_VBS_EA' | 'XMI_OMG')
                  }
                >
                  <option value="XMI_251_EA">UML 2.5.1 / XMI 2.5.1 con extensión EA (verificación visual pendiente)</option>
                  <option value="XMI_251_OMG">UML 2.5.1 / XMI 2.5.1 semántico (sin posiciones)</option>
                  <option value="XMI_EA">
                    Compatibilidad anterior: XMI 2.1 con extensión EA (con pérdidas)
                  </option>
                  <option value="SCRIPT_VBS_EA">
                    Script VBScript para Enterprise Architect (.vbs ejecutable en EA)
                  </option>
                  <option value="XMI_OMG">
                    Compatibilidad anterior: XMI 2.1 semántico (con pérdidas)
                  </option>
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="xmi-version">Versión o estado del modelo</label>
                <select
                  id="xmi-version"
                  value={version}
                  onChange={(e) => setVersion(e.target.value)}
                >
                  <option value="__ACTUAL__">
                    Estado actual de edición (Borrador en vivo / Tal cual está)
                  </option>
                  {versiones.map((v) => (
                    <option key={v.id} value={v.id}>
                      Versión {v.numeroVersion} · revisión {v.revision}
                    </option>
                  ))}
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
                  placeholder="Ej: Versión para revisión en Enterprise Architect"
                  value={descripcion}
                  onChange={(e) => setDescripcion(e.target.value)}
                />
              </div>

              <InstruccionesImportacion formato={formato} />
            </div>
          ) : null}

          {estado === 'exportando' && (
            <p className="dialog-status">
              <span className="spinner" aria-hidden="true" /> Generando exportación…
            </p>
          )}
          {estado === 'descargando' && (
            <p className="dialog-status">
              <span className="spinner" aria-hidden="true" /> Descargando archivo…
            </p>
          )}
          {estado === 'completado' && resultado && (
            <div className="dialog-success" role="status">
              <p>✓ Exportación completada y archivo descargado</p>
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

              <InstruccionesImportacion formato={formato} modoExito />
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

function InstruccionesImportacion({
  formato,
  modoExito = false,
}: {
  formato: 'XMI_251_EA' | 'XMI_251_OMG' | 'XMI_EA' | 'SCRIPT_VBS_EA' | 'XMI_OMG';
  modoExito?: boolean;
}) {
  return (
    <div className={`xmi-guide-card ${modoExito ? 'success-guide' : ''}`} role="note">
      {(formato === 'XMI_EA' || formato === 'XMI_251_EA') && (
        <>
          <div className="guide-title">
            <span>📋</span>{' '}
            <strong>
              {modoExito
                ? 'Siguientes pasos: ¿Cómo importar este archivo en Enterprise Architect?'
                : 'Instrucciones para importar el perfil seleccionado en Enterprise Architect:'}
            </strong>
          </div>
          <ol>
            <li>
              Abre <strong>Enterprise Architect</strong> con tu proyecto (o crea uno nuevo).
            </li>
            <li>
              En el panel izquierdo (<strong>Project Browser</strong>), haz clic derecho sobre el
              modelo o paquete raíz donde deseas importar.
            </li>
            <li>
              Selecciona: <strong>Publish</strong> ➔ <strong>Import XML</strong> ➔{' '}
              <strong>Import Package from XML</strong> (o presiona <code>Ctrl + Alt + I</code>).
            </li>
            <li>
              Selecciona el archivo <code>.xmi</code> descargado y{' '}
              <strong>asegúrate de marcar la casilla "Import Diagrams"</strong>.
            </li>
            <li>
              Haz clic en <strong>Import</strong>.
            </li>
            <li>
              En el Project Browser, despliega la carpeta con el nombre de tu modelo y haz{' '}
              <strong>doble clic en el diagrama de clases</strong>: revisa los elementos y la disposición importados. La compatibilidad visual de esta exportación con una instalación real de EA está pendiente de verificación.
            </li>
          </ol>
        </>
      )}

      {formato === 'SCRIPT_VBS_EA' && (
        <>
          <div className="guide-title">
            <span>📋</span>{' '}
            <strong>
              {modoExito
                ? 'Siguientes pasos: ¿Cómo ejecutar el Script en Enterprise Architect?'
                : 'Instrucciones para ejecutar el Script en Enterprise Architect (VBScript):'}
            </strong>
          </div>
          <ol>
            <li>
              Abre <strong>Enterprise Architect</strong> con tu proyecto.
            </li>
            <li>
              En el panel <strong>Project Browser</strong>, selecciona el paquete o modelo donde se
              creará el diagrama.
            </li>
            <li>
              Abre la consola de scripts desde el menú <strong>Tools</strong> ➔{' '}
              <strong>Scripting</strong> (o presiona <code>Ctrl + Alt + V</code>).
            </li>
            <li>
              Crea un nuevo script VBScript (o abre el archivo <code>.vbs</code> descargado con el
              Bloc de notas), copia todo el código y pégalo en el editor de EA.
            </li>
            <li>
              Haz clic en <strong>Run Script</strong> (botón ▶): el script creará el paquete,
              colocará las clases con sus multiplicidades y{' '}
              <strong>abrirá el diagrama automáticamente en pantalla</strong>.
            </li>
          </ol>
        </>
      )}

      {(formato === 'XMI_OMG' || formato === 'XMI_251_OMG') && (
        <>
          <div className="guide-title">
            <span>📋</span>{' '}
            <strong>
              {modoExito
                ? 'Siguientes pasos: ¿Cómo importar este archivo XMI estándar?'
                : 'Instrucciones para herramientas UML compatibles con OMG:'}
            </strong>
          </div>
          <ol>
            <li>
              Abre tu herramienta CASE compatible con el perfil UML/XMI seleccionado (StarUML, Visual Paradigm, MagicDraw,
              Eclipse Papyrus, etc.).
            </li>
            <li>
              Selecciona la opción <strong>Import XMI</strong> en el menú de archivo de la
              herramienta.
            </li>
            <li>
              Selecciona el archivo <code>.xmi</code> para cargar las clases, atributos, métodos y
              asociaciones estándar.
            </li>
          </ol>
        </>
      )}
    </div>
  );
}

