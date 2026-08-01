package com.textil.inventario.transferencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
    List<Transferencia> findAllByOrderByFechaSolicitudDesc();
    List<Transferencia> findByEstado(Transferencia.EstadoTransferencia estado);

    // A5: el numero se deriva del MAXIMO existente, NO de count(). Con count(),
    // borrar una transferencia BORRADOR intermedia bajaba el contador y el
    // siguiente alta reusaba un TRF-NNNNNN ya existente -> viola el UNIQUE.
    @Query("SELECT MAX(t.numero) FROM Transferencia t")
    String findMaxNumero();
}
