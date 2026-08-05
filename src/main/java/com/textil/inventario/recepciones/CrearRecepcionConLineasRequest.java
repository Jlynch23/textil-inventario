package com.textil.inventario.recepciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CrearRecepcionConLineasRequest(
        Long empresaId,
        String numeroGuia,
        String numeroFactura,
        LocalDate fechaGuia,
        String observaciones,
        // Emisor (tintoreria) que el OCR leyo de la guia. Opcional: si la guia
        // se carga a mano o el dato no se pudo leer, viaja null y la recepcion
        // simplemente queda sin emisor registrado.
        String emisorNombre,
        String emisorRuc,
        List<LineaRequest> lineas
) {
    public record LineaRequest(
            Long articuloId,
            Long colorId,
            String programaTenido,
            Integer rollosGuia,
            BigDecimal pesoBrutoKg
    ) {}
}
