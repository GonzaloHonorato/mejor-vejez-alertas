package cl.adndigital.mejorvejez.alertas.deteccion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.alertas.core.ClienteCore;

/** Prueba {@link ServicioAlertasPreventivas} contra un servicio core simulado (MockWebServer). */
class ServicioAlertasPreventivasTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZoneOffset.of("-03:00"));
    private static final int HORAS_RETROSPECTIVA = 48;
    private static final int HORAS_CITA = 24;
    private static final int DIAS_REPOSICION = 3;

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

    private ServicioAlertasPreventivas servicio() {
        Clock reloj = Clock.fixed(AHORA.toInstant(), AHORA.getOffset());
        return new ServicioAlertasPreventivas(clienteCore, desde -> 0, reloj, HORAS_RETROSPECTIVA, HORAS_CITA,
                DIAS_REPOSICION);
    }

    private String candidatosPreventivos(String citas, String reposiciones) {
        return "{\"citas\":[" + citas + "],\"reposiciones\":[" + reposiciones + "]}";
    }

    @Test
    void preventivasPideCitasYReposiciones() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":700}"));
        core.enqueue(json(200, "{\"citas\":[],\"reposiciones\":[]}"));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.idEjecucion()).isEqualTo(700L);
        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isZero();
        assertThat(core.getRequestCount()).isEqualTo(3);

        core.takeRequest(); // POST /api/interno/ejecuciones
        RecordedRequest candidatos = core.takeRequest();
        assertThat(candidatos.getMethod()).isEqualTo("GET");
        assertThat(candidatos.getUrl().encodedPath()).isEqualTo("/api/interno/alertas/candidatos-preventivos");
        assertThat(candidatos.getUrl().queryParameter("ahora")).isEqualTo(AHORA.toString());
        assertThat(candidatos.getUrl().queryParameter("horasCita")).isEqualTo(String.valueOf(HORAS_CITA));
        assertThat(candidatos.getUrl().queryParameter("diasReposicion")).isEqualTo(String.valueOf(DIAS_REPOSICION));
    }

    /**
     * Si falla la creación, el resumen identifica el origen igual que el core: "Tarea {id}: {clase}" para una
     * cita y "Detalle {id}: {clase}" para una reposición — nunca el tipo de alerta crudo.
     */
    @Test
    void erroresDeCitaYReposicionUsanTareaYDetalleSegunElOrigen() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":701}"));
        core.enqueue(json(200, candidatosPreventivos(cita(5, AHORA.plusHours(1)), reposicion(7, "2026-11-05"))));
        core.enqueue(vacio(503)); // falla la alerta de la cita
        core.enqueue(vacio(503)); // falla la alerta de la reposición
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isEqualTo(2);
        assertThat(resumen.resumen()).contains("Tarea 5: ").contains("Detalle 7: ")
                .doesNotContain("CITA_PROXIMA 5").doesNotContain("REPOSICION_MEDICAMENTO 7");
    }

    @Test
    void corridaRegistraEjecucionEnCore() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":702}"));
        core.enqueue(json(200, candidatosPreventivos("", "")));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.idEjecucion()).isEqualTo(702L);
        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isZero();

        RecordedRequest iniciar = core.takeRequest();
        assertThat(iniciar.getMethod()).isEqualTo("POST");
        assertThat(iniciar.getUrl().encodedPath()).isEqualTo("/api/interno/ejecuciones");
        assertThat(iniciar.getBody().utf8()).contains("\"tipoEjecucion\":\"ALERTAS_PREVENTIVAS\"");

        core.takeRequest(); // GET candidatos-preventivos

        RecordedRequest finalizar = core.takeRequest();
        assertThat(finalizar.getMethod()).isEqualTo("PUT");
        assertThat(finalizar.getUrl().encodedPath()).isEqualTo("/api/interno/ejecuciones/702");
        assertThat(finalizar.getBody().utf8()).contains("\"codigoEstado\":\"EXITOSA\"");
    }

    @Test
    void coreCaidoNoRompeLaCorrida() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":703}"));
        core.enqueue(vacio(503));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.idEjecucion()).isEqualTo(703L);
        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isEqualTo(1);
        assertThat(resumen.resumen()).contains("Proceso:");

        core.takeRequest();
        core.takeRequest();
        RecordedRequest finalizarFallida = core.takeRequest();
        assertThat(finalizarFallida.getBody().utf8()).contains("\"codigoEstado\":\"FALLIDA\"");
    }

    @Test
    void creacionExitosaDeCitaYReposicion() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":704}"));
        core.enqueue(json(200, candidatosPreventivos(cita(8, AHORA.plusHours(2)), reposicion(9, "2026-11-05"))));
        core.enqueue(json(200, "{\"idAlerta\":1,\"creada\":true}"));
        core.enqueue(json(200, "{\"idAlerta\":2,\"creada\":true}"));
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.creadas()).isEqualTo(2);
        assertThat(resumen.errores()).isZero();
    }

    /**
     * Si el cuerpo de la corrida falla con algo que no es un error del servicio core (caso realista: el core
     * responde 200 con el cuerpo vacío en {@code candidatos-preventivos} y el acceso a las listas da {@code
     * NullPointerException}), la corrida no debe romperse: cierra como {@code FALLIDA} igual que un error REST.
     */
    @Test
    void cuerpoConErrorNoRestNoRompeLaCorrida() throws Exception {
        core.enqueue(json(201, "{\"idEjecucion\":705}"));
        core.enqueue(vacio(200)); // 200 sin cuerpo: candidatosPreventivos() devuelve null
        core.enqueue(vacio(200));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.idEjecucion()).isEqualTo(705L);
        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isEqualTo(1);
        assertThat(resumen.resumen()).contains("Proceso:");

        core.takeRequest();
        core.takeRequest();
        RecordedRequest finalizarFallida = core.takeRequest();
        assertThat(finalizarFallida.getMethod()).isEqualTo("PUT");
        assertThat(finalizarFallida.getBody().utf8()).contains("\"codigoEstado\":\"FALLIDA\"");
    }

    /** Si el core cae al abrir la ejecución, la corrida igual intenta su cuerpo y no envía el cierre (no hay id). */
    @Test
    void coreCaidoAlAbrirLaEjecucionNoEnviaCierre() throws Exception {
        core.enqueue(vacio(503)); // falla POST /api/interno/ejecuciones
        core.enqueue(json(200, candidatosPreventivos("", "")));

        ResumenCorrida resumen = servicio().ejecutar(AHORA);

        assertThat(resumen.idEjecucion()).isNull();
        assertThat(resumen.creadas()).isZero();
        assertThat(resumen.errores()).isZero();
        assertThat(core.getRequestCount()).isEqualTo(2); // nunca un tercer PUT de cierre
    }

    private String cita(long idTarea, OffsetDateTime fechaHorario) {
        return "{\"idTarea\":" + idTarea + ",\"fechaHorario\":\"" + fechaHorario + "\"}";
    }

    private String reposicion(long idDetallePlan, String fechaEstimadaTermino) {
        return "{\"idDetallePlan\":" + idDetallePlan + ",\"fechaEstimadaTermino\":\"" + fechaEstimadaTermino + "\"}";
    }

    private MockResponse json(int codigo, String cuerpo) {
        return new MockResponse.Builder().code(codigo).addHeader("Content-Type", "application/json").body(cuerpo)
                .build();
    }

    private MockResponse vacio(int codigo) {
        return new MockResponse.Builder().code(codigo).build();
    }
}
