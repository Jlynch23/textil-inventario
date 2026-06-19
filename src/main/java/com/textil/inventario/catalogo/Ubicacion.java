package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ubicaciones")
public class Ubicacion extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoUbicacion tipo;

    @Column(name = "es_principal", nullable = false)
    private Boolean esPrincipal = false;

    @Column(nullable = false)
    private Boolean activo = true;

    public enum TipoUbicacion {
        ALMACEN, TIENDA
    }
}
