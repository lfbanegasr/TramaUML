import { useState, type FormEvent } from 'react';
import type { DatosServicio, ServicioPlataforma } from '../api/administracionApi';

export function ServicioForm({
  servicio,
  ocupado,
  guardar,
  cancelar,
}: {
  servicio: ServicioPlataforma | null;
  ocupado: boolean;
  guardar: (datos: DatosServicio) => Promise<void>;
  cancelar: () => void;
}) {
  const configuracion = servicio?.configuracion ?? {};
  const { modelo = '', urlVerificacion = '', ...adicionales } = configuracion;
  const [nombre, setNombre] = useState(servicio?.nombre ?? '');
  const [proveedor, setProveedor] = useState<ServicioPlataforma['proveedor']>(
    servicio?.proveedor ?? 'LOCAL',
  );
  const [tipo, setTipo] = useState<ServicioPlataforma['tipoServicio']>(
    servicio?.tipoServicio ?? 'IA_TEXTO',
  );
  const [url, setUrl] = useState(servicio?.urlServicio ?? '');
  const [modeloRemoto, setModeloRemoto] = useState(String(modelo));
  const [urlPrueba, setUrlPrueba] = useState(String(urlVerificacion));
  const [parametros, setParametros] = useState(JSON.stringify(adicionales, null, 2));
  const [credencial, setCredencial] = useState('');
  const [retirar, setRetirar] = useState(false);
  const [error, setError] = useState('');

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setError('');
    try {
      const otros = JSON.parse(parametros || '{}') as unknown;
      if (!otros || Array.isArray(otros) || typeof otros !== 'object')
        throw new Error('Los parámetros adicionales deben formar un objeto JSON.');
      const parametrosFinales = { ...(otros as Record<string, unknown>) };
      if (proveedor !== 'LOCAL') {
        parametrosFinales.modelo = modeloRemoto.trim();
        if (urlPrueba.trim()) parametrosFinales.urlVerificacion = urlPrueba.trim();
      }
      await guardar({
        nombre: nombre.trim(),
        proveedor,
        tipoServicio: tipo,
        urlServicio: proveedor === 'LOCAL' ? '' : url.trim(),
        configuracion: parametrosFinales,
        credencial: credencial || undefined,
        retirarCredencial: retirar,
        revision: servicio?.revision,
      });
    } catch (e) {
      setError(
        e instanceof SyntaxError
          ? 'Revisa el JSON de parámetros adicionales.'
          : (e as Error).message,
      );
    }
  }

  return (
    <form onSubmit={enviar} className="service-form">
      <div className="panel-heading">
        <div>
          <span className="eyebrow">CONEXIÓN PROTEGIDA</span>
          <h2>{servicio ? 'Actualizar servicio' : 'Registrar servicio'}</h2>
          <p className="muted">
            La prueba de disponibilidad se realiza antes de confirmar los cambios.
          </p>
        </div>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="form-grid">
        <label>
          Nombre
          <input
            required
            maxLength={140}
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
          />
        </label>
        <label>
          Tipo de servicio
          <select value={tipo} onChange={(e) => setTipo(e.target.value as typeof tipo)}>
            <option value="IA_TEXTO">Interpretación de texto</option>
            <option value="VOZ_A_TEXTO">Transcripción de voz</option>
            <option value="IA_IMAGEN">Análisis de imágenes</option>
          </select>
        </label>
        <label>
          Proveedor
          <select
            value={proveedor}
            onChange={(e) => setProveedor(e.target.value as typeof proveedor)}
          >
            <option value="LOCAL">Motor local</option>
            <option value="OPENAI">OpenAI</option>
            <option value="GEMINI">Gemini</option>
            <option value="OLLAMA">Ollama remoto</option>
          </select>
        </label>
        {proveedor !== 'LOCAL' && (
          <>
            <label className="span-2">
              URL HTTPS del servicio
              <input
                required
                type="url"
                maxLength={2048}
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://proveedor.example/v1/..."
              />
            </label>
            <label>
              Modelo remoto
              <input
                required
                maxLength={200}
                value={modeloRemoto}
                onChange={(e) => setModeloRemoto(e.target.value)}
              />
            </label>
            <label className="span-2">
              URL HTTPS de verificación (opcional)
              <input
                type="url"
                maxLength={2048}
                value={urlPrueba}
                onChange={(e) => setUrlPrueba(e.target.value)}
              />
            </label>
          </>
        )}
        <label className="span-2">
          Parámetros adicionales (JSON, sin credenciales)
          <textarea rows={5} value={parametros} onChange={(e) => setParametros(e.target.value)} />
        </label>
        <label className="span-2">
          {servicio?.tieneCredencial
            ? 'Nueva credencial (dejar vacía para conservar)'
            : 'Credencial'}
          <input
            type="password"
            autoComplete="new-password"
            maxLength={4096}
            value={credencial}
            onChange={(e) => setCredencial(e.target.value)}
            disabled={retirar}
          />
          <small>Se cifra antes de persistirse y nunca volverá a mostrarse.</small>
        </label>
        {servicio?.tieneCredencial && (
          <label className="check-row span-2">
            <input
              type="checkbox"
              checked={retirar}
              onChange={(e) => {
                setRetirar(e.target.checked);
                if (e.target.checked) setCredencial('');
              }}
            />
            Retirar la credencial almacenada
          </label>
        )}
      </div>
      <div className="form-actions">
        <button type="button" onClick={cancelar} disabled={ocupado}>
          Cancelar
        </button>
        <button className="primary" disabled={ocupado}>
          {ocupado ? 'Comprobando…' : servicio ? 'Guardar y comprobar' : 'Registrar y comprobar'}
        </button>
      </div>
    </form>
  );
}
