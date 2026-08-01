package com.textil.inventario.catalogo;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO del formulario de Titulo (#7, auditoría): el binder solo toca estos
 * campos, nunca activo/version/timestamps. Clase JavaBean por compatibilidad con
 * th:field.
 */
@Getter
@Setter
public class TituloForm {

    private Long id;

    @NotBlank(message = "El valor del título es obligatorio.")
    @Size(max = 20, message = "El valor no puede superar los 20 caracteres.")
    private String valor;

    @Size(max = 200, message = "La descripción no puede superar los 200 caracteres.")
    private String descripcion;

}
