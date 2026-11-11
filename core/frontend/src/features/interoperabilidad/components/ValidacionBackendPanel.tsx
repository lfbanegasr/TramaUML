import { useEffect, useState } from 'react';
import {
  descargarArtefacto,
  leerInformeBackend,
  validarBackend,
  type GeneracionDisponible,
} from '../api/generacionApi';

export function ValidacionBackendPanel({
  modelo,
  generacion,
}: {
  modelo: string;
  generacion: GeneracionDisponible;
}) {
  const [informe, setInforme] = useState(generacion.validacion);
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [consulta, setConsulta] = useState(0);

  useEffect(() => {
    let activo = true;
    let timer: ReturnType<typeof setTimeout>;
    async function consultar() {
      try {
        const actual = await leerInformeBackend(modelo, generacion.idGeneracion);
        if (!activo) return;
        setInforme(actual);
        setError(null);
        if (actual.estado === 'EN_EJECUCION') timer = setTimeout(() => void consultar(), 1200);
      } catch (e) {
        if (activo)
          setError(e instanceof Error ? e.message : 'No se pudo consultar la validación.');
      }
    }
    if (informe?.estado === 'EN_EJECUCION') void consultar();
    return () => {
      activo = false;
      clearTimeout(timer);
    };
  }, [modelo, generacion.idGeneracion, informe?.estado, consulta]);

  async function validar() {
    setOcupado(true);
    setError(null);
    try {
      setInforme(await validarBackend(modelo, generacion.idGeneracion));
      setConsulta((n) => n + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo solicitar la validación.');
    } finally {
      setOcupado(false);
    }
  }

  async function descargar() {
    setOcupado(true);
    setError(null);
    try {
      await descargarArtefacto(modelo, generacion.idGeneracion, generacion.nombreArchivo);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo descargar el ZIP.');
    } finally {
      setOcupado(false);
    }
  }

  return (
    <section aria-label="Validación del backend" className="form-group">
      <h3>Validación y descarga</h3>
      <p>
        {generacion.nombreArchivo} · versión UML {generacion.numeroVersion}
      </p>
      <p role="status">
        {informe
          ? informe.estado === 'EN_EJECUCION'
            ? 'Compilación en curso… Puedes cerrar este diálogo y consultar el resultado después.'
            : `Validación: ${informe.estado}`
          : 'Backend generado. Debes validarlo antes de descargarlo.'}
      </p>
      {informe && (
        <>
          <p>
            {informe.comprobacionesAprobadas} comprobaciones aprobadas · {informe.cantidadErrores}{' '}
            errores · {informe.cantidadAdvertencias} advertencias
          </p>
          <ul>
            {informe.hallazgos.map((h, i) => (
              <li key={i}>
                <strong>
                  {h.severidad} · {h.tipoComprobacion}:
                </strong>{' '}
                {h.mensaje}
                {h.componente && <code> {h.componente}</code>}
              </li>
            ))}
          </ul>
          {informe.registroCompilacion && (
            <details>
              <summary>Registro de compilación</summary>
              <pre style={{ whiteSpace: 'pre-wrap', maxHeight: 280, overflow: 'auto' }}>
                {informe.registroCompilacion}
              </pre>
            </details>
          )}
        </>
      )}
      {error && (
        <p role="alert" className="dialog-error">
          {error}
        </p>
      )}
      {error && informe?.estado === 'EN_EJECUCION' && (
        <button onClick={() => setConsulta((n) => n + 1)}>Reintentar consulta</button>
      )}
      <div className="dialog-footer">
        {generacion.puedeValidar && (
          <button
            onClick={() => void validar()}
            disabled={ocupado || informe?.estado === 'EN_EJECUCION'}
          >
            {informe ? 'Volver a validar backend' : 'Validar backend'}
          </button>
        )}
        <button
          className="primary"
          onClick={() => void descargar()}
          disabled={ocupado || !informe?.puedeDescargar}
        >
          Descargar ZIP validado
        </button>
      </div>
    </section>
  );
}
