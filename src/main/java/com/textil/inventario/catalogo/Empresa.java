package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "empresas")
public class Empresa extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 50)
    private String carpeta;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(nullable = false)
    private Boolean activo = true;
}
