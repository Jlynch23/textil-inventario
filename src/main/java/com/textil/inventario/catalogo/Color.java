package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "colores")
public class Color extends BaseEntity {

    @Column(name = "nombre_oficial", nullable = false, unique = true, length = 100)
    private String nombreOficial;

    @Column(name = "codigo_fast_dye", length = 20)
    private String codigoFastDye;

    @Column(length = 50)
    private String familia;

    @Column(nullable = false)
    private Boolean activo = true;
}
