package com.textil.inventario.seguridad;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Rol extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;

    // Orden de PODER (1 = mayor). Independiente del id: define como se muestran
    // los roles en la UI, de mayor a menor autoridad. Ver V36__roles_jerarquia.
    @Column(nullable = false)
    private Integer jerarquia = 0;
}
