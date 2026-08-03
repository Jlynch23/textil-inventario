package com.textil.inventario.auditoria;

import com.textil.inventario.seguridad.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "log_eventos")
public class LogEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 50)
    private String accion;

    @Column(length = 50)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // Auditoria KDX-01: el log de auditoria es append-only. Un evento registrado
    // no se edita; este guard hace que cualquier UPDATE accidental reviente en
    // vez de alterar en silencio la historia forense. (El DELETE queda a nivel de
    // usuario SQL / retencion; V34 lo vacia por SQL directo en el arranque, sin
    // pasar por este callback.)
    @PreUpdate
    protected void impedirActualizacion() {
        throw new UnsupportedOperationException(
                "El log de auditoría es append-only: un evento no se puede modificar.");
    }
}
