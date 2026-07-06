package com.textil.inventario.transferencias;

import com.textil.inventario.catalogo.Articulo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "transferencia_detalle")
public class TransferenciaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transferencia_id", nullable = false)
    private Transferencia transferencia;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;

    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada;

    @Column(name = "cantidad_confirmada_salida")
    private Integer cantidadConfirmadaSalida;

    @Column(name = "cantidad_confirmada_llegada")
    private Integer cantidadConfirmadaLlegada;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    public Integer getDiferenciaSalida() {
        if (cantidadConfirmadaSalida == null) return null;
        return cantidadConfirmadaSalida - cantidadSolicitada;
    }

    public Integer getDiferenciaLlegada() {
        if (cantidadConfirmadaLlegada == null || cantidadConfirmadaSalida == null) return null;
        return cantidadConfirmadaLlegada - cantidadConfirmadaSalida;
    }
}
