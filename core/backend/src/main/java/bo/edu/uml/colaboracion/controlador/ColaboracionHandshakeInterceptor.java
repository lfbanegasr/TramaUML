package bo.edu.uml.colaboracion.controlador;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.colaboracion.servicio.ColaboracionServicio;
import bo.edu.uml.compartido.ApiException;

import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Component
public class ColaboracionHandshakeInterceptor implements HandshakeInterceptor {
    static final String ACTOR = "actor";
    static final String MODELO = "modelo";
    static final String SESION = "sesion";
    static final String PROYECTO = "proyecto";

    private final ColaboracionServicio servicio;

    public ColaboracionHandshakeInterceptor(ColaboracionServicio servicio) {
        this.servicio = servicio;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        try {
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
            if (autenticacion == null || !(autenticacion.getPrincipal() instanceof Identidad actor))
                throw new ApiException(401, "Inicia sesión para continuar.");
            String[] ruta = request.getURI().getPath().split("/");
            if (ruta.length != 6
                    || !"ws".equals(ruta[1])
                    || !"modelos".equals(ruta[2])
                    || !"sesiones".equals(ruta[4]))
                throw new ApiException(404, "Canal colaborativo no disponible.");
            UUID modelo = UUID.fromString(ruta[3]);
            UUID sesion = UUID.fromString(ruta[5]);
            var acceso = servicio.autorizarCanal(actor, modelo, sesion);
            attributes.put(ACTOR, acceso.actor());
            attributes.put(MODELO, modelo);
            attributes.put(SESION, sesion);
            attributes.put(PROYECTO, acceso.proyecto());
            return true;
        } catch (ApiException e) {
            response.setStatusCode(HttpStatusCode.valueOf(e.status()));
            return false;
        } catch (IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}
}
