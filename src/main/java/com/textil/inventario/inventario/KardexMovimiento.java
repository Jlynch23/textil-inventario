package com.textil.inventario.inventario;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.seguridad.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "kardex_movimientos")
public class KardexMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false, length = 20)
    private TipoMovimiento tipoMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_origen_id")
    private Ubicacion ubicacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_destino_id")
    private Ubicacion ubicacionDestino;

    @Column(nullable = false)
    private Integer rollos;

    @Column(name = "peso_kg", precision = 10, scale = 2)
    private BigDecimal pesoKg;

    @Column(name = "recepcion_detalle_id")
    private Long recepcionDetalleId;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Auditoria KDX-01: el kardex es append-only. Los movimientos NUNCA se
    // actualizan (solo se insertan); corregir un error contable se hace con un
    // contramovimiento, no editando la historia. Este guard hace que cualquier
    // UPDATE accidental (de codigo futuro que herede el repo JpaRepository
    // completo) reviente en vez de mutar en silencio un asiento historico.
    // NO se bloquea @PreRemove: el borrado se usa en el seed/cleanup de los tests
    // de integracion y en migraciones; la garantia fuerte contra DELETE va a
    // nivel de usuario SQL (GRANT), fuera del alcance de la app.
    @PreUpdate
    protected void impedirActualizacion() {
        throw new UnsupportedOperationException(
                "El kardex es append-only: un movimiento no se puede modificar (usar un contramovimiento).");
    }

    public enum TipoMovimiento {
        INGRESO, TRANSFERENCIA_OUT, TRANSFERENCIA_IN, AJUSTE
    }
}
