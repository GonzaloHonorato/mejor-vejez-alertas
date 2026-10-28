package cl.adndigital.mejorvejez.alertas.core;

import java.time.LocalDate;

/** Un detalle de plan candidato a una alerta de reposición de medicamento, parte de {@link CandidatosPreventivos}. */
public record CandidatoReposicion(long idDetallePlan, LocalDate fechaEstimadaTermino) {
}
