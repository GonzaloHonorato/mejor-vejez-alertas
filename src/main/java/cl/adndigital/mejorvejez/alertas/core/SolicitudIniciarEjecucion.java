package cl.adndigital.mejorvejez.alertas.core;

import java.time.OffsetDateTime;

/**
 * Cuerpo de {@code POST /api/interno/ejecuciones}: registra el inicio de una
 * corrida propia ({@code tipoEjecucion}: por ejemplo {@code
 * DETECCION_ATRASOS} o {@code ALERTAS_PREVENTIVAS}), con su ventana.
 */
public record SolicitudIniciarEjecucion(String tipoEjecucion, OffsetDateTime inicioVentana,
        OffsetDateTime finVentana) {
}
