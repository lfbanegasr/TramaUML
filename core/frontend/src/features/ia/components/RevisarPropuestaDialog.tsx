import { useEffect, useState } from 'react';
import {
  aplicarPropuesta,
  rechazarPropuesta,
  revisarPropuesta,
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

type EdicionOperacion = { seleccionada: boolean; valor: string };

function descripcion(operacion: OperacionIa) {
  const valor = operacion.valorPropuesto ?? operacion.valorActual ?? {};
  const nombre = typeof valor.nombre === 'string' ? valor.nombre : '';
  const tipo = typeof valor.tipoUml === 'string' ? valor.tipoUml : '';
  return `${ETIQUETAS[operacion.tipoOperacion]} ${ETIQUETAS[operacion.tipoEntidad]}${nombre ? ` · ${nombre}` : ''}${tipo ? `: ${tipo}` : ''}`;
}

function ediciones(propuesta: PropuestaIa): Record<string, EdicionOperacion> {
  return Object.fromEntries(
    propuesta.operaciones.map((operacion) => [
      operacion.id,
      {
        seleccionada: operacion.seleccionada,
        valor: JSON.stringify(operacion.valorPropuesto, null, 2),
      },
    ]),
  );
}

function mensajes(operacion: OperacionIa): string[] {
  if (!Array.isArray(operacion.mensajesValidacion)) return [];
  return operacion.mensajesValidacion
    .filter(
      (mensaje): mensaje is { tipo: string; mensaje: string } =>
        typeof mensaje === 'object' &&
        mensaje !== null &&
        'tipo' in mensaje &&
        'mensaje' in mensaje &&
        mensaje.tipo === 'ERROR' &&
        typeof mensaje.mensaje === 'string',
    )
    .map((mensaje) => mensaje.mensaje);
}

export function RevisarPropuestaDialog({
  modelo,
  propuesta,
  soloLectura,
  cerrar,
  alActualizar,
  alAplicar,
  alRechazar,
}: {
  modelo: string;
  propuesta: PropuestaIa;
  soloLectura: boolean;
  cerrar: () => void;
  alActualizar: (propuesta: PropuestaIa) => void;
  alAplicar: (resultado: ResultadoAplicacionIa) => Promise<void>;
  alRechazar: () => Promise<void>;
}) {
  const [actual, setActual] = useState(propuesta);
  const [cambios, setCambios] = useState(() => ediciones(propuesta));
  const [modificada, setModificada] = useState(false);
  const [rechazando, setRechazando] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setActual(propuesta);
    setCambios(ediciones(propuesta));
    setModificada(false);
  }, [propuesta]);

  const desactualizada =
    actual.estado === 'DESACTUALIZADO' || actual.revisionBase !== actual.revisionActual;
  const aplicable =
    actual.estado === 'LISTO' && !desactualizada && actual.puedeAplicar && !modificada;

  function cambiar(id: string, cambio: Partial<EdicionOperacion>) {
    setCambios((estado) => ({ ...estado, [id]: { ...estado[id], ...cambio } }));
    setModificada(true);
  }

  async function guardar() {
    setProcesando(true);
    setError('');
    try {
      const operaciones = actual.operaciones.map((operacion) => {
        const edicion = cambios[operacion.id];
        let valorPropuesto: Record<string, unknown> | null;
        try {
          valorPropuesto = JSON.parse(edicion.valor) as Record<string, unknown> | null;
        } catch {
          throw new Error(
            `Los datos de la operación ${operacion.numeroSecuencia} no son JSON válido.`,
          );
        }
        return { id: operacion.id, seleccionada: edicion.seleccionada, valorPropuesto };
      });
      const revisada = await revisarPropuesta(modelo, actual, operaciones);
      setActual(revisada);
      setCambios(ediciones(revisada));
      setModificada(false);
      alActualizar(revisada);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setProcesando(false);
    }
  }

  async function aplicar() {
    setProcesando(true);
    setError('');
    try {
      const resultado = await aplicarPropuesta(modelo, actual.id);
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
      await rechazarPropuesta(modelo, actual.id, motivo.trim());
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
              <strong>{Math.round(Number(actual.confianzaGeneral) * 100)}%</strong>
              <span>confianza</span>
            </div>
            <div>
              <p>{actual.resumen}</p>
              <small>
                Entrada por {actual.tipoEntrada.toLowerCase()} · revisión base {actual.revisionBase}
              </small>
            </div>
          </div>
          {desactualizada && (
            <p className="ia-stale-warning" role="alert">
              El modelo avanzó a la revisión {actual.revisionActual}. Esta propuesta se conserva
              para auditoría, pero ya no puede aplicarse.
            </p>
          )}
          <p className="dialog-desc">
            Selecciona los cambios deseados. Puedes corregir sus datos JSON y revalidarlos antes de
            aplicar; el modelo no cambia durante esta revisión.
          </p>
          <div className="ia-operation-list">
            {actual.operaciones.map((operacion) => (
              <details
                className={'ia-operation' + (operacion.estado === 'INVALIDO' ? ' invalid' : '')}
                key={operacion.id}
              >
                <summary>
                  <input
                    aria-label={`Seleccionar operación ${operacion.numeroSecuencia}`}
                    type="checkbox"
                    checked={cambios[operacion.id]?.seleccionada ?? false}
                    disabled={procesando || desactualizada || soloLectura}
                    onClick={(evento) => evento.stopPropagation()}
                    onChange={(evento) =>
                      cambiar(operacion.id, { seleccionada: evento.target.checked })
                    }
                  />
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
                  {mensajes(operacion).map((mensaje) => (
                    <small className="ia-operation-omitted" key={mensaje}>
                      {mensaje}
                    </small>
                  ))}
                  {operacion.valorActual && (
                    <details className="ia-current-value">
                      <summary>Ver valor actual</summary>
                      <pre>{JSON.stringify(operacion.valorActual, null, 2)}</pre>
                    </details>
                  )}
                  <label htmlFor={`ia-operation-${operacion.id}`}>Datos propuestos</label>
                  <textarea
                    id={`ia-operation-${operacion.id}`}
                    aria-label={`Datos propuestos de operación ${operacion.numeroSecuencia}`}
                    rows={7}
                    spellCheck={false}
                    value={cambios[operacion.id]?.valor ?? 'null'}
                    disabled={procesando || desactualizada || soloLectura}
                    onChange={(evento) => cambiar(operacion.id, { valor: evento.target.value })}
                  />
                </div>
              </details>
            ))}
          </div>
          {modificada && (
            <p className="ia-stale-warning" role="status">
              Hay cambios sin revalidar. Guárdalos antes de aplicar la propuesta.
            </p>
          )}
          {soloLectura && (
            <p className="dialog-desc">
              Tu rol permite consultar esta propuesta, pero no corregirla, aplicarla ni rechazarla.
            </p>
          )}
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
          {!soloLectura &&
            actual.estado === 'LISTO' &&
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
                <button disabled={procesando || desactualizada || !modificada} onClick={guardar}>
                  {procesando ? 'Validando…' : 'Guardar y revalidar'}
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
