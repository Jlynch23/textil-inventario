package com.textil.inventario.catalogo;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO del formulario de Empresa (#7, auditoría): el binder solo toca estos
 * campos, nunca activo/version/timestamps. Clase JavaBean por compatibilidad con
 * th:field.
 */
@Getter
@Setter
public class EmpresaForm {

    private Long id;

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String nombre;

    @NotBlank(message = "El RUC es obligatorio.")
    @Pattern(regexp = "\\d{11}", message = "El RUC debe tener exactamente 11 dígitos numéricos.")
    private String ruc;

}
