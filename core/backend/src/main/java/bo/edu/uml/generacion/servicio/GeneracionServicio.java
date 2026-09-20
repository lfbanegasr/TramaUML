package bo.edu.uml.generacion.servicio;

import bo.edu.uml.acceso.modelo.Identidad;
import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.generacion.dto.GeneracionDto.*;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.CabeceraModelo;
import bo.edu.uml.generacion.repositorio.GeneracionRepositorio.ContenidoArchivo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Servicio de aplicación para CU29/CU30/CU32 — Configuración, generación y descarga del backend
 * Spring Boot generado a partir del diagrama UML.
 */
@Service
public class GeneracionServicio {

    private final GeneracionRepositorio repo;
    private final MotorGeneracionSpringBoot motor;

    public GeneracionServicio(GeneracionRepositorio repo, MotorGeneracionSpringBoot motor) {
        this.repo = repo;
        this.motor = motor;
    }

    // ─── CU29: Configuración ─────────────────────────────────────────────────

    /**
     * Lee la configuración vigente del modelo o devuelve valores predeterminados razonables.
     * Requiere {@code CONFIGURAR_BACKEND}.
     */
    @Transactional(readOnly = true)
    public ConfiguracionGeneracion leerConfiguracion(Identidad actor, UUID modelo) {
        CabeceraModelo c = repo.cabecera(modelo);
        autorizarConfig(actor, c);
        return repo.leerConfiguracion(modelo)
                .orElseGet(
                        () ->
                                new ConfiguracionGeneracion(
                                        null,
                                        "Mi Proyecto",
                                        nombreSeguro(c.nombre()),
                                        "com.empresa",
                                        nombreSeguro(c.nombre()),
                                        "com.empresa." + segmentoPaqueteSeguro(c.nombre()),
                                        "1.0.0-SNAPSHOT",
                                        true,
                                        true,
                                        true,
                                        true));
    }

    /**
     * Guarda o actualiza la configuración de generación para el modelo. Requiere {@code
     * CONFIGURAR_BACKEND}.
     */
    @Transactional
    public ConfiguracionGeneracion guardarConfiguracion(
            Identidad actor, UUID modelo, ActualizarConfiguracion cfg) {
        CabeceraModelo c = repo.cabecera(modelo);
        autorizarConfig(actor, c);
        UUID idCfg = repo.guardarConfiguracion(modelo, c.idVersionActual(), actor.id(), cfg);
        return repo.leerConfiguracion(modelo)
                .orElseGet(
                        () ->
                                new ConfiguracionGeneracion(
                                        idCfg,
                                        cfg.nombre(),
                                        cfg.nombreProyecto(),
                                        cfg.idGrupo(),
                                        cfg.idArtefacto(),
                                        cfg.paqueteBase(),
                                        cfg.versionInicial(),
                                        cfg.generarRepositorios(),
                                        cfg.generarControladores(),
                                        cfg.generarDtos(),
                                        cfg.exponerRest()));
    }

    // ─── CU30: Generar backend ────────────────────────────────────────────────

