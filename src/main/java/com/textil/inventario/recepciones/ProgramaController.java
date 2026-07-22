package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.AcabadoRepository;
import com.textil.inventario.catalogo.ComposicionRepository;
import com.textil.inventario.catalogo.EmpresaRepository;
import com.textil.inventario.catalogo.TipoTelaRepository;
import com.textil.inventario.catalogo.TituloRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/programas")
@RequiredArgsConstructor
public class ProgramaController {

    private final ProgramaService programaService;
    private final EmpresaRepository empresaRepository;
    private final ColorRepository colorRepository;
    private final ComposicionRepository composicionRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final AcabadoRepository acabadoRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("programas", programaService.listarProgramas());
        return "programas/lista";
    }

    @GetMapping("/nuevo")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public String nuevo(Model model) {
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        model.addAttribute("colores", colorRepository.findByActivoTrue());
        model.addAttribute("composiciones", composicionRepository.findByActivoTrue());
        model.addAttribute("tiposTela", tipoTelaRepository.findByActivoTrue());
        model.addAttribute("titulos", tituloRepository.findByActivoTrue());
        model.addAttribute("acabados", acabadoRepository.findByActivoTrue());
        return "programas/nuevo";
    }

    @PostMapping("/crear")
    public String crear(@RequestParam String numero,
                         @RequestParam Long empresaId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                         @RequestParam(required = false) String observaciones,
                         @RequestParam Integer totalRollos,
                         @RequestParam("tipoTelaId") List<Long> tipoTelaIds,
                         @RequestParam("tituloId") List<Long> tituloIds,
                         @RequestParam("composicionId") List<Long> composicionIds,
                         @RequestParam("acabadoId") List<Long> acabadoIds,
                         @RequestParam("colorId") List<Long> colorIds,
                         @RequestParam("cantidad") List<Integer> cantidades,
                         RedirectAttributes ra) {
        try {
            Programa p = programaService.crearPrograma(numero, empresaId, fecha, observaciones, totalRollos,
                    tipoTelaIds, tituloIds, composicionIds, acabadoIds, colorIds, cantidades);
            ra.addFlashAttribute("mensaje", "Programa creado correctamente.");
            return "redirect:/programas/" + p.getId();
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/programas/nuevo";
        }
    }

    @GetMapping("/{id}")
    public String verSeguimiento(@PathVariable Long id, Model model) {
        Programa programa = programaService.buscarPrograma(id);
        model.addAttribute("programa", programa);

        Map<Long, List<ProgramaService.HistorialGuiaView>> historialPorLinea = new HashMap<>();
        for (ProgramaDetalle d : programa.getDetalles()) {
            if (d.isCompleto()) {
                historialPorLinea.put(d.getId(), programaService.historialDeLineaConDocumento(d.getId()));
            }
        }
        model.addAttribute("historialPorLinea", historialPorLinea);

        return "programas/seguimiento";
    }

    @GetMapping("/{id}/editar")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Programa programa = programaService.buscarPrograma(id);
        if (programa.isCompleto()) {
            ra.addFlashAttribute("error", "Este programa ya está completo y no se puede editar.");
            return "redirect:/programas/" + id;
        }
        model.addAttribute("programa", programa);
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        model.addAttribute("colores", colorRepository.findByActivoTrue());
        model.addAttribute("composiciones", composicionRepository.findByActivoTrue());
        model.addAttribute("tiposTela", tipoTelaRepository.findByActivoTrue());
        model.addAttribute("titulos", tituloRepository.findByActivoTrue());
        model.addAttribute("acabados", acabadoRepository.findByActivoTrue());
        return "programas/editar";
    }

    @PostMapping("/{id}/actualizar")
    public String actualizar(@PathVariable Long id,
                              @RequestParam String numero,
                              @RequestParam Long empresaId,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                              @RequestParam(required = false) String observaciones,
                              @RequestParam Integer totalRollos,
                              @RequestParam(value = "detalleId", required = false) List<Long> detalleIdsExistentes,
                              @RequestParam(value = "cantidadExistente", required = false) List<Integer> cantidadesExistentes,
                              @RequestParam(value = "eliminarDetalleId", required = false) List<Long> detalleIdsAEliminar,
                              @RequestParam(value = "nuevoTipoTelaId", required = false) List<Long> nuevosTipoTelaIds,
                              @RequestParam(value = "nuevoTituloId", required = false) List<Long> nuevosTituloIds,
                              @RequestParam(value = "nuevoComposicionId", required = false) List<Long> nuevosComposicionIds,
                              @RequestParam(value = "nuevoAcabadoId", required = false) List<Long> nuevosAcabadoIds,
                              @RequestParam(value = "nuevoColorId", required = false) List<Long> nuevosColorIds,
                              @RequestParam(value = "nuevaCantidad", required = false) List<Integer> nuevasCantidades,
                              RedirectAttributes ra) {
        try {
            programaService.actualizarPrograma(id, numero, empresaId, fecha, observaciones, totalRollos,
                    listaOVacia(detalleIdsExistentes), listaOVacia(cantidadesExistentes),
                    listaOVacia(detalleIdsAEliminar),
                    listaOVacia(nuevosTipoTelaIds), listaOVacia(nuevosTituloIds),
                    listaOVacia(nuevosComposicionIds), listaOVacia(nuevosAcabadoIds),
                    listaOVacia(nuevosColorIds), listaOVacia(nuevasCantidades));
            ra.addFlashAttribute("mensaje", "Programa actualizado correctamente.");
            return "redirect:/programas/" + id;
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error",
                    "No se pudo actualizar: una de las líneas que intentaste quitar ya tiene recepciones vinculadas y no se puede eliminar.");
            return "redirect:/programas/" + id + "/editar";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/programas/" + id + "/editar";
        }
    }

    private <T> List<T> listaOVacia(List<T> lista) {
        return lista != null ? lista : List.of();
    }
}
