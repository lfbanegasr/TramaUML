package bo.edu.uml.servicios.servicio;

import bo.edu.uml.compartido.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
public class CifradorSecretosServicio {
    public static final String ALGORITMO = "AES-256-GCM";
    private static final int TAMANO_NONCE = 12;
    private static final int TAMANO_ETIQUETA = 128;
    private static final byte FORMATO = 1;

    private final SecretKeySpec clave;
    private final String versionClave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorSecretosServicio(
            @Value("${uml.servicios.clave-maestra:}") String claveCodificada,
            @Value("${uml.servicios.version-clave:v1}") String versionClave) {
        this.versionClave = versionClave == null || versionClave.isBlank() ? "v1" : versionClave;
        this.clave = construirClave(claveCodificada);
    }

    public byte[] cifrar(String secreto) {
        exigirClave();
        byte[] nonce = new byte[TAMANO_NONCE];
        byte[] claro = secreto.getBytes(StandardCharsets.UTF_8);
        aleatorio.nextBytes(nonce);
        try {
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAMANO_ETIQUETA, nonce));
            cifrador.updateAAD(versionClave.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cifrador.doFinal(claro);
            return ByteBuffer.allocate(1 + nonce.length + cifrado.length)
                    .put(FORMATO)
                    .put(nonce)
                    .put(cifrado)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new ApiException(503, "No fue posible proteger la credencial del servicio.");
        } finally {
            Arrays.fill(claro, (byte) 0);
        }
    }

    public String descifrar(byte[] contenido, String algoritmo, String version) {
        exigirClave();
        if (!ALGORITMO.equals(algoritmo)
                || !versionClave.equals(version)
                || contenido == null
                || contenido.length <= 1 + TAMANO_NONCE
                || contenido[0] != FORMATO)
            throw new ApiException(503, "La credencial protegida no puede utilizarse.");
        byte[] nonce = Arrays.copyOfRange(contenido, 1, 1 + TAMANO_NONCE);
        byte[] cifrado = Arrays.copyOfRange(contenido, 1 + TAMANO_NONCE, contenido.length);
        try {
            Cipher descifrador = Cipher.getInstance("AES/GCM/NoPadding");
            descifrador.init(
                    Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAMANO_ETIQUETA, nonce));
            descifrador.updateAAD(versionClave.getBytes(StandardCharsets.UTF_8));
            byte[] claro = descifrador.doFinal(cifrado);
            try {
                return new String(claro, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(claro, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            throw new ApiException(503, "La credencial protegida no puede utilizarse.");
        }
    }

    public String versionClave() {
        return versionClave;
    }

    private SecretKeySpec construirClave(String codificada) {
        if (codificada == null || codificada.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(codificada.trim());
            if (bytes.length != 32)
                throw new IllegalStateException(
                        "UML_SERVICIOS_MASTER_KEY debe contener 32 bytes en Base64.");
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "UML_SERVICIOS_MASTER_KEY no contiene Base64 válido.", e);
        }
    }

    private void exigirClave() {
        if (clave == null)
            throw new ApiException(
                    503,
                    "Configura UML_SERVICIOS_MASTER_KEY antes de almacenar o utilizar"
                            + " credenciales.");
    }
}
