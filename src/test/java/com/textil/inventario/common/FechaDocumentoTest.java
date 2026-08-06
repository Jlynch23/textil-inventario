package com.textil.inventario.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.textil.inventario.common.FechaDocumento.aIso;
import static com.textil.inventario.common.FechaDocumento.parse;
import static org.assertj.core.api.Assertions.assertThat;

class FechaDocumentoTest {

    @Test
    @DisplayName("Una guia peruana se lee DIA/MES/AÑO, no mes/dia")
    void diaMesAnio() {
        // El caso que se rompia: dia <= 12, asi que "05/08" tambien es una fecha
        // valida leida al reves. 5 de AGOSTO, no 8 de mayo.
        assertThat(parse("05/08/2026")).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(parse("01/12/2026")).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("Con dia mayor que 12 tambien, aunque ahi el error no se notaba")
    void diaMayorQueDoce() {
        assertThat(parse("30/07/2026")).isEqualTo(LocalDate.of(2026, 7, 30));
    }

    @Test
    @DisplayName("Sirve cualquier separador y el dia/mes sin cero adelante")
    void separadoresYSinCeros() {
        assertThat(parse("5/8/2026")).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(parse("05-08-2026")).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(parse("05.08.2026")).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    @DisplayName("Año de dos digitos: una guia es de este siglo")
    void anioCorto() {
        assertThat(parse("05/08/26")).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    @DisplayName("ISO se sigue aceptando: es lo que manda el navegador")
    void iso() {
        assertThat(parse("2026-08-05")).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(parse("2026-8-5")).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    @DisplayName("Una fecha que no existe se rechaza, no se corre al mes siguiente")
    void fechaInexistente() {
        assertThat(parse("31/02/2026")).isNull();
        assertThat(parse("32/01/2026")).isNull();
        assertThat(parse("05/13/2026")).isNull();
    }

    @Test
    @DisplayName("Sin fecha legible se devuelve null: el usuario la escribe a mano")
    void ilegible() {
        assertThat(parse(null)).isNull();
        assertThat(parse("   ")).isNull();
        assertThat(parse("no se lee")).isNull();
        assertThat(parse("05/08")).isNull();
    }

    @Test
    @DisplayName("aIso deja la fecha lista para el <input type=date>")
    void normalizadaAIso() {
        assertThat(aIso("05/08/2026")).isEqualTo("2026-08-05");
        assertThat(aIso("2026-08-05")).isEqualTo("2026-08-05");
        assertThat(aIso("basura")).isNull();
    }

    @Test
    @DisplayName("Se toleran espacios de sobra")
    void conEspacios() {
        assertThat(parse("  05/08/2026 ")).isEqualTo(LocalDate.of(2026, 8, 5));
    }
}
