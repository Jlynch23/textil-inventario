package com.textil.inventario.inventario;

import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.TipoTelaRepository;
import com.textil.inventario.catalogo.TituloRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import com.textil.inventario.recepciones.RecepcionDetalleRepository;
import com.textil.inventario.recepciones.RecepcionDocumentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/inventario")
@RequiredArgsConstructor
public class StockController {

    private final StockActualRepository stockActualRepository;
    private final KardexMovimientoRepository kardexMovimientoRepository;
    private final ArticuloRepository articuloRepository;
    private final UbicacionRepository ubicacionRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final RecepcionDetalleRepository recepcionDetalleRepository;
    private final RecepcionDocumentoRepository recepcionDocumentoRepository;

    // Umbral de "stock bajo" (mismo criterio que el dashboard): un color cuyo total
    // de rollos cae por debajo se marca para saltar a reponer.
    private static final int UMBRAL_STOCK_BAJO = 10;

    @GetMapping("/stock")
    public String listar(@RequestParam(required = false) Long ubicacionId,
                          @RequestParam(required = false) Long tipoTelaId,
                          @RequestParam(required = false) Long tituloId,
                          @RequestParam(required = false) Long colorId,
                          Model model) {

        List<StockActual> stock = stockActualRepository.findStockDisponible();

        if (ubicacionId != null) {
            stock = stock.stream()
                    .filter(s -> s.getUbicacion().getId().equals(ubicacionId))
                    .toList();
        }
        if (tipoTelaId != null) {
            stock = stock.stream()
                    .filter(s -> s.getArticulo().getTipoTela().getId().equals(tipoTelaId))
                    .toList();
        }
        if (tituloId != null) {
            stock = stock.stream()
                    .filter(s -> s.getArticulo().getTitulo().getId().equals(tituloId))
                    .toList();
        }
        if (colorId != null) {
            stock = stock.stream()
                    .filter(s -> s.getColor().getId().equals(colorId))
                    .toList();
        }

        model.addAttribute("stockList", stock);
        // Vista "Stock por color": mismo stock (ya filtrado) agrupado por color ->
        // ubicacion -> articulo, para el resumen "cuanto tengo y donde".
        model.addAttribute("resumenColor", StockPorColor.construir(stock, UMBRAL_STOCK_BAJO));
        model.addAttribute("ubicaciones", ubicacionRepository.findByActivoTrue());
        model.addAttribute("tiposTela", tipoTelaRepository.findByActivoTrue());
        model.addAttribute("titulos", tituloRepository.findByActivoTrue());
        model.addAttribute("colores", colorRepository.findByActivoTrueOrderByNombreOficialAsc());

        model.addAttribute("filtroUbicacionId", ubicacionId);
        model.addAttribute("filtroTipoTelaId", tipoTelaId);
        model.addAttribute("filtroTituloId", tituloId);
        model.addAttribute("filtroColorId", colorId);

        return "stock/lista";
    }

    @GetMapping("/kardex")
    public String kardex(@RequestParam(required = false) Long articuloId, Model model) {
        List<KardexMovimiento> movimientos;
        if (articuloId != null) {
            model.addAttribute("articulo", articuloRepository.findById(articuloId).orElse(null));
            movimientos = kardexMovimientoRepository.findByArticuloIdOrderByFechaDesc(articuloId);
        } else {
            model.addAttribute("articulo", null);
            movimientos = kardexMovimientoRepository.findTop500ByOrderByFechaDesc();
        }
        model.addAttribute("movimientos", movimientos);
        model.addAttribute("articulos", articuloRepository.findByActivoTrue());
        model.addAttribute("filtroArticuloId", articuloId);
        // Kardex → guía: mapa recepcionDetalleId -> id del documento GUÍA, para el
        // ojito "ver guía" en cada movimiento de ingreso.
        model.addAttribute("guiaPorDetalle", guiaDocPorDetalle(movimientos));
        return "stock/kardex";
    }

    /**
     * Para los movimientos mostrados, resuelve el documento GUÍA (PDF) de cada uno
     * a partir de su recepcionDetalleId: detalle → recepción → doc GUÍA. Dos queries
     * por lote (sin N+1). Solo los ingresos con guía adjunta quedan en el mapa.
     */
    private Map<Long, Long> guiaDocPorDetalle(List<KardexMovimiento> movimientos) {
        if (movimientos == null || movimientos.isEmpty()) return Map.of();

        List<Long> detalleIds = movimientos.stream()
                .map(KardexMovimiento::getRecepcionDetalleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (detalleIds.isEmpty()) return Map.of();

        // detalle -> recepción
        Map<Long, Long> recepcionPorDetalle = new HashMap<>();
        List<Long> recepcionIds = new ArrayList<>();
        for (Object[] fila : recepcionDetalleRepository.recepcionIdPorDetalle(detalleIds)) {
            Long detalleId = ((Number) fila[0]).longValue();
            Long recepcionId = ((Number) fila[1]).longValue();
            recepcionPorDetalle.put(detalleId, recepcionId);
            recepcionIds.add(recepcionId);
        }
        if (recepcionIds.isEmpty()) return Map.of();

        // recepción -> doc guía
        Map<Long, Long> guiaPorRecepcion = new HashMap<>();
        for (Object[] fila : recepcionDocumentoRepository.guiaDocPorRecepcion(recepcionIds)) {
            guiaPorRecepcion.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }

        // detalle -> doc guía
        Map<Long, Long> resultado = new HashMap<>();
        recepcionPorDetalle.forEach((detalleId, recepcionId) -> {
            Long docId = guiaPorRecepcion.get(recepcionId);
            if (docId != null) resultado.put(detalleId, docId);
        });
        return resultado;
    }
}
