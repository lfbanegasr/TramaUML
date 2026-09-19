package bo.edu.uml.ia.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.dto.IaDto.*;
import bo.edu.uml.ia.repositorio.IaRepositorio;
import bo.edu.uml.ia.repositorio.IaRepositorio.CabeceraModelo;
import bo.edu.uml.ia.repositorio.IaRepositorio.PropuestaBloqueada;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Interpretacion;
import bo.edu.uml.modelado.dto.ModeladoDto.Cambio;
import bo.edu.uml.modelado.dto.ModeladoDto.Confirmacion;
import bo.edu.uml.modelado.servicio.EdicionTransaccional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import javax.imageio.ImageIO;

@Service
public class IaServicio {
    private static final long MAX_AUDIO = 15L * 1024 * 1024;
    private static final long MAX_IMAGEN = 10L * 1024 * 1024;

    private final IaRepositorio repo;
    private final MotorAsistenciaInteligente motor;
    private final MotorVisionDiagrama vision;
    private final ProveedorIa proveedor;
    private final EdicionTransaccional edicion;
    private final ObjectMapper json;

    public IaServicio(
            IaRepositorio repo,
            MotorAsistenciaInteligente motor,
            MotorVisionDiagrama vision,
            ProveedorIa proveedor,
            EdicionTransaccional edicion,
            ObjectMapper json) {
        this.repo = repo;
        this.motor = motor;
        this.vision = vision;
        this.proveedor = proveedor;
        this.edicion = edicion;
        this.json = json;
    }

    @Transactional
    public ResultadoSolicitud solicitarTexto(
            Identidad actor, UUID modelo, SolicitudTexto solicitud) {
        CabeceraModelo cabecera = autorizar(actor, modelo, "SOLICITAR_IA");
        UUID idSolicitud =
                repo.crearSolicitud(
                        modelo,
                        cabecera.revision(),
                        actor.id(),
                        "TEXTO",
                        solicitud.prompt().trim(),
                        null);
        return procesar(
                idSolicitud,
                cabecera,
                solicitud.prompt().trim(),
                () -> motor.interpretar(solicitud.prompt().trim(), contexto(modelo)));
    }

    @Transactional
    public ResultadoSolicitud solicitarVoz(
            Identidad actor, UUID modelo, MultipartFile archivo, String textoTranscrito) {
        CabeceraModelo cabecera = autorizar(actor, modelo, "SOLICITAR_IA");
        byte[] contenido = leerAudio(archivo);
        UUID idArchivo =
                repo.guardarArchivo(
                        cabecera.proyecto(),
                        actor.id(),
                        "AUDIO_VOZ",
                        nombreSeguro(archivo.getOriginalFilename(), "voz.webm"),
                        tipoAudio(archivo.getContentType()),
                        contenido);
        UUID idSolicitud =
                repo.crearSolicitud(
                        modelo, cabecera.revision(), actor.id(), "VOZ", null, idArchivo);
        String texto = textoTranscrito;
        if (texto == null || texto.isBlank())
            texto =
                    proveedor
                            .transcribir(
                                    contenido,
                                    tipoAudio(archivo.getContentType()),
                                    nombreSeguro(archivo.getOriginalFilename(), "voz.webm"))
                            .orElse(null);
        if (texto == null || texto.isBlank()) {
            String mensaje =
                    "El audio se conservó, pero no hay un proveedor de transcripción disponible.";
            repo.fallarSolicitud(idSolicitud, mensaje);
            return fallida(idSolicitud, cabecera.revision(), mensaje);
        }
        texto = texto.trim();
        String textoProcesado = texto;
        return procesar(
                idSolicitud,
                cabecera,
                textoProcesado,
                () -> motor.interpretar(textoProcesado, contexto(modelo)));
    }

