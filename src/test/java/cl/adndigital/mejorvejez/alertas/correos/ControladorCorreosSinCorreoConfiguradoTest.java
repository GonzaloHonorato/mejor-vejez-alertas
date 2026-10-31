package cl.adndigital.mejorvejez.alertas.correos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Prueba {@code POST /api/interno/correos/cuenta-nueva} sin {@code MAIL_HOST}
 * configurado (HU-01): la cuenta ya se creó en el core, así que esta ruta
 * igual responde 200 con {@code enviado=false} en vez de fallar.
 */
@SpringBootTest(properties = { "mejorvejez.procesos.habilitados=false",
        "mejorvejez.interno.clave-servicio=clave-correcta", "spring.mail.host=", "mejorvejez.notificaciones.remitente=" })
@AutoConfigureMockMvc
class ControladorCorreosSinCorreoConfiguradoTest {

    private static final String RUTA_CUENTA_NUEVA = "/api/interno/correos/cuenta-nueva";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void correoNoConfiguradoDevuelveEnviadoFalse() throws Exception {
        String cuerpo = "{\"nombre\":\"Ana Pérez\",\"email\":\"ana.sinconfigurar@correo.cl\","
                + "\"claveTemporal\":\"Clave2026x\",\"urlIngreso\":\"http://localhost:4200/ingreso\"}";

        mockMvc.perform(post(RUTA_CUENTA_NUEVA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(false));
    }
}
