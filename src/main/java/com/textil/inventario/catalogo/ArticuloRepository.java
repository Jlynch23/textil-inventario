package com.textil.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {
    List<Articulo> findByActivoTrue();
    Optional<Articulo> findByCodigoInterno(String codigoInterno);

    // Activos ordenados igual que se muestran (tipo de tela -> titulo -> composicion
    // -> acabado), para que los desplegables no salgan mezclados (2X1/1X1 juntos).
    @Query("SELECT a FROM Articulo a WHERE a.activo = true " +
           "ORDER BY a.tipoTela.nombre, a.titulo.valor, a.composicion.nombre, a.acabado.nombre")
    List<Articulo> findByActivoTrueOrdenados();

    @Query("SELECT a FROM Articulo a WHERE a.tipoTela.id = :tipoTelaId AND a.titulo.id = :tituloId AND a.composicion.id = :composicionId AND a.acabado.id = :acabadoId AND a.activo = true")
    java.util.Optional<Articulo> findByTipoTelaIdAndTituloIdAndComposicionIdAndAcabadoId(Long tipoTelaId, Long tituloId, Long composicionId, Long acabadoId);
}
