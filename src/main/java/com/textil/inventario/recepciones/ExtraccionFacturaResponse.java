package com.textil.inventario.recepciones;

import java.util.List;

public record ExtraccionFacturaResponse(
        String numeroFactura,
        String fechaFactura,
        String razonSocialDetectada,
        List<String> guiasReferenciadas,
        String advertencia
) {}
