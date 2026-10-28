package cl.adndigital.mejorvejez.alertas.deteccion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Prueba la cadena interna completa (filtro + ruta) con una clave de
 * servicio configurada: un patrón mal registrado en {@code
 * FilterRegistrationBean} (por ejemplo, que no cubra la ruta o que cubra de
 * más una ruta pública) se detectaría acá.
 */
@SpringBootTest(properties = { "mejorvejez.procesos.habilitados=false",
        "mejorvejez.interno.clave-servicio=clave-correcta" })
@AutoConfigureMockMvc
class ControladorProcesosSeguridadTest {

    private static final String RUTA_DETECCION = "/api/interno/procesos/deteccion-atrasos";

    private static MockWebServer core;

    @DynamicPropertySource
    static void registrarCoreSimulado(DynamicPropertyRegistry registro) throws Exception {
        core = new MockWebServer();
        core.start();
        registro.add("mejorvejez.core-url", () -> core.url("/").toString());
    }

    @AfterAll
    static void cerrarCore() throws Exception {
        core.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rutaInternaSinClaveDa401() throws Exception {
        mockMvc.perform(post(RUTA_DETECCION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));
    }

    @Test
    void rutaInternaConClaveIncorrectaDa401() throws Exception {
        mockMvc.perform(post(RUTA_DETECCION).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-incorrecta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));
    }

    @Test
    void rutaInternaConClaveCorrectaPasa() throws Exception {
        core.enqueue(new MockResponse.Builder().code(201).addHeader("Content-Type", "application/json")
                .body("{\"idEjecucion\":1}").build());
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("[]")
                .build());
        core.enqueue(new MockResponse.Builder().code(200).build());

        mockMvc.perform(post(RUTA_DETECCION).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isOk());
    }

    @Test
    void rutaPublicaSinClaveDa200() throws Exception {
        mockMvc.perform(get("/api/salud")).andExpect(status().isOk());
    }
}
