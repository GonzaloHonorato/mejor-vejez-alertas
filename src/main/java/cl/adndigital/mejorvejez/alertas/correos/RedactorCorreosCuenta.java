package cl.adndigital.mejorvejez.alertas.correos;

import org.springframework.stereotype.Component;

/**
 * Redacta los correos de cuenta nueva y de recuperación de contraseña
 * (HU-01): texto plano en español. El enlace de ingreso y el de
 * restablecimiento ya vienen armados en la solicitud (los arma el servicio
 * core), así que este redactor solo compone el asunto y el cuerpo.
 */
@Component
public class RedactorCorreosCuenta {

    static final String ASUNTO_CUENTA_NUEVA = "Mejor Vejez · Tu cuenta está lista";
    static final String ASUNTO_RECUPERAR_CLAVE = "Mejor Vejez · Restablece tu contraseña";

    /** Correo de cuenta nueva: saludo, correo de ingreso, contraseña temporal, enlace y recomendación de cambio. */
    CorreoRedactado redactarCuentaNueva(String nombre, String email, String claveTemporal, String urlIngreso) {
        String cuerpo = "Hola " + nombre + ",\n\n"
                + "Tu cuenta de Mejor Vejez ya está lista. Ingresa con tu correo (" + email + ") y esta "
                + "contraseña temporal:\n\n" + claveTemporal + "\n\n"
                + "Ingresa aquí: " + urlIngreso + "\n\n"
                + "Te recomendamos cambiarla en \"Mi perfil\" apenas ingreses.\n\nMejor Vejez";
        return new CorreoRedactado(ASUNTO_CUENTA_NUEVA, cuerpo);
    }

    /** Correo de recuperación de contraseña: saludo, enlace, vigencia y aviso si no se pidió. */
    CorreoRedactado redactarRecuperarClave(String nombre, String enlace) {
        String cuerpo = "Hola " + nombre + ",\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña de Mejor Vejez. Usa este enlace:\n\n"
                + enlace + "\n\n"
                + "Este enlace vence en 1 hora.\n\nSi no lo pediste, ignora este correo.\n\nMejor Vejez";
        return new CorreoRedactado(ASUNTO_RECUPERAR_CLAVE, cuerpo);
    }
}
