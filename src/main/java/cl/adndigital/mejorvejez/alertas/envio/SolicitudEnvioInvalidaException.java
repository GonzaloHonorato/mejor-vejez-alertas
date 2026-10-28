package cl.adndigital.mejorvejez.alertas.envio;

/**
 * La solicitud de envío del reporte semanal no es válida en algo que Bean
 * Validation no puede comprobar sobre el cuerpo (por ejemplo, que el PDF
 * decodifique). {@code getMessage()} es siempre un texto curado, sin datos
 * sensibles, pensado para devolverse tal cual en un 400.
 */
public class SolicitudEnvioInvalidaException extends RuntimeException {

    public SolicitudEnvioInvalidaException(String mensaje) {
        super(mensaje);
    }
}
