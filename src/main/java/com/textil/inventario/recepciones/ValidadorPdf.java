package com.textil.inventario.recepciones;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Validación de PDFs en el BACKEND (auditoría).
 *
 * El navegador NO es una barrera de seguridad: {@code accept=".pdf"} solo filtra
 * el diálogo de selección y se salta trivialmente (arrastrar otro archivo, o un
 * POST directo). Antes de leer un documento con la IA o de guardarlo en disco se
 * comprueba, del lado del servidor:
 *   1. que el archivo no esté vacío,
 *   2. que no supere el tamaño permitido (defensa de memoria: el OCR mantiene en
 *      RAM el PDF + byte[] + Base64 + JSON a la vez),
 *   3. que empiece con la firma mágica {@code %PDF-} (la comprobación más
 *      importante: un .exe renombrado a .pdf falla acá, no en el navegador).
 */
public final class ValidadorPdf {

    // Tope explícito por PDF. Debe ser <= MAX_UPLOAD_SIZE (multipart, 25MB por
    // defecto); 20MB deja margen para guías/facturas escaneadas multipágina sin
    // permitir subidas desmedidas que agoten la memoria del OCR.
    public static final long MAX_PDF_BYTES = 20L * 1024 * 1024;

    private ValidadorPdf() {}

    /** Valida un PDF subido (vacío / tamaño / firma mágica). */
    public static void validar(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }
        if (archivo.getSize() > MAX_PDF_BYTES) {
            throw new IllegalArgumentException(
                    "El PDF supera el tamaño permitido (máx " + (MAX_PDF_BYTES / 1024 / 1024) + " MB).");
        }
        byte[] cabecera;
        try (var in = archivo.getInputStream()) {
            cabecera = in.readNBytes(5);
        }
        if (!esPdf(cabecera)) {
            throw new IllegalArgumentException("El archivo no es un PDF válido.");
        }
    }

    /** True si los bytes empiezan con la firma mágica de PDF ("%PDF-"). */
    public static boolean esPdf(byte[] datos) {
        if (datos == null || datos.length < 5) {
            return false;
        }
        return "%PDF-".equals(new String(datos, 0, 5, StandardCharsets.US_ASCII));
    }
}
