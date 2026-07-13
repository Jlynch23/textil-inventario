package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RecepcionDocumentoRepository extends JpaRepository<RecepcionDocumento, Long> {
    List<RecepcionDocumento> findByRecepcionId(Long recepcionId);

    @Query("""
        SELECT d FROM RecepcionDocumento d
        WHERE (:empresaId IS NULL OR d.recepcion.empresa.id = :empresaId)
          AND (:tipoDocumento IS NULL OR d.tipoDocumento = :tipoDocumento)
          AND (:anio IS NULL OR YEAR(d.createdAt) = :anio)
          AND (:mes IS NULL OR MONTH(d.createdAt) = :mes)
        ORDER BY d.createdAt DESC
        """)
    List<RecepcionDocumento> buscarConFiltros(@Param("empresaId") Long empresaId,
                                                @Param("tipoDocumento") String tipoDocumento,
                                                @Param("anio") Integer anio,
                                                @Param("mes") Integer mes);
}
