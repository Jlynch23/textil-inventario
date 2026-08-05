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
public class ColorController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    // #7: mapea la entidad al DTO para poblar el form en edicion.
    private ColorForm aColorForm(Color c) {
        ColorForm f = new ColorForm();
        f.setId(c.getId());
        f.setNombreOficial(c.getNombreOficial());
        f.setCodigoFastDye(c.getCodigoFastDye());
        f.setApodo(c.getApodo());
        return f;
    }

    @GetMapping("/colores")
    public String listarColores(Model model) {
        model.addAttribute("colores", catalogoService.listarColores());
        model.addAttribute("color", new ColorForm());
        return "catalogo/colores";
    }

    @PostMapping("/colores/guardar")
    public String guardarColor(@Valid @ModelAttribute("color") ColorForm color, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/colores";
        }
        catalogoService.guardarColor(color);
        ra.addFlashAttribute("mensaje", "Color guardado correctamente.");
        return "redirect:/catalogo/colores";
    }

    @GetMapping("/colores/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarColor(@PathVariable Long id, Model model) {
        model.addAttribute("colores", catalogoService.listarColores());
        model.addAttribute("color", aColorForm(catalogoService.buscarColor(id)));
        return "catalogo/colores";
    }

    @PostMapping("/colores/inactivar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String inactivarColor(@PathVariable Long id, RedirectAttributes ra) {
        Color c = catalogoService.buscarColor(id);
        c.setActivo(false);
        catalogoService.guardarColor(c);
        ra.addFlashAttribute("mensaje", "Color inactivado.");
        return "redirect:/catalogo/colores";
    }

    @PostMapping("/colores/eliminar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
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
            if (request.nombreOficial() == null || request.nombreOficial().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre oficial es obligatorio."));
            }
            // Idempotente: si ya existe un color activo con ese codigo FAST DYE,
            // se reutiliza en vez de intentar crear un duplicado.
            if (request.codigoFastDye() != null && !request.codigoFastDye().isBlank()) {
                Optional<Color> existente = catalogoService.resolverColorPorCodigo(request.codigoFastDye(), request.nombreOficial());
                if (existente.isPresent()) {
                    return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombreOficial", existente.get().getNombreOficial(), "yaExistia", true));
                }
            }
            Color color = new Color();
            color.setNombreOficial(request.nombreOficial());
            color.setCodigoFastDye(request.codigoFastDye());
            color.setActivo(true);
            Color guardado = catalogoService.guardarColor(color);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombreOficial", guardado.getNombreOficial()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un color con ese nombre. FAST DYE repite nombres con códigos distintos: usa un nombre diferenciado (ej. BLANCO AZULADO / BLANCO CREMOSO)."));
        } catch (Exception e) {
            log.error("Error en crearColorRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }
}
