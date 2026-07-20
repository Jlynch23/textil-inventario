package com.textil.inventario.transferencias;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.inventario.*;
import com.textil.inventario.seguridad.UsuarioActualService;
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
    private final ColorRepository colorRepository;
    private final UsuarioActualService usuarioActualService;
    private final StockActualRepository stockActualRepository;
    private final KardexMovimientoRepository kardexRepository;
    private final UbicacionRepository ubicacionRepository;
    private final com.textil.inventario.auditoria.AuditLogService auditLogService;

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
        t.setUsuarioSolicita(usuarioActualService.obtenerUsuarioActual());
        t.setFechaSolicitud(LocalDateTime.now());
        t.setObservaciones(observaciones);
        t.setEstado(Transferencia.EstadoTransferencia.BORRADOR);
        Transferencia guardada = transferenciaRepository.save(t);
        auditLogService.registrar("CREAR", "Transferencia", guardada.getId(),
                "Creo transferencia " + guardada.getNumero());
        return guardada;
    }

    private String generarNumero() {
        long total = transferenciaRepository.count();
        return String.format("TRF-%06d", total + 1);
    }

    @Transactional
    public TransferenciaDetalle agregarDetalle(Long transferenciaId, Long articuloId, Long colorId,
                                                Integer cantidadSolicitada, String observaciones) {
        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setTransferencia(transferenciaRepository.findById(transferenciaId).orElseThrow());
        d.setArticulo(articuloRepository.findById(articuloId).orElseThrow());
        d.setColor(colorRepository.findById(colorId).orElseThrow());
        d.setCantidadSolicitada(cantidadSolicitada);
        d.setObservaciones(observaciones);
        return detalleRepository.save(d);
    }

    @Transactional
    public void confirmarSalida(Long transferenciaId, List<Long> detalleIds,
                                 List<Integer> cantidadesConfirmadas, List<String> observaciones) {
        Transferencia t = transferenciaRepository.findById(transferenciaId).orElseThrow();

        for (int i = 0; i < detalleIds.size(); i++) {
            TransferenciaDetalle d = detalleRepository.findById(detalleIds.get(i)).orElseThrow();
            Integer cantidad = i < cantidadesConfirmadas.size() ? cantidadesConfirmadas.get(i) : d.getCantidadSolicitada();
            d.setCantidadConfirmadaSalida(cantidad);
            d.setObservaciones(i < observaciones.size() ? observaciones.get(i) : d.getObservaciones());
            detalleRepository.save(d);

            StockActual stockOrigen = stockActualRepository
                .findByArticuloIdAndUbicacionIdAndColorId(d.getArticulo().getId(), t.getUbicacionOrigen().getId(), d.getColor().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No hay stock registrado en Praderas para el artículo " + d.getArticulo().getId()
                        + " / color " + d.getColor().getId()));

            if (stockOrigen.getRollos() < cantidad) {
                throw new IllegalStateException("Stock insuficiente en Praderas para el artículo " + d.getArticulo().getId()
                    + " / color " + d.getColor().getId()
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
            k.setColor(d.getColor());
            k.setUbicacionOrigen(t.getUbicacionOrigen());
            k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.TRANSFERENCIA_OUT);
            k.setRollos(cantidad);
            k.setPesoKg(pesoMovido);
            k.setTransferenciaId(t.getId());
            k.setUsuario(t.getUsuarioSolicita());
            k.setObservaciones("Salida transferencia " + t.getNumero());
            kardexRepository.save(k);
        }

        t.setUsuarioConfirmaSalida(usuarioActualService.obtenerUsuarioActual());
        t.setFechaConfirmacionSalida(LocalDateTime.now());
        t.setEstado(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA);
        transferenciaRepository.save(t);
        auditLogService.registrar("CONFIRMAR", "Transferencia", t.getId(),
                "Confirmo salida de transferencia " + t.getNumero());
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
            // (antes: kardexRepository.findAll() completo + filtro en memoria con String.contains())
            BigDecimal pesoUnitario = kardexRepository
                .findFirstByTransferenciaIdAndArticuloIdAndTipoMovimiento(
                        t.getId(), d.getArticulo().getId(), KardexMovimiento.TipoMovimiento.TRANSFERENCIA_OUT)
                .map(km -> km.getRollos() > 0
                        ? km.getPesoKg().divide(new BigDecimal(km.getRollos()), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);

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
                    .findByArticuloIdAndUbicacionIdAndColorId(d.getArticulo().getId(), destino.getId(), d.getColor().getId())
                    .orElseGet(() -> {
                        StockActual s = new StockActual();
                        s.setArticulo(d.getArticulo());
                        s.setColor(d.getColor());
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
                k.setColor(d.getColor());
                k.setUbicacionDestino(destino);
                k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.TRANSFERENCIA_IN);
                k.setRollos(cantidad);
                k.setPesoKg(pesoMovido);
                k.setTransferenciaId(t.getId());
                k.setUsuario(t.getUsuarioSolicita());
                k.setObservaciones("Llegada transferencia " + t.getNumero());
                kardexRepository.save(k);
            }
        }

        t.setUsuarioConfirmaLlegada(usuarioActualService.obtenerUsuarioActual());
        t.setFechaConfirmacionLlegada(LocalDateTime.now());
        t.setEstado(tieneDiferencias
            ? Transferencia.EstadoTransferencia.CON_DIFERENCIA
            : Transferencia.EstadoTransferencia.CONFIRMADA_LLEGADA);
        transferenciaRepository.save(t);
        auditLogService.registrar("CONFIRMAR", "Transferencia", t.getId(),
                "Confirmo llegada de transferencia " + t.getNumero() + (tieneDiferencias ? " (con diferencias)" : ""));
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPERADMIN')")
    public void eliminarTransferencia(Long id) {
        Transferencia t = transferenciaRepository.findById(id).orElseThrow();
        if (t.getEstado() != Transferencia.EstadoTransferencia.BORRADOR) {
            throw new IllegalStateException(
                "Solo se pueden eliminar transferencias en estado BORRADOR. " +
                "Esta transferencia ya tiene salida y/o llegada confirmada (estado " + t.getEstado() + ") " +
                "y borrarla dejaría el stock inconsistente.");
        }
        transferenciaRepository.delete(t);
        auditLogService.registrar("ELIMINAR", "Transferencia", id, "Elimino transferencia " + t.getNumero() + " (estaba BORRADOR)");
    }
}
