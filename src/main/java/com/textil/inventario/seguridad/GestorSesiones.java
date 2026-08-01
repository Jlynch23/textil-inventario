package com.textil.inventario.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Expira en el acto las sesiones vivas de un usuario (auditoría red-team R-A1).
 *
 * Las authorities y la identidad se resuelven al loguear y quedan cacheadas en
 * la HttpSession (timeout de 8 h); la autorización por URL evalúa esa sesión, no
 * la base de datos. Por eso inactivar, degradar de rol o resetear la contraseña
 * de un usuario NO cortaba su sesión abierta: seguía operando hasta que expirara.
 *
 * Con el SessionRegistry poblado (ver SecurityConfig), marcar sus sesiones como
 * expiradas hace que el ConcurrentSessionFilter las invalide en el siguiente
 * request del usuario, forzándolo a re-autenticar (donde ya se le niega el acceso).
 */
@Component
@RequiredArgsConstructor
public class GestorSesiones {

    private final SessionRegistry sessionRegistry;

    /** Marca como expiradas todas las sesiones activas del username dado. */
    public void cerrarSesionesDe(String username) {
        if (username == null || username.isBlank()) return;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String nombre = principal instanceof UserDetails ud ? ud.getUsername() : principal.toString();
            if (username.equalsIgnoreCase(nombre)) {
                sessionRegistry.getAllSessions(principal, false)
                        .forEach(SessionInformation::expireNow);
            }
        }
    }
}
