package com.textil.inventario.transferencias;

import com.textil.inventario.catalogo.Ubicacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "transferencia_distribucion",
    uniqueConstraints = @UniqueConstraint(columnNames = {"transferencia_detalle_id", "ubicacion_id"}))
public class TransferenciaDistribucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transferencia_detalle_id", nullable = false)
    private TransferenciaDetalle transferenciaDetalle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Column(nullable = false)
    private Integer rollos;
}
