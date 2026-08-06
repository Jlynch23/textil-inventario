package com.textil.inventario.almacen;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalidaRapidaRepository extends JpaRepository<SalidaRapida, Long> {
    List<SalidaRapida> findByEstadoOrderByCreatedAtDesc(String estado);
}
