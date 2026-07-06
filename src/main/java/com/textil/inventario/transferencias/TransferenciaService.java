package com.textil.inventario.transferencias;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.inventario.*;
import com.textil.inventario.seguridad.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransferenciaService {

    private final TransferenciaRepository transferenciaRepository;
    private final TransferenciaDetalleRepository detalleRepository;
    private final TransferenciaDistribucionRepository distribucionRepository;
    private final ArticuloRepository articuloRepository;
    private final UsuarioRepository usuarioRepository;
    private final StockActualRepository stockActualRepository;
    private final KardexMovimientoRepository kardexRepository;
    private final UbicacionRepository ubicacionRepository;

    public List<Transferencia> listarTransferencias() {
        return transferenciaRepository.findAllByOrderByFechaSolicitudDesc();
    }

    public Transferencia buscarTransferencia(Long id) {
        return transferenciaRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Transferencia crearTransferencia(String observaciones) {
        Ubicacion praderas = ubicacionRepository.findByEsPrincipalTrue().orElseThrow();

        Transferencia t = new Transferencia();
        t.setNumero(generarNumero());
        t.setUbicacionOrigen(praderas);
        t.setUsuarioSolicita(usuarioRepository.findById(1L).orElseThrow());
        t.setFechaSolicitud(LocalDateTime.now());
        t.setObservaciones(observaciones);
        t.setEstado(Transferencia.EstadoTransferencia.BORRADOR);
        return transferenciaRepository.save(t);
    }

    private String generarNumero() {
        long total = transferenciaRepository.count();
        return String.format("TRF-%06d", total + 1);
    }

    @Transactional
    public TransferenciaDetalle agregarDetalle(Long transferenciaId, Long articuloId, Integer cantidadSolicitada, String observaciones) {
        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setTransferencia(transferenciaRepository.findById(transferenciaId).orElseThrow());
        d.setArticulo(articuloRepository.findById(articuloId).orElseThrow());
        d.setCantidadSolicitada(cantidadSolicitada);
        d.setObservaciones(observaciones);
        return detalleRepository.save(d);
    }

    @Transactional
    public void confirmarSalida(Long transferenciaId, List<Integer> cantidadesConfirmadas, List<String> observaciones) {
        Transferencia t = transferenciaRepository.findById(transferenciaId).orElseThrow();
        List<TransferenciaDetalle> detalles = detalleRepository.findByTransferenciaId(transferenciaId);

        for (int i = 0; i < detalles.size(); i++) {
            TransferenciaDetalle d = detalles.get(i);
            Integer cantidad = i < cantidadesConfirmadas.size() ? cantidadesConfirmadas.get(i) : d.getCantidadSolicitada();
            d.setCantidadConfirmadaSalida(cantidad);
            d.setObservaciones(i < observaciones.size() ? observaciones.get(i) : d.getObservaciones());
            detalleRepository.save(d);

            StockActual stockOrigen = stockActualRepository
                .findByArticuloIdAndUbicacionId(d.getArticulo().getId(), t.getUbicacionOrigen().getId())
                .orElseThrow(() -> new IllegalStateException("No hay stock registrado en Praderas para el artículo " + d.getArticulo().getId()));

            if (stockOrigen.getRollos() < cantidad) {
                throw new IllegalStateException("Stock insuficiente en Praderas para el artículo " + d.getArticulo().getId()
                    + ". Disponible: " + stockOrigen.getRollos() + ", solicitado: " + cantidad);
            }

            BigDecimal pesoPromedio = stockOrigen.getRollos() > 0
                ? stockOrigen.getPesoKg().divide(new BigDecimal(stockOrigen.getRollos()), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            BigDecimal pesoMovido = pesoPromedio.multiply(new BigDecimal(cantidad)).setScale(2, RoundingMode.HALF_UP);

            stockOrigen.setRollos(stockOrigen.getRollos() - cantidad);
            stockOrigen.setPesoKg(stockOrigen.getPesoKg().subtract(pesoMovido));
            stockActualRepository.save(stockOrigen);

            KardexMovimiento k = new KardexMovimiento();
            k.setArticulo(d.getArticulo());
            k.setUbicacionOrigen(t.getUbicacionOrigen());
            k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.TRANSFERENCIA_OUT);
            k.setRollos(cantidad);
            k.setPesoKg(pesoMovido);
            k.setUsuario(t.getUsuarioSolicita());
            k.setObservaciones("Salida transferencia " + t.getNumero());
            kardexRepository.save(k);
        }

        t.setUsuarioConfirmaSalida(usuarioRepository.findById(1L).orElseThrow());
        t.setFechaConfirmacionSalida(LocalDateTime.now());
        t.setEstado(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA);
        transferenciaRepository.save(t);
    }

    /**
     * Confirma la llegada repartiendo cada línea entre una o varias ubicaciones destino.
     * repartoPorDetalle: detalleId -> (ubicacionId -> cantidad)
     */
    @Transactional
    public void confirmarLlegada(Long transferenciaId, Map<Long, Map<Long, Integer>> repartoPorDetalle) {
        Transferencia t = transferenciaRepository.findById(transferenciaId).orElseThrow();
        List<TransferenciaDetalle> detalles = detalleRepository.findByTransferenciaId(transferenciaId);
        boolean tieneDiferencias = false;

        for (TransferenciaDetalle d : detalles) {
            Map<Long, Integer> reparto = repartoPorDetalle.getOrDefault(d.getId(), Map.of());

            int totalRepartido = reparto.values().stream().mapToInt(Integer::intValue).sum();
            d.setCantidadConfirmadaLlegada(totalRepartido);
            detalleRepository.save(d);

            if (!Integer.valueOf(totalRepartido).equals(d.getCantidadConfirmadaSalida())) {
                tieneDiferencias = true;
            }

            // Peso unitario de referencia según lo despachado en la salida de esta transferencia
            List<KardexMovimiento> salidas = kardexRepository.findAll();
            BigDecimal pesoUnitario = BigDecimal.ZERO;
            for (KardexMovimiento km : salidas) {
                if (km.getArticulo().getId().equals(d.getArticulo().getId())
                    && km.getTipoMovimiento() == KardexMovimiento.TipoMovimiento.TRANSFERENCIA_OUT
                    && km.getObservaciones() != null && km.getObservaciones().contains(t.getNumero())) {
                    pesoUnitario = km.getRollos() > 0 ? km.getPesoKg().divide(new BigDecimal(km.getRollos()), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    break;
                }
            }

            for (Map.Entry<Long, Integer> entry : reparto.entrySet()) {
                Long ubicacionId = entry.getKey();
                Integer cantidad = entry.getValue();
                if (cantidad == null || cantidad <= 0) continue;

                Ubicacion destino = ubicacionRepository.findById(ubicacionId).orElseThrow();
                BigDecimal pesoMovido = pesoUnitario.multiply(new BigDecimal(cantidad)).setScale(2, RoundingMode.HALF_UP);

                TransferenciaDistribucion dist = new TransferenciaDistribucion();
                dist.setTransferenciaDetalle(d);
                dist.setUbicacion(destino);
                dist.setRollos(cantidad);
                distribucionRepository.save(dist);

                StockActual stockDestino = stockActualRepository
                    .findByArticuloIdAndUbicacionId(d.getArticulo().getId(), destino.getId())
                    .orElseGet(() -> {
                        StockActual s = new StockActual();
                        s.setArticulo(d.getArticulo());
                        s.setUbicacion(destino);
                        s.setRollos(0);
                        s.setPesoKg(BigDecimal.ZERO);
                        return s;
                    });

                stockDestino.setRollos(stockDestino.getRollos() + cantidad);
                stockDestino.setPesoKg(stockDestino.getPesoKg().add(pesoMovido));
                stockActualRepository.save(stockDestino);

                KardexMovimiento k = new KardexMovimiento();
                k.setArticulo(d.getArticulo());
                k.setUbicacionDestino(destino);
                k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.TRANSFERENCIA_IN);
                k.setRollos(cantidad);
                k.setPesoKg(pesoMovido);
                k.setUsuario(t.getUsuarioSolicita());
                k.setObservaciones("Llegada transferencia " + t.getNumero());
                kardexRepository.save(k);
            }
        }

        t.setUsuarioConfirmaLlegada(usuarioRepository.findById(1L).orElseThrow());
        t.setFechaConfirmacionLlegada(LocalDateTime.now());
        t.setEstado(tieneDiferencias
            ? Transferencia.EstadoTransferencia.CON_DIFERENCIA
            : Transferencia.EstadoTransferencia.CONFIRMADA_LLEGADA);
        transferenciaRepository.save(t);
    }
}
