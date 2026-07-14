package com.textil.inventario.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioRepository.findAll());
        model.addAttribute("roles", rolRepository.findAll());
        return "usuarios/lista";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam String nombre,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam Long rolId,
                           RedirectAttributes ra) {
        if (usuarioRepository.existsByEmail(email)) {
            ra.addFlashAttribute("error", "Ya existe un usuario registrado con ese email.");
            return "redirect:/usuarios";
        }
        if (password == null || password.length() < 6) {
            ra.addFlashAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "redirect:/usuarios";
        }

        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRol(rolRepository.findById(rolId).orElseThrow());
        u.setActivo(true);
        usuarioRepository.save(u);

        ra.addFlashAttribute("mensaje", "Usuario creado correctamente.");
        return "redirect:/usuarios";
    }

    @GetMapping("/inactivar/{id}")
    public String inactivar(@PathVariable Long id, RedirectAttributes ra) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        u.setActivo(false);
        usuarioRepository.save(u);
        ra.addFlashAttribute("mensaje", "Usuario inactivado.");
        return "redirect:/usuarios";
    }

    @GetMapping("/reactivar/{id}")
    public String reactivar(@PathVariable Long id, RedirectAttributes ra) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        u.setActivo(true);
        usuarioRepository.save(u);
        ra.addFlashAttribute("mensaje", "Usuario reactivado.");
        return "redirect:/usuarios";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes ra, Authentication authentication) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();

        if (authentication != null && u.getEmail().equalsIgnoreCase(authentication.getName())) {
            ra.addFlashAttribute("error", "No puedes eliminar tu propio usuario mientras tienes la sesión activa.");
            return "redirect:/usuarios";
        }

        boolean esSuperadmin = u.getRol() != null && "SUPERADMIN".equalsIgnoreCase(u.getRol().getNombre());
        if (esSuperadmin) {
            long totalSuperadminsActivos = usuarioRepository.findAll().stream()
                    .filter(x -> Boolean.TRUE.equals(x.getActivo())
                            && x.getRol() != null
                            && "SUPERADMIN".equalsIgnoreCase(x.getRol().getNombre()))
                    .count();
            if (totalSuperadminsActivos <= 1) {
                ra.addFlashAttribute("error", "No puedes eliminar el único usuario SUPERADMIN activo del sistema.");
                return "redirect:/usuarios";
            }
        }

        try {
            usuarioRepository.deleteById(id);
            ra.addFlashAttribute("mensaje", "Usuario eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error",
                "No se puede eliminar: este usuario tiene recepciones, transferencias, movimientos de kardex " +
                "u otro historial asociado (esto es intencional, para no perder trazabilidad). Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/usuarios";
    }
}
