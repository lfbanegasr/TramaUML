package bo.edu.uml.proyectos.servicio;

import bo.edu.uml.compartido.ApiException;

import java.time.LocalDate;

public final class FiltrosConsulta {
    private FiltrosConsulta() {}

    public static void validar(int pagina, int tamano, LocalDate desde, LocalDate hasta) {
        if (pagina < 0
                || pagina > 100000
                || tamano < 1
                || tamano > 100
                || (desde != null && (desde.getYear() < 1 || desde.getYear() > 9998))
                || (hasta != null && (hasta.getYear() < 1 || hasta.getYear() > 9998))
                || (desde != null && hasta != null && desde.isAfter(hasta)))
            throw new ApiException(400, "Revisa la paginación y el rango de fechas.");
    }
}
