package com.textil.inventario.transferencias;

import com.textil.inventario.auditoria.AuditLogService;
import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.Color;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.Ubicacion;
import com.textil.inventario.catalogo.UbicacionRepository;
import com.textil.inventario.inventario.KardexMovimientoRepository;
import com.textil.inventario.inventario.StockActual;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioActualService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * confirmarSalida/confirmarLlegada mueven stock real -- son el camino mas
 * critico de este servicio (equivalente a RecepcionServiceTest para
 * recepciones). Antes de esta clase, TransferenciaService no tenia ningun
 * test (ver auditoria: cobertura casi inexistente).
 */
@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    @Mock private TransferenciaRepository transferenciaRepository;
    @Mock private TransferenciaDetalleRepository detalleRepository;
    @Mock private TransferenciaDistribucionRepository distribucionRepository;
    @Mock private ArticuloRepository articuloRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private UsuarioActualService usuarioActualService;
    @Mock private StockActualRepository stockActualRepository;
    @Mock private KardexMovimientoRepository kardexRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private TransferenciaService service;

    private Ubicacion praderasDePrueba() {
        Ubicacion u = new Ubicacion();
        u.setId(1L);
        u.setNombre("Praderas");
        return u;
    }

    private Ubicacion tiendaDePrueba() {
        Ubicacion u = new Ubicacion();
        u.setId(2L);
        u.setNombre("Tienda Centro");
        return u;
    }

    private Articulo articuloDePrueba() {
        Articulo a = new Articulo();
        a.setId(10L);
        return a;
    }

    private Color colorDePrueba() {
        Color c = new Color();
        c.setId(20L);
        c.setNombreOficial("NEGRO");
        return c;
    }

    private Transferencia transferenciaDePrueba(Ubicacion origen) {
        Transferencia t = new Transferencia();
        t.setId(1L);
        t.setNumero("TR-000001");
        t.setUbicacionOrigen(origen);
        t.setUsuarioSolicita(new Usuario());
        t.setEstado(Transferencia.EstadoTransferencia.BORRADOR);
        return t;
    }

    private StockActual stockDePrueba(int rollos, String pesoKg) {
        StockActual s = new StockActual();
        s.setArticulo(articuloDePrueba());
        s.setColor(colorDePrueba());
        s.setRollos(rollos);
        s.setPesoKg(new BigDecimal(pesoKg));
        return s;
    }

    // --- confirmarSalida ---

    @Test
    void confirmarSalida_normal_descuentaStockYRegistraKardex() {
        Transferencia t = transferenciaDePrueba(praderasDePrueba());
        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setId(100L);
        d.setArticulo(articuloDePrueba());
        d.setColor(colorDePrueba());
        d.setCantidadSolicitada(10);
        StockActual stockOrigen = stockDePrueba(50, "500.00");

        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(d));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L))
                .thenReturn(Optional.of(stockOrigen));
        when(usuarioActualService.obtenerUsuarioActual()).thenReturn(new Usuario());

        service.confirmarSalida(1L, List.of(100L), List.of(10), List.of(""));

        ArgumentCaptor<StockActual> stockCap = ArgumentCaptor.forClass(StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getRollos()).isEqualTo(40);
        // pesoPromedio = 500.00 / 50 rollos = 10.00/rollo; pesoMovido = 10 rollos * 10.00 = 100.00
        // peso restante en origen = 500.00 - 100.00 = 400.00
        assertThat(stockCap.getValue().getPesoKg()).isEqualByComparingTo("400.00");

        assertThat(t.getEstado()).isEqualTo(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA);
    }

    @Test
    void confirmarSalida_cantidadNegativa_lanzaExcepcionYNoTocaStock() {
        Transferencia t = transferenciaDePrueba(praderasDePrueba());
        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setId(100L);
        d.setArticulo(articuloDePrueba());
        d.setColor(colorDePrueba());
        d.setCantidadSolicitada(10);

        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() ->
                service.confirmarSalida(1L, List.of(100L), List.of(-5), List.of(""))
        ).isInstanceOf(IllegalArgumentException.class);

        verify(stockActualRepository, never()).save(any());
    }

    @Test
    void confirmarSalida_stockInsuficiente_lanzaExcepcion() {
        Transferencia t = transferenciaDePrueba(praderasDePrueba());
        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setId(100L);
        d.setArticulo(articuloDePrueba());
        d.setColor(colorDePrueba());
        d.setCantidadSolicitada(10);
        StockActual stockOrigen = stockDePrueba(3, "30.00");

        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(d));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L))
                .thenReturn(Optional.of(stockOrigen));

        assertThatThrownBy(() ->
                service.confirmarSalida(1L, List.of(100L), List.of(10), List.of(""))
        ).isInstanceOf(IllegalStateException.class);

        verify(stockActualRepository, never()).save(any());
    }

    // --- confirmarLlegada ---

    @Test
    void confirmarLlegada_normal_sumaStockEnDestino() {
        Ubicacion praderas = praderasDePrueba();
        Ubicacion tienda = tiendaDePrueba();
        Transferencia t = transferenciaDePrueba(praderas);
        t.setEstado(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA); // precondicion: salida ya confirmada

        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setId(100L);
        d.setArticulo(articuloDePrueba());
        d.setColor(colorDePrueba());
        d.setCantidadSolicitada(10);
        d.setCantidadConfirmadaSalida(10);

        StockActual stockDestino = stockDePrueba(5, "50.00");

        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));
        when(detalleRepository.findByTransferenciaId(1L)).thenReturn(List.of(d));
        when(kardexRepository.findFirstByTransferenciaIdAndArticuloIdAndTipoMovimiento(
                eq(1L), eq(10L), any()))
                .thenReturn(Optional.empty());
        when(ubicacionRepository.findById(2L)).thenReturn(Optional.of(tienda));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 2L, 20L))
                .thenReturn(Optional.of(stockDestino));
        when(usuarioActualService.obtenerUsuarioActual()).thenReturn(new Usuario());

        service.confirmarLlegada(1L, Map.of(100L, Map.of(2L, 10)));

        ArgumentCaptor<StockActual> stockCap = ArgumentCaptor.forClass(StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getRollos()).isEqualTo(15);

        assertThat(t.getEstado()).isEqualTo(Transferencia.EstadoTransferencia.CONFIRMADA_LLEGADA);
    }

    @Test
    void confirmarLlegada_conDiferencia_marcaEstadoConDiferencia() {
        Ubicacion praderas = praderasDePrueba();
        Transferencia t = transferenciaDePrueba(praderas);
        t.setEstado(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA); // precondicion: salida ya confirmada

        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setId(100L);
        d.setArticulo(articuloDePrueba());
        d.setColor(colorDePrueba());
        d.setCantidadSolicitada(10);
        d.setCantidadConfirmadaSalida(10);

        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));
        when(detalleRepository.findByTransferenciaId(1L)).thenReturn(List.of(d));
        when(usuarioActualService.obtenerUsuarioActual()).thenReturn(new Usuario());

        // Reparto vacio para el detalle 100L: llega menos de lo confirmado en salida.
        service.confirmarLlegada(1L, Map.of());

        assertThat(t.getEstado()).isEqualTo(Transferencia.EstadoTransferencia.CON_DIFERENCIA);
        verify(stockActualRepository, never()).save(any());
    }

    // --- Idempotencia / guards de estado (auditoria P0-1) ---

    @Test
    void confirmarSalida_yaConfirmada_lanzaYNoTocaStock() {
        // C2: la salida solo se confirma desde BORRADOR; reenviar no descuenta 2 veces.
        Transferencia t = transferenciaDePrueba(praderasDePrueba());
        t.setEstado(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA);
        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() ->
                service.confirmarSalida(1L, List.of(100L), List.of(10), List.of("")))
                .isInstanceOf(IllegalStateException.class);

        verify(stockActualRepository, never()).save(any());
        verify(kardexRepository, never()).save(any());
    }

    @Test
    void confirmarLlegada_sinSalidaConfirmada_lanzaYNoTocaStock() {
        // C3: no se puede confirmar llegada de una transferencia en BORRADOR
        // (crearia stock en destino sin haberlo descontado del origen).
        Transferencia t = transferenciaDePrueba(praderasDePrueba()); // queda en BORRADOR
        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() ->
                service.confirmarLlegada(1L, Map.of(100L, Map.of(2L, 10))))
                .isInstanceOf(IllegalStateException.class);

        verify(stockActualRepository, never()).save(any());
    }

    @Test
    void confirmarLlegada_repartoMayorQueSalida_lanzaYNoTocaStock() {
        // C4: no se puede recibir mas de lo despachado en la salida.
        Transferencia t = transferenciaDePrueba(praderasDePrueba());
        t.setEstado(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA);

        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setId(100L);
        d.setArticulo(articuloDePrueba());
        d.setColor(colorDePrueba());
        d.setCantidadSolicitada(10);
        d.setCantidadConfirmadaSalida(10); // salieron 10

        when(transferenciaRepository.findById(1L)).thenReturn(Optional.of(t));
        when(detalleRepository.findByTransferenciaId(1L)).thenReturn(List.of(d));

        // Se reparten 12 (6 + 6) contra una salida de 10 -> debe rechazarse.
        assertThatThrownBy(() ->
                service.confirmarLlegada(1L, Map.of(100L, Map.of(2L, 6, 3L, 6))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(stockActualRepository, never()).save(any());
    }
}
