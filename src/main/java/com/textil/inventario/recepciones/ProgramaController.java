package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/programas")
@RequiredArgsConstructor
public class ProgramaController {

    private final ProgramaService programaService;
    private final EmpresaRepository empresaRepository;
    private final ColorRepository colorRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("programas", programaService.listarProgramas());
        return "programas/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        model.addAttribute("colores", colorRepository.findByActivoTrue());
        return "programas/nuevo";
    }

    @PostMapping("/crear")
    public String crear(@RequestParam String numero,
                         @RequestParam Long empresaId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                         @RequestParam(required = false) String observaciones,
                         @RequestParam("colorId") List<Long> colorIds,
                         @RequestParam("cantidad") List<Integer> cantidades,
                         RedirectAttributes ra) {
        Programa p = programaService.crearPrograma(numero, empresaId, fecha, observaciones, colorIds, cantidades);
        ra.addFlashAttribute("mensaje", "Programa creado correctamente.");
        return "redirect:/programas/" + p.getId();
    }

    @GetMapping("/{id}")
    public String verSeguimiento(@PathVariable Long id, Model model) {
        Programa programa = programaService.buscarPrograma(id);
        model.addAttribute("programa", programa);
        return "programas/seguimiento";
    }
}
