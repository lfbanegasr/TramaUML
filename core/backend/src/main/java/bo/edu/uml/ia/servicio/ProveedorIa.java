package bo.edu.uml.ia.servicio;

import java.util.Optional;

/** Puerto opcional para proveedores externos. Una respuesta vacía activa el respaldo local. */
public interface ProveedorIa {
    default Optional<String> normalizar(String instruccion) {
        return Optional.empty();
    }

    default Optional<String> transcribir(byte[] audio, String tipoContenido, String nombre) {
        return Optional.empty();
    }

    default Optional<String> reconocerDiagrama(byte[] imagen, String tipoContenido) {
        return Optional.empty();
    }
}
