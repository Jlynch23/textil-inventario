package com.textil.inventario.catalogo;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO del formulario de Composicion (#7, auditoría): el binder solo toca estos
 * campos, nunca activo/version/timestamps. Clase JavaBean por compatibilidad con
 * th:field.
 */
@Getter
@Setter
public class ComposicionForm {

    private Long id;

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres.")
    private String nombre;

    @Size(max = 200, message = "La descripción no puede superar los 200 caracteres.")
    private String descripcion;

}
