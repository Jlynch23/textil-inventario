package com.textil.inventario.inventario;
import com.textil.inventario.catalogo.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Getter
@Setter
@Entity
@Table(name = "stock_actual",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"articulo_id", "ubicacion_id", "color_id"}
    )
)
public class StockActual {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;
    @Column(nullable = false)
    private Integer rollos = 0;
    @Column(name = "peso_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal pesoKg = BigDecimal.ZERO;
    @Version
    private Integer version = 0;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @PreUpdate
    @PrePersist
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
