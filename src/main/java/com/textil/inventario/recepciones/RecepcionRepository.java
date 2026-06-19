package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {
    List<Recepcion> findAllByOrderByCreatedAtDesc();
    List<Recepcion> findByEstado(Recepcion.EstadoRecepcion estado);
}
