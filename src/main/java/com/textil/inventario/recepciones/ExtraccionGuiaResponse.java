package com.textil.inventario.recepciones;

import java.util.List;

/**
 * Datos que el OCR extrae de una guia de remision.
 *
 * Una guia tiene DOS empresas y no hay que confundirlas:
 *  - el DESTINATARIO ({@code razonSocialDetectada} + {@code rucDetectado}): la
 *    empresa del cliente que recibe la tela. Se cruza contra el catalogo local
 *    de empresas -- por RUC, que es exacto (ver ArticuloMatchingService).
 *  - el EMISOR ({@code emisorNombre} + {@code emisorRuc}): la tintoreria que
 *    tiño y despacho. Antes se asumia FAST DYE; ahora se lee del documento,
 *    porque cada cliente trabaja con su propia tintoreria.
 */
public record ExtraccionGuiaResponse(
        String numeroGuia,
        String numeroFactura,
        String fechaGuia,
        String razonSocialDetectada,
        String rucDetectado,
        String emisorNombre,
        String emisorRuc,
        List<ProductoExtraido> productos,
        String advertencia
) {}
