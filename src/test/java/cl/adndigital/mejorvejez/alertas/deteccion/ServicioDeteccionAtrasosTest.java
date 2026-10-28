package cl.adndigital.mejorvejez.alertas.deteccion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.alertas.core.ClienteCore;

/** Prueba {@link ServicioDeteccionAtrasos} contra un servicio core simulado (MockWebServer). */
class ServicioDeteccionAtrasosTest {

    /** Lunes 2026-11-02 10:00 en Santiago (−03:00). */
    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZoneOffset.of("-03:00"));
    private static final int HORAS_RETROSPECTIVA = 48;

    private MockWebServer core;
    private ClienteCore clienteCore;

    @BeforeEach
    void preparar() throws Exception {
        core = new MockWebServer();
        core.start();
        clienteCore = new ClienteCore(core.url("/").toString(), "clave-de-prueba");
    }

    @AfterEach
    void cerrar() throws Exception {
        core.close();
    }

    private ServicioDeteccionAtrasos servicio() {
        return servicio(desde -> 0);
    }

    private ServicioDeteccionAtrasos servicio(NotificadorPendientes notificador) {
        Clock reloj = Clock.fixed(AHORA.toInstant(), AHORA.getOffset());
        return new ServicioDeteccionAtrasos(clienteCore, notificador, reloj, HORAS_RETROSPECTIVA);
    }

    /** Tarea a las 09:30 con 30 minutos de tolerancia: el límite es exactamente las 10:00, igual a "ahora". */
    @Test
    void atrasoSoloSiSuperaTolerancia() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":500}"));
        core.enqueue(json(200, candidatos(candidato(1, AHORA.minusMinutes(30), 30, "PENDIENTE", true))));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isZero();
        assertThat(core.getRequestCount()).isEqualTo(3);
    }

    @Test
    void enCursoTambienCuenta() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":501}"));
        core.enqueue(json(200, candidatos(candidato(2, AHORA.minusMinutes(40), 30, "EN_CURSO", true))));
        core.enqueue(json(200, "{\"idAlerta\":9001,\"creada\":true}"));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.creadas()).isEqualTo(1);
        assertThat(resumen.errores()).isZero();
        core.takeRequest(); // POST /api/interno/ejecuciones
        core.takeRequest(); // GET candidatos-atraso
        RecordedRequest crearAlerta = core.takeRequest();
        assertThat(crearAlerta.getMethod()).isEqualTo("POST");
        assertThat(crearAlerta.getUrl().encodedPath()).isEqualTo("/api/interno/alertas");
        assertThat(crearAlerta.getBody().utf8()).contains("\"tipoAlerta\":\"ATRASO_TAREA\"", "\"idTarea\":2");
    }

    @Test
    void sinGeneraAlertaNoCrea() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":502}"));
        core.enqueue(json(200, candidatos(candidato(3, AHORA.minusMinutes(40), 30, "PENDIENTE", false))));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isZero();
        assertThat(core.getRequestCount()).isEqualTo(3);
    }

    @Test
    void corridaRegistraEjecucionEnCore() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":503}"));
        core.enqueue(json(200, "[]"));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.idEjecucion()).isEqualTo(503L);
        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isZero();
        assertThat(resumen.resumen()).isNull();

        RecordedRequest iniciar = core.takeRequest();
        assertThat(iniciar.getMethod()).isEqualTo("POST");
        assertThat(iniciar.getUrl().encodedPath()).isEqualTo("/api/interno/ejecuciones");
        assertThat(iniciar.getBody().utf8()).contains("\"tipoEjecucion\":\"DETECCION_ATRASOS\"",
                "\"inicioVentana\":\"" + iso(AHORA.minusHours(HORAS_RETROSPECTIVA)) + "\"",
                "\"finVentana\":\"" + iso(AHORA) + "\"");

        core.takeRequest(); // GET candidatos-atraso

        RecordedRequest finalizar = core.takeRequest();
        assertThat(finalizar.getMethod()).isEqualTo("PUT");
        assertThat(finalizar.getUrl().encodedPath()).isEqualTo("/api/interno/ejecuciones/503");
        assertThat(finalizar.getBody().utf8()).contains("\"codigoEstado\":\"EXITOSA\"", "\"creadas\":0",
                "\"errores\":0");
    }

    @Test
    void coreCaidoNoRompeLaCorrida() throws Exception {
        // Primera corrida: el servicio core abre la ejecución pero se cae al pedir las candidatas.
        core.enqueue(json(201, "{\"idEjecucion\":600}"));
        core.enqueue(vacio(503));
        core.enqueue(vacio(200));

        ResumenCorrida primera = servicio().ejecutar(AHORA);

        assertThat(primera.idEjecucion()).isEqualTo(600L);
        assertThat(primera.creadas()).isZero();
        assertThat(primera.errores()).isEqualTo(1);
        assertThat(primera.resumen()).contains("Proceso:");

        core.takeRequest();
        core.takeRequest();
        RecordedRequest finalizarFallida = core.takeRequest();
        assertThat(finalizarFallida.getBody().utf8()).contains("\"codigoEstado\":\"FALLIDA\"");

        // Siguiente disparo: el servicio core ya respondió con normalidad.
        core.enqueue(json(201, "{\"idEjecucion\":601}"));
        core.enqueue(json(200, "[]"));
        core.enqueue(vacio(200));

        ResumenCorrida segunda = servicio().ejecutar(AHORA);

        assertThat(segunda.idEjecucion()).isEqualTo(601L);
        assertThat(segunda.creadas()).isZero();
        assertThat(segunda.errores()).isZero();
    }

    /**
     * Si "notificar pendientes" lanza algo que no es un error del servicio core (la implementación real puede
     * lanzar otras excepciones), la corrida no debe romperse: cierra la ejecución igual y no relanza.
     */
    @Test
    void notificadorFallaNoRompeLaCorrida() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":610}"));
        core.enqueue(json(200, "[]"));
        core.enqueue(vacio(200));
        NotificadorPendientes notificadorQueFalla = desde -> {
            throw new IllegalStateException("Fallo inyectado en la prueba");
        };

        ResumenCorrida resumen = servicio(notificadorQueFalla).ejecutar(AHORA);

        assertThat(resumen.idEjecucion()).isEqualTo(610L);
        assertThat(resumen.errores()).isEqualTo(1);
        assertThat(resumen.resumen()).contains("Notificación:", "IllegalStateException");

        core.takeRequest(); // POST /api/interno/ejecuciones
        core.takeRequest(); // GET candidatos-atraso
        RecordedRequest finalizar = core.takeRequest();
        assertThat(finalizar.getMethod()).isEqualTo("PUT");
        assertThat(finalizar.getUrl().encodedPath()).isEqualTo("/api/interno/ejecuciones/610");
    }

    @Test
    void candidatosAtrasoUsaDesdeYHastaEnIso() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":611}"));
        core.enqueue(json(200, "[]"));
        core.enqueue(vacio(200));

        servicio().ejecutar(AHORA);

        core.takeRequest(); // POST /api/interno/ejecuciones
        RecordedRequest candidatos = core.takeRequest();
        assertThat(candidatos.getMethod()).isEqualTo("GET");
        assertThat(candidatos.getUrl().encodedPath()).isEqualTo("/api/interno/alertas/candidatos-atraso");
        assertThat(candidatos.getUrl().queryParameter("desde"))
                .isEqualTo(AHORA.minusHours(HORAS_RETROSPECTIVA).toString());
        assertThat(candidatos.getUrl().queryParameter("hasta")).isEqualTo(AHORA.toString());
    }

    /** El formato exacto (siempre con segundos) con el que Jackson serializa un {@code OffsetDateTime}. */
    private String iso(OffsetDateTime instante) {
        return instante.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String candidato(long idTarea, OffsetDateTime fechaHorario, int minutosTolerancia, String codigoEstado,
            boolean generaAlerta) {
        return "{\"idTarea\":" + idTarea + ",\"idGrupo\":10,\"idAdulto\":20,\"fechaHorario\":\"" + fechaHorario
                + "\",\"minutosTolerancia\":" + minutosTolerancia + ",\"codigoEstado\":\"" + codigoEstado
                + "\",\"generaAlerta\":" + generaAlerta + "}";
    }

    private String candidatos(String... elementos) {
        return "[" + String.join(",", elementos) + "]";
    }

    private MockResponse json(int codigo, String cuerpo) {
        return new MockResponse.Builder().code(codigo).addHeader("Content-Type", "application/json").body(cuerpo)
                .build();
    }

    private MockResponse vacio(int codigo) {
        return new MockResponse.Builder().code(codigo).build();
    }
}
