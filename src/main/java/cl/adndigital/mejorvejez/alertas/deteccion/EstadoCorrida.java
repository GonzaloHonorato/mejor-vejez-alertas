package cl.adndigital.mejorvejez.alertas.deteccion;

/**
 * Estado final de una corrida: {@code EXITOSA} sin errores; {@code PARCIAL}
 * con errores y al menos una alerta creada; {@code FALLIDA} con errores y
 * nada creado.
 */
final class EstadoCorrida {

    private EstadoCorrida() {
    }

    static String codigo(int creadas, int errores) {
        if (errores == 0) {
            return CodigosProcesos.ESTADO_EJECUCION_EXITOSA;
        }
        return creadas > 0 ? CodigosProcesos.ESTADO_EJECUCION_PARCIAL : CodigosProcesos.ESTADO_EJECUCION_FALLIDA;
    }
}
