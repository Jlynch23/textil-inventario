package com.textil.inventario.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utilidades compartidas por los endpoints que responden JSON (@ResponseBody).
 *
 * El mismo par de catch estaba escrito a mano en 13 endpoints: uno traducia los
 * errores de dominio a 400 y otro logueaba y devolvia el 500 generico, con el
 * texto del mensaje repetido literal en cada copia. Ademas de ser ruido, era
 * fragil: el GlobalExceptionHandler del proyecto responde la VISTA html de
 * error, asi que a un endpoint JSON al que se le olvidara el catch le llegaba
 * HTML a un fetch que esperaba JSON (el front lo veia como "error de conexion"
 * sin mas detalle).
 *
 * No se resolvio con un @RestControllerAdvice global a proposito: varios
 * controladores mezclan pantallas HTML y endpoints JSON, y un advice global les
 * cambiaria el manejo de errores tambien a las pantallas.
 */
public final class RespuestaJson {

    private static final Logger log = LoggerFactory.getLogger(RespuestaJson.class);

    /** Lo unico que se le muestra al usuario ante un fallo inesperado. */
    public static final String ERROR_GENERICO =
            "Ocurrió un error interno. Intenta de nuevo o contacta al administrador.";

    private RespuestaJson() {}

    /** Cuerpo de una respuesta de error, en el formato que espera el front. */
    public static Map<String, String> error(String mensaje) {
        return Map.of("error", mensaje);
    }

    /** Accion que produce el cuerpo de la respuesta y puede fallar. */
    @FunctionalInterface
    public interface Accion {
        Object ejecutar() throws Exception;
    }

    /**
     * Ejecuta la accion y arma la respuesta:
     *  - ok            -> 200 con lo que devolvio la accion
     *  - error de dominio (IllegalArgument/IllegalState) -> 400 con SU mensaje,
     *    que esta redactado para el usuario (ej. "la guia ya existe")
     *  - choque de UNIQUE -> 400 con el mensaje que pase el llamador
     *  - cualquier otra  -> 500 generico, con el detalle solo en el log
     *
     * @param contexto      nombre del endpoint, para poder ubicarlo en el log
     * @param mensajeChoque que decirle al usuario si la BD rechaza por duplicado
     */
    public static ResponseEntity<?> responder(String contexto, String mensajeChoque, Accion accion) {
        try {
            return ResponseEntity.ok(accion.ejecutar());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(error(mensajeChoque));
        } catch (Exception e) {
            log.error("Error en {}: {}", contexto, e.getMessage(), e);
            return ResponseEntity.status(500).body(error(ERROR_GENERICO));
        }
    }

    /** Variante para endpoints que no pueden chocar contra un UNIQUE. */
    public static ResponseEntity<?> responder(String contexto, Accion accion) {
        return responder(contexto, ERROR_GENERICO, accion);
    }

    /**
     * Primer mensaje de validacion del form, sin repetidos. Estaba copiado
     * identico en los 8 controladores de catalogo.
     */
    public static String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }
}
