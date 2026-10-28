package cl.adndigital.mejorvejez.alertas.core;

import java.time.OffsetDateTime;

/**
 * Una tarea candidata a una alerta de atraso, tal como la devuelve {@code
 * GET /api/interno/alertas/candidatos-atraso}. El servicio core ya filtra
 * por estado ({@code PENDIENTE}/{@code EN_CURSO}) y por {@code
 * generaAlerta}; este microservicio revalida ambos por defensa y aplica la
 * tolerancia ({@code ahora > fechaHorario + minutosTolerancia}) para decidir
 * si corresponde crear la alerta.
 */
public record CandidatoAtraso(long idTarea, long idGrupo, long idAdulto, OffsetDateTime fechaHorario,
        int minutosTolerancia, String codigoEstado, boolean generaAlerta) {
}
