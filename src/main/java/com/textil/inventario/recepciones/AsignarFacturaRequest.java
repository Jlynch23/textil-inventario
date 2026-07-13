package com.textil.inventario.recepciones;

import java.time.LocalDate;
import java.util.List;

public record AsignarFacturaRequest(
        String numeroFactura,
        LocalDate fechaFactura,
        List<Long> recepcionIds
) {}
