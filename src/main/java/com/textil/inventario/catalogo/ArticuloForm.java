package com.textil.inventario.catalogo;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO del formulario de Artículo (#7, auditoría). El artículo se define por sus
 * cuatro piezas base (tejido/título/composición/acabado) elegidas por id; el
 * código interno se autogenera y activo/version nunca los toca el binding.
 */
@Getter
@Setter
public class ArticuloForm {

    private Long id;

    @NotNull(message = "El tipo de tela es obligatorio.")
    private Long tipoTelaId;

    @NotNull(message = "El título es obligatorio.")
    private Long tituloId;

    @NotNull(message = "La composición es obligatoria.")
    private Long composicionId;

    @NotNull(message = "El acabado es obligatorio.")
    private Long acabadoId;
}
