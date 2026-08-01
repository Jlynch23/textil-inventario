package com.textil.inventario.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Endurece el binding de formularios contra mass assignment (auditoría red-team
 * R-B4). Varios controllers (sobre todo CatalogoController) hacen
 * {@code @ModelAttribute <EntidadJPA>} y luego {@code repository.save(...)}: el
 * binder de Spring acepta CUALQUIER propiedad presente en el body, no solo las
 * del formulario. Sin este filtro, un POST manipulado podía setear campos que el
 * formulario no muestra.
 *
 * Se prohíben globalmente los campos que NUNCA deben venir de un formulario:
 * - {@code version}: rompería el lock optimista (R-C1) al fijar la versión a mano.
 * - {@code createdAt}/{@code updatedAt}: metadatos de auditoría de BaseEntity.
 * - {@code activo}: la baja/reactivación tiene endpoints dedicados con su propia
 *   lógica; todas las entidades lo inicializan en true. Sin esto, el formulario
 *   de alta podía reactivar/inactivar una fila por la puerta de atrás.
 *
 * NO se prohíbe {@code id} (la edición del catálogo reusa el mismo endpoint que
 * el alta y necesita el id oculto para hacer UPDATE) ni {@code esPrincipal} (el
 * formulario de ubicaciones lo bindea legítimamente vía checkbox).
 */
@ControllerAdvice
public class GlobalBinderAdvice {

    private static final String[] CAMPOS_PROHIBIDOS = {
            "version", "createdAt", "updatedAt", "activo"
    };

    @InitBinder
    public void restringirCamposSensibles(WebDataBinder binder) {
        binder.setDisallowedFields(CAMPOS_PROHIBIDOS);
    }
}
