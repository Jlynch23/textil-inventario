package com.textil.inventario.transferencias;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransferenciaDistribucionRepository extends JpaRepository<TransferenciaDistribucion, Long> {
    List<TransferenciaDistribucion> findByTransferenciaDetalleId(Long transferenciaDetalleId);
}
