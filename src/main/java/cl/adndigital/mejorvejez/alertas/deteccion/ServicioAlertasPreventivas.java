package cl.adndigital.mejorvejez.alertas.deteccion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.adndigital.mejorvejez.alertas.core.CandidatosPreventivos;
import cl.adndigital.mejorvejez.alertas.core.ClienteCore;
import cl.adndigital.mejorvejez.alertas.core.SolicitudCrearAlerta;

/**
 * Detector de alertas preventivas: cita próxima y reposición de un
 * medicamento. El servicio core ({@code GET
 * /api/interno/alertas/candidatos-preventivos}) ya entrega solo las citas y
 * las reposiciones sin una alerta vigente, con los parámetros {@code
 * horasCita} y {@code diasReposicion}; este microservicio pide crear la
 * alerta de cada una. Una corrida registra el inicio en el servicio core con
 * la ventana {@code [ahora, ahora + horas-cita]}, crea las alertas que
 * correspondan, notifica lo pendiente con el mismo umbral que la detección
 * de atrasos y cierra la corrida con sus totales — ver {@link
 * CorridaProceso} para la conducta ante una falla.
 */
@Service
public class ServicioAlertasPreventivas {

    private static final String NOMBRE_PROCESO = "Alertas preventivas";
    private static final String ETIQUETA_TAREA = "Tarea";
    private static final String ETIQUETA_DETALLE = "Detalle";

    private final ClienteCore clienteCore;
    private final CorridaProceso corrida;
    private final Clock reloj;
    private final int horasRetrospectiva;
    private final int horasCita;
    private final int diasReposicion;

    public ServicioAlertasPreventivas(ClienteCore clienteCore, NotificadorPendientes notificador, Clock reloj,
            @Value("${mejorvejez.alertas.horas-retrospectiva}") int horasRetrospectiva,
            @Value("${mejorvejez.alertas.horas-cita}") int horasCita,
            @Value("${mejorvejez.alertas.dias-reposicion}") int diasReposicion) {
        this.clienteCore = clienteCore;
        this.corrida = new CorridaProceso(clienteCore, notificador);
        this.reloj = reloj;
        this.horasRetrospectiva = horasRetrospectiva;
        this.horasCita = horasCita;
        this.diasReposicion = diasReposicion;
    }

    /** Corrida programada o manual (disparada por {@code POST /api/interno/procesos/alertas-preventivas}). */
    public ResumenCorrida ejecutar() {
        return ejecutar(OffsetDateTime.now(reloj));
    }

    ResumenCorrida ejecutar(OffsetDateTime ahora) {
        OffsetDateTime finVentana = ahora.plusHours(horasCita);
        OffsetDateTime desdeNotificacion = ahora.minusHours(horasRetrospectiva);
        return corrida.ejecutar(NOMBRE_PROCESO, CodigosProcesos.TIPO_EJECUCION_ALERTAS_PREVENTIVAS, ahora,
                finVentana, desdeNotificacion, errores -> ejecutarCuerpo(ahora, errores));
    }

    private int ejecutarCuerpo(OffsetDateTime ahora, List<String> errores) {
        int creadas = 0;
        CandidatosPreventivos candidatos = clienteCore.candidatosPreventivos(ahora, horasCita, diasReposicion);
        for (var cita : candidatos.citas()) {
            SolicitudCrearAlerta solicitud = new SolicitudCrearAlerta(CodigosProcesos.TIPO_CITA_PROXIMA,
                    cita.idTarea(), null);
            creadas += corrida.crearAlerta(NOMBRE_PROCESO, solicitud, ETIQUETA_TAREA, cita.idTarea(), errores);
        }
        for (var reposicion : candidatos.reposiciones()) {
            SolicitudCrearAlerta solicitud = new SolicitudCrearAlerta(CodigosProcesos.TIPO_REPOSICION_MEDICAMENTO,
                    null, reposicion.idDetallePlan());
            creadas += corrida.crearAlerta(NOMBRE_PROCESO, solicitud, ETIQUETA_DETALLE, reposicion.idDetallePlan(),
                    errores);
        }
        return creadas;
    }
}
