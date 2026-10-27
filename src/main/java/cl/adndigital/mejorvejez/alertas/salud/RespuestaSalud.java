package cl.adndigital.mejorvejez.alertas.salud;

import java.time.OffsetDateTime;

/** Estado del microservicio, para el healthcheck del contenedor. */
public record RespuestaSalud(String estado, String servicio, String version, OffsetDateTime fechaHora) {
}
