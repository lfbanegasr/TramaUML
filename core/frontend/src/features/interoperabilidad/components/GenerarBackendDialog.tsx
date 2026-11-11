import { useEffect, useState, useRef, useCallback } from 'react';
import type { Modelo, Accion } from '../../modelado/api/modeladoApi';
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
import {
  solicitar as solicitarValidacion,
  informe as consultarInformeValidacion,
} from '../../validacion/api/validacionApi';

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
  modeloObj,
  onCambiar,
  onCambiarLote,
  onAbrirIa,
  onAbrirValidacion,
  onLocalizar,
}: {
  modelo: string;
  nombreModelo: string;
  cerrar: () => void;
  modeloObj?: Modelo | null;
  onCambiar?: (accion: Accion) => Promise<boolean>;
  onCambiarLote?: (acciones: Accion[]) => Promise<boolean>;
  onAbrirIa?: (prompt: string, claseId?: string) => void;
  onAbrirValidacion?: () => void;
  onLocalizar?: (elemento: string, clase?: string) => void;
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

  const recargarConfig = useCallback(async () => {
    try {
      const configuracion = await leerConfigGeneracion(modelo);
      setCfg(configuracion);
      return configuracion;
    } catch {
      return null;
    }
  }, [modelo]);

  const ultimaRevisionCargada = useRef<number | undefined>(modeloObj?.revision);
  useEffect(() => {
    if (modeloObj && modeloObj.revision !== ultimaRevisionCargada.current) {
      ultimaRevisionCargada.current = modeloObj.revision;
      void recargarConfig();
    }
  }, [modeloObj?.revision, recargarConfig]);

  async function validarYRecargar(): Promise<ConfiguracionGeneracion | null> {
    try {
      const t = await solicitarValidacion(modelo);
      let intentos = 0;
      while (intentos < 20) {
        await new Promise((resolve) => setTimeout(resolve, 500));
        const info = await consultarInformeValidacion(modelo, t.id);
        if (info.estado && info.estado !== 'EN_EJECUCION') {
          break;
        }
        intentos++;
      }
    } catch (e) {
      console.warn('Error durante la validación:', e);
    }
    const recargada = await leerConfigGeneracion(modelo);
    setCfg(recargada);
    return recargada;
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
                <div className="diagnostico-backend-container" aria-live="polite">
                  <div className="diagnostico-backend-header-row">
                    <h4 className="diagnostico-backend-titulo">
                      {cfg.mensajes.some((m) => m.nivel === 'ERROR')
                        ? '⚠️ Problemas que impiden la generación del backend:'
                        : 'ℹ️ Observaciones de la configuración:'}
                    </h4>
                    {cfg.mensajes.filter((m) => m.codigo === 'ATRIBUTO_SIN_TIPO' || m.codigo === 'CLASE_SIN_ID').length > 1 &&
                      (onCambiar || onCambiarLote) &&
                      modeloObj && (
                        <button
                          type="button"
                          className="btn-batch-action"
                          disabled={ocupado}
                          title="Resuelve automáticamente los tipos no definidos y los identificadores faltantes"
                          onClick={async () => {
                            setEstado('guardando');
                            try {
                              const acciones: Accion[] = [];
                              const attrsVistos = new Set<string>();
                              const clasesVistas = new Set<string>();

                              for (const m of cfg.mensajes) {
                                if (m.codigo === 'ATRIBUTO_SIN_TIPO') {
                                  const matchClase = m.mensaje.match(/En la clase '([^']+)'/i);
                                  const matchAttr = m.mensaje.match(/el atributo '([^']+)'/i);
                                  const clase = matchClase ? modeloObj.clases.find((c) => c.nombre === matchClase[1]) : null;
                                  const attr = modeloObj.atributos.find(
                                    (a) => (clase ? a.id_clase === clase.id : true) && (matchAttr ? a.nombre === matchAttr[1] : true),
                                  );
                                  if (attr && !attrsVistos.has(attr.id)) {
                                    attrsVistos.add(attr.id);
                                    const n = attr.nombre.toLowerCase();
                                    const tipo = n === 'id' || n.endsWith('_id') ? 'Long' : n.includes('fecha') ? 'LocalDate' : n.startsWith('es') || n.includes('activo') ? 'Boolean' : n.includes('numero') || n.includes('cantidad') || n.includes('anio') ? 'Integer' : 'String';
                                    let restricciones = {};
                                    try { restricciones = JSON.parse(attr.restricciones_json || '{}'); } catch {}
                                    acciones.push({
                                      operacion: 'ACTUALIZAR',
                                      entidad: 'ATRIBUTO',
                                      idEntidad: attr.id,
                                      datos: {
                                        idClase: attr.id_clase,
                                        nombre: attr.nombre,
                                        tipoUml: tipo,
                                        visibilidad: attr.visibilidad || 'PRIVADO',
                                        minimo: n === 'id' ? 1 : attr.multiplicidad_minima,
                                        maximo: n === 'id' ? 1 : attr.multiplicidad_maxima,
                                        esIdentificador: n === 'id' ? true : attr.es_identificador,
                                        esEstatico: n === 'id' ? false : attr.es_estatico,
                                        esUnico: n === 'id' ? true : attr.es_unico,
                                        valorPredeterminado: attr.valor_predeterminado ?? '',
                                        restricciones,
                                      },
                                    });
                                  }
                                } else if (m.codigo === 'CLASE_SIN_ID') {
                                  const matchClase = m.mensaje.match(/clase persistente '([^']+)'/i);
                                  const clase = matchClase ? modeloObj.clases.find((c) => c.nombre === matchClase[1]) : null;
                                  if (clase && !clasesVistas.has(clase.id)) {
                                    clasesVistas.add(clase.id);
                                    const attrId = modeloObj.atributos.find((a) => a.id_clase === clase.id && a.nombre.toLowerCase() === 'id');
                                    if (attrId) {
                                      let restricciones = {};
                                      try { restricciones = JSON.parse(attrId.restricciones_json || '{}'); } catch {}
                                      acciones.push({
                                        operacion: 'ACTUALIZAR',
                                        entidad: 'ATRIBUTO',
                                        idEntidad: attrId.id,
                                        datos: {
                                          idClase: clase.id,
                                          nombre: attrId.nombre,
                                          tipoUml: 'Long',
                                          visibilidad: 'PRIVADO',
                                          minimo: 1,
                                          maximo: 1,
                                          esIdentificador: true,
                                          esEstatico: false,
                                          esUnico: true,
                                          valorPredeterminado: '',
                                          restricciones,
                                        },
                                      });
                                    } else {
                                      acciones.push({
                                        operacion: 'CREAR',
                                        entidad: 'ATRIBUTO',
                                        idEntidad: crypto.randomUUID(),
                                        datos: {
                                          idClase: clase.id,
                                          nombre: 'id',
                                          tipoUml: 'Long',
                                          visibilidad: 'PRIVADO',
                                          minimo: 1,
                                          maximo: 1,
                                          esIdentificador: true,
                                          esEstatico: false,
                                          esUnico: true,
                                          valorPredeterminado: '',
                                          restricciones: {},
                                        },
                                      });
                                    }
                                  }
                                }
                              }

                              if (acciones.length > 0) {
                                setEstado('guardando');
                                const CHUNK_SIZE = 40;
                                for (let i = 0; i < acciones.length; i += CHUNK_SIZE) {
                                  const chunk = acciones.slice(i, i + CHUNK_SIZE);
                                  if (onCambiarLote) {
                                    await onCambiarLote(chunk);
                                  } else if (onCambiar) {
                                    for (const act of chunk) await onCambiar(act);
                                  }
                                }
                                await validarYRecargar();
                              }
                            } catch (e: any) {
                              setError(e?.message || 'Error al aplicar corrección en lote');
                            } finally {
                              setEstado('listo');
                            }
                          }}
                        >
                          ⚡ Auto-corregir problemas en lote
                        </button>
                      )}
                  </div>
                  <div className="diagnostico-backend-lista">
                    {cfg.mensajes.map((m) => {
                      const esError = m.nivel === 'ERROR';
                      const esTipoFaltante = m.codigo === 'ATRIBUTO_SIN_TIPO';
                      const esFaltaId = m.codigo === 'CLASE_SIN_ID';
                      const esSinValidar = m.codigo === 'VERSION_SIN_VALIDAR';

                      const matchClase = m.mensaje.match(/clase (?:persistente )?'([^']+)'/i) || m.mensaje.match(/En la clase '([^']+)'/i);
                      const matchAttr = m.mensaje.match(/el atributo '([^']+)'/i);
                      const nombreClase = matchClase ? matchClase[1] : null;
                      const nombreAttr = matchAttr ? matchAttr[1] : null;

                      const clase = modeloObj?.clases.find((c) => c.nombre === nombreClase);
                      const attr = modeloObj?.atributos.find(
                        (a) => (clase ? a.id_clase === clase.id : true) && (nombreAttr ? a.nombre === nombreAttr : true),
                      );

                      return (
                        <div
                          key={m.codigo + m.mensaje}
                          className={`diagnostico-item ${esError ? 'error' : 'advertencia'}`}
                        >
                          <div className="diagnostico-item-header">
                            <span className={`badge-nivel ${esError ? 'error' : 'warn'}`}>
                              {esError ? '❌ BLOQUEANTE' : '⚠️ ADVERTENCIA'}
                            </span>
                            <span className="diagnostico-codigo">{m.codigo}</span>
                          </div>
                          <p className="diagnostico-mensaje">{m.mensaje}</p>

                          {esTipoFaltante && onCambiar && (attr || modeloObj) && (
                            <div className="diagnostico-quick-fix">
                              <span className="quick-fix-label">⚡ Asignar tipo rápido:</span>
                              <div className="quick-fix-buttons">
                                {attr?.nombre.toLowerCase() === 'id' && (
                                  <button
                                    type="button"
                                    className="btn-quick-action"
                                    disabled={ocupado}
                                    title="Asigna Long y marca como @Id"
                                    onClick={async () => {
                                      if (!attr) return;
                                      let restricciones = {};
                                      try { restricciones = JSON.parse(attr.restricciones_json || '{}'); } catch {}
                                      const exito = await onCambiar({
                                        operacion: 'ACTUALIZAR',
                                        entidad: 'ATRIBUTO',
                                        idEntidad: attr.id,
                                        datos: {
                                          idClase: attr.id_clase,
                                          nombre: attr.nombre,
                                          tipoUml: 'Long',
                                          visibilidad: 'PRIVADO',
                                          minimo: 1,
                                          maximo: 1,
                                          esIdentificador: true,
                                          esEstatico: false,
                                          esUnico: true,
                                          valorPredeterminado: '',
                                          restricciones,
                                        },
                                      });
                                      if (exito) {
                                        setEstado('guardando');
                                        await validarYRecargar();
                                        setEstado('listo');
                                      }
                                    }}
                                  >
                                    ⚡ Long (@Id)
                                  </button>
                                )}
                                {['String', 'Long', 'Integer', 'Boolean', 'UUID', 'LocalDate'].map(
                                  (tipo) => (
                                    <button
                                      key={tipo}
                                      type="button"
                                      className="btn-quick-type"
                                      disabled={ocupado}
                                      onClick={async () => {
                                        const targetAttr = attr || modeloObj?.atributos.find((a) => m.mensaje.includes(`'${a.nombre}'`));
                                        if (targetAttr) {
                                          let restricciones = {};
                                          try {
                                            restricciones = JSON.parse(targetAttr.restricciones_json || '{}');
                                          } catch {
                                            restricciones = {};
                                          }
                                          const exito = await onCambiar({
                                            operacion: 'ACTUALIZAR',
                                            entidad: 'ATRIBUTO',
                                            idEntidad: targetAttr.id,
                                            datos: {
                                              idClase: targetAttr.id_clase,
                                              nombre: targetAttr.nombre,
                                              tipoUml: tipo,
                                              visibilidad: targetAttr.visibilidad || 'PRIVADO',
                                              minimo: targetAttr.multiplicidad_minima ?? 1,
                                              maximo: targetAttr.multiplicidad_maxima ?? 1,
                                              esIdentificador: targetAttr.es_identificador ?? false,
                                              esEstatico: targetAttr.es_estatico ?? false,
                                              esUnico: targetAttr.es_unico ?? false,
                                              valorPredeterminado: targetAttr.valor_predeterminado ?? '',
                                              restricciones,
                                            },
                                          });
                                          if (exito) {
                                            setEstado('guardando');
                                            await validarYRecargar();
                                            setEstado('listo');
                                          }
                                        }
                                      }}
                                    >
                                      {tipo}
                                    </button>
                                  ),
                                )}
                              </div>
                            </div>
                          )}

                          {esFaltaId && onCambiar && (clase || modeloObj) && (
                            <div className="diagnostico-quick-fix">
                              <span className="quick-fix-label">⚡ Identificador:</span>
                              <div className="quick-fix-buttons">
                                <button
                                  type="button"
                                  className="btn-quick-action"
                                  disabled={ocupado}
                                  onClick={async () => {
                                    const targetClase = clase || modeloObj?.clases.find((c) => m.mensaje.includes(`'${c.nombre}'`));
                                    if (targetClase) {
                                      const attrExistente = modeloObj?.atributos.find(
                                        (a) => a.id_clase === targetClase.id && a.nombre.toLowerCase() === 'id',
                                      );

                                      let exito = false;
                                      if (attrExistente) {
                                        let restricciones = {};
                                        try { restricciones = JSON.parse(attrExistente.restricciones_json || '{}'); } catch {}
                                        exito = await onCambiar({
                                          operacion: 'ACTUALIZAR',
                                          entidad: 'ATRIBUTO',
                                          idEntidad: attrExistente.id,
                                          datos: {
                                            idClase: targetClase.id,
                                            nombre: attrExistente.nombre,
                                            tipoUml: attrExistente.tipo_uml && !attrExistente.tipo_uml.startsWith('uml:') ? attrExistente.tipo_uml : 'Long',
                                            visibilidad: 'PRIVADO',
                                            minimo: 1,
                                            maximo: 1,
                                            esIdentificador: true,
                                            esEstatico: false,
                                            esUnico: true,
                                            valorPredeterminado: '',
                                            restricciones,
                                          },
                                        });
                                      } else {
                                        exito = await onCambiar({
                                          operacion: 'CREAR',
                                          entidad: 'ATRIBUTO',
                                          idEntidad: crypto.randomUUID(),
                                          datos: {
                                            idClase: targetClase.id,
                                            nombre: 'id',
                                            tipoUml: 'Long',
                                            visibilidad: 'PRIVADO',
                                            minimo: 1,
                                            maximo: 1,
                                            esIdentificador: true,
                                            esEstatico: false,
                                            esUnico: true,
                                            valorPredeterminado: '',
                                            restricciones: {},
                                          },
                                        });
                                      }

                                      if (exito) {
                                        setEstado('guardando');
                                        await validarYRecargar();
                                        setEstado('listo');
                                      }
                                    }
                                  }}
                                >
                                  + Añadir 'id: Long' (@Id)
                                </button>
                              </div>
                            </div>
                          )}

                          {esSinValidar && (
                            <div className="diagnostico-quick-fix">
                              <span className="quick-fix-label">⚡ Validación requerida:</span>
                              <div className="quick-fix-buttons">
                                <button
                                  type="button"
                                  className="btn-quick-action"
                                  disabled={ocupado}
                                  onClick={async () => {
                                    setEstado('guardando');
                                    try {
                                      await validarYRecargar();
                                      setEstado('listo');
                                    } catch (e) {
                                      setError(e instanceof Error ? e.message : 'Error al validar el modelo UML.');
                                      setEstado('error');
                                    }
                                  }}
                                >
                                  ⚡ Validar modelo UML ahora
                                </button>
                              </div>
                            </div>
                          )}

                          <div className="diagnostico-item-acciones">
                            {onAbrirIa && (
                              <button
                                type="button"
                                className="btn-ia-fix-sm"
                                onClick={() => {
                                  cerrar();
                                  onAbrirIa(
                                    `Ayúdame a corregir este problema en el backend: ${m.mensaje}. Por favor propón los cambios necesarios en el modelo UML.`,
                                  );
                                }}
                              >
                                🤖 Corregir con IA
                              </button>
                            )}
                            {onAbrirValidacion && m.codigo === 'VERSION_SIN_VALIDAR' && (
                              <button
                                type="button"
                                className="btn-link-sm"
                                onClick={() => {
                                  cerrar();
                                  onAbrirValidacion();
                                }}
                              >
                                🔍 Abrir panel de validación semántica
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
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
