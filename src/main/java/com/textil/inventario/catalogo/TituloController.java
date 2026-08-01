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
public class TituloController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    @GetMapping("/titulos")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String listarTitulos(Model model) {
        model.addAttribute("titulos", catalogoService.listarTitulos());
        // Nombre "tituloForm" (no "titulo") a proposito: el fragmento del layout
        // (layout/base.html) declara un parametro llamado "titulo" para el texto
        // de la pestana del navegador -- si el atributo del modelo se llamara
        // igual, ese parametro lo tapa y Thymeleaf falla al resolver "titulo.id"
        // dentro de esta pantalla (EL1008E: Fragment has no field 'id').
        model.addAttribute("tituloForm", new Titulo());
        return "catalogo/titulos";
    }

    @PostMapping("/titulos/guardar")
    public String guardarTituloForm(@Valid @ModelAttribute("tituloForm") Titulo tituloForm, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/titulos";
        }
        try {
            catalogoService.guardarTitulo(tituloForm);
            ra.addFlashAttribute("mensaje", "Título guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un título con ese valor.");
        }
        return "redirect:/catalogo/titulos";
    }

    @GetMapping("/titulos/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarTitulo(@PathVariable Long id, Model model) {
        model.addAttribute("titulos", catalogoService.listarTitulos());
        model.addAttribute("tituloForm", catalogoService.buscarTitulo(id));
        return "catalogo/titulos";
    }

    @PostMapping("/titulos/inactivar/{id}")
    public String inactivarTitulo(@PathVariable Long id, RedirectAttributes ra) {
        Titulo t = catalogoService.buscarTitulo(id);
        t.setActivo(false);
        catalogoService.guardarTitulo(t);
        ra.addFlashAttribute("mensaje", "Título inactivado.");
        return "redirect:/catalogo/titulos";
    }

    @PostMapping("/titulos/eliminar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String eliminarTitulo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarTitulo(id);
            ra.addFlashAttribute("mensaje", "Título eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este título está en uso por uno o más artículos. Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/catalogo/titulos";
    }

    @PostMapping("/titulos/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearTituloRapido(@RequestBody TituloRapidoRequest request) {
        try {
            if (request.valor() == null || request.valor().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El valor es obligatorio."));
            }
            Optional<Titulo> existente = catalogoService.buscarTituloPorValor(request.valor());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "valor", existente.get().getValor(), "yaExistia", true));
            }
            Titulo titulo = new Titulo();
            titulo.setValor(request.valor().trim());
            titulo.setActivo(true);
            Titulo guardado = catalogoService.guardarTitulo(titulo);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "valor", guardado.getValor(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un título con ese valor. Puede que otro usuario lo haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearTituloRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }
}
