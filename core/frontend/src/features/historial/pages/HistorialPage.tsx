import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api';
import {
  listarVersiones,
  compararVersiones,
  type ResumenVersion,
  type DifVersion,
  type PaginaVersiones,
} from '../api/historialApi';
import { ComparacionPanel } from '../components/ComparacionPanel';
import { RestauracionDialog } from '../components/RestauracionDialog';

export function HistorialPage({
  modelo,
  revisionVigente,
  versionVigente,
  puedeRestaurar,
  volver,
  prepararRestauracion,
  alRestaurar,
}: {
  modelo: string;
  revisionVigente: number;
  versionVigente: string;
  puedeRestaurar: boolean;
  volver: () => void;
  prepararRestauracion?: () => Promise<boolean | void>;
  alRestaurar: (revision: number, version: string) => void;
}) {
  const [pagina, setPagina] = useState<PaginaVersiones | null>(null);
  const [error, setError] = useState('');
  const [seleccion, setSeleccion] = useState<ResumenVersion | null>(null);
  const [dif, setDif] = useState<DifVersion | null>(null);
  const [difError, setDifError] = useState('');
  const [comparando, setComparando] = useState(false);
  const [restaurar, setRestaurar] = useState<ResumenVersion | null>(null);

  const [filtroAutor, setFiltroAutor] = useState('');
  const [filtroDesde, setFiltroDesde] = useState('');
  const [filtroHasta, setFiltroHasta] = useState('');

  const cargar = (cursor?: number, append = false) => {
    setError('');
    listarVersiones(
      modelo,
      cursor,
      20,
      filtroAutor.trim() || undefined,
      filtroDesde || undefined,
      filtroHasta || undefined,
    )
      .then((p) => {
        if (append) {
          setPagina((prev) => ({
            ...p,
            versiones: [...(prev?.versiones ?? []), ...p.versiones],
          }));
        } else {
          setPagina(p);
        }
      })
      .catch((e: ApiError) => {
        setError(e.message);
      });
  };

  useEffect(() => {
    cargar();
  }, [modelo]);

  function aplicarFiltros(e: React.FormEvent) {
    e.preventDefault();
    cargar();
  }

  function limpiarFiltros() {
    setFiltroAutor('');
    setFiltroDesde('');
    setFiltroHasta('');
    listarVersiones(modelo, undefined, 20)
      .then(setPagina)
      .catch((e: ApiError) => setError(e.message));
  }

  function cargarMas() {
    if (!pagina?.cursorSiguiente) return;
    cargar(pagina.cursorSiguiente, true);
  }

  async function comparar(version: ResumenVersion) {
    setComparando(true);
    setDifError('');
    try {
      const resultado = await compararVersiones(modelo, version.id, versionVigente);
      setDif(resultado);
    } catch (e: unknown) {
      setDifError((e as ApiError).message);
    } finally {
      setComparando(false);
    }
  }

  function etiquetaValidacion(estado: string | null) {
    if (!estado) return null;
    const mapa: Record<string, string> = {
      SIN_VALIDAR: '… Sin validar',
      VALIDO: '✓ Válido',
      VALIDO_CON_ADVERTENCIAS: '⚠ Con advertencias',
      INVALIDO: '✗ Inválido',
      DESACTUALIZADO: '⟳ Desactualizado',
    };
    return mapa[estado] ?? estado.toLowerCase();
  }

  return (
    <div className="historial-page">
      <div className="editor-heading">
        <div>
          <button className="text-button" onClick={volver}>
            ← Volver al editor
          </button>
          <h1>
            Historial del modelo{' '}
            <span className="badge active">{pagina ? pagina.total + ' versiones' : '…'}</span>
          </h1>
        </div>
        <span className="save-state">Revisión vigente: {revisionVigente}</span>
      </div>

      <form className="historial-filtros filter-bar" onSubmit={aplicarFiltros}>
        <label>
          <span>Autor (UUID):</span>
          <input
            id="filtro-autor"
            type="text"
            placeholder="Filtrar por UUID de autor…"
            value={filtroAutor}
            onChange={(e) => setFiltroAutor(e.target.value)}
          />
        </label>
        <label>
          <span>Desde:</span>
          <input
            id="filtro-desde"
            type="date"
            value={filtroDesde}
            onChange={(e) => setFiltroDesde(e.target.value)}
          />
        </label>
        <label>
          <span>Hasta:</span>
          <input
            id="filtro-hasta"
            type="date"
            value={filtroHasta}
            onChange={(e) => setFiltroHasta(e.target.value)}
          />
        </label>
        <button id="btn-aplicar-filtros" type="submit">
          Filtrar
        </button>
        {(filtroAutor || filtroDesde || filtroHasta) && (
          <button
            id="btn-limpiar-filtros"
            type="button"
            className="text-button"
            onClick={limpiarFiltros}
          >
            Limpiar
          </button>
        )}
      </form>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {difError && (
        <p className="error" role="alert">
          {difError}
        </p>
      )}

      <div className="historial-body">
        {/* Lista de versiones */}
        <div className="historial-lista">
          {!pagina ? (
            <p className="muted">Cargando historial…</p>
          ) : pagina.versiones.length === 0 ? (
            <p className="muted">Este modelo todavía no tiene versiones registradas.</p>
          ) : (
            <>
              {pagina.versiones.map((v) => (
                <button
                  key={v.id}
                  className={
                    'historial-item' + (seleccion?.id === v.id ? ' historial-item-selected' : '')
                  }
                  id={`version-${v.id}`}
                  onClick={() => setSeleccion(v)}
                >
                  <div className="historial-item-head">
                    <span className="historial-num">v{v.numeroVersion}</span>
                    <span className="historial-version-label">Versión #{v.numeroVersion}</span>
                    {v.idVersionRestaurada && <span className="badge">Restauración</span>}
                    {v.estadoValidacion && (
                      <span className={'badge badge-val-' + v.estadoValidacion.toLowerCase()}>
                        {etiquetaValidacion(v.estadoValidacion)}
                      </span>
                    )}
                  </div>
                  <div className="historial-item-meta">
                    <span>{v.nombreAutor}</span>
                    <span>{v.creadoEn?.slice(0, 16).replace('T', ' ')}</span>
                  </div>
                  {v.resumen && <div className="historial-item-resumen">{v.resumen}</div>}
                </button>
              ))}
              {pagina.cursorSiguiente != null && (
                <button className="wide" onClick={cargarMas}>
                  Cargar más versiones
                </button>
              )}
            </>
          )}
        </div>

        {/* Panel lateral de detalle */}
        <aside className="historial-detalle">
          {!seleccion ? (
            <div className="inspector-empty">Selecciona una versión para ver sus detalles.</div>
          ) : (
            <>
              <span className="eyebrow">VERSIÓN {seleccion.numeroVersion}</span>
              <h2>Revisión {seleccion.revision}</h2>
              <p className="muted">{seleccion.resumen || 'Sin descripción'}</p>
              <dl className="historial-dl">
                <dt>Autor</dt>
                <dd>{seleccion.nombreAutor}</dd>
                <dt>Fecha</dt>
                <dd>{seleccion.creadoEn?.slice(0, 19).replace('T', ' ')}</dd>
                {seleccion.estadoValidacion && (
                  <>
                    <dt>Validación</dt>
                    <dd>{etiquetaValidacion(seleccion.estadoValidacion)}</dd>
                  </>
                )}
                {seleccion.idVersionRestaurada && (
                  <>
                    <dt>Restaurada de</dt>
                    <dd>
                      <code className="small">{seleccion.idVersionRestaurada.slice(0, 8)}…</code>
                    </dd>
                  </>
                )}
              </dl>
              <div className="panel-actions">
                <button
                  id={`btn-comparar-${seleccion.id}`}
                  disabled={comparando || seleccion.id === versionVigente}
                  onClick={() => comparar(seleccion)}
                >
                  {comparando ? 'Comparando…' : '↔ Comparar con versión actual'}
                </button>
                {puedeRestaurar && seleccion.id !== versionVigente && (
                  <button
                    id={`btn-restaurar-${seleccion.id}`}
                    className="danger"
                    onClick={async () => {
                      if (prepararRestauracion) {
                        await prepararRestauracion();
                      }
                      setRestaurar(seleccion);
                    }}
                  >
                    ↩ Restaurar esta versión
                  </button>
                )}
              </div>
            </>
          )}
        </aside>
      </div>

      {/* Comparación */}
      {dif && <ComparacionPanel dif={dif} cerrar={() => setDif(null)} />}

      {/* Restauración */}
      {restaurar && (
        <RestauracionDialog
          modelo={modelo}
          idVersionOrigen={restaurar.id}
          numeroVersion={restaurar.numeroVersion}
          revisionVigente={revisionVigente}
          cerrar={() => setRestaurar(null)}
          exito={(rev, ver) => {
            setRestaurar(null);
            alRestaurar(rev, ver);
          }}
        />
      )}
    </div>
  );
}
