package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    List<Empresa> findByActivoTrue();
    // Para la pantalla de gestion: TODAS (activas e inactivas), las activas
    // primero y luego por nombre. Las inactivas se muestran para poder
    // reactivarlas (el RUC sigue ocupado aunque esten inactivas).
    List<Empresa> findAllByOrderByActivoDescNombreAsc();
}
