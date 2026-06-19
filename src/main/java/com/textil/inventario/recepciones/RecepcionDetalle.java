package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.Articulo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "recepcion_detalles")
public class RecepcionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recepcion_id", nullable = false)
    private Recepcion recepcion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;

    @Column(name = "programa_tenido", length = 20)
    private String programaTenido;

    @Column(name = "rollos_guia", nullable = false)
    private Integer rollosGuia;

    @Column(name = "rollos_recibidos")
    private Integer rollosRecibidos;

    @Column(name = "peso_bruto_kg", precision = 10, scale = 2)
    private java.math.BigDecimal pesoBrutoKg;

    @Column(columnDefinition = "TEXT")
    private String observacion;

    public Integer getDiferenciaRollos() {
        if (rollosRecibidos == null) return null;
        return rollosRecibidos - rollosGuia;
    }
}
