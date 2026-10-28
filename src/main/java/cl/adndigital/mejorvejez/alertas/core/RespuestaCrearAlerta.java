package cl.adndigital.mejorvejez.alertas.core;

/**
 * Respuesta de {@code POST /api/interno/alertas}. {@code idAlerta} es {@code
 * null} cuando el origen ya no corresponde (la tarea se confirmó, se
 * canceló, se inactivó su adulto/grupo/plan/detalle...); {@code creada}
 * distingue "se creó ahora" de "ya existía" (idempotencia): en ambos casos
 * con éxito {@code idAlerta} trae el mismo id.
 */
public record RespuestaCrearAlerta(Long idAlerta, boolean creada) {
}
