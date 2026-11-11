import { useState, type FormEvent } from 'react';
import type {
  Accion,
  Modelo,
  Clase,
  Atributo,
  Operacion,
  Relacion,
  Paquete,
} from '../api/modeladoApi';
import { obtenerTiposDisponibles } from '../constants/CatalogoTipos';
import {
  PRESETS_MULTIPLICIDAD,
  OPCIONES_EXTREMO,
  detectarPreset,
  detectarExtremo,
} from '../constants/CatalogoMultiplicidades';

export type Borrador = {
  entidad: Accion['entidad'];
  id: string;
  nuevo: boolean;
  idClase?: string;
  origen?: string;
  destino?: string;
  clase?: Clase;
  atributo?: Atributo;
  operacion?: Operacion;
  relacion?: Relacion;
  paquete?: Paquete;
};

type ParametroEditable = {
  id: string;
  nombre: string;
  tipoUml: string;
  direccion: string;
  esObligatorio: boolean;
  valorPredeterminado: string;
};

const tipos: Record<string, string> = {
  CLASE: 'clase',
  ATRIBUTO: 'atributo',
  OPERACION: 'operación',
  RELACION: 'relación',
  PAQUETE: 'paquete',
};

const maximo = (v: FormDataEntryValue | null) => (v === '*' ? null : Number(v));

