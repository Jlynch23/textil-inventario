package com.textil.inventario.seguridad;

import com.textil.inventario.auditoria.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private static final String ROL_SUPERADMIN = "SUPERADMIN";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final UsuarioActualService usuarioActualService;

    /**
     * ¿El usuario autenticado es el proveedor (SUPERADMIN)? Se resuelve de las
     * authorities de la sesion (sin ir a base de datos). Es la clave de toda la
     * ocultacion: SUPERADMIN es una cuenta oculta de soporte del proveedor, y
     * el ADMIN (dueño-cliente) no debe verla ni tocarla.
     */
    private boolean esSuperadmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + ROL_SUPERADMIN).equals(a.getAuthority()));
    }

    private boolean esUsuarioSuperadmin(Usuario u) {
        return u.getRol() != null && ROL_SUPERADMIN.equalsIgnoreCase(u.getRol().getNombre());
    }

    @GetMapping
    public String listar(Model model, Authentication authentication) {
        boolean superadmin = esSuperadmin(authentication);

        // El ADMIN no ve las cuentas SUPERADMIN (proveedor) ni puede asignar ese
        // rol: se filtran tanto la lista de usuarios como el selector de roles.
        List<Usuario> usuarios = usuarioRepository.findAll().stream()
                .filter(u -> superadmin || !esUsuarioSuperadmin(u))
                .toList();
        List<Rol> roles = rolRepository.findAll().stream()
                .filter(r -> superadmin || !ROL_SUPERADMIN.equalsIgnoreCase(r.getNombre()))
                .toList();

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("roles", roles);
        return "usuarios/lista";
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/guardar")
    public String guardar(@RequestParam String nombre,
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam Long rolId,
                           RedirectAttributes ra,
                           Authentication authentication) {
        if (usuarioRepository.existsByUsername(username)) {
            ra.addFlashAttribute("error", "Ya existe un usuario registrado con ese nombre de usuario.");
            return "redirect:/usuarios";
        }
        Rol rol = rolRepository.findById(rolId).orElseThrow();
        // Solo el proveedor (SUPERADMIN) puede crear otra cuenta SUPERADMIN.
        if (ROL_SUPERADMIN.equalsIgnoreCase(rol.getNombre()) && !esSuperadmin(authentication)) {
            ra.addFlashAttribute("error", "No tienes permiso para asignar ese rol.");
            return "redirect:/usuarios";
        }
        if (password == null || password.length() < 6) {
            ra.addFlashAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "redirect:/usuarios";
        }
        // SEC-04 (auditoria 17-jul-2026): BCrypt trunca silenciosamente cualquier
        // entrada mayor a 72 bytes -- sin este chequeo, un usuario podria creer que
        // esta usando una passphrase larga y segura cuando en realidad solo los
        // primeros 72 bytes importan para el login. Se valida en bytes (no
        // caracteres) porque BCrypt trunca por bytes UTF-8, no por longitud de String.
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            ra.addFlashAttribute("error", "La contraseña no puede superar los 72 caracteres (limite tecnico de BCrypt).");
            return "redirect:/usuarios";
        }

        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRol(rol);
        u.setActivo(true);
        Usuario guardado = usuarioRepository.save(u);
        auditLogService.registrar("CREAR", "Usuario", guardado.getId(),
                "Creo el usuario " + guardado.getUsername() + " con rol " + guardado.getRol().getNombre());

        ra.addFlashAttribute("mensaje", "Usuario creado correctamente.");
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/resetear-password/{id}")
    public String resetearPassword(@PathVariable Long id,
                                    @RequestParam String password,
                                    RedirectAttributes ra,
                                    Authentication authentication) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        if (bloqueadoPorSerSuperadmin(u, authentication, ra)) {
            return "redirect:/usuarios";
        }
        if (password == null || password.length() < 6) {
            ra.addFlashAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "redirect:/usuarios";
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            ra.addFlashAttribute("error", "La contraseña no puede superar los 72 caracteres (limite tecnico de BCrypt).");
            return "redirect:/usuarios";
        }

        u.setPasswordHash(passwordEncoder.encode(password));
        usuarioRepository.save(u);
        auditLogService.registrar("RESETEAR_PASSWORD", "Usuario", u.getId(),
                "Reseteo la contraseña de " + u.getUsername());

        ra.addFlashAttribute("mensaje", "Contraseña de " + u.getNombre() + " actualizada correctamente.");
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/inactivar/{id}")
    public String inactivar(@PathVariable Long id, RedirectAttributes ra, Authentication authentication) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        if (bloqueadoPorSerSuperadmin(u, authentication, ra)) {
            return "redirect:/usuarios";
        }
        u.setActivo(false);
        usuarioRepository.save(u);
        auditLogService.registrar("INACTIVAR", "Usuario", u.getId(), "Inactivo el usuario " + u.getUsername());
        ra.addFlashAttribute("mensaje", "Usuario inactivado.");
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/reactivar/{id}")
    public String reactivar(@PathVariable Long id, RedirectAttributes ra, Authentication authentication) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        if (bloqueadoPorSerSuperadmin(u, authentication, ra)) {
            return "redirect:/usuarios";
        }
        u.setActivo(true);
        usuarioRepository.save(u);
        auditLogService.registrar("REACTIVAR", "Usuario", u.getId(), "Reactivo el usuario " + u.getUsername());
        ra.addFlashAttribute("mensaje", "Usuario reactivado.");
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes ra, Authentication authentication) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();

        if (bloqueadoPorSerSuperadmin(u, authentication, ra)) {
            return "redirect:/usuarios";
        }

        if (authentication != null && u.getUsername().equalsIgnoreCase(authentication.getName())) {
            ra.addFlashAttribute("error", "No puedes eliminar tu propio usuario mientras tienes la sesión activa.");
            return "redirect:/usuarios";
        }

        if (esUsuarioSuperadmin(u)) {
            long totalSuperadminsActivos = usuarioRepository.findAll().stream()
                    .filter(x -> Boolean.TRUE.equals(x.getActivo()) && esUsuarioSuperadmin(x))
                    .count();
            if (totalSuperadminsActivos <= 1) {
                ra.addFlashAttribute("error", "No puedes eliminar el único usuario SUPERADMIN activo del sistema.");
                return "redirect:/usuarios";
            }
        }

        try {
            usuarioRepository.deleteById(id);
            auditLogService.registrar("ELIMINAR", "Usuario", id, "Elimino el usuario " + u.getUsername());
            ra.addFlashAttribute("mensaje", "Usuario eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error",
                "No se puede eliminar: este usuario tiene recepciones, transferencias, movimientos de kardex " +
                "u otro historial asociado (esto es intencional, para no perder trazabilidad). Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/usuarios";
    }

    /**
     * Un ADMIN (dueño-cliente) no puede tocar cuentas SUPERADMIN (proveedor):
     * ni las ve en la lista, asi que si llega un id de una por URL directa se
     * responde como si no existiera. Solo el propio SUPERADMIN puede operarlas.
     */
    private boolean bloqueadoPorSerSuperadmin(Usuario u, Authentication authentication, RedirectAttributes ra) {
        if (esUsuarioSuperadmin(u) && !esSuperadmin(authentication)) {
            ra.addFlashAttribute("error", "Usuario no encontrado.");
            return true;
        }
        return false;
    }

    // ---------- AUTOSERVICIO: MI CUENTA ----------
    // Disponible para CUALQUIER usuario autenticado (ver SecurityConfig): permite
    // que el dueño (ADMIN) rote su propia contraseña al recibir la copia, sin
    // depender del proveedor. Rutas /usuarios/mi-cuenta y /usuarios/cambiar-mi-password.

    @GetMapping("/mi-cuenta")
    public String miCuenta(Model model) {
        model.addAttribute("usuario", usuarioActualService.obtenerUsuarioActual());
        return "usuarios/mi-cuenta";
    }

    @PostMapping("/cambiar-mi-password")
    public String cambiarMiPassword(@RequestParam String passwordActual,
                                     @RequestParam String passwordNueva,
                                     @RequestParam String passwordConfirmacion,
                                     RedirectAttributes ra) {
        Usuario u = usuarioActualService.obtenerUsuarioActual();

        if (!passwordEncoder.matches(passwordActual, u.getPasswordHash())) {
            ra.addFlashAttribute("error", "La contraseña actual no es correcta.");
            return "redirect:/usuarios/mi-cuenta";
        }
        if (passwordNueva == null || passwordNueva.length() < 6) {
            ra.addFlashAttribute("error", "La nueva contraseña debe tener al menos 6 caracteres.");
            return "redirect:/usuarios/mi-cuenta";
        }
        if (passwordNueva.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            ra.addFlashAttribute("error", "La contraseña no puede superar los 72 caracteres (limite tecnico de BCrypt).");
            return "redirect:/usuarios/mi-cuenta";
        }
        if (!passwordNueva.equals(passwordConfirmacion)) {
            ra.addFlashAttribute("error", "La nueva contraseña y su confirmación no coinciden.");
            return "redirect:/usuarios/mi-cuenta";
        }

        u.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(u);
        auditLogService.registrar("CAMBIAR_PASSWORD_PROPIA", "Usuario", u.getId(), "Cambio su propia contraseña");

        ra.addFlashAttribute("mensaje", "Tu contraseña se actualizó correctamente.");
        return "redirect:/usuarios/mi-cuenta";
    }
}
