package cl.adndigital.mejorvejez.alertas.envio;

/**
 * Resultado de un intento de envío ({@link EnviadorCorreo}), nunca una
 * excepción: {@code exito} indica si se pudo entregar, {@code
 * referenciaProveedor} es el identificador que da el proveedor (el
 * Message-ID del correo) y {@code detalleError} es el detalle de un fallo,
 * sin direcciones de correo.
 */
record ResultadoEnvio(boolean exito, String referenciaProveedor, String detalleError) {

    static ResultadoEnvio exitoso(String referenciaProveedor) {
        return new ResultadoEnvio(true, referenciaProveedor, null);
    }

    static ResultadoEnvio fallido(String detalleError) {
        return new ResultadoEnvio(false, null, detalleError);
    }
}
