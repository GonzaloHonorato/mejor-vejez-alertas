package cl.adndigital.mejorvejez.alertas.envio;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cl.adndigital.mejorvejez.alertas.core.DatosAlertaCorreo;
import cl.adndigital.mejorvejez.alertas.deteccion.CodigosProcesos;

/**
 * Redacta el correo de una alerta: asunto con la etiqueta del tipo y cuerpo
 * con el mensaje ya armado (y truncado) por el servicio core, más el enlace
 * a la alerta en el front. El enlace usa {@code
 * mejorvejez.notificaciones.url-app}.
 */
@Component
public class RedactorCorreoAlertas {

    private static final Map<String, String> ETIQUETAS_TIPO = Map.of(
            CodigosProcesos.TIPO_ATRASO_TAREA, "Tarea atrasada",
            CodigosProcesos.TIPO_CITA_PROXIMA, "Cita próxima",
            CodigosProcesos.TIPO_REPOSICION_MEDICAMENTO, "Reposición de medicamento");

    private final String urlApp;

    public RedactorCorreoAlertas(@Value("${mejorvejez.notificaciones.url-app}") String urlApp) {
        // Sin barra final: `APP_URL=https://…cl/` no debe generar `…cl//alertas/…`.
        this.urlApp = urlApp.replaceAll("/+$", "");
    }

    /** Mensaje de correo de {@code alerta} para {@code nombreDestinatario} en {@code destino}. */
    MensajeCorreo redactar(DatosAlertaCorreo alerta, String nombreDestinatario, String destino) {
        String etiqueta = ETIQUETAS_TIPO.get(alerta.tipoAlerta());
        String asunto = "Cuida+ · " + etiqueta;
        String cuerpo = "Hola " + nombreDestinatario + ",\n\n" + alerta.mensaje()
                + "\n\nRevisa la alerta en: " + urlApp + "/alertas/" + alerta.idAlerta() + "?grupo="
                + alerta.idGrupo() + "\n\nCuida+";
        return new MensajeCorreo(destino, nombreDestinatario, asunto, cuerpo);
    }
}
