import { useEffect, useState } from 'react';
import * as api from '../api/validacionApi';

export function InformeValidacion({
  modelo,
  revision,
  puedeValidar,
  bloqueado,
  localizar,
  cerrar,
}: {
  modelo: string;
  revision: number;
  puedeValidar: boolean;
  bloqueado: boolean;
  localizar: (elemento: string, clase?: string) => void;
  cerrar: () => void;
}) {
  const [datos, setDatos] = useState<api.Informe | null>(null),
    [trabajo, setTrabajo] = useState<string>();
  const [error, setError] = useState(''),
    [enviando, setEnviando] = useState(false),
    [recarga, setRecarga] = useState(0);
  useEffect(() => {
    let vivo = true;
    let siguiente: ReturnType<typeof setTimeout>;
    async function consultar() {
      try {
        const d = await api.informe(modelo, trabajo);
        if (vivo) {
          setDatos(d);
          setError('');
          if (d.estado === 'EN_EJECUCION') siguiente = setTimeout(consultar, 1000);
        }
      } catch (e) {
        if (vivo) setError((e as Error).message);
      }
    }
    consultar();
    return () => {
      vivo = false;
      clearTimeout(siguiente);
    };
  }, [modelo, revision, trabajo, recarga]);
  async function validar() {
    setEnviando(true);
    setError('');
    try {
      const t = await api.solicitar(modelo);
      setTrabajo(t.id);
      setRecarga((r) => r + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }
  return (
    <section className="validation-panel" aria-label="Informe de validación">
      <div className="panel-heading">
        <div>
          <h3>Validación semántica</h3>
          <p role="status">
            {datos?.estado ?? 'Consultando…'}
            {datos?.revision !== undefined ? ' · revisión ' + datos.revision : ''}
          </p>
        </div>
        <div>
          {puedeValidar && (
            <button
              className="primary"
              disabled={bloqueado || enviando || datos?.estado === 'EN_EJECUCION'}
              onClick={validar}
            >
              Validar modelo
            </button>
          )}
          <button onClick={cerrar}>Cerrar informe</button>
        </div>
      </div>
      {datos?.estado === 'DESACTUALIZADO' && (
        <p className="error">
          Este informe corresponde a una revisión anterior. Valida de nuevo la versión actual antes
          de usar sus conclusiones.
        </p>
      )}
      {error && (
        <p role="alert" className="error">
          {error}
          <button onClick={() => setRecarga((r) => r + 1)}>Reintentar informe</button>
        </p>
      )}
      {datos && (
        <p>
          {datos.errores ?? 0} errores · {datos.advertencias ?? 0} advertencias
        </p>
      )}
      <div className="validation-findings">
        {datos?.hallazgos.map((h) => (
          <article key={h.id} className={'finding ' + h.severidad.toLowerCase()}>
            <div>
              <b>
                {h.severidad} · {h.regla}
              </b>
              <p>{h.mensaje}</p>
              <small>{h.recomendacion}</small>
            </div>
            {h.elemento && (
              <button
                disabled={bloqueado}
                onClick={() => {
                  const u = JSON.parse(h.ubicacion_json) as { clase?: string };
                  localizar(h.elemento!, u.clase);
                }}
              >
                Localizar elemento
              </button>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}
