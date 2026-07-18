package com.textil.inventario.inventario;

import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.TipoTelaRepository;
import com.textil.inventario.catalogo.TituloRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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
        model.addAttribute("ubicaciones", ubicacionRepository.findByActivoTrue());
        model.addAttribute("tiposTela", tipoTelaRepository.findByActivoTrue());
        model.addAttribute("titulos", tituloRepository.findByActivoTrue());
        model.addAttribute("colores", colorRepository.findByActivoTrue());

        model.addAttribute("filtroUbicacionId", ubicacionId);
        model.addAttribute("filtroTipoTelaId", tipoTelaId);
        model.addAttribute("filtroTituloId", tituloId);
        model.addAttribute("filtroColorId", colorId);

        return "stock/lista";
    }

    @GetMapping("/kardex")
    public String kardex(@RequestParam(required = false) Long articuloId, Model model) {
        if (articuloId != null) {
            model.addAttribute("articulo", articuloRepository.findById(articuloId).orElse(null));
            model.addAttribute("movimientos",
                    kardexMovimientoRepository.findByArticuloIdOrderByFechaDesc(articuloId));
        } else {
            model.addAttribute("articulo", null);
            model.addAttribute("movimientos", kardexMovimientoRepository.findAllByOrderByFechaDesc());
        }
        model.addAttribute("articulos", articuloRepository.findByActivoTrue());
        model.addAttribute("filtroArticuloId", articuloId);
        return "stock/kardex";
    }
}
