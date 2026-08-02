package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface RecepcionDetalleRepository extends JpaRepository<RecepcionDetalle, Long> {
    List<RecepcionDetalle> findByRecepcionId(Long recepcionId);
    List<RecepcionDetalle> findByProgramaDetalleId(Long programaDetalleId);

    // Kardex → guía: para un lote de detalles, el id de su recepción. Proyección
    // liviana (no carga entidades) para resolver el ojito de la guía sin N+1.
    @Query("SELECT d.id, d.recepcion.id FROM RecepcionDetalle d WHERE d.id IN :ids")
    List<Object[]> recepcionIdPorDetalle(@Param("ids") Collection<Long> ids);
    // ¿Alguna línea de este programa ya fue usada en una recepción? Sirve para
    // proteger el borrado del programa (no romper la trazabilidad del kardex).
    boolean existsByProgramaDetalle_ProgramaId(Long programaId);
}
