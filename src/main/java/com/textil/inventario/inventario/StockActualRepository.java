package com.textil.inventario.inventario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    // M5 (auditoria): @EntityGraph trae las asociaciones to-one en el MISMO
    // query (LEFT JOINs, sin duplicar filas), en vez de disparar un SELECT por
    // fila al renderizar el reporte de stock (N+1). Los @ManyToOne siguen EAGER;
    // esto solo controla COMO se cargan en este listado.
    @EntityGraph(attributePaths = {
            "ubicacion", "color",
            "articulo", "articulo.tipoTela", "articulo.titulo",
            "articulo.composicion", "articulo.acabado"
    })
    @Query("SELECT s FROM StockActual s WHERE s.rollos > 0 ORDER BY s.ubicacion.nombre, s.articulo.tipoTela.nombre")
    List<StockActual> findStockDisponible();

    // Stock con rollos > 0 de UNA ubicacion (origen de la transferencia), con
    // color y articulo YA cargados (@EntityGraph): lo consume el desplegable
    // dependiente del form de agregar linea, que corre fuera de @Transactional
    // (OSIV apagado), asi que las asociaciones deben venir inicializadas.
    @EntityGraph(attributePaths = {"color", "articulo"})
    @Query("SELECT s FROM StockActual s WHERE s.ubicacion.id = :ubicacionId AND s.rollos > 0")
    List<StockActual> findDisponibleConColorPorUbicacion(@Param("ubicacionId") Long ubicacionId);
}
