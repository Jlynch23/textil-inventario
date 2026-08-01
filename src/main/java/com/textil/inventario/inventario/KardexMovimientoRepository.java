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

    // M3 (auditoria): incluye colorId. Dos lineas del mismo articulo con distinto
    // color en la misma transferencia tomaban el peso de la primera salida OUT,
    // dejando mal el peso_kg de una de las lineas al confirmar la llegada.
    Optional<KardexMovimiento> findFirstByTransferenciaIdAndArticuloIdAndColorIdAndTipoMovimiento(
            Long transferenciaId, Long articuloId, Long colorId, KardexMovimiento.TipoMovimiento tipoMovimiento);
}
