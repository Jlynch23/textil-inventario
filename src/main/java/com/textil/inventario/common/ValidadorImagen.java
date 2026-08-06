package com.textil.inventario.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Validación de las FOTOS rápidas del almacén en el BACKEND (auditoría FILE-01).
 *
 * La carga rápida (entrada/salida) guardaba la foto según su extensión/nombre sin
 * mirar el contenido: un binario arbitrario (un .exe, o un HTML/SVG con script)
 * renombrado a .png se escribía igual en disco y luego se servía como imagen.
 * Antes de guardar se comprueba, del lado del servidor:
 *   1. que el archivo no esté vacío,
 *   2. que no supere el tamaño permitido,
 *   3. que sus bytes iniciales sean la firma mágica de una imagen aceptada
 *      (JPG/PNG/WEBP) o de un PDF (la lista blanca de la carga también admite
 *      PDF como evidencia, así que se tolera para no romper ese flujo).
 *
 * No decodifica la imagen ni valida dimensiones (ImageIO no trae lector WEBP en
 * el JDK estándar, rechazaría WEBP válidos): la firma mágica ya cierra el vector
 * de "binario arbitrario renombrado". La decodificación/dimensiones/hash quedan
 * como endurecimiento futuro.
 */
public final class ValidadorImagen {

    // Tope por foto. <= MAX_UPLOAD_SIZE (multipart, 25MB por defecto). 15MB cubre
    // fotos de cámara de celular sin permitir subidas desmedidas.
    public static final long MAX_IMAGEN_BYTES = 15L * 1024 * 1024;

    private ValidadorImagen() {}

    /** Valida una foto rápida subida (vacío / tamaño / firma mágica). */
    public static void validar(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("La foto está vacía.");
        }
        if (archivo.getSize() > MAX_IMAGEN_BYTES) {
            throw new IllegalArgumentException(
                    "La foto supera el tamaño permitido (máx " + (MAX_IMAGEN_BYTES / 1024 / 1024) + " MB).");
        }
        byte[] cabecera;
        try (var in = archivo.getInputStream()) {
            cabecera = in.readNBytes(12);
        }
        // Se acepta imagen (JPG/PNG/WEBP) o PDF (la carga rápida comparte la lista
        // blanca de extensiones con los documentos, que incluye .pdf).
        if (!esImagen(cabecera) && !ValidadorPdf.esPdf(cabecera)) {
            throw new IllegalArgumentException(
                    "El archivo no es una imagen válida (se aceptan JPG, PNG o WEBP).");
        }
    }

    /** True si los bytes empiezan con la firma mágica de JPG, PNG o WEBP. */
    public static boolean esImagen(byte[] d) {
        return esJpeg(d) || esPng(d) || esWebp(d);
    }

    private static boolean esJpeg(byte[] d) {
        return d != null && d.length >= 3
                && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    private static boolean esPng(byte[] d) {
        return d != null && d.length >= 8
                && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && (d[4] & 0xFF) == 0x0D && (d[5] & 0xFF) == 0x0A && (d[6] & 0xFF) == 0x1A && (d[7] & 0xFF) == 0x0A;
    }

    private static boolean esWebp(byte[] d) {
        // "RIFF"...."WEBP"
        return d != null && d.length >= 12
                && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }
}
