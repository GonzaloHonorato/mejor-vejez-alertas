package cl.adndigital.mejorvejez.alertas.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.adndigital.mejorvejez.alertas.envio.EnvioEnCursoException;
import cl.adndigital.mejorvejez.alertas.envio.SolicitudEnvioInvalidaException;

/**
 * Traduce las excepciones a respuestas {@link ProblemDetail} uniformes, en
 * español, sin filtrar detalles internos (mensajes de la causa, nombres de
 * clases, stack traces).
 *
 * <p>Las excepciones estándar de Spring MVC que ya traen su estado HTTP
 * ({@link ErrorResponse}) conservan ese estado 4xx; las de validación de
 * campos ({@link MethodArgumentNotValidException}) devuelven en {@code
 * detail} el mensaje curado del primer campo inválido, y el resto, un texto
 * genérico en español con el título según el estado HTTP.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);
    private static final String DETALLE_VALIDACION_GENERICO = "Los datos enviados no son válidos.";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException excepcion) {
        LOG.debug("Cuerpo de solicitud ilegible: {}", excepcion.getMessage());
        return construirProblema(HttpStatus.BAD_REQUEST, "Datos inválidos", "El cuerpo de la solicitud no es válido.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException excepcion) {
        String detalle = excepcion.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(DETALLE_VALIDACION_GENERICO);
        LOG.debug("Solicitud inválida: {}", detalle);
        return construirProblema(HttpStatus.BAD_REQUEST, "Datos inválidos", detalle);
    }

    @ExceptionHandler(SolicitudEnvioInvalidaException.class)
    public ProblemDetail manejarSolicitudEnvioInvalida(SolicitudEnvioInvalidaException excepcion) {
        LOG.debug("Solicitud de envío de reporte inválida: {}", excepcion.getMessage());
        return construirProblema(HttpStatus.BAD_REQUEST, "Datos inválidos", excepcion.getMessage());
    }

    @ExceptionHandler(EnvioEnCursoException.class)
    public ProblemDetail manejarEnvioEnCurso(EnvioEnCursoException excepcion) {
        LOG.debug("Envío de reporte ya en curso con esa clave");
        return construirProblema(HttpStatus.CONFLICT, "Envío en curso",
                "Ya hay un envío de reporte en curso con esa clave.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarErrorInesperado(Exception excepcion) {
        if (excepcion instanceof ErrorResponse respuestaSpring
                && respuestaSpring.getStatusCode().is4xxClientError()) {
            return manejarErrorDeCliente(respuestaSpring);
        }
        LOG.error("Error no controlado", excepcion);
        return ResponseEntity.internalServerError()
                .body(construirProblema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado."));
    }

    private ResponseEntity<ProblemDetail> manejarErrorDeCliente(ErrorResponse respuestaSpring) {
        HttpStatusCode codigo = respuestaSpring.getStatusCode();
        LOG.debug("Error de cliente {}: {}", codigo.value(), respuestaSpring.getBody().getDetail());
        HttpStatus estado = HttpStatus.resolve(codigo.value());
        HttpStatus estadoResuelto = estado != null ? estado : HttpStatus.BAD_REQUEST;
        ProblemDetail problema = construirProblema(estadoResuelto, tituloSegunEstado(estadoResuelto),
                "La solicitud no es válida.");
        return ResponseEntity.status(codigo).headers(respuestaSpring.getHeaders()).body(problema);
    }

    /** Título del {@code ProblemDetail} genérico según el estado HTTP, en vez de "Datos inválidos" para todo. */
    private String tituloSegunEstado(HttpStatus estado) {
        return switch (estado) {
            case NOT_FOUND -> "Recurso no encontrado";
            case METHOD_NOT_ALLOWED -> "Método no permitido";
            case BAD_REQUEST -> "Datos inválidos";
            default -> "Solicitud inválida";
        };
    }

    private ProblemDetail construirProblema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