    @Transactional
    public ResultadoSolicitud solicitarImagen(
            Identidad actor, UUID modelo, MultipartFile archivo, String textoDetectado) {
        CabeceraModelo cabecera = autorizar(actor, modelo, "SOLICITAR_IA");
        byte[] contenido = leerImagen(archivo);
        UUID idArchivo =
                repo.guardarArchivo(
                        cabecera.proyecto(),
                        actor.id(),
                        "IMAGEN_DIAGRAMA",
                        nombreSeguro(archivo.getOriginalFilename(), "diagrama.png"),
                        archivo.getContentType(),
                        contenido);
        UUID idSolicitud =
                repo.crearSolicitud(
                        modelo, cabecera.revision(), actor.id(), "IMAGEN", null, idArchivo);
        return procesar(
                idSolicitud,
                cabecera,
                textoDetectado == null ? null : textoDetectado.trim(),
                () ->
                        vision.interpretar(
                                contenido,
                                archivo.getContentType(),
                                textoDetectado,
                                contexto(modelo)));
    }

    @Transactional(readOnly = true)
    public Optional<Propuesta> vigente(Identidad actor, UUID modelo) {
        autorizar(actor, modelo, "REVISAR_PROPUESTA_IA");
        return repo.propuestaVigente(modelo).map(id -> repo.detalle(modelo, id));
    }

    @Transactional(readOnly = true)
    public Propuesta detalle(Identidad actor, UUID modelo, UUID propuesta) {
        autorizar(actor, modelo, "REVISAR_PROPUESTA_IA");
        return repo.detalle(modelo, propuesta);
    }

