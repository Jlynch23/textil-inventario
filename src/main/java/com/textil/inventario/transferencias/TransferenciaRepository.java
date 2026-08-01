package com.textil.inventario.transferencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
    // #9 (auditoria): trae la ubicacion de origen en el mismo query. Las demas
    // to-one de Transferencia (los 3 usuarios) pasaron a LAZY y no se listan, asi
    // que ya no disparan un SELECT por fila.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "ubicacionOrigen")
    List<Transferencia> findAllByOrderByFechaSolicitudDesc();
    List<Transferencia> findByEstado(Transferencia.EstadoTransferencia estado);

    // A5: el numero se deriva del MAXIMO existente, NO de count(). Con count(),
    // borrar una transferencia BORRADOR intermedia bajaba el contador y el
    // siguiente alta reusaba un TRF-NNNNNN ya existente -> viola el UNIQUE.
    @Query("SELECT MAX(t.numero) FROM Transferencia t")
    String findMaxNumero();
}
