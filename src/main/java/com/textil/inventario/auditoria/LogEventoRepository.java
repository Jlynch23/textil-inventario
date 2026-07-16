package com.textil.inventario.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface LogEventoRepository extends JpaRepository<LogEvento, Long> {

    @Query("""
        SELECT l FROM LogEvento l
        WHERE (:usuarioId IS NULL OR l.usuario.id = :usuarioId)
          AND (:accion IS NULL OR l.accion = :accion)
          AND (:desde IS NULL OR l.createdAt >= :desde)
          AND (:hasta IS NULL OR l.createdAt <= :hasta)
        ORDER BY l.createdAt DESC
        """)
    List<LogEvento> buscarConFiltros(@Param("usuarioId") Long usuarioId,
                                       @Param("accion") String accion,
                                       @Param("desde") LocalDateTime desde,
                                       @Param("hasta") LocalDateTime hasta);

    // Usado por Reportes > Errores del Sistema (solo SUPERADMIN) para listar
    // las excepciones no controladas capturadas por GlobalExceptionHandler.
    List<LogEvento> findByAccionOrderByCreatedAtDesc(String accion);
}
