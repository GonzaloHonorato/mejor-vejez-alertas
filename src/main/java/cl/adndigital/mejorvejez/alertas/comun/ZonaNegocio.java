package cl.adndigital.mejorvejez.alertas.comun;

import java.time.ZoneId;

/** Zona horaria de negocio de Mejor Vejez: todas las fechas y horas se interpretan en America/Santiago. */
public final class ZonaNegocio {

    /** Mismo valor que {@link #ZONA}, como texto: lo exige {@code @Scheduled(zone = ...)}, que solo acepta una constante de compilación. */
    public static final String NOMBRE = "America/Santiago";

    public static final ZoneId ZONA = ZoneId.of(NOMBRE);

    private ZonaNegocio() {
    }
}
