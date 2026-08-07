package com.textil.inventario.inventario;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KardexMovimientoRepository extends JpaRepository<KardexMovimiento, Long> {
    // #9 (auditoria): @EntityGraph para no disparar N+1 al renderizar el kardex
    // por articulo (kardex de inventario).
    @EntityGraph(attributePaths = {
            "articulo", "articulo.tipoTela", "articulo.titulo", "articulo.composicion", "articulo.acabado",
            "color", "empresa", "ubicacionOrigen", "ubicacionDestino", "usuario"
    })
    List<KardexMovimiento> findByArticuloIdOrderByFechaDesc(Long articuloId);

    // Auditoria (ALTA, N+1/memoria): el reporte de kardex empuja el rango de
    // fechas a SQL en vez de traer TODA la tabla (que crece sin fin) y filtrar en
    // memoria. desde/hasta son limites [desde, hasta) en LocalDateTime.
    @EntityGraph(attributePaths = {
            "articulo", "articulo.tipoTela", "articulo.titulo", "articulo.composicion", "articulo.acabado",
            "color", "empresa", "ubicacionOrigen", "ubicacionDestino", "usuario"
    })
    @Query("SELECT k FROM KardexMovimiento k WHERE (:desde IS NULL OR k.fecha >= :desde) "
            + "AND (:hasta IS NULL OR k.fecha < :hasta) ORDER BY k.fecha DESC")
    List<KardexMovimiento> buscarPorRango(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    // M5 (auditoria): las asociaciones to-one del kardex se traen en un solo
    // query (LEFT JOINs) para no disparar un SELECT por fila al listar/exportar.
    //
    // El kardex crece sin limite (un registro por cada linea de recepcion y
    // transferencia, para siempre). Sin filtro por articulo no tiene sentido
    // cargar la tabla completa en memoria; se acota a los mas recientes.
    @EntityGraph(attributePaths = {
            "articulo", "articulo.tipoTela", "articulo.titulo", "articulo.composicion", "articulo.acabado",
            "color", "empresa", "ubicacionOrigen", "ubicacionDestino", "usuario"
    })
    List<KardexMovimiento> findTop500ByOrderByFechaDesc();

    // M3 (auditoria): incluye colorId. Dos lineas del mismo articulo con distinto
    // color en la misma transferencia tomaban el peso de la primera salida OUT,
    // dejando mal el peso_kg de una de las lineas al confirmar la llegada.
    //
    // V47: el vinculo dejo de ser transferencia_id y paso al par
    // (tipo_documento, documento_id), asi que la busqueda arranca por el tipo.
    Optional<KardexMovimiento> findFirstByTipoDocumentoAndDocumentoIdAndArticuloIdAndColorIdAndTipoMovimiento(
            KardexMovimiento.TipoDocumento tipoDocumento, Long documentoId, Long articuloId, Long colorId,
            KardexMovimiento.TipoMovimiento tipoMovimiento);
}
