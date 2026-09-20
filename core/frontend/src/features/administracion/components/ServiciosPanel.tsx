import { useEffect, useState } from 'react';
import * as api from '../api/administracionApi';
import { ServicioForm } from './ServicioForm';

export function ServiciosPanel({
  onEdicionChange,
}: {
  onEdicionChange: (activa: boolean) => void;
}) {
  const [datos, setDatos] = useState<api.PaginaServicios | null>(null);
  const [nombre, setNombre] = useState('');
  const [proveedor, setProveedor] = useState('');
  const [tipo, setTipo] = useState('');
  const [estado, setEstado] = useState('');
  const [pagina, setPagina] = useState(0);
  const [editar, setEditar] = useState<api.ServicioPlataforma | null | undefined>(undefined);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [recarga, setRecarga] = useState(0);

  function reemplazarServicio(servicio: api.ServicioPlataforma) {
    setDatos((actual) =>
      actual
        ? {
            ...actual,
            servicios: actual.servicios.map((item) => (item.id === servicio.id ? servicio : item)),
          }
        : actual,
    );
  }

  useEffect(() => {
    onEdicionChange(editar !== undefined || ocupado);
    return () => onEdicionChange(false);
  }, [editar, ocupado, onEdicionChange]);
  useEffect(() => {
    let vigente = true;
    const cargar = async () => {
      try {
        const respuesta = await api.servicios(nombre, proveedor, tipo, estado, pagina);
        if (vigente) {
          setDatos(respuesta);
          setError('');
        }
      } catch (e) {
        if (vigente) setError((e as Error).message);
      }
    };
    const espera = setTimeout(cargar, 180);
    return () => {
      vigente = false;
      clearTimeout(espera);
    };
  }, [nombre, proveedor, tipo, estado, pagina, recarga]);

  async function guardar(datosServicio: api.DatosServicio) {
    setOcupado(true);
    setError('');
    setMensaje('');
    try {
      const guardado = await api.guardarServicio(editar ?? null, datosServicio);
      setMensaje(
        guardado.estado === 'ACTIVO'
          ? 'Servicio guardado y disponible.'
          : 'Servicio guardado sin disponibilidad confirmada; puedes reintentar la prueba.',
      );
      setEditar(undefined);
      setRecarga((valor) => valor + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }

  async function verificar(servicio: api.ServicioPlataforma) {
    setOcupado(true);
    setError('');
    setMensaje('');
    try {
      const verificado = await api.verificarServicio(servicio.id);
      setMensaje(
        verificado.ultimaComprobacion?.estado === 'DISPONIBLE'
          ? 'Conexión comprobada correctamente.'
          : 'El servicio no está disponible; se conservó el resultado para diagnóstico.',
      );
      reemplazarServicio(verificado);
      setRecarga((valor) => valor + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }

  async function alternar(servicio: api.ServicioPlataforma) {
    const nuevoEstado = servicio.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    const confirma =
      nuevoEstado !== 'INACTIVO' ||
      window.confirm(
        'Desactivar este servicio puede interrumpir funciones inteligentes. ¿Continuar?',
      );
    if (!confirma) return;
    setOcupado(true);
    setError('');
    setMensaje('');
    try {
      const actualizado = await api.cambiarEstadoServicio(
        servicio,
        nuevoEstado,
        nuevoEstado === 'INACTIVO',
      );
      setMensaje(nuevoEstado === 'ACTIVO' ? 'Servicio activado.' : 'Servicio desactivado.');
      reemplazarServicio(actualizado);
      setRecarga((valor) => valor + 1);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }

  if (editar !== undefined)
    return (
      <div className="management-form">
        <ServicioForm
          key={editar?.id ?? 'nuevo-servicio'}
          servicio={editar}
          ocupado={ocupado}
          guardar={guardar}
          cancelar={() => setEditar(undefined)}
        />
      </div>
    );

  return (
    <section aria-labelledby="servicios-titulo">
      <div className="panel-heading">
        <div>
          <h2 id="servicios-titulo">Servicios inteligentes</h2>
          <p className="muted">
            Configura texto, voz e imagen sin exponer credenciales almacenadas.
          </p>
        </div>
        <button className="primary" disabled={ocupado} onClick={() => setEditar(null)}>
          Registrar servicio
        </button>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
          <button onClick={() => setRecarga((valor) => valor + 1)}>Reintentar</button>
        </p>
      )}
      {mensaje && (
        <p className="success" role="status">
          {mensaje}
        </p>
      )}
      <div className="filter-bar">
        <input
          aria-label="Buscar servicios"
          placeholder="Buscar servicio por nombre"
          value={nombre}
          onChange={(e) => {
            setNombre(e.target.value);
            setPagina(0);
          }}
        />
        <select
          aria-label="Filtrar proveedor de servicios"
          value={proveedor}
          onChange={(e) => {
            setProveedor(e.target.value);
            setPagina(0);
          }}
        >
          <option value="">Todos los proveedores</option>
          {['LOCAL', 'OPENAI', 'GEMINI', 'OLLAMA'].map((valor) => (
            <option key={valor}>{valor}</option>
          ))}
        </select>
        <select
          aria-label="Filtrar tipo de servicio"
          value={tipo}
          onChange={(e) => {
            setTipo(e.target.value);
            setPagina(0);
          }}
        >
          <option value="">Todos los tipos</option>
          <option value="IA_TEXTO">Texto</option>
          <option value="VOZ_A_TEXTO">Voz a texto</option>
          <option value="IA_IMAGEN">Imagen</option>
        </select>
        <select
          aria-label="Filtrar estado de servicios"
          value={estado}
          onChange={(e) => {
            setEstado(e.target.value);
            setPagina(0);
          }}
        >
          <option value="">Todos los estados</option>
          {['ACTIVO', 'INACTIVO', 'SIN_VERIFICAR', 'NO_DISPONIBLE'].map((valor) => (
            <option key={valor}>{valor}</option>
          ))}
        </select>
      </div>
      <div className="table-scroll">
        <table className="management-table services-table">
          <thead>
            <tr>
              <th>Servicio</th>
              <th>Estado</th>
              <th>Última verificación</th>
              <th>Credencial</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {datos?.servicios.map((servicio) => (
              <tr key={servicio.id}>
                <td>
                  <b>{servicio.nombre}</b>
                  <small>
                    {servicio.tipoServicio} · {servicio.proveedor}
                  </small>
                </td>
                <td>
                  <span className={servicio.estado === 'ACTIVO' ? 'badge active' : 'badge'}>
                    {servicio.estado.replaceAll('_', ' ')}
                  </span>
                </td>
                <td>
                  {servicio.ultimaComprobacion ? (
                    <>
                      <b>{servicio.ultimaComprobacion.estado}</b>
                      <small>
                        {servicio.ultimaComprobacion.latenciaMs ?? '—'} ms ·{' '}
                        {new Date(servicio.ultimaComprobacion.comprobadoEn).toLocaleString()}
                      </small>
                      <small>{servicio.ultimaComprobacion.detalle}</small>
                    </>
                  ) : (
                    'Sin comprobar'
                  )}
                </td>
                <td>{servicio.tieneCredencial ? 'Protegida' : 'No configurada'}</td>
                <td>
                  <button disabled={ocupado} onClick={() => setEditar(servicio)}>
                    Editar {servicio.nombre}
                  </button>
                  <button disabled={ocupado} onClick={() => verificar(servicio)}>
                    Probar conexión {servicio.nombre}
                  </button>
                  <button disabled={ocupado} onClick={() => alternar(servicio)}>
                    {servicio.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'} {servicio.nombre}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {datos?.total === 0 && <p className="empty">No hay servicios con esos filtros.</p>}
      {datos && datos.total > datos.tamano && (
        <div className="pagination">
          <button disabled={pagina === 0} onClick={() => setPagina((valor) => valor - 1)}>
            Página anterior
          </button>
          <span>
            Página {pagina + 1} · {datos.total} servicios
          </span>
          <button
            disabled={(pagina + 1) * datos.tamano >= datos.total}
            onClick={() => setPagina((valor) => valor + 1)}
          >
            Página siguiente
          </button>
        </div>
      )}
    </section>
  );
}
