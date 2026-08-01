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
public class ComposicionController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    // #7: entidad -> DTO para poblar el form en edicion.
    private ComposicionForm aComposicionForm(Composicion e) {
        ComposicionForm f = new ComposicionForm();
        f.setId(e.getId());
        f.setNombre(e.getNombre());
        f.setDescripcion(e.getDescripcion());
        return f;
    }

    @GetMapping("/composiciones")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String listarComposiciones(Model model) {
        model.addAttribute("composiciones", catalogoService.listarComposiciones());
        model.addAttribute("composicion", new ComposicionForm());
        return "catalogo/composiciones";
    }

    @PostMapping("/composiciones/guardar")
    public String guardarComposicionForm(@Valid @ModelAttribute("composicion") ComposicionForm composicion, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/composiciones";
        }
        try {
            catalogoService.guardarComposicion(composicion);
            ra.addFlashAttribute("mensaje", "Composición guardada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe una composición con ese nombre.");
        }
        return "redirect:/catalogo/composiciones";
    }

    @GetMapping("/composiciones/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarComposicion(@PathVariable Long id, Model model) {
        model.addAttribute("composiciones", catalogoService.listarComposiciones());
        model.addAttribute("composicion", aComposicionForm(catalogoService.buscarComposicion(id)));
        return "catalogo/composiciones";
    }

    @PostMapping("/composiciones/inactivar/{id}")
    public String inactivarComposicion(@PathVariable Long id, RedirectAttributes ra) {
        Composicion c = catalogoService.buscarComposicion(id);
        c.setActivo(false);
        catalogoService.guardarComposicion(c);
        ra.addFlashAttribute("mensaje", "Composición inactivada.");
        return "redirect:/catalogo/composiciones";
    }

    @PostMapping("/composiciones/eliminar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String eliminarComposicion(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarComposicion(id);
            ra.addFlashAttribute("mensaje", "Composición eliminada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: esta composición está en uso por uno o más artículos. Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/catalogo/composiciones";
    }

    @PostMapping("/composiciones/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearComposicionRapido(@RequestBody ComposicionRapidoRequest request) {
        try {
            if (request.nombre() == null || request.nombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre es obligatorio."));
            }
            Optional<Composicion> existente = catalogoService.buscarComposicionPorNombre(request.nombre());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombre", existente.get().getNombre(), "yaExistia", true));
            }
            Composicion composicion = new Composicion();
            composicion.setNombre(request.nombre().trim());
            composicion.setActivo(true);
            Composicion guardado = catalogoService.guardarComposicion(composicion);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombre", guardado.getNombre(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe una composición con ese nombre. Puede que otro usuario la haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearComposicionRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }
}
