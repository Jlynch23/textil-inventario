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
}
