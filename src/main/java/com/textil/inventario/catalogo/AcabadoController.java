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
public class AcabadoController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    @GetMapping("/acabados")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String listarAcabados(Model model) {
        model.addAttribute("acabados", catalogoService.listarAcabados());
        model.addAttribute("acabado", new Acabado());
        return "catalogo/acabados";
    }

    @PostMapping("/acabados/guardar")
    public String guardarAcabadoForm(@Valid @ModelAttribute Acabado acabado, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/acabados";
        }
        try {
            catalogoService.guardarAcabado(acabado);
            ra.addFlashAttribute("mensaje", "Acabado guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un acabado con ese nombre.");
        }
        return "redirect:/catalogo/acabados";
    }

    @GetMapping("/acabados/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarAcabado(@PathVariable Long id, Model model) {
        model.addAttribute("acabados", catalogoService.listarAcabados());
        model.addAttribute("acabado", catalogoService.buscarAcabado(id));
        return "catalogo/acabados";
    }

    @PostMapping("/acabados/inactivar/{id}")
    public String inactivarAcabado(@PathVariable Long id, RedirectAttributes ra) {
        Acabado a = catalogoService.buscarAcabado(id);
        a.setActivo(false);
        catalogoService.guardarAcabado(a);
        ra.addFlashAttribute("mensaje", "Acabado inactivado.");
        return "redirect:/catalogo/acabados";
    }

    @PostMapping("/acabados/eliminar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String eliminarAcabado(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarAcabado(id);
            ra.addFlashAttribute("mensaje", "Acabado eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este acabado está en uso por uno o más artículos. Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/catalogo/acabados";
    }

    @PostMapping("/acabados/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearAcabadoRapido(@RequestBody AcabadoRapidoRequest request) {
        try {
            if (request.nombre() == null || request.nombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre es obligatorio."));
            }
            Optional<Acabado> existente = catalogoService.buscarAcabadoPorNombre(request.nombre());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombre", existente.get().getNombre(), "yaExistia", true));
            }
            Acabado acabado = new Acabado();
            acabado.setNombre(request.nombre().trim());
            acabado.setActivo(true);
            Acabado guardado = catalogoService.guardarAcabado(acabado);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombre", guardado.getNombre(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un acabado con ese nombre. Puede que otro usuario lo haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearAcabadoRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }
}
