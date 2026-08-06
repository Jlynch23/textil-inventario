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

    // EMISOR de la guia: la tintoreria que tiño y despacho (V45). Se lee del
    // propio documento con el OCR, NO se asume: en el modelo multi-cliente cada
    // cliente trabaja con su propia tintoreria. Nullable porque las recepciones
    // anteriores a V45 no lo tienen y una guia puede no traerlo legible.
    @Column(name = "emisor_nombre", length = 150)
    private String emisorNombre;

    @Column(name = "emisor_ruc", length = 11)
    private String emisorRuc;

    // Auditoria INV-02: numero_guia lleva UNIQUE (una guia = una recepcion). Es
    // NULLABLE a proposito: una recepcion puede quedar sin guia, y en MySQL el
    // UNIQUE permite multiples NULL (las guias en blanco se guardan como NULL, no
    // como "", para que no choquen entre si). La unicidad la impone la migracion
    // V42 (constraint uq_recepcion_numero_guia).
    @Column(name = "numero_guia", length = 50)
    private String numeroGuia;

    @Column(name = "numero_factura", length = 50)
    private String numeroFactura;

    @Column(name = "fecha_factura")
    private java.time.LocalDate fechaFactura;

    @Column(name = "fecha_guia", nullable = false)
    private LocalDate fechaGuia;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDate fechaRecepcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRecepcion estado = EstadoRecepcion.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // Lock optimista (auditoria red-team R-C1): el guard de estado de
    // confirmarRecepcion es TOCTOU -- dos confirmaciones CONCURRENTES leen ambas
    // estado=PENDIENTE, pasan el guard y aplican el stock DOS veces. Con @Version,
    // la segunda transaccion actualiza la fila WHERE version=<vieja> -> 0 filas ->
    // OptimisticLockException -> rollback de todo su movimiento de stock/kardex.
    @Version
    @Column(nullable = false)
    private Integer version;

    // #9 (auditoria): LAZY. No se muestra en el listado (que ya trae empresa por
    // @EntityGraph), asi que EAGER solo disparaba un SELECT de usuario por fila.
    // El unico acceso Java es dentro de confirmarRecepcion (@Transactional, sesion
    // abierta), donde carga sin problema. NO volver a EAGER (reintroduce N+1).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RecepcionDetalle> detalles = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    /**
     * Numero de guia sin serie ni ceros de relleno ("TG01-00022836" -> "22836"),
     * para las listas donde lo unico que se hace es comparar guias de un vistazo.
     * El numero completo sigue en getNumeroGuia() (y se deja en el tooltip).
     */
    public String getNumeroGuiaCorto() {
        return com.textil.inventario.common.NumeroDocumento.corto(numeroGuia);
    }

    /** Igual que getNumeroGuiaCorto() pero para la factura. */
    public String getNumeroFacturaCorto() {
        return com.textil.inventario.common.NumeroDocumento.corto(numeroFactura);
    }

    public enum EstadoRecepcion {
        PENDIENTE, CONFIRMADA, CON_DIFERENCIAS
    }
}
