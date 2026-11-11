import { useEffect, useState, useRef, useCallback } from 'react';
import * as api from '../api/validacionApi';
import type { Modelo, Accion } from '../../modelado/api/modeladoApi';

export function InformeValidacion({
  modelo,
  revision,
  puedeValidar,
  bloqueado,
  localizar,
  cerrar,
  modeloObj,
  onCambiar,
  onCambiarLote,
  onAbrirIa,
  onAbrirGenerarBackend,
}: {
  modelo: string;
  revision: number;
  puedeValidar: boolean;
  bloqueado: boolean;
  localizar: (elemento: string, clase?: string) => void;
  cerrar: () => void;
  modeloObj?: Modelo | null;
  onCambiar?: (accion: Accion) => Promise<boolean>;
  onCambiarLote?: (acciones: Accion[]) => Promise<boolean>;
  onAbrirIa?: (prompt: string, claseId?: string) => void;
  onAbrirGenerarBackend?: () => void;
}) {
  const [datos, setDatos] = useState<api.Informe | null>(null);
  const [trabajo, setTrabajo] = useState<string>();
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [recarga, setRecarga] = useState(0);
  const [enviandoQuickFix, setEnviandoQuickFix] = useState<string | null>(null);
  const [enviandoLote, setEnviandoLote] = useState(false);
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);
  const ultimaRevisionValidada = useRef<number | null>(null);

  const enviandoRef = useRef(false);
  const ultimaRevisionSolicitadaRef = useRef<number | null>(revision);

  // Consulta el informe de validación del backend
  useEffect(() => {
    let vivo = true;
    let siguiente: ReturnType<typeof setTimeout>;
    async function consultar() {
      try {
        const d = await api.informe(modelo, trabajo);
        if (vivo) {
          setDatos(d);
          setError('');
          if (d.revision !== undefined) {
            ultimaRevisionValidada.current = d.revision;
          }
          if (d.estado === 'EN_EJECUCION') {
            siguiente = setTimeout(consultar, 700);
          }
        }
      } catch (e) {
        if (vivo) setError((e as Error).message);
      }
    }
    consultar();
    return () => {
      vivo = false;
      clearTimeout(siguiente);
    };
  }, [modelo, trabajo, recarga]);

  // Ejecuta una nueva solicitud de validación
  const validar = useCallback(async () => {
    if (enviandoRef.current) return;
    enviandoRef.current = true;
    setEnviando(true);
    setError('');
    try {
      ultimaRevisionSolicitadaRef.current = revision;
      const t = await api.solicitar(modelo);
      setTrabajo(t.id);
      setRecarga((r) => r + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      enviandoRef.current = false;
      setEnviando(false);
    }
  }, [modelo, revision]);

  // Auto-revalidación cuando la revisión del modelo cambia respecto a la última solicitada
  useEffect(() => {
    if (!puedeValidar) return;
    if (ultimaRevisionSolicitadaRef.current === revision) return;

    const temporizador = setTimeout(() => {
      void validar();
    }, 600);

    return () => clearTimeout(temporizador);
  }, [revision, puedeValidar, validar]);

  const esValido = datos?.estado === 'VALIDO';
  const esValidoConAdvertencias = datos?.estado === 'VALIDO_CON_ADVERTENCIAS';
  const esDesactualizado =
    datos?.estado === 'DESACTUALIZADO' ||
    (datos?.revision !== undefined && datos.revision !== revision);
  const enEjecucion = enviando || datos?.estado === 'EN_EJECUCION';

  // Heurística de tipo inteligente según el nombre del campo
  const deducirTipoInteligente = (nombreAttr: string): string => {
    const n = nombreAttr.toLowerCase();
    if (n === 'id' || n.endsWith('_id') || n.endsWith('id')) return 'Long';
    if (n.includes('fecha') || n.includes('date')) return 'LocalDate';
    if (n.startsWith('es') || n.startsWith('tiene') || n.includes('activo') || n.includes('habilitado')) return 'Boolean';
    if (n.includes('precio') || n.includes('monto') || n.includes('saldo') || n.includes('costo') || n.includes('total')) return 'Double';
    if (n.includes('numero') || n.includes('cantidad') || n.includes('anio') || n.includes('año') || n.includes('capacidad') || n.includes('inscritos') || n.includes('edad')) return 'Integer';
    return 'String';
  };

  // Contar hallazgos de tipos no resueltos y de falta de id para el panel masivo
  const hallazgosTipo = (datos?.hallazgos || []).filter((h) => {
    const reg = (h.regla || '').toUpperCase();
    const msg = (h.mensaje || '').toLowerCase();
    return (
      reg.includes('TIPO') ||
      reg.includes('TYPE') ||
      reg === 'ATRIBUTO.TIPO.OBLIGATORIO' ||
      msg.includes('no tiene tipo de dato') ||
      msg.includes('no se encuentra el tipo uml') ||
      msg.includes('tipo uml')
    );
  });

  const hallazgosFaltaId = (datos?.hallazgos || []).filter((h) => {
    const reg = (h.regla || '').toUpperCase();
    const msg = (h.mensaje || '').toLowerCase();
    return (
      reg.includes('IDENTIFICADOR') ||
      reg.includes('PERSISTENTE') ||
      reg === 'PERSISTENTE.ID.OBLIGATORIO' ||
      msg.includes('identificador (@id)') ||
      msg.includes('atributo identificador')
    );
  });

  // Corrección masiva de tipos no resueltos
  const corregirTodosLosTipos = async () => {
    if (!modeloObj || (!onCambiar && !onCambiarLote) || enviandoLote) return;
    setEnviandoLote(true);
    setError('');
    try {
      const acciones: Accion[] = [];
      const atributosVistos = new Set<string>();

      for (const h of hallazgosTipo) {
        // Encontrar atributo
        let targetAttr = modeloObj.atributos.find((a) => a.id === h.elemento);
        if (!targetAttr) {
          const match = h.mensaje.match(/En la clase '([^']+)',.*?(?:el atributo|de) '([^']+)'/i);
          if (match) {
            const claseObj = modeloObj.clases.find((c) => c.nombre === match[1]);
            if (claseObj) {
              targetAttr = modeloObj.atributos.find((a) => a.id_clase === claseObj.id && a.nombre === match[2]);
            }
          }
        }

        if (targetAttr && !atributosVistos.has(targetAttr.id)) {
          atributosVistos.add(targetAttr.id);
          const tipoDeducido = deducirTipoInteligente(targetAttr.nombre);
          const esIdAttr = targetAttr.nombre.toLowerCase() === 'id';

          let restricciones = {};
          try {
            restricciones = JSON.parse(targetAttr.restricciones_json || '{}');
          } catch {
            restricciones = {};
          }

          acciones.push({
            operacion: 'ACTUALIZAR',
            entidad: 'ATRIBUTO',
            idEntidad: targetAttr.id,
            datos: {
              idClase: targetAttr.id_clase,
              nombre: targetAttr.nombre,
              tipoUml: tipoDeducido,
              visibilidad: targetAttr.visibilidad || 'PRIVADO',
              minimo: esIdAttr ? 1 : targetAttr.multiplicidad_minima,
              maximo: esIdAttr ? 1 : targetAttr.multiplicidad_maxima,
              esIdentificador: esIdAttr ? true : targetAttr.es_identificador,
              esEstatico: esIdAttr ? false : targetAttr.es_estatico,
              esUnico: esIdAttr ? true : targetAttr.es_unico,
              esOrdenado: targetAttr.es_ordenado ?? false,
              coleccionUnica: targetAttr.coleccion_unica ?? true,
              valorPredeterminado: targetAttr.valor_predeterminado ?? '',
              restricciones,
            },
          });
        }
      }

      if (acciones.length === 0) return;

      const CHUNK_SIZE = 40;
      for (let i = 0; i < acciones.length; i += CHUNK_SIZE) {
        const chunk = acciones.slice(i, i + CHUNK_SIZE);
        if (onCambiarLote) {
          await onCambiarLote(chunk);
        } else if (onCambiar) {
          for (const act of chunk) {
            await onCambiar(act);
          }
        }
      }

      setMensajeExito(`✓ Se asignaron tipos válidos a ${acciones.length} atributo(s) automáticamente.`);
      setTimeout(() => setMensajeExito(null), 4000);
      void validar();
    } catch (e: any) {
      setError(e?.message || 'Error al aplicar corrección masiva de tipos');
    } finally {
      setEnviandoLote(false);
    }
  };

  // Corrección masiva de clases persistentes sin @Id
  const corregirTodosLosIds = async () => {
    if (!modeloObj || (!onCambiar && !onCambiarLote) || enviandoLote) return;
    setEnviandoLote(true);
    setError('');
    try {
      const acciones: Accion[] = [];
      const clasesVistas = new Set<string>();

      for (const h of hallazgosFaltaId) {
        let claseObj = modeloObj.clases.find((c) => c.id === h.elemento);
        if (!claseObj) {
          const match = h.mensaje.match(/clase (?:persistente )?'([^']+)'/i);
          if (match) {
            claseObj = modeloObj.clases.find((c) => c.nombre === match[1]);
          }
        }

        if (claseObj && !clasesVistas.has(claseObj.id)) {
          clasesVistas.add(claseObj.id);
          // Verificar si ya tiene un atributo llamado id
          const attrId = modeloObj.atributos.find(
            (a) => a.id_clase === claseObj!.id && a.nombre.toLowerCase() === 'id',
          );

          if (attrId) {
            // Convertirlo en identificador
            let restricciones = {};
            try {
              restricciones = JSON.parse(attrId.restricciones_json || '{}');
            } catch {
              restricciones = {};
            }
            acciones.push({
              operacion: 'ACTUALIZAR',
              entidad: 'ATRIBUTO',
              idEntidad: attrId.id,
              datos: {
                idClase: claseObj.id,
                nombre: attrId.nombre,
                tipoUml: attrId.tipo_uml && !attrId.tipo_uml.startsWith('uml:') ? attrId.tipo_uml : 'Long',
                visibilidad: attrId.visibilidad || 'PRIVADO',
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
            // Crear nuevo atributo id
            acciones.push({
              operacion: 'CREAR',
              entidad: 'ATRIBUTO',
              idEntidad: crypto.randomUUID(),
              datos: {
                idClase: claseObj.id,
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

      if (acciones.length === 0) return;

      const CHUNK_SIZE = 40;
      for (let i = 0; i < acciones.length; i += CHUNK_SIZE) {
        const chunk = acciones.slice(i, i + CHUNK_SIZE);
        if (onCambiarLote) {
          await onCambiarLote(chunk);
        } else if (onCambiar) {
          for (const act of chunk) {
            await onCambiar(act);
          }
        }
      }

      setMensajeExito(`✓ Se configuró '@Id: Long' en ${acciones.length} clase(s) persistente(s).`);
      setTimeout(() => setMensajeExito(null), 4000);
      void validar();
    } catch (e: any) {
      setError(e?.message || 'Error al aplicar identificadores');
    } finally {
      setEnviandoLote(false);
    }
  };

  return (
    <section className="validation-panel" aria-label="Informe de validación">
      <div className="panel-heading">
        <div>
          <h3>Validación semántica del modelo</h3>
          <p role="status">
            {enEjecucion
              ? '⏳ Validando versión actual…'
              : esDesactualizado
                ? `🔄 Desactualizado (Revisión ${datos?.revision ?? '?'} vs actual ${revision})`
                : datos?.estado
                  ? `${datos.estado} · revisión ${datos.revision ?? revision}`
                  : 'Consultando…'}
          </p>
        </div>
        <div className="panel-heading-actions">
          {puedeValidar && (
            <button
              className="primary"
              disabled={enEjecucion}
              onClick={() => void validar()}
              title="Solicitar validación completa de las reglas UML en el servidor"
            >
              {enEjecucion ? 'Validando…' : 'Validar modelo'}
            </button>
          )}
          <button onClick={cerrar}>Cerrar</button>
        </div>
      </div>

      {mensajeExito && (
        <div className="validation-success-toast" role="status">
          {mensajeExito}
        </div>
      )}

      {esDesactualizado && !enEjecucion && (
        <div className="validation-warning-box">
          <p className="warn-text">
            ⚠️ El modelo fue modificado recientemente. La validación se actualizará automáticamente o puedes forzarla.
          </p>
          <button type="button" className="btn-quick-action" onClick={() => void validar()}>
            Revalidar ahora
          </button>
        </div>
      )}

      {error && (
        <p role="alert" className="error">
          {error}
          <button onClick={() => setRecarga((r) => r + 1)}>Reintentar</button>
        </p>
      )}

      {datos && (
        <div className="validation-summary-bar">
          <span className={`summary-badge ${(datos.errores ?? 0) > 0 ? 'error' : 'ok'}`}>
            {datos.errores ?? 0} {(datos.errores ?? 0) === 1 ? 'error' : 'errores'}
          </span>
          <span className={`summary-badge ${(datos.advertencias ?? 0) > 0 ? 'warn' : 'neutral'}`}>
            {datos.advertencias ?? 0} {(datos.advertencias ?? 0) === 1 ? 'advertencia' : 'advertencias'}
          </span>
          {(datos.errores ?? 0) === 0 && (datos.advertencias ?? 0) === 0 && (
            <span className="summary-ok-msg">✓ El modelo cumple todas las reglas semánticas.</span>
          )}
        </div>
      )}

      {/* Banner de Acciones Masivas / Asistente Rápido */}
      {(hallazgosTipo.length > 0 || hallazgosFaltaId.length > 0) && (onCambiar || onCambiarLote) && (
        <div className="batch-fix-card">
          <div className="batch-fix-header">
            <span className="batch-icon">⚡</span>
            <div>
              <strong>Asistente de Corrección Masiva</strong>
              <p>Resuelve inconsistencias comunes del modelo en un solo clic:</p>
            </div>
          </div>
          <div className="batch-fix-buttons">
            {hallazgosTipo.length > 0 && (
              <button
                type="button"
                className="btn-batch-action"
                disabled={enviandoLote || enEjecucion}
                onClick={corregirTodosLosTipos}
                title="Asigna automáticamente tipos de datos válidos (Long, String, Integer, etc.) según el nombre de los atributos"
              >
                {enviandoLote ? 'Aplicando…' : `⚡ Auto-asignar tipos a ${hallazgosTipo.length} atributo(s)`}
              </button>
            )}
            {hallazgosFaltaId.length > 0 && (
              <button
                type="button"
                className="btn-batch-action"
                disabled={enviandoLote || enEjecucion}
                onClick={corregirTodosLosIds}
                title="Añade o configura el identificador @Id (Long) a todas las clases persistentes que lo requieren"
              >
                {enviandoLote ? 'Aplicando…' : `⚡ Añadir @Id (Long) a ${hallazgosFaltaId.length} clase(s)`}
              </button>
            )}
          </div>
        </div>
      )}

      {/* Banner de felicitación y acceso directo a Backend */}
      {(esValido || (esValidoConAdvertencias && (datos?.errores ?? 0) === 0)) && !esDesactualizado && (
        <div className="validation-success-card">
          <div className="success-card-content">
            <span className="success-icon">✨</span>
            <div>
              <strong>¡Modelo UML listo para generar!</strong>
              <p>No existen errores bloqueantes en el diagrama UML.</p>
            </div>
          </div>
          {onAbrirGenerarBackend && (
            <button
              type="button"
              className="primary btn-open-backend"
              onClick={onAbrirGenerarBackend}
              title="Abrir la configuración y generar el código backend Spring Boot"
            >
              🚀 Generar Backend
            </button>
          )}
        </div>
      )}

      <div className="validation-findings">
        {datos?.hallazgos.map((h) => {
          let ubicacion: { clase?: string; regla?: string } = {};
          try {
            ubicacion = JSON.parse(h.ubicacion_json);
          } catch {
            // Ubicación no JSON
          }

          // Extracción inteligente de clase y atributo por ID o por mensaje
          let nombreClaseExtraido: string | undefined;
          let nombreAttrExtraido: string | undefined;
          const matchAttr = h.mensaje.match(/En la clase '([^']+)',.*?(?:el atributo|de) '([^']+)'/i);
          if (matchAttr) {
            nombreClaseExtraido = matchAttr[1];
            nombreAttrExtraido = matchAttr[2];
          } else {
            const matchClase = h.mensaje.match(/clase (?:persistente )?'([^']+)'/i);
            if (matchClase) {
              nombreClaseExtraido = matchClase[1];
            }
          }

          const claseId =
            ubicacion.clase ||
            (h.entidad === 'CLASE_UML' ? h.elemento : undefined) ||
            (h.entidad === 'ATRIBUTO_UML'
              ? modeloObj?.atributos.find((a) => a.id === h.elemento)?.id_clase
              : undefined);
          const claseObj = modeloObj?.clases.find(
            (c) => c.id === claseId || c.nombre === nombreClaseExtraido,
          );
          const nombreClase = claseObj?.nombre || nombreClaseExtraido;

          const attrObj = modeloObj?.atributos.find(
            (a) =>
              a.id === h.elemento ||
              (claseObj && a.id_clase === claseObj.id && a.nombre === nombreAttrExtraido),
          );
          const nombreAttr = attrObj?.nombre || nombreAttrExtraido;
          const esError = h.severidad === 'ERROR';

          // Detección insensible a mayúsculas y amplia de reglas
          const reglaUpper = (h.regla || '').toUpperCase();
          const mensajeLower = (h.mensaje || '').toLowerCase();

          const esFaltaTipo =
            reglaUpper.includes('TIPO') ||
            reglaUpper.includes('TYPE') ||
            reglaUpper === 'ATRIBUTO.TIPO.OBLIGATORIO' ||
            mensajeLower.includes('no tiene tipo de dato') ||
            mensajeLower.includes('no se encuentra el tipo uml') ||
            mensajeLower.includes('tipo uml');

          const esFaltaId =
            reglaUpper.includes('IDENTIFICADOR') ||
            reglaUpper.includes('PERSISTENTE') ||
            reglaUpper === 'PERSISTENTE.ID.OBLIGATORIO' ||
            mensajeLower.includes('identificador (@id)') ||
            mensajeLower.includes('atributo identificador');

          const esAttrId = nombreAttr?.toLowerCase() === 'id';

          return (
            <article
              key={h.id}
              className={`finding-card ${esError ? 'finding-error' : 'finding-warn'}`}
            >
              <div className="finding-card-top">
                <div className="finding-badges">
                  <span className={`badge-severidad ${esError ? 'error' : 'warn'}`}>
                    {esError ? '❌ ERROR' : '⚠️ ADVERTENCIA'}
                  </span>
                  {nombreClase && (
                    <span className="badge-context badge-clase" title="Clase afectada">
                      📦 {nombreClase}
                    </span>
                  )}
                  {nombreAttr && (
                    <span className="badge-context badge-attr" title="Atributo afectado">
                      🔹 {nombreAttr}
                    </span>
                  )}
                  <span className="finding-regla" title="Regla UML">
                    {h.regla}
                  </span>
                </div>
              </div>

              <div className="finding-card-body">
                <p className="finding-mensaje">{h.mensaje}</p>
                {h.recomendacion && (
                  <div className="finding-recomendacion">
                    <span className="rec-icon">💡</span>
                    <span>{h.recomendacion}</span>
                  </div>
                )}
              </div>

              {/* Acciones Rápidas (Quick Fixes) con contrato estricto de 11 campos camelCase */}
              {esFaltaTipo && (attrObj || h.elemento) && onCambiar && (
                <div className="finding-quick-fix">
                  <span className="quick-fix-title">⚡ Corrección rápida de tipo:</span>
                  <div className="quick-fix-buttons">
                    {/* Botón especial si el atributo es 'id' para asignarle Long y @Id de una sola vez */}
                    {esAttrId && (
                      <button
                        type="button"
                        className="btn-quick-action"
                        disabled={enviandoQuickFix === h.id || enviandoLote}
                        title="Asigna tipo Long y marca como @Id con multiplicidad 1..1"
                        onClick={async () => {
                          const target = attrObj || modeloObj?.atributos.find((a) => a.id === h.elemento);
                          if (!target) return;
                          setEnviandoQuickFix(h.id);
                          let restricciones = {};
                          try {
                            restricciones = JSON.parse(target.restricciones_json || '{}');
                          } catch {
                            restricciones = {};
                          }
                          const exito = await onCambiar({
                            operacion: 'ACTUALIZAR',
                            entidad: 'ATRIBUTO',
                            idEntidad: target.id,
                            datos: {
                              idClase: target.id_clase,
                              nombre: target.nombre,
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
                          setEnviandoQuickFix(null);
                          if (exito) {
                            setMensajeExito(`✓ Atributo '${target.nombre}' configurado como @Id (Long).`);
                            setTimeout(() => setMensajeExito(null), 3000);
                            void validar();
                          }
                        }}
                      >
                        ⚡ Long (@Id)
                      </button>
                    )}

                    {['String', 'Long', 'Integer', 'Boolean', 'UUID', 'LocalDate', 'Double'].map(
                      (tipo) => (
                        <button
                          key={tipo}
                          type="button"
                          className="btn-quick-type"
                          disabled={enviandoQuickFix === h.id || enviandoLote}
                          onClick={async () => {
                            const target = attrObj || modeloObj?.atributos.find((a) => a.id === h.elemento);
                            if (!target) return;
                            setEnviandoQuickFix(h.id);
                            let restricciones = {};
                            try {
                              restricciones = JSON.parse(target.restricciones_json || '{}');
                            } catch {
                              restricciones = {};
                            }
                            const exito = await onCambiar({
                              operacion: 'ACTUALIZAR',
                              entidad: 'ATRIBUTO',
                              idEntidad: target.id,
                              datos: {
                                idClase: target.id_clase,
                                nombre: target.nombre,
                                tipoUml: tipo,
                                visibilidad: target.visibilidad || 'PRIVADO',
                                minimo: target.multiplicidad_minima ?? 1,
                                maximo: target.multiplicidad_maxima,
                                esIdentificador: target.es_identificador ?? false,
                                esEstatico: target.es_estatico ?? false,
                                esUnico: target.es_unico ?? false,
                                esOrdenado: target.es_ordenado ?? false,
                                coleccionUnica: target.coleccion_unica ?? true,
                                valorPredeterminado: target.valor_predeterminado ?? '',
                                restricciones,
                              },
                            });
                            setEnviandoQuickFix(null);
                            if (exito) {
                              setMensajeExito(
                                `✓ Tipo '${tipo}' asignado a '${target.nombre}'.`,
                              );
                              setTimeout(() => setMensajeExito(null), 3000);
                              void validar();
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

              {esFaltaId && (claseObj || claseId) && onCambiar && (
                <div className="finding-quick-fix">
                  <span className="quick-fix-title">⚡ Corrección rápida de ID:</span>
                  <div className="quick-fix-buttons">
                    <button
                      type="button"
                      className="btn-quick-action"
                      disabled={enviandoQuickFix === h.id || enviandoLote}
                      onClick={async () => {
                        const targetClaseId = claseObj?.id || claseId;
                        if (!targetClaseId) return;
                        setEnviandoQuickFix(h.id);

                        // Si ya existe un atributo id, actualizarlo
                        const attrExistente = modeloObj?.atributos.find(
                          (a) => a.id_clase === targetClaseId && a.nombre.toLowerCase() === 'id',
                        );

                        let exito = false;
                        if (attrExistente) {
                          let restricciones = {};
                          try {
                            restricciones = JSON.parse(attrExistente.restricciones_json || '{}');
                          } catch {
                            restricciones = {};
                          }
                          exito = await onCambiar({
                            operacion: 'ACTUALIZAR',
                            entidad: 'ATRIBUTO',
                            idEntidad: attrExistente.id,
                            datos: {
                              idClase: targetClaseId,
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
                              idClase: targetClaseId,
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

                        setEnviandoQuickFix(null);
                        if (exito) {
                          setMensajeExito(
                            `✓ Atributo 'id: Long' (@Id) configurado en '${nombreClase || 'la clase'}'.`,
                          );
                          setTimeout(() => setMensajeExito(null), 3000);
                          void validar();
                        }
                      }}
                    >
                      + Añadir 'id: Long' (@Id)
                    </button>
                    {claseObj && (
                      <button
                        type="button"
                        className="btn-quick-action btn-secondary-action"
                        disabled={enviandoQuickFix === h.id || enviandoLote}
                        title="Quitar estereotipo persistente para no requerir identificador"
                        onClick={async () => {
                          setEnviandoQuickFix(h.id);
                          const exito = await onCambiar({
                            operacion: 'ACTUALIZAR',
                            entidad: 'CLASE',
                            idEntidad: claseObj.id,
                            datos: {
                              nombre: claseObj.nombre,
                              descripcion: claseObj.descripcion ?? '',
                              estereotipo: claseObj.estereotipo ?? '',
                              visibilidad: claseObj.visibilidad || 'PUBLICO',
                              esAbstracto: claseObj.es_abstracto ?? false,
                              esPersistente: false,
                              ...(modeloObj?.puedeOrganizar && claseObj.id_paquete
                                ? { idPaquete: claseObj.id_paquete }
                                : {}),
                            },
                          });
                          setEnviandoQuickFix(null);
                          if (exito) {
                            setMensajeExito(
                              `✓ Clase '${claseObj.nombre}' configurada como no persistente.`,
                            );
                            setTimeout(() => setMensajeExito(null), 3000);
                            void validar();
                          }
                        }}
                      >
                        Hacer no persistente
                      </button>
                    )}
                  </div>
                </div>
              )}

              <div className="finding-card-actions">
                {onAbrirIa && (
                  <button
                    type="button"
                    className="btn-ia-fix"
                    title="Pedir al Asistente IA que analice y resuelva este problema"
                    onClick={() => {
                      let prompt = '';
                      if (esFaltaTipo && nombreClase && nombreAttr) {
                        prompt = `actualiza el atributo ${nombreAttr} en clase ${nombreClase} a ${nombreAttr}: String`;
                      } else if (esFaltaId && nombreClase) {
                        prompt = `crea el atributo id: Long en la clase ${nombreClase}`;
                      } else {
                        prompt = `Corrige en la clase '${nombreClase || 'seleccionada'}': ${h.mensaje}. ${h.recomendacion || ''}`;
                      }
                      onAbrirIa(prompt, claseId);
                    }}
                  >
                    🤖 Corregir con IA
                  </button>
                )}
                {h.elemento && (
                  <button
                    type="button"
                    className="btn-locate"
                    disabled={bloqueado}
                    onClick={() => {
                      localizar(h.elemento!, claseId);
                    }}
                  >
                    🔍 Localizar en lienzo
                  </button>
                )}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
