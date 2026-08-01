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
public class EmpresaController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    // #7: entidad -> DTO para poblar el form en edicion.
    private EmpresaForm aEmpresaForm(Empresa e) {
        EmpresaForm f = new EmpresaForm();
        f.setId(e.getId());
        f.setNombre(e.getNombre());
        f.setRuc(e.getRuc());
        return f;
    }

    @GetMapping("/empresas")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String listarEmpresas(Model model) {
        model.addAttribute("empresas", catalogoService.listarEmpresas());
        model.addAttribute("empresa", new EmpresaForm());
        return "catalogo/empresas";
    }

    @PostMapping("/empresas/guardar")
    public String guardarEmpresaForm(@Valid @ModelAttribute("empresa") EmpresaForm empresa, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/empresas";
        }
        try {
            catalogoService.guardarEmpresa(empresa);
            ra.addFlashAttribute("mensaje", "Empresa guardada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe una empresa con ese RUC (puede estar inactiva en la lista; reactivala en vez de crear otra).");
        }
        return "redirect:/catalogo/empresas";
    }

    @GetMapping("/empresas/editar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String editarEmpresa(@PathVariable Long id, Model model) {
        model.addAttribute("empresas", catalogoService.listarEmpresas());
        model.addAttribute("empresa", aEmpresaForm(catalogoService.buscarEmpresa(id)));
        return "catalogo/empresas";
    }

    @PostMapping("/empresas/inactivar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String inactivarEmpresa(@PathVariable Long id, RedirectAttributes ra) {
        Empresa e = catalogoService.buscarEmpresa(id);
        e.setActivo(false);
        catalogoService.guardarEmpresa(e);
        ra.addFlashAttribute("mensaje", "Empresa inactivada. Sigue en la lista (marcada como inactiva); puedes reactivarla cuando quieras.");
        return "redirect:/catalogo/empresas";
    }

    @PostMapping("/empresas/reactivar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String reactivarEmpresa(@PathVariable Long id, RedirectAttributes ra) {
        Empresa e = catalogoService.buscarEmpresa(id);
        e.setActivo(true);
        catalogoService.guardarEmpresa(e);
        ra.addFlashAttribute("mensaje", "Empresa reactivada.");
        return "redirect:/catalogo/empresas";
    }

    @PostMapping("/empresas/eliminar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String eliminarEmpresa(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarEmpresa(id);
            ra.addFlashAttribute("mensaje", "Empresa eliminada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: esta empresa tiene recepciones u otros registros asociados. Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/catalogo/empresas";
    }
}
