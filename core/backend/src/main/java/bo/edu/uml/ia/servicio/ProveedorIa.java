package bo.edu.uml.ia.servicio;

import java.util.Optional;

/** Puerto opcional para proveedores externos. Una respuesta vacía activa el respaldo local. */
public interface ProveedorIa {
  default java.util.Map<String, Object> configuracionPublica() {
    return java.util.Map.of("proveedor", "LOCAL", "modelo", "", "configurado", false);
  }

  /** Recibe exclusivamente contexto autorizado del modelo y su conversación. */
  default Optional<String> conversar(String contextoJson) {
    return Optional.empty();
  }

  default Optional<String> normalizar(String instruccion) {
    return Optional.empty();
  }

  default Optional<String> transcribir(byte[] audio, String tipoContenido, String nombre) {
    return Optional.empty();
  }

  default Optional<String> reconocerDiagrama(byte[] imagen, String tipoContenido) {
    return Optional.empty();
  }

  default Optional<String> reconocerDiagrama(
      byte[] imagen, String tipoContenido, String guiaOInstruccion) {
    return reconocerDiagrama(imagen, tipoContenido);
  }
}
