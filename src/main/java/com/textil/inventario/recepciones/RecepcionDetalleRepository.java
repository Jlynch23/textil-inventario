package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface RecepcionDetalleRepository extends JpaRepository<RecepcionDetalle, Long> {
    List<RecepcionDetalle> findByRecepcionId(Long recepcionId);
    List<RecepcionDetalle> findByProgramaDetalleId(Long programaDetalleId);

    // Kardex → guía: para un lote de detalles, el id de su recepción. Proyección
    // liviana (no carga entidades) para resolver el ojito de la guía sin N+1.
    @Query("SELECT d.id, d.recepcion.id FROM RecepcionDetalle d WHERE d.id IN :ids")
    List<Object[]> recepcionIdPorDetalle(@Param("ids") Collection<Long> ids);
    // ¿Alguna línea de este programa ya fue usada en una recepción? Sirve para
    // proteger el borrado del programa (no romper la trazabilidad del kardex).
    boolean existsByProgramaDetalle_ProgramaId(Long programaId);

    // Igual, pero para UNA línea del programa: protege el cambio de artículo o
    // color de esa línea (ver ProgramaService.aplicarArticuloYColorExistente).
    boolean existsByProgramaDetalleId(Long programaDetalleId);

    // Líneas de recepción que NOMBRAN un programa pero no quedaron vinculadas a
    // ninguna de sus líneas (programa_detalle_id NULL). Son el descuento que
    // "no se hizo": el stock entró igual, pero el programa nunca se entero.
    //
    // Pasa por dos caminos, los dos mudos hasta ahora (agregarDetalle):
    //   1. el numero de programa de la guia no resuelve a ningun Programa;
    //   2. resuelve, pero el programa no tiene una linea con ESE articulo+color
    //      (tipico: misma tela/titulo/color pero distinta composicion o acabado,
    //      que son articulos distintos).
    //
    // Se compara con el numero SIN ceros a la izquierda en los dos lados, igual
    // que buscarProgramaNormalizado, porque la guia suele traer "0472" y el
    // programa se registra "472".
    @Query("""
           SELECT d FROM RecepcionDetalle d
           WHERE d.programaDetalle IS NULL
             AND d.programaTenido IS NOT NULL
             AND TRIM(LEADING '0' FROM TRIM(d.programaTenido)) = TRIM(LEADING '0' FROM :numero)
           ORDER BY d.id
           """)
    List<RecepcionDetalle> huerfanasDelPrograma(@Param("numero") String numero);
}
