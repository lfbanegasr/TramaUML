import { useEffect, useRef, useState, type DragEvent } from 'react';
import { solicitarImagen } from '../api/iaApi';

const TIPOS = ['image/png', 'image/jpeg'];
const LIMITE = 10 * 1024 * 1024;

export type TipoOrigenImagen = 'AUTO' | 'PAPEL' | 'PIZARRA' | 'DIGITAL';

export function SubirFotoPizarraModal({
  modelo,
  cerrar,
  alGenerar,
}: {
  modelo: string;
  cerrar: () => void;
  alGenerar: (id: string) => Promise<void>;
}) {
  const [archivo, setArchivo] = useState<File | null>(null);
  const [vista, setVista] = useState('');
  const [rotacion, setRotacion] = useState(0);
  const [tipoOrigen, setTipoOrigen] = useState<TipoOrigenImagen>('AUTO');
  const [realzarContraste, setRealzarContraste] = useState(false);
  const [modoAccion, setModoAccion] = useState<'FUSION' | 'NUEVO' | 'CONSULTAR'>('FUSION');
  const [textoDetectado, setTextoDetectado] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [mensajeProgreso, setMensajeProgreso] = useState('');
  const [error, setError] = useState('');
  const entrada = useRef<HTMLInputElement | null>(null);

  useEffect(
    () => () => {
      if (vista) URL.revokeObjectURL(vista);
    },
    [vista],
  );

  function seleccionar(nuevo: File | undefined) {
    setError('');
    if (!nuevo) return;
    if (!TIPOS.includes(nuevo.type)) {
      setError('Selecciona una imagen PNG o JPG.');
      return;
    }
    if (nuevo.size > LIMITE) {
      setError('La imagen supera el límite de 10 MB.');
      return;
    }
    if (vista) URL.revokeObjectURL(vista);
    setArchivo(nuevo);
    setRotacion(0);
    setVista(URL.createObjectURL(nuevo));
  }

  function soltar(e: DragEvent<HTMLButtonElement>) {
    e.preventDefault();
    seleccionar(e.dataTransfer.files[0]);
  }

  function girar(grados: number) {
    setRotacion((prev) => (prev + grados + 360) % 360);
  }

  function cambiarTipoOrigen(nuevo: TipoOrigenImagen) {
    setTipoOrigen(nuevo);
    if (nuevo === 'PAPEL' || nuevo === 'PIZARRA') {
      setRealzarContraste(true);
    } else if (nuevo === 'DIGITAL') {
      setRealzarContraste(false);
    }
  }

  async function prepararArchivoRotadoYFiltrado(
    file: File,
    deg: number,
    filtrar: boolean,
  ): Promise<File> {
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => {
        const swap = deg === 90 || deg === 270;
        const origW = swap ? img.height : img.width;
        const origH = swap ? img.width : img.height;

        // Limitar dimensiones máximas a 1400px para que el envío y el análisis en IA sea ultra rápido (pasa de varios MB a ~200 KB)
        const MAX_DIM = 1400;
        let scale = 1;
        if (origW > MAX_DIM || origH > MAX_DIM) {
          scale = Math.min(MAX_DIM / origW, MAX_DIM / origH);
        }

        const targetW = Math.max(1, Math.round(origW * scale));
        const targetH = Math.max(1, Math.round(origH * scale));

        const canvas = document.createElement('canvas');
        canvas.width = targetW;
        canvas.height = targetH;
        const ctx = canvas.getContext('2d');
        if (!ctx) return resolve(file);

        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate((deg * Math.PI) / 180);
        if (filtrar) {
          ctx.filter = 'contrast(1.65) grayscale(100%) brightness(1.05)';
        }

        const drawW = Math.round(img.width * scale);
        const drawH = Math.round(img.height * scale);
        ctx.drawImage(img, -drawW / 2, -drawH / 2, drawW, drawH);

        // Convertir siempre a JPEG optimizado (calidad 85%): reduce el tiempo de análisis hasta 10 veces
        canvas.toBlob(
          (blob) => {
            if (!blob) return resolve(file);
            resolve(new File([blob], 'diagrama_optimizado.jpg', { type: 'image/jpeg' }));
          },
          'image/jpeg',
          0.85,
        );
      };
      img.onerror = () => resolve(file);
      img.src = URL.createObjectURL(file);
    });
  }

  async function enviar() {
    if (!archivo || enviando) return;
    setEnviando(true);
    setError('');
    setMensajeProgreso('Orientando y preparando imagen...');

    const timer1 = setTimeout(() => {
      setMensajeProgreso('Reconociendo cajas de clases, atributos y métodos...');
    }, 1800);
    const timer2 = setTimeout(() => {
      setMensajeProgreso('Analizando flechas de herencia, relaciones y multiplicidades...');
    }, 4500);

    try {
      const archivoFinal = await prepararArchivoRotadoYFiltrado(
        archivo,
        rotacion,
        realzarContraste,
      );

      const etiquetasOrigen: Record<TipoOrigenImagen, string> = {
        AUTO: '[ORIGEN: AUTO]',
        PAPEL:
          '[ORIGEN: PAPEL_CUADERNO - Dibujo a lápiz/bolígrafo en hoja o libreta; ignorar cuadrícula/rayas de fondo; normalizar tipos SQL a UML; reconocer claves primarias y relaciones implícitas]',
        PIZARRA:
          '[ORIGEN: PIZARRA - Trazos de marcador en pizarra blanca o tiza; ignorar reflejos de flash o lámparas; enfocar en cajas y flechas manuscritas]',
        DIGITAL:
          '[ORIGEN: DIGITAL_CASE - Captura de software CASE como Enterprise Architect o StarUML; reconocer estereotipos, multiplicidades y visibilidades exactas]',
      };

      const modoTexto =
        modoAccion === 'CONSULTAR'
          ? '[MODO: CONSULTA]'
          : modoAccion === 'FUSION'
            ? '[MODO: FUSION]'
            : '[MODO: NUEVO]';
      const partesGuia = [etiquetasOrigen[tipoOrigen], modoTexto];
      if (textoDetectado.trim()) {
        partesGuia.push(textoDetectado.trim());
      } else if (modoAccion === 'CONSULTAR') {
        partesGuia.push('¿Qué observas en esta imagen y qué le faltaría para ser un modelo UML adecuado?');
      }
      const guiaFinal = partesGuia.join('\n');
      const intencionImagen = modoAccion === 'CONSULTAR' ? 'CONSULTAR' : 'MODIFICAR';

      const resultado = await solicitarImagen(modelo, archivoFinal, guiaFinal, intencionImagen);
      if (resultado.idPropuesta) {
        setMensajeProgreso('Propuesta generada. Abriendo visor de revisión...');
        await alGenerar(resultado.idPropuesta);
        cerrar();
        return;
      }
      if (modoAccion === 'CONSULTAR' || textoDetectado.trim().includes('?')) {
        cerrar();
        return;
      }
      throw new Error(resultado.mensaje);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      clearTimeout(timer1);
      clearTimeout(timer2);
      setEnviando(false);
      setMensajeProgreso('');
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="dialog dialog-wide" role="dialog" aria-modal="true">
        <div className="dialog-header">
          <h2>Reconstruir o actualizar desde imagen / pizarra / papel</h2>
          <button className="dialog-close" aria-label="Cerrar carga de foto" onClick={cerrar}>
            ×
          </button>
        </div>
        <div className="dialog-body">
          <p className="dialog-desc">
            Sube fotos de bocetos a mano en papel o libreta, dibujos en pizarra acrílica o capturas
            digitales de Enterprise Architect / CASE. El sistema reconocerá automáticamente clases,
            atributos, tipos y relaciones.
          </p>

          <div className="ia-presets-container">
            <span className="ia-presets-label">Tipo de soporte / origen de la imagen:</span>
            <div className="ia-presets-grid">
              <button
                type="button"
                className={'ia-preset-tab' + (tipoOrigen === 'AUTO' ? ' active' : '')}
                onClick={() => cambiarTipoOrigen('AUTO')}
                disabled={enviando}
              >
                <span className="ia-preset-tab-icon">🔍</span>
                <span className="ia-preset-tab-title">Automático</span>
              </button>
              <button
                type="button"
                className={'ia-preset-tab' + (tipoOrigen === 'PAPEL' ? ' active' : '')}
                onClick={() => cambiarTipoOrigen('PAPEL')}
                disabled={enviando}
              >
                <span className="ia-preset-tab-icon">📝</span>
                <span className="ia-preset-tab-title">Papel / Cuaderno</span>
              </button>
              <button
                type="button"
                className={'ia-preset-tab' + (tipoOrigen === 'PIZARRA' ? ' active' : '')}
                onClick={() => cambiarTipoOrigen('PIZARRA')}
                disabled={enviando}
              >
                <span className="ia-preset-tab-icon">🖍️</span>
                <span className="ia-preset-tab-title">Pizarra / Tablero</span>
              </button>
              <button
                type="button"
                className={'ia-preset-tab' + (tipoOrigen === 'DIGITAL' ? ' active' : '')}
                onClick={() => cambiarTipoOrigen('DIGITAL')}
                disabled={enviando}
              >
                <span className="ia-preset-tab-icon">💻</span>
                <span className="ia-preset-tab-title">Digital / CASE</span>
              </button>
            </div>
            <div className="ia-preset-tip">
              {tipoOrigen === 'AUTO' && (
                <span>
                  <b>🔍 Detección automática:</b> Identifica cualquier diagrama visual extrayendo
                  clases, atributos y conectores.
                </span>
              )}
              {tipoOrigen === 'PAPEL' && (
                <span>
                  <b>📝 Modo Cuaderno / Papel:</b> Optimizado para lápiz o bolígrafo sobre hojas
                  cuadriculadas o rayadas. Normaliza tipos de datos SQL (VARCHAR, INT, DECIMAL) a UML
                  y sintetiza claves foráneas manuscritas (cliente_id).
                </span>
              )}
              {tipoOrigen === 'PIZARRA' && (
                <span>
                  <b>🖍️ Modo Pizarra:</b> Atenúa reflejos de luz o borrones y resalta los trazos de
                  marcador acrílico o tiza.
                </span>
              )}
              {tipoOrigen === 'DIGITAL' && (
                <span>
                  <b>💻 Modo Digital / CASE:</b> Lee estereotipos de Enterprise Architect
                  («entity», «interface»), visibilidades exactas (+, -, #, ~) y multiplicidades.
                </span>
              )}
            </div>
          </div>

          <input
            ref={entrada}
            className="ia-file-input"
            type="file"
            accept="image/png,image/jpeg,.png,.jpg,.jpeg"
            onChange={(e) => seleccionar(e.target.files?.[0])}
          />

          <button
            type="button"
            className={'ia-dropzone' + (archivo ? ' selected' : '')}
            onClick={() => entrada.current?.click()}
            onDragOver={(e) => e.preventDefault()}
            onDrop={soltar}
          >
            {vista ? (
              <img
                src={vista}
                alt="Vista previa del diagrama"
                style={{
                  transform: `rotate(${rotacion}deg)`,
                  filter: realzarContraste
                    ? 'contrast(165%) grayscale(100%) brightness(105%)'
                    : 'none',
                  transition: 'transform 0.25s ease, filter 0.2s ease',
                  maxHeight: rotacion % 180 !== 0 ? '220px' : '310px',
                }}
              />
            ) : (
              <span className="ia-dropzone-empty">
                <b>Arrastra un boceto en papel, foto de pizarra o diagrama aquí</b>
                <small>o haz clic para seleccionar · PNG/JPG · máx. 10 MB</small>
              </span>
            )}
          </button>

          {archivo && (
            <>
              <div className="ia-image-tools">
                <span className="ia-file-meta">
                  {archivo.name} · {(archivo.size / 1024 / 1024).toFixed(2)} MB
                  {rotacion !== 0 && ` · Rotada ${rotacion}°`}
                  {realzarContraste && ' · Alto Contraste B/N'}
                </span>
                <div style={{ display: 'flex', gap: '6px' }}>
                  <button
                    type="button"
                    className="ia-rotate-btn"
                    title="Girar 90° a la izquierda"
                    onClick={() => girar(-90)}
                    disabled={enviando}
                  >
                    ↺ Girar -90°
                  </button>
                  <button
                    type="button"
                    className="ia-rotate-btn"
                    title="Girar 90° a la derecha"
                    onClick={() => girar(90)}
                    disabled={enviando}
                  >
                    ↻ Girar +90°
                  </button>
                </div>
              </div>

              <div className="ia-filter-toggle">
                <label>
                  <input
                    type="checkbox"
                    checked={realzarContraste}
                    onChange={(e) => setRealzarContraste(e.target.checked)}
                    disabled={enviando}
                  />
                  <span>
                    ⚡ Realzar trazos (Alto contraste B/N para lápiz tenue, bolígrafo o marcador)
                  </span>
                </label>
                <span className="ia-filter-toggle-desc">
                  {realzarContraste ? 'Activo en vista previa y envío' : 'Desactivado'}
                </span>
              </div>
            </>
          )}

          <div className="ia-mode-selector">
            <span className="ia-mode-title">Comportamiento con el modelo actual:</span>
            <label>
              <input
                type="radio"
                name="modoAnalisis"
                checked={modoAccion === 'FUSION'}
                onChange={() => setModoAccion('FUSION')}
                disabled={enviando}
              />
              <span>
                <b>Fusionar y actualizar:</b> Si una clase ya existe en tu diagrama, añadirá sus
                nuevos atributos y relaciones sin duplicarla.
              </span>
            </label>
            <label>
              <input
                type="radio"
                name="modoAnalisis"
                checked={modoAccion === 'NUEVO'}
                onChange={() => setModoAccion('NUEVO')}
                disabled={enviando}
              />
              <span>
                <b>Crear como nuevo modelo:</b> Tratará todos los elementos como un diseño
                independiente.
              </span>
            </label>
            <label>
              <input
                type="radio"
                name="modoAnalisis"
                checked={modoAccion === 'CONSULTAR'}
                onChange={() => setModoAccion('CONSULTAR')}
                disabled={enviando}
              />
              <span>
                <b>Consultar con el mentor (sin modificar):</b> Evalúa la imagen o responde tu
                pregunta en el chat compartido sin generar operaciones sobre el lienzo.
              </span>
            </label>
          </div>

          <details className="ia-detected-text">
            <summary>¿Deseas guiar el análisis, hacer una consulta o tienes texto detectado? (Opcional)</summary>
            <p>
              Opcional. Puedes escribir una pregunta de diseño sobre la imagen (ej.: «¿qué le falta a
              este diagrama?») o indicar instrucciones adicionales si alguna parte es poco legible.
            </p>
            <textarea
              rows={3}
              maxLength={12000}
              value={textoDetectado}
              onChange={(e) => setTextoDetectado(e.target.value)}
              placeholder="Ej.: ¿Qué le faltaría a este diagrama? o Clase Factura con id: Long y monto: BigDecimal"
            />
          </details>

          {enviando && (
            <div className="ia-progress-card" role="status">
              <div className="ia-spinner" />
              <span>{mensajeProgreso || 'Analizando imagen…'}</span>
            </div>
          )}

          {error && (
            <p className="dialog-error" role="alert">
              {error}
            </p>
          )}
        </div>
        <div className="dialog-footer">
          <button disabled={enviando} onClick={cerrar}>
            Cancelar
          </button>
          <button className="primary" disabled={!archivo || enviando} onClick={enviar}>
            {enviando
              ? 'Analizando…'
              : modoAccion === 'CONSULTAR'
                ? 'Consultar imagen con el mentor'
                : 'Analizar y generar propuesta'}
          </button>
        </div>
      </section>
    </div>
  );
}
