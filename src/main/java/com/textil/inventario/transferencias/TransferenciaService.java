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
    private final CorrelativoRepository correlativoRepository;
    private final com.textil.inventario.auditoria.AuditLogService auditLogService;
    private final com.textil.inventario.alertas.AlertaStockPublisher alertaStockPublisher;

    // R-C2 (red-team): cota superior por linea para que sumar cantidades enormes
    // a stock.rollos (int) no desborde en silencio a negativo.
    private static final int MAX_ROLLOS_POR_LINEA = 1_000_000;

    public List<Transferencia> listarTransferencias() {
        return transferenciaRepository.findAllByOrderByFechaSolicitudDesc();
    }

    public Transferencia buscarTransferencia(Long id) {
        // #9 (OSIV off): con ubicacionOrigen y detalles precargados para render.
        return transferenciaRepository.findWithDetallesById(id).orElseThrow();
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

    // #6 (auditoria): el numero se reserva de un CORRELATIVO con bloqueo pesimista,
    // no del MAX(numero). Antes, dos altas simultaneas leian el mismo MAX y
    // generaban el mismo "TRF-000101" (una fallaba contra el UNIQUE). Ahora el
    // SELECT ... FOR UPDATE serializa: cada request obtiene un numero distinto.
    // Corre dentro de la transaccion de crearTransferencia (@Transactional), asi
    // que el lock se mantiene hasta el commit.
    private String generarNumero() {
        Correlativo c = correlativoRepository.bloquearPorNombre("transferencia")
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el correlativo 'transferencia' (deberia crearlo la migracion V41)."));
        long siguiente = Math.addExact(c.getUltimoValor(), 1);
        c.setUltimoValor(siguiente);
        correlativoRepository.save(c);
        return String.format("TRF-%06d", siguiente);
    }

    @Transactional
    public TransferenciaDetalle agregarDetalle(Long transferenciaId, Long articuloId, Long colorId,
                                                Integer cantidadSolicitada, String observaciones) {
        // Auditoria: solo se agregan lineas mientras la transferencia esta en
        // BORRADOR; despues de confirmar la salida el detalle ya no debe cambiar.
        Transferencia t = transferenciaRepository.findById(transferenciaId).orElseThrow();
        if (t.getEstado() != Transferencia.EstadoTransferencia.BORRADOR) {
            throw new IllegalStateException(
                    "Solo se pueden agregar líneas mientras la transferencia está en BORRADOR (estado actual: "
                    + t.getEstado() + ").");
        }
        // Auditoria: la cantidad solicitada debe ser positiva.
        if (cantidadSolicitada == null || cantidadSolicitada <= 0) {
            throw new IllegalArgumentException("La cantidad solicitada debe ser mayor que cero.");
        }
        TransferenciaDetalle d = new TransferenciaDetalle();
        d.setTransferencia(t);
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
        // Idempotencia (auditoria P0-1, C2): la salida solo se confirma desde
        // BORRADOR. Sin este guard, reenviar el formulario descuenta el stock de
        // origen dos veces (pudiendo dejarlo negativo) y duplica el kardex
        // TRANSFERENCIA_OUT.
        if (t.getEstado() != Transferencia.EstadoTransferencia.BORRADOR) {
            throw new IllegalStateException(
                    "La transferencia " + t.getNumero() + " ya tiene la salida confirmada (estado "
                    + t.getEstado() + "); no se puede confirmar la salida de nuevo.");
        }

        // Auditoria (integridad): un detalleId repetido descontaria el stock de
        // esa linea dos veces. Se rechazan duplicados (complementa el guard M1).
        if (new java.util.HashSet<>(detalleIds).size() != detalleIds.size()) {
            throw new IllegalArgumentException("Llegaron líneas de detalle repetidas. Recarga la página e intenta de nuevo.");
        }

        for (int i = 0; i < detalleIds.size(); i++) {
            TransferenciaDetalle d = detalleRepository.findById(detalleIds.get(i)).orElseThrow();
            // M1 (auditoria): el detalle DEBE pertenecer a esta transferencia,
            // para que un POST manipulado no descuente stock de otra.
            if (d.getTransferencia() == null || !d.getTransferencia().getId().equals(transferenciaId)) {
                throw new IllegalArgumentException(
                        "La línea de detalle " + detalleIds.get(i) + " no pertenece a esta transferencia.");
            }
            Integer cantidad = i < cantidadesConfirmadas.size() ? cantidadesConfirmadas.get(i) : d.getCantidadSolicitada();
            // Auditoria (rigurosidad, jul-2026): sin este guardado, una cantidad
            // negativa pasa la comprobacion de stock insuficiente de abajo
            // (rollos < cantidad es falso si cantidad es negativa) y termina
            // SUMANDO stock en una SALIDA -- crea rollos de la nada sin error.
            if (cantidad == null || cantidad < 0) {
                throw new IllegalArgumentException(
                        "La cantidad confirmada de salida no puede ser negativa (línea de detalle " + detalleIds.get(i) + ").");
            }
            if (cantidad > MAX_ROLLOS_POR_LINEA) {
                throw new IllegalArgumentException(
                        "La cantidad confirmada de salida (" + cantidad + ") es demasiado alta "
                        + "(máx " + MAX_ROLLOS_POR_LINEA + " por línea).");
            }
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

            int rollosAntes = stockOrigen.getRollos();
            stockOrigen.setRollos(rollosAntes - cantidad);
            stockOrigen.setPesoKg(stockOrigen.getPesoKg().subtract(pesoMovido));
            stockActualRepository.save(stockOrigen);
            // Alerta de stock bajo (SMS): publica un evento si ESTA salida cruzó el
            // umbral en la ubicación vigilada. El envío es post-commit y async
            // (AlertaStockPublisher decide; AlertaStockListener lo manda).
            alertaStockPublisher.evaluarSalida(t.getUbicacionOrigen(), d.getArticulo(), d.getColor(),
                    rollosAntes, rollosAntes - cantidad);

            KardexMovimiento k = new KardexMovimiento();
            k.setArticulo(d.getArticulo());
            k.setColor(d.getColor());
            k.setUbicacionOrigen(t.getUbicacionOrigen());
            k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.TRANSFERENCIA_OUT);
            k.setRollos(cantidad);
            k.setPesoKg(pesoMovido);
            k.vincularTransferencia(t.getId());
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
        // Idempotencia (auditoria P0-1, C3): la llegada solo se confirma tras una
        // salida confirmada. Sin este guard se podria (a) confirmar llegada sin
        // salida -> sumar stock al destino sin haberlo descontado del origen
        // (rollos de la nada, con peso 0), o (b) reenviar el formulario ->
        // duplicar el stock en destino y el kardex TRANSFERENCIA_IN.
        if (t.getEstado() != Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA) {
            throw new IllegalStateException(
                    "La transferencia " + t.getNumero() + " no está lista para confirmar la llegada (estado "
                    + t.getEstado() + "; primero hay que confirmar la salida).");
        }
        List<TransferenciaDetalle> detalles = detalleRepository.findByTransferenciaId(transferenciaId);
        boolean tieneDiferencias = false;

        for (TransferenciaDetalle d : detalles) {
            Map<Long, Integer> reparto = repartoPorDetalle.getOrDefault(d.getId(), Map.of());

            // Auditoria INV-01: rechazar cantidades negativas ANTES de sumar. Los
            // negativos participaban en la suma total (un reparto +100/-90 sumaba
            // 10, pasaba el tope de "no mas que lo despachado"), pero mas abajo el
            // -90 se descartaba con `continue` y el +100 SI se agregaba al destino
            // -> 90 rollos fabricados de la nada. Se validan aca, no con `continue`,
            // para que un reparto invalido corte la operacion completa. El 0 se
            // tolera (no mueve stock).
            for (Map.Entry<Long, Integer> e : reparto.entrySet()) {
                if (e.getValue() == null || e.getValue() < 0) {
                    throw new IllegalArgumentException(
                            "El reparto de una línea contiene una cantidad inválida (" + e.getValue()
                            + ") para la ubicación " + e.getKey()
                            + ". No se permiten cantidades negativas.");
                }
            }

            int totalRepartido = reparto.values().stream().mapToInt(Integer::intValue).sum();
            // Auditoria P0-1 (C4): no se puede recibir mas de lo que salio. Sin
            // este tope, repartir mas que lo despachado sumaba rollos de la nada
            // al destino (la transferencia solo quedaba etiquetada CON_DIFERENCIA,
            // pero el stock global ya estaba inflado).
            int despachado = d.getCantidadConfirmadaSalida() != null ? d.getCantidadConfirmadaSalida() : 0;
            if (totalRepartido > despachado) {
                throw new IllegalArgumentException(
                        "El reparto de una línea (" + totalRepartido + " rollos) supera lo despachado en la salida ("
                        + despachado + "). No se puede recibir más de lo que salió.");
            }
            d.setCantidadConfirmadaLlegada(totalRepartido);
            detalleRepository.save(d);

            if (!Integer.valueOf(totalRepartido).equals(d.getCantidadConfirmadaSalida())) {
                tieneDiferencias = true;
            }

            // Peso unitario de referencia según lo despachado en la salida de esta transferencia
            // (antes: kardexRepository.findAll() completo + filtro en memoria con String.contains())
            BigDecimal pesoUnitario = kardexRepository
                .findFirstByTipoDocumentoAndDocumentoIdAndArticuloIdAndColorIdAndTipoMovimiento(
                        KardexMovimiento.TipoDocumento.TRANSFERENCIA, t.getId(),
                        d.getArticulo().getId(), d.getColor().getId(), KardexMovimiento.TipoMovimiento.TRANSFERENCIA_OUT)
                .map(km -> km.getRollos() > 0
                        ? km.getPesoKg().divide(new BigDecimal(km.getRollos()), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);

            for (Map.Entry<Long, Integer> entry : reparto.entrySet()) {
                Long ubicacionId = entry.getKey();
                Integer cantidad = entry.getValue();
                if (cantidad == null || cantidad <= 0) continue;

                Ubicacion destino = ubicacionRepository.findById(ubicacionId).orElseThrow();
                // Auditoria: el destino del reparto debe ser una ubicacion activa y
                // NO la principal (la tela sale de la principal hacia tiendas/otras).
                if (Boolean.FALSE.equals(destino.getActivo()) || Boolean.TRUE.equals(destino.getEsPrincipal())) {
                    throw new IllegalArgumentException(
                            "Destino de reparto no válido: " + destino.getNombre() + " (inactiva o es la ubicación principal).");
                }
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

                stockDestino.setRollos(Math.addExact(stockDestino.getRollos(), cantidad));
                stockDestino.setPesoKg(stockDestino.getPesoKg().add(pesoMovido));
                stockActualRepository.save(stockDestino);

                KardexMovimiento k = new KardexMovimiento();
                k.setArticulo(d.getArticulo());
                k.setColor(d.getColor());
                k.setUbicacionDestino(destino);
                k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.TRANSFERENCIA_IN);
                k.setRollos(cantidad);
                k.setPesoKg(pesoMovido);
                k.vincularTransferencia(t.getId());
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
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
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
