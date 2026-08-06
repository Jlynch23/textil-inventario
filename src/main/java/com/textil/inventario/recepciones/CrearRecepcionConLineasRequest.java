package com.textil.inventario.recepciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CrearRecepcionConLineasRequest(
        Long empresaId,
        String numeroGuia,
        String numeroFactura,
        LocalDate fechaGuia,
        String observaciones,
        // Emisor (tintoreria) que el OCR leyo de la guia. Opcional: si la guia
        // se carga a mano o el dato no se pudo leer, viaja null y la recepcion
        // simplemente queda sin emisor registrado.
        String emisorNombre,
        String emisorRuc,
        List<LineaRequest> lineas,
        // Lineas que SI venian en la guia y el usuario decidio NO registrar
        // (marcandolas "No incluir" en la pantalla de carga). No crean detalle
        // ni tocan stock: quedan anotadas en las observaciones y en el log de
        // auditoria. Antes una linea sin resolver se descartaba en silencio y la
        // recepcion quedaba con menos lineas que el papel, sin rastro del porque.
        List<LineaExcluidaRequest> lineasExcluidas
) {
    public record LineaRequest(
            Long articuloId,
            Long colorId,
            String programaTenido,
            Integer rollosGuia,
            BigDecimal pesoBrutoKg
    ) {}

    /**
     * Una linea excluida se guarda como TEXTO, no como referencias al catalogo:
     * el caso tipico es justamente el de una linea que NO se pudo resolver
     * (articulo o color inexistente), asi que no hay ids que guardar. Lo que
     * importa es dejar escrito que en la guia venia y por que no entro.
     */
    public record LineaExcluidaRequest(
            String descripcion,
            String colorNombre,
            String colorCodigo,
            String programaTenido,
            Integer rollos,
            BigDecimal pesoBrutoKg,
            String motivo
    ) {}
}
