package com.textil.inventario.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StockActualRepository extends JpaRepository<StockActual, Long> {

    Optional<StockActual> findByArticuloIdAndUbicacionIdAndEmpresaId(
        Long articuloId, Long ubicacionId, Long empresaId);

    List<StockActual> findByUbicacionId(Long ubicacionId);

    List<StockActual> findByEmpresaId(Long empresaId);

    @Query("SELECT s FROM StockActual s WHERE s.rollos > 0 ORDER BY s.ubicacion.nombre, s.articulo.tipoTela.nombre")
    List<StockActual> findStockDisponible();
}
