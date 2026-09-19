import { useEffect, useState } from 'react';
import {
  leerConfigGeneracion,
  guardarConfigGeneracion,
  generarBackend,
  descargarArtefacto,
  type ActualizarConfiguracion,
  type ResultadoGeneracion,
} from '../api/generacionApi';

type Estado = 'cargando' | 'listo' | 'guardando' | 'generando' | 'completado' | 'error';

export function GenerarBackendDialog({
  modelo,
  nombreModelo,
  cerrar,
}: {
  modelo: string;
  nombreModelo: string;
  cerrar: () => void;
}) {
  const [estado, setEstado] = useState<Estado>('cargando');
  const [cfg, setCfg] = useState<ActualizarConfiguracion>({
    nombre: 'Mi Proyecto',
    nombreProyecto: nombreModelo.toLowerCase().replace(/[^a-z0-9-]/g, '-'),
    idGrupo: 'com.empresa',
    idArtefacto: nombreModelo.toLowerCase().replace(/[^a-z0-9-]/g, '-'),
    paqueteBase: 'com.empresa.' + nombreModelo.toLowerCase().replace(/[^a-z0-9]/g, ''),
    versionInicial: '1.0.0-SNAPSHOT',
    generarRepositorios: true,
    generarControladores: true,
    generarDtos: true,
    exponerRest: true,
  });
  const [resultado, setResultado] = useState<ResultadoGeneracion | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    leerConfigGeneracion(modelo)
      .then((c) => {
        if (!activo) return;
        setCfg({
          nombre: c.nombre,
          nombreProyecto: c.nombreProyecto,
          idGrupo: c.idGrupo,
          idArtefacto: c.idArtefacto,
          paqueteBase: c.paqueteBase,
          versionInicial: c.versionInicial,
          generarRepositorios: c.generarRepositorios,
          generarControladores: c.generarControladores,
          generarDtos: c.generarDtos,
          exponerRest: c.exponerRest,
        });
        setEstado('listo');
      })
      .catch(() => {
        if (activo) setEstado('listo'); // usar defaults si falla
      });
    return () => {
      activo = false;
    };
  }, [modelo]);

  async function generar() {
    setEstado('guardando');
    setError(null);
    try {
      await guardarConfigGeneracion(modelo, cfg);
      setEstado('generando');
      const res = await generarBackend(modelo);
      setResultado(res);
      await descargarArtefacto(modelo, res.idGeneracion, res.nombreArchivo);
      setEstado('completado');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al generar el backend.');
      setEstado('error');
    }
  }

  const ocupado = estado === 'cargando' || estado === 'guardando' || estado === 'generando';

  return (
    <div
      className="dialog-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="gen-dialog-title"
    >
      <div className="dialog dialog-wide">
        <div className="dialog-header">
          <h2 id="gen-dialog-title">Generar Backend Spring Boot</h2>
          <button
            className="dialog-close"
            aria-label="Cerrar diálogo"
            onClick={cerrar}
            disabled={ocupado}
          >
            ×
          </button>
        </div>

        <div className="dialog-body">
          <p className="dialog-desc">
            Genera un proyecto Spring Boot completo (Java 21 / Maven / PostgreSQL) con 4 capas a
            partir del modelo <em>{nombreModelo}</em>. El proyecto incluye entidades JPA,
            repositorios, controladores REST con CRUD completo, DTOs y Swagger UI.
          </p>

          {estado === 'cargando' && (
            <p className="dialog-status">
              <span className="spinner" aria-hidden="true" /> Cargando configuración…
            </p>
          )}

          {(estado === 'listo' || estado === 'error') && (
            <div className="form-grid">
              <div className="form-group">
                <label htmlFor="gen-nombre">Nombre del proyecto</label>
                <input
                  id="gen-nombre"
                  type="text"
                  value={cfg.nombre}
                  onChange={(e) => setCfg((c) => ({ ...c, nombre: e.target.value }))}
                  disabled={ocupado}
                />
              </div>
              <div className="form-group">
                <label htmlFor="gen-grupo">Group ID (Maven)</label>
                <input
                  id="gen-grupo"
                  type="text"
                  placeholder="com.empresa"
                  value={cfg.idGrupo}
                  onChange={(e) => setCfg((c) => ({ ...c, idGrupo: e.target.value }))}
                  disabled={ocupado}
                />
              </div>
              <div className="form-group">
                <label htmlFor="gen-artefacto">Artifact ID (Maven)</label>
                <input
                  id="gen-artefacto"
                  type="text"
                  placeholder="mi-sistema"
                  value={cfg.idArtefacto}
                  onChange={(e) => setCfg((c) => ({ ...c, idArtefacto: e.target.value }))}
                  disabled={ocupado}
                />
              </div>
              <div className="form-group">
                <label htmlFor="gen-version">Versión inicial</label>
                <input
                  id="gen-version"
                  type="text"
                  placeholder="1.0.0-SNAPSHOT"
                  value={cfg.versionInicial}
                  onChange={(e) => setCfg((c) => ({ ...c, versionInicial: e.target.value }))}
                  disabled={ocupado}
                />
              </div>
              <div className="form-group form-full">
                <label htmlFor="gen-paquete">Paquete base Java</label>
                <input
                  id="gen-paquete"
                  type="text"
                  placeholder="com.empresa.sistema"
                  value={cfg.paqueteBase}
                  onChange={(e) => setCfg((c) => ({ ...c, paqueteBase: e.target.value }))}
                  disabled={ocupado}
                />
              </div>
              <div className="form-group form-full">
                <fieldset className="checkbox-group">
                  <legend>Capas a generar</legend>
                  <label>
                    <input
                      type="checkbox"
                      checked={cfg.generarRepositorios}
                      onChange={(e) =>
                        setCfg((c) => ({ ...c, generarRepositorios: e.target.checked }))
                      }
                      disabled={ocupado}
                    />
                    Repositorios JPA
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={cfg.generarControladores}
                      onChange={(e) =>
                        setCfg((c) => ({ ...c, generarControladores: e.target.checked }))
                      }
                      disabled={ocupado}
                    />
                    Controladores REST con CRUD
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={cfg.generarDtos}
                      onChange={(e) => setCfg((c) => ({ ...c, generarDtos: e.target.checked }))}
                      disabled={ocupado}
                    />
                    DTOs (Records inmutables)
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={cfg.exponerRest}
                      onChange={(e) => setCfg((c) => ({ ...c, exponerRest: e.target.checked }))}
                      disabled={ocupado}
                    />
                    Swagger UI (OpenAPI)
                  </label>
                </fieldset>
              </div>
            </div>
          )}

          {estado === 'guardando' && (
            <p className="dialog-status">
              <span className="spinner" aria-hidden="true" /> Guardando configuración…
            </p>
          )}
          {estado === 'generando' && (
            <p className="dialog-status">
              <span className="spinner" aria-hidden="true" /> Generando proyecto Spring Boot…
            </p>
          )}
          {estado === 'completado' && resultado && (
            <div className="dialog-success" role="status">
              <p>✓ Proyecto generado y descargado</p>
              <ul className="dialog-stats">
                <li>
                  <strong>{resultado.totalClases}</strong> clases procesadas
                </li>
                <li>
                  <strong>{resultado.totalArchivosGenerados}</strong> archivos generados
                </li>
                <li>
                  Archivo: <code>{resultado.nombreArchivo}</code>
                </li>
              </ul>
              <p className="dialog-hint">
                Extrae el ZIP y ejecuta <code>mvn spring-boot:run</code> para iniciar el servidor.
              </p>
            </div>
          )}
          {estado === 'error' && error && (
            <p className="dialog-error" role="alert">
              {error}
            </p>
          )}
        </div>

        <div className="dialog-footer">
          <button onClick={cerrar} disabled={ocupado}>
            {estado === 'completado' ? 'Cerrar' : 'Cancelar'}
          </button>
          {estado !== 'completado' && (
            <button
              id="btn-confirmar-generar-backend"
              className="primary"
              onClick={generar}
              disabled={ocupado}
            >
              {ocupado ? 'Generando…' : 'Generar y descargar ZIP'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
