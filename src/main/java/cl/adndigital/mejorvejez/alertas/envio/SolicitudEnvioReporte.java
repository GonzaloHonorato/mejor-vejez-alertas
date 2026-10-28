package cl.adndigital.mejorvejez.alertas.envio;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code POST /api/interno/envios-reporte}: el correo del reporte
 * semanal, con su PDF en base64. {@code claveEnvio} es la clave de
 * idempotencia: dos solicitudes con la misma clave mientras la primera
 * sigue en curso dan 409.
 */
public record SolicitudEnvioReporte(
        @NotBlank(message = "La clave de envío es obligatoria.") String claveEnvio,
        @NotBlank(message = "El asunto es obligatorio.") String asunto,
        @NotBlank(message = "El cuerpo es obligatorio.") String cuerpo,
        @NotBlank(message = "El nombre del archivo es obligatorio.")
        @Pattern(regexp = ".*\\.pdf$",
                message = "El nombre del archivo debe terminar en '.pdf'.") String nombreArchivo,
        @NotBlank(message = "El PDF es obligatorio.") String pdfBase64,
        @NotEmpty(message = "Debe indicarse al menos un destinatario.")
        List<@NotNull(message = "Un destinatario no puede ser nulo.") @Valid Destinatario> destinatarios) {
}
