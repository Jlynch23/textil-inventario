package com.textil.inventario.ocr;

import java.math.BigDecimal;

public record ProductoExtraido(
        String tipoTela,
        String titulo,
        String composicion,
        String acabado,
        String colorCodigo,
        String colorNombre,
        String programaTenido,
        Integer rollos,
        BigDecimal pesoBrutoKg,
        /**
         * El texto de la descripcion TAL CUAL viene en la guia, sin interpretar.
         * No se muestra al usuario: sirve para que el backend pueda VERIFICAR de
         * forma determinista lo que la IA dijo haber leido (ver
         * ArticuloMatchingService.desajusteDeAcabado). Es null en los flujos
         * viejos que no lo piden, y ahi la verificacion simplemente no corre.
         */
        String descripcionOriginal
) {}
