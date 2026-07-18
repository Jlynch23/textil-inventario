package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComposicionRepository extends JpaRepository<Composicion, Long> {
    List<Composicion> findByActivoTrue();
    java.util.Optional<Composicion> findByNombreIgnoreCase(String nombre);
}
