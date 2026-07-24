package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecepcionDetalleRepository extends JpaRepository<RecepcionDetalle, Long> {
    List<RecepcionDetalle> findByRecepcionId(Long recepcionId);
    List<RecepcionDetalle> findByProgramaDetalleId(Long programaDetalleId);
    // ¿Alguna línea de este programa ya fue usada en una recepción? Sirve para
    // proteger el borrado del programa (no romper la trazabilidad del kardex).
    boolean existsByProgramaDetalle_ProgramaId(Long programaId);
}
