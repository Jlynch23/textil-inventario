package com.textil.inventario.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface LogEventoRepository extends JpaRepository<LogEvento, Long> {

    // Excluye ERROR_SISTEMA: esas entradas tienen su propia vista dedicada
    // en Reportes > Errores del Sistema (solo SUPERADMIN), para que no se
    // vean duplicadas entre las dos pantallas.
    //
    // ocultarSuperadmin: cuando lo consulta un ADMIN (dueño-cliente), se ocultan
    // las acciones del proveedor (rol SUPERADMIN) para que su log muestre solo la
    // actividad de su propio equipo, sin ver el mantenimiento del proveedor. Se
    // usa LEFT JOIN para no descartar eventos de sistema con usuario nulo.
    @Query("""
        SELECT l FROM LogEvento l
        LEFT JOIN l.usuario u
        LEFT JOIN u.rol r
        WHERE l.accion != 'ERROR_SISTEMA'
          AND (:usuarioId IS NULL OR u.id = :usuarioId)
          AND (:accion IS NULL OR l.accion = :accion)
          AND (:desde IS NULL OR l.createdAt >= :desde)
          AND (:hasta IS NULL OR l.createdAt <= :hasta)
          AND (:ocultarSuperadmin = FALSE OR r IS NULL OR r.nombre <> 'SUPERADMIN')
        ORDER BY l.createdAt DESC
        """)
    List<LogEvento> buscarConFiltros(@Param("usuarioId") Long usuarioId,
                                       @Param("accion") String accion,
                                       @Param("desde") LocalDateTime desde,
                                       @Param("hasta") LocalDateTime hasta,
                                       @Param("ocultarSuperadmin") boolean ocultarSuperadmin);

    // Usado por Reportes > Errores del Sistema (solo SUPERADMIN) para listar
    // las excepciones no controladas capturadas por GlobalExceptionHandler.
    List<LogEvento> findByAccionOrderByCreatedAtDesc(String accion);
}
