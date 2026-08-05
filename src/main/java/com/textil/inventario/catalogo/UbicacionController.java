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
public class UbicacionController {

    private final CatalogoService catalogoService;

    // #7: entidad -> DTO para poblar el form en edicion.
    private UbicacionForm aUbicacionForm(Ubicacion e) {
        UbicacionForm f = new UbicacionForm();
        f.setId(e.getId());
        f.setCodigo(e.getCodigo());
        f.setNombre(e.getNombre());
        f.setTipo(e.getTipo());
        f.setEsPrincipal(e.getEsPrincipal());
        return f;
    }

    @GetMapping("/ubicaciones")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String listarUbicaciones(Model model) {
        model.addAttribute("ubicaciones", catalogoService.listarUbicaciones());
        model.addAttribute("ubicacion", new UbicacionForm());
        return "catalogo/ubicaciones";
    }

    @GetMapping("/ubicaciones/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarUbicacion(@PathVariable Long id, Model model) {
        model.addAttribute("ubicaciones", catalogoService.listarUbicaciones());
        model.addAttribute("ubicacion", aUbicacionForm(catalogoService.buscarUbicacion(id)));
        return "catalogo/ubicaciones";
    }

    @PostMapping("/ubicaciones/guardar")
    public String guardarUbicacion(@Valid @ModelAttribute("ubicacion") UbicacionForm ubicacion, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", RespuestaJson.primerError(bindingResult));
            return "redirect:/catalogo/ubicaciones";
        }
        catalogoService.guardarUbicacion(ubicacion);
        ra.addFlashAttribute("mensaje", "Ubicación guardada correctamente.");
        return "redirect:/catalogo/ubicaciones";
    }

    @PostMapping("/ubicaciones/eliminar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String eliminarUbicacion(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarUbicacion(id);
            boolean quedaPrincipal = catalogoService.listarUbicaciones().stream()
                    .anyMatch(x -> Boolean.TRUE.equals(x.getEsPrincipal()));
            if (quedaPrincipal) {
                ra.addFlashAttribute("mensaje", "Ubicación eliminada correctamente.");
            } else {
                ra.addFlashAttribute("advertencia", "Ubicación eliminada. Ya no hay ninguna ubicación "
                        + "marcada como principal: designa una antes de confirmar recepciones.");
            }
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: esta ubicación tiene stock o transferencias asociadas.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/catalogo/ubicaciones";
    }
}
