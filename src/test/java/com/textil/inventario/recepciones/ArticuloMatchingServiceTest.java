package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * matchLinea() es el corazon del flujo de OCR: decide si una linea leida de
 * un PDF por la IA se puede vincular a un Articulo existente o no, y por
 * que. Antes de esta clase no tenia ningun test (ver auditoria).
 */
@ExtendWith(MockitoExtension.class)
class ArticuloMatchingServiceTest {

    @Mock private TipoTelaRepository tipoTelaRepository;
    @Mock private TituloRepository tituloRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private ComposicionRepository composicionRepository;
    @Mock private ArticuloRepository articuloRepository;
    @Mock private CatalogoService catalogoService;

    @InjectMocks
    private ArticuloMatchingService service;

    private ProductoExtraido productoCompleto() {
        return new ProductoExtraido("RIB 2X1", "30/1", "ALGODON", "ACANALADO",
                "631085", "COCOA LOLA", "627", 18, new BigDecimal("400.15"));
    }

    private TipoTela tipoTela() {
        TipoTela t = new TipoTela();
        t.setId(1L);
        t.setNombre("RIB 2X1");
        return t;
    }

    private Titulo titulo() {
        Titulo t = new Titulo();
        t.setId(2L);
        t.setValor("30/1");
        return t;
    }

    private Composicion composicion() {
        Composicion c = new Composicion();
        c.setId(3L);
        c.setNombre("ALGODON");
        return c;
    }

    private Color color() {
        Color c = new Color();
        c.setId(4L);
        c.setNombreOficial("COCOA LOLA");
        return c;
    }

    private Acabado acabado() {
        Acabado a = new Acabado();
        a.setId(5L);
        a.setNombre("ACANALADO");
        return a;
    }

    private Articulo articulo() {
        Articulo a = new Articulo();
        a.setId(99L);
        return a;
    }

    @Test
    void matchLinea_datosBasicosFaltantes_noMatchea() {
        ProductoExtraido p = new ProductoExtraido(null, "30/1", "ALGODON", "ACANALADO",
                "631085", "COCOA LOLA", "627", 18, new BigDecimal("400.15"));

        LineaSugerida resultado = service.matchLinea(p);

        assertThat(resultado.matched()).isFalse();
        assertThat(resultado.motivoNoMatch()).contains("Faltan datos");
        verifyNoInteractions(tipoTelaRepository);
    }

    @Test
    void matchLinea_tipoTelaNoExiste_noMatchea() {
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.empty());

        LineaSugerida resultado = service.matchLinea(productoCompleto());

