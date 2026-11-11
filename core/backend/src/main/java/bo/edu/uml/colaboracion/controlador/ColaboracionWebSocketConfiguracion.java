package bo.edu.uml.colaboracion.controlador;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class ColaboracionWebSocketConfiguracion implements WebSocketConfigurer {
    private final ColaboracionWebSocketHandler handler;
    private final ColaboracionHandshakeInterceptor interceptor;

    public ColaboracionWebSocketConfiguracion(
            ColaboracionWebSocketHandler handler, ColaboracionHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Sin orígenes adicionales: Spring acepta únicamente el mismo origen del frontend.
        registry.addHandler(handler, "/ws/modelos/*/sesiones/*").addInterceptors(interceptor);
    }
}
