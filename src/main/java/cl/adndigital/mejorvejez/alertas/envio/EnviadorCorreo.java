package cl.adndigital.mejorvejez.alertas.envio;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Enviador de correo SMTP: texto plano UTF-8 para una alerta, o con un PDF
 * adjunto para el reporte semanal. Remitente ({@code MAIL_REMITENTE}) y host
 * ({@code MAIL_HOST}) configurados por variable; si falta alguno, el envío
 * queda FALLIDO sin tocar el servidor SMTP (ver {@link #configurado()}, que
 * usa quien llama para decidir si reserva algo).
 *
 * <p>Ante un fallo SMTP real, el detalle que se devuelve es solo el nombre
 * de la clase de la excepción, nunca su mensaje (podría traer una dirección
 * de correo, el host o el puerto); el mensaje sanitizado (sin direcciones)
 * se registra en un {@code LOG.warn} para que el operador conserve el
 * diagnóstico.
 *
 * <p>{@code JavaMailSender} llega por {@code ObjectProvider} para no exigir
 * el bean en el arranque cuando el correo no está configurado todavía (host
 * vacío): la configuración se valida recién al enviar.
 */
@Component
public class EnviadorCorreo {

    private static final Logger LOG = LoggerFactory.getLogger(EnviadorCorreo.class);

    /**
     * Dirección de correo dentro del mensaje de una excepción SMTP: se
     * reemplaza por {@link #DESTINO_OCULTO} antes de registrarla en el log,
     * para que nunca salga un destino.
     */
    private static final Pattern PATRON_DIRECCION = Pattern.compile("[^\\s<>@]+@[^\\s<>@]+");
    private static final String DESTINO_OCULTO = "[destino]";
    private static final String DETALLE_NO_CONFIGURADO = "El envío de correo no está configurado.";
    static final String MENSAJE_NO_CONFIGURADO_LOG =
            "El envío de correo no está configurado: los avisos quedan pendientes hasta configurarlo.";

    private final ObjectProvider<JavaMailSender> remitenteCorreo;
    private final String host;
    private final String remitente;

    public EnviadorCorreo(ObjectProvider<JavaMailSender> remitenteCorreo, @Value("${spring.mail.host:}") String host,
            @Value("${mejorvejez.notificaciones.remitente:}") String remitente) {
        this.remitenteCorreo = remitenteCorreo;
        this.host = host;
        this.remitente = remitente;
        if (!configurado()) {
            LOG.warn(MENSAJE_NO_CONFIGURADO_LOG);
        }
    }

    /** Envía un correo de texto plano (una alerta ya redactada). */
    ResultadoEnvio enviar(MensajeCorreo mensaje) {
        return enviarMensaje(mensaje.destino(), mensaje.asunto(), mensaje.cuerpo(), null, null);
    }

    /**
     * Envía un correo de texto plano con {@code asunto} y {@code cuerpo} ya
     * redactados a {@code destino} (HU-01: cuenta nueva y recuperación de
     * contraseña), para quien llama desde otro paquete y no necesita el
     * detalle del error, solo si se pudo entregar.
     */
    public boolean enviarTextoPlano(String destino, String asunto, String cuerpo) {
        return enviarMensaje(destino, asunto, cuerpo, null, null).exito();
    }

    /**
     * Envía el correo del reporte semanal con {@code pdf} adjunto ({@code
     * application/pdf}, con el nombre {@code nombreArchivo}) a {@code
     * destino}. El arreglo de bytes solo vive en memoria.
     */
    ResultadoEnvio enviarConAdjunto(String destino, String asunto, String cuerpo, String nombreArchivo, byte[] pdf) {
        return enviarMensaje(destino, asunto, cuerpo, nombreArchivo, pdf);
    }

    /**
     * Arma y envía el {@code MimeMessage}, con adjunto si {@code pdf} no es
     * {@code null}; comparte la verificación de configuración y la
     * sanitización del error entre {@link #enviar} y {@link
     * #enviarConAdjunto}.
     */
    private ResultadoEnvio enviarMensaje(String destino, String asunto, String cuerpo, String nombreArchivo,
            byte[] pdf) {
        if (!configurado()) {
            return ResultadoEnvio.fallido(DETALLE_NO_CONFIGURADO);
        }
        try {
            JavaMailSender mailSender = remitenteCorreo.getObject();
            MimeMessage mime = mailSender.createMimeMessage();
            boolean conAdjunto = pdf != null;
            MimeMessageHelper helper = new MimeMessageHelper(mime, conAdjunto, StandardCharsets.UTF_8.name());
            helper.setFrom(remitente);
            helper.setTo(destino);
            helper.setSubject(asunto);
            helper.setText(cuerpo, false);
            if (conAdjunto) {
                helper.addAttachment(nombreArchivo, new ByteArrayResource(pdf), "application/pdf");
            }
            mailSender.send(mime);
            return ResultadoEnvio.exitoso(mime.getMessageID());
        } catch (Exception e) {
            LOG.warn("Envío fallido ({}): {}", e.getClass().getSimpleName(), ocultarDirecciones(e.getMessage(), destino));
            return ResultadoEnvio.fallido(e.getClass().getSimpleName());
        }
    }

    /** {@code true} si hay host SMTP y remitente configurados; lo usa quien llama para decidir si reserva algo. */
    boolean configurado() {
        return host != null && !host.isBlank() && remitente != null && !remitente.isBlank();
    }

    /** {@code texto} sin el destino exacto ni ninguna otra dirección de correo (ambos pasan a {@code [destino]}). */
    private static String ocultarDirecciones(String texto, String destino) {
        if (texto == null) {
            return null;
        }
        String resultado = texto;
        if (destino != null && !destino.isBlank()) {
            resultado = resultado.replace(destino, DESTINO_OCULTO);
        }
        return PATRON_DIRECCION.matcher(resultado).replaceAll(Matcher.quoteReplacement(DESTINO_OCULTO));
    }
}
