package com.textil.inventario.transferencias;

import com.textil.inventario.catalogo.Ubicacion;
import com.textil.inventario.common.BaseEntity;
import com.textil.inventario.seguridad.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "transferencias")
public class Transferencia extends BaseEntity {

    @Column(name = "numero", nullable = false, length = 20, unique = true)
    private String numero;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ubicacion_origen_id", nullable = false)
    private Ubicacion ubicacionOrigen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_solicita_id", nullable = false)
    private Usuario usuarioSolicita;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_confirma_salida_id")
    private Usuario usuarioConfirmaSalida;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_confirma_llegada_id")
    private Usuario usuarioConfirmaLlegada;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_confirmacion_salida")
    private LocalDateTime fechaConfirmacionSalida;

    @Column(name = "fecha_confirmacion_llegada")
    private LocalDateTime fechaConfirmacionLlegada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoTransferencia estado = EstadoTransferencia.BORRADOR;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @OneToMany(mappedBy = "transferencia", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TransferenciaDetalle> detalles = new ArrayList<>();

    public enum EstadoTransferencia {
        BORRADOR, CONFIRMADA_SALIDA, CONFIRMADA_LLEGADA, CON_DIFERENCIA, ANULADA
    }
}
