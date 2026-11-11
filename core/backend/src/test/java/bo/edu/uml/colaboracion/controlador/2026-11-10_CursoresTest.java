package bo.edu.uml.colaboracion.controlador;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.servicio.ColaboracionServicio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.*;

class CursoresTest {
  final ObjectMapper json = new ObjectMapper();
  final ColaboracionServicio servicio = mock(ColaboracionServicio.class);
  final ColaboracionWebSocketHandler handler = new ColaboracionWebSocketHandler(servicio, json);
  final UUID modelo = UUID.randomUUID(), sesion = UUID.randomUUID();
  final Identidad actor = new Identidad(UUID.randomUUID(), "ana", "Ana", List.of(), List.of());

  WebSocketSession conectar(UUID sala) throws Exception {
    var socket = mock(WebSocketSession.class);
    when(socket.getId()).thenReturn(UUID.randomUUID().toString());
    when(socket.isOpen()).thenReturn(true);
    when(socket.getAttributes())
        .thenReturn(
            Map.of(
                ColaboracionHandshakeInterceptor.ACTOR, actor,
                ColaboracionHandshakeInterceptor.MODELO, modelo,
                ColaboracionHandshakeInterceptor.SESION, sala,
                ColaboracionHandshakeInterceptor.PROYECTO, UUID.randomUUID()));
    handler.afterConnectionEstablished(socket);
    clearInvocations(socket);
    return socket;
  }

  @Test
  void transmiteCoordenadasSoloEnLaMismaSesion() throws Exception {
    var origen = conectar(sesion);
    var receptor = conectar(sesion);
    var otraSala = conectar(UUID.randomUUID());
    handler.handleTextMessage(
        origen,
        new TextMessage(
            """
            {"tipo":"CURSOR","x":125.5,"y":-40,"accion":"MOVIENDO"}
            """));
    var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
    verify(receptor).sendMessage(captor.capture());
    var evento = json.readTree((String) captor.getValue().getPayload());
    assertThat(evento.path("tipo").asText()).isEqualTo("CURSOR");
    assertThat(evento.path("datos").path("actor").asText()).isEqualTo(actor.id().toString());
    assertThat(evento.path("datos").path("x").asDouble()).isEqualTo(125.5);
    verify(otraSala, never()).sendMessage(any());
    verify(servicio, never()).enfocar(any(), any(), any(), any());
  }

  @Test
  void salirDelLienzoTransmiteCursorNulo() throws Exception {
    var origen = conectar(sesion);
    var receptor = conectar(sesion);
    handler.handleTextMessage(
        origen, new TextMessage("{\"tipo\":\"CURSOR\",\"x\":null,\"y\":null}"));
    var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
    verify(receptor).sendMessage(captor.capture());
    assertThat(
            json.readTree((String) captor.getValue().getPayload()).path("datos").path("x").isNull())
        .isTrue();
  }
}
