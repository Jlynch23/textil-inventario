package com.textil.inventario.archivohistorico;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Archivo Historico esta RESERVADO al SUPERADMIN (el proveedor), y el motivo
 * no es de confianza sino de COSTO: subir un ZIP dispara una llamada a la API de
 * Anthropic POR CADA documento que trae adentro, contra la UNICA API key del
 * proveedor, compartida por todas las instancias. Un ADMIN cargando un zip grande
 * gasta plata que no es suya y que nadie le factura.
 *
 * Por que un test y no solo la anotacion: este permiso se afloja sin hacer ruido.
 * Nadie escribe "de ADMIN a SUPERADMIN" al reves a proposito, pero alcanza con
 * copiar el @PreAuthorize de otro controlador, o con "unificar" las anotaciones del
 * paquete, para volver a abrirlo. Y no falla nada: la pantalla sigue andando, el
 * ZIP se procesa igual, y la unica señal es la factura de la API a fin de mes.
 *
 * OJO: esto cubre la anotacion del controlador (defensa en profundidad). La regla
 * de URL vive aparte, en SecurityConfig (`/archivo-historico/**` -> SUPERADMIN);
 * las dos tienen que decir lo mismo.
 */
class ArchivoHistoricoReservadoTest {

    @Test
    void elControlador_estaReservadoAlSuperadmin() {
        PreAuthorize anotacion = ArchivoHistoricoController.class.getAnnotation(PreAuthorize.class);

        assertThat(anotacion)
                .as("ArchivoHistoricoController debe llevar @PreAuthorize a nivel de clase")
                .isNotNull();
        assertThat(anotacion.value())
                .as("el Archivo Historico gasta la API key del proveedor: solo SUPERADMIN")
                .isEqualTo("hasRole('SUPERADMIN')");
    }

    @Test
    void elControlador_noLeAbreLaPuertaAlAdmin() {
        String expresion = ArchivoHistoricoController.class.getAnnotation(PreAuthorize.class).value();

        // Se mira el texto de la expresion a proposito: si alguien vuelve a poner
        // hasAnyRole('ADMIN','SUPERADMIN') --que es lo que decia antes-- el test de
        // arriba ya falla, pero este deja explicito CUAL es el rol que no debe estar.
        //
        // El rol se busca ENTRECOMILLADO de los dos lados: "ADMIN'" a secas tambien
        // matchea dentro de "SUPERADMIN')" y el test pasaria a fallar siempre.
        assertThat(expresion)
                .as("ningun rol fuera de SUPERADMIN entra al Archivo Historico")
                .doesNotContain("'ADMIN'")
                .doesNotContain("'GERENTE'")
                .doesNotContain("'SUPERVISOR'")
                .doesNotContain("'VENDEDOR'");
    }
}
