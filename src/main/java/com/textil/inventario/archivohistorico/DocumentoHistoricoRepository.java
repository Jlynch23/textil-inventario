package com.textil.inventario.archivohistorico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentoHistoricoRepository extends JpaRepository<DocumentoHistorico, Long> {

    List<DocumentoHistorico> findTop500ByEstadoProcesoOrderByIdAsc(DocumentoHistorico.EstadoProceso estado);

    long countByEstadoProceso(DocumentoHistorico.EstadoProceso estado);

    @Query("""
        SELECT d FROM DocumentoHistorico d
        WHERE (:empresaId IS NULL OR d.empresa.id = :empresaId)
          AND (:tipoDocumento IS NULL OR d.tipoDocumento = :tipoDocumento)
          AND (:estadoProceso IS NULL OR d.estadoProceso = :estadoProceso)
          AND (:anio IS NULL OR YEAR(d.fechaDocumento) = :anio)
          AND (:busqueda IS NULL OR :busqueda = ''
               OR LOWER(d.numeroGuia) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(d.numeroFactura) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(d.razonSocialDetectada) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(d.nombreOriginal) LIKE LOWER(CONCAT('%', :busqueda, '%')))
        ORDER BY d.fechaDocumento DESC NULLS LAST, d.id DESC
        """)
    List<DocumentoHistorico> buscarConFiltros(@Param("empresaId") Long empresaId,
                                               @Param("tipoDocumento") DocumentoHistorico.TipoDocumentoHistorico tipoDocumento,
                                               @Param("estadoProceso") DocumentoHistorico.EstadoProceso estadoProceso,
                                               @Param("anio") Integer anio,
                                               @Param("busqueda") String busqueda);

    // Vinculacion FACTURA -> GUIAS: cuando se procesa una factura, busca las
    // guias ya subidas que coincidan con cada numero que la IA leyo en
    // "guiasReferenciadas", para copiarles el numero de factura.
    List<DocumentoHistorico> findByTipoDocumentoAndNumeroGuia(
            DocumentoHistorico.TipoDocumentoHistorico tipoDocumento, String numeroGuia);

    // Vinculacion GUIA -> FACTURA (orden inverso): cuando se procesa una guia
    // y todavia no tiene numero de factura, busca si alguna factura YA
    // procesada la menciona en su guias_referenciadas.
    @Query("""
        SELECT d FROM DocumentoHistorico d
        WHERE d.tipoDocumento = com.textil.inventario.archivohistorico.DocumentoHistorico$TipoDocumentoHistorico.FACTURA
          AND d.guiasReferenciadas IS NOT NULL
          AND LOWER(d.guiasReferenciadas) LIKE LOWER(CONCAT('%', :numeroGuia, '%'))
        """)
    List<DocumentoHistorico> buscarFacturasQueReferencianGuia(@Param("numeroGuia") String numeroGuia);
}
