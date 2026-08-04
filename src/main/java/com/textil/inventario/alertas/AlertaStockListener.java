package com.textil.inventario.alertas;

import com.textil.inventario.seguridad.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recibe el {@link StockBajoEvent} DESPUÉS de que la transacción de la salida
 * commiteó (AFTER_COMMIT: no avisa si el movimiento se revierte) y en un hilo
 * aparte (@Async: no bloquea la confirmación).
 *
 * Resuelve los destinatarios desde la base LOCAL de la instancia: los celulares
 * de los usuarios ADMIN/GERENTE activos con número cargado (el SUPERADMIN queda
 * afuera). Como cada cliente es una instancia con su propia base, la alerta de
 * un cliente le llega SOLO a su gente. `twilio.to` queda como destinatario extra
 * opcional (útil para pruebas), pero en producción la lista sale de los usuarios.
 *
 * Anti-spam: como máximo un aviso por artículo+color+ubicación por día (en
 * memoria; una instancia por cliente).
 */
@Component
@RequiredArgsConstructor
public class AlertaStockListener {

    private static final Logger log = LoggerFactory.getLogger(AlertaStockListener.class);

    private final NotificadorStockBajo notificador;
    private final UsuarioRepository usuarioRepository;
    private final Map<String, LocalDate> ultimoAvisoPorClave = new ConcurrentHashMap<>();

    /** Destinatario(s) extra opcional(es), separados por coma. Vacío en producción. */
    @Value("${twilio.to:}")
    private String destinatariosExtra;

    @Async("alertaTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockBajo(StockBajoEvent e) {
        List<String> destinatarios = resolverDestinatarios();
        if (destinatarios.isEmpty()) {
            log.warn("Stock bajo detectado pero SIN destinatarios (ningún ADMIN/GERENTE con celular): {}",
                    e.colorNombre());
            return; // no se marca como avisado: si cargan un celular hoy, aún puede salir
        }

        String clave = e.articuloId() + ":" + e.colorId() + ":" + e.ubicacionId();
        LocalDate hoy = LocalDate.now();
        if (hoy.equals(ultimoAvisoPorClave.get(clave))) {
            log.debug("Alerta de stock bajo omitida (ya avisada hoy): {}", clave);
            return;
        }
        ultimoAvisoPorClave.put(clave, hoy);
        notificador.alertar(e, destinatarios);
    }

    /** ADMIN/GERENTE con celular (de la base local) + los extra opcionales, sin duplicar. */
    private List<String> resolverDestinatarios() {
        List<String> destinatarios = new ArrayList<>(usuarioRepository.celularesDeAdminYGerente());
        if (destinatariosExtra != null && !destinatariosExtra.isBlank()) {
            for (String extra : destinatariosExtra.split(",")) {
                String t = extra.trim();
                if (!t.isEmpty() && !destinatarios.contains(t)) {
                    destinatarios.add(t);
                }
            }
        }
        return destinatarios;
    }
}
