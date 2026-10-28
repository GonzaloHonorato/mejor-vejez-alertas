package cl.adndigital.mejorvejez.alertas.envio;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cl.adndigital.mejorvejez.alertas.core.ClienteCore;
import cl.adndigital.mejorvejez.alertas.core.DatosAlertaCorreo;
import cl.adndigital.mejorvejez.alertas.core.IntentoReservado;
import cl.adndigital.mejorvejez.alertas.core.RespuestaReservaIntentos;
import cl.adndigital.mejorvejez.alertas.core.SolicitudResultadoIntento;
import cl.adndigital.mejorvejez.alertas.deteccion.NotificadorPendientes;

/**
 * Implementación real de {@link NotificadorPendientes}: por cada alerta
 * pendiente que entrega el servicio core ({@code GET
 * /api/interno/alertas/envios-pendientes}), reserva sus intentos ({@code
 * POST .../intentos/reservar}), envía cada par por CORREO (los demás canales
 * quedan FALLIDO "Canal no habilitado", porque este microservicio todavía no
 * tiene otro enviador) y confirma el resultado con {@code PUT
 * /api/interno/intentos/{id}}.
 *
 * <p>Si el correo no está configurado ({@link EnviadorCorreo#configurado()}
 * en falso), esta corrida no pide {@code envios-pendientes} ni reserva nada:
 * devuelve 0 y deja un {@code LOG.warn}. Así las alertas no gastan sus
 * intentos contra un SMTP que no existe: siguen sin ningún intento y
 * {@code envios-pendientes} las sigue listando hasta que se configure el
 * correo, dentro de la ventana de 48 h del core.
 *
 * <p>Un error inesperado al notificar una alerta no detiene las demás. Si el
 * servicio core no responde al confirmar un intento, se registra en el log y
 * se sigue: ese intento queda PENDIENTE en el core, expira solo a los 10
 * minutos y se reintenta en una corrida posterior. El total devuelto cuenta
 * solo los correos que quedaron ENVIADO.
 */
@Service
public class ServicioNotificacionAlertas implements NotificadorPendientes {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioNotificacionAlertas.class);

    private static final String CANAL_CORREO = "CORREO";
    private static final String RESULTADO_ENVIADO = "ENVIADO";
    private static final String RESULTADO_FALLIDO = "FALLIDO";
    private static final String DETALLE_CANAL_NO_HABILITADO = "Canal no habilitado";

    private final ClienteCore clienteCore;
    private final EnviadorCorreo enviadorCorreo;
    private final RedactorCorreoAlertas redactor;

    public ServicioNotificacionAlertas(ClienteCore clienteCore, EnviadorCorreo enviadorCorreo,
            RedactorCorreoAlertas redactor) {
        this.clienteCore = clienteCore;
        this.enviadorCorreo = enviadorCorreo;
        this.redactor = redactor;
    }

    @Override
    public int notificarPendientes(OffsetDateTime desde) {
        if (!enviadorCorreo.configurado()) {
            LOG.warn(EnviadorCorreo.MENSAJE_NO_CONFIGURADO_LOG);
            return 0;
        }
        int enviados = 0;
        for (Long idAlerta : clienteCore.enviosPendientes(desde)) {
            try {
                enviados += notificarAlerta(idAlerta);
            } catch (RuntimeException ex) {
                LOG.error("Fallo inesperado al notificar la alerta {}: {}", idAlerta, ex.getClass().getSimpleName());
                LOG.debug("Traza del fallo al notificar la alerta {}", idAlerta, ex);
            }
        }
        return enviados;
    }

    private int notificarAlerta(long idAlerta) {
        RespuestaReservaIntentos respuesta = clienteCore.reservarIntentos(idAlerta);
        int enviados = 0;
        for (IntentoReservado intento : respuesta.intentos()) {
            if (notificarIntento(respuesta.alerta(), intento)) {
                enviados++;
            }
        }
        return enviados;
    }

    private boolean notificarIntento(DatosAlertaCorreo alerta, IntentoReservado intento) {
        if (!CANAL_CORREO.equals(intento.canal())) {
            registrarResultado(intento.idIntento(), RESULTADO_FALLIDO, DETALLE_CANAL_NO_HABILITADO, null);
            return false;
        }
        MensajeCorreo mensaje = redactor.redactar(alerta, intento.nombre(), intento.destino());
        ResultadoEnvio resultado;
        try {
            resultado = enviadorCorreo.enviar(mensaje);
        } catch (RuntimeException ex) {
            // Solo la clase: el mensaje de la excepción podría traer el destino.
            resultado = ResultadoEnvio.fallido(ex.getClass().getSimpleName());
        }
        registrarResultado(intento.idIntento(), resultado.exito() ? RESULTADO_ENVIADO : RESULTADO_FALLIDO,
                resultado.detalleError(), resultado.referenciaProveedor());
        return resultado.exito();
    }

    private void registrarResultado(Long idIntento, String resultado, String detalleError,
            String referenciaProveedor) {
        try {
            clienteCore.registrarResultadoIntento(idIntento,
                    new SolicitudResultadoIntento(resultado, detalleError, referenciaProveedor));
        } catch (RuntimeException ex) {
            LOG.error("No se pudo registrar el resultado del intento {} en el servicio core: {}", idIntento,
                    ex.getClass().getSimpleName());
        }
    }
}
