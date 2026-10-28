package cl.adndigital.mejorvejez.alertas.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.mail.internet.MimeMessage;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import cl.adndigital.mejorvejez.alertas.core.ClienteCore;

/**
 * Prueba {@link ServicioNotificacionAlertas} contra un servicio core
 * simulado (MockWebServer) y un SMTP simulado (GreenMail): reserva los
 * intentos de una alerta, envía cada par CORREO y confirma el resultado.
 */
class ServicioNotificacionAlertasTest {

    private static final OffsetDateTime DESDE = OffsetDateTime.of(2026, 10, 26, 0, 0, 0, 0, ZoneOffset.of("-03:00"));

    @RegisterExtension
    private final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private MockWebServer core;
    private ClienteCore clienteCore;

    @BeforeEach
    void preparar() throws Exception {
        core = new MockWebServer();
        core.start();
        clienteCore = new ClienteCore(core.url("/").toString(), "clave-de-prueba");
    }

    @AfterEach
    void cerrar() throws Exception {
        core.close();
    }

    private ServicioNotificacionAlertas servicio() {
        return new ServicioNotificacionAlertas(clienteCore, enviadorCorreo(), redactor());
    }

    private EnviadorCorreo enviadorCorreo() {
        return enviadorCorreo(mailSenderReal());
    }

    private EnviadorCorreo enviadorCorreo(JavaMailSender mailSender) {
        ObjectProvider<JavaMailSender> proveedor = proveedorDe(mailSender);
        return new EnviadorCorreo(proveedor, "localhost", "alertas@mejorvejez.local");
    }

    private RedactorCorreoAlertas redactor() {
        return new RedactorCorreoAlertas("http://localhost:4200");
    }

    private JavaMailSenderImpl mailSenderReal() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(greenMail.getSmtp().getPort());
        return mailSender;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> proveedorDe(JavaMailSender mailSender) {
        ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenReturn(mailSender);
        return proveedor;
    }

    private MockResponse json(int codigo, String cuerpo) {
        return new MockResponse.Builder().code(codigo).addHeader("Content-Type", "application/json").body(cuerpo)
                .build();
    }

    private MockResponse vacio(int codigo) {
        return new MockResponse.Builder().code(codigo).build();
    }

    private String respuestaReserva(long idAlerta, String intentosJson) {
        return "{\"alerta\":{\"idAlerta\":" + idAlerta + ",\"idGrupo\":10,\"tipoAlerta\":\"ATRASO_TAREA\","
                + "\"mensaje\":\"Mensaje de prueba\"},\"intentos\":[" + intentosJson + "]}";
    }

    private String intento(long idIntento, long idUsuario, String nombre, String canal, String destino) {
        return "{\"idIntento\":" + idIntento + ",\"idUsuario\":" + idUsuario + ",\"nombre\":\"" + nombre
                + "\",\"canal\":\"" + canal + "\",\"destino\":\"" + destino + "\",\"numeroIntento\":1}";
    }