    @Transactional
    public ResultadoAplicacion aplicar(Identidad actor, UUID modelo, UUID propuesta) {
        PropuestaBloqueada fila = repo.bloquear(modelo, propuesta);
        autorizar(actor, modelo, "APLICAR_PROPUESTA_IA");
        if (!"LISTO".equals(fila.estado()))
            throw new ApiException(409, "La propuesta ya fue procesada o no está disponible.");
        if (fila.revisionBase() != fila.revisionActual()) {
            repo.desactualizar(propuesta);
            return new ResultadoAplicacion(
                    propuesta,
                    "DESACTUALIZADO",
                    false,
                    fila.revisionActual(),
                    null,
                    "El modelo cambió; genera y revisa una propuesta nueva.");
        }
        if (repo.dependenciasInvalidas(propuesta))
            throw new ApiException(422, "La selección omite una operación requerida.");
        var acciones = repo.accionesAplicables(propuesta);
        if (acciones.isEmpty())
            throw new ApiException(
                    422, "La propuesta no contiene operaciones válidas seleccionadas.");
        UUID sesion =
                repo.sesionEditable(modelo, actor.id())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                409,
                                                "Conecta una sesión colaborativa editable antes de"
                                                        + " aplicar la propuesta."));
        UUID cambio = UUID.randomUUID();
        Cambio solicitud = new Cambio(sesion, cambio, fila.revisionBase(), List.copyOf(acciones));
        Confirmacion confirmacion = edicion.aplicar(actor, modelo, solicitud, serializar(acciones));
        repo.marcarAplicada(propuesta, actor.id(), confirmacion.idVersion());
        return new ResultadoAplicacion(
                propuesta,
                "APLICADO",
                true,
                confirmacion.revision(),
                confirmacion.idVersion(),
                "La propuesta se aplicó como una única versión del modelo.");
    }

    @Transactional
    public ResultadoRechazo rechazar(
            Identidad actor, UUID modelo, UUID propuesta, Rechazo solicitud) {
        PropuestaBloqueada fila = repo.bloquear(modelo, propuesta);
        autorizar(actor, modelo, "APLICAR_PROPUESTA_IA");
        if (!"LISTO".equals(fila.estado()))
            throw new ApiException(409, "La propuesta ya fue procesada o no está disponible.");
        String motivo =
                solicitud == null || solicitud.motivo() == null || solicitud.motivo().isBlank()
                        ? null
                        : solicitud.motivo().trim();
        repo.rechazar(propuesta, actor.id(), motivo);
        return new ResultadoRechazo(propuesta, "RECHAZADO", motivo);
    }

    private ResultadoSolicitud procesar(
            UUID solicitud,
            CabeceraModelo cabecera,
            String textoProcesado,
            Interpretador interpretador) {
        repo.procesando(solicitud);
        try {
            Interpretacion interpretacion = interpretador.interpretar();
            UUID propuesta =
                    repo.guardarPropuesta(
                            solicitud,
                            interpretacion.resumen(),
                            interpretacion.confianzaGeneral(),
                            interpretacion.operaciones());
            repo.completarSolicitud(solicitud, textoProcesado);
            return new ResultadoSolicitud(
                    solicitud,
                    propuesta,
                    "COMPLETADO",
                    interpretacion.resumen(),
                    interpretacion.confianzaGeneral(),
                    cabecera.revision(),
                    "Propuesta lista para revisión.");
        } catch (ApiException e) {
            repo.fallarSolicitud(solicitud, e.getMessage());
            return fallida(solicitud, cabecera.revision(), e.getMessage());
        }
    }

    private Contexto contexto(UUID modelo) {
        return new Contexto(repo.cantidadClases(modelo), repo.nombresClases(modelo));
    }

    private CabeceraModelo autorizar(Identidad actor, UUID modelo, String permiso) {
        CabeceraModelo cabecera = repo.cabecera(modelo);
        if (!"ACTIVO".equals(cabecera.estadoProyecto())
                || "ARCHIVADO".equals(cabecera.estadoModelo()))
            throw new ApiException(404, "Modelo no disponible.");
        if (!actor.esAdministrador() && !repo.permiso(actor.id(), cabecera.proyecto(), permiso))
            throw new ApiException(
                    403, "No tienes permiso para utilizar esta función inteligente.");
        return cabecera;
    }

    private byte[] leerAudio(MultipartFile archivo) {
        byte[] contenido = leer(archivo, MAX_AUDIO, "audio");
        String tipo = tipoAudio(archivo.getContentType());
        if (!Set.of("audio/webm", "audio/wav", "audio/x-wav", "audio/ogg").contains(tipo))
            throw new ApiException(415, "El audio debe estar en formato WebM, WAV u OGG.");
        return contenido;
    }

    private byte[] leerImagen(MultipartFile archivo) {
        byte[] contenido = leer(archivo, MAX_IMAGEN, "imagen");
        if (!Set.of("image/png", "image/jpeg").contains(archivo.getContentType()))
            throw new ApiException(415, "La imagen debe estar en formato PNG o JPG.");
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
            if (imagen == null)
                throw new ApiException(422, "El archivo no contiene una imagen válida.");
            if (imagen.getWidth() > 12000 || imagen.getHeight() > 12000)
                throw new ApiException(413, "Las dimensiones de la imagen son demasiado grandes.");
        } catch (IOException e) {
            throw new ApiException(422, "No se pudo leer la imagen.");
        }
        return contenido;
    }

    private byte[] leer(MultipartFile archivo, long limite, String nombreTipo) {
        if (archivo == null || archivo.isEmpty())
            throw new ApiException(400, "Selecciona un archivo de " + nombreTipo + ".");
        if (archivo.getSize() > limite)
            throw new ApiException(
                    413, "El archivo de " + nombreTipo + " supera el límite permitido.");
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new ApiException(400, "No se pudo leer el archivo de " + nombreTipo + ".");
        }
    }

    private String tipoAudio(String tipo) {
        if (tipo == null) return "application/octet-stream";
        int separador = tipo.indexOf(';');
        return (separador < 0 ? tipo : tipo.substring(0, separador)).toLowerCase(Locale.ROOT);
    }

    private String nombreSeguro(String original, String predeterminado) {
        if (original == null || original.isBlank()) return predeterminado;
        String nombre = original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        return nombre.isBlank()
                ? predeterminado
                : nombre.substring(0, Math.min(nombre.length(), 255));
    }

    private String serializar(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "La propuesta contiene datos inválidos.");
        }
    }

    private ResultadoSolicitud fallida(UUID solicitud, long revision, String mensaje) {
        return new ResultadoSolicitud(solicitud, null, "FALLIDO", null, null, revision, mensaje);
    }

    @FunctionalInterface
    private interface Interpretador {
        Interpretacion interpretar();
    }
}
