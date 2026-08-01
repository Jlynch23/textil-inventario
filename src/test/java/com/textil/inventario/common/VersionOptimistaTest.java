package com.textil.inventario.common;

import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.transferencias.Transferencia;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresión de R-C1 (auditoría red-team): las entidades padre de los flujos de
 * stock DEBEN llevar un campo @Version. Es el lock optimista que evita que dos
 * confirmaciones concurrentes pasen el guard de estado (TOCTOU) y apliquen el
 * movimiento de stock dos veces.
 *
 * Una prueba de concurrencia real necesita un contexto JPA + BD (Testcontainers,
 * pendiente en el hardening de CI). Mientras tanto, este test evita que un
 * refactor futuro quite el @Version sin que nadie lo note.
 */
class VersionOptimistaTest {

    @Test
    void recepcion_tiene_campo_version() {
        assertTrue(tieneCampoVersion(Recepcion.class),
                "Recepcion debe tener un campo anotado con @Version (lock optimista, R-C1)");
    }

    @Test
    void transferencia_tiene_campo_version() {
        assertTrue(tieneCampoVersion(Transferencia.class),
                "Transferencia debe tener un campo anotado con @Version (lock optimista, R-C1)");
    }

    private boolean tieneCampoVersion(Class<?> tipo) {
        for (Field f : tipo.getDeclaredFields()) {
            if (f.isAnnotationPresent(Version.class)) {
                return true;
            }
        }
        return false;
    }
}
