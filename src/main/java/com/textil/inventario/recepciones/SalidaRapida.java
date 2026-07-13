package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.Color;
import com.textil.inventario.catalogo.TipoTela;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.transferencias.Transferencia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "salidas_rapidas")
public class SalidaRapida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer cantidad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_tela_id")
    private TipoTela tipoTela;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "color_id")
    private Color color;

    @Column(name = "foto_ruta", nullable = false, length = 500)
    private String fotoRuta;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "observaciones_admin", columnDefinition = "TEXT")
    private String observacionesAdmin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transferencia_id")
    private Transferencia transferencia;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
