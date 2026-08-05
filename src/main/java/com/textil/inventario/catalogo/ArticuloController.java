package com.textil.inventario.catalogo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/catalogo")
@RequiredArgsConstructor
@Slf4j
public class ArticuloController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    @PostMapping("/articulos/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearArticuloRapido(@RequestBody ArticuloRapidoRequest request) {
        try {
            // Onboarding (multi-cliente): si el tipo de tela / titulo / composicion
            // / acabado que detecto la guia todavia no existen (cliente nuevo con
            // catalogo vacio), se CREAN automaticamente con el valor leido, en vez
            // de cortar con "no existe en el catalogo base". Asi el cliente arma su
            // catalogo A MEDIDA que lee guias. Se reusan los mismos buscar/guardar
            // que la creacion inline manual (mismo comportamiento, empaquetado).
            if (request.tipoTelaNombre() == null || request.tipoTelaNombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "Falta el tipo de tela detectado en la guía."));
            }
            TipoTela tipoTela = catalogoService.buscarTipoTelaPorNombre(request.tipoTelaNombre())
                    .orElseGet(() -> {
                        TipoTela t = new TipoTela();
                        t.setNombre(request.tipoTelaNombre().trim());
                        t.setActivo(true);
                        return catalogoService.guardarTipoTela(t);
                    });

            if (request.tituloValor() == null || request.tituloValor().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "Falta el título detectado en la guía."));
            }
            Titulo titulo = catalogoService.buscarTituloPorValor(request.tituloValor())
                    .orElseGet(() -> {
                        Titulo t = new Titulo();
                        t.setValor(request.tituloValor().trim());
                        t.setActivo(true);
                        return catalogoService.guardarTitulo(t);
                    });

            if (request.composicionNombre() == null || request.composicionNombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "Falta la composición detectada en la guía."));
            }
            Composicion composicion = catalogoService.buscarComposicionPorNombre(request.composicionNombre())
                    .orElseGet(() -> {
                        Composicion c = new Composicion();
                        c.setNombre(request.composicionNombre().trim());
                        c.setActivo(true);
                        return catalogoService.guardarComposicion(c);
                    });

            String acabadoNombre = (request.acabadoNombre() == null || request.acabadoNombre().isBlank())
                    ? "LISO" : request.acabadoNombre();
            Acabado acabado = catalogoService.buscarAcabadoPorNombre(acabadoNombre)
                    .orElseGet(() -> {
                        Acabado a = new Acabado();
                        a.setNombre(acabadoNombre.trim());
                        a.setActivo(true);
                        return catalogoService.guardarAcabado(a);
                    });

            Optional<Articulo> existente = catalogoService.buscarArticuloPorCombinacion(
                    tipoTela.getId(), titulo.getId(), composicion.getId(), acabado.getId());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "yaExistia", true));
            }

            Articulo articulo = new Articulo();
            articulo.setTipoTela(tipoTela);
            articulo.setTitulo(titulo);
            articulo.setComposicion(composicion);
            articulo.setAcabado(acabado);
            articulo.setCodigoInterno(catalogoService.generarCodigoInterno(tipoTela, titulo, composicion, acabado));
            articulo.setActivo(true);

            Articulo guardado = catalogoService.guardarArticulo(articulo);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "yaExistia", false));

        } catch (Exception e) {
            log.error("Error en crearArticuloRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    // #7: entidad -> DTO (ids de las piezas) para poblar el form en edicion.
    private ArticuloForm aArticuloForm(Articulo a) {
        ArticuloForm f = new ArticuloForm();
        f.setId(a.getId());
        f.setTipoTelaId(a.getTipoTela() != null ? a.getTipoTela().getId() : null);
        f.setTituloId(a.getTitulo() != null ? a.getTitulo().getId() : null);
        f.setComposicionId(a.getComposicion() != null ? a.getComposicion().getId() : null);
        f.setAcabadoId(a.getAcabado() != null ? a.getAcabado().getId() : null);
        return f;
    }

    @GetMapping("/articulos")
    public String listarArticulos(Model model) {
        model.addAttribute("articulos", catalogoService.listarArticulos());
        model.addAttribute("articulo", new ArticuloForm());
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("titulos", catalogoService.listarTitulos());
        model.addAttribute("composiciones", catalogoService.listarComposiciones());
        model.addAttribute("acabados", catalogoService.listarAcabados());
        return "catalogo/articulos";
    }

    @PostMapping("/articulos/guardar")
    public String guardarArticulo(@Valid @ModelAttribute("articulo") ArticuloForm articulo,
                                   BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/articulos";
        }
        try {
            catalogoService.guardarArticulo(articulo);
            ra.addFlashAttribute("mensaje", "Artículo guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un artículo con esa combinación de tejido, título, composición y acabado.");
        }
        return "redirect:/catalogo/articulos";
    }

    @GetMapping("/articulos/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarArticulo(@PathVariable Long id, Model model) {
        model.addAttribute("articulos", catalogoService.listarArticulos());
        model.addAttribute("articulo", aArticuloForm(catalogoService.buscarArticulo(id)));
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("titulos", catalogoService.listarTitulos());
        model.addAttribute("composiciones", catalogoService.listarComposiciones());
        model.addAttribute("acabados", catalogoService.listarAcabados());
        return "catalogo/articulos";
    }

    @PostMapping("/articulos/eliminar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String eliminarArticulo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarArticulo(id);
            ra.addFlashAttribute("mensaje", "Artículo eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este artículo tiene stock, movimientos de kardex u otros registros asociados.");
        }
        return "redirect:/catalogo/articulos";
    }
}
