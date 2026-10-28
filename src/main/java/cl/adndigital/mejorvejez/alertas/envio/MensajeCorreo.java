package cl.adndigital.mejorvejez.alertas.envio;

/**
 * Mensaje de correo ya redactado (asunto y cuerpo armados por {@link
 * RedactorCorreoAlertas}), listo para que {@link EnviadorCorreo} lo entregue
 * por SMTP. {@code destino} nunca sale en una respuesta HTTP ni en un log.
 */
record MensajeCorreo(String destino, String nombreDestinatario, String asunto, String cuerpo) {
}
