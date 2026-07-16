package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "documentos_historicos")
public class DocumentoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private TipoDocumentoHistorico tipoDocumento;

    @Column(name = "numero_guia", length = 50)
    private String numeroGuia;

    @Column(name = "numero_factura", length = 50)
    private String numeroFactura;

    @Column(name = "fecha_documento")
    private LocalDate fechaDocumento;

    @Column(name = "razon_social_detectada", length = 200)
    private String razonSocialDetectada;

    @Column(name = "guias_referenciadas", columnDefinition = "TEXT")
    private String guiasReferenciadas;

    @Column(name = "ruta_relativa_zip", length = 500)
    private String rutaRelativaZip;

    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    @Column(name = "ruta_archivo", nullable = false, length = 500)
    private String rutaArchivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_proceso", nullable = false, length = 20)
    private EstadoProceso estadoProceso = EstadoProceso.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "productos_encontrados", nullable = false)
    private Integer productosEncontrados = 0;

    @Column(name = "colores_creados", nullable = false)
    private Integer coloresCreados = 0;

    @Column(name = "articulos_creados", nullable = false)
    private Integer articulosCreados = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "procesado_at")
    private LocalDateTime procesadoAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public enum TipoDocumentoHistorico {
        GUIA, FACTURA, OTRO
    }

    public enum EstadoProceso {
        PENDIENTE, PROCESADO, ERROR
    }
}
