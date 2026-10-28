package cl.adndigital.mejorvejez.alertas.core;

import java.util.List;

/**
 * Respuesta de {@code POST /api/interno/alertas/{idAlerta}/intentos/reservar}:
 * los datos de la alerta y los pares que quedaron reservados en {@code
 * PENDIENTE}. {@code intentos} viene vacía cuando el origen de la alerta ya
 * no está vigente o cuando ningún par es elegible.
 */
public record RespuestaReservaIntentos(DatosAlertaCorreo alerta, List<IntentoReservado> intentos) {
}
