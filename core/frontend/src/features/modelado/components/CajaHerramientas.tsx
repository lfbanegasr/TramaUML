import type { DragEvent } from 'react';

export type TipoElementoArrastrable = 'CLASE' | 'CLASE_ABSTRACTA' | 'CLASE_ASOCIACION' | 'PAQUETE';

interface CajaHerramientasProps {
  puedeEditar: boolean;
  bloqueado: boolean;
  alIniciarConector?: (tipo: string) => void;
  conectorActivo?: string | null;
}

export function CajaHerramientas({
  puedeEditar,
  bloqueado,
  alIniciarConector,
  conectorActivo,
}: CajaHerramientasProps) {
  function iniciarArrastre(e: DragEvent<HTMLDivElement>, tipo: TipoElementoArrastrable) {
    if (!puedeEditar || bloqueado) {
      e.preventDefault();
      return;
    }
    e.dataTransfer.setData('application/reactflow-tipo', tipo);
    e.dataTransfer.effectAllowed = 'move';
  }

  return (
    <aside className="toolbox" aria-label="Caja de Herramientas UML">
      <div className="toolbox-header">
        <span className="eyebrow">PALETA UML</span>
        <h3>Caja de Herramientas</h3>
      </div>

      <div className="toolbox-section">
        <span className="toolbox-section-title">ELEMENTOS (ARRASTRAR)</span>
        <div className="toolbox-items">
          <div
            className={`toolbox-item ${!puedeEditar || bloqueado ? 'disabled' : ''}`}
            draggable={puedeEditar && !bloqueado}
            onDragStart={(e) => iniciarArrastre(e, 'CLASE')}
            title="Arrastra al lienzo para crear una Clase concreta"
            data-testid="herramienta-clase"
          >
            <div className="toolbox-item-icon class-icon">
              <span>C</span>
            </div>
            <div className="toolbox-item-content">
              <b>Clase</b>
              <small>Clase conceptual concreta</small>
            </div>
          </div>

          <div
            className={`toolbox-item ${!puedeEditar || bloqueado ? 'disabled' : ''}`}
            draggable={puedeEditar && !bloqueado}
            onDragStart={(e) => iniciarArrastre(e, 'CLASE_ABSTRACTA')}
            title="Arrastra al lienzo para crear una Clase Abstracta"
            data-testid="herramienta-clase-abstracta"
          >
            <div className="toolbox-item-icon abstract-icon">
              <span>
                <i>A</i>
              </span>
            </div>
            <div className="toolbox-item-content">
              <b>Clase Abstracta</b>
              <small>Clase con abstracción</small>
            </div>
          </div>

          <div
            className={`toolbox-item ${!puedeEditar || bloqueado ? 'disabled' : ''}`}
            draggable={puedeEditar && !bloqueado}
            onDragStart={(e) => iniciarArrastre(e, 'CLASE_ASOCIACION')}
            title="Arrastra al lienzo para crear una clase intermedia ordinaria"
            data-testid="herramienta-clase-asociacion"
          >
            <div className="toolbox-item-icon association-icon">
              <span>⧉</span>
            </div>
            <div className="toolbox-item-content">
              <b>Clase intermedia</b>
              <small>Clase ordinaria para una relación N:M</small>
            </div>
          </div>

          <div
            className={`toolbox-item ${!puedeEditar || bloqueado ? 'disabled' : ''}`}
            draggable={puedeEditar && !bloqueado}
            onDragStart={(e) => iniciarArrastre(e, 'PAQUETE')}
            title="Arrastra al lienzo para crear un Paquete"
            data-testid="herramienta-paquete"
          >
            <div className="toolbox-item-icon package-icon">
              <span>▱</span>
            </div>
            <div className="toolbox-item-content">
              <b>Paquete</b>
              <small>Contenedor lógico</small>
            </div>
          </div>
        </div>
      </div>

      <div className="toolbox-section">
        <span className="toolbox-section-title">CONECTORES UML (CLIC O ARRASTRE)</span>
        <p className="toolbox-hint">
          {conectorActivo ? (
            <b style={{ color: '#1b5d4c' }}>
              ● Clickea la clase origen y luego la clase destino para conectar.
            </b>
          ) : (
            'Toca un conector para activar el modo de conexión entre dos clases, o arrastra desde sus puntos.'
          )}
        </p>
        <div className="toolbox-connectors">
          <div
            className={`toolbox-connector-item ${conectorActivo === 'GENERALIZACION' ? 'active-connector' : ''}`}
            onClick={() => alIniciarConector?.('GENERALIZACION')}
            title="Herencia (Generalización). Haz clic y luego selecciona 2 clases para conectar"
          >
            <span className="connector-symbol">▷</span>
            <div className="connector-info">
              <b>Generalización / Herencia</b>
              <small>
                {conectorActivo === 'GENERALIZACION'
                  ? '● ACTIVO: Clic en 2 clases'
                  : 'Clase específica a clase general'}
              </small>
            </div>
          </div>

          <div
            className={`toolbox-connector-item ${conectorActivo === 'COMPOSICION' ? 'active-connector' : ''}`}
            onClick={() => alIniciarConector?.('COMPOSICION')}
            title="Composición: Rombo negro. Haz clic y luego selecciona 2 clases para conectar"
          >
            <span className="connector-symbol black-diamond">◆</span>
            <div className="connector-info">
              <b>Composición</b>
              <small>
                {conectorActivo === 'COMPOSICION'
                  ? '● ACTIVO: Clic en 2 clases'
                  : 'Rombo negro sólido'}
              </small>
            </div>
          </div>

          <div
            className={`toolbox-connector-item ${conectorActivo === 'AGREGACION' ? 'active-connector' : ''}`}
            onClick={() => alIniciarConector?.('AGREGACION')}
            title="Agregación: Rombo blanco. Haz clic y luego selecciona 2 clases para conectar"
          >
            <span className="connector-symbol white-diamond">◇</span>
            <div className="connector-info">
              <b>Agregación</b>
              <small>
                {conectorActivo === 'AGREGACION'
                  ? '● ACTIVO: Clic en 2 clases'
                  : 'Rombo blanco hueco'}
              </small>
            </div>
          </div>

          <div
            className={`toolbox-connector-item ${conectorActivo === 'ASOCIACION' ? 'active-connector' : ''}`}
            onClick={() => alIniciarConector?.('ASOCIACION')}
            title="Asociación: Haz clic y luego selecciona 2 clases para conectar"
          >
            <span className="connector-symbol">—</span>
            <div className="connector-info">
              <b>Asociación</b>
              <small>
                {conectorActivo === 'ASOCIACION'
                  ? '● ACTIVO: Clic en 2 clases'
                  : 'Asociación binaria con multiplicidades'}
              </small>
            </div>
          </div>

          <div
            className={`toolbox-connector-item ${conectorActivo === 'DEPENDENCIA' ? 'active-connector' : ''}`}
            onClick={() => alIniciarConector?.('DEPENDENCIA')}
            title="Dependencia: selecciona la clase cliente y luego su proveedor"
          >
            <span className="connector-symbol">⇢</span>
            <div className="connector-info">
              <b>Dependencia</b>
              <small>
                {conectorActivo === 'DEPENDENCIA'
                  ? '● ACTIVO: Clic en 2 clases'
                  : 'Cliente a proveedor; línea discontinua'}
              </small>
            </div>
          </div>
        </div>
      </div>
    </aside>
  );
}
