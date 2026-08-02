package com.textil.inventario.inventario;

import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.catalogo.Color;
import com.textil.inventario.catalogo.TipoTela;
import com.textil.inventario.catalogo.Titulo;
import com.textil.inventario.catalogo.Ubicacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de la agregación "Stock por color" (sin BD): agrupa por color →
 * ubicación → artículo, suma rollos/peso, ordena por cantidad y marca stock bajo.
 */
class StockPorColorTest {

    private TipoTela tela(String n) { TipoTela t = new TipoTela(); t.setNombre(n); return t; }
    private Titulo titulo(String v) { Titulo t = new Titulo(); t.setValor(v); return t; }

    private Ubicacion ubic(long id, String n) {
        Ubicacion u = new Ubicacion(); u.setId(id); u.setNombre(n); return u;
    }
    private Color color(long id, String nombre, String fd) {
        Color c = new Color(); c.setId(id); c.setNombreOficial(nombre); c.setCodigoFastDye(fd); return c;
    }
    private Articulo art(String tela, String titulo) {
        Articulo a = new Articulo(); a.setTipoTela(tela(tela)); a.setTitulo(titulo(titulo)); return a;
    }
    private StockActual sa(Articulo a, Color c, Ubicacion u, int rollos, String peso) {
        StockActual s = new StockActual();
        s.setArticulo(a); s.setColor(c); s.setUbicacion(u);
        s.setRollos(rollos); s.setPesoKg(new BigDecimal(peso));
        return s;
    }

    @Test
    void agrupaPorColorYSumaRollosYPeso() {
        Color rojo = color(1, "ROJO", "1188");
        Color negro = color(2, "NEGRO", "9000");
        Ubicacion praderas = ubic(1, "Praderas");
        Ubicacion tienda = ubic(2, "Tienda");
        Articulo rib1 = art("RIB 1X1", "24/1");
        Articulo rib2 = art("RIB 2X1", "24/1");

        List<StockActual> stock = List.of(
                sa(rib1, rojo, praderas, 14, "320"),
                sa(rib2, rojo, praderas, 14, "321"),
                sa(rib1, rojo, tienda, 14, "319"),
                sa(rib1, negro, praderas, 5, "115")
        );

        StockPorColor.Resumen r = StockPorColor.construir(stock, 10);

        // Totales globales
        assertThat(r.getTotalRollos()).isEqualTo(47);
        assertThat(r.getTotalPeso()).isEqualByComparingTo("1075");
        assertThat(r.getTotalColores()).isEqualTo(2);
        assertThat(r.getTotalUbicaciones()).isEqualTo(2);

        // Ordenado por cantidad: ROJO (42) antes que NEGRO (5)
        assertThat(r.getColores()).hasSize(2);
        StockPorColor.PorColor primerColor = r.getColores().get(0);
        assertThat(primerColor.getNombre()).isEqualTo("ROJO");
        assertThat(primerColor.getRollos()).isEqualTo(42);
        assertThat(primerColor.isBajo()).isFalse();
        assertThat(primerColor.getAnchoBarra()).isEqualTo(100); // el más grande

        // ROJO está en 2 ubicaciones; Praderas (28) antes que Tienda (14)
        assertThat(primerColor.getUbicaciones()).hasSize(2);
        assertThat(primerColor.getUbicaciones().get(0).getUbicacion()).isEqualTo("Praderas");
        assertThat(primerColor.getUbicaciones().get(0).getRollos()).isEqualTo(28);
        // En Praderas hay 2 artículos (RIB 1X1 y RIB 2X1)
        assertThat(primerColor.getUbicaciones().get(0).getLineas()).hasSize(2);

        // NEGRO cae bajo el umbral (5 < 10)
        StockPorColor.PorColor segundo = r.getColores().get(1);
        assertThat(segundo.getNombre()).isEqualTo("NEGRO");
        assertThat(segundo.isBajo()).isTrue();
    }

    @Test
    void colorHexReconoceColoresComunesYNeutralParaDesconocidos() {
        assertThat(StockPorColor.colorHex("ROJO")).isEqualTo("#d32f2f");
        assertThat(StockPorColor.colorHex("V. BOTELLA")).isEqualTo("#0b6b3a");
        assertThat(StockPorColor.colorHex("NEGRO")).isEqualTo("#222831");
        // Nombre de fantasía sin palabra de color -> gris neutro (no inventa)
        assertThat(StockPorColor.colorHex("PPT")).isEqualTo("#b8c0cc");
        assertThat(StockPorColor.colorHex(null)).isEqualTo("#b8c0cc");
    }
}
