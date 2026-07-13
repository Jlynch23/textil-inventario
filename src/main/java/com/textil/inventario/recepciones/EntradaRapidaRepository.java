package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EntradaRapidaRepository extends JpaRepository<EntradaRapida, Long> {
    List<EntradaRapida> findByEstadoOrderByCreatedAtDesc(String estado);
    List<EntradaRapida> findAllByOrderByCreatedAtDesc();
}
