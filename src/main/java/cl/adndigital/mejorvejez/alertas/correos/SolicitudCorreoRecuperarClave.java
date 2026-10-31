package cl.adndigital.mejorvejez.alertas.correos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /api/interno/correos/recuperar-clave} (HU-01): lo envía el servicio core. */
public record SolicitudCorreoRecuperarClave(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no es válido.") String email,
        @NotBlank(message = "El enlace es obligatorio.") String enlace) {

    /** Nunca muestra {@code enlace} (trae el {@code oobCode}): solo si hay uno o no. */
    @Override
    public String toString() {
        return "SolicitudCorreoRecuperarClave[nombre=" + nombre + ", email=" + email
                + ", enlace=" + (enlace == null ? "null" : "***") + "]";
    }
}
