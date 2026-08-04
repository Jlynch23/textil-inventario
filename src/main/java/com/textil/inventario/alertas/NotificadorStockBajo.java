package com.textil.inventario.alertas;

/**
 * Canal por el que se avisa un stock bajo. Hoy la implementación es SMS
 * ({@link NotificadorSmsTwilio}); mañana, WhatsApp/Meta será OTRA implementación
 * de esta misma interfaz, sin tocar la lógica de detección ni el disparo.
 */
public interface NotificadorStockBajo {

    void alertar(StockBajoEvent evento);
}
