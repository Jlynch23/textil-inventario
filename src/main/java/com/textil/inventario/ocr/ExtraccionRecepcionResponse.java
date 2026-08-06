package com.textil.inventario.ocr;

import java.util.List;

/**
 * Lo que la pantalla de nueva recepcion recibe tras leer la guia con el OCR:
 * los datos ya cruzados contra el catalogo local (empresa sugerida, lineas
 * matcheadas) mas el emisor tal como vino en el documento.
 */
public record ExtraccionRecepcionResponse(
        String numeroGuia,
        String numeroFactura,
        String fechaGuia,
        Long empresaIdSugerida,
        String empresaNombreDetectado,
        // Emisor (tintoreria) leido de la guia: viaja al front para mostrarlo y
        // vuelve al crear la recepcion, que lo persiste (V45).
        String emisorNombre,
        String emisorRuc,
        List<LineaSugerida> lineas,
        String advertencia
) {}
