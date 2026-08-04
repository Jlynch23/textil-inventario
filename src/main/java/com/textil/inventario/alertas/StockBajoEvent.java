package com.textil.inventario.alertas;

/**
 * Aviso de que un artículo/color quedó por debajo del umbral en una ubicación.
 *
 * Lleva ya resueltos los textos (label del artículo, nombre del color y de la
 * ubicación): se arma DENTRO de la transacción (sesión de Hibernate abierta) y
 * lo consume el listener DESPUÉS del commit y en otro hilo, donde las entidades
 * LAZY ya estarían desconectadas. Por eso acá no viajan entidades, solo datos.
 */
public record StockBajoEvent(
        Long articuloId,
        Long colorId,
        Long ubicacionId,
        String articuloLabel,
        String colorNombre,
        String ubicacionNombre,
        int rollosRestantes,
        int umbral) {
}
