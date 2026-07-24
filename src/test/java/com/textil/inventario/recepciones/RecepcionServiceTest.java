package com.textil.inventario.recepciones;

import com.textil.inventario.auditoria.AuditLogService;
import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.Color;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.EmpresaRepository;
import com.textil.inventario.inventario.KardexMovimientoRepository;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import com.textil.inventario.seguridad.UsuarioActualService;
import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.catalogo.Ubicacion;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecepcionServiceTest {

    @Mock private RecepcionRepository recepcionRepository;
    @Mock private RecepcionDetalleRepository detalleRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private ArticuloRepository articuloRepository;
    @Mock private ColorRepository colorRepository;
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

    // --- confirmarRecepcion(): camino critico de negocio (mueve stock real) ---

    private Recepcion recepcionDePrueba() {
        Recepcion r = new Recepcion();
        r.setId(1L);
        r.setNumeroGuia("TG01-00021376");
        r.setEstado(Recepcion.EstadoRecepcion.PENDIENTE);
        return r;
    }

    private Ubicacion praderasDePrueba() {
        Ubicacion u = new Ubicacion();
        u.setId(1L);
        u.setNombre("Praderas");
        return u;
    }

    private Articulo articuloDePrueba() {
        Articulo a = new Articulo();
        a.setId(10L);
        return a;
    }

    // El Color ya no vive en Articulo (ver V26): cada RecepcionDetalle/StockActual
    // de prueba necesita su propio Color, igual que en el diseño real.
    private Color colorDePrueba() {
        Color c = new Color();
        c.setId(20L);
        c.setNombreOficial("NEGRO");
        return c;
    }

    @Test
    void confirmarRecepcion_sinDiferencias_calculaPesoCompletoYActualizaStockYKardex() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(14);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L)).thenReturn(Optional.empty());

        service.confirmarRecepcion(1L, List.of(100L), List.of(14), List.of(""));

        ArgumentCaptor<com.textil.inventario.inventario.StockActual> stockCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getRollos()).isEqualTo(14);
        assertThat(stockCap.getValue().getPesoKg()).isEqualByComparingTo("300.00");

        ArgumentCaptor<com.textil.inventario.inventario.KardexMovimiento> kardexCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.KardexMovimiento.class);
        verify(kardexRepository).save(kardexCap.capture());
        assertThat(kardexCap.getValue().getRollos()).isEqualTo(14);
        assertThat(kardexCap.getValue().getPesoKg()).isEqualByComparingTo("300.00");

        assertThat(recepcion.getEstado()).isEqualTo(Recepcion.EstadoRecepcion.CONFIRMADA);
        verify(recepcionRepository).save(recepcion);
    }

    @Test
    void confirmarRecepcion_conFaltante_prorrateaPesoYMarcaConDiferencias() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(14);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L)).thenReturn(Optional.empty());

        service.confirmarRecepcion(1L, List.of(100L), List.of(13), List.of("Falto 1 rollo"));

        ArgumentCaptor<com.textil.inventario.inventario.StockActual> stockCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getRollos()).isEqualTo(13);
        assertThat(stockCap.getValue().getPesoKg()).isEqualByComparingTo("278.57");

        assertThat(recepcion.getEstado()).isEqualTo(Recepcion.EstadoRecepcion.CON_DIFERENCIAS);
    }

    @Test
    void confirmarRecepcion_rollosGuiaCero_noLanzaExcepcion_pesoQuedaEnCero() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(0);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L)).thenReturn(Optional.empty());

        assertThatCode(() ->
            service.confirmarRecepcion(1L, List.of(100L), List.of(0), List.of("OCR fallido, revisar"))
        ).doesNotThrowAnyException();

        ArgumentCaptor<com.textil.inventario.inventario.StockActual> stockCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getPesoKg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void confirmarRecepcion_rollosNegativos_lanzaExcepcionYNoTocaStock() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(14);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.confirmarRecepcion(1L, List.of(100L), List.of(-3), List.of(""))
        ).isInstanceOf(IllegalArgumentException.class);

        verify(stockActualRepository, never()).save(any());
    }

    @Test
    void confirmarRecepcion_yaConfirmada_lanzaYNoTocaStock() {
        // Idempotencia (auditoria P0-1, C1): una recepcion ya confirmada no se
        // vuelve a confirmar; un doble-click no debe re-sumar el stock ni duplicar
        // el kardex.
        Recepcion recepcion = recepcionDePrueba();
        recepcion.setEstado(Recepcion.EstadoRecepcion.CONFIRMADA);
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.confirmarRecepcion(1L, List.of(100L), List.of(14), List.of(""))
        ).isInstanceOf(IllegalStateException.class);

        verify(stockActualRepository, never()).save(any());
        verify(kardexRepository, never()).save(any());
    }
}
