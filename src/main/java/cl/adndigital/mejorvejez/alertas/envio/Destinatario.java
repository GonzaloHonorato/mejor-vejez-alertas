package cl.adndigital.mejorvejez.alertas.envio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Un destinatario del reporte semanal: a quién se le envía el correo con el PDF adjunto. */
public record Destinatario(
        @NotNull(message = "El id de usuario del destinatario es obligatorio.") Long idUsuario,
        String nombre,
        @NotBlank(message = "El correo del destinatario es obligatorio.") String correo) {
}
