package com.textil.inventario.recepciones;

import java.util.List;

/**
 * Datos que el OCR extrae de una factura. Misma distincion que en la guia:
 * destinatario (el cliente, {@code razonSocialDetectada}/{@code rucDetectado})
 * vs emisor (quien factura el servicio de teñido).
 */
public record ExtraccionFacturaResponse(
        String numeroFactura,
        String fechaFactura,
        String razonSocialDetectada,
        String rucDetectado,
        String emisorNombre,
        String emisorRuc,
        List<String> guiasReferenciadas,
        String advertencia
) {}
