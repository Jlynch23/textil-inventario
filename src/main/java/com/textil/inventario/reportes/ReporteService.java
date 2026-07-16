package com.textil.inventario.reportes;

import com.textil.inventario.auditoria.LogEvento;
import com.textil.inventario.auditoria.LogEventoRepository;
import com.textil.inventario.catalogo.*;
import com.textil.inventario.inventario.KardexMovimiento;
import com.textil.inventario.inventario.KardexMovimientoRepository;
import com.textil.inventario.inventario.StockActual;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.recepciones.RecepcionRepository;
import com.textil.inventario.transferencias.Transferencia;
import com.textil.inventario.transferencias.TransferenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Concentra el acceso a datos de todos los dominios que necesita el modulo
 * de Reportes (stock, kardex, recepciones, transferencias, catalogo,
 * auditoria de errores). Es normal y esperado que un servicio de reportes
 * toque muchos dominios distintos para agregacion de solo lectura; lo que
 * se corrige aqui (deuda menor detectada en la auditoria de arquitectura)
 * es que esos repositorios vivian directamente en el @Controller en vez
 * de en una capa de Service.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final String ACCION_ERROR_SISTEMA = "ERROR_SISTEMA";

    private final StockActualRepository stockActualRepository;
    private final KardexMovimientoRepository kardexMovimientoRepository;
    private final RecepcionRepository recepcionRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final EmpresaRepository empresaRepository;
    private final LogEventoRepository logEventoRepository;

    // ---------- CATALOGOS PARA FILTROS ----------

    public List<Ubicacion> listarUbicacionesActivas() {
        return ubicacionRepository.findByActivoTrue();
    }

    public List<TipoTela> listarTiposTelaActivos() {
        return tipoTelaRepository.findByActivoTrue();
    }

    public List<Empresa> listarEmpresasActivas() {
        return empresaRepository.findByActivoTrue();
    }

    public Transferencia.EstadoTransferencia[] estadosTransferencia() {
        return Transferencia.EstadoTransferencia.values();
    }

    // ---------- STOCK POR UBICACION ----------

    public List<StockActual> filtrarStock(Long ubicacionId, Long tipoTelaId) {
        List<StockActual> stock = stockActualRepository.findStockDisponible();
        if (ubicacionId != null) {
            stock = stock.stream().filter(s -> s.getUbicacion().getId().equals(ubicacionId)).toList();
        }
        if (tipoTelaId != null) {
            stock = stock.stream().filter(s -> s.getArticulo().getTipoTela().getId().equals(tipoTelaId)).toList();
        }
        return stock;
    }

    // ---------- KARDEX POR RANGO DE FECHAS ----------

    public List<KardexMovimiento> filtrarKardex(LocalDate desde, LocalDate hasta) {
        List<KardexMovimiento> movimientos = kardexMovimientoRepository.findAllByOrderByFechaDesc();
        if (desde != null) {
            movimientos = movimientos.stream()
                    .filter(m -> !m.getFecha().toLocalDate().isBefore(desde))
                    .toList();
        }
        if (hasta != null) {
            movimientos = movimientos.stream()
                    .filter(m -> !m.getFecha().toLocalDate().isAfter(hasta))
                    .toList();
        }
        return movimientos;
    }

    // ---------- RECEPCIONES POR PROVEEDOR / FECHA ----------

    public List<Recepcion> filtrarRecepciones(Long empresaId, LocalDate desde, LocalDate hasta) {
        List<Recepcion> recepciones = recepcionRepository.findAllByOrderByCreatedAtDesc();
        if (empresaId != null) {
            recepciones = recepciones.stream().filter(r -> r.getEmpresa().getId().equals(empresaId)).toList();
        }
        if (desde != null) {
            recepciones = recepciones.stream().filter(r -> !r.getFechaGuia().isBefore(desde)).toList();
        }
        if (hasta != null) {
            recepciones = recepciones.stream().filter(r -> !r.getFechaGuia().isAfter(hasta)).toList();
        }
        return recepciones;
    }

    // ---------- TRANSFERENCIAS ENTRE UBICACIONES ----------

    public List<Transferencia> filtrarTransferencias(Long ubicacionOrigenId, Transferencia.EstadoTransferencia estado,
                                                       LocalDate desde, LocalDate hasta) {
        List<Transferencia> transferencias = transferenciaRepository.findAllByOrderByFechaSolicitudDesc();
        if (ubicacionOrigenId != null) {
            transferencias = transferencias.stream().filter(t -> t.getUbicacionOrigen().getId().equals(ubicacionOrigenId)).toList();
        }
        if (estado != null) {
            transferencias = transferencias.stream().filter(t -> t.getEstado() == estado).toList();
        }
        if (desde != null) {
            transferencias = transferencias.stream().filter(t -> !t.getFechaSolicitud().toLocalDate().isBefore(desde)).toList();
        }
        if (hasta != null) {
            transferencias = transferencias.stream().filter(t -> !t.getFechaSolicitud().toLocalDate().isAfter(hasta)).toList();
        }
        return transferencias;
    }

    // ---------- STOCK BAJO / CRITICO ----------

    public Map<String, Integer> articulosStockBajo(int umbral) {
        List<StockActual> stockDisponible = stockActualRepository.findStockDisponible();
        Map<String, Integer> totalPorArticulo = stockDisponible.stream()
                .collect(Collectors.groupingBy(
                        s -> descripcionArticulo(s.getArticulo()),
                        java.util.LinkedHashMap::new,
                        Collectors.summingInt(StockActual::getRollos)
                ));
        return totalPorArticulo.entrySet().stream()
                .filter(e -> e.getValue() < umbral)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    public String descripcionArticulo(Articulo a) {
        return a.getTipoTela().getNombre() + " - " + a.getTitulo().getValor() + " - " + a.getColor().getNombreOficial();
    }

    // ---------- ERRORES DEL SISTEMA ----------

    public List<LogEvento> erroresSistema() {
        return logEventoRepository.findByAccionOrderByCreatedAtDesc(ACCION_ERROR_SISTEMA);
    }
}
