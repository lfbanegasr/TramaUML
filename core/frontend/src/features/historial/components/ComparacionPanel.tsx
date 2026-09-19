import type { DifVersion, ElementoDif } from '../api/historialApi';

export function ComparacionPanel({ dif, cerrar }: { dif: DifVersion; cerrar: () => void }) {
  const agregados = dif.diferencias.filter((d) => d.operacion === 'AGREGADO');
  const eliminados = dif.diferencias.filter((d) => d.operacion === 'ELIMINADO');
  const modificados = dif.diferencias.filter((d) => d.operacion === 'MODIFICADO');
  const visuales = dif.diferencias.filter((d) => d.tipo === 'VISUAL');

  function etiqueta(tipo: string) {
    const mapa: Record<string, string> = {
      PAQUETE: '▱ Paquete',
      CLASE: '◇ Clase',
      ATRIBUTO: '· Atributo',
      OPERACION: 'ƒ Operación',
      PARAMETRO: '→ Parámetro',
      RELACION: '↔ Relación',
      VISUAL: '⊞ Posición',
    };
    return mapa[tipo] ?? tipo;
  }

  return (
    <div
      className="panel-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="Comparación de versiones"
    >
      <div className="panel-box panel-wide">
        <div className="panel-head">
          <div>
            <span className="eyebrow">Comparación de versiones</span>
            <h2>
              Comparación v{dif.desde.numeroVersion} → v{dif.hasta.numeroVersion}
            </h2>
          </div>
          <button
            className="text-button"
            onClick={cerrar}
            aria-label="Cerrar comparación"
            id="btn-cerrar-comparacion"
          >
            ×
          </button>
        </div>
        <div className="version-meta-row">
          <div className="version-meta-cell">
            <span className="eyebrow">DESDE</span>
            <b>v{dif.desde.numeroVersion}</b> · rev. {dif.desde.revision}
            <small>
              {dif.desde.nombreAutor} · {dif.desde.creadoEn?.slice(0, 10)}
            </small>
          </div>
          <div className="version-meta-cell">
            <span className="eyebrow">HASTA</span>
            <b>v{dif.hasta.numeroVersion}</b> · rev. {dif.hasta.revision}
            <small>
              {dif.hasta.nombreAutor} · {dif.hasta.creadoEn?.slice(0, 10)}
            </small>
          </div>
        </div>
        {dif.diferencias.length === 0 ? (
          <p className="muted">Las dos versiones son idénticas.</p>
        ) : (
          <>
            <Seccion
              titulo={`Agregados (${agregados.length})`}
              items={agregados}
              etiqueta={etiqueta}
              color="added"
            />
            <Seccion
              titulo={`Eliminados (${eliminados.length})`}
              items={eliminados}
              etiqueta={etiqueta}
              color="removed"
            />
            <Seccion
              titulo={`Modificados (${modificados.length})`}
              items={modificados}
              etiqueta={etiqueta}
              color="changed"
              conPropiedades
            />
            {visuales.length > 0 && (
              <p className="muted">
                {visuales.length} posición(es) visual(es) cambiada(s) (no se muestran en detalle).
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function Seccion({
  titulo,
  items,
  etiqueta,
  color,
  conPropiedades,
}: {
  titulo: string;
  items: ElementoDif[];
  etiqueta: (t: string) => string;
  color: string;
  conPropiedades?: boolean;
}) {
  if (items.length === 0) return null;
  return (
    <div className="diff-section">
      <h3>{titulo}</h3>
      {items.map((el, i) => (
        <div key={el.id ?? i} className={`diff-item diff-${color}`}>
          <span className="diff-tipo">{etiqueta(el.tipo)}</span>
          <span className="diff-nombre">{el.nombre ?? el.id ?? '—'}</span>
          {conPropiedades && el.propiedades.length > 0 && (
            <table className="diff-props">
              <thead>
                <tr>
                  <th>Propiedad</th>
                  <th>Antes</th>
                  <th>Después</th>
                </tr>
              </thead>
              <tbody>
                {el.propiedades.map((p) => (
                  <tr key={p.clave} className="diff-prop-row">
                    <td>
                      <code>{p.clave}</code>
                    </td>
                    <td className="diff-before">{String(p.valorAntes ?? '—')}</td>
                    <td className="diff-after">{String(p.valorDespues ?? '—')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      ))}
    </div>
  );
}
