package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProgramaDetalleRepository extends JpaRepository<ProgramaDetalle, Long> {

    // El Color ya no vive en Articulo (ver V26): un mismo articulo puede
    // tener varias lineas de DISTINTO color en el mismo programa, asi que
    // el match de una recepcion a su linea de programa debe considerar
    // tambien el color, no solo el articulo, para no acreditar el lote
    // equivocado.
    List<ProgramaDetalle> findByProgramaIdAndArticuloIdAndColorIdOrderByIdAsc(Long programaId, Long articuloId, Long colorId);
}
