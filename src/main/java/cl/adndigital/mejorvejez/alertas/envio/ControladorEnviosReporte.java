package cl.adndigital.mejorvejez.alertas.envio;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ruta interna del correo del reporte semanal ({@code POST
 * /api/interno/envios-reporte}): la consume el microservicio de reportes.
 * Protegida solo por la cadena interna ({@code FiltroClaveServicio}).
 */
@RestController
@RequestMapping("/api/interno/envios-reporte")
public class ControladorEnviosReporte {

    private final ServicioEnviosReporte servicio;

    public ControladorEnviosReporte(ServicioEnviosReporte servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public RespuestaEnvioReporte enviar(@Valid @RequestBody SolicitudEnvioReporte solicitud) {
        return servicio.enviar(solicitud);
    }
}
