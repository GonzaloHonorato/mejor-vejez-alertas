package cl.adndigital.mejorvejez.alertas.core;

/**
 * Un par (destinatario, canal) con su intento ya reservado en {@code
 * PENDIENTE} en el servicio core: este microservicio lo envía y confirma el
 * resultado con {@code PUT /api/interno/intentos/{idIntento}}.
 */
public record IntentoReservado(Long idIntento, Long idUsuario, String nombre, String canal, String destino,
        Integer numeroIntento) {
}
