package cl.adndigital.mejorvejez.alertas.deteccion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.adndigital.mejorvejez.alertas.core.ClienteCore;
import cl.adndigital.mejorvejez.alertas.core.RespuestaCrearAlerta;
import cl.adndigital.mejorvejez.alertas.core.SolicitudCrearAlerta;

/**
 * Lo que comparten la detección de atrasos y las alertas preventivas: abrir
 * la ejecución en el servicio core, correr el cuerpo propio de cada corrida,
 * notificar lo pendiente y cerrar la ejecución con su estado final. Nunca
 * lanza una excepción: si el servicio core no responde al abrir la
 * ejecución, el cuerpo igual se intenta (queda con {@code idEjecucion =
 * null}); si el cuerpo falla a mitad, cierra como {@code FALLIDA} (si llegó
 * a abrirla); si falla solo la notificación, no descarta lo ya creado y
 * deja el error en el resumen.
 */
class CorridaProceso {

    private static final Logger LOG = LoggerFactory.getLogger(CorridaProceso.class);
    private static final String SEPARADOR_ERRORES = "\n";

    private final ClienteCore clienteCore;
    private final NotificadorPendientes notificador;

    CorridaProceso(ClienteCore clienteCore, NotificadorPendientes notificador) {
        this.clienteCore = clienteCore;
        this.notificador = notificador;
    }

    ResumenCorrida ejecutar(String nombreProceso, String tipoEjecucion, OffsetDateTime inicioVentana,
            OffsetDateTime finVentana, OffsetDateTime desdeNotificacion, CuerpoCorrida cuerpo) {
        Long idEjecucion = iniciar(nombreProceso, tipoEjecucion, inicioVentana, finVentana);
        List<String> errores = new ArrayList<>();
        int creadas;
        try {
            creadas = cuerpo.ejecutar(errores);
        } catch (RuntimeException ex) {
            LOG.error("{}: la corrida falló a mitad de camino", nombreProceso, ex);
            errores.add("Proceso: " + ex.getClass().getSimpleName());
            return cerrar(nombreProceso, idEjecucion, 0, errores, true);
        }
        notificar(nombreProceso, desdeNotificacion, errores);
        return cerrar(nombreProceso, idEjecucion, creadas, errores, false);
    }

    /**
     * Pide crear una alerta; si falla, acumula {@code "{etiquetaOrigen} {idOrigen}: {claseDelError}"} (el mismo
     * formato que usa el servicio core) sin detener la corrida.
     */
    int crearAlerta(String nombreProceso, SolicitudCrearAlerta solicitud, String etiquetaOrigen, long idOrigen,
            List<String> errores) {
        try {
            RespuestaCrearAlerta respuesta = clienteCore.crearAlerta(solicitud);
            return respuesta.creada() ? 1 : 0;
        } catch (RuntimeException ex) {
            LOG.warn("{}: falló la creación de una alerta ({} {})", nombreProceso, etiquetaOrigen, idOrigen, ex);
            errores.add(etiquetaOrigen + " " + idOrigen + ": " + ex.getClass().getSimpleName());
            return 0;
        }
    }

    private Long iniciar(String nombreProceso, String tipoEjecucion, OffsetDateTime inicioVentana,
            OffsetDateTime finVentana) {
        try {
            return clienteCore.iniciarEjecucion(tipoEjecucion, inicioVentana, finVentana);
        } catch (RuntimeException ex) {
            LOG.error("{}: no se pudo registrar el inicio de la corrida en el servicio core", nombreProceso, ex);
            return null;
        }
    }

    /**
     * Notifica lo pendiente. Cualquier excepción (no solo del servicio core: la implementación real puede fallar
     * por otros motivos) se acumula como error y nunca escapa de acá, para que la corrida siempre llegue a
     * cerrar su ejecución.
     */
    private void notificar(String nombreProceso, OffsetDateTime desde, List<String> errores) {
        try {
            int enviados = notificador.notificarPendientes(desde);
            LOG.info("{}: {} correos enviados al notificar lo pendiente", nombreProceso, enviados);
        } catch (RuntimeException ex) {
            LOG.error("{}: la notificación de lo pendiente falló", nombreProceso, ex);
            errores.add("Notificación: " + ex.getClass().getSimpleName());
        }
    }

    private ResumenCorrida cerrar(String nombreProceso, Long idEjecucion, int creadas, List<String> errores,
            boolean falloAMitad) {
        String codigoEstado = falloAMitad ? CodigosProcesos.ESTADO_EJECUCION_FALLIDA
                : EstadoCorrida.codigo(creadas, errores.size());
        String resumen = errores.isEmpty() ? null : String.join(SEPARADOR_ERRORES, errores);
        if (idEjecucion != null) {
            try {
                clienteCore.finalizarEjecucion(idEjecucion, codigoEstado, creadas, errores.size(), resumen);
            } catch (RuntimeException ex) {
                LOG.error("{}: no se pudo registrar el cierre de la corrida {} en el servicio core", nombreProceso,
                        idEjecucion, ex);
            }
        }
        return new ResumenCorrida(idEjecucion, creadas, errores.size(), resumen);
    }

    /** El cuerpo propio de cada corrida: pide sus candidatas y crea las alertas que correspondan. */
    @FunctionalInterface
    interface CuerpoCorrida {

        int ejecutar(List<String> errores);
    }
}
