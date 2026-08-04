package com.textil.inventario.alertas;

import java.util.List;

/**
 * Canal por el que se avisa un stock bajo. Hoy la implementación es SMS
 * ({@link NotificadorSmsTwilio}); mañana, WhatsApp/Meta será OTRA implementación
 * de esta misma interfaz, sin tocar la lógica de detección ni el disparo.
 *
 * Los destinatarios los resuelve {@link AlertaStockListener} (los ADMIN/GERENTE
 * con celular cargado); el canal solo se ocupa de entregar el mensaje.
 */
public interface NotificadorStockBajo {

    void alertar(StockBajoEvent evento, List<String> destinatarios);
}
