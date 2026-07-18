package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProgramaDetalleRepository extends JpaRepository<ProgramaDetalle, Long> {
    List<ProgramaDetalle> findByProgramaId(Long programaId);

    // Puede haber mas de una linea con el mismo articulo en el mismo
    // programa (es normal en el negocio real: dos lotes separados de la
    // misma tela+gramaje+color). Se ordena por id para poder llenar primero
    // la linea mas antigua que todavia tenga pendiente (ver RecepcionService).
    List<ProgramaDetalle> findByProgramaIdAndArticuloIdOrderByIdAsc(Long programaId, Long articuloId);

    // El Color ya no vive en Articulo (ver V26): un mismo articulo puede
    // tener varias lineas de DISTINTO color en el mismo programa, asi que
    // el match de una recepcion a su linea de programa debe considerar
    // tambien el color, no solo el articulo, para no acreditar el lote
    // equivocado.
    List<ProgramaDetalle> findByProgramaIdAndArticuloIdAndColorIdOrderByIdAsc(Long programaId, Long articuloId, Long colorId);
}
