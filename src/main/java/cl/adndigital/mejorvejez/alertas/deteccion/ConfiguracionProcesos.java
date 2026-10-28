package cl.adndigital.mejorvejez.alertas.deteccion;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita {@code @Scheduled} para los procesos programados ({@link ProgramadorAlertas}). */
@Configuration
@EnableScheduling
public class ConfiguracionProcesos {
}