    @Test
    void envioMarcaEnviadoEnCore() throws Exception {
        core.enqueue(json(200, "[500]"));
        core.enqueue(json(200, respuestaReserva(500, intento(1, 7, "Ana", "CORREO", "ana@correo.cl"))));
        core.enqueue(vacio(204));

        int enviados = servicio().notificarPendientes(DESDE);

        assertThat(enviados).isEqualTo(1);
        assertThat(greenMail.waitForIncomingEmail(1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);

        core.takeRequest(); // GET envios-pendientes
        core.takeRequest(); // POST reservar
        RecordedRequest confirmacion = core.takeRequest();
        assertThat(confirmacion.getMethod()).isEqualTo("PUT");
        assertThat(confirmacion.getUrl().encodedPath()).isEqualTo("/api/interno/intentos/1");
        assertThat(confirmacion.getBody().utf8()).contains("\"resultado\":\"ENVIADO\"");
    }

    @Test
    void sinReservasNoEnvia() throws Exception {
        core.enqueue(json(200, "[]"));

        int enviados = servicio().notificarPendientes(DESDE);

        assertThat(enviados).isZero();
        assertThat(core.getRequestCount()).isEqualTo(1);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    /** Sin MAIL_HOST/MAIL_REMITENTE, la corrida no debe gastar ningún intento: ni siquiera pide envios-pendientes. */
    @Test
    void correoNoConfiguradoNoPideEnviosPendientesNiReservaNada() {
        ObjectProvider<JavaMailSender> proveedor = proveedorDe(mailSenderReal());
        EnviadorCorreo enviadorNoConfigurado = new EnviadorCorreo(proveedor, "", "");
        ServicioNotificacionAlertas servicio = new ServicioNotificacionAlertas(clienteCore, enviadorNoConfigurado,
                redactor());

        int enviados = servicio.notificarPendientes(DESDE);

        assertThat(enviados).isZero();
        assertThat(core.getRequestCount()).isZero();
    }

    /** Además de no reservar, cada corrida sin correo configurado deja un aviso en el log. */
    @Test
    void correoNoConfiguradoLogueaUnWarnEnCadaCorrida() {
        ObjectProvider<JavaMailSender> proveedor = proveedorDe(mailSenderReal());
        EnviadorCorreo enviadorNoConfigurado = new EnviadorCorreo(proveedor, "", "");
        ServicioNotificacionAlertas servicio = new ServicioNotificacionAlertas(clienteCore, enviadorNoConfigurado,
                redactor());

        Logger logger = (Logger) LoggerFactory.getLogger(ServicioNotificacionAlertas.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            servicio.notificarPendientes(DESDE);

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(mensaje -> assertThat(mensaje).contains("no está configurado"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    /** M9: un offset positivo en {@code desde} no debe filtrarse a la URL como un '+' sin escapar. */
    @Test
    void desdeConOffsetPositivoSeEscapaEnLaConsultaDeEnviosPendientes() throws Exception {
        OffsetDateTime desdeOffsetPositivo = OffsetDateTime.of(2026, 10, 26, 0, 0, 0, 0, ZoneOffset.ofHours(2));
        core.enqueue(json(200, "[]"));

        servicio().notificarPendientes(desdeOffsetPositivo);

        RecordedRequest solicitud = core.takeRequest();
        assertThat(solicitud.getUrl().encodedQuery()).contains("%2B").doesNotContain("+");
    }

    @Test
    void canalNoCorreoQuedaFallidoCanalNoHabilitado() throws Exception {
        core.enqueue(json(200, "[501]"));
        core.enqueue(json(200, respuestaReserva(501, intento(2, 8, "Luis", "WHATSAPP", "+56900000000"))));
        core.enqueue(vacio(204));

        int enviados = servicio().notificarPendientes(DESDE);

        assertThat(enviados).isZero();
        assertThat(greenMail.getReceivedMessages()).isEmpty();

        core.takeRequest();
        core.takeRequest();
        RecordedRequest confirmacion = core.takeRequest();
        assertThat(confirmacion.getUrl().encodedPath()).isEqualTo("/api/interno/intentos/2");
        assertThat(confirmacion.getBody().utf8()).contains("\"resultado\":\"FALLIDO\"")
                .contains("\"detalleError\":\"Canal no habilitado\"");
    }

    /**
     * Dos pares CORREO de la misma alerta; el segundo falla al enviar (el
     * {@code JavaMailSender} real queda envuelto para simular el fallo SMTP
     * solo con ese destino, el primero se entrega de verdad en GreenMail).
     */
    @Test
    void smtpFallaUnDestinatarioLosDemasSeEnvian() throws Exception {
        core.enqueue(json(200, "[502]"));
        core.enqueue(json(200, respuestaReserva(502,
                intento(3, 9, "Ana", "CORREO", "ana.ok@correo.cl") + ","
                        + intento(4, 10, "Luis", "CORREO", "luis.falla@correo.cl"))));
        core.enqueue(vacio(204));
        core.enqueue(vacio(204));

        int enviados = notificarConMailSender(conFalloParaUnDestino("luis.falla@correo.cl"), DESDE);

        assertThat(enviados).isEqualTo(1);
        assertThat(greenMail.waitForIncomingEmail(1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        assertThat(GreenMailUtil.getAddressList(greenMail.getReceivedMessages()[0].getAllRecipients()))
                .contains("ana.ok@correo.cl");

        core.takeRequest(); // GET envios-pendientes
        core.takeRequest(); // POST reservar
        RecordedRequest confirmacionAna = core.takeRequest();
        assertThat(confirmacionAna.getUrl().encodedPath()).isEqualTo("/api/interno/intentos/3");
        assertThat(confirmacionAna.getBody().utf8()).contains("\"resultado\":\"ENVIADO\"");
        RecordedRequest confirmacionLuis = core.takeRequest();
        assertThat(confirmacionLuis.getUrl().encodedPath()).isEqualTo("/api/interno/intentos/4");
        assertThat(confirmacionLuis.getBody().utf8()).contains("\"resultado\":\"FALLIDO\"");
    }

    /** Core caído al confirmar un intento: se loguea y se sigue con el siguiente par. */
    @Test
    void coreCaidoEnElPutNoDetieneALosDemas() throws Exception {
        core.enqueue(json(200, "[503]"));
        core.enqueue(json(200, respuestaReserva(503,
                intento(5, 11, "Ana", "CORREO", "ana.503@correo.cl") + ","
                        + intento(6, 12, "Luis", "CORREO", "luis.503@correo.cl"))));
        core.enqueue(vacio(503)); // el core está caído al confirmar el primer intento
        core.enqueue(vacio(204));

        int enviados = servicio().notificarPendientes(DESDE);

        assertThat(enviados).isEqualTo(2);
        assertThat(greenMail.waitForIncomingEmail(2)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(2);
        assertThat(core.getRequestCount()).isEqualTo(4);
    }

    private int notificarConMailSender(JavaMailSender mailSender, OffsetDateTime desde) {
        ServicioNotificacionAlertas servicio = new ServicioNotificacionAlertas(clienteCore, enviadorCorreo(mailSender),
                redactor());
        return servicio.notificarPendientes(desde);
    }

    /** El {@code JavaMailSender} real, espiado para fallar solo cuando el destino es {@code destinoQueFalla}. */
    private JavaMailSender conFalloParaUnDestino(String destinoQueFalla) throws Exception {
        JavaMailSenderImpl real = mailSenderReal();
        JavaMailSender espiado = org.mockito.Mockito.spy(real);
        doAnswer(invocacion -> {
            MimeMessage mime = invocacion.getArgument(0);
            if (GreenMailUtil.getAddressList(mime.getAllRecipients()).contains(destinoQueFalla)) {
                throw new MailSendException("Fallo SMTP simulado");
            }
            return invocacion.callRealMethod();
        }).when(espiado).send(any(MimeMessage.class));
        return espiado;
    }
}
