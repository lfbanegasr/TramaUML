import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  descartarConflicto,
  listarConflictos,
  resolverConflicto,
  type Conflicto,
  type Diferencia,
} from '../api/conflictosApi';

type Decision = 'CONSERVAR_ALMACENADO' | 'APLICAR_ENTRANTE' | 'FUSIONAR' | 'DESCARTAR';

const etiquetas: Record<string, string> = {
  CONSERVAR_ALMACENADO: 'Conservar estado confirmado',
  APLICAR_ENTRANTE: 'Aplicar propuesta entrante',
  FUSIONAR: 'Fusionar propiedades compatibles',
  DESCARTAR: 'Descartar propuesta',
  $existencia: 'Existencia del elemento',
  $sin_cambios: 'Sin diferencias de propiedades',
};

function fecha(valor: string | null | undefined) {
  return valor ? new Date(valor).toLocaleString('es-BO') : 'Sin fecha';
}

function valor(v: unknown) {
  if (v === null || v === undefined) return 'No existe';
  if (typeof v === 'string') return v || 'Vacío';
  return JSON.stringify(v, null, 2);
}

function FilaDiferencia({ diferencia }: { diferencia: Diferencia }) {
  return (
    <tr className={diferencia.incompatible ? 'difference-incompatible' : ''}>
      <th scope="row">{etiquetas[diferencia.propiedad] ?? diferencia.propiedad}</th>
      <td>
        <pre>{valor(diferencia.valorBase)}</pre>
      </td>
      <td>
        <pre>{valor(diferencia.valorConfirmado)}</pre>
      </td>
      <td>
        <pre>{valor(diferencia.valorPropuesto)}</pre>
      </td>
    </tr>
  );
}

