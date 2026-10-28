package cl.adndigital.mejorvejez.alertas.deteccion;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas internas que disparan las corridas de este microservicio (las
 * consumen el servicio de reportes y el servicio core, al reenviar la
 * corrida manual del administrador). Protegidas solo por la cadena interna
 * ({@code FiltroClaveServicio}).
 */
@RestController
@RequestMapping("/api/interno/procesos")
public class ControladorProcesos {

    private final ServicioDeteccionAtrasos servicioDeteccion;
    private final ServicioAlertasPreventivas servicioPreventivas;

    public ControladorProcesos(ServicioDeteccionAtrasos servicioDeteccion,
            ServicioAlertasPreventivas servicioPreventivas) {
        this.servicioDeteccion = servicioDeteccion;
        this.servicioPreventivas = servicioPreventivas;
    }

    @PostMapping("/deteccion-atrasos")
    public ResumenCorrida deteccionAtrasos() {
        return servicioDeteccion.ejecutar();
    }

    @PostMapping("/alertas-preventivas")
    public ResumenCorrida alertasPreventivas() {
        return servicioPreventivas.ejecutar();
    }
}
