package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ColorRepository extends JpaRepository<Color, Long> {
    List<Color> findByActivoTrue();
    List<Color> findByFamilia(String familia);
    java.util.Optional<Color> findByCodigoFastDye(String codigoFastDye);
}
