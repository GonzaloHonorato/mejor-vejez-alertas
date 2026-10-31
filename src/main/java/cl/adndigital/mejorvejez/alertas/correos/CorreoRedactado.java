package cl.adndigital.mejorvejez.alertas.correos;

/** Correo ya redactado (asunto y cuerpo armados por {@link RedactorCorreosCuenta}), listo para enviar. */
record CorreoRedactado(String asunto, String cuerpo) {
}
