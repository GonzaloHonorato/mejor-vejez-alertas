package cl.adndigital.mejorvejez.alertas.envio;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Envía el correo del reporte semanal (con el PDF adjunto) a cada
 * destinatario de {@code POST /api/interno/envios-reporte}; un destinatario
 * que falla no detiene a los demás. El PDF solo vive en memoria: se decodifica
 * una vez y nunca se escribe a disco ni se registra en el log.
 *
 * <p>Una misma {@code claveEnvio} no puede procesarse dos veces en paralelo:
 * el registro en memoria de las claves en curso se libera al terminar
 * (bien o mal), en un {@code finally}.
 */
@Service
public class ServicioEnviosReporte {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioEnviosReporte.class);

    private final Set<String> clavesEnCurso = ConcurrentHashMap.newKeySet();
    private final EnviadorCorreo enviadorCorreo;

    public ServicioEnviosReporte(EnviadorCorreo enviadorCorreo) {
        this.enviadorCorreo = enviadorCorreo;
    }

    public RespuestaEnvioReporte enviar(SolicitudEnvioReporte solicitud) {
        byte[] pdf = decodificarPdf(solicitud.pdfBase64());
        if (!clavesEnCurso.add(solicitud.claveEnvio())) {
            throw new EnvioEnCursoException();
        }
        try {
            return new RespuestaEnvioReporte(enviarATodos(solicitud, pdf));
        } finally {
            clavesEnCurso.remove(solicitud.claveEnvio());
        }
    }

    private List<ResultadoDestinatario> enviarATodos(SolicitudEnvioReporte solicitud, byte[] pdf) {
        return solicitud.destinatarios().stream()
                .map(destinatario -> enviarAUno(solicitud, pdf, destinatario))
                .toList();
    }

    private ResultadoDestinatario enviarAUno(SolicitudEnvioReporte solicitud, byte[] pdf, Destinatario destinatario) {
        try {
            ResultadoEnvio resultado = enviadorCorreo.enviarConAdjunto(destinatario.correo(), solicitud.asunto(),
                    solicitud.cuerpo(), solicitud.nombreArchivo(), pdf);
            return new ResultadoDestinatario(destinatario.idUsuario(), resultado.exito(), resultado.detalleError());
        } catch (RuntimeException ex) {
            LOG.error("Fallo inesperado al enviar el reporte a un destinatario: {}", ex.getClass().getSimpleName());
            return new ResultadoDestinatario(destinatario.idUsuario(), false, ex.getClass().getSimpleName());
        }
    }

    /** Decodifica el PDF en memoria; nunca lo escribe a disco ni lo registra en el log. */
    private static byte[] decodificarPdf(String pdfBase64) {
        byte[] pdf;
        try {
            pdf = Base64.getDecoder().decode(pdfBase64);
        } catch (IllegalArgumentException ex) {
            throw new SolicitudEnvioInvalidaException("El PDF no es un base64 válido.");
        }
        if (pdf.length == 0) {
            throw new SolicitudEnvioInvalidaException("El PDF no puede estar vacío.");
        }
        return pdf;
    }
}
