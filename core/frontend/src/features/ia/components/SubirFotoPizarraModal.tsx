import { useEffect, useRef, useState, type DragEvent } from 'react';
import { solicitarImagen } from '../api/iaApi';

const TIPOS = ['image/png', 'image/jpeg'];
const LIMITE = 10 * 1024 * 1024;

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
  const [textoDetectado, setTextoDetectado] = useState('');
  const [enviando, setEnviando] = useState(false);
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
    setVista(URL.createObjectURL(nuevo));
  }

  function soltar(e: DragEvent<HTMLButtonElement>) {
    e.preventDefault();
    seleccionar(e.dataTransfer.files[0]);
  }

  async function enviar() {
    if (!archivo || enviando) return;
    setEnviando(true);
    setError('');
    try {
      const resultado = await solicitarImagen(modelo, archivo, textoDetectado);
      if (!resultado.idPropuesta) throw new Error(resultado.mensaje);
      await alGenerar(resultado.idPropuesta);
      cerrar();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="dialog dialog-wide" role="dialog" aria-modal="true">
        <div className="dialog-header">
          <h2>Reconstruir desde una foto</h2>
          <button className="dialog-close" aria-label="Cerrar carga de foto" onClick={cerrar}>
            ×
          </button>
        </div>
        <div className="dialog-body">
          <p className="dialog-desc">
            Usa una foto nítida, tomada de frente y con los nombres legibles. La imagen original se
            conservará junto con la solicitud.
          </p>
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
              <img src={vista} alt="Vista previa del diagrama" />
            ) : (
              <span className="ia-dropzone-empty">
                <b>Arrastra una foto aquí</b>
                <small>o selecciónala · PNG/JPG · máximo 10 MB</small>
              </span>
            )}
          </button>
          {archivo && (
            <span className="ia-file-meta">
              {archivo.name} · {(archivo.size / 1024 / 1024).toFixed(2)} MB
            </span>
          )}
          <details className="ia-detected-text">
            <summary>¿Ya tienes el texto detectado?</summary>
            <p>
              Es opcional. Sin un proveedor OCR/multimodal configurado, este texto permite generar
              la propuesta sin atribuirle una lectura automática inexistente.
            </p>
            <textarea
              rows={4}
              maxLength={12000}
              value={textoDetectado}
              onChange={(e) => setTextoDetectado(e.target.value)}
              placeholder="Ej.: Clase Cliente con id: Long y nombre: String"
            />
          </details>
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
            {enviando ? 'Analizando…' : 'Analizar imagen'}
          </button>
        </div>
      </section>
    </div>
  );
}
