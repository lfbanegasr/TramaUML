package bo.edu.uml.ia.servicio;

import bo.edu.uml.compartido.ApiException;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Contexto;
import bo.edu.uml.ia.servicio.MotorAsistenciaInteligente.Interpretacion;

import org.springframework.stereotype.Service;

@Service
public class MotorVisionDiagrama {
    private final MotorAsistenciaInteligente motorTexto;
    private final ProveedorIa proveedor;

    public MotorVisionDiagrama(MotorAsistenciaInteligente motorTexto, ProveedorIa proveedor) {
        this.motorTexto = motorTexto;
        this.proveedor = proveedor;
    }

    public Interpretacion interpretar(
            byte[] imagen, String tipoContenido, String textoDetectado, Contexto contexto) {
        String texto = textoDetectado;
        if (texto == null || texto.isBlank())
            texto =
                    proveedor
                            .reconocerDiagrama(imagen, tipoContenido)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    503,
                                                    "La imagen se conservó, pero no hay un"
                                                            + " proveedor OCR o multimodal"
                                                            + " disponible."));
        return motorTexto.interpretar(texto, contexto);
    }
}
