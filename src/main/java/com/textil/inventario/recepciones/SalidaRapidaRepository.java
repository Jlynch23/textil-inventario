package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalidaRapidaRepository extends JpaRepository<SalidaRapida, Long> {
    List<SalidaRapida> findByEstadoOrderByCreatedAtDesc(String estado);
    List<SalidaRapida> findAllByOrderByCreatedAtDesc();
}