export function ConflictosPanel({
  modelo,
  sesion,
  conectado,
  senal,
  cerrar,
  cambiarTotal,
  resuelto,
}: {
  modelo: string;
  sesion: string | null;
  conectado: boolean;
  senal: number;
  cerrar: () => void;
  cambiarTotal: (total: number) => void;
  resuelto: () => Promise<unknown>;
}) {
  const [conflictos, setConflictos] = useState<Conflicto[]>([]);
  const [seleccionado, setSeleccionado] = useState<string | null>(null);
  const [decision, setDecision] = useState<Decision | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const solicitudes = useRef<Record<string, string>>({});

  const cargar = useCallback(async () => {
    try {
      const lista = await listarConflictos(modelo);
      setConflictos(lista.conflictos);
      cambiarTotal(lista.total);
      setSeleccionado((actual) =>
        actual && lista.conflictos.some((c) => c.id === actual)
          ? actual
          : (lista.conflictos[0]?.id ?? null),
      );
      setError('');
    } catch (e) {
      setError((e as Error).message);
    }
  }, [modelo, cambiarTotal]);

  useEffect(() => {
    void cargar();
  }, [cargar, senal]);

  const conflicto = useMemo(
    () => conflictos.find((c) => c.id === seleccionado) ?? null,
    [conflictos, seleccionado],
  );

  useEffect(() => {
    if (!conflicto) {
      setDecision(null);
      return;
    }
    const guardada = sessionStorage.getItem('uml-conflicto-decision-' + conflicto.id) as Decision;
    setDecision(
      ['CONSERVAR_ALMACENADO', 'APLICAR_ENTRANTE', 'FUSIONAR', 'DESCARTAR'].includes(guardada)
        ? guardada
        : null,
    );
    setConfirmando(false);
  }, [conflicto]);

  function elegir(nueva: Decision) {
    if (!conflicto) return;
    setDecision(nueva);
    sessionStorage.setItem('uml-conflicto-decision-' + conflicto.id, nueva);
    setConfirmando(false);
  }

  async function confirmar() {
    if (!conflicto || !decision || !sesion) return;
    setOcupado(true);
    setError('');
    const clave = conflicto.id + '|' + decision;
    const idSolicitudResolucion = solicitudes.current[clave] ?? crypto.randomUUID();
    solicitudes.current[clave] = idSolicitudResolucion;
    try {
      if (decision === 'DESCARTAR')
        await descartarConflicto(modelo, conflicto.id, {
          idSesionEdicion: sesion,
          idSolicitudResolucion,
          revisionEsperada: conflicto.revisionActual,
          confirmar: true,
        });
      else
        await resolverConflicto(modelo, conflicto.id, {
          idSesionEdicion: sesion,
          idSolicitudResolucion,
          revisionEsperada: conflicto.revisionActual,
          tipoResolucion: decision,
          confirmar: true,
        });
      delete solicitudes.current[clave];
      sessionStorage.removeItem('uml-conflicto-decision-' + conflicto.id);
      setDecision(null);
      setConfirmando(false);
      await cargar();
      await resuelto();
    } catch (e) {
      setError((e as Error).message);
      await cargar();
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="modal-backdrop conflict-backdrop">
      <section className="conflict-panel" role="dialog" aria-label="Conflictos del modelo">
        <header>
          <div>
            <span className="eyebrow">CU18 · REVISIÓN HUMANA</span>
            <h2>Conflictos del modelo</h2>
            <p>{conflictos.length} propuesta(s) pendiente(s)</p>
          </div>
          <button onClick={cerrar} aria-label="Cerrar conflictos">
            ×
          </button>
        </header>
        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}
        <div className="conflict-layout">
          <nav aria-label="Lista de conflictos">
            {conflictos.map((c) => (
              <button
                className={c.id === seleccionado ? 'current' : ''}
                key={c.id}
                onClick={() => setSeleccionado(c.id)}
              >
                <b>{c.tipoEntidad.toLowerCase()}</b>
                <span>
                  Base {c.revisionBase} · actual {c.revisionActual}
                </span>
                <small>{fecha(c.detectadoEn)}</small>
              </button>
            ))}
            {!conflictos.length && <p>No hay conflictos pendientes.</p>}
          </nav>
          <main>
            {conflicto ? (
              <>
                <div className="conflict-summary">
                  <div>
                    <span>Elemento</span>
                    <b>
                      {conflicto.tipoEntidad} · {conflicto.idEntidad ?? 'cambio compuesto'}
                    </b>
                  </div>
                  <div>
                    <span>Propuesta de</span>
                    <b>{conflicto.cambioEntrante?.autor.nombre ?? 'Autor no disponible'}</b>
                    <small>{fecha(conflicto.cambioEntrante?.creadoEn)}</small>
                  </div>
                  <div>
                    <span>Cambio confirmado</span>
                    <b>
                      {conflicto.cambioAlmacenado?.autor.nombre ?? 'Sin cambio causal identificado'}
                    </b>
                    <small>{fecha(conflicto.cambioAlmacenado?.creadoEn)}</small>
                  </div>
                </div>
                {conflicto.elementoEliminado && (
                  <div className="warning">
                    El elemento fue eliminado. Aplicar la propuesta intentará reactivarlo y validará
                    sus dependencias.
                  </div>
                )}
                <div className="comparison-scroll">
                  <table className="comparison-table">
                    <thead>
                      <tr>
                        <th>Propiedad</th>
                        <th>Base</th>
                        <th>Confirmado actual</th>
                        <th>Propuesto</th>
                      </tr>
                    </thead>
                    <tbody>
                      {conflicto.diferencias.map((d, i) => (
                        <FilaDiferencia diferencia={d} key={i} />
                      ))}
                    </tbody>
                  </table>
                </div>
                {conflicto.puedeResolver ? (
                  <div className="conflict-actions">
                    <p>
                      Selecciona una decisión. La revisión y tus permisos se comprobarán otra vez al
                      confirmar.
                    </p>
                    <div>
                      <button
                        className={decision === 'CONSERVAR_ALMACENADO' ? 'selected' : ''}
                        onClick={() => elegir('CONSERVAR_ALMACENADO')}
                      >
                        Conservar confirmado
                      </button>
                      <button
                        className={decision === 'APLICAR_ENTRANTE' ? 'selected' : ''}
                        onClick={() => elegir('APLICAR_ENTRANTE')}
                      >
                        Aplicar propuesta
                      </button>
                      <button
                        disabled={!conflicto.fusionable}
                        className={decision === 'FUSIONAR' ? 'selected' : ''}
                        onClick={() => elegir('FUSIONAR')}
                      >
                        Fusionar propiedades
                      </button>
                      <button
                        className={decision === 'DESCARTAR' ? 'selected danger' : 'danger'}
                        onClick={() => elegir('DESCARTAR')}
                      >
                        Descartar
                      </button>
                    </div>
                    {decision && !confirmando && (
                      <button
                        className="primary"
                        disabled={!conectado || ocupado}
                        onClick={() => setConfirmando(true)}
                      >
                        Revisar confirmación
                      </button>
                    )}
                    {confirmando && (
                      <div className="resolution-confirm">
                        <b>¿Confirmar: {etiquetas[decision!]}?</b>
                        <span>
                          Se usará la revisión {conflicto.revisionActual}. Si cambió, el servidor no
                          aplicará esta decisión.
                        </span>
                        <button
                          className="primary"
                          disabled={!conectado || ocupado}
                          onClick={confirmar}
                        >
                          {ocupado ? 'Confirmando…' : 'Confirmar resolución'}
                        </button>
                        <button disabled={ocupado} onClick={() => setConfirmando(false)}>
                          Volver a comparar
                        </button>
                      </div>
                    )}
                    {!conectado && (
                      <p className="muted">
                        La decisión queda seleccionada. Se habilitará al recuperar la conexión
                        colaborativa.
                      </p>
                    )}
                  </div>
                ) : (
                  <div className="read-only-note">
                    Puedes consultar las diferencias, pero tu acceso actual no permite resolverlas.
                  </div>
                )}
              </>
            ) : (
              <div className="empty">El modelo no tiene propuestas pendientes.</div>
            )}
          </main>
        </div>
      </section>
    </div>
  );
}
