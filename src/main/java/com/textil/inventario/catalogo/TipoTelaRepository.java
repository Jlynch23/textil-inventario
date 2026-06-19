package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TipoTelaRepository extends JpaRepository<TipoTela, Long> {
    List<TipoTela> findByActivoTrue();
}
