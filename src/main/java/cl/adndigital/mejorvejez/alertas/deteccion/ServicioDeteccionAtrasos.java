package cl.adndigital.mejorvejez.alertas.deteccion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.adndigital.mejorvejez.alertas.core.CandidatoAtraso;
import cl.adndigital.mejorvejez.alertas.core.ClienteCore;
import cl.adndigital.mejorvejez.alertas.core.SolicitudCrearAlerta;

/**
 * Detector de atrasos: por cada tarea candidata que entrega el servicio core
 * ({@code GET /api/interno/alertas/candidatos-atraso}), aplica la regla de
 * atraso ({@code ahora > fechaHorario + minutosTolerancia}, solo para
 * {@code PENDIENTE} o {@code EN_CURSO} y con {@code generaAlerta}) y pide
 * crear la alerta correspondiente. Una corrida registra el inicio en el
 * servicio core con la ventana {@code [ahora − horas-retrospectiva, ahora]},
 * crea las alertas que correspondan, notifica lo pendiente y cierra la
 * corrida con sus totales — ver {@link CorridaProceso} para la conducta ante
 * una falla.
 */
@Service
public class ServicioDeteccionAtrasos {

    private static final String NOMBRE_PROCESO = "Detección de atrasos";
    private static final String ETIQUETA_TAREA = "Tarea";
    private static final Set<String> ESTADOS_QUE_GENERAN = Set.of(CodigosProcesos.ESTADO_TAREA_PENDIENTE,
            CodigosProcesos.ESTADO_TAREA_EN_CURSO);

    private final ClienteCore clienteCore;
    private final CorridaProceso corrida;
    private final Clock reloj;
    private final int horasRetrospectiva;

    public ServicioDeteccionAtrasos(ClienteCore clienteCore, NotificadorPendientes notificador, Clock reloj,
            @Value("${mejorvejez.alertas.horas-retrospectiva}") int horasRetrospectiva) {
        this.clienteCore = clienteCore;
        this.corrida = new CorridaProceso(clienteCore, notificador);
        this.reloj = reloj;
        this.horasRetrospectiva = horasRetrospectiva;
    }

    /** Corrida programada o manual (disparada por {@code POST /api/interno/procesos/deteccion-atrasos}). */
    public ResumenCorrida ejecutar() {
        return ejecutar(OffsetDateTime.now(reloj));
    }

    ResumenCorrida ejecutar(OffsetDateTime ahora) {
        OffsetDateTime desde = ahora.minusHours(horasRetrospectiva);
        return corrida.ejecutar(NOMBRE_PROCESO, CodigosProcesos.TIPO_EJECUCION_DETECCION_ATRASOS, desde, ahora,
                desde, errores -> ejecutarCuerpo(desde, ahora, errores));
    }

    private int ejecutarCuerpo(OffsetDateTime desde, OffsetDateTime ahora, List<String> errores) {
        int creadas = 0;
        for (CandidatoAtraso candidato : clienteCore.candidatosAtraso(desde, ahora)) {
            if (generaAlerta(candidato, ahora)) {
                SolicitudCrearAlerta solicitud = new SolicitudCrearAlerta(CodigosProcesos.TIPO_ATRASO_TAREA,
                        candidato.idTarea(), null);
                creadas += corrida.crearAlerta(NOMBRE_PROCESO, solicitud, ETIQUETA_TAREA, candidato.idTarea(),
                        errores);
            }
        }
        return creadas;
    }

    private boolean generaAlerta(CandidatoAtraso candidato, OffsetDateTime ahora) {
        if (!candidato.generaAlerta() || !ESTADOS_QUE_GENERAN.contains(candidato.codigoEstado())) {
            return false;
        }
        OffsetDateTime limite = candidato.fechaHorario().plusMinutes(candidato.minutosTolerancia());
        return ahora.isAfter(limite);
    }
}
