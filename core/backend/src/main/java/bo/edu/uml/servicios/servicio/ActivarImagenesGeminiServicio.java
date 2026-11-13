package bo.edu.uml.servicios.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.acceso.repositorio.AccesoRepositorio;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.compartido.Auditoria;
import bo.edu.uml.servicios.dto.ServicioPlataformaDto.Servicio;
import bo.edu.uml.servicios.repositorio.ServicioPlataformaRepositorio;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ActivarImagenesGeminiServicio {
    private final ServicioPlataformaRepositorio repo;
    private final AccesoRepositorio acceso;
    private final CifradorSecretosServicio cifrador;
    private final ResolverServicioActivo resolver;
    private final ComprobadorServicioPlataforma comprobador;
    private final TransactionTemplate tx;
    private final String claveEntorno, modeloEntorno;
    private static final List<String> MODELOS_RESPALDO =
            List.of("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.1-flash-lite");

    public ActivarImagenesGeminiServicio(ServicioPlataformaRepositorio repo, AccesoRepositorio acceso,
            CifradorSecretosServicio cifrador, ResolverServicioActivo resolver,
            ComprobadorServicioPlataforma comprobador, PlatformTransactionManager manager,
            @Value("${uml.ia.clave:}") String clave,
            @Value("${uml.ia.modelo-vision:gemini-3.8-flash}") String modelo) {
        this.repo = repo; this.acceso = acceso; this.cifrador = cifrador;
        this.resolver = resolver; this.comprobador = comprobador;
        this.tx = new TransactionTemplate(manager);
        this.claveEntorno = clave == null ? "" : clave.trim();
        this.modeloEntorno = modelo;
    }

    public Servicio activar(Identidad actor) {
        autorizar(actor);
        // Reutiliza secretos del servidor, nunca los devuelve al navegador.
        String clave = resolver.resolver("IA_TEXTO")
                .filter(c -> "GEMINI".equals(c.proveedor()) && !c.credencial().isBlank())
                .map(ResolverServicioActivo.ConfiguracionActiva::credencial)
                .orElseGet(() -> resolver.resolver("IA_IMAGEN")
                        .filter(c -> "GEMINI".equals(c.proveedor()) && !c.credencial().isBlank())
                        .map(ResolverServicioActivo.ConfiguracionActiva::credencial).orElse(claveEntorno));
        if (clave.isBlank()) throw new ApiException(422,
                "Configura la API key de Gemini en el servicio de texto o en UML_IA_CLAVE de Render y vuelve a habilitar imágenes.");
        byte[] secreto = cifrador.cifrar(clave);
        var candidatos = new LinkedHashSet<String>();
        if (modeloEntorno != null && modeloEntorno.matches("[A-Za-z0-9._-]+")) candidatos.add(modeloEntorno);
        candidatos.addAll(MODELOS_RESPALDO);
        ComprobadorServicioPlataforma.Resultado ultima = null;
        for (String modelo : candidatos) {
            var prueba = comprobador.comprobarImagenGemini(modelo, clave);
            ultima = prueba;
            if (prueba.disponible()) return guardar(actor, modelo, secreto, prueba);
            if (prueba.detalle().contains("rechazó la credencial")
                    || prueba.detalle().toLowerCase(Locale.ROOT).contains("cuota")) break;
        }
        throw new ApiException(503, "El servicio de imágenes no se activó: "
                + (ultima == null ? "no se pudo comprobar Gemini." : ultima.detalle()));
    }

    private Servicio guardar(Identidad actor, String modelo, byte[] secreto,
            ComprobadorServicioPlataforma.Resultado prueba) {
        // La prueba de red termina ANTES de abrir la transacción de escritura.
        return tx.execute(status -> {
            autorizar(actor);
            repo.bloquearEscrituras();
            var existente = repo.listar("", "GEMINI", "IA_IMAGEN", "", 0, 100).servicios()
                    .stream().max(Comparator.comparing(Servicio::actualizadoEn)).orElse(null);
            var cfg = JsonNodeFactory.instance.objectNode().put("modelo", modelo);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelo + ":generateContent";
            UUID id;
            if (existente == null) {
                id = UUID.randomUUID();
                String nombre = "Gemini imágenes";
                if (repo.existeNombre(null, nombre)) nombre += " " + id.toString().substring(0, 8);
                repo.insertar(id, actor.id(), nombre, "GEMINI", "IA_IMAGEN", url, cfg, "ACTIVO");
            } else {
                id = existente.id();
                if (repo.tieneSolicitudEnCurso(id)) throw new ApiException(409,
                        "Hay una solicitud de imagen en curso. Espera a que finalice antes de activar el servicio.");
                repo.actualizar(id, existente.nombre(), "GEMINI", "IA_IMAGEN", url, cfg,
                        "ACTIVO", existente.revision());
            }
            repo.reemplazarSecreto(id, secreto, CifradorSecretosServicio.ALGORITMO, cifrador.versionClave());
            repo.guardarComprobacion(id, actor.id(), prueba.estado(), prueba.latenciaMs(), prueba.detalle());
            Auditoria.despuesDelCommit(actor.id(), "ACTIVAR_SERVICIO_PLATAFORMA", id);
            return repo.detalle(id);
        });
    }

    private void autorizar(Identidad actor) {
        var vigente = acceso.identidad(actor.id());
        if (!vigente.esAdministrador() && !vigente.permisosGlobales().contains("CONFIGURAR_SERVICIOS"))
            throw new ApiException(403, "No tienes permiso para configurar servicios.");
    }
}
