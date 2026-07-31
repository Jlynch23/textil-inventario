package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre detectarEmpresa(): funcion pura que decide a que empresa pertenece un
 * documento a partir de la ruta del ZIP o de la razon social leida por la IA.
 * El bug que motiva estos tests: las carpetas reales ("T. CLEMENTE") y las
 * razones sociales no calzaban con el slug de carpeta ("textil-clemente"), y
 * todo quedaba "(sin identificar)". Ahora se matchea por palabra distintiva.
 */
class DocumentoHistoricoClasificadorTest {

    private final DocumentoHistoricoClasificador clasificador = new DocumentoHistoricoClasificador();

    private Empresa empresa(long id, String nombre, String carpeta) {
        Empresa e = new Empresa();
        e.setId(id);
        e.setNombre(nombre);
        e.setCarpeta(carpeta);
        return e;
    }

    private List<Empresa> dosEmpresas() {
        return List.of(
                empresa(1, "TEXTIL LAURA", "textil-laura"),
                empresa(2, "TEXTIL CLEMENTE", "textil-clemente"));
    }

    @Test
    void carpetaAbreviada_matcheaPorPalabraDistintiva() {
        // Carpeta real dentro del ZIP: no contiene el slug "textil-clemente".
        Empresa e = clasificador.detectarEmpresa("FACTURAS/T. CLEMENTE/2024/TG01-00022836.pdf", dosEmpresas());
        assertThat(e).isNotNull();
        assertThat(e.getNombre()).isEqualTo("TEXTIL CLEMENTE");
    }

    @Test
    void razonSocialConSufijo_matcheaPorNombreCompleto() {
        Empresa e = clasificador.detectarEmpresa("TEXTIL CLEMENTE S.A.C.", dosEmpresas());
        assertThat(e).isNotNull();
        assertThat(e.getNombre()).isEqualTo("TEXTIL CLEMENTE");
    }

    @Test
    void soloApellidoEnMayusculas_matchea() {
        Empresa e = clasificador.detectarEmpresa("GUIA DE REMISION - CLIENTE: LAURA", dosEmpresas());
        assertThat(e).isNotNull();
        assertThat(e.getNombre()).isEqualTo("TEXTIL LAURA");
    }

    @Test
    void slugDeCarpetaExacto_matchea() {
        Empresa e = clasificador.detectarEmpresa("historico/textil-laura/archivo.pdf", dosEmpresas());
        assertThat(e).isNotNull();
        assertThat(e.getNombre()).isEqualTo("TEXTIL LAURA");
    }

    @Test
    void sinPalabraDistintiva_devuelveNull() {
        // "TEXTIL" es generica (la comparten las dos): no alcanza para decidir.
        assertThat(clasificador.detectarEmpresa("FACTURAS/TEXTIL/2024/x.pdf", dosEmpresas())).isNull();
    }

    @Test
    void textoQueMencionaAmbas_esAmbiguo_devuelveNull() {
        assertThat(clasificador.detectarEmpresa("TRASPASO LAURA A CLEMENTE", dosEmpresas())).isNull();
    }

    @Test
    void textoVacioONull_devuelveNull() {
        assertThat(clasificador.detectarEmpresa(null, dosEmpresas())).isNull();
        assertThat(clasificador.detectarEmpresa("   ", dosEmpresas())).isNull();
    }
}
