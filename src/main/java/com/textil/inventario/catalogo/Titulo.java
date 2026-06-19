package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "titulos")
public class Titulo extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String valor;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;
}
