package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/recepciones")
@RequiredArgsConstructor
public class RecepcionController {

    private final RecepcionService recepcionService;
    private final EmpresaRepository empresaRepository;
    private final ArticuloRepository articuloRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("recepciones", recepcionService.listarRecepciones());
        return "recepciones/lista";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        return "recepciones/nueva";
    }

    @PostMapping("/crear")
    public String crear(@RequestParam Long empresaId,
                        @RequestParam String numeroGuia,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaGuia,
                        @RequestParam(required = false) String observaciones,
                        RedirectAttributes ra) {
        Recepcion r = recepcionService.crearRecepcion(empresaId, numeroGuia, fechaGuia, observaciones);
        ra.addFlashAttribute("mensaje", "Recepción creada. Ahora agrega los detalles.");
        return "redirect:/recepciones/" + r.getId() + "/detalle";
    }

    @GetMapping("/{id}/detalle")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("recepcion", recepcionService.buscarRecepcion(id));
        model.addAttribute("articulos", articuloRepository.findByActivoTrue());
        return "recepciones/detalle";
    }

    @PostMapping("/{id}/agregar-linea")
    public String agregarLinea(@PathVariable Long id,
                                @RequestParam Long articuloId,
                                @RequestParam String programaTenido,
                                @RequestParam Integer rollosGuia,
                                @RequestParam(required = false) BigDecimal pesoBrutoKg,
                                RedirectAttributes ra) {
        recepcionService.agregarDetalle(id, articuloId, programaTenido, rollosGuia, pesoBrutoKg);
        ra.addFlashAttribute("mensaje", "Línea agregada correctamente.");
        return "redirect:/recepciones/" + id + "/detalle";
    }

    @GetMapping("/{id}/confirmar")
    public String confirmarForm(@PathVariable Long id, Model model) {
        model.addAttribute("recepcion", recepcionService.buscarRecepcion(id));
        return "recepciones/confirmar";
    }

    @PostMapping("/{id}/confirmar")
    public String confirmar(@PathVariable Long id,
                             @RequestParam(value="detalleIds") List<Long> detalleIds,
                             @RequestParam(value="rollosRecibidos") List<Integer> rollosRecibidos,
                             @RequestParam(value="observacionesDetalle") List<String> observacionesDetalle,
                             RedirectAttributes ra) {
        recepcionService.confirmarRecepcion(id, detalleIds, rollosRecibidos, observacionesDetalle);
        ra.addFlashAttribute("mensaje", "Recepción confirmada. Stock actualizado.");
        return "redirect:/recepciones";
    }
}
