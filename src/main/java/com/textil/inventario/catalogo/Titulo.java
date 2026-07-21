package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "titulos")
public class Titulo extends BaseEntity {

    @NotBlank(message = "El valor del título es obligatorio.")
    @Size(max = 20, message = "El valor no puede superar los 20 caracteres.")
    @Column(nullable = false, unique = true, length = 20)
    private String valor;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;
}
