package com.textil.inventario.alertas;

import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.catalogo.Color;
import com.textil.inventario.catalogo.Ubicacion;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Decide si una salida dejó a un artículo/color por debajo del umbral en la
 * ubicación vigilada y, en ese caso, publica un {@link StockBajoEvent}. La
 * notificación real la manda {@link AlertaStockListener} DESPUÉS del commit y en
 * otro hilo: así no se ata a la transacción ni bloquea la confirmación, y no se
 * avisa por un movimiento que después se revierta.
 *
 * Dispara SOLO en el cruce hacia abajo (venía >= umbral y quedó < umbral), para
 * no spamear cuando el stock ya venía bajo. Se llama DENTRO de la transacción de
 * la salida, así que puede leer las asociaciones LAZY del artículo/color.
 */
@Component
@RequiredArgsConstructor
public class AlertaStockPublisher {

    private final ApplicationEventPublisher publisher;

    /** Interruptor general: apagado por defecto (los clientes sin SMS no se ven afectados). */
    @Value("${alerta.stock.enabled:false}")
    private boolean habilitado;

    /** Avisar cuando el stock quede por debajo de este número de rollos. */
    @Value("${alerta.stock.umbral:5}")
    private int umbral;

    /**
     * Nombre de la ubicación a vigilar. Vacío = TODAS las salidas (que hoy salen
     * de Praderas). Si se setea, solo se alerta cuando el origen coincide (sin
     * distinguir mayúsculas).
     */
    @Value("${alerta.stock.ubicacion:}")
    private String ubicacionVigilada;

    public void evaluarSalida(Ubicacion origen, Articulo articulo, Color color,
                              int rollosAntes, int rollosDespues) {
        if (!habilitado) return;
        // Cruce hacia abajo: solo cuando ESTE movimiento lo llevó por debajo del umbral.
        if (!(rollosAntes >= umbral && rollosDespues < umbral)) return;
        if (ubicacionVigilada != null && !ubicacionVigilada.isBlank()
                && !ubicacionVigilada.trim().equalsIgnoreCase(origen.getNombre())) return;

        String articuloLabel = articulo.getTipoTela().getNombre() + " "
                + articulo.getTitulo().getValor() + " / "
                + articulo.getComposicion().getNombre();

        publisher.publishEvent(new StockBajoEvent(
                articulo.getId(), color.getId(), origen.getId(),
                articuloLabel, color.getNombreMostrar(), origen.getNombre(),
                rollosDespues, umbral));
    }
}
