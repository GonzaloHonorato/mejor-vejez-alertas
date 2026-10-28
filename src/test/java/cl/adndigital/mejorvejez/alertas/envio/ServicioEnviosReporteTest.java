package cl.adndigital.mejorvejez.alertas.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba {@link ServicioEnviosReporte} como lógica pura (con {@link
 * EnviadorCorreo} simulado): la idempotencia "durante la ejecución" de
 * {@code claveEnvio} y que un destinatario que falla no detiene a los
 * demás.
 */
class ServicioEnviosReporteTest {

    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor();

    @AfterEach
    void cerrar() {
        ejecutor.shutdown();
    }

    private SolicitudEnvioReporte solicitud(String claveEnvio) {
        String pdfBase64 = Base64.getEncoder().encodeToString("contenido-de-prueba".getBytes());
        return new SolicitudEnvioReporte(claveEnvio, "Reporte semanal", "Cuerpo del reporte", "reporte.pdf",
                pdfBase64, List.of(new Destinatario(1L, "Ana", "ana@correo.cl")));
    }

    @Test
    void mismaClaveEnvioConcurrenteDa409() throws Exception {
        EnviadorCorreo enviadorCorreo = mock(EnviadorCorreo.class);
        CountDownLatch enEjecucion = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        when(enviadorCorreo.enviarConAdjunto(any(), any(), any(), any(), any())).thenAnswer(invocacion -> {
            enEjecucion.countDown();
            liberar.await();
            return ResultadoEnvio.exitoso("mensaje-1");
        });
        ServicioEnviosReporte servicio = new ServicioEnviosReporte(enviadorCorreo);
        SolicitudEnvioReporte solicitud = solicitud("envio-concurrente");

        Future<RespuestaEnvioReporte> primeraLlamada = ejecutor.submit(() -> servicio.enviar(solicitud));
        assertThat(enEjecucion.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> servicio.enviar(solicitud)).isInstanceOf(EnvioEnCursoException.class);

        liberar.countDown();
        assertThat(primeraLlamada.get().resultados()).hasSize(1);

        // Liberado el registro en memoria, una tercera llamada con la misma clave ya no da 409.
        when(enviadorCorreo.enviarConAdjunto(any(), any(), any(), any(), any()))
                .thenReturn(ResultadoEnvio.exitoso("mensaje-2"));
        RespuestaEnvioReporte tercera = servicio.enviar(solicitud);
        assertThat(tercera.resultados()).hasSize(1);
    }

    @Test
    void destinatarioQueFallaNoDetieneALosDemas() {
        EnviadorCorreo enviadorCorreo = mock(EnviadorCorreo.class);
        when(enviadorCorreo.enviarConAdjunto(org.mockito.ArgumentMatchers.eq("falla@correo.cl"), any(), any(), any(),
                any())).thenReturn(ResultadoEnvio.fallido("AddressException"));
        when(enviadorCorreo.enviarConAdjunto(org.mockito.ArgumentMatchers.eq("ok@correo.cl"), any(), any(), any(),
                any())).thenReturn(ResultadoEnvio.exitoso("mensaje-ok"));
        ServicioEnviosReporte servicio = new ServicioEnviosReporte(enviadorCorreo);
        String pdfBase64 = Base64.getEncoder().encodeToString("contenido".getBytes());
        SolicitudEnvioReporte solicitud = new SolicitudEnvioReporte("clave-parcial", "Asunto", "Cuerpo",
                "reporte.pdf", pdfBase64,
                List.of(new Destinatario(1L, "Ana", "ok@correo.cl"), new Destinatario(2L, "Luis", "falla@correo.cl")));

        RespuestaEnvioReporte respuesta = servicio.enviar(solicitud);

        assertThat(respuesta.resultados()).hasSize(2);
        assertThat(respuesta.resultados()).anySatisfy(r -> {
            assertThat(r.idUsuario()).isEqualTo(1L);
            assertThat(r.enviado()).isTrue();
        });
        assertThat(respuesta.resultados()).anySatisfy(r -> {
            assertThat(r.idUsuario()).isEqualTo(2L);
            assertThat(r.enviado()).isFalse();
            assertThat(r.detalleError()).doesNotContain("@");
        });
    }

    @Test
    void pdfBase64InvalidoLanzaSolicitudEnvioInvalida() {
        EnviadorCorreo enviadorCorreo = mock(EnviadorCorreo.class);
        ServicioEnviosReporte servicio = new ServicioEnviosReporte(enviadorCorreo);
        SolicitudEnvioReporte solicitud = new SolicitudEnvioReporte("clave-invalida", "Asunto", "Cuerpo",
                "reporte.pdf", "esto-no-es-base64-válido!!", List.of(new Destinatario(1L, "Ana", "ana@correo.cl")));

        assertThatThrownBy(() -> servicio.enviar(solicitud)).isInstanceOf(SolicitudEnvioInvalidaException.class);
    }
}