        assertThat(resultado.matched()).isFalse();
        assertThat(resultado.motivoNoMatch()).contains("Tipo de tela");
        verifyNoInteractions(tituloRepository);
    }

    @Test
    void matchLinea_tituloNoExiste_noMatchea() {
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.of(tipoTela()));
        when(tituloRepository.findByValorIgnoreCase("30/1")).thenReturn(Optional.empty());

        LineaSugerida resultado = service.matchLinea(productoCompleto());

        assertThat(resultado.matched()).isFalse();
        assertThat(resultado.motivoNoMatch()).contains("Título");
    }

    @Test
    void matchLinea_composicionEnBlanco_noMatchea() {
        ProductoExtraido p = new ProductoExtraido("RIB 2X1", "30/1", "  ", "ACANALADO",
                "631085", "COCOA LOLA", "627", 18, new BigDecimal("400.15"));
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.of(tipoTela()));
        when(tituloRepository.findByValorIgnoreCase("30/1")).thenReturn(Optional.of(titulo()));

        LineaSugerida resultado = service.matchLinea(p);

        assertThat(resultado.matched()).isFalse();
        assertThat(resultado.motivoNoMatch()).contains("composición");
        verifyNoInteractions(composicionRepository);
    }

    @Test
    void matchLinea_composicionNoExiste_noMatchea() {
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.of(tipoTela()));
        when(tituloRepository.findByValorIgnoreCase("30/1")).thenReturn(Optional.of(titulo()));
        when(composicionRepository.findByNombreIgnoreCase("ALGODON")).thenReturn(Optional.empty());

        LineaSugerida resultado = service.matchLinea(productoCompleto());

        assertThat(resultado.matched()).isFalse();
        assertThat(resultado.motivoNoMatch()).contains("Composición");
    }

    @Test
    void matchLinea_colorNoExiste_noMatchea() {
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.of(tipoTela()));
        when(tituloRepository.findByValorIgnoreCase("30/1")).thenReturn(Optional.of(titulo()));
        when(composicionRepository.findByNombreIgnoreCase("ALGODON")).thenReturn(Optional.of(composicion()));
        when(catalogoService.resolverColorPorCodigo("631085", "COCOA LOLA")).thenReturn(Optional.empty());

        LineaSugerida resultado = service.matchLinea(productoCompleto());

        assertThat(resultado.matched()).isFalse();
        assertThat(resultado.motivoNoMatch()).contains("Color código");
    }

    @Test
    void matchLinea_acabadoEnBlanco_usaLisoPorDefecto() {
        ProductoExtraido p = new ProductoExtraido("RIB 2X1", "30/1", "ALGODON", null,
                "631085", "COCOA LOLA", "627", 18, new BigDecimal("400.15"));
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.of(tipoTela()));
        when(tituloRepository.findByValorIgnoreCase("30/1")).thenReturn(Optional.of(titulo()));
        when(composicionRepository.findByNombreIgnoreCase("ALGODON")).thenReturn(Optional.of(composicion()));
        when(catalogoService.resolverColorPorCodigo("631085", "COCOA LOLA")).thenReturn(Optional.of(color()));
        Acabado liso = new Acabado();
        liso.setId(6L);
        liso.setNombre("LISO");
        when(catalogoService.buscarAcabadoPorNombre("LISO")).thenReturn(Optional.of(liso));
        when(articuloRepository.findByTipoTelaIdAndTituloIdAndComposicionIdAndAcabadoId(1L, 2L, 3L, 6L))
                .thenReturn(Optional.of(articulo()));

        LineaSugerida resultado = service.matchLinea(p);

        assertThat(resultado.matched()).isTrue();
        verify(catalogoService).buscarAcabadoPorNombre("LISO");
    }

    @Test
    void matchLinea_combinacionArticuloNoRegistrada_noMatchea() {
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.of(tipoTela()));
        when(tituloRepository.findByValorIgnoreCase("30/1")).thenReturn(Optional.of(titulo()));
        when(composicionRepository.findByNombreIgnoreCase("ALGODON")).thenReturn(Optional.of(composicion()));
        when(catalogoService.resolverColorPorCodigo("631085", "COCOA LOLA")).thenReturn(Optional.of(color()));
        when(catalogoService.buscarAcabadoPorNombre("ACANALADO")).thenReturn(Optional.of(acabado()));
        when(articuloRepository.findByTipoTelaIdAndTituloIdAndComposicionIdAndAcabadoId(1L, 2L, 3L, 5L))
                .thenReturn(Optional.empty());

        LineaSugerida resultado = service.matchLinea(productoCompleto());

        assertThat(resultado.matched()).isFalse();
        assertThat(resultado.motivoNoMatch()).contains("no está registrada como artículo");
        // El color SI se resolvio -- debe conservarse en la sugerencia para que
        // la UI pueda mostrarlo aunque el articulo final no matcheara.
        assertThat(resultado.colorId()).isEqualTo(4L);
    }

    @Test
    void matchLinea_todoExiste_matcheaCompleto() {
        when(tipoTelaRepository.findByNombreIgnoreCase("RIB 2X1")).thenReturn(Optional.of(tipoTela()));
        when(tituloRepository.findByValorIgnoreCase("30/1")).thenReturn(Optional.of(titulo()));
        when(composicionRepository.findByNombreIgnoreCase("ALGODON")).thenReturn(Optional.of(composicion()));
        when(catalogoService.resolverColorPorCodigo("631085", "COCOA LOLA")).thenReturn(Optional.of(color()));
        when(catalogoService.buscarAcabadoPorNombre("ACANALADO")).thenReturn(Optional.of(acabado()));
        when(articuloRepository.findByTipoTelaIdAndTituloIdAndComposicionIdAndAcabadoId(1L, 2L, 3L, 5L))
                .thenReturn(Optional.of(articulo()));

        LineaSugerida resultado = service.matchLinea(productoCompleto());

        assertThat(resultado.matched()).isTrue();
        assertThat(resultado.articuloId()).isEqualTo(99L);
        assertThat(resultado.colorId()).isEqualTo(4L);
        assertThat(resultado.motivoNoMatch()).isNull();
    }

    // --- matchEmpresa ---

    private Empresa empresa(long id, String nombre) {
        Empresa e = new Empresa();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    private Empresa empresaConRuc(long id, String nombre, String ruc) {
        Empresa e = empresa(id, nombre);
        e.setRuc(ruc);
        return e;
    }

    @Test
    void matchEmpresa_razonSocialVacia_devuelveNull() {
        assertThat(service.matchEmpresa("", List.of(empresa(1L, "TEXTIL LAURA")))).isNull();
        assertThat(service.matchEmpresa(null, List.of(empresa(1L, "TEXTIL LAURA")))).isNull();
    }

    @Test
    void matchEmpresa_masPalabrasEnComun_ganaSobreLaDeMenosCoincidencias() {
        // "LAURA" (score 1) vs "CLEMENTE PALMIRA" (score 2, ambas palabras
        // >3 caracteres aparecen en el texto detectado) -- debe ganar Clemente.
        Empresa laura = empresa(1L, "TEXTIL LAURA");
        Empresa clemente = empresa(2L, "TEXTIL CLEMENTE PALMIRA");

        Long resultado = service.matchEmpresa("CLEMENTE AROTINGO LAURA PALMIRA", List.of(laura, clemente));

        assertThat(resultado).isEqualTo(2L);
    }

    @Test
    void matchEmpresa_empateDeScore_noSugiereNinguna() {
        // Ambas tienen score 1 ("LAURA" vs "CLEMENTE"): el documento no permite
        // distinguirlas. Antes ganaba la primera de la lista, o sea el azar del
        // orden, y la recepcion podia quedar imputada a la empresa equivocada.
        // Ahora no se sugiere ninguna y el usuario elige.
        Empresa laura = empresa(1L, "TEXTIL LAURA");
        Empresa clemente = empresa(2L, "TEXTIL CLEMENTE");

        Long resultado = service.matchEmpresa("CLEMENTE AROTINGO LAURA PALMIRA", List.of(laura, clemente));

        assertThat(resultado).isNull();
    }

    @Test
    void matchEmpresa_palabraGenericaCompartida_noAlcanzaParaSugerir() {
        // El caso real que rompia: "TEXTIL" esta en el nombre de las dos
        // empresas, asi que contarla las empataba a ambas contra CUALQUIER guia
        // que dijera "TEXTIL" y se elegia una al azar. Las palabras genericas
        // del rubro ya no puntuan.
        Empresa laura = empresa(1L, "TEXTIL LAURA");
        Empresa clemente = empresa(2L, "TEXTIL CLEMENTE");

        Long resultado = service.matchEmpresa("TEXTIL SAC", List.of(laura, clemente));

        assertThat(resultado).isNull();
    }

    @Test
    void matchEmpresa_porRuc_identificaAunqueElNombreNoCoincida() {
        // El RUC es unico por empresa: alcanza por si solo, incluso cuando la
        // razon social del documento esta abreviada o escrita distinto.
        Empresa laura = empresaConRuc(1L, "TEXTIL LAURA E.I.R.L.", "20549819028");
        Empresa clemente = empresaConRuc(2L, "TEXTIL CLEMENTE E.I.R.L.", "20549819029");

        Long resultado = service.matchEmpresa("20549819029", "T. CLEM", List.of(laura, clemente));

        assertThat(resultado).isEqualTo(2L);
    }

    @Test
    void matchEmpresa_rucConGuionesYEspacios_seNormaliza() {
        Empresa laura = empresaConRuc(1L, "TEXTIL LAURA", "20549819028");

        assertThat(service.matchEmpresa("20-54981902-8", null, List.of(laura))).isEqualTo(1L);
        assertThat(service.matchEmpresa(" 20549819028 ", null, List.of(laura))).isEqualTo(1L);
    }

    @Test
    void matchEmpresa_rucDesconocido_caeAlNombre() {
        // Un RUC que no esta en el catalogo no debe descartar la deteccion: se
        // usa la razon social como respaldo.
        Empresa laura = empresaConRuc(1L, "TEXTIL LAURA", "20549819028");

        Long resultado = service.matchEmpresa("20999999999", "GUIA PARA LAURA", List.of(laura));

        assertThat(resultado).isEqualTo(1L);
    }

    @Test
    void matchEmpresa_sinNingunaCoincidencia_devuelveNull() {
        Empresa laura = empresa(1L, "TEXTIL LAURA EIRL");

        Long resultado = service.matchEmpresa("PROVEEDOR TOTALMENTE DISTINTO SAC", List.of(laura));

        assertThat(resultado).isNull();
    }

    @Test
    void matchEmpresa_soloPalabrasCortas_noCuentanParaElScore() {
        // "SAC" y "EIRL" tienen <=3 y >3 caracteres respectivamente; "SAC" (3
        // letras) NO cuenta segun la regla (> 3), asi que sin otra palabra en
        // comun no deberia matchear.
        Empresa empresa = empresa(1L, "ABC SAC");

        Long resultado = service.matchEmpresa("ALGO CON SAC ADENTRO", List.of(empresa));

        assertThat(resultado).isNull();
    }
}