    /**
     * Genera el proyecto Spring Boot completo y lo empaqueta como ZIP. Requiere {@code
     * GENERAR_BACKEND}.
     */
    @Transactional
    public ResultadoGeneracion generar(Identidad actor, UUID modelo) {
        CabeceraModelo c = repo.cabecera(modelo);
        autorizarGeneracion(actor, c);

        // Leer configuración vigente (o usar defaults)
        ConfiguracionGeneracion cfg =
                repo.leerConfiguracion(modelo)
                        .orElseGet(
                                () ->
                                        new ConfiguracionGeneracion(
                                                null,
                                                "Mi Proyecto",
                                                nombreSeguro(c.nombre()),
                                                "com.empresa",
                                                nombreSeguro(c.nombre()),
                                                "com.empresa." + segmentoPaqueteSeguro(c.nombre()),
                                                "1.0.0-SNAPSHOT",
                                                true,
                                                true,
                                                true,
                                                true));

        // Guardar config si no existe
        UUID idCfg = cfg.id();
        if (idCfg == null) {
            ActualizarConfiguracion nueva =
                    new ActualizarConfiguracion(
                            cfg.nombre(),
                            cfg.nombreProyecto(),
                            cfg.idGrupo(),
                            cfg.idArtefacto(),
                            cfg.paqueteBase(),
                            cfg.versionInicial(),
                            cfg.generarRepositorios(),
                            cfg.generarControladores(),
                            cfg.generarDtos(),
                            cfg.exponerRest());
            idCfg = repo.guardarConfiguracion(modelo, c.idVersionActual(), actor.id(), nueva);
            cfg = repo.leerConfiguracion(modelo).orElseThrow();
        }

        // Leer elementos del modelo
        var clases = repo.leerClases(modelo);
        var atributos = repo.leerAtributos(modelo);
        var relaciones = repo.leerRelaciones(modelo);

        if (clases.isEmpty())
            throw new ApiException(422, "El modelo no tiene clases para generar.");

        // Generar ZIP
        byte[] zip =
                motor.generar(
                        cfg.idGrupo(),
                        cfg.idArtefacto(),
                        cfg.versionInicial(),
                        cfg.paqueteBase(),
                        clases,
                        atributos,
                        relaciones);

        long clasesGeneradas = clases.stream().filter(cl -> bool(cl.get("es_persistente"))).count();
        int totalArchivos =
                6
                        + (int) clasesGeneradas
                                * 4; // pom+gitignore+App+OpenApiConfig+props+schema + 4 por clase

        String nombreZip = cfg.idArtefacto() + "-generated.zip";
        UUID idArchivo = repo.guardarZip(c.proyecto(), actor.id(), nombreZip, zip);
        UUID idGen = repo.registrarGeneracion(idCfg, actor.id(), idArchivo, clases.size());

        return new ResultadoGeneracion(idGen, idArchivo, nombreZip, clases.size(), totalArchivos);
    }

    // ─── CU32: Descarga ──────────────────────────────────────────────────────

    /**
     * Descarga el ZIP de una generación previamente creada. Requiere {@code DESCARGAR_ARTEFACTO}.
     */
    @Transactional(readOnly = true)
    public ContenidoArchivo descargar(Identidad actor, UUID modelo, UUID idGeneracion) {
        CabeceraModelo c = repo.cabecera(modelo);
        autorizarDescarga(actor, c);
        return repo.descargarPorGeneracion(idGeneracion);
    }

    // ─── Autorización ────────────────────────────────────────────────────────

    private void autorizarConfig(Identidad actor, CabeceraModelo c) {
        if (!"ACTIVO".equals(c.estadoProyecto()))
            throw new ApiException(404, "Modelo no disponible.");
        if (actor.esAdministrador()) return;
        if (!repo.permiso(actor.id(), c.proyecto(), "CONFIGURAR_BACKEND"))
            throw new ApiException(
                    403, "No tienes permiso para configurar la generación de backend.");
    }

    private void autorizarGeneracion(Identidad actor, CabeceraModelo c) {
        if (!"ACTIVO".equals(c.estadoProyecto()))
            throw new ApiException(404, "Modelo no disponible.");
        if (actor.esAdministrador()) return;
        if (!repo.permiso(actor.id(), c.proyecto(), "GENERAR_BACKEND"))
            throw new ApiException(403, "No tienes permiso para generar el backend.");
    }

    private void autorizarDescarga(Identidad actor, CabeceraModelo c) {
        if (!"ACTIVO".equals(c.estadoProyecto()))
            throw new ApiException(404, "Modelo no disponible.");
        if (actor.esAdministrador()) return;
        if (!repo.permiso(actor.id(), c.proyecto(), "DESCARGAR_ARTEFACTO"))
            throw new ApiException(403, "No tienes permiso para descargar el artefacto.");
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private String nombreSeguro(String nombre) {
        if (nombre == null) return "proyecto";
        return nombre.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String segmentoPaqueteSeguro(String nombre) {
        if (nombre == null) return "proyecto";
        String segmento = nombre.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (segmento.isBlank()) return "proyecto";
        return Character.isDigit(segmento.charAt(0)) ? "p" + segmento : segmento;
    }

    private boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }
}
