package bo.edu.uml.colaboracion.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.dto.ColaboracionDto.*;
import bo.edu.uml.colaboracion.servicio.ColaboracionServicio;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.modelado.servicio.EdicionTransaccional.ModeloConfirmado;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ColaboracionWebSocketHandler extends TextWebSocketHandler {
    private final ColaboracionServicio servicio;
    private final ObjectMapper json;
    private final Map<String, Conexion> conexiones = new ConcurrentHashMap<>();

    private record Conexion(
            WebSocketSession socket,
            Identidad actor,
            UUID proyecto,
            UUID modelo,
            UUID sesion,
            String originalId) {}

    private record MensajeEntrada(String tipo, String tipoEntidad, UUID idEntidad) {}

    public ColaboracionWebSocketHandler(ColaboracionServicio servicio, ObjectMapper json) {
        this.servicio = servicio;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var actor = (Identidad) session.getAttributes().get(ColaboracionHandshakeInterceptor.ACTOR);
        var modelo = (UUID) session.getAttributes().get(ColaboracionHandshakeInterceptor.MODELO);
        var sesion = (UUID) session.getAttributes().get(ColaboracionHandshakeInterceptor.SESION);
        var proyecto =
                (UUID) session.getAttributes().get(ColaboracionHandshakeInterceptor.PROYECTO);
        var segura = new ConcurrentWebSocketSessionDecorator(session, 5000, 128 * 1024);
        var conexion = new Conexion(segura, actor, proyecto, modelo, sesion, session.getId());
        conexiones.put(session.getId(), conexion);
        try {
            servicio.conectar(actor, modelo, sesion);
            enviar(segura, new Evento("CONECTADO", Map.of("sesion", sesion)));
        } catch (RuntimeException e) {
            conexiones.remove(session.getId());
            cerrar(segura, CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var conexion = conexiones.get(session.getId());
        if (conexion == null) return;
        if (message.getPayloadLength() > 4096) {
            cerrar(conexion.socket(), CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        try {
            var entrada = json.readValue(message.getPayload(), MensajeEntrada.class);
            if ("PING".equals(entrada.tipo()))
                servicio.pulso(conexion.actor(), conexion.modelo(), conexion.sesion());
            else if ("FOCO".equals(entrada.tipo()))
                servicio.enfocar(
                        conexion.actor(),
                        conexion.modelo(),
                        conexion.sesion(),
                        new Foco(entrada.tipoEntidad(), entrada.idEntidad()));
            else throw new ApiException(400, "Mensaje colaborativo no reconocido.");
        } catch (ApiException e) {
            enviar(conexion.socket(), new Evento("ERROR", Map.of("mensaje", e.getMessage())));
            if (e.status() == 401 || e.status() == 403 || e.status() == 404 || e.status() == 409)
                cerrar(conexion.socket(), CloseStatus.POLICY_VIOLATION);
        } catch (Exception e) {
            enviar(
                    conexion.socket(),
                    new Evento("ERROR", Map.of("mensaje", "Mensaje colaborativo inválido.")));
            cerrar(conexion.socket(), CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var conexion = conexiones.remove(session.getId());
        if (conexion == null || tieneOtraConexion(conexion)) return;
        servicio.ausentar(conexion.actor(), conexion.modelo(), conexion.sesion());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        cerrar(session, CloseStatus.SERVER_ERROR);
    }

    public void publicarPresencia(Sesion sesion) {
        for (var conexion : List.copyOf(conexiones.values())) {
            if (!sesion.idModelo().equals(conexion.modelo())
                    || !sesion.id().equals(conexion.sesion())) continue;
            try {
                servicio.autorizarCanal(conexion.actor(), conexion.modelo(), conexion.sesion());
                var participante =
                        sesion.participantes().stream()
                                .filter(p -> p.idUsuario().equals(conexion.actor().id()))
                                .findFirst();
                if (participante.isEmpty()) {
                    conexiones.remove(conexion.originalId());
                    cerrar(conexion.socket(), CloseStatus.POLICY_VIOLATION);
                    continue;
                }
                var personalizada =
                        new Sesion(
                                sesion.id(),
                                sesion.idModelo(),
                                sesion.modo(),
                                sesion.estado(),
                                sesion.revisionBase(),
                                participante.get().modo(),
                                sesion.participantes());
                enviar(conexion.socket(), new Evento("PRESENCIA", Map.of("sesion", personalizada)));
            } catch (RuntimeException e) {
                conexiones.remove(conexion.originalId());
                cerrar(conexion.socket(), CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    public void publicarCambio(ModeloConfirmado cambio) {
        publicar(
                cambio.modelo(),
                null,
                new Evento(
                        "MODELO_CONFIRMADO",
                        Map.of(
                                "modelo",
                                cambio.modelo(),
                                "cambio",
                                cambio.cambio(),
                                "actor",
                                cambio.actor(),
                                "revision",
                                cambio.revision(),
                                "version",
                                cambio.version())));
    }

    public void revisarAccesos(UUID proyecto) {
        for (var conexion : List.copyOf(conexiones.values())) {
            if (!proyecto.equals(conexion.proyecto())) continue;
            try {
                servicio.pulso(conexion.actor(), conexion.modelo(), conexion.sesion());
            } catch (RuntimeException e) {
                conexiones.remove(conexion.originalId());
                cerrar(conexion.socket(), CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    private void publicar(UUID modelo, UUID sesion, Evento evento) {
        for (var conexion : List.copyOf(conexiones.values())) {
            if (!modelo.equals(conexion.modelo())
                    || (sesion != null && !sesion.equals(conexion.sesion()))) continue;
            try {
                servicio.autorizarCanal(conexion.actor(), conexion.modelo(), conexion.sesion());
                enviar(conexion.socket(), evento);
            } catch (RuntimeException e) {
                conexiones.remove(conexion.originalId());
                cerrar(conexion.socket(), CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    private boolean tieneOtraConexion(Conexion cerrada) {
        return conexiones.values().stream()
                .anyMatch(
                        c ->
                                c.actor().id().equals(cerrada.actor().id())
                                        && c.sesion().equals(cerrada.sesion()));
    }

    private void enviar(WebSocketSession socket, Evento evento) {
        if (!socket.isOpen()) return;
        try {
            socket.sendMessage(new TextMessage(json.writeValueAsString(evento)));
        } catch (IOException e) {
            cerrar(socket, CloseStatus.SERVER_ERROR);
        }
    }

    private void cerrar(WebSocketSession socket, CloseStatus estado) {
        try {
            if (socket.isOpen()) socket.close(estado);
        } catch (IOException ignored) {
            // El transporte ya estaba interrumpido.
        }
    }
}
