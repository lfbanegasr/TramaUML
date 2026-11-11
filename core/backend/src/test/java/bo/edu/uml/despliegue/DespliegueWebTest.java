package bo.edu.uml.despliegue;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import bo.edu.uml.acceso.controlador.SeguridadConfiguracion;
import bo.edu.uml.acceso.servicio.AccesoServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.*;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SaludControlador.class)
@Import({SeguridadPublicaConfiguracion.class, SeguridadConfiguracion.class})
@ActiveProfiles("despliegue")
class DespliegueWebTest {
  @Autowired MockMvc mvc;
  @MockitoBean JdbcTemplate jdbc;
  @MockitoBean ApplicationAvailability disponibilidad;
  @MockitoBean AccesoServicio acceso;

  @Test
  void saludPublicaCompruebaDisponibilidadYBaseSinExponerDetalles() throws Exception {
    when(disponibilidad.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
    when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
    mvc.perform(get("/salud"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
        .andExpect(header().string("Cache-Control", "no-store"));
    when(jdbc.queryForObject("select 1", Integer.class))
        .thenThrow(new DataAccessResourceFailureException("detalle privado"));
    mvc.perform(get("/salud"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().json("{\"estado\":\"NO_DISPONIBLE\"}"));
  }

  @Test
  void saludNoAnunciaArranqueHastaCompletarLasVerificaciones() throws Exception {
    when(disponibilidad.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
    mvc.perform(get("/salud")).andExpect(status().isServiceUnavailable());
    verifyNoInteractions(jdbc);
  }

  @Test
  void apiYWebsocketSiguenExigiendoSesion() throws Exception {
    mvc.perform(get("/api/proyectos")).andExpect(status().isUnauthorized());
    mvc.perform(get("/ws/modelos/prueba/sesiones/prueba")).andExpect(status().isUnauthorized());
  }

  @Test
  void activosPublicosNoPermitenEscrituras() throws Exception {
    mvc.perform(post("/assets/ejemplo.js")).andExpect(status().isForbidden());
  }
}
