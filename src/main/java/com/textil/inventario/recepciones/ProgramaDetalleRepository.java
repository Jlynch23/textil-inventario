package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProgramaDetalleRepository extends JpaRepository<ProgramaDetalle, Long> {
    List<ProgramaDetalle> findByProgramaId(Long programaId);
    Optional<ProgramaDetalle> findByProgramaIdAndColorId(Long programaId, Long colorId);
}
