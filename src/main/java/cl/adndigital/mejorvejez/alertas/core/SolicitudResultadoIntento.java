package cl.adndigital.mejorvejez.alertas.core;

/**
 * Cuerpo de {@code PUT /api/interno/intentos/{idIntento}}: el resultado que
 * este microservicio entrega tras intentar enviar un par (destinatario,
 * canal) reservado en {@code PENDIENTE}. {@code resultado} es {@code
 * ENVIADO} o {@code FALLIDO}.
 */
public record SolicitudResultadoIntento(String resultado, String detalleError, String referenciaProveedor) {
}
