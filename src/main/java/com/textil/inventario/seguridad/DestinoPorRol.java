package com.textil.inventario.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * A qué pantalla mandar a alguien según su rol.
 * <p>
 * No todos los roles pueden ver la misma página de inicio: el SUPERVISOR NO tiene
 * permiso sobre {@code /} ni {@code /dashboard} (ver SecurityConfig: esa regla es
 * GERENTE+ADMIN+SUPERADMIN), su pantalla es {@code /almacen}. Mandarlo al dashboard
 * no le muestra un menú recortado -- le da un 403.
 * <p>
 * Vive acá, y no repetido en cada handler, porque lo necesitan TRES lugares y basta
 * con que uno se olvide para que el rol quede en una pantalla que no puede abrir:
 * <ul>
 *   <li>al loguearse (AuthenticationSuccessHandler),</li>
 *   <li>al entrar o salir de la vista previa por rol (SwitchUserFilter),</li>
 *   <li>al rebotar contra un 403 (AccessDeniedHandler), para dejarlo en un lugar
 *       del que pueda seguir navegando.</li>
 * </ul>
 * Mordió en el segundo: la vista previa redirigía siempre a {@code /} y elegir
 * SUPERVISOR daba 403 -- y sin página, tampoco se veía el botón para salir.
 */
public final class DestinoPorRol {

    public static final String INICIO = "/";
    public static final String ALMACEN = "/almacen";

    private DestinoPorRol() {}

    /** Pantalla de inicio que ese rol SÍ puede abrir. */
    public static String destinoPara(Authentication auth) {
        if (auth == null) return INICIO;
        boolean esSupervisor = tiene(auth, "ROLE_SUPERVISOR");
        boolean esSuperadmin = tiene(auth, "ROLE_SUPERADMIN");
        // El SUPERADMIN previsualizando SUPERVISOR ya no lleva ROLE_SUPERADMIN
        // (el switch reemplaza las authorities), asi que cae en /almacen, que es
        // justo lo que se quiere ver. La condicion sigue estando para el caso de
        // una cuenta real que tuviera los dos roles.
        return (esSupervisor && !esSuperadmin) ? ALMACEN : INICIO;
    }

    private static boolean tiene(Authentication auth, String authority) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (authority.equals(a.getAuthority())) return true;
        }
        return false;
    }
}
