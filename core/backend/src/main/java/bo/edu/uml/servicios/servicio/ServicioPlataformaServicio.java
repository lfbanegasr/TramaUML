package bo.edu.uml.servicios.servicio;

import static bo.edu.uml.servicios.dto.ServicioPlataformaDto.*;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.compartido.Auditoria;
import bo.edu.uml.servicios.repositorio.ServicioPlataformaRepositorio;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ServicioPlataformaServicio {
    private static final int MAXIMO_CONFIGURACION = 16_384;
    private static final Set<String> CLAVES_SENSIBLES =
            Set.of(
                    "secret",
                    "secreto",
                    "token",
                    "password",
                    "contrasena",
                    "contraseña",
                    "clave",
                    "apikey",
                    "api_key",
                    "authorization",
                    "credencial");

    private final ServicioPlataformaRepositorio repo;
    private final AccesoRepositorio acceso;
    private final CifradorSecretosServicio cifrador;
    private final ComprobadorServicioPlataforma comprobador;

    public ServicioPlataformaServicio(
            ServicioPlataformaRepositorio repo,
            AccesoRepositorio acceso,
            CifradorSecretosServicio cifrador,
            ComprobadorServicioPlataforma comprobador) {
        this.repo = repo;
        this.acceso = acceso;
        this.cifrador = cifrador;
        this.comprobador = comprobador;
    }

    @Transactional(readOnly = true)
    public PaginaServicios listar(
            Identidad actor,
            String nombre,
            String proveedor,
            String tipo,
            String estado,
            int pagina,
            int tamano) {
        autorizar(actor);
        return repo.listar(
                limpiar(nombre),
                normalizarFiltro(proveedor),
                normalizarFiltro(tipo),
                normalizarFiltro(estado),
                pagina,
                tamano);
    }

    @Transactional(readOnly = true)
    public Servicio detalle(Identidad actor, UUID id) {
        autorizar(actor);
        return repo.detalle(id);
    }

    @Transactional
    public Servicio crear(Identidad actor, GuardarServicio solicitud) {
        autorizar(actor);
        Datos datos = validar(solicitud, true);
        repo.bloquearEscrituras();
        validarDuplicados(null, datos);

        byte[] secretoCifrado =
                datos.credencial() == null ? null : cifrador.cifrar(datos.credencial());
        var resultado = comprobar(datos);
        rechazarCredencialInvalida(resultado);
        UUID id = UUID.randomUUID();
        repo.insertar(
                id,
                actor.id(),
                datos.nombre(),
                datos.proveedor(),
                datos.tipo(),
                datos.url(),
                datos.configuracion(),
                resultado.disponible() ? "ACTIVO" : "SIN_VERIFICAR");
        if (secretoCifrado != null)
            repo.reemplazarSecreto(
                    id,
                    secretoCifrado,
                    CifradorSecretosServicio.ALGORITMO,
                    cifrador.versionClave());
        registrarComprobacion(id, actor.id(), resultado);
        Auditoria.despuesDelCommit(actor.id(), "CREAR_SERVICIO_PLATAFORMA", id);
        return repo.detalle(id);
    }

    @Transactional
    public Servicio actualizar(Identidad actor, UUID id, GuardarServicio solicitud) {
        autorizar(actor);
        Datos datos = validar(solicitud, false);
        repo.bloquearEscrituras();
        Servicio existente = repo.detalle(id);
        if (solicitud.revision() == null || solicitud.revision().isBlank())
            throw new ApiException(400, "La revisión del servicio es obligatoria.");
        validarDuplicados(id, datos);

        String credencialPrueba = credencialParaPrueba(existente, datos);
        Datos comprobables = datos.conCredencial(credencialPrueba);
        byte[] secretoCifrado =
                datos.credencial() == null ? null : cifrador.cifrar(datos.credencial());
        var resultado = comprobar(comprobables);
        rechazarCredencialInvalida(resultado);
        String estado =
                "INACTIVO".equals(existente.estado())
                        ? "INACTIVO"
                        : resultado.disponible() ? "ACTIVO" : "SIN_VERIFICAR";
        repo.actualizar(
                id,
                datos.nombre(),
                datos.proveedor(),
                datos.tipo(),
                datos.url(),
                datos.configuracion(),
                estado,
                solicitud.revision());
        if (datos.retirarCredencial()) repo.retirarSecreto(id, false);
        else if (secretoCifrado != null)
            repo.reemplazarSecreto(
                    id,
                    secretoCifrado,
                    CifradorSecretosServicio.ALGORITMO,
                    cifrador.versionClave());
        registrarComprobacion(id, actor.id(), resultado);
        Auditoria.despuesDelCommit(actor.id(), "ACTUALIZAR_SERVICIO_PLATAFORMA", id);
        return repo.detalle(id);
    }

    @Transactional
    public Servicio verificar(Identidad actor, UUID id) {
        autorizar(actor);
        repo.bloquearEscrituras();
        Servicio servicio = repo.detalle(id);
        String credencial = credencialActual(servicio);
        var resultado =
                comprobador.comprobar(
                        servicio.proveedor(),
                        servicio.urlServicio(),
                        servicio.configuracion(),
                        credencial);
        registrarComprobacion(id, actor.id(), resultado);
        if (!"INACTIVO".equals(servicio.estado()))
            repo.actualizarEstadoComprobado(
                    id, resultado.disponible() ? "ACTIVO" : "NO_DISPONIBLE");
        Auditoria.despuesDelCommit(actor.id(), "COMPROBAR_SERVICIO_PLATAFORMA", id);
        return repo.detalle(id);
    }

    @Transactional
    public Servicio cambiarEstado(Identidad actor, UUID id, CambiarEstado solicitud) {
        autorizar(actor);
        repo.bloquearEscrituras();
        Servicio servicio = repo.detalle(id);
        if (solicitud.estado().equals(servicio.estado())) return servicio;
        if ("INACTIVO".equals(solicitud.estado())) {
            if (!solicitud.confirmarImpacto())
                throw new ApiException(
                        409,
                        "Confirma la desactivación porque puede afectar funciones inteligentes.");
            if (repo.tieneSolicitudEnCurso(id))
                throw new ApiException(
                        409,
                        "El servicio participa en una solicitud en curso; espera su finalización.");
        } else if (servicio.ultimaComprobacion() == null
                || !"DISPONIBLE".equals(servicio.ultimaComprobacion().estado())) {
            throw new ApiException(422, "Comprueba el servicio antes de activarlo.");
        }
        repo.cambiarEstado(id, solicitud.estado(), solicitud.revision());
        Auditoria.despuesDelCommit(
                actor.id(),
                "ACTIVO".equals(solicitud.estado())
                        ? "ACTIVAR_SERVICIO_PLATAFORMA"
                        : "DESACTIVAR_SERVICIO_PLATAFORMA",
                id);
        return repo.detalle(id);
    }

    private void autorizar(Identidad actor) {
        Identidad vigente = acceso.identidad(actor.id());
        if (!vigente.esAdministrador()
                && !vigente.permisosGlobales().contains("CONFIGURAR_SERVICIOS"))
            throw new ApiException(403, "No tienes permiso para configurar servicios.");
    }

    private Datos validar(GuardarServicio solicitud, boolean nuevo) {
        if (!solicitud.configuracion().isObject())
            throw new ApiException(422, "La configuración debe ser un objeto JSON.");
        if (solicitud.configuracion().toString().length() > MAXIMO_CONFIGURACION)
            throw new ApiException(422, "La configuración del servicio es demasiado extensa.");
        validarSinSecretos(solicitud.configuracion());
        if (solicitud.retirarCredencial()
                && solicitud.credencial() != null
                && !solicitud.credencial().isBlank())
            throw new ApiException(
                    422, "No puedes reemplazar y retirar la credencial al mismo tiempo.");
        if (nuevo && solicitud.retirarCredencial())
            throw new ApiException(422, "Un servicio nuevo no tiene una credencial que retirar.");

        String proveedor = solicitud.proveedor().toUpperCase(Locale.ROOT);
        String url = limpiar(solicitud.urlServicio());
        if ("LOCAL".equals(proveedor)) {
            if (!url.isBlank())
                throw new ApiException(422, "El motor local no utiliza una URL externa.");
            url = null;
        } else {
            if (url.isBlank()) throw new ApiException(422, "La URL del servicio es obligatoria.");
            if (solicitud.configuracion().path("modelo").asText("").isBlank())
                throw new ApiException(422, "Indica el modelo remoto dentro de la configuración.");
        }
        String credencial =
                solicitud.credencial() == null || solicitud.credencial().isBlank()
                        ? null
                        : solicitud.credencial();
        return new Datos(
                solicitud.nombre().trim(),
                proveedor,
                solicitud.tipoServicio(),
                url,
                solicitud.configuracion(),
                credencial,
                solicitud.retirarCredencial());
    }

    private void validarDuplicados(UUID id, Datos datos) {
        if (repo.existeNombre(id, datos.nombre()))
            throw new ApiException(409, "Ya existe un servicio con ese nombre.");
        if (repo.existeEquivalente(
                id, datos.proveedor(), datos.tipo(), datos.url(), datos.configuracion()))
            throw new ApiException(409, "Ya existe una configuración de servicio equivalente.");
    }

    private String credencialParaPrueba(Servicio existente, Datos datos) {
        if ("LOCAL".equals(datos.proveedor()) || datos.retirarCredencial()) return null;
        if (datos.credencial() != null) return datos.credencial();
        return credencialActual(existente);
    }

    private String credencialActual(Servicio servicio) {
        if ("LOCAL".equals(servicio.proveedor())) return null;
        return repo.secretoActivo(servicio.id())
                .map(
                        secreto ->
                                cifrador.descifrar(
                                        secreto.contenido(),
                                        secreto.algoritmo(),
                                        secreto.versionClave()))
                .orElse(null);
    }

    private ComprobadorServicioPlataforma.Resultado comprobar(Datos datos) {
        return comprobador.comprobar(
                datos.proveedor(), datos.url(), datos.configuracion(), datos.credencial());
    }

    private void rechazarCredencialInvalida(ComprobadorServicioPlataforma.Resultado resultado) {
        if (resultado.detalle().contains("rechazó la credencial"))
            throw new ApiException(422, "El proveedor rechazó la credencial indicada.");
    }

    private void registrarComprobacion(
            UUID servicio, UUID actor, ComprobadorServicioPlataforma.Resultado resultado) {
        repo.guardarComprobacion(
                servicio, actor, resultado.estado(), resultado.latenciaMs(), resultado.detalle());
    }

    private void validarSinSecretos(JsonNode nodo) {
        if (nodo.isObject()) {
            Iterator<String> nombres = nodo.fieldNames();
            while (nombres.hasNext()) {
                String nombre = nombres.next();
                String normalizado = nombre.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_ñ]", "");
                if (CLAVES_SENSIBLES.stream().anyMatch(normalizado::contains))
                    throw new ApiException(
                            422,
                            "Las credenciales deben enviarse en el campo protegido, no dentro de"
                                    + " la configuración.");
                validarSinSecretos(nodo.get(nombre));
            }
        } else if (nodo.isArray()) nodo.forEach(this::validarSinSecretos);
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String normalizarFiltro(String valor) {
        return limpiar(valor).toUpperCase(Locale.ROOT);
    }

    private record Datos(
            String nombre,
            String proveedor,
            String tipo,
            String url,
            JsonNode configuracion,
            String credencial,
            boolean retirarCredencial) {
        Datos conCredencial(String valor) {
            return new Datos(nombre, proveedor, tipo, url, configuracion, valor, retirarCredencial);
        }
    }
}
