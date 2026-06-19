package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.common.BaseEntity;
import com.textil.inventario.seguridad.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "recepciones")
public class Recepcion extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "numero_guia", nullable = false, length = 50)
    private String numeroGuia;

    @Column(name = "fecha_guia", nullable = false)
    private LocalDate fechaGuia;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDate fechaRecepcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRecepcion estado = EstadoRecepcion.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RecepcionDetalle> detalles = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum EstadoRecepcion {
        PENDIENTE, CONFIRMADA, CON_DIFERENCIAS
    }
}
