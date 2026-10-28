package cl.adndigital.mejorvejez.alertas.envio;

/**
 * Ya hay un envío de reporte en curso con la misma {@code claveEnvio}
 * (idempotencia durante la ejecución, no persistente): {@link
 * ServicioEnviosReporte} libera el registro al terminar, haya ido bien o
 * mal.
 */
public class EnvioEnCursoException extends RuntimeException {
}
