package com.textil.inventario.transferencias;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransferenciaDetalleRepository extends JpaRepository<TransferenciaDetalle, Long> {
    List<TransferenciaDetalle> findByTransferenciaId(Long transferenciaId);
}