export function FormularioElemento({
  borrador: b,
  modelo,
  bloqueado,
  cancelar,
  guardar,
  alCrearClaseIntermedia,
}: {
  borrador: Borrador;
  modelo: Modelo;
  bloqueado: boolean;
  cancelar: () => void;
  guardar: (a: Accion) => Promise<boolean>;
  alCrearClaseIntermedia?: (idRelacion: string) => void;
}) {
  const [error, setError] = useState('');
  const [tipoRelacion, setTipoRelacion] = useState(b.relacion?.tipo_relacion ?? 'ASOCIACION');
  const catalogoTipos = obtenerTiposDisponibles();

  // Multiplicidad / Cardinalidades
  const [minO, setMinO] = useState<number | null>(b.relacion?.minimo_origen ?? 1);
  const [maxO, setMaxO] = useState<number | null>(b.relacion ? b.relacion.maximo_origen : 1);
  const [minD, setMinD] = useState<number | null>(b.relacion?.minimo_destino ?? 1);
  const [maxD, setMaxD] = useState<number | null>(b.relacion ? b.relacion.maximo_destino : 1);

  const [presetMultiplicidad, setPresetMultiplicidad] = useState<string>(() =>
    detectarPreset(
      b.relacion?.minimo_origen ?? 1,
      b.relacion ? b.relacion.maximo_origen : 1,
      b.relacion?.minimo_destino ?? 1,
      b.relacion ? b.relacion.maximo_destino : 1,
    ),
  );

  const [extremoOrigen, setExtremoOrigen] = useState<string>(() =>
    detectarExtremo(b.relacion?.minimo_origen ?? 1, b.relacion ? b.relacion.maximo_origen : 1),
  );

  const [extremoDestino, setExtremoDestino] = useState<string>(() =>
    detectarExtremo(b.relacion?.minimo_destino ?? 1, b.relacion ? b.relacion.maximo_destino : 1),
  );

  const esMuchosAMuchos = maxO === null && maxD === null;

  function aplicarPreset(presetId: string) {
    setPresetMultiplicidad(presetId);
    const encontrado = PRESETS_MULTIPLICIDAD.find((p) => p.id === presetId);
    if (!encontrado) return;
    setMinO(encontrado.minO);
    setMaxO(encontrado.maxO);
    setMinD(encontrado.minD);
    setMaxD(encontrado.maxD);
    setExtremoOrigen(detectarExtremo(encontrado.minO, encontrado.maxO));
    setExtremoDestino(detectarExtremo(encontrado.minD, encontrado.maxD));
  }

  function aplicarExtremoOrigen(val: string) {
    setExtremoOrigen(val);
    const opt = OPCIONES_EXTREMO.find((o) => o.valor === val);
    if (!opt) return;
    setMinO(opt.min);
    setMaxO(opt.max);
    setPresetMultiplicidad(detectarPreset(opt.min, opt.max, minD, maxD));
  }

  function aplicarExtremoDestino(val: string) {
    setExtremoDestino(val);
    const opt = OPCIONES_EXTREMO.find((o) => o.valor === val);
    if (!opt) return;
    setMinD(opt.min);
    setMaxD(opt.max);
    setPresetMultiplicidad(detectarPreset(minO, maxO, opt.min, opt.max));
  }

  const [parametros, setParametros] = useState<ParametroEditable[]>(
    modelo.parametros
      .filter((p) => p.id_operacion === b.id)
      .map((p) => ({
        id: p.id,
        nombre: p.nombre,
        tipoUml: p.tipo_uml,
        direccion: p.direccion,
        esObligatorio: p.es_obligatorio,
        valorPredeterminado: p.valor_predeterminado ?? '',
      })),
  );

  function parametro(id: string, datos: Partial<ParametroEditable>) {
    setParametros((ps) => ps.map((p) => (p.id === id ? { ...p, ...datos } : p)));
  }

  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError('');
    const f = new FormData(e.currentTarget);
    let datos: Record<string, unknown>;

    if (b.entidad === 'CLASE') {
      datos = {
        nombre: String(f.get('nombre')),
        descripcion: String(f.get('descripcion')),
        estereotipo: String(f.get('estereotipo')),
        visibilidad: String(f.get('visibilidad')),
        esAbstracto: f.get('abstracto') === 'on',
        esPersistente: f.get('persistente') === 'on',
        ...(modelo.puedeOrganizar ? { idPaquete: String(f.get('paquete')) || null } : {}),
        ...(b.nuevo ? { x: modelo.clases.length * 300, y: 100 } : {}),
      };
    } else if (b.entidad === 'PAQUETE') {
      datos = {
        nombre: String(f.get('nombre')),
        descripcion: String(f.get('descripcion')),
        padre: String(f.get('padre')) || null,
      };
    } else if (b.entidad === 'ATRIBUTO') {
      let restricciones: unknown;
      try {
        restricciones = JSON.parse(String(f.get('restricciones')) || '{}');
      } catch {
        setError('Las restricciones deben ser un objeto JSON válido.');
        return;
      }
      if (!restricciones || typeof restricciones !== 'object' || Array.isArray(restricciones)) {
        setError('Las restricciones deben ser un objeto JSON.');
        return;
      }
      datos = {
        idClase: b.idClase ?? b.atributo?.id_clase,
        nombre: String(f.get('nombre')),
        tipoUml: String(f.get('tipo')),
        visibilidad: String(f.get('visibilidad')),
        minimo: Number(f.get('minimo')),
        maximo: maximo(f.get('maximo')),
        esIdentificador: f.get('identificador') === 'on',
        esEstatico: f.get('estatico') === 'on',
        esUnico: f.get('unico') === 'on',
        esOrdenado: f.get('ordenado') === 'on',
        coleccionUnica: f.get('coleccionUnica') === 'on',
        valorPredeterminado: String(f.get('predeterminado')),
        restricciones,
      };
    } else if (b.entidad === 'OPERACION') {
      datos = {
        idClase: b.idClase ?? b.operacion?.id_clase,
        nombre: String(f.get('nombre')),
        tipoRetorno: String(f.get('retorno')) || null,
        visibilidad: String(f.get('visibilidad')),
        esEstatico: f.get('estatico') === 'on',
        esAbstracto: f.get('abstracto') === 'on',
        parametros,
      };
    } else {
      // RELACION
      let nombreRelacion = String(f.get('nombre') || '').trim();
      datos = {
        origen: String(f.get('origen')),
        destino: String(f.get('destino')),
        nombre: nombreRelacion || null,
        tipo: tipoRelacion,
        direccion: ['GENERALIZACION', 'DEPENDENCIA'].includes(tipoRelacion) ? 'ORIGEN_A_DESTINO' : String(f.get('direccion')),
        rolOrigen: String(f.get('rolOrigen')),
        rolDestino: String(f.get('rolDestino')),
        ...(!['GENERALIZACION', 'DEPENDENCIA'].includes(tipoRelacion)
          ? {
              minimoOrigen: Number(f.get('minimoOrigen')),
              maximoOrigen: maximo(f.get('maximoOrigen')),
              minimoDestino: Number(f.get('minimoDestino')),
              maximoDestino: maximo(f.get('maximoDestino')),
            }
          : {}),
      };
    }

    await guardar({
      operacion: b.nuevo ? 'CREAR' : 'ACTUALIZAR',
      entidad: b.entidad,
      idEntidad: b.id,
      datos,
    });
  }

  return (
    <form onSubmit={enviar} aria-label="Propiedades del elemento" className="formulario-elemento">
      <h2>
        {b.nuevo ? 'Nueva ' : 'Editar '}
        {tipos[b.entidad]}
      </h2>
      <fieldset disabled={bloqueado}>
        <label>
          Nombre
          <input
            name="nombre"
            required={b.entidad !== 'RELACION'}
            maxLength={180}
            defaultValue={
              b.clase?.nombre ??
              b.atributo?.nombre ??
              b.operacion?.nombre ??
              b.relacion?.nombre ??
              b.paquete?.nombre ??
              ''
            }
            placeholder={
              b.entidad === 'RELACION'
                ? tipoRelacion === 'GENERALIZACION'
                  ? 'Nombre descriptivo opcional'
                  : 'Opcional (ej: pertenece a)'
                : 'Nombre'
            }
            autoFocus
          />
        </label>

        {b.entidad === 'PAQUETE' && (
          <>
            <label>
              Descripción
              <textarea
                name="descripcion"
                maxLength={4000}
                defaultValue={b.paquete?.descripcion ?? ''}
              />
            </label>
            <label>
              Paquete padre
              <select name="padre" defaultValue={b.paquete?.id_paquete_padre ?? ''}>
                <option value="">Raíz del modelo</option>
                {modelo.paquetes
                  .filter((p) => p.id !== b.id)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nombre}
                    </option>
                  ))}
              </select>
            </label>
            <p className="muted">Debes vaciar o reubicar su contenido antes de eliminarlo.</p>
          </>
        )}

        {b.entidad === 'CLASE' && (
          <>
            <label>
              Descripción
              <textarea
                name="descripcion"
                maxLength={4000}
                defaultValue={b.clase?.descripcion ?? ''}
              />
            </label>
            <label>
              Estereotipo
              <input
                name="estereotipo"
                maxLength={100}
                defaultValue={b.clase?.estereotipo ?? ''}
                placeholder="Etiqueta libre opcional, p. ej., servicio o entidad"
              />
            </label>
            {modelo.puedeOrganizar && (
              <label>
                Paquete de la clase
                <select name="paquete" defaultValue={b.clase?.id_paquete ?? ''}>
                  <option value="">Raíz del modelo</option>
                  {modelo.paquetes.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nombre}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <Visibilidad valor={b.clase?.visibilidad ?? 'PUBLICO'} />
            <Check nombre="abstracto" label="Clase abstracta" valor={b.clase?.es_abstracto} />
            <Check
              nombre="persistente"
              label="Entidad persistente"
              valor={b.clase?.es_persistente ?? false}
            />
          </>
        )}

        {b.entidad === 'ATRIBUTO' && (
          <>
            <label>
              Tipo de dato UML
              <div className="tipo-selector-container">
                <input
                  name="tipo"
                  required
                  maxLength={180}
                  defaultValue={b.atributo?.tipo_uml ?? 'String'}
                  list="lista-tipos-uml"
                  placeholder="Selecciona o escribe un tipo…"
                  className="input-tipo-uml"
                />
                <datalist id="lista-tipos-uml">
                  {catalogoTipos.map((cat) => (
                    <optgroup key={cat.categoria} label={cat.categoria}>
                      {cat.tipos.map((t) => (
                        <option key={t.valor} value={t.valor}>
                          {t.valor} — {t.descripcion}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </datalist>
              </div>
              <small className="field-hint">
                Tipos admitidos: int, short, long, float, double, boolean, char, String, UUID, Date,
                o clases del modelo.
              </small>
            </label>

            <Visibilidad valor={b.atributo?.visibilidad ?? 'PRIVADO'} />
            <div className="field-pair">
              <Numero
                nombre="minimo"
                label="Mínimo"
                valor={b.atributo?.multiplicidad_minima ?? 1}
              />
              <Numero
                nombre="maximo"
                label="Máximo (* ilimitado)"
                ilimitado
                valor={b.atributo ? b.atributo.multiplicidad_maxima : 1}
              />
            </div>
            <Check
              nombre="identificador"
              label="Identificador {id}"
              valor={b.atributo?.es_identificador}
            />
            <Check nombre="estatico" label="Estático (subrayado)" valor={b.atributo?.es_estatico} />
            <Check nombre="unico" label="Único en base de datos (opción de generación)" valor={b.atributo?.es_unico} />
            <Check nombre="ordenado" label="Colección ordenada {ordered}" valor={b.atributo?.es_ordenado ?? false} />
            <Check nombre="coleccionUnica" label="Valores únicos en la colección UML" valor={b.atributo?.coleccion_unica ?? true} />
            <label>
              Valor predeterminado
              <input
                name="predeterminado"
                maxLength={4000}
                defaultValue={b.atributo?.valor_predeterminado ?? ''}
              />
            </label>

            <details className="advanced-options">
              <summary>Opciones avanzadas (Restricciones JSON)</summary>
              <label>
                Restricciones UML (JSON)
                <textarea
                  name="restricciones"
                  maxLength={8000}
                  defaultValue={b.atributo?.restricciones_json ?? '{}'}
                />
              </label>
            </details>
          </>
        )}

        {b.entidad === 'OPERACION' && (
          <>
            <label>
              Tipo de retorno
              <input
                name="retorno"
                maxLength={180}
                defaultValue={b.operacion?.tipo_retorno ?? ''}
                list="lista-tipos-retorno"
                placeholder="void, String, int…"
              />
              <datalist id="lista-tipos-retorno">
                <option value="void">void — Sin retorno</option>
                {catalogoTipos.map((cat) => (
                  <optgroup key={cat.categoria} label={cat.categoria}>
                    {cat.tipos.map((t) => (
                      <option key={t.valor} value={t.valor}>
                        {t.valor}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </datalist>
            </label>
            <Visibilidad valor={b.operacion?.visibilidad ?? 'PUBLICO'} />
            <Check nombre="estatico" label="Estática" valor={b.operacion?.es_estatico} />
            <Check nombre="abstracto" label="Abstracta" valor={b.operacion?.es_abstracto} />

            <h3>Parámetros</h3>
            {parametros.map((p, i) => (
              <section className="parameter-card" key={p.id} aria-label={'Parámetro ' + (i + 1)}>
                <label>
                  Nombre del parámetro
                  <input
                    required
                    maxLength={180}
                    value={p.nombre}
                    onChange={(e) => parametro(p.id, { nombre: e.target.value })}
                  />
                </label>
                <label>
                  Tipo del parámetro
                  <input
                    required
                    maxLength={180}
                    value={p.tipoUml}
                    list="lista-tipos-uml"
                    onChange={(e) => parametro(p.id, { tipoUml: e.target.value })}
                  />
                </label>
                <label>
                  Dirección
                  <select
                    aria-label="Dirección del parámetro"
                    value={p.direccion}
                    onChange={(e) => parametro(p.id, { direccion: e.target.value })}
                  >
                    <option value="ENTRADA">Entrada</option>
                    <option value="SALIDA">Salida</option>
                    <option value="ENTRADA_SALIDA">Entrada y salida</option>
                    <option value="RETORNO">Retorno</option>
                  </select>
                </label>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={p.esObligatorio}
                    onChange={(e) => parametro(p.id, { esObligatorio: e.target.checked })}
                  />{' '}
                  Obligatorio
                </label>
                <label>
                  Valor predeterminado del parámetro
                  <input
                    maxLength={4000}
                    value={p.valorPredeterminado}
                    onChange={(e) => parametro(p.id, { valorPredeterminado: e.target.value })}
                  />
                </label>
                <button
                  type="button"
                  onClick={() => setParametros((ps) => ps.filter((v) => v.id !== p.id))}
                >
                  Quitar parámetro {i + 1}
                </button>
              </section>
            ))}
            <button
              type="button"
              disabled={parametros.length >= 100}
              onClick={() =>
                setParametros((ps) => [
                  ...ps,
                  {
                    id: crypto.randomUUID(),
                    nombre: '',
                    tipoUml: 'String',
                    direccion: 'ENTRADA',
                    esObligatorio: true,
                    valorPredeterminado: '',
                  },
                ])
              }
            >
              ＋ Añadir parámetro
            </button>
          </>
        )}

        {b.entidad === 'RELACION' && (
          <>
            <label>
              Clase origen
              <select
                aria-label="Clase origen"
                name="origen"
                defaultValue={b.relacion?.id_clase_origen ?? b.origen}
                required
              >
                {modelo.clases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Clase destino
              <select
                aria-label="Clase destino"
                name="destino"
                defaultValue={b.relacion?.id_clase_destino ?? b.destino}
                required
              >
                {modelo.clases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Tipo de relación
              <select
                aria-label="Tipo de relación"
                value={tipoRelacion}
                onChange={(e) => setTipoRelacion(e.target.value)}
                className="select-tipo-relacion"
              >
                <option value="ASOCIACION">— Asociación estándar</option>
                <option value="COMPOSICION">◆ Composición (Rombo negro)</option>
                <option value="AGREGACION">◇ Agregación (Rombo blanco)</option>
                <option value="GENERALIZACION">▷ Generalización / Herencia</option>
                <option value="DEPENDENCIA">⇢ Dependencia (cliente a proveedor)</option>
              </select>
            </label>

            {/* Aviso visual según el tipo de relación */}
            {tipoRelacion === 'COMPOSICION' && (
              <div className="relation-type-notice notice-composition">
                <b>◆ Composición:</b> El extremo origen muestra el <b>rombo negro sólido</b>.
                Cada parte pertenece como máximo a un conjunto por instancia. El ciclo de vida
                necesita las reglas de dominio correspondientes.
              </div>
            )}
            {tipoRelacion === 'AGREGACION' && (
              <div className="relation-type-notice notice-aggregation">
                <b>◇ Agregación:</b> El extremo origen muestra el <b>rombo blanco hueco</b>.
                Su significado de pertenencia compartida se define en el dominio.
              </div>
            )}

            {tipoRelacion === 'GENERALIZACION' && (
              <p className="field-hint">Origen: subclase. Destino: superclase. Los conjuntos de generalización (disjoint, complete) están fuera de alcance; el nombre es texto libre.</p>
            )}
            <label>
              Dirección
              <select
                aria-label="Dirección de la relación"
                disabled={['GENERALIZACION', 'DEPENDENCIA'].includes(tipoRelacion)}
                name="direccion"
                defaultValue={b.relacion?.direccion ?? 'SIN_DIRECCION'}
              >
                <option value="SIN_DIRECCION">Sin dirección</option>
                <option value="ORIGEN_A_DESTINO">Origen a destino</option>
                <option value="DESTINO_A_ORIGEN">Destino a origen</option>
                <option value="BIDIRECCIONAL">Bidireccional</option>
              </select>
            </label>
            <label>
              Rol origen
              <input name="rolOrigen" maxLength={180} defaultValue={b.relacion?.rol_origen ?? ''} />
            </label>
            <label>
              Rol destino
              <input
                name="rolDestino"
                maxLength={180}
                defaultValue={b.relacion?.rol_destino ?? ''}
              />
            </label>
            {!['GENERALIZACION', 'DEPENDENCIA'].includes(tipoRelacion) && (
              <>
                <div className="multiplicity-section">
                  <span className="section-subtitle">CARDINALIDADES / MULTIPLICIDAD</span>
                  <label>
                    Esquema predefinido (Preset)
                    <select
                      value={presetMultiplicidad}
                      onChange={(e) => aplicarPreset(e.target.value)}
                      className="select-preset-multiplicidad"
                    >
                      {PRESETS_MULTIPLICIDAD.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.label}
                        </option>
                      ))}
                      <option value="custom">Personalizado…</option>
                    </select>
                  </label>

                  <div className="field-pair">
                    <label>
                      Extremo Origen
                      <select
                        value={extremoOrigen}
                        onChange={(e) => aplicarExtremoOrigen(e.target.value)}
                      >
                        {OPCIONES_EXTREMO.map((o) => (
                          <option key={o.valor} value={o.valor}>
                            {o.label}
                          </option>
                        ))}
                        <option value="custom">Personalizado</option>
                      </select>
                    </label>

                    <label>
                      Extremo Destino
                      <select
                        value={extremoDestino}
                        onChange={(e) => aplicarExtremoDestino(e.target.value)}
                      >
                        {OPCIONES_EXTREMO.map((o) => (
                          <option key={o.valor} value={o.valor}>
                            {o.label}
                          </option>
                        ))}
                        <option value="custom">Personalizado</option>
                      </select>
                    </label>
                  </div>

                  <details className="custom-multiplicity-details">
                    <summary>Valores numéricos detallados (Mín / Máx)</summary>
                    <div className="field-pair">
                      <Numero
                        nombre="minimoOrigen"
                        label="Mínimo origen"
                        valor={minO}
                        onChange={(val) => {
                          setMinO(val);
                          setPresetMultiplicidad('custom');
                          setExtremoOrigen('custom');
                        }}
                      />
                      <Numero
                        nombre="maximoOrigen"
                        label="Máximo origen (* ilimitado)"
                        ilimitado
                        valor={maxO}
                        onChange={(val) => {
                          setMaxO(val);
                          setPresetMultiplicidad('custom');
                          setExtremoOrigen('custom');
                        }}
                      />
                    </div>
                    <div className="field-pair">
                      <Numero
                        nombre="minimoDestino"
                        label="Mínimo destino"
                        valor={minD}
                        onChange={(val) => {
                          setMinD(val);
                          setPresetMultiplicidad('custom');
                          setExtremoDestino('custom');
                        }}
                      />
                      <Numero
                        nombre="maximoDestino"
                        label="Máximo destino (* ilimitado)"
                        ilimitado
                        valor={maxD}
                        onChange={(val) => {
                          setMaxD(val);
                          setPresetMultiplicidad('custom');
                          setExtremoDestino('custom');
                        }}
                      />
                    </div>
                  </details>

                  {/* Aviso y botón de clase intermedia para relaciones Muchos a Muchos */}
                  {esMuchosAMuchos && (
                    <div className="nm-relation-warning">
                      <b>⚡ Relación de Muchos a Muchos (*..*)</b>
                      <p>
                        La asociación N:M es válida. Como alternativa de diseño, puedes introducir
                        una <b>clase intermedia ordinaria</b>; esta opción no crea una AssociationClass UML.
                      </p>
                      {alCrearClaseIntermedia && b.relacion?.id && (
                        <button
                          type="button"
                          className="primary"
                          onClick={() => alCrearClaseIntermedia(b.relacion!.id)}
                        >
                          ⚡ Sustituir por clase intermedia (opcional)
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </>
            )}
          </>
        )}

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="form-actions">
          <button type="button" onClick={cancelar}>
            Cancelar
          </button>
          <button className="primary" disabled={!modelo.puedeEditar}>
            Guardar elemento
          </button>
        </div>
      </fieldset>
    </form>
  );
}

function Visibilidad({ valor }: { valor: string }) {
  return (
    <label>
      Visibilidad
      <select aria-label="Visibilidad" name="visibilidad" defaultValue={valor}>
        <option value="PUBLICO">+ Pública</option>
        <option value="PRIVADO">− Privada</option>
        <option value="PROTEGIDO"># Protegida</option>
        <option value="PAQUETE">~ Paquete</option>
      </select>
    </label>
  );
}

function Check({ nombre, label, valor }: { nombre: string; label: string; valor?: boolean }) {
  return (
    <label className="check">
      <input type="checkbox" name={nombre} defaultChecked={valor} /> {label}
    </label>
  );
}

function Numero({
  nombre,
  label,
  ilimitado = false,
  valor,
  onChange,
}: {
  nombre: string;
  label: string;
  ilimitado?: boolean;
  valor: number | null;
  onChange?: (val: number | null) => void;
}) {
  return (
    <label>
      {label}
      <input
        name={nombre}
        value={onChange ? (valor === null ? '*' : String(valor)) : undefined}
        defaultValue={!onChange ? (valor === null ? '*' : String(valor)) : undefined}
        onChange={(e) => {
          if (!onChange) return;
          const v = e.target.value.trim();
          if (v === '*' && ilimitado) {
            onChange(null);
          } else {
            const parsed = parseInt(v, 10);
            onChange(isNaN(parsed) ? 0 : parsed);
          }
        }}
        required
        pattern={ilimitado ? '[0-9]+|\\*' : '[0-9]+'}
      />
    </label>
  );
}
