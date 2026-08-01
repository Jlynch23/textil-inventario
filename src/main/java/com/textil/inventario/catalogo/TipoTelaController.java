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
public class TipoTelaController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    // #7: entidad -> DTO para poblar el form en edicion.
    private TipoTelaForm aTipoTelaForm(TipoTela e) {
        TipoTelaForm f = new TipoTelaForm();
        f.setId(e.getId());
        f.setNombre(e.getNombre());
        f.setDescripcion(e.getDescripcion());
        return f;
    }

    @GetMapping("/tipos-tela")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String listarTiposTela(Model model) {
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("tipoTela", new TipoTelaForm());
        return "catalogo/tipos-tela";
    }

    @PostMapping("/tipos-tela/guardar")
    public String guardarTipoTela(@Valid @ModelAttribute("tipoTela") TipoTelaForm tipoTela, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/tipos-tela";
        }
        try {
            catalogoService.guardarTipoTela(tipoTela);
            ra.addFlashAttribute("mensaje", "Tipo de tela guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un tipo de tela con ese nombre.");
        }
        return "redirect:/catalogo/tipos-tela";
    }

    @GetMapping("/tipos-tela/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarTipoTela(@PathVariable Long id, Model model) {
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("tipoTela", aTipoTelaForm(catalogoService.buscarTipoTela(id)));
        return "catalogo/tipos-tela";
    }

    @PostMapping("/tipos-tela/inactivar/{id}")
    public String inactivarTipoTela(@PathVariable Long id, RedirectAttributes ra) {
        TipoTela t = catalogoService.buscarTipoTela(id);
        t.setActivo(false);
        catalogoService.guardarTipoTela(t);
        ra.addFlashAttribute("mensaje", "Tipo de tela inactivado.");
        return "redirect:/catalogo/tipos-tela";
    }

    @PostMapping("/tipos-tela/eliminar/{id}")
    public String eliminarTipoTela(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarTipoTela(id);
            ra.addFlashAttribute("mensaje", "Tipo de tela eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este tipo de tela está en uso por uno o más artículos. Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/catalogo/tipos-tela";
    }

    @PostMapping("/tipos-tela/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearTipoTelaRapido(@RequestBody TipoTelaRapidoRequest request) {
        try {
            if (request.nombre() == null || request.nombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre es obligatorio."));
            }
            Optional<TipoTela> existente = catalogoService.buscarTipoTelaPorNombre(request.nombre());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombre", existente.get().getNombre(), "yaExistia", true));
            }
            TipoTela tipoTela = new TipoTela();
            tipoTela.setNombre(request.nombre().trim());
            tipoTela.setActivo(true);
            TipoTela guardado = catalogoService.guardarTipoTela(tipoTela);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombre", guardado.getNombre(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un tipo de tela con ese nombre. Puede que otro usuario lo haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearTipoTelaRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }
}
