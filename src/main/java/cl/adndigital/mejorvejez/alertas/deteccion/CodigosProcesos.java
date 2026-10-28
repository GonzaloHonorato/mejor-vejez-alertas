package cl.adndigital.mejorvejez.alertas.deteccion;

/** Valores exactos (tipos de alerta, tipos de ejecución y estados de tarea) que usan los procesos de este módulo. */
public final class CodigosProcesos {

    // Tipos de alerta (los mismos que acepta POST /api/interno/alertas).
    public static final String TIPO_ATRASO_TAREA = "ATRASO_TAREA";
    public static final String TIPO_CITA_PROXIMA = "CITA_PROXIMA";
    public static final String TIPO_REPOSICION_MEDICAMENTO = "REPOSICION_MEDICAMENTO";

    // Tipos de ejecución (EJECUCION_PROCESO.TIPO_EJECUCION en el servicio core).
    public static final String TIPO_EJECUCION_DETECCION_ATRASOS = "DETECCION_ATRASOS";
    public static final String TIPO_EJECUCION_ALERTAS_PREVENTIVAS = "ALERTAS_PREVENTIVAS";

    // Estados de tarea que, pasado el límite de tolerancia, generan una alerta de atraso.
    public static final String ESTADO_TAREA_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_TAREA_EN_CURSO = "EN_CURSO";

    // Estados finales de una corrida (EJECUCION_PROCESO.ESTADO en el servicio core).
    public static final String ESTADO_EJECUCION_EXITOSA = "EXITOSA";
    public static final String ESTADO_EJECUCION_PARCIAL = "PARCIAL";
    public static final String ESTADO_EJECUCION_FALLIDA = "FALLIDA";

    private CodigosProcesos() {
    }
}
