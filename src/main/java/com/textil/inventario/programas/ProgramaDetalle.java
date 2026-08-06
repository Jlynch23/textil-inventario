package com.textil.inventario.programas;

import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.catalogo.Color;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "programa_detalles")
public class ProgramaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "programa_id", nullable = false)
    private Programa programa;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada;

    @Column(name = "cantidad_recibida", nullable = false)
    private Integer cantidadRecibida = 0;

    // Auditoria INV-03: lock optimista sobre el avance del programa. El @Version
    // del padre Recepcion (V38) protege el estado de CADA recepcion, pero NO este
    // contador compartido: dos recepciones DISTINTAS del mismo programa que se
    // confirman en paralelo leen ambas cantidad_recibida=X y escriben X+propio ->
    // last-write-wins, se pierde un incremento (stock/kardex quedan bien, pero el
    // avance del programa queda corto). Con @Version, Hibernate agrega
    // `AND version = ?` al UPDATE: la segunda transaccion afecta 0 filas ->
    // OptimisticLockException -> rollback. Ver migracion V43.
    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Integer getCantidadPendiente() {
        return cantidadSolicitada - cantidadRecibida;
    }

    public boolean isCompleto() {
        return cantidadRecibida >= cantidadSolicitada;
    }
}
