package com.textil.inventario.transferencias;

import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/transferencias")
@RequiredArgsConstructor
public class TransferenciaController {

    private final TransferenciaService transferenciaService;
    private final ArticuloRepository articuloRepository;
    private final UbicacionRepository ubicacionRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("transferencias", transferenciaService.listarTransferencias());
        return "transferencias/lista";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferenciaService.eliminarTransferencia(id);
            ra.addFlashAttribute("mensaje", "Transferencia eliminada correctamente.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transferencias";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        return "transferencias/nueva";
    }

    @PostMapping("/crear")
    public String crear(@RequestParam(required = false) String observaciones,
                         RedirectAttributes ra) {
        Transferencia t = transferenciaService.crearTransferencia(observaciones);
        ra.addFlashAttribute("mensaje", "Transferencia creada. Ahora agrega los artículos a enviar.");
        return "redirect:/transferencias/" + t.getId() + "/detalle";
    }

    @GetMapping("/{id}/detalle")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("transferencia", transferenciaService.buscarTransferencia(id));
        model.addAttribute("articulos", articuloRepository.findByActivoTrue());
        return "transferencias/detalle";
    }

    @PostMapping("/{id}/agregar-linea")
    public String agregarLinea(@PathVariable Long id,
                                @RequestParam Long articuloId,
                                @RequestParam Integer cantidadSolicitada,
                                @RequestParam(required = false) String observaciones,
                                RedirectAttributes ra) {
        transferenciaService.agregarDetalle(id, articuloId, cantidadSolicitada, observaciones);
        ra.addFlashAttribute("mensaje", "Artículo agregado a la transferencia.");
        return "redirect:/transferencias/" + id + "/detalle";
    }

    @GetMapping("/{id}/confirmar-salida")
    public String confirmarSalidaForm(@PathVariable Long id, Model model) {
        model.addAttribute("transferencia", transferenciaService.buscarTransferencia(id));
        return "transferencias/confirmar-salida";
    }

    @PostMapping("/{id}/confirmar-salida")
    public String confirmarSalida(@PathVariable Long id,
                                   @RequestParam(value = "cantidades") java.util.List<Integer> cantidades,
                                   @RequestParam(value = "observacionesDetalle") java.util.List<String> observacionesDetalle,
                                   RedirectAttributes ra) {
        transferenciaService.confirmarSalida(id, cantidades, observacionesDetalle);
        ra.addFlashAttribute("mensaje", "Salida confirmada. Stock descontado de Praderas.");
        return "redirect:/transferencias";
    }

    @GetMapping("/{id}/confirmar-llegada")
    public String confirmarLlegadaForm(@PathVariable Long id, Model model) {
        model.addAttribute("transferencia", transferenciaService.buscarTransferencia(id));
        model.addAttribute("ubicacionesDestino",
            ubicacionRepository.findByActivoTrue().stream()
                .filter(u -> !u.getEsPrincipal())
                .toList());
        return "transferencias/confirmar-llegada";
    }

    @PostMapping("/{id}/confirmar-llegada")
    public String confirmarLlegada(@PathVariable Long id,
                                    @RequestParam Map<String, String> allParams,
                                    RedirectAttributes ra) {
        // Se leen todos los parámetros con formato reparto_<detalleId>_<ubicacionId> = cantidad
        Map<Long, Map<Long, Integer>> repartoPorDetalle = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("reparto_")) continue;
            String[] partes = key.substring("reparto_".length()).split("_");
            if (partes.length != 2) continue;
            Long detalleId = Long.valueOf(partes[0]);
            Long ubicacionId = Long.valueOf(partes[1]);
            Integer cantidad;
            try {
                cantidad = entry.getValue().isBlank() ? 0 : Integer.valueOf(entry.getValue());
            } catch (NumberFormatException e) {
                cantidad = 0;
            }
            if (cantidad <= 0) continue;
            repartoPorDetalle.computeIfAbsent(detalleId, k -> new HashMap<>()).put(ubicacionId, cantidad);
        }

        transferenciaService.confirmarLlegada(id, repartoPorDetalle);
        ra.addFlashAttribute("mensaje", "Llegada confirmada. Stock repartido y actualizado en destino(s).");
        return "redirect:/transferencias";
    }
}
