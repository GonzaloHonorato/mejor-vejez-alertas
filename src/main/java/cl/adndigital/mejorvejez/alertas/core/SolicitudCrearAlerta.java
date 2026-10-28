package cl.adndigital.mejorvejez.alertas.core;

/**
 * Cuerpo de {@code POST /api/interno/alertas}: pide crear una alerta de un
 * tipo, con el origen que corresponda ({@code idTarea} para {@code
 * ATRASO_TAREA} y {@code CITA_PROXIMA}; {@code idDetallePlan} para {@code
 * REPOSICION_MEDICAMENTO}). El servicio core revalida todo con bloqueo antes
 * de crear: es idempotente por clave.
 */
public record SolicitudCrearAlerta(String tipoAlerta, Long idTarea, Long idDetallePlan) {
}
