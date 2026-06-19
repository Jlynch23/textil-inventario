package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {
    List<Ubicacion> findByActivoTrue();
    Optional<Ubicacion> findByCodigo(String codigo);
    Optional<Ubicacion> findByEsPrincipalTrue();
}
