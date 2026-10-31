package cl.adndigital.mejorvejez.alertas.correos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /api/interno/correos/cuenta-nueva} (HU-01): lo envía
 * el servicio core con la cuenta recién creada. {@code urlIngreso} llega ya
 * armada desde el core.
 */
public record SolicitudCorreoCuentaNueva(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no es válido.") String email,
        @NotBlank(message = "La contraseña temporal es obligatoria.") String claveTemporal,
        @NotBlank(message = "El enlace de ingreso es obligatorio.") String urlIngreso) {

    /** Nunca muestra {@code claveTemporal}. */
    @Override
    public String toString() {
        return "SolicitudCorreoCuentaNueva[nombre=" + nombre + ", email=" + email
                + ", claveTemporal=" + (claveTemporal == null ? "null" : "***") + ", urlIngreso=" + urlIngreso + "]";
    }
}
