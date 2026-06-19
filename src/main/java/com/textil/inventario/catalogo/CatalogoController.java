package com.textil.inventario.catalogo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/catalogo")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    // ─── COLORES ───────────────────────────────────────────
    @GetMapping("/colores")
    public String listarColores(Model model) {
        model.addAttribute("colores", catalogoService.listarColores());
        model.addAttribute("color", new Color());
        return "catalogo/colores";
    }

    @PostMapping("/colores/guardar")
    public String guardarColor(@ModelAttribute Color color, RedirectAttributes ra) {
        catalogoService.guardarColor(color);
        ra.addFlashAttribute("mensaje", "Color guardado correctamente.");
        return "redirect:/catalogo/colores";
    }

    @GetMapping("/colores/editar/{id}")
    public String editarColor(@PathVariable Long id, Model model) {
        model.addAttribute("colores", catalogoService.listarColores());
        model.addAttribute("color", catalogoService.buscarColor(id));
        return "catalogo/colores";
    }

    @GetMapping("/colores/inactivar/{id}")
    public String inactivarColor(@PathVariable Long id, RedirectAttributes ra) {
        Color c = catalogoService.buscarColor(id);
        c.setActivo(false);
        catalogoService.guardarColor(c);
        ra.addFlashAttribute("mensaje", "Color inactivado.");
        return "redirect:/catalogo/colores";
    }

    // ─── ARTÍCULOS ─────────────────────────────────────────
    @GetMapping("/articulos")
    public String listarArticulos(Model model) {
        model.addAttribute("articulos", catalogoService.listarArticulos());
        model.addAttribute("articulo", new Articulo());
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("titulos", catalogoService.listarTitulos());
        model.addAttribute("colores", catalogoService.listarColores());
        return "catalogo/articulos";
    }

    @PostMapping("/articulos/guardar")
    public String guardarArticulo(@ModelAttribute Articulo articulo,
                                   @RequestParam Long tipoTelaId,
                                   @RequestParam Long tituloId,
                                   @RequestParam Long colorId,
                                   RedirectAttributes ra) {
        articulo.setTipoTela(catalogoService.listarTiposTela().stream()
            .filter(t -> t.getId().equals(tipoTelaId)).findFirst().orElseThrow());
        articulo.setTitulo(catalogoService.listarTitulos().stream()
            .filter(t -> t.getId().equals(tituloId)).findFirst().orElseThrow());
        articulo.setColor(catalogoService.buscarColor(colorId));

        // Generar código interno automático
        if (articulo.getCodigoInterno() == null || articulo.getCodigoInterno().isBlank()) {
            String codigo = articulo.getTipoTela().getNombre().replace(" ", "").substring(0, 3).toUpperCase()
                + "-" + articulo.getTitulo().getValor().replace("/", "")
                + "-" + articulo.getColor().getNombreOficial().replace(" ", "").substring(0, Math.min(4, articulo.getColor().getNombreOficial().length())).toUpperCase();
            articulo.setCodigoInterno(codigo);
        }

        catalogoService.guardarArticulo(articulo);
        ra.addFlashAttribute("mensaje", "Artículo guardado correctamente.");
        return "redirect:/catalogo/articulos";
    }

    // ─── UBICACIONES ───────────────────────────────────────
    @GetMapping("/ubicaciones")
    public String listarUbicaciones(Model model) {
        model.addAttribute("ubicaciones", catalogoService.listarUbicaciones());
        model.addAttribute("ubicacion", new Ubicacion());
        return "catalogo/ubicaciones";
    }

    @PostMapping("/ubicaciones/guardar")
    public String guardarUbicacion(@ModelAttribute Ubicacion ubicacion, RedirectAttributes ra) {
        catalogoService.guardarUbicacion(ubicacion);
        ra.addFlashAttribute("mensaje", "Ubicación guardada correctamente.");
        return "redirect:/catalogo/ubicaciones";
    }
}
