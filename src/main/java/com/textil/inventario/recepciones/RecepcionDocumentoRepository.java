package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RecepcionDocumentoRepository extends JpaRepository<RecepcionDocumento, Long> {
    List<RecepcionDocumento> findByRecepcionId(Long recepcionId);

    // Kardex → guía: para un lote de recepciones, el id del documento GUÍA de cada
    // una (MIN si hubiera más de uno). Un solo query, sin N+1.
    @Query("SELECT d.recepcion.id, MIN(d.id) FROM RecepcionDocumento d " +
           "WHERE d.recepcion.id IN :recepcionIds AND d.tipoDocumento = 'GUIA' " +
           "GROUP BY d.recepcion.id")
    List<Object[]> guiaDocPorRecepcion(@Param("recepcionIds") java.util.Collection<Long> recepcionIds);

    // El filtro de anio/mes usa la fecha REAL de la guia (recepcion.fechaGuia),
    // no d.createdAt (que es cuando el PDF se subio al sistema, no cuando
    // realmente ocurrio la recepcion). Con createdAt, un documento de una
    // guia de junio subido hoy en julio nunca aparecia al filtrar por junio.
    @Query("""
        SELECT d FROM RecepcionDocumento d
        WHERE (:empresaId IS NULL OR d.recepcion.empresa.id = :empresaId)
          AND (:tipoDocumento IS NULL OR d.tipoDocumento = :tipoDocumento)
          AND (:anio IS NULL OR YEAR(d.recepcion.fechaGuia) = :anio)
          AND (:mes IS NULL OR MONTH(d.recepcion.fechaGuia) = :mes)
        ORDER BY d.recepcion.fechaGuia DESC
        """)
    List<RecepcionDocumento> buscarConFiltros(@Param("empresaId") Long empresaId,
                                                @Param("tipoDocumento") String tipoDocumento,
                                                @Param("anio") Integer anio,
                                                @Param("mes") Integer mes);
}
