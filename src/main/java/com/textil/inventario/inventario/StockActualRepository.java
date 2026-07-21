package com.textil.inventario.inventario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
public interface StockActualRepository extends JpaRepository<StockActual, Long> {
    // PESSIMISTIC_WRITE (SELECT ... FOR UPDATE): este metodo solo se usa
    // dentro de RecepcionService/TransferenciaService para hacer
    // stock.setRollos(stock.getRollos() + n) y guardar. Sin el lock, dos
    // confirmaciones concurrentes sobre el mismo articulo+ubicacion+color
    // pueden leer el mismo valor inicial y la segunda en guardar pisa el
    // incremento de la primera (lost update) -- el conteo final queda mal
    // sin ningun error visible. El lock obliga a la segunda transaccion a
    // esperar a que la primera confirme antes de leer el valor actualizado.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockActual> findByArticuloIdAndUbicacionIdAndColorId(Long articuloId, Long ubicacionId, Long colorId);
    List<StockActual> findByUbicacionId(Long ubicacionId);
    @Query("SELECT s FROM StockActual s WHERE s.rollos > 0 ORDER BY s.ubicacion.nombre, s.articulo.tipoTela.nombre")
    List<StockActual> findStockDisponible();
}
