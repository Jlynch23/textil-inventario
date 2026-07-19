package com.textil.inventario.recepciones;

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
        BigDecimal pesoBrutoKg
) {}
