package com.textil.inventario.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FILE-01: las fotos rápidas se validan por CONTENIDO (firma mágica + tamaño) en
 * el backend, no por la extensión del nombre. Un binario arbitrario renombrado a
 * .png debe rechazarse.
 */
class ValidadorImagenTest {

    private static final byte[] PNG  = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] PDF  = "%PDF-1.7 contenido".getBytes();

    @Test
    void esImagen_pngJpegWebp_true() {
        assertThat(ValidadorImagen.esImagen(PNG)).isTrue();
        assertThat(ValidadorImagen.esImagen(JPEG)).isTrue();
        assertThat(ValidadorImagen.esImagen(WEBP)).isTrue();
    }

    @Test
    void esImagen_noImagen_false() {
        assertThat(ValidadorImagen.esImagen("MZ ejecutable".getBytes())).isFalse();
        assertThat(ValidadorImagen.esImagen(new byte[] {1, 2})).isFalse();
        assertThat(ValidadorImagen.esImagen(null)).isFalse();
    }

    @Test
    void validar_png_noLanza() throws Exception {
        ValidadorImagen.validar(new MockMultipartFile("foto", "conteo.png", "image/png", PNG));
    }

    @Test
    void validar_pdfComoEvidencia_noLanza() throws Exception {
        // La carga rápida comparte la lista blanca con los documentos (incluye PDF).
        ValidadorImagen.validar(new MockMultipartFile("foto", "guia.pdf", "application/pdf", PDF));
    }

    @Test
    void validar_binarioArbitrarioRenombrado_lanza() {
        MockMultipartFile f = new MockMultipartFile("foto", "malicioso.png", "image/png",
                "MZ ejecutable".getBytes());
        assertThatThrownBy(() -> ValidadorImagen.validar(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es una imagen");
    }

    @Test
    void validar_vacio_lanza() {
        MockMultipartFile f = new MockMultipartFile("foto", "vacio.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> ValidadorImagen.validar(f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validar_superaTamano_lanza() {
        byte[] grande = new byte[(int) ValidadorImagen.MAX_IMAGEN_BYTES + 1];
        // firma PNG válida, pero tamaño por encima del tope
        grande[0] = (byte) 0x89; grande[1] = 'P'; grande[2] = 'N'; grande[3] = 'G';
        grande[4] = 0x0D; grande[5] = 0x0A; grande[6] = 0x1A; grande[7] = 0x0A;
        MockMultipartFile f = new MockMultipartFile("foto", "grande.png", "image/png", grande);
        assertThatThrownBy(() -> ValidadorImagen.validar(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamaño");
    }
}
