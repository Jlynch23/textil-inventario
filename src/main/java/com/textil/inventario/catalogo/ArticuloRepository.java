package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {
    List<Articulo> findByActivoTrue();
    Optional<Articulo> findByCodigoInterno(String codigoInterno);

    @Query("SELECT a FROM Articulo a WHERE a.tipoTela.id = :tipoTelaId AND a.activo = true")
    List<Articulo> findByTipoTelaId(Long tipoTelaId);

    @Query("SELECT a FROM Articulo a WHERE a.color.id = :colorId AND a.activo = true")
    List<Articulo> findByColorId(Long colorId);

    @Query("SELECT a FROM Articulo a WHERE a.tipoTela.id = :tipoTelaId AND a.titulo.id = :tituloId AND a.color.id = :colorId AND a.activo = true")
    java.util.Optional<Articulo> findByTipoTelaIdAndTituloIdAndColorId(Long tipoTelaId, Long tituloId, Long colorId);
}
