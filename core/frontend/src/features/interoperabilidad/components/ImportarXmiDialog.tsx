import { useRef, useState } from 'react';
import {
  aplicarImportacionXmi,
  cancelarImportacionXmi,
  importarXmi,
  type ImportacionXmi,
} from '../api/xmiApi';

type Estado = 'seleccion' | 'analizando' | 'revision' | 'aplicando' | 'completado' | 'error';

export function ImportarXmiDialog({
  modelo,
  sesion,
  cerrar,
  alAplicar,
}: {
  modelo: string;
  sesion: string;
  cerrar: () => void;
  alAplicar: () => Promise<void>;
}) {
  const [archivo, setArchivo] = useState<File | null>(null);
  const [estrategia, setEstrategia] = useState<'FUSIONAR' | 'REEMPLAZAR'>('FUSIONAR');
  const [estado, setEstado] = useState<Estado>('seleccion');
  const [resultado, setResultado] = useState<ImportacionXmi | null>(null);
  const [error, setError] = useState('');
  const [arrastrando, setArrastrando] = useState(false);
  const solicitudCarga = useRef(crypto.randomUUID());
  const solicitudAplicacion = useRef(crypto.randomUUID());

  function seleccionar(seleccionado?: File) {
    setError('');
    if (!seleccionado) return;
    if (!/\.(xmi|xml)$/i.test(seleccionado.name)) {
      setArchivo(null);
      setError('Selecciona un archivo con extensión .xmi o .xml.');
      return;
    }
    if (seleccionado.size === 0 || seleccionado.size > 5 * 1024 * 1024) {
      setArchivo(null);
      setError('Selecciona un archivo con contenido de hasta 5 MB.');
      return;
    }
    setArchivo(seleccionado);
    solicitudCarga.current = crypto.randomUUID();
    solicitudAplicacion.current = crypto.randomUUID();
  }

  async function analizar() {
    if (!archivo || ocupado) return;
    setEstado('analizando');
    setError('');
    try {
      const importacion = await importarXmi(modelo, archivo, estrategia, solicitudCarga.current);
      setResultado(importacion);
      if (importacion.estado === 'FALLIDO') {
        setError(importacion.detalleError ?? 'No se pudo interpretar el archivo XMI.');
        setEstado('error');
      } else setEstado('revision');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo analizar el archivo XMI.');
      setEstado('error');
    }
  }

  async function aplicar() {
    if (!resultado || ocupado || !resultado.puedeAplicar) return;
    setEstado('aplicando');
    setError('');
    try {
      await aplicarImportacionXmi(
        modelo,
        resultado.idIntercambio,
        sesion,
        resultado.revisionBase,
        solicitudAplicacion.current,
      );
      setEstado('completado');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo aplicar la importación.');
      setEstado('revision');
      return;
    }
    try {
      await alAplicar();
    } catch {
      setError(
        'La importación se guardó. No se pudo refrescar el lienzo; vuelve a abrir el modelo para verla.',
      );
    }
  }

  async function cancelar() {
    if (estado !== 'completado' && resultado?.estado === 'LISTO_PARA_REVISION') {
      try {
        await cancelarImportacionXmi(modelo, resultado.idIntercambio);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'No se pudo cancelar la importación.');
        return;
      }
    }
    cerrar();
  }

  async function volver() {
    setError('');
    if (resultado?.estado === 'LISTO_PARA_REVISION') {
      setEstado('analizando');
      try {
        await cancelarImportacionXmi(modelo, resultado.idIntercambio);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'No se pudo cerrar la previsualización.');
        setEstado('revision');
        return;
      }
    }
    setResultado(null);
    solicitudCarga.current = crypto.randomUUID();
    solicitudAplicacion.current = crypto.randomUUID();
    setEstado('seleccion');
  }

  const ocupado = estado === 'analizando' || estado === 'aplicando';

  return (
    <div
      className="dialog-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="importar-xmi-title"
    >
      <div className="dialog dialog-wide xmi-import-dialog">
        <div className="dialog-header">
          <div>
            <span className="eyebrow">INTEROPERABILIDAD</span>
            <h2 id="importar-xmi-title">Importar XMI de Enterprise Architect</h2>
          </div>
          <button
            className="dialog-close"
            aria-label="Cerrar diálogo"
            onClick={cancelar}
            disabled={ocupado}
          >
            ×
          </button>
        </div>
        <div className="dialog-body">
          <ol className="workflow-steps" aria-label="Pasos de importación">
            {['Seleccionar archivo', 'Revisar cambios', 'Aplicar y guardar versión'].map(
              (paso, i) => (
                <li
                  key={paso}
                  aria-current={
                    (i === 0 && !resultado) ||
                    (i === 1 && !!resultado && estado !== 'completado') ||
                    (i === 2 && estado === 'completado')
                      ? 'step'
                      : undefined
                  }
                >
                  {paso}
                </li>
              ),
            )}
          </ol>
          {(estado === 'seleccion' || estado === 'error') && !resultado && (
            <>
              <p className="dialog-desc">
                El archivo se analizará y mostrará como una previsualización. No se modificará el
                modelo hasta que confirmes su aplicación.
              </p>
              <label
                className={'xmi-drop-zone' + (arrastrando ? ' dragging' : '')}
                onDragOver={(e) => {
                  e.preventDefault();
                  setArrastrando(true);
                }}
                onDragLeave={() => setArrastrando(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setArrastrando(false);
                  seleccionar(e.dataTransfer.files[0]);
                }}
              >
                <input
                  type="file"
                  accept=".xmi,.xml,application/xml,text/xml"
                  onChange={(e) => seleccionar(e.target.files?.[0])}
                />
                <strong>{archivo ? archivo.name : 'Arrastra o selecciona un archivo XMI'}</strong>
                <span>{archivo ? `${Math.ceil(archivo.size / 1024)} KB` : 'Máximo 5 MB'}</span>
              </label>
              <div className="form-group">
                <label htmlFor="xmi-estrategia">Estrategia de importación</label>
                <select
                  id="xmi-estrategia"
                  value={estrategia}
                  onChange={(e) => {
                    setEstrategia(e.target.value as 'FUSIONAR' | 'REEMPLAZAR');
                    solicitudCarga.current = crypto.randomUUID();
                  }}
                >
                  <option value="FUSIONAR">Fusionar con el modelo actual</option>
                  <option value="REEMPLAZAR">Reemplazar el contenido actual</option>
                </select>
              </div>
              {estrategia === 'REEMPLAZAR' && (
                <p className="xmi-destructive-warning">
                  Reemplazar propondrá eliminar los elementos vigentes. La operación seguirá siendo
                  atómica y quedará registrada como una nueva versión.
                </p>
              )}
            </>
          )}

          {estado === 'analizando' && (
            <p className="dialog-status" role="status">
              <span className="spinner" aria-hidden="true" /> Analizando estructura XMI…
            </p>
          )}

          {resultado && estado !== 'completado' && (
            <div className="xmi-preview">
              <div className="xmi-summary" role="status">
                <strong>{resultado.nombreArchivo}</strong>
                <span>
                  {resultado.totalPaquetes} paquetes · {resultado.totalClases} clases ·{' '}
                  {resultado.totalAtributos} atributos · {resultado.totalOperaciones} operaciones ·{' '}
                  {resultado.totalRelaciones} relaciones
                </span>
                <span>
                  Estrategia {resultado.estrategia.toLowerCase()} · revisión base{' '}
                  {resultado.revisionBase}
                  {resultado.versionEnterpriseArchitect
                    ? ` · Enterprise Architect ${resultado.versionEnterpriseArchitect}`
                    : ''}
                </span>
              </div>
              {resultado.advertencias.length > 0 && (
                <div className="xmi-warnings">
                  <strong>Advertencias</strong>
                  <ul>
                    {resultado.advertencias.map((mensaje) => (
                      <li key={mensaje}>{mensaje}</li>
                    ))}
                  </ul>
                </div>
              )}
              {resultado.bloqueadores.length > 0 && (
                <div className="dialog-error" role="alert">
                  <strong>La importación no puede aplicarse</strong>
                  <ul>
                    {resultado.bloqueadores.map((mensaje) => (
                      <li key={mensaje}>{mensaje}</li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="xmi-operation-list" aria-label="Operaciones de importación">
                {resultado.operaciones.map((operacion) => (
                  <div className="xmi-operation" key={`${operacion.orden}-${operacion.idEntidad}`}>
                    <span className={`operation-kind ${operacion.operacion.toLowerCase()}`}>
                      {operacion.operacion}
                    </span>
                    <span>
                      {operacion.entidad} <strong>{operacion.nombre}</strong>
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {estado === 'aplicando' && (
            <p className="dialog-status" role="status">
              <span className="spinner" aria-hidden="true" /> Aplicando una revisión atómica…
            </p>
          )}
          {estado === 'completado' && (
            <div className="dialog-success" role="status">
              <p>✓ Importación aplicada y versionada</p>
              <span>El lienzo se actualizó con el contenido confirmado.</span>
            </div>
          )}
          {error && (
            <p className="dialog-error" role="alert">
              {error}
            </p>
          )}
        </div>
        <div className="dialog-footer">
          <button onClick={estado === 'completado' ? cerrar : cancelar} disabled={ocupado}>
            {estado === 'completado' ? 'Cerrar' : resultado ? 'Cancelar importación' : 'Cancelar'}
          </button>
          {!resultado && estado !== 'completado' && (
            <button className="primary" onClick={analizar} disabled={!archivo || ocupado}>
              {estado === 'analizando' ? 'Analizando…' : 'Analizar archivo'}
            </button>
          )}
          {resultado && estado !== 'completado' && (
            <>
              <button onClick={() => void volver()} disabled={ocupado}>
                Cambiar archivo o estrategia
              </button>
              <button
                className="primary"
                onClick={aplicar}
                disabled={!resultado.puedeAplicar || ocupado}
              >
                {estado === 'aplicando' ? 'Aplicando…' : 'Aplicar importación'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
