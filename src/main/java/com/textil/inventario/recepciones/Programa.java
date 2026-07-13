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

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @OneToMany(mappedBy = "programa", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProgramaDetalle> detalles = new ArrayList<>();
}
