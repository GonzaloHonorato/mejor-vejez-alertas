package cl.adndigital.mejorvejez.alertas.deteccion;

import java.time.OffsetDateTime;

/** Envía (o reintenta) las notificaciones de las alertas abiertas desde {@code desde}; devuelve cuántas envió. */
public interface NotificadorPendientes {

    int notificarPendientes(OffsetDateTime desde);
}
