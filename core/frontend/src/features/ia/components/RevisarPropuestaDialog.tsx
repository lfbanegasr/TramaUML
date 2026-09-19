import { useState } from 'react';
import {
  aplicarPropuesta,
  rechazarPropuesta,
  type OperacionIa,
  type PropuestaIa,
  type ResultadoAplicacionIa,
} from '../api/iaApi';

const ETIQUETAS: Record<string, string> = {
  CLASE: 'Clase',
  ATRIBUTO: 'Atributo',
  OPERACION: 'Operación',
  RELACION: 'Relación',
  PAQUETE: 'Paquete',
  CREAR: 'Crear',
  ACTUALIZAR: 'Actualizar',
  ELIMINAR: 'Eliminar',
};

function descripcion(operacion: OperacionIa) {
  const valor = operacion.valorPropuesto ?? operacion.valorActual ?? {};
  const nombre = typeof valor.nombre === 'string' ? valor.nombre : '';
  const tipo = typeof valor.tipoUml === 'string' ? valor.tipoUml : '';
  return `${ETIQUETAS[operacion.tipoOperacion]} ${ETIQUETAS[operacion.tipoEntidad]}${nombre ? ` · ${nombre}` : ''}${tipo ? `: ${tipo}` : ''}`;
}

export function RevisarPropuestaDialog({
  modelo,
  propuesta,
  cerrar,
  alAplicar,
  alRechazar,
}: {
  modelo: string;
  propuesta: PropuestaIa;
  cerrar: () => void;
  alAplicar: (resultado: ResultadoAplicacionIa) => Promise<void>;
  alRechazar: () => Promise<void>;
}) {
  const [rechazando, setRechazando] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState('');
  const desactualizada =
    propuesta.estado === 'DESACTUALIZADO' || propuesta.revisionBase !== propuesta.revisionActual;
  const aplicable = propuesta.estado === 'LISTO' && !desactualizada;

  async function aplicar() {
    setProcesando(true);
    setError('');
    try {
      const resultado = await aplicarPropuesta(modelo, propuesta.id);
      await alAplicar(resultado);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setProcesando(false);
    }
  }

  async function rechazar() {
    setProcesando(true);
    setError('');
    try {
      await rechazarPropuesta(modelo, propuesta.id, motivo.trim());
      await alRechazar();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setProcesando(false);
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <section
        className="dialog dialog-wide ia-review-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ia-review-title"
      >
        <div className="dialog-header">
          <div>
            <span className="ia-kicker">PROPUESTA DEL ASISTENTE</span>
            <h2 id="ia-review-title">Revisar cambios interpretados</h2>
          </div>
          <button
            className="dialog-close"
            aria-label="Cerrar propuesta"
            disabled={procesando}
            onClick={cerrar}
          >
            ×
          </button>
        </div>
        <div className="dialog-body">
          <div className="ia-proposal-summary">
            <div className="ia-confidence" aria-label="Confianza general">
              <strong>{Math.round(Number(propuesta.confianzaGeneral) * 100)}%</strong>
              <span>confianza</span>
            </div>
            <div>
              <p>{propuesta.resumen}</p>
              <small>
                Entrada por {propuesta.tipoEntrada.toLowerCase()} · revisión base{' '}
                {propuesta.revisionBase}
              </small>
            </div>
          </div>
          {desactualizada && (
            <p className="ia-stale-warning" role="alert">
              El modelo avanzó a la revisión {propuesta.revisionActual}. Esta propuesta se conserva
              para auditoría, pero ya no puede aplicarse.
            </p>
          )}
          <div className="ia-operation-list">
            {propuesta.operaciones.map((operacion) => (
              <details className="ia-operation" key={operacion.id}>
                <summary>
                  <span className={'ia-operation-kind ' + operacion.tipoOperacion.toLowerCase()}>
                    {operacion.tipoOperacion}
                  </span>
                  <span>{descripcion(operacion)}</span>
                  <span className="ia-operation-confidence">
                    {Math.round(Number(operacion.confianza) * 100)}%
                  </span>
                </summary>
                <div>
                  <p>{operacion.justificacion}</p>
                  {operacion.dependencias.length > 0 && (
                    <small>
                      Requiere {operacion.dependencias.length}{' '}
                      {operacion.dependencias.length === 1
                        ? 'operación previa'
                        : 'operaciones previas'}
                    </small>
                  )}
                  {!operacion.seleccionada && (
                    <small className="ia-operation-omitted">
                      No se aplicará: {operacion.estado}
                    </small>
                  )}
                </div>
              </details>
            ))}
          </div>
          {rechazando && (
            <div className="ia-reject-box">
              <label className="ia-prompt-label" htmlFor="ia-reject-reason">
                Motivo del rechazo
              </label>
              <textarea
                id="ia-reject-reason"
                rows={3}
                maxLength={1000}
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Ej.: La cardinalidad interpretada no corresponde al dominio."
              />
            </div>
          )}
          {error && (
            <p className="dialog-error" role="alert">
              {error}
            </p>
          )}
        </div>
        <div className="dialog-footer">
          <button disabled={procesando} onClick={cerrar}>
            Cerrar
          </button>
          {propuesta.estado === 'LISTO' &&
            (rechazando ? (
              <>
                <button disabled={procesando} onClick={() => setRechazando(false)}>
                  Volver
                </button>
                <button
                  className="danger"
                  disabled={procesando || !motivo.trim()}
                  onClick={rechazar}
                >
                  {procesando ? 'Rechazando…' : 'Confirmar rechazo'}
                </button>
              </>
            ) : (
              <>
                <button
                  className="danger"
                  disabled={procesando}
                  onClick={() => setRechazando(true)}
                >
                  Rechazar
                </button>
                <button className="primary" disabled={procesando || !aplicable} onClick={aplicar}>
                  {procesando ? 'Aplicando…' : 'Aplicar propuesta'}
                </button>
              </>
            ))}
        </div>
      </section>
    </div>
  );
}
