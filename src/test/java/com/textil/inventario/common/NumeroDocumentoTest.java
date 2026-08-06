package com.textil.inventario.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.textil.inventario.common.NumeroDocumento.corto;
import static org.assertj.core.api.Assertions.assertThat;

class NumeroDocumentoTest {

    @Test
    @DisplayName("Saca la serie y los ceros de relleno")
    void guiaTipica() {
        assertThat(corto("TG01-00022836")).isEqualTo("22836");
        assertThat(corto("F001-00001234")).isEqualTo("1234");
    }

    @Test
    @DisplayName("Un numero sin serie tambien pierde los ceros")
    void sinSerie() {
        assertThat(corto("00022836")).isEqualTo("22836");
        assertThat(corto("22836")).isEqualTo("22836");
    }

    @Test
    @DisplayName("Sin numero no hay nada que mostrar")
    void vacio() {
        assertThat(corto(null)).isNull();
        assertThat(corto("   ")).isNull();
    }

    @Test
    @DisplayName("Un correlativo con letras se deja tal cual: recortarlo seria adivinar")
    void correlativoConLetras() {
        assertThat(corto("TG01-A0022")).isEqualTo("A0022");
    }

    @Test
    @DisplayName("Todo ceros no desaparece, queda 0")
    void todoCeros() {
        assertThat(corto("TG01-00000")).isEqualTo("0");
    }

    @Test
    @DisplayName("Un numero que termina en guion se devuelve entero, no vacio")
    void terminaEnGuion() {
        assertThat(corto("TG01-")).isEqualTo("TG01-");
    }

    @Test
    @DisplayName("Se limpian los espacios de los lados")
    void conEspacios() {
        assertThat(corto("  TG01-00022836  ")).isEqualTo("22836");
    }
}
