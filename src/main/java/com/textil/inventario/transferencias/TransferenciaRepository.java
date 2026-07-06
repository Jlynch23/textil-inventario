package com.textil.inventario.transferencias;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
    List<Transferencia> findAllByOrderByFechaSolicitudDesc();
    List<Transferencia> findByEstado(Transferencia.EstadoTransferencia estado);
}
