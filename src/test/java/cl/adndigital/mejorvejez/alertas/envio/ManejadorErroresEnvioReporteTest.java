package cl.adndigital.mejorvejez.alertas.envio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Un error interno inesperado (no una validación de la solicitud) nunca
 * debe caer en el 400 curado del PDF/la clave de envío: tiene que darse el
 * 500 genérico, sin exponer el mensaje de la excepción.
 */
@SpringBootTest(properties = { "mejorvejez.procesos.habilitados=false",
        "mejorvejez.interno.clave-servicio=clave-correcta" })
@AutoConfigureMockMvc
class ManejadorErroresEnvioReporteTest {

    private static final String RUTA = "/api/interno/envios-reporte";
    private static final String CUERPO_VALIDO = "{\"claveEnvio\":\"x\",\"asunto\":\"a\",\"cuerpo\":\"c\","
            + "\"nombreArchivo\":\"r.pdf\",\"pdfBase64\":\"Y29udGVuaWRv\",\"destinatarios\":["
            + "{\"idUsuario\":1,\"correo\":\"a@a.cl\"}]}";

    @MockitoBean
    private ServicioEnviosReporte servicioEnviosReporte;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void argumentoInvalidoGenericoDaQuinientosSinExponerElMensaje() throws Exception {
        when(servicioEnviosReporte.enviar(any())).thenThrow(new IllegalArgumentException("detalle-interno-sensible"));

        mockMvc.perform(post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado."));
    }

    @Test
    void rutaInexistenteDaCuatroCientosCuatroConTituloPropio() throws Exception {
        mockMvc.perform(get("/api/interno/esto-no-existe")
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    void metodoNoPermitidoDaCuatroCientosCincoConTituloPropio() throws Exception {
        mockMvc.perform(get(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método no permitido"));
    }
}
