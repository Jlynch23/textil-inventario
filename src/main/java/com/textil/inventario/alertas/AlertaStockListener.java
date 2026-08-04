package com.textil.inventario.alertas;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recibe el {@link StockBajoEvent} DESPUÉS de que la transacción de la salida
 * commiteó (AFTER_COMMIT: no avisa si el movimiento se revierte) y en un hilo
 * aparte (@Async: no bloquea la confirmación). Delega el envío al
 * {@link NotificadorStockBajo} vigente (hoy SMS).
 *
 * Anti-spam: como máximo un aviso por artículo+color+ubicación por día. El
 * registro es EN MEMORIA a propósito: hay una instancia por cliente, así que
 * alcanza; se reinicia con la app y, en el peor caso, tras un despliegue podría
 * repetir un aviso ese día. Para el volumen real (un color cruza el umbral de
 * vez en cuando) es más que suficiente.
 */
@Component
@RequiredArgsConstructor
public class AlertaStockListener {

    private static final Logger log = LoggerFactory.getLogger(AlertaStockListener.class);

    private final NotificadorStockBajo notificador;
    private final Map<String, LocalDate> ultimoAvisoPorClave = new ConcurrentHashMap<>();

    @Async("alertaTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockBajo(StockBajoEvent e) {
        String clave = e.articuloId() + ":" + e.colorId() + ":" + e.ubicacionId();
        LocalDate hoy = LocalDate.now();
        if (hoy.equals(ultimoAvisoPorClave.get(clave))) {
            log.debug("Alerta de stock bajo omitida (ya avisada hoy): {}", clave);
            return;
        }
        // Se marca ANTES de enviar: si el envío falla, no reintenta hoy (anti-spam
        // por sobre la entrega; los cruces de umbral son eventos raros).
        ultimoAvisoPorClave.put(clave, hoy);
        notificador.alertar(e);
    }
}
