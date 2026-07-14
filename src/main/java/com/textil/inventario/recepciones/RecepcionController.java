package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes ra) {
        try {
            recepcionService.eliminarRecepcion(id);
            ra.addFlashAttribute("mensaje", "Recepción eliminada correctamente.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/recepciones";
    }

    @GetMapping("/verificar-guia")
    @ResponseBody
    public ResponseEntity<?> verificarGuia(@RequestParam String numero) {
        return recepcionService.buscarPorNumeroGuia(numero)
                .map(r -> ResponseEntity.ok(Map.of(
                        "existe", true,
                        "empresa", r.getEmpresa().getNombre(),
                        "fecha", r.getFechaGuia().toString(),
                        "estado", r.getEstado().toString()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of("existe", false)));
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
                        @RequestParam(required = false) String numeroFactura,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaGuia,
                        @RequestParam(required = false) String observaciones,
                        RedirectAttributes ra) {
        Recepcion r = recepcionService.crearRecepcion(empresaId, numeroGuia, numeroFactura, fechaGuia, observaciones);
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

    @GetMapping("/facturar")
    public String facturarForm(Model model) {
        model.addAttribute("recepciones", recepcionService.listarRecepcionesSinFactura());
        return "recepciones/facturar";
    }

    @PostMapping("/extraer-factura")
    @ResponseBody
    public ResponseEntity<?> extraerFactura(@RequestParam("file") MultipartFile file) {
        try {
            ExtraccionFacturaResponse resultado = anthropicOcrService.extraerDatosFactura(file);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error en extraerFactura: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/asignar-factura")
    @ResponseBody
    public ResponseEntity<?> asignarFactura(@RequestBody AsignarFacturaRequest request) {
        try {
            recepcionService.asignarFactura(request.numeroFactura(), request.fechaFactura(), request.recepcionIds());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("Error en asignarFactura: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/{id}/guardar-documento-guia")
    @ResponseBody
    public ResponseEntity<?> guardarDocumentoGuia(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            recepcionService.guardarDocumentoGuia(id, file);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("Error en guardarDocumentoGuia: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/guardar-documento-factura")
    @ResponseBody
    public ResponseEntity<?> guardarDocumentoFactura(@RequestParam("recepcionIds") List<Long> recepcionIds,
                                                       @RequestParam("file") MultipartFile file) {
        try {
            recepcionService.guardarDocumentoFactura(recepcionIds, file);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("Error en guardarDocumentoFactura: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
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
                    extraccion.numeroFactura(),
                    extraccion.fechaGuia(),
                    empresaIdSugerida,
                    extraccion.razonSocialDetectada(),
                    lineas,
                    extraccion.advertencia()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error en extraerGuia: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/rematch-linea")
    @ResponseBody
    public ResponseEntity<?> rematchLinea(@RequestBody ProductoExtraido producto) {
        try {
            LineaSugerida resultado = articuloMatchingService.matchLinea(producto);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error en rematchLinea: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/crear-con-lineas")
    @ResponseBody
    public ResponseEntity<?> crearConLineas(@RequestBody CrearRecepcionConLineasRequest request) {
        try {
            Recepcion r = recepcionService.crearRecepcionConLineas(
                    request.empresaId(), request.numeroGuia(), request.numeroFactura(), request.fechaGuia(),
                    request.observaciones(), request.lineas());
            return ResponseEntity.ok(Map.of("id", r.getId(), "redirectUrl", "/recepciones/" + r.getId() + "/detalle"));
        } catch (Exception e) {
            log.error("Error en crearConLineas: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }
}
