package com.textil.inventario.seguridad;

import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class Usuario extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void inicializarUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Nombre para mostrar en pantalla, capitalizando el username segun la
     * convencion de cuentas usada (inicial + apellido, ej "jlynch",
     * "oclemente"): se ponen en mayuscula los primeros 2 caracteres
     * (la inicial + la primera letra del apellido) y el resto queda tal
     * cual. "jlynch" -> "JLynch", "oclemente" -> "OClemente".
     */
    public String getNombreMostrar() {
        if (username == null || username.isBlank()) return username;
        if (username.length() < 2) return username.toUpperCase();
        return username.substring(0, 2).toUpperCase() + username.substring(2);
    }
}
