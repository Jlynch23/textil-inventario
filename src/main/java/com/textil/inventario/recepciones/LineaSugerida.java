package com.textil.inventario.recepciones;

import java.math.BigDecimal;

public record LineaSugerida(
        Long articuloId,
        String tipoTela,
        String titulo,
        Long colorId,
        String colorCodigo,
        String colorNombre,
        String programaTenido,
        Integer rollosGuia,
        BigDecimal pesoBrutoKg,
        boolean matched,
        String motivoNoMatch
) {}
