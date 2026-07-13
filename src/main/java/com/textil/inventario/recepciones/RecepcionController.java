package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/recepciones")
@RequiredArgsConstructor
public class RecepcionController {

    private final RecepcionService recepcionService;
    private final EmpresaRepository empresaRepository;
    private final ArticuloRepository articuloRepository;
    private final AnthropicOcrService anthropicOcrService;
    private final ArticuloMatchingService articuloMatchingService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("recepciones", recepcionService.listarRecepciones());
        return "recepciones/lista";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        model.addAttribute("articulos", articuloRepository.findByActivoTrue());
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

    @PostMapping("/extraer-guia")
    @ResponseBody
    public ResponseEntity<?> extraerGuia(@RequestParam("file") MultipartFile file) {
        try {
            ExtraccionGuiaResponse extraccion = anthropicOcrService.extraerDatosGuia(file);
            List<Empresa> empresas = empresaRepository.findByActivoTrue();

            Long empresaIdSugerida = articuloMatchingService.matchEmpresa(
                    extraccion.razonSocialDetectada(), empresas);

            List<LineaSugerida> lineas = extraccion.productos().stream()
                    .map(articuloMatchingService::matchLinea)
                    .toList();

            ExtraccionRecepcionResponse response = new ExtraccionRecepcionResponse(
                    extraccion.numeroGuia(),
                    extraccion.fechaGuia(),
                    empresaIdSugerida,
                    extraccion.razonSocialDetectada(),
                    lineas,
                    extraccion.advertencia()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rematch-linea")
    @ResponseBody
    public ResponseEntity<?> rematchLinea(@RequestBody ProductoExtraido producto) {
        try {
            LineaSugerida resultado = articuloMatchingService.matchLinea(producto);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/crear-con-lineas")
    @ResponseBody
    public ResponseEntity<?> crearConLineas(@RequestBody CrearRecepcionConLineasRequest request) {
        try {
            Recepcion r = recepcionService.crearRecepcionConLineas(
                    request.empresaId(), request.numeroGuia(), request.fechaGuia(),
                    request.observaciones(), request.lineas());
            return ResponseEntity.ok(Map.of("id", r.getId(), "redirectUrl", "/recepciones/" + r.getId() + "/detalle"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
