package cl.adndigital.mejorvejez.alertas.core;

/**
 * Datos de la alerta que entrega el servicio core para redactar y enviar el
 * correo, dentro de {@link RespuestaReservaIntentos}. Vienen siempre, aunque
 * {@code intentos} esté vacía (el origen ya no está vigente o no hay ningún
 * par elegible), para que quien llama sepa de qué alerta se trataba.
 */
public record DatosAlertaCorreo(Long idAlerta, Long idGrupo, String tipoAlerta, String mensaje) {
}
