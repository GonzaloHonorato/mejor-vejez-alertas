package cl.adndigital.mejorvejez.alertas.core;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Cliente REST del servicio core: todas las llamadas a {@code
 * /api/interno/**} que necesita este microservicio, con la cabecera de la
 * clave de servicio y un tiempo máximo de 10 segundos para conectar y para
 * leer la respuesta.
 */
@Component
public class ClienteCore {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String RUTA_CANDIDATOS_ATRASO = "/api/interno/alertas/candidatos-atraso";
    private static final String RUTA_CANDIDATOS_PREVENTIVOS = "/api/interno/alertas/candidatos-preventivos";
    private static final String RUTA_ALERTAS = "/api/interno/alertas";
    private static final String RUTA_EJECUCIONES = "/api/interno/ejecuciones";
    private static final String RUTA_EJECUCION = "/api/interno/ejecuciones/{idEjecucion}";
    private static final String RUTA_ENVIOS_PENDIENTES = "/api/interno/alertas/envios-pendientes";
    private static final String RUTA_RESERVAR_INTENTOS = "/api/interno/alertas/{idAlerta}/intentos/reservar";
    private static final String RUTA_INTENTO = "/api/interno/intentos/{idIntento}";

    private final RestClient restClient;

    public ClienteCore(@Value("${mejorvejez.core-url}") String urlCore,
            @Value("${mejorvejez.interno.clave-servicio}") String claveServicio) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT);
        fabrica.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(urlCore)
                .requestFactory(fabrica)
                .defaultHeader(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, claveServicio)
                .build();
    }

    /** Tareas candidatas a una alerta de atraso, en la ventana {@code [desde, hasta]}. */
    public List<CandidatoAtraso> candidatosAtraso(OffsetDateTime desde, OffsetDateTime hasta) {
        return restClient.get()
                .uri(constructor -> constructor.path(RUTA_CANDIDATOS_ATRASO)
                        .queryParam("desde", "{desde}")
                        .queryParam("hasta", "{hasta}")
                        .build(Map.of("desde", desde, "hasta", hasta)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<CandidatoAtraso>>() {
                });
    }

    /** Citas próximas y reposiciones de medicamento candidatas a una alerta preventiva. */
    public CandidatosPreventivos candidatosPreventivos(OffsetDateTime ahora, int horasCita, int diasReposicion) {
        return restClient.get()
                .uri(constructor -> constructor.path(RUTA_CANDIDATOS_PREVENTIVOS)
                        .queryParam("ahora", "{ahora}")
                        .queryParam("horasCita", "{horasCita}")
                        .queryParam("diasReposicion", "{diasReposicion}")
                        .build(Map.of("ahora", ahora, "horasCita", horasCita, "diasReposicion", diasReposicion)))
                .retrieve()
                .body(CandidatosPreventivos.class);
    }

    /** Pide crear una alerta; idempotente por clave. */
    public RespuestaCrearAlerta crearAlerta(SolicitudCrearAlerta solicitud) {
        return restClient.post()
                .uri(RUTA_ALERTAS)
                .body(solicitud)
                .retrieve()
                .body(RespuestaCrearAlerta.class);
    }

    /** Registra el inicio de una corrida propia y devuelve su id. */
    public long iniciarEjecucion(String tipoEjecucion, OffsetDateTime inicioVentana, OffsetDateTime finVentana) {
        RespuestaIdEjecucion respuesta = restClient.post()
                .uri(RUTA_EJECUCIONES)
                .body(new SolicitudIniciarEjecucion(tipoEjecucion, inicioVentana, finVentana))
                .retrieve()
                .body(RespuestaIdEjecucion.class);
        return respuesta.idEjecucion();
    }

    /** Cierra una corrida ya iniciada con su estado final y sus totales. */
    public void finalizarEjecucion(long idEjecucion, String codigoEstado, int creadas, int errores, String resumen) {
        restClient.put()
                .uri(RUTA_EJECUCION, idEjecucion)
                .body(new SolicitudFinalizarEjecucion(codigoEstado, creadas, errores, resumen))
                .retrieve()
                .toBodilessEntity();
    }

    /** Ids de las alertas ABIERTA detectadas desde {@code desde} con algún par pendiente de notificar. */
    public List<Long> enviosPendientes(OffsetDateTime desde) {
        return restClient.get()
                .uri(constructor -> constructor.path(RUTA_ENVIOS_PENDIENTES)
                        .queryParam("desde", "{desde}")
                        .build(Map.of("desde", desde)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Long>>() {
                });
    }

    /** Reserva en el servicio core los pares (destinatario, canal) pendientes de enviar de una alerta. */
    public RespuestaReservaIntentos reservarIntentos(long idAlerta) {
        return restClient.post()
                .uri(RUTA_RESERVAR_INTENTOS, idAlerta)
                .retrieve()
                .body(RespuestaReservaIntentos.class);
    }

    /** Confirma en el servicio core el resultado de un intento ya reservado. */
    public void registrarResultadoIntento(long idIntento, SolicitudResultadoIntento solicitud) {
        restClient.put()
                .uri(RUTA_INTENTO, idIntento)
                .body(solicitud)
                .retrieve()
                .toBodilessEntity();
    }
}
