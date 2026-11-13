import { useEffect, useState, useRef } from 'react';
import {
  descargarArtefacto,
  leerInformeBackend,
  validarBackend,
  type GeneracionDisponible,
  type InformeBackend,
} from '../api/generacionApi';

export function ValidacionBackendPanel({
  modelo,
  generacion,
  alActualizar,
}: {
  modelo: string;
  generacion: GeneracionDisponible;
  alActualizar?: (id: string, informe: InformeBackend) => void;
}) {
  const [informe, setInforme] = useState(generacion.validacion);
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [consulta, setConsulta] = useState(0);
  const [descargaPendiente, setDescargaPendiente] = useState(false);
  const descargaAutomatica = useRef(false);

  useEffect(() => {
    setInforme(generacion.validacion);
  }, [generacion.validacion]);

  useEffect(() => {
    if (!descargaPendiente || !informe || informe.estado === 'EN_EJECUCION') return;
    if (!informe.puedeDescargar) {
      setDescargaPendiente(false);
      return;
    }
    if (descargaAutomatica.current) return;
    descargaAutomatica.current = true;
    void descargarArtefacto(modelo, generacion.idGeneracion, generacion.nombreArchivo)
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudo descargar el ZIP.'))
      .finally(() => {
        descargaAutomatica.current = false;
        setDescargaPendiente(false);
      });
  }, [descargaPendiente, informe, modelo, generacion.idGeneracion, generacion.nombreArchivo]);

  useEffect(() => {
    let activo = true;
    let timer: ReturnType<typeof setTimeout>;
    async function consultar() {
      try {
        const actual = await leerInformeBackend(modelo, generacion.idGeneracion);
        if (!activo) return;
        setInforme(actual);
        alActualizar?.(generacion.idGeneracion, actual);
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
  }, [modelo, generacion.idGeneracion, informe?.estado, consulta, alActualizar]);

  async function validar(descargarAlTerminar = false) {
    setOcupado(true);
    setError(null);
    try {
      const actual = await validarBackend(modelo, generacion.idGeneracion);
      setInforme(actual);
      alActualizar?.(generacion.idGeneracion, actual);
      setDescargaPendiente(descargarAlTerminar);
      setConsulta((n) => n + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo solicitar la validación.');
      setDescargaPendiente(false);
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
    <section aria-label="Validación del backend" className="form-group backend-validation-panel">
      <h3>Compilar y descargar backend</h3>
      <p>
        La validación UML revisa el diagrama. Esta comprobación compila el proyecto generado; su
        resultado y el ZIP quedan guardados para futuras descargas.
      </p>
      {!generacion.puedeValidar && !informe?.puedeDescargar && (
        <p>
          Un propietario o líder con permiso para validar backend debe iniciar la compilación para
          habilitar la descarga de este ZIP.
        </p>
      )}
      <p>
        {generacion.nombreArchivo} · versión UML {generacion.numeroVersion}
      </p>
      <p role="status">
        {informe
          ? informe.estado === 'EN_EJECUCION'
            ? 'Compilación en curso… Puedes cerrar este diálogo y consultar el resultado después.'
            : `Validación: ${informe.estado}`
          : 'Código generado y guardado. Falta comprobar su compilación para habilitar la descarga.'}
      </p>
      {informe?.puedeDescargar &&
        !['VALIDO', 'VALIDO_CON_ADVERTENCIAS'].includes(informe.estado) && (
          <p className="dialog-success">
            Este ZIP conserva una aprobación anterior para su mismo contenido. Puedes descargarlo
            aunque la nueva comprobación esté en curso o no haya terminado correctamente.
          </p>
        )}
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
            onClick={() => void validar(false)}
            disabled={ocupado || informe?.estado === 'EN_EJECUCION'}
          >
            {informe ? 'Volver a validar backend' : 'Validar backend'}
          </button>
        )}
        {generacion.puedeValidar && !informe?.puedeDescargar && (
          <button
            className="primary"
            onClick={() => void validar(true)}
            disabled={ocupado || descargaPendiente}
          >
            {descargaPendiente ? 'Descarga pendiente de compilación…' : 'Validar y descargar ZIP'}
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
