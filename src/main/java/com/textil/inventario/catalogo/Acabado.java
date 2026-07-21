package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Catalogo de acabados de la tela (ej. LISO, ACANALADO, LISTADO BLANCO,
 * LISTADO MELANGE 10%). Atributo independiente del Articulo, separado del
 * tejido base (TipoTela), el titulo y la composicion. Surge del analisis
 * de guias reales de FAST DYE: "ACANALADO" y "LIST X" nunca son un tipo
 * de tela en si mismos, siempre acompañan a un tejido base (ej.
 * "RIB 2X1 30/1 ALG ACANALADO", "RIB 2X1 24/1 ALG LIST BLANCO").
 */
@Getter
@Setter
@Entity
@Table(name = "acabados")
public class Acabado extends BaseEntity {

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres.")
    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;
}
