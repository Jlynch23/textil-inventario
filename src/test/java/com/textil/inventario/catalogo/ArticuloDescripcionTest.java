package com.textil.inventario.catalogo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Articulo.getDescripcion() es la UNICA fuente del texto que describe un
 * articulo en pantallas, reportes, Excel y alertas. Antes cada lugar lo armaba
 * por su cuenta y habian divergido: los reportes omitian composicion y acabado,
 * lo que ademas hacia que el reporte de stock bajo agrupara como un solo item a
 * articulos distintos y ocultara faltantes reales.
 */
class ArticuloDescripcionTest {

    private Articulo articulo(String tipo, String titulo, String composicion, String acabado) {
        TipoTela tt = new TipoTela();   tt.setNombre(tipo);
        Titulo t = new Titulo();        t.setValor(titulo);
        Composicion c = new Composicion(); c.setNombre(composicion);
        Acabado a = new Acabado();      a.setNombre(acabado);

        Articulo art = new Articulo();
        art.setTipoTela(tt);
        art.setTitulo(t);
        art.setComposicion(c);
        art.setAcabado(a);
        return art;
    }

    @Test
    void incluyeComposicion_yOmiteElAcabadoLisoPorSerElDefecto() {
        assertThat(articulo("RIB 2X1", "30/1", "ALGODON", "LISO").getDescripcion())
                .isEqualTo("RIB 2X1 30/1 / ALGODON");
    }

    @Test
    void muestraElAcabadoCuandoNoEsLiso() {
        assertThat(articulo("RIB 2X1", "30/1", "ALGODON", "ACANALADO").getDescripcion())
                .isEqualTo("RIB 2X1 30/1 / ALGODON · ACANALADO");
    }

    @Test
    void articulosQueSoloSeDiferencianPorComposicionOAcabado_noComparteDescripcion() {
        // Este es el caso que rompia los reportes: con el texto viejo (solo tipo
        // de tela + titulo) los tres colapsaban en "RIB 2X1 30/1" y sus rollos
        // se sumaban como si fueran el mismo articulo.
        String liso      = articulo("RIB 2X1", "30/1", "ALGODON", "LISO").getDescripcion();
        String melange   = articulo("RIB 2X1", "30/1", "MELANGE 10%", "LISO").getDescripcion();
        String acanalado = articulo("RIB 2X1", "30/1", "ALGODON", "ACANALADO").getDescripcion();

        assertThat(liso).isNotEqualTo(melange);
        assertThat(liso).isNotEqualTo(acanalado);
        assertThat(melange).isNotEqualTo(acanalado);
    }

    @Test
    void elAcabadoLisoSeOmiteSinImportarMayusculas() {
        assertThat(articulo("RIB 1X1", "24/1", "ALGODON", "liso").getDescripcion())
                .doesNotContain("·");
    }
}
