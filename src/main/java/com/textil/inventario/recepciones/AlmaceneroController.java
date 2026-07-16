package com.textil.inventario.recepciones;

import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioActualService;
import com.textil.inventario.transferencias.TransferenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/almacen")
@RequiredArgsConstructor
public class AlmaceneroController {

    private final EntradaRapidaRepository entradaRapidaRepository;
    private final SalidaRapidaRepository salidaRapidaRepository;
    private final DocumentoStorageService documentoStorageService;
    private final RecepcionService recepcionService;
    private final TransferenciaService transferenciaService;
    private final UsuarioActualService usuarioActualService;
    private final com.textil.inventario.auditoria.AuditLogService auditLogService;

    @GetMapping
    public String home() {
        return "almacen/home";
    }

    @GetMapping("/entrada")
    public String entradaForm() {
        return "almacen/entrada";
    }

    @PostMapping("/entrada")
    public String entradaGuardar(@RequestParam Integer totalRollos,
                                  @RequestParam("foto") MultipartFile foto,
                                  RedirectAttributes ra) throws java.io.IOException {
        Usuario usuario = usuarioActualService.obtenerUsuarioActual();

        String ruta = documentoStorageService.guardarFotoRapida(foto, "Entradas");

        EntradaRapida er = new EntradaRapida();
        er.setUsuario(usuario);
        er.setTotalRollos(totalRollos);
        er.setFotoRuta(ruta);
        entradaRapidaRepository.save(er);
        auditLogService.registrar("CREAR", "EntradaRapida", er.getId(),
                usuario.getNombre() + " registro entrada rapida de " + totalRollos + " rollos");

        ra.addFlashAttribute("mensaje", "Entrada registrada correctamente.");
        return "redirect:/almacen";
    }

    @GetMapping("/salida")
    public String salidaForm() {
        return "almacen/salida";
    }

    @PostMapping("/salida")
    public String salidaGuardar(@RequestParam Integer cantidad,
                                 @RequestParam("foto") MultipartFile foto,
                                 RedirectAttributes ra) throws java.io.IOException {
        Usuario usuario = usuarioActualService.obtenerUsuarioActual();

        String ruta = documentoStorageService.guardarFotoRapida(foto, "Salidas");

        SalidaRapida sr = new SalidaRapida();
        sr.setUsuario(usuario);
        sr.setCantidad(cantidad);
        sr.setFotoRuta(ruta);
        salidaRapidaRepository.save(sr);
        auditLogService.registrar("CREAR", "SalidaRapida", sr.getId(),
                usuario.getNombre() + " registro salida rapida de " + cantidad + " rollos");

        ra.addFlashAttribute("mensaje", "Salida registrada correctamente.");
        return "redirect:/almacen";
    }

    // ─── REVISION ADMIN ───────────────────────────────────────

    @GetMapping("/revision")
    public String revision(Model model) {
        model.addAttribute("entradas", entradaRapidaRepository.findByEstadoOrderByCreatedAtDesc("PENDIENTE"));
        model.addAttribute("salidas", salidaRapidaRepository.findByEstadoOrderByCreatedAtDesc("PENDIENTE"));
        model.addAttribute("recepciones", recepcionService.listarRecepciones());
        model.addAttribute("transferencias", transferenciaService.listarTransferencias());
        return "almacen/revision";
    }

    @GetMapping("/revision/entrada/{id}/foto")
    public ResponseEntity<org.springframework.core.io.Resource> verFotoEntrada(@PathVariable Long id) throws java.net.MalformedURLException {
        EntradaRapida er = entradaRapidaRepository.findById(id).orElseThrow();
        return servirFoto(er.getFotoRuta());
    }

    @GetMapping("/revision/salida/{id}/foto")
    public ResponseEntity<org.springframework.core.io.Resource> verFotoSalida(@PathVariable Long id) throws java.net.MalformedURLException {
        SalidaRapida sr = salidaRapidaRepository.findById(id).orElseThrow();
        return servirFoto(sr.getFotoRuta());
    }

    private ResponseEntity<org.springframework.core.io.Resource> servirFoto(String ruta) throws java.net.MalformedURLException {
        java.nio.file.Path path = java.nio.file.Paths.get(ruta);
        org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
        String contentType = ruta.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @PostMapping("/revision/entrada/{id}/marcar")
    public String marcarEntrada(@PathVariable Long id,
                                 @RequestParam(required = false) Long recepcionId,
                                 @RequestParam(required = false) String observaciones,
                                 RedirectAttributes ra) {
        EntradaRapida er = entradaRapidaRepository.findById(id).orElseThrow();
        er.setEstado("REVISADO");
        er.setObservacionesAdmin(observaciones);
        if (recepcionId != null) {
            er.setRecepcion(recepcionService.buscarRecepcion(recepcionId));
        }
        entradaRapidaRepository.save(er);
        auditLogService.registrar("EDITAR", "EntradaRapida", er.getId(), "Marco entrada rapida como revisada");
        ra.addFlashAttribute("mensaje", "Entrada marcada como revisada.");
        return "redirect:/almacen/revision";
    }

    @PostMapping("/revision/salida/{id}/marcar")
    public String marcarSalida(@PathVariable Long id,
                                @RequestParam(required = false) Long transferenciaId,
                                @RequestParam(required = false) String observaciones,
                                RedirectAttributes ra) {
        SalidaRapida sr = salidaRapidaRepository.findById(id).orElseThrow();
        sr.setEstado("REVISADO");
        sr.setObservacionesAdmin(observaciones);
        if (transferenciaId != null) {
            sr.setTransferencia(transferenciaService.buscarTransferencia(transferenciaId));
        }
        salidaRapidaRepository.save(sr);
        auditLogService.registrar("EDITAR", "SalidaRapida", sr.getId(), "Marco salida rapida como revisada");
        ra.addFlashAttribute("mensaje", "Salida marcada como revisada.");
        return "redirect:/almacen/revision";
    }
}
