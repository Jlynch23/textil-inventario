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
}
