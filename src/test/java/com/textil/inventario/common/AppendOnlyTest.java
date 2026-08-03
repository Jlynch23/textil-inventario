package com.textil.inventario.common;

import com.textil.inventario.auditoria.LogEvento;
import com.textil.inventario.inventario.KardexMovimiento;
import jakarta.persistence.PreUpdate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KDX-01: el kardex y el log de auditoría son append-only. Un callback @PreUpdate
 * hace que cualquier intento de MODIFICAR un asiento/registro histórico reviente
 * (las correcciones se hacen con contramovimientos, no editando la historia).
 * Esta prueba evita que un refactor futuro quite el guard sin que nadie lo note.
 */
class AppendOnlyTest {

    @Test
    void kardexMovimiento_preUpdate_lanza() {
        assertPreUpdateLanza(new KardexMovimiento());
    }

    @Test
    void logEvento_preUpdate_lanza() {
        assertPreUpdateLanza(new LogEvento());
    }

    private void assertPreUpdateLanza(Object entidad) {
        Method preUpdate = null;
        for (Method m : entidad.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(PreUpdate.class)) {
                preUpdate = m;
                break;
            }
        }
        assertThat(preUpdate)
                .as("%s debe tener un método @PreUpdate (append-only, KDX-01)", entidad.getClass().getSimpleName())
                .isNotNull();
        preUpdate.setAccessible(true);
        final Method m = preUpdate;
        assertThatThrownBy(() -> m.invoke(entidad))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
