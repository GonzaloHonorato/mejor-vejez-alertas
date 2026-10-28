package cl.adndigital.mejorvejez.alertas.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.stream.Stream;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Prueba {@code POST /api/interno/envios-reporte} de punta a punta: la
 * cadena interna (clave de servicio), las validaciones de la solicitud y el
 * envío con el PDF adjunto contra un SMTP simulado (GreenMail).
 */
@SpringBootTest(properties = { "mejorvejez.procesos.habilitados=false",
        "mejorvejez.interno.clave-servicio=clave-correcta",
        "mejorvejez.notificaciones.remitente=alertas@mejorvejez.local" })
@AutoConfigureMockMvc
class ControladorEnviosReporteTest {

    private static final String RUTA = "/api/interno/envios-reporte";
    private static final String PDF_BASE64 = Base64.getEncoder()
            .encodeToString("contenido-del-pdf-de-prueba".repeat(10).getBytes());

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

    private String solicitud(String claveEnvio, String nombreArchivo, String pdfBase64, String destinatariosJson) {
        return "{\"claveEnvio\":\"" + claveEnvio + "\",\"asunto\":\"Reporte semanal\","
                + "\"cuerpo\":\"Cuerpo del reporte\",\"nombreArchivo\":\"" + nombreArchivo + "\",\"pdfBase64\":\""
                + pdfBase64 + "\",\"destinatarios\":[" + destinatariosJson + "]}";
    }

    private String destinatario(long idUsuario, String nombre, String correo) {
        return "{\"idUsuario\":" + idUsuario + ",\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\"}";
    }

    @Test
    void reporteSinClaveDa401() throws Exception {
        String cuerpo = solicitud("sin-clave", "reporte.pdf", PDF_BASE64, destinatario(1, "Ana", "ana@correo.cl"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));
    }

    @Test
    void reportePdfInvalidoDa400() throws Exception {
        String cuerpo = solicitud("pdf-invalido", "reporte.pdf", "esto-no-es-base64-válido!!",
                destinatario(1, "Ana", "ana@correo.cl"));

        mockMvc.perform(post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reporteAdjuntaPdfAUnoPorDestinatario() throws Exception {
        String cuerpo = solicitud("adjunta-pdf", "reporte-semanal.pdf", PDF_BASE64,
                destinatario(1, "Ana", "ana.adjunta@correo.cl") + ","
                        + destinatario(2, "Luis", "luis.adjunta@correo.cl"));

        mockMvc.perform(post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(2))
                .andExpect(jsonPath("$.resultados[0].enviado").value(true))
                .andExpect(jsonPath("$.resultados[1].enviado").value(true));

        assertThat(greenMail.waitForIncomingEmail(2)).isTrue();
        var mensajes = greenMail.getReceivedMessages();
        assertThat(mensajes).hasSize(2);
        int tamañoPdfOriginal = Base64.getDecoder().decode(PDF_BASE64).length;
        for (var mensaje : mensajes) {
            MimeMultipart multipart = (MimeMultipart) mensaje.getContent();
            MimeBodyPart adjunto = adjuntoPdf(multipart);
            assertThat(adjunto).isNotNull();
            assertThat(adjunto.getContentType()).contains("application/pdf");
            assertThat(GreenMailUtil.getBodyAsBytes(adjunto)).hasSize(tamañoPdfOriginal);
        }
    }

    @Test
    void reporteFalloParcialDevuelveResultadoPorDestinatario() throws Exception {
        String cuerpo = solicitud("fallo-parcial", "reporte.pdf", PDF_BASE64,
                destinatario(1, "Ana", "ana.parcial@correo.cl") + ","
                        + destinatario(2, "Luis", "roto@@correo.cl"));

        mockMvc.perform(post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(2))
                .andExpect(jsonPath("$.resultados[0].idUsuario").value(1))
                .andExpect(jsonPath("$.resultados[0].enviado").value(true))
                .andExpect(jsonPath("$.resultados[1].idUsuario").value(2))
                .andExpect(jsonPath("$.resultados[1].enviado").value(false))
                .andExpect(jsonPath("$.resultados[1].detalleError", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("@"))));

        assertThat(greenMail.waitForIncomingEmail(1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

    static Stream<Arguments> solicitudesInvalidas() {
        return Stream.of(
                Arguments.of("claveEnvio vacía",
                        "{\"claveEnvio\":\"\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"}]}"),
                Arguments.of("asunto vacío",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"}]}"),
                Arguments.of("cuerpo vacío",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"\",\"nombreArchivo\":\"r.pdf\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"}]}"),
                Arguments.of("nombreArchivo vacío",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"}]}"),
                Arguments.of("nombreArchivo sin .pdf",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"reporte.txt\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"}]}"),
                Arguments.of("destinatarios vacío",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":[]}"),
                Arguments.of("destinatario sin idUsuario",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"correo\":\"a@a.cl\"}]}"),
                Arguments.of("destinatario sin correo",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"idUsuario\":1}]}"),
                Arguments.of("destinatario nulo",
                        "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                                + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"},null]}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solicitudesInvalidas")
    void solicitudInvalidaDa400(String descripcion, String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void claveDeEnvioVaciaInformaElMensajeCuradoEnElDetalle() throws Exception {
        String cuerpo = "{\"claveEnvio\":\"\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":[{\"idUsuario\":1,\"correo\":\"a@a.cl\"}]}";

        mockMvc.perform(post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La clave de envío es obligatoria."));
    }

    @Test
    void destinatarioNuloDa400ConMensajeCurado() throws Exception {
        String cuerpo = "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\",\"nombreArchivo\":\"r.pdf\","
                + "\"pdfBase64\":\"" + PDF_BASE64 + "\",\"destinatarios\":["
                + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"},null]}";

        mockMvc.perform(post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Un destinatario no puede ser nulo."));
    }

    private MimeBodyPart adjuntoPdf(MimeMultipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            MimeBodyPart parte = (MimeBodyPart) multipart.getBodyPart(i);
            if (parte.getContentType() != null && parte.getContentType().contains("application/pdf")) {
                return parte;
            }
        }
        return null;
    }
}
