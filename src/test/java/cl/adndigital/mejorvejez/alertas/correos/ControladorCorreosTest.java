package cl.adndigital.mejorvejez.alertas.correos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.internet.MimeMessage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Prueba {@code POST /api/interno/correos/cuenta-nueva} y {@code
 * .../recuperar-clave} de punta a punta (HU-01): la cadena interna (clave de
 * servicio), las validaciones de la solicitud y el envío contra un SMTP
 * simulado (GreenMail).
 */
@SpringBootTest(properties = { "mejorvejez.procesos.habilitados=false",
        "mejorvejez.interno.clave-servicio=clave-correcta",
        "mejorvejez.notificaciones.remitente=alertas@mejorvejez.local" })
@AutoConfigureMockMvc
class ControladorCorreosTest {

    private static final String RUTA_CUENTA_NUEVA = "/api/interno/correos/cuenta-nueva";
    private static final String RUTA_RECUPERAR_CLAVE = "/api/interno/correos/recuperar-clave";

    private static GreenMail greenMail;

    @DynamicPropertySource
    static void registrarSmtpSimulado(DynamicPropertyRegistry registro) {
        greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        greenMail.start();
        registro.add("spring.mail.host", () -> "localhost");
        registro.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        // GreenMail no exige autenticación ni STARTTLS: se apagan solo para la prueba.
        registro.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registro.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    }

    @AfterAll
    static void cerrarSmtp() {
        greenMail.stop();
    }

    @BeforeEach
    void limpiarBandeja() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
    }

    @Autowired
    private MockMvc mockMvc;

    private String solicitudCuentaNueva(String nombre, String email, String claveTemporal, String urlIngreso) {
        return "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"claveTemporal\":\"" + claveTemporal
                + "\",\"urlIngreso\":\"" + urlIngreso + "\"}";
    }

    private String solicitudRecuperarClave(String nombre, String email, String enlace) {
        return "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"enlace\":\"" + enlace + "\"}";
    }

    @Test
    void cuentaNuevaEnviaClaveYEnlace() throws Exception {
        String cuerpo = solicitudCuentaNueva("Ana Pérez", "ana.cuenta@correo.cl", "Clave2026x",
                "http://localhost:4200/ingreso");

        mockMvc.perform(post(RUTA_CUENTA_NUEVA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(true));

        assertThat(greenMail.waitForIncomingEmail(1)).isTrue();
        MimeMessage[] mensajes = greenMail.getReceivedMessages();
        assertThat(mensajes).hasSize(1);
        MimeMessage mensaje = mensajes[0];
        assertThat(mensaje.getSubject()).isEqualTo("Mejor Vejez · Tu cuenta está lista");
        assertThat(GreenMailUtil.getAddressList(mensaje.getAllRecipients())).contains("ana.cuenta@correo.cl");
        String cuerpoRecibido = (String) mensaje.getContent();
        assertThat(cuerpoRecibido).contains("Ana Pérez", "ana.cuenta@correo.cl", "Clave2026x",
                "http://localhost:4200/ingreso", "Mi perfil");
    }

    @Test
    void recuperarEnviaEnlace() throws Exception {
        String cuerpo = solicitudRecuperarClave("Ana Pérez", "ana.recupera@correo.cl",
                "http://localhost:4200/restablecer-clave?codigo=abc123");

        mockMvc.perform(post(RUTA_RECUPERAR_CLAVE).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO,
                        "clave-correcta").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(true));

        assertThat(greenMail.waitForIncomingEmail(1)).isTrue();
        MimeMessage[] mensajes = greenMail.getReceivedMessages();
        assertThat(mensajes).hasSize(1);
        MimeMessage mensaje = mensajes[0];
        assertThat(mensaje.getSubject()).isEqualTo("Mejor Vejez · Restablece tu contraseña");
        assertThat(GreenMailUtil.getAddressList(mensaje.getAllRecipients())).contains("ana.recupera@correo.cl");
        String cuerpoRecibido = (String) mensaje.getContent();
        assertThat(cuerpoRecibido).contains("Ana Pérez", "http://localhost:4200/restablecer-clave?codigo=abc123",
                "vence en 1 hora", "Si no lo pediste, ignora este correo");
    }

    @Test
    void sinClaveDeServicioDa401() throws Exception {
        String cuerpo = solicitudCuentaNueva("Ana Pérez", "ana.sinclave@correo.cl", "Clave2026x",
                "http://localhost:4200/ingreso");

        mockMvc.perform(post(RUTA_CUENTA_NUEVA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void correoInvalidoDa400() throws Exception {
        String cuerpo = solicitudCuentaNueva("Ana Pérez", "esto-no-es-un-correo", "Clave2026x",
                "http://localhost:4200/ingreso");

        mockMvc.perform(post(RUTA_CUENTA_NUEVA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void claveNoSeLoguea() throws Exception {
        String claveTemporal = "ClaveSecreta99";
        String enlaceRecuperar = "http://localhost:4200/restablecer-clave?codigo=secreto-enlace-123";
        Logger loggerRaiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        loggerRaiz.addAppender(appender);
        try {
            String cuerpoCuentaNueva = solicitudCuentaNueva("Ana Pérez", "ana.sinlog@correo.cl", claveTemporal,
                    "http://localhost:4200/ingreso");
            mockMvc.perform(post(RUTA_CUENTA_NUEVA)
                    .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpoCuentaNueva));

            String cuerpoRecuperar = solicitudRecuperarClave("Ana Pérez", "ana.sinlog@correo.cl", enlaceRecuperar);
            mockMvc.perform(post(RUTA_RECUPERAR_CLAVE)
                    .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpoRecuperar));

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(mensajeLogueado -> mensajeLogueado.contains(claveTemporal)
                            || mensajeLogueado.contains(enlaceRecuperar));
        } finally {
            loggerRaiz.detachAppender(appender);
        }
    }
}
