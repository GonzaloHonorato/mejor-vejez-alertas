package cl.adndigital.mejorvejez.alertas.correos;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.adndigital.mejorvejez.alertas.envio.EnviadorCorreo;

/**
 * Rutas internas de los correos de cuenta (HU-01): cuenta nueva y
 * recuperación de contraseña. Las consume el servicio core. Protegidas solo
 * por la cadena interna ({@code FiltroClaveServicio}).
 */
@RestController
@RequestMapping("/api/interno/correos")
public class ControladorCorreos {

    private final EnviadorCorreo enviadorCorreo;
    private final RedactorCorreosCuenta redactor;

    public ControladorCorreos(EnviadorCorreo enviadorCorreo, RedactorCorreosCuenta redactor) {
        this.enviadorCorreo = enviadorCorreo;
        this.redactor = redactor;
    }

    @PostMapping("/cuenta-nueva")
    public RespuestaCorreo cuentaNueva(@Valid @RequestBody SolicitudCorreoCuentaNueva solicitud) {
        CorreoRedactado correo = redactor.redactarCuentaNueva(solicitud.nombre(), solicitud.email(),
                solicitud.claveTemporal(), solicitud.urlIngreso());
        boolean enviado = enviadorCorreo.enviarTextoPlano(solicitud.email(), correo.asunto(), correo.cuerpo());
        return new RespuestaCorreo(enviado);
    }

    @PostMapping("/recuperar-clave")
    public RespuestaCorreo recuperarClave(@Valid @RequestBody SolicitudCorreoRecuperarClave solicitud) {
        CorreoRedactado correo = redactor.redactarRecuperarClave(solicitud.nombre(), solicitud.enlace());
        boolean enviado = enviadorCorreo.enviarTextoPlano(solicitud.email(), correo.asunto(), correo.cuerpo());
        return new RespuestaCorreo(enviado);
    }
}
