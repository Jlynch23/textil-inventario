package com.textil.inventario.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KardexMovimientoRepository extends JpaRepository<KardexMovimiento, Long> {
    List<KardexMovimiento> findByArticuloIdOrderByFechaDesc(Long articuloId);
    List<KardexMovimiento> findAllByOrderByFechaDesc();
    List<KardexMovimiento> findByEmpresaIdOrderByFechaDesc(Long empresaId);
}
