package cl.adndigital.mejorvejez.alertas.correos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Prueba {@link RedactorCorreosCuenta} como lógica pura (sin Spring): el
 * asunto y el cuerpo exactos de cada correo (HU-01).
 */
class RedactorCorreosCuentaTest {

    private final RedactorCorreosCuenta redactor = new RedactorCorreosCuenta();

    @Test
    void cuentaNuevaConNombreCorreoClaveYEnlace() {
        CorreoRedactado correo = redactor.redactarCuentaNueva("Ana Pérez", "ana@correo.cl", "Clave2026x",
                "http://localhost:4200/ingreso");

        assertThat(correo.asunto()).isEqualTo("Cuida+ · Tu cuenta está lista");
        assertThat(correo.cuerpo()).isEqualTo("Hola Ana Pérez,\n\n"
                + "Tu cuenta de Cuida+ ya está lista. Ingresa con tu correo (ana@correo.cl) y esta "
                + "contraseña temporal:\n\nClave2026x\n\nIngresa aquí: http://localhost:4200/ingreso\n\n"
                + "Te recomendamos cambiarla en \"Mi perfil\" apenas ingreses.\n\nCuida+");
    }

    @Test
    void recuperarClaveConNombreYEnlace() {
        CorreoRedactado correo = redactor.redactarRecuperarClave("Ana Pérez",
                "http://localhost:4200/restablecer-clave?codigo=abc123");

        assertThat(correo.asunto()).isEqualTo("Cuida+ · Restablece tu contraseña");
        assertThat(correo.cuerpo()).isEqualTo("Hola Ana Pérez,\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña de Cuida+. Usa este enlace:\n\n"
                + "http://localhost:4200/restablecer-clave?codigo=abc123\n\n"
                + "Este enlace vence en 1 hora.\n\nSi no lo pediste, ignora este correo.\n\nCuida+");
    }
}
