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

    @Query("SELECT a FROM Articulo a WHERE a.composicion.id = :composicionId AND a.activo = true")
    List<Articulo> findByComposicionId(Long composicionId);

    @Query("SELECT a FROM Articulo a WHERE a.tipoTela.id = :tipoTelaId AND a.titulo.id = :tituloId AND a.composicion.id = :composicionId AND a.activo = true")
    java.util.Optional<Articulo> findByTipoTelaIdAndTituloIdAndComposicionId(Long tipoTelaId, Long tituloId, Long composicionId);
}
