import { useEffect, useState } from 'react';
import {
  actividadInicial,
  consultarActividad,
  detalleActividad,
  filtrosActividad,
  type ConsultaActividad,
  type PaginaActividad,
  type DetalleActividad,
  type FiltrosActividad,
} from '../api/actividadApi';

export function ActividadPanel({ proyecto }: { proyecto: string }) {
  const [consulta, setConsulta] = useState<ConsultaActividad>(actividadInicial),
    [pagina, setPagina] = useState<PaginaActividad | null>(null),
    [filtros, setFiltros] = useState<FiltrosActividad | null>(null);
  const [seleccion, setSeleccion] = useState<string | null>(null),
    [detalle, setDetalle] = useState<DetalleActividad | null>(null),
    [error, setError] = useState(''),
    [errorDetalle, setErrorDetalle] = useState(''),
    [cargando, setCargando] = useState(true),
    [recarga, setRecarga] = useState(0);
  const cambiar = (c: Partial<ConsultaActividad>) => {
    setConsulta({ ...consulta, pagina: 0, ...c });
    setSeleccion(null);
    setDetalle(null);
  };
  useEffect(() => {
    let activo = true;
    filtrosActividad(proyecto)
      .then((f) => {
        if (activo) setFiltros(f);
      })
      .catch((e) => {
        if (activo) setError(e.message);
      });
    return () => {
      activo = false;
    };
  }, [proyecto, recarga]);
  useEffect(() => {
    let activo = true;
    setCargando(true);
    setError('');
    const t = setTimeout(
      () =>
        consultarActividad(proyecto, consulta)
          .then((p) => {
            if (activo) setPagina(p);
          })
          .catch((e) => {
            if (activo) {
              setPagina(null);
              setError(e.message);
            }
          })
          .finally(() => {
            if (activo) setCargando(false);
          }),
      200,
    );
    return () => {
      activo = false;
      clearTimeout(t);
    };
  }, [proyecto, consulta, recarga]);
  useEffect(() => {
    if (!seleccion) return;
    let activo = true;
    setDetalle(null);
    setErrorDetalle('');
    detalleActividad(proyecto, seleccion)
      .then((d) => {
        if (activo) setDetalle(d);
      })
      .catch((e) => {
        if (activo) setErrorDetalle(e.message);
      });
    return () => {
      activo = false;
    };
  }, [proyecto, seleccion, recarga]);
  return (
    <section className="activity-panel" aria-label="Actividad del proyecto">
      <h3>Actividad del proyecto</h3>
      <div className="activity-filters">
        <label>
          Responsable
          <select
            value={consulta.responsable}
            onChange={(e) => cambiar({ responsable: e.target.value })}
          >
            <option value="">Todos</option>
            {filtros?.actores.map((a) => (
              <option key={a.id} value={a.id}>
                {a.nombre}
              </option>
            ))}
          </select>
        </label>
        <label>
          Acción
          <select value={consulta.accion} onChange={(e) => cambiar({ accion: e.target.value })}>
            <option value="">Todas</option>
            {filtros?.acciones.map((a) => (
              <option key={a}>{a}</option>
            ))}
          </select>
        </label>
        <label>
          Tipo de entidad
          <select value={consulta.tipo} onChange={(e) => cambiar({ tipo: e.target.value })}>
            <option value="">Todos</option>
            {filtros?.tipos.map((a) => (
              <option key={a}>{a}</option>
            ))}
          </select>
        </label>
        <label>
          UUID de la entidad
          <input
            placeholder="Identificador del elemento"
            value={consulta.entidad}
            onChange={(e) => cambiar({ entidad: e.target.value })}
          />
        </label>
        <label>
          Actividad desde (UTC)
          <input
            type="date"
            value={consulta.desde}
            onChange={(e) => cambiar({ desde: e.target.value })}
          />
        </label>
        <label>
          Actividad hasta (UTC)
          <input
            type="date"
            value={consulta.hasta}
            onChange={(e) => cambiar({ hasta: e.target.value })}
          />
        </label>
        <label>
          Eventos por página
          <select
            value={consulta.tamano}
            onChange={(e) => cambiar({ tamano: Number(e.target.value) })}
          >
            <option value="10">10</option>
            <option value="20">20</option>
            <option value="50">50</option>
          </select>
        </label>
        <button onClick={() => cambiar({ ...actividadInicial })}>Limpiar actividad</button>
      </div>
      {error && (
        <div className="error" role="alert">
          {error}
          <button onClick={() => setRecarga((r) => r + 1)}>Reintentar actividad</button>
        </div>
      )}
      {cargando ? (
        <p>Consultando actividad…</p>
      ) : (
        pagina && (
          <>
            <p className="muted">{pagina.total} eventos · los más recientes primero</p>
            {pagina.eventos.length === 0 ? (
              <p className="empty">No hay actividad que coincida con la consulta.</p>
            ) : (
              <div className="table-scroll">
                <table className="management-table">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Responsable</th>
                      <th>Acción</th>
                      <th>Entidad afectada</th>
                      <th>Consulta</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagina.eventos.map((e) => (
                      <tr key={e.id}>
                        <td>{new Date(e.ocurridoEn).toLocaleString()}</td>
                        <td>{e.responsable}</td>
                        <td>
                          {e.accion}
                          <small>{e.categoria}</small>
                        </td>
                        <td>
                          {e.tipoEntidad ?? 'No registrada'}
                          {e.idEntidad && <small className="entity-id">{e.idEntidad}</small>}
                        </td>
                        <td>
                          <button
                            aria-label={`Detalle del evento ${e.id}`}
                            onClick={() => setSeleccion(e.id)}
                          >
                            Ver detalle
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="pagination">
              <button
                disabled={consulta.pagina === 0}
                onClick={() => cambiar({ pagina: consulta.pagina - 1 })}
              >
                Actividad anterior
              </button>
              <span>Página {consulta.pagina + 1}</span>
              <button
                disabled={(consulta.pagina + 1) * pagina.tamano >= pagina.total}
                onClick={() => cambiar({ pagina: consulta.pagina + 1 })}
              >
                Actividad siguiente
              </button>
            </div>
          </>
        )
      )}
      {seleccion && (
        <div className="activity-detail" aria-label="Detalle de actividad">
          <div className="panel-heading">
            <h4>Detalle del evento {seleccion}</h4>
            <button onClick={() => setSeleccion(null)}>Cerrar detalle</button>
          </div>
          {errorDetalle && (
            <p className="error" role="alert">
              {errorDetalle}
              <button onClick={() => setRecarga((r) => r + 1)}>Reintentar detalle</button>
            </p>
          )}
          {!detalle && !errorDetalle ? (
            <p>Consultando detalle…</p>
          ) : (
            detalle && (
              <>
                <p>
                  {detalle.evento.accion} · {detalle.evento.responsable}
                </p>
                {detalle.evento.idVersionModelo ? (
                  <p>
                    Versión vinculada: <code>{detalle.evento.idVersionModelo}</code> · revisión{' '}
                    {detalle.evento.revisionModelo}
                  </p>
                ) : (
                  <p className="muted">Sin versión de modelo vinculada.</p>
                )}
                <div className="activity-values">
                  <div>
                    <h4>Valor anterior</h4>
                    {detalle.valorAnterior === null ? (
                      <p className="muted">Sin valor anterior registrado.</p>
                    ) : (
                      <pre>{JSON.stringify(detalle.valorAnterior, null, 2)}</pre>
                    )}
                  </div>
                  <div>
                    <h4>Valor posterior</h4>
                    {detalle.valorPosterior === null ? (
                      <p className="muted">Sin valor posterior registrado.</p>
                    ) : (
                      <pre>{JSON.stringify(detalle.valorPosterior, null, 2)}</pre>
                    )}
                  </div>
                </div>
                <h4>Datos de la acción</h4>
                <pre>{JSON.stringify(detalle.detalle, null, 2)}</pre>
              </>
            )
          )}
        </div>
      )}
    </section>
  );
}
