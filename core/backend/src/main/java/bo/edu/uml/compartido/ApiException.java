package bo.edu.uml.compartido;

public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException conflicto() {
        return new ApiException(
                409, "El modelo cambió. Recarga y revisa tus cambios antes de volver a enviarlos.");
    }
}
