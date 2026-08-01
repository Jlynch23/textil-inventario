package com.textil.inventario.catalogo;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO del formulario de Ubicacion (#7, auditoría): el binder solo toca estos
 * campos, nunca activo/version/timestamps. Clase JavaBean por compatibilidad con
 * th:field.
 */
@Getter
@Setter
public class UbicacionForm {

    private Long id;

    @NotBlank(message = "El código es obligatorio.")
    @Size(max = 20, message = "El código no puede superar los 20 caracteres.")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String nombre;

    @NotNull(message = "El tipo es obligatorio.")
    private Ubicacion.TipoUbicacion tipo;

    private Boolean esPrincipal = false;

}
