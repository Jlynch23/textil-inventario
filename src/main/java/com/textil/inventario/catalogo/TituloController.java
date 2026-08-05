package com.textil.inventario.catalogo;

import com.textil.inventario.common.RespuestaJson;
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

    // #7: entidad -> DTO para poblar el form en edicion.
    private TituloForm aTituloForm(Titulo e) {
        TituloForm f = new TituloForm();
        f.setId(e.getId());
        f.setValor(e.getValor());
        f.setDescripcion(e.getDescripcion());
        return f;
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
        model.addAttribute("tituloForm", new TituloForm());
        return "catalogo/titulos";
    }

    @PostMapping("/titulos/guardar")
    public String guardarTituloForm(@Valid @ModelAttribute("tituloForm") TituloForm tituloForm, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", RespuestaJson.primerError(bindingResult));
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
        model.addAttribute("tituloForm", aTituloForm(catalogoService.buscarTitulo(id)));
        return "catalogo/titulos";
    }

    @PostMapping("/titulos/inactivar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
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
        return RespuestaJson.responder("crearTituloRapido",
                "Ya existe un título con ese valor. Puede que otro usuario lo haya creado justo ahora — recarga e intenta de nuevo.",
                () -> {
                if (request.valor() == null || request.valor().isBlank()) {
                    throw new IllegalArgumentException("El valor es obligatorio.");
                }
                Optional<Titulo> existente = catalogoService.buscarTituloPorValor(request.valor());
                if (existente.isPresent()) {
                    return Map.of("id", existente.get().getId(), "valor", existente.get().getValor(), "yaExistia", true);
                }
                Titulo titulo = new Titulo();
                titulo.setValor(request.valor().trim());
                titulo.setActivo(true);
                Titulo guardado = catalogoService.guardarTitulo(titulo);
                return Map.of("id", guardado.getId(), "valor", guardado.getValor(), "yaExistia", false);
                });
    }
}
