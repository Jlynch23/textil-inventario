package com.textil.inventario.transferencias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Contador transaccional por nombre (#6, auditoría). Se lee con bloqueo pesimista
 * (ver CorrelativoRepository) para reservar el siguiente número sin carreras.
 * No hereda de BaseEntity: su clave primaria es el propio nombre, no un id
 * autogenerado.
 */
@Entity
@Table(name = "correlativo")
@Getter
@Setter
public class Correlativo {

    @Id
    @Column(name = "nombre", length = 50)
    private String nombre;

    @Column(name = "ultimo_valor", nullable = false)
    private Long ultimoValor;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
