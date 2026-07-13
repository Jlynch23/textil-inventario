package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.TipoTelaRepository;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
    private final UsuarioRepository usuarioRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final ColorRepository colorRepository;
    private final DocumentoStorageService documentoStorageService;

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
                                  Authentication auth,
                                  RedirectAttributes ra) throws java.io.IOException {
        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();

        String ruta = documentoStorageService.guardarFotoRapida(foto, "Entradas");

        EntradaRapida er = new EntradaRapida();
        er.setUsuario(usuario);
        er.setTotalRollos(totalRollos);
        er.setFotoRuta(ruta);
        entradaRapidaRepository.save(er);

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
                                 Authentication auth,
                                 RedirectAttributes ra) throws java.io.IOException {
        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElseThrow();

        String ruta = documentoStorageService.guardarFotoRapida(foto, "Salidas");

        SalidaRapida sr = new SalidaRapida();
        sr.setUsuario(usuario);
        sr.setCantidad(cantidad);
        sr.setFotoRuta(ruta);
        salidaRapidaRepository.save(sr);

        ra.addFlashAttribute("mensaje", "Salida registrada correctamente.");
        return "redirect:/almacen";
    }
}
