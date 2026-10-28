package cl.adndigital.mejorvejez.alertas.deteccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Prueba el arranque del microservicio con los procesos programados
 * apagados: {@link ProgramadorAlertas} no se registra y la salud sigue
 * pública. Sin {@code mejorvejez.interno.clave-servicio} configurada
 * (default vacío): toda ruta interna rechaza, aunque venga una clave.
 */
@SpringBootTest(properties = "mejorvejez.procesos.habilitados=false")
@AutoConfigureMockMvc
class ControladorProcesosTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void procesosDesactivadosNoProgramanCorridas() {
        assertThat(contexto.getBeanNamesForType(ProgramadorAlertas.class)).isEmpty();
    }

    @Test
    void saludResponde200() throws Exception {
        mockMvc.perform(get("/api/salud")).andExpect(status().isOk());
    }

    @Test
    void rutaProcesoConClaveSinVariableConfiguradaDa401() throws Exception {
        mockMvc.perform(post("/api/interno/procesos/deteccion-atrasos")
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "cualquier-clave"))
                .andExpect(status().isUnauthorized());
    }
}
