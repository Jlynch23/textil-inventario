package com.textil.inventario.recepciones;

import java.util.List;

public record ExtraccionGuiaResponse(
        String numeroGuia,
        String fechaGuia,
        String razonSocialDetectada,
        List<ProductoExtraido> productos,
        String advertencia
) {}
