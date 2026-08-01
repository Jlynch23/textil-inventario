package com.textil.inventario.config;
import com.textil.inventario.auditoria.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
/**
 * Captura cualquier excepción no controlada que llegue desde un @Controller,
 * la deja en el log de Spring (como antes) y ADEMÁS la registra en log_eventos
 * (accion = ERROR_SISTEMA) para que el SUPERADMIN pueda verla en
 * Reportes > Errores del Sistema, sin depender de tener la terminal abierta.
 * La pantalla que ve el usuario final (error.html) no cambia.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {
    private static final int LARGO_MAXIMO_TRAZA = 4000;
    private final AuditLogService auditLogService;
    // Recursos estáticos faltantes (favicon.ico, etc.) NO son errores del sistema:
    // son 404 normales que el navegador pide solo. Se excluyen para no ensuciar
    // el log de errores ni mostrar la pantalla de error por algo inofensivo.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> manejarRecursoNoEncontrado(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // R-B5 (red-team): una denegacion de @PreAuthorize lanza AuthorizationDenied
    // Exception (subclase de AccessDeniedException). Sin este handler, caia en el
    // catch de Exception -> HTTP 500 + un falso ERROR_SISTEMA que ensuciaba el
    // Reporte de Errores del SUPERADMIN con denegaciones de permiso legitimas.
    // Ahora se responde 403 limpio y NO se registra como error del sistema.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String manejarAccesoDenegado(org.springframework.security.access.AccessDeniedException ex,
                                        HttpServletRequest request) {
        log.warn("Acceso denegado en {}: {}", request.getRequestURI(), ex.getMessage());
        return "error";
    }
    // Auditoria: errores de CLIENTE (request malformado) son 4xx, NO errores del
    // sistema. Sin este handler caian en el catch de Exception -> HTTP 500 + un
    // falso ERROR_SISTEMA que ensuciaba el Reporte de Errores del SUPERADMIN (ej.
    // un id no numerico en la URL, un parametro requerido faltante, un body ilegible).
    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.validation.BindException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarPeticionInvalida(Exception ex, HttpServletRequest request) {
        log.warn("Petición inválida en {}: {}", request.getRequestURI(), ex.getMessage());
        return "error";
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String manejarMetodoNoSoportado(org.springframework.web.HttpRequestMethodNotSupportedException ex,
                                           HttpServletRequest request) {
        log.warn("Método no soportado en {}: {}", request.getRequestURI(), ex.getMessage());
        return "error";
    }

    // SEC-01 (auditoría 17-jul-2026): sin @ResponseStatus, Spring MVC responde
    // HTTP 200 en este handler aunque haya ocurrido una excepción no controlada,
    // lo que rompe cualquier monitoreo/health-check basado en código HTTP.
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String manejarError(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String traza = sw.toString();
        if (traza.length() > LARGO_MAXIMO_TRAZA) {
            traza = traza.substring(0, LARGO_MAXIMO_TRAZA) + "\n... (traza truncada)";
        }
        String entidad = request.getRequestURI();
        if (entidad != null && entidad.length() > 50) {
            entidad = entidad.substring(0, 50);
        }
        String descripcion = ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n\n" + traza;
        auditLogService.registrar("ERROR_SISTEMA", entidad, null, descripcion);
        return "error";
    }
}
