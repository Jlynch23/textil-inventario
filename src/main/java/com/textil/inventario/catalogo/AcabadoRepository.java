package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AcabadoRepository extends JpaRepository<Acabado, Long> {
    List<Acabado> findByActivoTrue();
    java.util.Optional<Acabado> findByNombreIgnoreCase(String nombre);
}
