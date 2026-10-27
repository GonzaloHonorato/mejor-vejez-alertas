package cl.adndigital.mejorvejez.alertas.comun;

import java.time.ZoneId;

/** Zona horaria de negocio de Mejor Vejez: todas las fechas y horas se interpretan en America/Santiago. */
public final class ZonaNegocio {

    public static final ZoneId ZONA = ZoneId.of("America/Santiago");

    private ZonaNegocio() {
    }
}
