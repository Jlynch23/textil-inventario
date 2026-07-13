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
        List<LineaRequest> lineas
) {
    public record LineaRequest(
            Long articuloId,
            String programaTenido,
            Integer rollosGuia,
            BigDecimal pesoBrutoKg
    ) {}
}
