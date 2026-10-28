package cl.adndigital.mejorvejez.alertas.deteccion;

/**
 * Resultado de una corrida de detección de atrasos o de alertas preventivas.
 * {@code idEjecucion} es {@code null} cuando el servicio core no respondió al
 * registrar el inicio de la corrida: en ese caso la corrida igual intenta lo
 * suyo, pero no queda registrada.
 */
public record ResumenCorrida(Long idEjecucion, int creadas, int errores, String resumen) {
}
