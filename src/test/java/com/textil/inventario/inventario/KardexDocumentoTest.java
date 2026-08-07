package com.textil.inventario.inventario;

import com.textil.inventario.inventario.KardexMovimiento.TipoDocumento;
import com.textil.inventario.inventario.KardexMovimiento.TipoMovimiento;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V47: el kardex vincula el documento que origino el movimiento con el par
 * (tipo_documento, documento_id) en vez de una columna por tipo.
 *
 * Lo que se cuida aca es que el par no se pueda leer mal: el id 7 puede ser una
 * recepcion o una transferencia, y confundirlos enlazaria un movimiento con el
 * papel equivocado.
 */
class KardexDocumentoTest {

    @Test
    void vincularRecepcionDetalle_soloResponde_comoRecepcion() {
        KardexMovimiento k = new KardexMovimiento();
        k.vincularRecepcionDetalle(7L);

        assertThat(k.getTipoDocumento()).isEqualTo(TipoDocumento.RECEPCION_DETALLE);
        assertThat(k.getDocumentoId()).isEqualTo(7L);
        assertThat(k.getRecepcionDetalleId()).isEqualTo(7L);
        // El mismo id existe como transferencia, pero este movimiento no vino de ahi.
        assertThat(k.getTransferenciaId()).isNull();
    }

    @Test
    void vincularTransferencia_soloResponde_comoTransferencia() {
        KardexMovimiento k = new KardexMovimiento();
        k.vincularTransferencia(7L);

        assertThat(k.getTipoDocumento()).isEqualTo(TipoDocumento.TRANSFERENCIA);
        assertThat(k.getDocumentoId()).isEqualTo(7L);
        assertThat(k.getTransferenciaId()).isEqualTo(7L);
        assertThat(k.getRecepcionDetalleId()).isNull();
    }

    @Test
    void sinVincular_ningunAccesorInventaUnDocumento() {
        KardexMovimiento k = new KardexMovimiento();

        assertThat(k.getTipoDocumento()).isNull();
        assertThat(k.getDocumentoId()).isNull();
        assertThat(k.getRecepcionDetalleId()).isNull();
        assertThat(k.getTransferenciaId()).isNull();
    }

    @Test
    void vincularDocumento_exigeLosDosDatos() {
        // Un documento_id sin tipo no se puede interpretar, y un tipo sin id no
        // vincula nada: por eso no hay setters sueltos para el par.
        assertThatThrownBy(() -> new KardexMovimiento().vincularDocumento(TipoDocumento.TRANSFERENCIA, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KardexMovimiento().vincularDocumento(null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hayTiposParaExpresarUnaTransformacion() {
        // V2 (hilo -> tela cruda) necesita expresar que un material se consume y
        // aparece otro distinto. Si alguien los saca, V2 vuelve a quedar sin forma
        // de registrarlo y el kardex miente por omision.
        assertThat(TipoMovimiento.values())
                .contains(TipoMovimiento.TRANSFORMACION_IN, TipoMovimiento.TRANSFORMACION_OUT);
    }

    @Test
    void losNombresDeLosEnums_entranEnSuColumna() {
        // Los dos se guardan como texto (VARCHAR(30)) para que sumar un tipo en V2
        // no obligue a un ALTER sobre la tabla mas grande del sistema. El precio es
        // que MySQL ya no valida el conjunto de valores: un nombre mas largo que la
        // columna se corta o revienta recien al insertar, en produccion.
        for (TipoMovimiento t : TipoMovimiento.values()) {
            assertThat(t.name().length())
                    .as("TipoMovimiento.%s no entra en tipo_movimiento VARCHAR(30)", t.name())
                    .isLessThanOrEqualTo(30);
        }
        for (TipoDocumento t : TipoDocumento.values()) {
            assertThat(t.name().length())
                    .as("TipoDocumento.%s no entra en tipo_documento VARCHAR(30)", t.name())
                    .isLessThanOrEqualTo(30);
        }
    }
}
