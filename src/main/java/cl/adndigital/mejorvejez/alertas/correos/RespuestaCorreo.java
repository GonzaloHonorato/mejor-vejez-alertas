package cl.adndigital.mejorvejez.alertas.correos;

/** Respuesta de {@code POST /api/interno/correos/*} (HU-01): si el correo se pudo entregar. */
public record RespuestaCorreo(boolean enviado) {
}
