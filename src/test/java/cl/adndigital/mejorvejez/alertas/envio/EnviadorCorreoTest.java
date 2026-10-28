package cl.adndigital.mejorvejez.alertas.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Prueba {@link EnviadorCorreo} como lógica pura (sin SMTP real): la
 * sanitización del error, el texto de "no configurado" y el {@code
 * LOG.warn} al arrancar y ante un fallo SMTP.
 */
class EnviadorCorreoTest {

    private static final Session SESION = Session.getInstance(new Properties());

    private final MensajeCorreo mensaje = new MensajeCorreo("ana@correo.cl", "Ana Pérez",
            "Mejor Vejez · Tarea sin confirmar",
            "Hola Ana Pérez,\n\n«Tomar colchicina» de José, atención médica.\n\nRevisa la alerta en: "
                    + "http://localhost:4200/alertas/1?grupo=1\n\nMejor Vejez");

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void correoNoConfiguradoQuedaFallido() {
        EnviadorCorreo enviador = new EnviadorCorreo(proveedor, "", "");

        ResultadoEnvio resultado = enviador.enviar(mensaje);

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.detalleError()).isEqualTo("El envío de correo no está configurado.");
        assertThat(resultado.referenciaProveedor()).isNull();
    }

    @Test
    void errorNoContieneDireccion() throws Exception {
        when(proveedor.getObject()).thenReturn(mailSender);
        MimeMessage mime = new MimeMessage(SESION);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("550 rechazado: persona@ejemplo.cl (destino <ana@correo.cl>)"))
                .when(mailSender).send(any(MimeMessage.class));

        EnviadorCorreo enviador = new EnviadorCorreo(proveedor, "localhost", "alertas@mejorvejez.local");
        ResultadoEnvio resultado = enviador.enviar(mensaje);

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.detalleError())
                .doesNotContain("@")
                .doesNotContain("persona")
                .doesNotContain("ana@correo.cl")
                .isEqualTo("MailSendException");
    }

    @Test
    void fallaSmtpLogueaElMensajeSanitizadoYSoloLaClaseEnElDetalle() throws Exception {
        when(proveedor.getObject()).thenReturn(mailSender);
        MimeMessage mime = new MimeMessage(SESION);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("550 rechazado: persona@ejemplo.cl (destino <ana@correo.cl>)"))
                .when(mailSender).send(any(MimeMessage.class));
        EnviadorCorreo enviador = new EnviadorCorreo(proveedor, "localhost", "alertas@mejorvejez.local");

        Logger logger = (Logger) LoggerFactory.getLogger(EnviadorCorreo.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ResultadoEnvio resultado = enviador.enviar(mensaje);

            assertThat(resultado.detalleError()).isEqualTo("MailSendException");
            List<String> mensajesLogueados = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(mensajesLogueados).anySatisfy(logueado -> {
                assertThat(logueado).contains("MailSendException").doesNotContain("@").doesNotContain("persona")
                        .doesNotContain("ana@correo.cl");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void sinHostNiRemitenteAlCrearseLogueaUnWarnDeArranque() {
        Logger logger = (Logger) LoggerFactory.getLogger(EnviadorCorreo.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new EnviadorCorreo(proveedor, "", "");

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(mensaje -> assertThat(mensaje).contains("no está configurado"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void conHostYRemitenteAlCrearseNoLogueaElWarnDeArranque() {
        Logger logger = (Logger) LoggerFactory.getLogger(EnviadorCorreo.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new EnviadorCorreo(proveedor, "localhost", "alertas@mejorvejez.local");

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void enviaTextoPlanoUtf8ConAsuntoYDestino() throws Exception {
        when(proveedor.getObject()).thenReturn(mailSender);
        MimeMessage mime = new MimeMessage(SESION);
        when(mailSender.createMimeMessage()).thenReturn(mime);

        EnviadorCorreo enviador = new EnviadorCorreo(proveedor, "localhost", "alertas@mejorvejez.local");
        ResultadoEnvio resultado = enviador.enviar(mensaje);

        assertThat(resultado.exito()).isTrue();
        assertThat(mime.getFrom()).hasSize(1);
        assertThat(mime.getFrom()[0].toString()).isEqualTo("alertas@mejorvejez.local");
        assertThat(mime.getAllRecipients()).hasSize(1);
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("ana@correo.cl");
        assertThat(mime.getSubject()).isEqualTo(mensaje.asunto());
        assertThat((String) mime.getContent()).isEqualTo(mensaje.cuerpo());
    }
}
