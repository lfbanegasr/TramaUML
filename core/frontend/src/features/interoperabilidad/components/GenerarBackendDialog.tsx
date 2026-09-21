import { useEffect, useState } from 'react';
import {
  generarBackend,
  listarGeneraciones,
  guardarConfigGeneracion,
  leerConfigGeneracion,
  type ActualizarConfiguracion,
  type ConfiguracionGeneracion,
  type ResultadoGeneracion,
  type GeneracionDisponible,
} from '../api/generacionApi';
import { ValidacionBackendPanel } from './ValidacionBackendPanel';

type Estado = 'cargando' | 'listo' | 'guardando' | 'generando' | 'completado' | 'error';

const solicitud = (cfg: ConfiguracionGeneracion): ActualizarConfiguracion => ({
  idVersionModelo: cfg.idVersionModelo,
  nombre: cfg.nombre,
  nombreProyecto: cfg.nombreProyecto,
  idGrupo: cfg.idGrupo,
  idArtefacto: cfg.idArtefacto,
  paqueteBase: cfg.paqueteBase,
  versionInicial: cfg.versionInicial,
  versionJava: cfg.versionJava,
  versionSpringBoot: cfg.versionSpringBoot,
  herramientaMigracion: cfg.herramientaMigracion,
  generarRepositorios: cfg.generarRepositorios,
  generarServicios: cfg.generarServicios,
  generarControladores: cfg.generarControladores,
  generarDtos: cfg.generarDtos,
  exponerRest: cfg.exponerRest,
  clases: cfg.clases.map((clase) => ({
    idClase: clase.idClase,
    seleccionada: clase.seleccionada,
    esPersistente: clase.esPersistente,
    exponerRest: clase.exponerRest,
    nombreTabla: clase.nombreTabla,
    nombreEntidad: clase.nombreEntidad,
    operaciones: clase.operaciones.map((operacion) => ({
      idOperacion: operacion.id,
      seleccionada: operacion.seleccionada,
    })),
  })),
  mapeosTipo: cfg.mapeosTipo,
  dependencias: cfg.dependencias,
});

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
  const [cfg, setCfg] = useState<ConfiguracionGeneracion | null>(null);
  const [resultado, setResultado] = useState<ResultadoGeneracion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [generaciones, setGeneraciones] = useState<GeneracionDisponible[]>([]);
  const [seleccionada, setSeleccionada] = useState<string>('');

  useEffect(() => {
    let activo = true;
    listarGeneraciones(modelo)
      .then((items) => {
        if (activo) setGeneraciones(items);
      })
      .catch((e: unknown) => {
        if (activo)
          setError(e instanceof Error ? e.message : 'No se pudo leer el historial de generación.');
      });
    leerConfigGeneracion(modelo)
      .then((configuracion) => {
        if (activo) {
          setCfg(configuracion);
          setEstado('listo');
        }
      })
      .catch((e: unknown) => {
        if (activo) {
          setError(e instanceof Error ? e.message : 'No se pudo leer la configuración.');
          setEstado('error');
        }
      });
    return () => {
      activo = false;
    };
  }, [modelo]);

  const cambiar = <K extends keyof ConfiguracionGeneracion>(
    clave: K,
    valor: ConfiguracionGeneracion[K],
  ) => setCfg((actual) => (actual ? { ...actual, [clave]: valor } : actual));

  async function cambiarVersion(version: string) {
    setEstado('cargando');
    setError(null);
    try {
      setCfg(await leerConfigGeneracion(modelo, version));
      setEstado('listo');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cargar la versión.');
      setEstado('error');
    }
  }

  async function guardar(): Promise<ConfiguracionGeneracion | null> {
    if (!cfg) return null;
    setEstado('guardando');
    setError(null);
    try {
      const guardada = await guardarConfigGeneracion(modelo, solicitud(cfg));
      setCfg(guardada);
      setEstado('listo');
      return guardada;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo guardar la configuración.');
      setEstado('error');
      return null;
    }
  }

  async function generar() {
    const guardada = await guardar();
    if (!guardada?.id) return;
    if (!guardada.puedeGenerar) {
      setError('Revisa los mensajes de configuración antes de generar.');
      setEstado('error');
      return;
    }
    setEstado('generando');
    try {
      const res = await generarBackend(modelo, guardada.id);
      setResultado(res);
      setGeneraciones(await listarGeneraciones(modelo));
      setSeleccionada(res.idGeneracion);
      setEstado('completado');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al generar el backend.');
      setEstado('error');
    }
  }

  const ocupado = estado === 'cargando' || estado === 'guardando' || estado === 'generando';

  return (
    <div className="dialog-backdrop" role="dialog" aria-modal="true" aria-labelledby="gen-title">
      <div className="dialog dialog-wide">
        <div className="dialog-header">
          <h2 id="gen-title">Configuración avanzada del backend</h2>
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
          {generaciones.length > 0 && (
            <div className="form-group">
              <label htmlFor="gen-artefacto">Generaciones guardadas</label>
              <select
                id="gen-artefacto"
                value={seleccionada}
                onChange={(e) => setSeleccionada(e.target.value)}
              >
                <option value="">Selecciona un backend para validar o descargar</option>
                {generaciones.map((g) => (
                  <option key={g.idGeneracion} value={g.idGeneracion}>
                    {g.nombreArchivo} · {new Date(g.creadoEn).toLocaleString()}
                  </option>
                ))}
              </select>
            </div>
          )}
          {generaciones
            .filter((g) => g.idGeneracion === seleccionada)
            .map((g) => (
              <ValidacionBackendPanel key={g.idGeneracion} modelo={modelo} generacion={g} />
            ))}
          <p className="dialog-desc">
            Configura una generación reproducible de <em>{nombreModelo}</em> sobre una versión UML
            congelada. La validación semántica de esa versión es obligatoria antes de generar.
          </p>

          {estado === 'cargando' && <p className="dialog-status">Cargando configuración…</p>}

          {cfg && estado !== 'cargando' && (
            <>
              <div className="form-grid">
                <div className="form-group">
                  <label htmlFor="gen-version-modelo">Versión del modelo</label>
                  <select
                    id="gen-version-modelo"
                    value={cfg.idVersionModelo}
                    onChange={(e) => void cambiarVersion(e.target.value)}
                    disabled={ocupado}
                  >
                    {cfg.versionesDisponibles.map((version) => (
                      <option key={version.id} value={version.id}>
                        v{version.numeroVersion} · rev. {version.revision} ·{' '}
                        {version.estadoValidacion}
                        {version.versionActual ? ' · actual' : ''}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label htmlFor="gen-nombre">Nombre de configuración</label>
                  <input
                    id="gen-nombre"
                    value={cfg.nombre}
                    onChange={(e) => cambiar('nombre', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="gen-proyecto">Nombre del proyecto generado</label>
                  <input
                    id="gen-proyecto"
                    value={cfg.nombreProyecto}
                    onChange={(e) => cambiar('nombreProyecto', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="gen-grupo">Group ID</label>
                  <input
                    id="gen-grupo"
                    value={cfg.idGrupo}
                    onChange={(e) => cambiar('idGrupo', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="gen-artefacto">Artifact ID</label>
                  <input
                    id="gen-artefacto"
                    value={cfg.idArtefacto}
                    onChange={(e) => cambiar('idArtefacto', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="gen-version">Versión inicial</label>
                  <input
                    id="gen-version"
                    value={cfg.versionInicial}
                    onChange={(e) => cambiar('versionInicial', e.target.value)}
                  />
                </div>
                <div className="form-group form-full">
                  <label htmlFor="gen-paquete">Paquete base Java</label>
                  <input
                    id="gen-paquete"
                    value={cfg.paqueteBase}
                    onChange={(e) => cambiar('paqueteBase', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="gen-java">Java</label>
                  <select
                    id="gen-java"
                    value={cfg.versionJava}
                    onChange={(e) => cambiar('versionJava', e.target.value)}
                  >
                    <option value="21">21</option>
                    <option value="17">17</option>
                  </select>
                </div>
                <div className="form-group">
                  <label htmlFor="gen-spring">Spring Boot</label>
                  <input
                    id="gen-spring"
                    value={cfg.versionSpringBoot}
                    onChange={(e) => cambiar('versionSpringBoot', e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label htmlFor="gen-migracion">Migraciones</label>
                  <select
                    id="gen-migracion"
                    value={cfg.herramientaMigracion}
                    onChange={(e) =>
                      cambiar(
                        'herramientaMigracion',
                        e.target.value as ConfiguracionGeneracion['herramientaMigracion'],
                      )
                    }
                  >
                    <option value="FLYWAY">Flyway</option>
                    <option value="LIQUIBASE">Liquibase</option>
                    <option value="NINGUNO">schema.sql</option>
                  </select>
                </div>
              </div>

              <fieldset className="checkbox-group">
                <legend>Capas</legend>
                {(
                  [
                    ['generarRepositorios', 'Repositorios JPA'],
                    ['generarServicios', 'Servicios'],
                    ['generarControladores', 'Controladores CRUD'],
                    ['generarDtos', 'DTOs inmutables'],
                    ['exponerRest', 'REST y OpenAPI'],
                  ] as const
                ).map(([clave, etiqueta]) => (
                  <label key={clave}>
                    <input
                      type="checkbox"
                      checked={cfg[clave]}
                      onChange={(e) => cambiar(clave, e.target.checked)}
                    />
                    {etiqueta}
                  </label>
                ))}
              </fieldset>

              <fieldset className="checkbox-group">
                <legend>
                  Clases y operaciones ({cfg.clases.filter((c) => c.seleccionada).length}{' '}
                  seleccionadas)
                </legend>
                {cfg.clases.map((clase, indice) => (
                  <div key={clase.idClase} className="form-group form-full">
                    <label>
                      <input
                        type="checkbox"
                        checked={clase.seleccionada}
                        disabled={clase.requerida}
                        onChange={(e) => {
                          const clases = [...cfg.clases];
                          clases[indice] = { ...clase, seleccionada: e.target.checked };
                          cambiar('clases', clases);
                        }}
                      />
                      <strong>{clase.nombre}</strong>
                      {clase.requerida ? ' · requerida por una relación' : ''}
                    </label>
                    {clase.seleccionada && (
                      <div className="form-grid">
                        <label>
                          <input
                            type="checkbox"
                            checked={clase.esPersistente}
                            onChange={(e) => {
                              const clases = [...cfg.clases];
                              clases[indice] = { ...clase, esPersistente: e.target.checked };
                              cambiar('clases', clases);
                            }}
                          />
                          Generar entidad persistente
                        </label>
                        <label>
                          <input
                            type="checkbox"
                            checked={clase.exponerRest}
                            onChange={(e) => {
                              const clases = [...cfg.clases];
                              clases[indice] = { ...clase, exponerRest: e.target.checked };
                              cambiar('clases', clases);
                            }}
                          />
                          Exponer controlador REST
                        </label>
                        <input
                          aria-label={'Entidad de ' + clase.nombre}
                          value={clase.nombreEntidad}
                          onChange={(e) => {
                            const clases = [...cfg.clases];
                            clases[indice] = { ...clase, nombreEntidad: e.target.value };
                            cambiar('clases', clases);
                          }}
                        />
                        <input
                          aria-label={'Tabla de ' + clase.nombre}
                          value={clase.nombreTabla}
                          onChange={(e) => {
                            const clases = [...cfg.clases];
                            clases[indice] = { ...clase, nombreTabla: e.target.value };
                            cambiar('clases', clases);
                          }}
                        />
                        {clase.operaciones.map((operacion, opIndice) => (
                          <label key={operacion.id}>
                            <input
                              type="checkbox"
                              checked={operacion.seleccionada}
                              onChange={(e) => {
                                const clases = [...cfg.clases];
                                const operaciones = [...clase.operaciones];
                                operaciones[opIndice] = {
                                  ...operacion,
                                  seleccionada: e.target.checked,
                                };
                                clases[indice] = { ...clase, operaciones };
                                cambiar('clases', clases);
                              }}
                            />
                            {operacion.nombre}(): {operacion.tipoRetorno || 'void'}
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </fieldset>

              <details>
                <summary>Mapeos de tipos ({cfg.mapeosTipo.length})</summary>
                <div className="form-grid">
                  {cfg.mapeosTipo.map((mapeo, indice) => (
                    <div className="form-group" key={mapeo.tipoUml}>
                      <label>{mapeo.tipoUml}</label>
                      <input
                        aria-label={'Java para ' + mapeo.tipoUml}
                        value={mapeo.tipoJava}
                        onChange={(e) => {
                          const mapeos = [...cfg.mapeosTipo];
                          mapeos[indice] = { ...mapeo, tipoJava: e.target.value };
                          cambiar('mapeosTipo', mapeos);
                        }}
                      />
                      <input
                        aria-label={'PostgreSQL para ' + mapeo.tipoUml}
                        value={mapeo.tipoPostgresql}
                        onChange={(e) => {
                          const mapeos = [...cfg.mapeosTipo];
                          mapeos[indice] = { ...mapeo, tipoPostgresql: e.target.value };
                          cambiar('mapeosTipo', mapeos);
                        }}
                      />
                    </div>
                  ))}
                </div>
              </details>

              <details>
                <summary>Dependencias Maven ({cfg.dependencias.length})</summary>
                <p>Las dependencias obligatorias se conservan al guardar la configuración.</p>
                <ul className="dialog-stats">
                  {cfg.dependencias.map((dependencia, indice) => (
                    <li key={dependencia.idGrupo + ':' + dependencia.idArtefacto}>
                      <code>
                        {dependencia.idGrupo}:{dependencia.idArtefacto}
                      </code>
                      {dependencia.version ? ':' + dependencia.version : ''} · {dependencia.ambito}
                      {dependencia.obligatoria ? ' · obligatoria' : ''}
                      {!dependencia.obligatoria && (
                        <button
                          type="button"
                          aria-label={'Quitar dependencia ' + dependencia.idArtefacto}
                          onClick={() =>
                            cambiar(
                              'dependencias',
                              cfg.dependencias.filter((_, actual) => actual !== indice),
                            )
                          }
                        >
                          Quitar
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
                <button
                  type="button"
                  onClick={() =>
                    cambiar('dependencias', [
                      ...cfg.dependencias,
                      {
                        idGrupo: 'org.example',
                        idArtefacto: 'dependencia-' + (cfg.dependencias.length + 1),
                        version: '1.0.0',
                        ambito: 'COMPILACION',
                        obligatoria: false,
                      },
                    ])
                  }
                >
                  Añadir dependencia Maven
                </button>
                {cfg.dependencias.map((dependencia, indice) =>
                  dependencia.obligatoria ? null : (
                    <div className="form-grid" key={'editar-' + indice}>
                      <input
                        aria-label={'Grupo dependencia ' + (indice + 1)}
                        value={dependencia.idGrupo}
                        onChange={(e) => {
                          const dependencias = [...cfg.dependencias];
                          dependencias[indice] = { ...dependencia, idGrupo: e.target.value };
                          cambiar('dependencias', dependencias);
                        }}
                      />
                      <input
                        aria-label={'Artefacto dependencia ' + (indice + 1)}
                        value={dependencia.idArtefacto}
                        onChange={(e) => {
                          const dependencias = [...cfg.dependencias];
                          dependencias[indice] = { ...dependencia, idArtefacto: e.target.value };
                          cambiar('dependencias', dependencias);
                        }}
                      />
                      <input
                        aria-label={'Versión dependencia ' + (indice + 1)}
                        value={dependencia.version ?? ''}
                        onChange={(e) => {
                          const dependencias = [...cfg.dependencias];
                          dependencias[indice] = {
                            ...dependencia,
                            version: e.target.value || null,
                          };
                          cambiar('dependencias', dependencias);
                        }}
                      />
                      <select
                        aria-label={'Ámbito dependencia ' + (indice + 1)}
                        value={dependencia.ambito}
                        onChange={(e) => {
                          const dependencias = [...cfg.dependencias];
                          dependencias[indice] = {
                            ...dependencia,
                            ambito: e.target.value as typeof dependencia.ambito,
                          };
                          cambiar('dependencias', dependencias);
                        }}
                      >
                        <option value="COMPILACION">Compilación</option>
                        <option value="EJECUCION">Ejecución</option>
                        <option value="PRUEBA">Prueba</option>
                        <option value="PROVISTO">Provisto</option>
                      </select>
                    </div>
                  ),
                )}
              </details>

              {cfg.mensajes.length > 0 && (
                <div aria-live="polite">
                  {cfg.mensajes.map((mensaje) => (
                    <p
                      key={mensaje.codigo + mensaje.mensaje}
                      className={mensaje.nivel === 'ERROR' ? 'dialog-error' : 'dialog-hint'}
                    >
                      {mensaje.mensaje}
                    </p>
                  ))}
                </div>
              )}
            </>
          )}

          {estado === 'guardando' && <p className="dialog-status">Guardando configuración…</p>}
          {estado === 'generando' && (
            <p className="dialog-status">Generando proyecto Spring Boot…</p>
          )}
          {estado === 'completado' && resultado && (
            <div className="dialog-success" role="status">
              <p>✓ Proyecto generado desde una versión UML congelada</p>
              <ul className="dialog-stats">
                <li>{resultado.totalClases} clases</li>
                <li>{resultado.totalOperaciones} operaciones seleccionadas</li>
                <li>{resultado.totalArchivosGenerados} archivos</li>
                <li>
                  <code>{resultado.nombreArchivo}</code>
                </li>
              </ul>
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
            Cerrar
          </button>
          {cfg && estado !== 'completado' && (
            <>
              <button onClick={() => void guardar()} disabled={ocupado}>
                Guardar
              </button>
              <button
                id="btn-confirmar-generar-backend"
                className="primary"
                onClick={() => void generar()}
                disabled={ocupado}
              >
                Guardar y generar
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
