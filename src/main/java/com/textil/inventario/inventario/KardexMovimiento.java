package com.textil.inventario.inventario;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.seguridad.Usuario;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "kardex_movimientos")
public class KardexMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    // VARCHAR, no ENUM de MySQL (V47): TRANSFORMACION_IN/OUT ya obligo a un ALTER
    // sobre esta tabla; el proximo tipo de movimiento no deberia obligar a otro.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false, length = 30)
    private TipoMovimiento tipoMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_origen_id")
    private Ubicacion ubicacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_destino_id")
    private Ubicacion ubicacionDestino;

    @Column(nullable = false)
    private Integer rollos;

    @Column(name = "peso_kg", precision = 10, scale = 2)
    private BigDecimal pesoKg;

    // Documento que origino el movimiento, generalizado a (tipo, id) en V47.
    //
    // Antes habia UNA COLUMNA POR TIPO de documento (recepcion_detalle_id,
    // transferencia_id). V2 suma compra de hilo, orden de tejido y envio a
    // tintoreria: serian CINCO columnas con cuatro siempre NULL en cada fila,
    // mas cinco FK y cinco indices, y cada modulo nuevo obligaria a un ALTER
    // TABLE sobre la tabla mas grande del sistema. Con el par (tipo, id),
    // sumar un tipo de documento no toca el esquema.
    //
    // El precio es perder la FK real: la integridad referencial ya no la
    // garantiza la base. Por eso NO hay setters sueltos -- se vincula con
    // vincularDocumento(), que exige los dos datos juntos, y se lee con los
    // accesores tipados de abajo, que devuelven el id SOLO si el tipo calza.
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 30)
    private TipoDocumento tipoDocumento;

    @Setter(AccessLevel.NONE)
    @Column(name = "documento_id")
    private Long documentoId;

    // Une las dos caras de UNA transformacion (TRANSFORMACION_OUT del material
    // consumido + TRANSFORMACION_IN del producido). No alcanza con el documento:
    // una misma orden de tejido puede tener varias transformaciones, y sin este
    // id no se sabe que hilo salio para que rollo. Queda NULL hasta V2.
    @Column(name = "operacion_id")
    private Long operacionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Auditoria KDX-01: el kardex es append-only. Los movimientos NUNCA se
    // actualizan (solo se insertan); corregir un error contable se hace con un
    // contramovimiento, no editando la historia. Este guard hace que cualquier
    // UPDATE accidental (de codigo futuro que herede el repo JpaRepository
    // completo) reviente en vez de mutar en silencio un asiento historico.
    // NO se bloquea @PreRemove: el borrado se usa en el seed/cleanup de los tests
    // de integracion y en migraciones; la garantia fuerte contra DELETE va a
    // nivel de usuario SQL (GRANT), fuera del alcance de la app.
    @PreUpdate
    protected void impedirActualizacion() {
        throw new UnsupportedOperationException(
                "El kardex es append-only: un movimiento no se puede modificar (usar un contramovimiento).");
    }

    /**
     * Vincula el movimiento con el documento que lo origino. Los dos datos van
     * juntos a proposito: un documento_id sin tipo no se puede interpretar (el id
     * 7 puede ser una recepcion o una transferencia), y un tipo sin id no vincula
     * nada.
     */
    public void vincularDocumento(TipoDocumento tipo, Long id) {
        if (tipo == null || id == null) {
            throw new IllegalArgumentException(
                    "El vinculo del kardex necesita tipo de documento Y id (recibido: " + tipo + ", " + id + ").");
        }
        this.tipoDocumento = tipo;
        this.documentoId = id;
    }

    public void vincularRecepcionDetalle(Long recepcionDetalleId) {
        vincularDocumento(TipoDocumento.RECEPCION_DETALLE, recepcionDetalleId);
    }

    public void vincularTransferencia(Long transferenciaId) {
        vincularDocumento(TipoDocumento.TRANSFERENCIA, transferenciaId);
    }

    /** El id del documento SOLO si es del tipo pedido; null en cualquier otro caso. */
    public Long documentoIdSi(TipoDocumento tipo) {
        return tipoDocumento == tipo ? documentoId : null;
    }

    // Accesores tipados: leer documentoId a secas invita a asumir que es de un
    // tipo que no es. Estos devuelven null cuando el movimiento vino de otro lado.
    public Long getRecepcionDetalleId() {
        return documentoIdSi(TipoDocumento.RECEPCION_DETALLE);
    }

    public Long getTransferenciaId() {
        return documentoIdSi(TipoDocumento.TRANSFERENCIA);
    }

    public enum TipoMovimiento {
        INGRESO, TRANSFERENCIA_OUT, TRANSFERENCIA_IN, AJUSTE,
        // Una transformacion consume UN material y produce OTRO distinto (entran
        // 100 kg de hilo, salen 80 kg de tela cruda). Los otros tipos mueven el
        // mismo material de un lado a otro o lo ajustan, y por eso no alcanzaban.
        // Las dos caras del mismo hecho se unen por operacionId.
        TRANSFORMACION_IN, TRANSFORMACION_OUT
    }

    /**
     * De que documento salio el movimiento. Se guarda como texto (no como ENUM de
     * MySQL) justamente para que sumar un tipo en V2 -- COMPRA_HILO, ORDEN_TEJIDO,
     * ENVIO_TINTE -- no obligue a un ALTER TABLE.
     */
    public enum TipoDocumento {
        RECEPCION_DETALLE, TRANSFERENCIA
    }
}
