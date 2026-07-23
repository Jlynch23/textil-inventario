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
    private final GeneradorUsername generadorUsername;

    /**
     * ¿El usuario autenticado es el proveedor (SUPERADMIN)? Se resuelve de las
     * authorities de la sesion (sin ir a base de datos). Es la clave de toda la
     * ocultacion.
     */
    private boolean esSuperadmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + ROL_SUPERADMIN).equals(a.getAuthority()));
    }

    private boolean esUsuarioSuperadmin(Usuario u) {
        return u.getRol() != null && ROL_SUPERADMIN.equalsIgnoreCase(u.getRol().getNombre());
    }

    /**
     * Cuentas OCULTAS para el ADMIN: las SUPERADMIN (proveedor) y las de PRUEBA.
     * El ADMIN ni las ve ni las puede tocar; solo el SUPERADMIN.
     */
    private boolean esOculto(Usuario u) {
        return esUsuarioSuperadmin(u) || Boolean.TRUE.equals(u.getEsPrueba());
    }

    @GetMapping
    public String listar(Model model, Authentication authentication) {
        boolean superadmin = esSuperadmin(authentication);

        // El ADMIN no ve las cuentas ocultas (SUPERADMIN + prueba) ni puede
        // asignar el rol SUPERADMIN: se filtran lista y selector de roles.
        List<Usuario> usuarios = usuarioRepository.findAll().stream()
                .filter(u -> superadmin || !esOculto(u))
                .toList();
        // Ordenados por jerarquia (mayor -> menor poder), no por id.
        List<Rol> roles = rolRepository.findAllByOrderByJerarquiaAsc().stream()
                .filter(r -> superadmin || !ROL_SUPERADMIN.equalsIgnoreCase(r.getNombre()))
                .toList();

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("roles", roles);
        return "usuarios/lista";
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/guardar")
    public String guardar(@RequestParam String nombre,
                           @RequestParam String password,
                           @RequestParam Long rolId,
                           RedirectAttributes ra,
                           Authentication authentication) {
        Rol rol = rolRepository.findById(rolId).orElseThrow();
        // Solo el proveedor (SUPERADMIN) puede crear otra cuenta SUPERADMIN.
        if (ROL_SUPERADMIN.equalsIgnoreCase(rol.getNombre()) && !esSuperadmin(authentication)) {
            ra.addFlashAttribute("error", "No tienes permiso para asignar ese rol.");
            return "redirect:/usuarios";
        }
        String errorPassword = validarPassword(password);
        if (errorPassword != null) {
            ra.addFlashAttribute("error", errorPassword);
            return "redirect:/usuarios";
        }
        if (nombre == null || nombre.isBlank()) {
            ra.addFlashAttribute("error", "El nombre es obligatorio.");
            return "redirect:/usuarios";
        }

        // El username se genera del nombre (ej. "Oscar Clemente" -> "oclemente").
        String username = generadorUsername.generar(nombre);

        Usuario u = new Usuario();
        u.setNombre(nombre.trim());
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRol(rol);
        u.setActivo(true);
        u.setEsPrueba(false);
        Usuario guardado = usuarioRepository.save(u);
        auditLogService.registrar("CREAR", "Usuario", guardado.getId(),
                "Creo el usuario " + guardado.getUsername() + " con rol " + guardado.getRol().getNombre());

        ra.addFlashAttribute("mensaje", "Usuario creado: " + guardado.getUsername());
        return "redirect:/usuarios";
    }

    /**
     * Edita nombre, rol y (opcionalmente) contraseña. Al cambiar el nombre se
     * REGENERA el username. Pensado para dar de alta un empleado sobre una
     * cuenta existente sin borrar/recrear.
     */
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/editar/{id}")
    public String editar(@PathVariable Long id,
                          @RequestParam String nombre,
                          @RequestParam Long rolId,
                          @RequestParam(required = false) String password,
                          RedirectAttributes ra,
                          Authentication authentication) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        if (bloqueadoPorOculto(u, authentication, ra)) {
            return "redirect:/usuarios";
        }
        if (nombre == null || nombre.isBlank()) {
            ra.addFlashAttribute("error", "El nombre es obligatorio.");
            return "redirect:/usuarios";
        }
        Rol rol = rolRepository.findById(rolId).orElseThrow();
        // El ADMIN no puede asignar (ni quitar) el rol SUPERADMIN.
        if (ROL_SUPERADMIN.equalsIgnoreCase(rol.getNombre()) && !esSuperadmin(authentication)) {
            ra.addFlashAttribute("error", "No tienes permiso para asignar ese rol.");
            return "redirect:/usuarios";
        }
        // Contraseña opcional: si viene en blanco, no se cambia.
        if (password != null && !password.isBlank()) {
            String errorPassword = validarPassword(password);
            if (errorPassword != null) {
                ra.addFlashAttribute("error", errorPassword);
                return "redirect:/usuarios";
            }
            u.setPasswordHash(passwordEncoder.encode(password));
        }

        u.setNombre(nombre.trim());
        u.setRol(rol);
        // Regenera el username del nuevo nombre, excluyendo al propio usuario.
        u.setUsername(generadorUsername.generar(nombre, u.getId()));
        usuarioRepository.save(u);
        auditLogService.registrar("EDITAR", "Usuario", u.getId(),
                "Edito el usuario: ahora " + u.getUsername() + " (" + u.getRol().getNombre() + ")");

        ra.addFlashAttribute("mensaje", "Usuario actualizado: " + u.getUsername());
        return "redirect:/usuarios";
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/resetear-password/{id}")
    public String resetearPassword(@PathVariable Long id,
                                    @RequestParam String password,
                                    RedirectAttributes ra,
                                    Authentication authentication) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        if (bloqueadoPorOculto(u, authentication, ra)) {
            return "redirect:/usuarios";
        }
        String errorPassword = validarPassword(password);
        if (errorPassword != null) {
            ra.addFlashAttribute("error", errorPassword);
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
        if (bloqueadoPorOculto(u, authentication, ra)) {
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
        if (bloqueadoPorOculto(u, authentication, ra)) {
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

        if (bloqueadoPorOculto(u, authentication, ra)) {
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

    // Valida una contraseña nueva. Devuelve el mensaje de error, o null si es válida.
    // SEC-04: BCrypt trunca en 72 bytes UTF-8, por eso se valida en bytes.
    private String validarPassword(String password) {
        if (password == null || password.length() < 6) {
            return "La contraseña debe tener al menos 6 caracteres.";
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return "La contraseña no puede superar los 72 caracteres (limite tecnico de BCrypt).";
        }
        return null;
    }

    /**
     * El ADMIN no puede tocar cuentas ocultas (SUPERADMIN o de prueba): ni las ve
     * en la lista, asi que si llega un id de una por URL directa se responde como
     * si no existiera. Solo el SUPERADMIN las opera.
     */
    private boolean bloqueadoPorOculto(Usuario u, Authentication authentication, RedirectAttributes ra) {
        if (esOculto(u) && !esSuperadmin(authentication)) {
            ra.addFlashAttribute("error", "Usuario no encontrado.");
            return true;
        }
        return false;
    }

    // ---------- AUTOSERVICIO: MI CUENTA ----------
    // Disponible para CUALQUIER usuario autenticado (ver SecurityConfig): permite
    // que cada quien rote su propia contraseña. Rutas /usuarios/mi-cuenta y
    // /usuarios/cambiar-mi-password.

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
        String errorPassword = validarPassword(passwordNueva);
        if (errorPassword != null) {
            ra.addFlashAttribute("error", errorPassword.replace("La contraseña", "La nueva contraseña"));
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
