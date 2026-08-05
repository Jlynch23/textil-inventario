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

    /**
     * Descripcion legible del articulo, UNICA para toda la aplicacion.
     *
     * Antes cada pantalla, cada reporte y cada alerta armaba este texto por su
     * cuenta y ya habian divergido: el Excel del kardex y el reporte de stock
     * bajo omitian composicion y acabado (por eso agrupaban como uno solo dos
     * articulos distintos, ocultando stock bajo real), y la alerta omitia el
     * acabado. Las cuatro asociaciones son EAGER, asi que es seguro llamarlo
     * tambien fuera de la sesion de Hibernate (open-in-view esta apagado).
     *
     * El acabado LISO no se muestra: es el valor por defecto y solo agrega ruido.
     */
    public String getDescripcion() {
        String base = tipoTela.getNombre() + " " + titulo.getValor() + " / " + composicion.getNombre();
        return "LISO".equalsIgnoreCase(acabado.getNombre()) ? base : base + " · " + acabado.getNombre();
    }
}
