package cl.adndigital.mejorvejez.alertas.envio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.alertas.core.DatosAlertaCorreo;
import cl.adndigital.mejorvejez.alertas.deteccion.CodigosProcesos;

/**
 * Prueba {@link RedactorCorreoAlertas} como lógica pura (sin Spring):
 * asunto con la etiqueta del tipo y cuerpo con el enlace a la alerta.
 */
class RedactorCorreoAlertasTest {

    private final RedactorCorreoAlertas redactor = new RedactorCorreoAlertas("http://localhost:4200");

    @Test
    void textoDelCorreoConAsuntoYEnlaceALaAlerta() {
        DatosAlertaCorreo alerta = new DatosAlertaCorreo(999L, 10L, CodigosProcesos.TIPO_ATRASO_TAREA,
                "«Tomar colchicina» de José Pérez, programada para el 26-10-2026 a las 09:00, sigue sin "
                        + "confirmación final.");

        MensajeCorreo mensaje = redactor.redactar(alerta, "Ana Pérez", "ana@correo.cl");

        assertThat(mensaje.destino()).isEqualTo("ana@correo.cl");
        assertThat(mensaje.nombreDestinatario()).isEqualTo("Ana Pérez");
        assertThat(mensaje.asunto()).isEqualTo("Cuida+ · Tarea atrasada");
        assertThat(mensaje.cuerpo()).isEqualTo("Hola Ana Pérez,\n\n«Tomar colchicina» de José Pérez, programada "
                + "para el 26-10-2026 a las 09:00, sigue sin confirmación final.\n\nRevisa la alerta en: "
                + "http://localhost:4200/alertas/999?grupo=10\n\nCuida+");
    }

    @Test
    void urlAppConBarraFinalNoDuplicaLaBarra() {
        RedactorCorreoAlertas conBarra = new RedactorCorreoAlertas("http://x/");
        DatosAlertaCorreo alerta = new DatosAlertaCorreo(7L, 10L, CodigosProcesos.TIPO_ATRASO_TAREA, "Mensaje");

        MensajeCorreo mensaje = conBarra.redactar(alerta, "Ana", "ana@correo.cl");

        assertThat(mensaje.cuerpo()).contains("Revisa la alerta en: http://x/alertas/7?grupo=10")
                .doesNotContain("//alertas");
    }
}
