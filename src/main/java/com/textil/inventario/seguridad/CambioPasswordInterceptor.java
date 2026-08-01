package com.textil.inventario.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Obliga a un usuario con debe_cambiar_password = TRUE a fijar una clave nueva
 * antes de usar el sistema (auditoría A1). Mientras el flag esté puesto, toda
 * navegación se redirige a "Mi cuenta"; solo se permiten la propia página de
 * cambio de contraseña, el logout y los estáticos.
 *
 * El flag se lee de la BD UNA vez por sesión y se cachea en la HttpSession para
 * no consultar en cada request. {@link UsuarioController#cambiarMiPassword}
 * apaga el flag (en BD y en la sesión) al rotar la clave.
 */
@Component
@RequiredArgsConstructor
public class CambioPasswordInterceptor implements HandlerInterceptor {

    /** Atributo de sesión que cachea si el usuario debe cambiar su contraseña. */
    public static final String ATTR_DEBE_CAMBIAR = "DEBE_CAMBIAR_PASSWORD";

    private final UsuarioRepository usuarioRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }

        HttpSession session = request.getSession();
        Boolean debe = (Boolean) session.getAttribute(ATTR_DEBE_CAMBIAR);
        if (debe == null) {
            debe = usuarioRepository.findByUsername(auth.getName())
                    .map(u -> Boolean.TRUE.equals(u.getDebeCambiarPassword()))
                    .orElse(false);
            session.setAttribute(ATTR_DEBE_CAMBIAR, debe);
        }
        if (!Boolean.TRUE.equals(debe)) {
            return true;
        }

        if (rutaPermitida(request.getRequestURI())) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/usuarios/mi-cuenta?forzar=1");
        return false;
    }

    private boolean rutaPermitida(String uri) {
        return uri.equals("/usuarios/mi-cuenta")
                || uri.equals("/usuarios/cambiar-mi-password")
                || uri.equals("/logout")
                || uri.equals("/favicon.ico")
                || uri.equals("/manifest.webmanifest")
                || uri.equals("/sw.js")
                || uri.equals("/offline.html")
                || uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/img/")
                // Bootstrap e iconos se sirven de /webjars/ (M11). Sin esto, la
                // pantalla de cambio forzado de clave redirige tambien el CSS a
                // mi-cuenta y queda SIN estilos.
                || uri.startsWith("/webjars/");
    }
}
