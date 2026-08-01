package com.textil.inventario.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO del formulario de Color (#7, auditoría). Migrar de {@code @ModelAttribute
 * Color} (entidad JPA) a este form es la solución de fondo al mass assignment:
 * el binder solo puede tocar estos campos, nunca {@code activo}, {@code version}
 * ni los timestamps (que sí quedan expuestos si se bindea la entidad directo,
 * aunque GlobalBinderAdvice los bloquee por lista negra).
 *
 * Clase JavaBean (no record) a propósito: {@code th:field} de Thymeleaf lee por
 * getter, así el template no cambia.
 */
@Getter
@Setter
public class ColorForm {

    private Long id;

    @NotBlank(message = "El nombre oficial es obligatorio.")
    @Size(max = 100, message = "El nombre oficial no puede superar los 100 caracteres.")
    private String nombreOficial;

    @Size(max = 20, message = "El código FAST DYE no puede superar los 20 caracteres.")
    private String codigoFastDye;

    @Size(max = 100, message = "El apodo no puede superar los 100 caracteres.")
    private String apodo;
}
