package cl.adndigital.mejorvejez.alertas.correos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.alertas.comun.FiltroClaveServicio;

/**
 * Prueba {@code POST /api/interno/correos/cuenta-nueva} con el SMTP
 * configurado pero inalcanzable (HU-01): la respuesta sigue siendo 200 con
 * {@code enviado=false}, nunca un error, porque la cuenta ya se creó en el
 * core y esta ruta no debe hacerla fallar.
 */
@SpringBootTest(properties = { "mejorvejez.procesos.habilitados=false",
        "mejorvejez.interno.clave-servicio=clave-correcta",
        "mejorvejez.notificaciones.remitente=alertas@mejorvejez.local" })
@AutoConfigureMockMvc
class ControladorCorreosSmtpCaidoTest {

    private static final String RUTA_CUENTA_NUEVA = "/api/interno/correos/cuenta-nueva";

    @DynamicPropertySource
    static void registrarSmtpInalcanzable(DynamicPropertyRegistry registro) throws IOException {
        int puertoSinServidorEscuchando;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoSinServidorEscuchando = socket.getLocalPort();
        }
        registro.add("spring.mail.host", () -> "localhost");
        registro.add("spring.mail.port", () -> puertoSinServidorEscuchando);
        registro.add("spring.mail.properties.mail.smtp.connectiontimeout", () -> "500");
        registro.add("spring.mail.properties.mail.smtp.timeout", () -> "500");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void smtpCaidoDevuelveEnviadoFalse() throws Exception {
        String cuerpo = "{\"nombre\":\"Ana Pérez\",\"email\":\"ana.smtpcaido@correo.cl\","
                + "\"claveTemporal\":\"Clave2026x\",\"urlIngreso\":\"http://localhost:4200/ingreso\"}";

        mockMvc.perform(post(RUTA_CUENTA_NUEVA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enviado").value(false));
    }
}
