package com.textil.inventario.recepciones;
import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@Entity
@Table(name = "programas")
public class Programa extends BaseEntity {
    @Column(nullable = false, unique = true, length = 20)
    private String numero;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @Column(nullable = false)
    private LocalDate fecha;
    @Column(name = "total_rollos", nullable = false)
    private Integer totalRollos = 0;
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    @OneToMany(mappedBy = "programa", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProgramaDetalle> detalles = new ArrayList<>();

    /**
     * Un programa se considera completo cuando tiene al menos una linea
     * y TODAS sus lineas ya recibieron la cantidad solicitada (isCompleto()
     * de ProgramaDetalle). Se usa para bloquear edicion de programas cerrados.
     */
    public boolean isCompleto() {
        return !detalles.isEmpty() && detalles.stream().allMatch(ProgramaDetalle::isCompleto);
    }

    public int getTotalDetalles() {
        return detalles.size();
    }

    public int getDetallesCompletos() {
        return (int) detalles.stream().filter(ProgramaDetalle::isCompleto).count();
    }

    /**
     * Porcentaje de lineas completadas (0-100), para la barra de progreso
     * en la lista de programas. Devuelve 0 si el programa no tiene lineas.
     */
    public int getPorcentajeProgreso() {
        if (detalles.isEmpty()) return 0;
        return (getDetallesCompletos() * 100) / getTotalDetalles();
    }
}
