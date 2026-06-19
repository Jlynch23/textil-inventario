package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TituloRepository extends JpaRepository<Titulo, Long> {
    List<Titulo> findByActivoTrue();
}
