package com.textil.inventario.catalogo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
import java.util.Optional;

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

    @GetMapping("/colores/eliminar/{id}")
    public String eliminarColor(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarColor(id);
            ra.addFlashAttribute("mensaje", "Color eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este color está en uso por uno o más artículos. Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/catalogo/colores";
    }

    @PostMapping("/colores/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearColorRapido(@RequestBody ColorRapidoRequest request) {
        try {
            Color color = new Color();
            color.setNombreOficial(request.nombreOficial());
            color.setCodigoFastDye(request.codigoFastDye());
            color.setFamilia(request.familia());
            color.setActivo(true);
            Color guardado = catalogoService.guardarColor(color);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombreOficial", guardado.getNombreOficial()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── ARTÍCULOS ─────────────────────────────────────────
    @PostMapping("/articulos/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearArticuloRapido(@RequestBody ArticuloRapidoRequest request) {
        try {
            Optional<TipoTela> tipoTela = catalogoService.buscarTipoTelaPorNombre(request.tipoTelaNombre());
            if (tipoTela.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error",
                        "Tipo de tela '" + request.tipoTelaNombre() + "' no existe en el catálogo base."));
            }

            Optional<Titulo> titulo = catalogoService.buscarTituloPorValor(request.tituloValor());
            if (titulo.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error",
                        "Título '" + request.tituloValor() + "' no existe en el catálogo base."));
            }

            Optional<Color> color = catalogoService.buscarColorPorCodigoFastDye(request.colorCodigo());
            if (color.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error",
                        "Primero debes crear el color (código '" + request.colorCodigo() + "')."));
            }

            Optional<Articulo> existente = catalogoService.buscarArticuloPorCombinacion(
                    tipoTela.get().getId(), titulo.get().getId(), color.get().getId());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "yaExistia", true));
            }

            Articulo articulo = new Articulo();
            articulo.setTipoTela(tipoTela.get());
            articulo.setTitulo(titulo.get());
            articulo.setColor(color.get());
            String codigo = tipoTela.get().getNombre().replace(" ", "").substring(0, 3).toUpperCase()
                    + "-" + titulo.get().getValor().replace("/", "")
                    + "-" + color.get().getNombreOficial().replace(" ", "").substring(0, Math.min(4, color.get().getNombreOficial().length())).toUpperCase();
            articulo.setCodigoInterno(codigo);
            articulo.setActivo(true);

            Articulo guardado = catalogoService.guardarArticulo(articulo);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "yaExistia", false));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


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

    @GetMapping("/articulos/eliminar/{id}")
    public String eliminarArticulo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarArticulo(id);
            ra.addFlashAttribute("mensaje", "Artículo eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este artículo tiene stock, movimientos de kardex u otros registros asociados.");
        }
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

    @GetMapping("/ubicaciones/eliminar/{id}")
    public String eliminarUbicacion(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarUbicacion(id);
            ra.addFlashAttribute("mensaje", "Ubicación eliminada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: esta ubicación tiene stock o transferencias asociadas.");
        }
        return "redirect:/catalogo/ubicaciones";
    }
}
