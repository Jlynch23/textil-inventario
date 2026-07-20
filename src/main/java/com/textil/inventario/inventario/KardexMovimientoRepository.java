package com.textil.inventario.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KardexMovimientoRepository extends JpaRepository<KardexMovimiento, Long> {
    List<KardexMovimiento> findByArticuloIdOrderByFechaDesc(Long articuloId);
    List<KardexMovimiento> findAllByOrderByFechaDesc();
    List<KardexMovimiento> findByEmpresaIdOrderByFechaDesc(Long empresaId);

    // El kardex crece sin limite (un registro por cada linea de recepcion y
    // transferencia, para siempre). Sin filtro por articulo no tiene sentido
    // cargar la tabla completa en memoria; se acota a los mas recientes.
    List<KardexMovimiento> findTop500ByOrderByFechaDesc();

    Optional<KardexMovimiento> findFirstByTransferenciaIdAndArticuloIdAndTipoMovimiento(
            Long transferenciaId, Long articuloId, KardexMovimiento.TipoMovimiento tipoMovimiento);
}
