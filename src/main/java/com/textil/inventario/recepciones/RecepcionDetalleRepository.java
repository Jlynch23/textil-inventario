package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecepcionDetalleRepository extends JpaRepository<RecepcionDetalle, Long> {
    List<RecepcionDetalle> findByRecepcionId(Long recepcionId);
    List<RecepcionDetalle> findByProgramaDetalleId(Long programaDetalleId);
}
