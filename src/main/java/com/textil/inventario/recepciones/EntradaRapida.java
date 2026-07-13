package com.textil.inventario.recepciones;

import com.textil.inventario.seguridad.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "entradas_rapidas")
public class EntradaRapida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "total_rollos", nullable = false)
    private Integer totalRollos;

    @Column(name = "foto_ruta", nullable = false, length = 500)
    private String fotoRuta;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "observaciones_admin", columnDefinition = "TEXT")
    private String observacionesAdmin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recepcion_id")
    private Recepcion recepcion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
