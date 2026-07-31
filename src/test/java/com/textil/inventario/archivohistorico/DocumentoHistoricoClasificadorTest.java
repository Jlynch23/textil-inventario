package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.recepciones.ArticuloMatchingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre detectarEmpresa(): decide a que empresa pertenece un documento a partir
 * de la ruta del ZIP o de la razon social leida por la IA. Delega en el MISMO
 * matcher que la recepcion individual (ArticuloMatchingService.matchEmpresa),
 * que cuenta palabras (>3 letras) del nombre presentes en el texto. El bug que
 * motiva estos tests: las carpetas reales ("T. CLEMENTE") no calzaban con el
 * slug ("textil-clemente") y todo quedaba "(sin identificar)".
 *
 * matchEmpresa solo usa la lista de empresas (no toca repositorios), por eso se
 * puede instanciar el service con dependencias nulas para este test.
 */
class DocumentoHistoricoClasificadorTest {

    private final DocumentoHistoricoClasificador clasificador =
            new DocumentoHistoricoClasificador(
                    new ArticuloMatchingService(null, null, null, null, null, null));

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
    void carpetaAbreviada_matcheaPorPalabra() {
        // Carpeta real dentro del ZIP: no contiene el slug "textil-clemente",
        // pero si la palabra "CLEMENTE".
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
    void slugDeCarpeta_matchea() {
        Empresa e = clasificador.detectarEmpresa("historico/textil-laura/archivo.pdf", dosEmpresas());
        assertThat(e).isNotNull();
        assertThat(e.getNombre()).isEqualTo("TEXTIL LAURA");
    }

    @Test
    void textoVacioONull_devuelveNull() {
        assertThat(clasificador.detectarEmpresa(null, dosEmpresas())).isNull();
        assertThat(clasificador.detectarEmpresa("   ", dosEmpresas())).isNull();
    }
}
