package com.textil.inventario.recepciones;

import com.textil.inventario.auditoria.AuditLogService;
import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.EmpresaRepository;
import com.textil.inventario.inventario.KardexMovimientoRepository;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import com.textil.inventario.seguridad.UsuarioActualService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecepcionServiceTest {

    @Mock private RecepcionRepository recepcionRepository;
    @Mock private RecepcionDetalleRepository detalleRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private ArticuloRepository articuloRepository;
    @Mock private UsuarioActualService usuarioActualService;
    @Mock private StockActualRepository stockActualRepository;
    @Mock private KardexMovimientoRepository kardexRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private ProgramaRepository programaRepository;
    @Mock private ProgramaDetalleRepository programaDetalleRepository;
    @Mock private RecepcionDocumentoRepository recepcionDocumentoRepository;
    @Mock private DocumentoStorageService documentoStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private RecepcionService service;

    @Test
    void buscarProgramaNormalizado_matchExacto_noReintentaSinCeros() {
        Programa programa = new Programa();
        when(programaRepository.findByNumero("534")).thenReturn(Optional.of(programa));

        Optional<Programa> resultado = service.buscarProgramaNormalizado("534");

        assertThat(resultado).contains(programa);
        verify(programaRepository, never()).findByNumero("0534");
    }

    @Test
    void buscarProgramaNormalizado_conCerosIzquierda_reintentaSinCeros() {
        Programa programa = new Programa();
        when(programaRepository.findByNumero("0534")).thenReturn(Optional.empty());
        when(programaRepository.findByNumero("534")).thenReturn(Optional.of(programa));

        Optional<Programa> resultado = service.buscarProgramaNormalizado("0534");

        assertThat(resultado).contains(programa);
        verify(programaRepository).findByNumero("0534");
        verify(programaRepository).findByNumero("534");
    }

    @Test
    void buscarProgramaNormalizado_sinMatchNiConCerosNiSin_devuelveVacio() {
        when(programaRepository.findByNumero("0534")).thenReturn(Optional.empty());
        when(programaRepository.findByNumero("534")).thenReturn(Optional.empty());

        Optional<Programa> resultado = service.buscarProgramaNormalizado("0534");

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscarProgramaNormalizado_sinCerosYSinMatch_noReintentaDeNuevo() {
        when(programaRepository.findByNumero("534")).thenReturn(Optional.empty());

        Optional<Programa> resultado = service.buscarProgramaNormalizado("534");

        assertThat(resultado).isEmpty();
        verify(programaRepository, times(1)).findByNumero(anyString());
    }

    @Test
    void buscarProgramaNormalizado_recortaEspacios() {
        Programa programa = new Programa();
        when(programaRepository.findByNumero("534")).thenReturn(Optional.of(programa));

        Optional<Programa> resultado = service.buscarProgramaNormalizado("  534  ");

        assertThat(resultado).contains(programa);
    }
}
