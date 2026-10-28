package cl.adndigital.mejorvejez.alertas.core;

import java.time.OffsetDateTime;

/** Una tarea de controles médicos candidata a una alerta de cita próxima, parte de {@link CandidatosPreventivos}. */
public record CandidatoCita(long idTarea, OffsetDateTime fechaHorario) {
}
