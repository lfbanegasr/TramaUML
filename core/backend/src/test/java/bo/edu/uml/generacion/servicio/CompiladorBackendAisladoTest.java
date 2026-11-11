package bo.edu.uml.generacion.servicio;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.zip.*;

class CompiladorBackendAisladoTest {
    @TempDir Path temporal;

    @Test
    void rechazaRutasQueSalenDelTemporalYExtensionesMaven() throws Exception {
        for (String nombre :
                List.of(
                        "../escape.java",
                        "/absoluto.java",
                        "x/.mvn/extensions.xml",
                        "x/script.cmd",
                        "C:/escape.java")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry(nombre));
                zip.write(1);
                zip.closeEntry();
            }
            assertThatThrownBy(() -> CompiladorBackendAislado.leerZip(bytes.toByteArray()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void noHeredaElEntornoYTerminaProcesosQueAgotanElTiempo() throws Exception {
        Path fuente = temporal.resolve("Comprobar.java");
        Files.writeString(
                fuente,
                "class Comprobar { public static void main(String[] args) throws Exception {"
                    + " System.out.println(System.getenv(\"PATH\")); if(args.length>0)"
                    + " Thread.sleep(30000); } }");
        var resultado =
                CompiladorBackendAislado.ejecutar(
                        List.of(CompiladorBackendAislado.javaEjecutable(), fuente.toString()),
                        temporal,
                        temporal,
                        Duration.ofSeconds(25));
        assertThat(resultado.codigo()).isZero();
        assertThat(resultado.salida().trim()).isEqualTo("null");
        var timeout =
                CompiladorBackendAislado.ejecutar(
                        List.of(
                                CompiladorBackendAislado.javaEjecutable(),
                                fuente.toString(),
                                "esperar"),
                        temporal,
                        temporal,
                        Duration.ofMillis(200));
        assertThat(timeout.agotado()).isTrue();
        assertThat(timeout.codigo()).isEqualTo(-1);
    }
}
