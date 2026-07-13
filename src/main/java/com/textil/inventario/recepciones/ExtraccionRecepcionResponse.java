package com.textil.inventario.recepciones;

import java.util.List;

public record ExtraccionRecepcionResponse(
        String numeroGuia,
        String numeroFactura,
        String fechaGuia,
        Long empresaIdSugerida,
        String empresaNombreDetectado,
        List<LineaSugerida> lineas,
        String advertencia
) {}
