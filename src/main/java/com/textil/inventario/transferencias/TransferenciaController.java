package com.textil.inventario.transferencias;

import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import com.textil.inventario.inventario.StockActual;
import com.textil.inventario.inventario.StockActualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/transferencias")
@RequiredArgsConstructor
public class TransferenciaController {

    private final TransferenciaService transferenciaService;
    private final ArticuloRepository articuloRepository;
    private final ColorRepository colorRepository;
    private final UbicacionRepository ubicacionRepository;
    private final StockActualRepository stockActualRepository;

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
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String nueva(Model model) {
        return "transferencias/nueva";
    }

    @PostMapping("/crear")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String crear(@RequestParam(required = false) String observaciones,
                         RedirectAttributes ra) {
        try {
            Transferencia t = transferenciaService.crearTransferencia(observaciones);
            ra.addFlashAttribute("mensaje", "Transferencia creada. Ahora agrega los artículos a enviar.");
            return "redirect:/transferencias/" + t.getId() + "/detalle";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // A5: dos altas casi simultaneas pudieron generar el mismo numero; el
            // UNIQUE lo evita. Se pide reintentar en vez de mostrar un 500.
            ra.addFlashAttribute("error", "Otra transferencia se creó al mismo tiempo; intenta de nuevo.");
            return "redirect:/transferencias";
        }
    }

    @GetMapping("/{id}/detalle")
    public String detalle(@PathVariable Long id, Model model) {
        Transferencia t = transferenciaService.buscarTransferencia(id);
        model.addAttribute("transferencia", t);
        model.addAttribute("articulos", articuloRepository.findByActivoTrueOrdenados());
        model.addAttribute("colores", colorRepository.findByActivoTrueOrderByNombreOficialAsc());
        // Stock disponible en el ORIGEN (Praderas), por articulo+color, para el
        // desplegable dependiente: al elegir un articulo, el color muestra SOLO lo
        // que hay en Praderas de ese articulo (con cuantos rollos). Se serializa a
        // JSON en la vista y lo filtra el JS.
        List<Map<String, Object>> stockOrigen = new ArrayList<>();
        for (StockActual s : stockActualRepository.findDisponibleConColorPorUbicacion(t.getUbicacionOrigen().getId())) {
            Map<String, Object> item = new HashMap<>();
            String codigo = s.getColor().getCodigoFastDye();
            String colorLabel = s.getColor().getNombreMostrar()
                    + (codigo != null && !codigo.isBlank() ? " (" + codigo + ")" : "");
            item.put("articuloId", s.getArticulo().getId());
            item.put("colorId", s.getColor().getId());
            item.put("colorLabel", colorLabel);
            item.put("rollos", s.getRollos());
            stockOrigen.add(item);
        }
        model.addAttribute("stockOrigen", stockOrigen);
        return "transferencias/detalle";
    }

    @PostMapping("/{id}/agregar-linea")
    public String agregarLinea(@PathVariable Long id,
                                @RequestParam Long articuloId,
                                @RequestParam Long colorId,
                                @RequestParam Integer cantidadSolicitada,
                                @RequestParam(required = false) String observaciones,
                                RedirectAttributes ra) {
        try {
            transferenciaService.agregarDetalle(id, articuloId, colorId, cantidadSolicitada, observaciones);
            ra.addFlashAttribute("mensaje", "Artículo agregado a la transferencia.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transferencias/" + id + "/detalle";
    }

    @GetMapping("/{id}/confirmar-salida")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String confirmarSalidaForm(@PathVariable Long id, Model model) {
        model.addAttribute("transferencia", transferenciaService.buscarTransferencia(id));
        return "transferencias/confirmar-salida";
    }

    @PostMapping("/{id}/confirmar-salida")
    public String confirmarSalida(@PathVariable Long id,
                                   @RequestParam(value = "detalleIds") java.util.List<Long> detalleIds,
                                   @RequestParam(value = "cantidades") java.util.List<Integer> cantidades,
                                   @RequestParam(value = "observacionesDetalle") java.util.List<String> observacionesDetalle,
                                   RedirectAttributes ra) {
        try {
            transferenciaService.confirmarSalida(id, detalleIds, cantidades, observacionesDetalle);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/transferencias/" + id + "/confirmar-salida";
        } catch (org.springframework.dao.DataIntegrityViolationException
                 | org.springframework.dao.OptimisticLockingFailureException e) {
            // A6 / R-C1: colision con otra confirmacion sobre el mismo stock.
            ra.addFlashAttribute("error",
                    "Otra operación modificó este stock al mismo tiempo. Vuelve a intentar.");
            return "redirect:/transferencias/" + id + "/confirmar-salida";
        }
        ra.addFlashAttribute("mensaje", "Salida confirmada. Stock descontado de Praderas.");
        return "redirect:/transferencias";
    }

    @GetMapping("/{id}/confirmar-llegada")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
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
            Long detalleId;
            Long ubicacionId;
            try {
                detalleId = Long.valueOf(partes[0]);
                ubicacionId = Long.valueOf(partes[1]);
            } catch (NumberFormatException e) {
                // Clave manipulada o basura: no corresponde a ninguna linea real
                // del formulario. Se ignora en vez de reventar con un 500.
                continue;
            }
            Integer cantidad;
            if (entry.getValue().isBlank()) {
                cantidad = 0;   // campo dejado vacio: ese destino no recibe nada
            } else {
                try {
                    cantidad = Integer.valueOf(entry.getValue().trim());
                } catch (NumberFormatException e) {
                    // Antes se convertia a 0 y seguia: los rollos de esa linea
                    // quedaban sin destino y la llegada se confirmaba igual, sin
                    // ningun aviso. Un valor no numerico es un error del usuario
                    // y hay que decirselo.
                    ra.addFlashAttribute("error",
                            "La cantidad «" + entry.getValue() + "» no es un número válido. Revisa el reparto.");
                    return "redirect:/transferencias/" + id + "/confirmar-llegada";
                }
            }
            if (cantidad < 0) {
                ra.addFlashAttribute("error", "Las cantidades del reparto no pueden ser negativas.");
                return "redirect:/transferencias/" + id + "/confirmar-llegada";
            }
            if (cantidad == 0) continue;
            repartoPorDetalle.computeIfAbsent(detalleId, k -> new HashMap<>()).put(ubicacionId, cantidad);
        }

        try {
            transferenciaService.confirmarLlegada(id, repartoPorDetalle);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/transferencias/" + id + "/confirmar-llegada";
        } catch (org.springframework.dao.DataIntegrityViolationException
                 | org.springframework.dao.OptimisticLockingFailureException e) {
            // A6 / R-C1: colision con otra confirmacion sobre el mismo stock destino.
            ra.addFlashAttribute("error",
                    "Otra operación modificó este stock al mismo tiempo. Vuelve a intentar.");
            return "redirect:/transferencias/" + id + "/confirmar-llegada";
        }
        ra.addFlashAttribute("mensaje", "Llegada confirmada. Stock repartido y actualizado en destino(s).");
        return "redirect:/transferencias";
    }
}
