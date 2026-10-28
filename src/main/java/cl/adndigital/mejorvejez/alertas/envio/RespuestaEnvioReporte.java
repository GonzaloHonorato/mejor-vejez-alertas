package cl.adndigital.mejorvejez.alertas.envio;

import java.util.List;

/** Respuesta de {@code POST /api/interno/envios-reporte}: el resultado del envío a cada destinatario. */
public record RespuestaEnvioReporte(List<ResultadoDestinatario> resultados) {
}
