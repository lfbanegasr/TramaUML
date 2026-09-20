package bo.edu.uml.servicios.servicio;

import bo.edu.uml.servicios.repositorio.ServicioPlataformaRepositorio;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ResolverServicioActivo {
    private final ServicioPlataformaRepositorio repo;
    private final CifradorSecretosServicio cifrador;

    public ResolverServicioActivo(
            ServicioPlataformaRepositorio repo, CifradorSecretosServicio cifrador) {
        this.repo = repo;
        this.cifrador = cifrador;
    }

    @Transactional(readOnly = true)
    public Optional<ConfiguracionActiva> resolver(String tipo) {
        return repo.activo(tipo)
                .map(
                        servicio -> {
                            String credencial =
                                    "LOCAL".equals(servicio.proveedor())
                                            ? ""
                                            : repo.secretoActivo(servicio.id())
                                                    .map(
                                                            secreto ->
                                                                    cifrador.descifrar(
                                                                            secreto.contenido(),
                                                                            secreto.algoritmo(),
                                                                            secreto.versionClave()))
                                                    .orElse("");
                            JsonNode configuracion = servicio.configuracion();
                            return new ConfiguracionActiva(
                                    servicio.proveedor(),
                                    servicio.urlServicio() == null ? "" : servicio.urlServicio(),
                                    configuracion.path("modelo").asText(""),
                                    credencial);
                        });
    }

    public record ConfiguracionActiva(
            String proveedor, String url, String modelo, String credencial) {}
}
