package cl.adndigital.mejorvejez.alertas.deteccion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cl.adndigital.mejorvejez.alertas.comun.ZonaNegocio;

/**
 * Dispara los procesos programados de alertas, en America/Santiago: la
 * detección de atrasos, cada 5 minutos ({@code
 * mejorvejez.alertas.cron-deteccion}), y las alertas preventivas de cita y
 * reposición, cada hora ({@code mejorvejez.alertas.cron-preventivas}). Se
 * apaga con {@code mejorvejez.procesos.habilitados=false} ({@code
 * PROCESOS_HABILITADOS}).
 */
@Component
@ConditionalOnProperty(name = "mejorvejez.procesos.habilitados", havingValue = "true", matchIfMissing = true)
public class ProgramadorAlertas {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramadorAlertas.class);

    private final ServicioDeteccionAtrasos servicioDeteccion;
    private final ServicioAlertasPreventivas servicioPreventivas;

    public ProgramadorAlertas(ServicioDeteccionAtrasos servicioDeteccion,
            ServicioAlertasPreventivas servicioPreventivas) {
        this.servicioDeteccion = servicioDeteccion;
        this.servicioPreventivas = servicioPreventivas;
    }

    /** Corre la detección de atrasos y registra el resultado en el log; nunca propaga excepciones al planificador. */
    @Scheduled(cron = "${mejorvejez.alertas.cron-deteccion}", zone = ZonaNegocio.NOMBRE)
    public void detectarAtrasos() {
        try {
            ResumenCorrida resumen = servicioDeteccion.ejecutar();
            LOG.info("Detección de atrasos: ejecución {}, {} creadas, {} errores", resumen.idEjecucion(),
                    resumen.creadas(), resumen.errores());
        } catch (RuntimeException ex) {
            LOG.error("Detección de atrasos: la corrida programada falló", ex);
        }
    }

    /** Corre las alertas preventivas y registra el resultado en el log; nunca propaga excepciones al planificador. */
    @Scheduled(cron = "${mejorvejez.alertas.cron-preventivas}", zone = ZonaNegocio.NOMBRE)
    public void generarAlertasPreventivas() {
        try {
            ResumenCorrida resumen = servicioPreventivas.ejecutar();
            LOG.info("Alertas preventivas: ejecución {}, {} creadas, {} errores", resumen.idEjecucion(),
                    resumen.creadas(), resumen.errores());
        } catch (RuntimeException ex) {
            LOG.error("Alertas preventivas: la corrida programada falló", ex);
        }
    }
}
