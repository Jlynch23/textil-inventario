package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Catalogo de composiciones/variantes de fibra (ej. ALGODON, MELANGE 10%,
 * MELANGE 3%, MELANGE 1%). Es un atributo independiente del Articulo,
 * separado de TipoTela (tejido base) y Color (que ahora vive a nivel de
 * cada movimiento -- StockActual, ProgramaDetalle, RecepcionDetalle,
 * TransferenciaDetalle, KardexMovimiento -- no como parte del Articulo).
 */
@Getter
@Setter
@Entity
@Table(name = "composiciones")
public class Composicion extends BaseEntity {

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres.")
    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;
}
