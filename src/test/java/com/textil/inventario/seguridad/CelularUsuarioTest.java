package com.textil.inventario.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.textil.inventario.seguridad.UsuarioController.errorCelular;
import static com.textil.inventario.seguridad.UsuarioController.normalizarCelular;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El celular del usuario alimenta las alertas. Antes solo se reconocia el movil
 * peruano de 9 digitos (para anteponerle +51) y CUALQUIER otra cosa se guardaba
 * tal cual: texto libre quedaba en la columna y salia despues como destinatario,
 * o pasaba de los 20 caracteres de la columna y reventaba al guardar el usuario.
 */
class CelularUsuarioTest {

    @Test
    @DisplayName("Un movil peruano de 9 digitos recibe el prefijo +51")
    void movilPeruano_recibePrefijo() {
        assertThat(normalizarCelular("987654321")).isEqualTo("+51987654321");
    }

    @Test
    @DisplayName("Se limpian espacios, guiones y parentesis antes de validar")
    void seLimpiaLaPuntuacion() {
        assertThat(normalizarCelular(" (987) 654-321 ")).isEqualTo("+51987654321");
        assertThat(errorCelular(normalizarCelular("+51 987 654 321"))).isNull();
    }

    @Test
    @DisplayName("Vacio es valido: significa 'no me mandes avisos'")
    void vacio_esValidoYQuedaNull() {
        assertThat(normalizarCelular(null)).isNull();
        assertThat(normalizarCelular("   ")).isNull();
        assertThat(errorCelular(null)).isNull();
    }

    @Test
    @DisplayName("Un numero con codigo de pais no peruano tambien vale")
    void codigoDePaisExtranjero_esValido() {
        assertThat(errorCelular(normalizarCelular("+5491123456789"))).isNull();
    }

    @Test
    @DisplayName("Texto libre se rechaza en vez de guardarse como destinatario")
    void textoLibre_seRechaza() {
        assertThat(errorCelular(normalizarCelular("no tengo"))).isNotNull();
        assertThat(errorCelular(normalizarCelular("mi celu es el de siempre"))).isNotNull();
    }

    @Test
    @DisplayName("Digitos sueltos sin prefijo se rechazan (no son un movil peruano)")
    void digitosIncompletos_seRechazan() {
        assertThat(errorCelular(normalizarCelular("12345"))).isNotNull();
        assertThat(errorCelular(normalizarCelular("9876543210"))).isNotNull();
    }

    @Test
    @DisplayName("Un numero mas largo que la columna (20) se rechaza, no revienta la BD")
    void demasiadoLargo_seRechaza() {
        assertThat(errorCelular(normalizarCelular("+5198765432198765432198"))).isNotNull();
    }
}
