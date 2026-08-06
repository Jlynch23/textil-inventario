package com.textil.inventario.recepciones;

import com.textil.inventario.common.RespuestaJson;
import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
    private final ColorRepository colorRepository;
    private final AnthropicOcrService anthropicOcrService;
    private final ArticuloMatchingService articuloMatchingService;
    private final RecepcionDocumentoRepository recepcionDocumentoRepository;

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
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String nueva(Model model) {
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        model.addAttribute("articulos", articuloRepository.findByActivoTrue());
        model.addAttribute("colores", colorRepository.findByActivoTrueOrderByNombreOficialAsc());
        return "recepciones/nueva";
    }

    @PostMapping("/crear")
    public String crear(@RequestParam Long empresaId,
                        @RequestParam String numeroGuia,
                        @RequestParam(required = false) String numeroFactura,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaGuia,
                        @RequestParam(required = false) String observaciones,
                        RedirectAttributes ra) {
        try {
            Recepcion r = recepcionService.crearRecepcion(empresaId, numeroGuia, numeroFactura, fechaGuia, observaciones);
            ra.addFlashAttribute("mensaje", "Recepción creada. Ahora agrega los detalles.");
            return "redirect:/recepciones/" + r.getId() + "/detalle";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/recepciones/nueva";
        }
    }

    @GetMapping("/{id}/detalle")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("recepcion", recepcionService.buscarRecepcion(id));
        model.addAttribute("articulos", articuloRepository.findByActivoTrueOrdenados());
        model.addAttribute("colores", colorRepository.findByActivoTrueOrderByNombreOficialAsc());
        // Ojitos "ver guía / ver factura": id del PDF de cada tipo, si está archivado
        // (el primero de cada tipo). Si no hay PDF, el ojo no se muestra.
        for (RecepcionDocumento doc : recepcionDocumentoRepository.findByRecepcionId(id)) {
            if ("GUIA".equals(doc.getTipoDocumento()) && !model.containsAttribute("guiaDocId"))
                model.addAttribute("guiaDocId", doc.getId());
            if ("FACTURA".equals(doc.getTipoDocumento()) && !model.containsAttribute("facturaDocId"))
                model.addAttribute("facturaDocId", doc.getId());
        }
        return "recepciones/detalle";
    }

    @PostMapping("/{id}/agregar-linea")
    public String agregarLinea(@PathVariable Long id,
                                @RequestParam Long articuloId,
                                @RequestParam Long colorId,
                                @RequestParam String programaTenido,
                                @RequestParam Integer rollosGuia,
                                @RequestParam(required = false) BigDecimal pesoBrutoKg,
                                RedirectAttributes ra) {
        recepcionService.agregarDetalle(id, articuloId, colorId, programaTenido, rollosGuia, pesoBrutoKg);
        ra.addFlashAttribute("mensaje", "Línea agregada correctamente.");
        return "redirect:/recepciones/" + id + "/detalle";
    }

    @GetMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
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
        try {
            recepcionService.confirmarRecepcion(id, detalleIds, rollosRecibidos, observacionesDetalle);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/recepciones/" + id + "/confirmar";
        } catch (org.springframework.dao.DataIntegrityViolationException
                 | org.springframework.dao.OptimisticLockingFailureException e) {
            // A6 / R-C1: dos confirmaciones simultaneas sobre el mismo stock. El
            // UNIQUE (articulo+ubicacion+color) y el @Version evitan la corrupcion;
            // aca se traduce la colision a un mensaje reintentable en vez de un 500.
            ra.addFlashAttribute("error",
                    "Otra operación modificó este stock al mismo tiempo. Vuelve a intentar.");
            return "redirect:/recepciones/" + id + "/confirmar";
        }
        ra.addFlashAttribute("mensaje", "Recepción confirmada. Stock actualizado.");
        return "redirect:/recepciones";
    }

    @GetMapping("/facturar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String facturarForm(Model model) {
        List<Recepcion> recepciones = recepcionService.listarRecepcionesSinFactura();
        model.addAttribute("recepciones", recepciones);
        // Ojito "ver guía" por fila: mapa recepcionId -> id del documento GUÍA.
        model.addAttribute("guiaPorRecepcion", guiaDocPorRecepcion(recepciones));
        return "recepciones/facturar";
    }

    /**
     * Para las recepciones mostradas, el id del documento GUÍA (PDF) de cada una,
     * para el ojito "ver guía". Un solo query (sin N+1). Solo entran las que tienen
     * guía adjunta; el resto no muestra ojo.
     */
    private Map<Long, Long> guiaDocPorRecepcion(List<Recepcion> recepciones) {
        if (recepciones == null || recepciones.isEmpty()) return Map.of();
        List<Long> ids = recepciones.stream().map(Recepcion::getId).toList();
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : recepcionDocumentoRepository.guiaDocPorRecepcion(ids)) {
            mapa.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        return mapa;
    }

    @PostMapping("/extraer-factura")
    @ResponseBody
    public ResponseEntity<?> extraerFactura(@RequestParam("file") MultipartFile file) {
        // La validacion del PDF (vacio / tamaño / no es PDF) lanza
        // IllegalArgumentException; el helper la traduce a 400 con su mensaje.
        return RespuestaJson.responder("extraerFactura",
                () -> anthropicOcrService.extraerDatosFactura(file));
    }

    @PostMapping("/asignar-factura")
    @ResponseBody
    public ResponseEntity<?> asignarFactura(@RequestBody AsignarFacturaRequest request) {
        return RespuestaJson.responder("asignarFactura", () -> {
                recepcionService.asignarFactura(request.numeroFactura(), request.fechaFactura(), request.recepcionIds());
                return Map.of("ok", true);
        });
    }

    @PostMapping("/{id}/guardar-documento-guia")
    @ResponseBody
    public ResponseEntity<?> guardarDocumentoGuia(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return RespuestaJson.responder("guardarDocumentoGuia", () -> {
            recepcionService.guardarDocumentoGuia(id, file);
            return Map.of("ok", true);
        });
    }

    @PostMapping("/guardar-documento-factura")
    @ResponseBody
    public ResponseEntity<?> guardarDocumentoFactura(@RequestParam("recepcionIds") List<Long> recepcionIds,
                                                       @RequestParam("file") MultipartFile file) {
        return RespuestaJson.responder("guardarDocumentoFactura", () -> {
            recepcionService.guardarDocumentoFactura(recepcionIds, file);
            return Map.of("ok", true);
        });
    }

    @PostMapping("/extraer-guia")
    @ResponseBody
    public ResponseEntity<?> extraerGuia(@RequestParam("file") MultipartFile file) {
        // La validacion del PDF (vacio / tamaño / no es PDF) lanza
        // IllegalArgumentException; el helper la traduce a 400 con su mensaje.
        return RespuestaJson.responder("extraerGuia", () -> {
            ExtraccionGuiaResponse extraccion = anthropicOcrService.extraerDatosGuia(file);
            List<Empresa> empresas = empresaRepository.findByActivoTrue();

            // El RUC manda: identifica a la empresa sin ambiguedad. La razon
            // social viaja solo como respaldo para guias que no lo traen legible.
            Long empresaIdSugerida = articuloMatchingService.matchEmpresa(
                    extraccion.rucDetectado(), extraccion.razonSocialDetectada(), empresas);

            List<LineaSugerida> lineas = extraccion.productos().stream()
                    .map(articuloMatchingService::matchLinea)
                    .toList();

            ExtraccionRecepcionResponse response = new ExtraccionRecepcionResponse(
                    extraccion.numeroGuia(),
                    extraccion.numeroFactura(),
                    // El modelo copia la fecha como esta impresa (DD/MM/AAAA en
                    // las guias peruanas); reordenarla es trabajo de Java, que
                    // no confunde el dia con el mes. Ver FechaDocumento.
                    com.textil.inventario.common.FechaDocumento.aIso(extraccion.fechaGuia()),
                    empresaIdSugerida,
                    extraccion.razonSocialDetectada(),
                    extraccion.emisorNombre(),
                    extraccion.emisorRuc(),
                    lineas,
                    extraccion.advertencia()
            );

            return response;
        });
    }

    @PostMapping("/rematch-linea")
    @ResponseBody
    public ResponseEntity<?> rematchLinea(@RequestBody ProductoExtraido producto) {
        return RespuestaJson.responder("rematchLinea", () -> {
                LineaSugerida resultado = articuloMatchingService.matchLinea(producto);
                return resultado;
        });
    }

    @PostMapping("/crear-con-lineas")
    @ResponseBody
    public ResponseEntity<?> crearConLineas(@RequestBody CrearRecepcionConLineasRequest request) {
        return RespuestaJson.responder("crearConLineas", () -> {
                Recepcion r = recepcionService.crearRecepcionConLineas(
                        request.empresaId(), request.numeroGuia(), request.numeroFactura(), request.fechaGuia(),
                        request.observaciones(), request.emisorNombre(), request.emisorRuc(), request.lineas());
                return Map.of("id", r.getId(), "redirectUrl", "/recepciones/" + r.getId() + "/detalle");
        });
    }
}
