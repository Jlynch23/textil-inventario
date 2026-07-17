package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.recepciones.AnthropicOcrService;
import com.textil.inventario.recepciones.RecepcionService;
import com.textil.inventario.seguridad.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Primer test del proyecto (auditoria TEST-01, 17-jul-2026).
 * Cubre normalizarNumeroGuia(): funcion pura que compara numeros de guia
 * entre la guia (con ceros a la izquierda, ej. "TG01-00021376") y la
 * factura (sin ceros, ej. "TG01-21376"), ya demostro ser fuente de bugs
 * reales en produccion (vinculacion factura-guia, ver migracion V14).
 */
@ExtendWith(MockitoExtension.class)
class ArchivoHistoricoServiceTest {

    @Mock private DocumentoHistoricoRepository documentoHistoricoRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private TipoTelaRepository tipoTelaRepository;
    @Mock private TituloRepository tituloRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private ArticuloRepository articuloRepository;
    @Mock private AnthropicOcrService ocrService;
    @Mock private RecepcionService recepcionService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CatalogoService catalogoService;

    @InjectMocks
    private ArchivoHistoricoService service;

    @ParameterizedTest(name = "normalizarNumeroGuia(\"{0}\") = \"{1}\"")
    @CsvSource({
        "TG01-00021376, TG01-21376",   // caso real documentado en el comentario del metodo
        "TG01-21376,    TG01-21376",   // ya sin ceros: no debe cambiar
        "tg01-0021376,  TG01-21376",   // minusculas: debe normalizar a mayusculas tambien
        "F003-00037985, F003-37985",   // caso real de factura FAST DYE
        "ABC123,        ABC123",       // sin guion: se devuelve tal cual (en mayusculas)
    })
    void normalizarNumeroGuia_quitaCerosIzquierdaYUppercase(String entrada, String esperado) {
        assertThat(service.normalizarNumeroGuia(entrada)).isEqualTo(esperado);
    }

    @Test
    void normalizarNumeroGuia_null_devuelveVacio() {
        assertThat(service.normalizarNumeroGuia(null)).isEqualTo("");
    }
}
