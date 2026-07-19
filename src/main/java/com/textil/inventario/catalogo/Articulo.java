package com.textil.inventario.catalogo;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "articulos",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"tipo_tela_id", "titulo_id", "composicion_id", "acabado_id"}
    )
)
public class Articulo extends BaseEntity {

    @Column(name = "codigo_interno", nullable = false, unique = true, length = 50)
    private String codigoInterno;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_tela_id", nullable = false)
    private TipoTela tipoTela;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "titulo_id", nullable = false)
    private Titulo titulo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "composicion_id", nullable = false)
    private Composicion composicion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "acabado_id", nullable = false)
    private Acabado acabado;

    @Column(nullable = false)
    private Boolean activo = true;
}
