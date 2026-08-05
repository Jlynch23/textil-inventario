package com.textil.inventario.recepciones;

import org.springframework.security.access.prepost.PreAuthorize;
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

    // Auditoria FAST-01: tope de cordura para las cantidades rapidas (mismo
    // criterio que RecepcionService.MAX_ROLLOS_POR_LINEA). Evita absurdos y el
    // desborde de int al conciliar contra stock.
    private static final int MAX_CANTIDAD_RAPIDA = 1_000_000;

    private static boolean cantidadValida(Integer cantidad) {
        return cantidad != null && cantidad > 0 && cantidad <= MAX_CANTIDAD_RAPIDA;
    }

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
        // Auditoria FAST-01: los @RequestParam se persistian sin validar. Un POST
        // con null/0/negativo (o un absurdo) contaminaba la conciliacion, reportes
        // y auditoria. Se valida en el backend (el front no es garantia).
        if (!cantidadValida(totalRollos)) {
            ra.addFlashAttribute("error", "La cantidad de rollos debe ser un número mayor que cero.");
            return "redirect:/almacen/entrada";
        }
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
        // Auditoria FAST-01: misma validacion de backend que la entrada rapida.
        if (!cantidadValida(cantidad)) {
            ra.addFlashAttribute("error", "La cantidad debe ser un número mayor que cero.");
            return "redirect:/almacen/salida";
        }
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
    // Defensa en profundidad: la cola de revision es del ADMIN. Hasta ahora solo
    // la protegia el orden de las reglas de URL en SecurityConfig; los POST de
    // aprobacion ya llevaban la anotacion, la pantalla que los lista no.
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public String revision(Model model) {
        model.addAttribute("entradas", entradaRapidaRepository.findByEstadoOrderByCreatedAtDesc("PENDIENTE"));
        model.addAttribute("salidas", salidaRapidaRepository.findByEstadoOrderByCreatedAtDesc("PENDIENTE"));
        model.addAttribute("recepciones", recepcionService.listarRecepciones());
        model.addAttribute("transferencias", transferenciaService.listarTransferencias());
        return "almacen/revision";
    }

    @GetMapping("/revision/entrada/{id}/foto")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<org.springframework.core.io.Resource> verFotoEntrada(@PathVariable Long id) throws java.net.MalformedURLException {
        EntradaRapida er = entradaRapidaRepository.findById(id).orElseThrow();
        return servirFoto(er.getFotoRuta());
    }

    @GetMapping("/revision/salida/{id}/foto")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<org.springframework.core.io.Resource> verFotoSalida(@PathVariable Long id) throws java.net.MalformedURLException {
        SalidaRapida sr = salidaRapidaRepository.findById(id).orElseThrow();
        return servirFoto(sr.getFotoRuta());
    }

    private ResponseEntity<org.springframework.core.io.Resource> servirFoto(String ruta) throws java.net.MalformedURLException {
        // FILE-02: unico punto que servia un archivo sin exigir que la ruta caiga
        // DENTRO de documentos.ruta-base. La ruta sale de la BD, pero el resto de
        // los endpoints que sirven archivos ya pasan por esta validacion y no hay
        // motivo para que este sea la excepcion (defensa en profundidad).
        java.nio.file.Path path = documentoStorageService.resolverRutaSegura(ruta);
        org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
        String contentType = ruta.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @PostMapping("/revision/entrada/{id}/marcar")
    // Auditoria (defensa en profundidad): aprobar la cola de revision es solo
    // ADMIN/SUPERADMIN (el SUPERVISOR carga entradas/salidas pero NO las aprueba).
    // A nivel de metodo, NO de clase: este controller tiene escrituras legitimas
    // del SUPERVISOR (entradaGuardar/salidaGuardar).
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
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
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
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
