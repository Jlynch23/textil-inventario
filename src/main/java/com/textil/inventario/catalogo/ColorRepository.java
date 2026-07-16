package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ColorRepository extends JpaRepository<Color, Long> {
    List<Color> findByActivoTrue();
    List<Color> findByFamilia(String familia);

    // FAST DYE reasigna codigos con el tiempo: puede haber mas de un color
    // con el mismo codigo_fast_dye (uno viejo y uno nuevo). Ver
    // CatalogoService.resolverColorPorCodigo() para la logica de desambiguacion.
    List<Color> findByCodigoFastDye(String codigoFastDye);

    java.util.Optional<Color> findByNombreOficialIgnoreCase(String nombreOficial);
}
