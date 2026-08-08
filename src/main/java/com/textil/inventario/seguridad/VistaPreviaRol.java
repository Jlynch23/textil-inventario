package com.textil.inventario.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;

/**
 * "Ver la página como GERENTE / ADMIN / SUPERVISOR": el SUPERADMIN cambia
 * temporalmente el rol efectivo de SU sesión para ver la app como la ve otro rol.
 * <p>
 * Sirve para lo que no se puede revisar leyendo código: qué entradas de menú
 * aparecen, qué botones se muestran, si una pantalla luce coherente sin los
 * paneles de escritura. Es exactamente el tipo de defecto que ni CI ni los tests
 * agarran -- por ejemplo, un enlace visible que después devuelve 403.
 *
 * <h2>Es un ROL sintético, no un usuario real</h2>
 * A propósito. Suplantar a una persona real ensucia la trazabilidad y, además,
 * no funcionaría acá: una instancia recién entregada (o dev y el demo hoy) tiene
 * UNA sola cuenta. Con un rol sintético la vista previa anda con la base vacía de
 * usuarios, que es justo cuando más se necesita.
 * <p>
 * El principal se llama {@code vista:GERENTE} y NO existe en la tabla usuarios.
 * De eso se desprende todo lo demás:
 * <ul>
 *   <li>No se puede loguear: este {@code UserDetailsService} lo usa SOLO el
 *       SwitchUserFilter, nunca el AuthenticationManager del formulario.</li>
 *   <li>Cualquier código que busque el Usuario en base no lo encuentra. Por eso
 *       la vista previa es de SOLO LECTURA (ver VistaPreviaSoloLecturaFilter):
 *       no es una restricción de prudencia, es que un alta sin usuario en base
 *       no tiene a quién atribuirse.</li>
 * </ul>
 *
 * <h2>SUPERADMIN no se puede previsualizar</h2>
 * No tendría sentido (ya lo sos) y evita la pregunta incómoda de si se puede
 * escalar de vuelta. La lista de roles ofrecidos lo excluye y el servicio lo
 * rechaza aunque se pida a mano.
 */
public final class VistaPreviaRol {

    /** Prefijo del username sintético. No colisiona: `usuarios.username` sale de
     *  GeneradorUsername (letras sin tildes), nunca trae ':'. */
    public static final String PREFIJO = "vista:";

    /** Rol que NUNCA se previsualiza. */
    public static final String ROL_EXCLUIDO = "SUPERADMIN";

    private VistaPreviaRol() {}

    public static String usernameDe(String rol) {
        return PREFIJO + rol.toUpperCase();
    }

    /**
     * ¿Esta sesión está viendo la app como otro rol?
     * <p>
     * Se pregunta por la autoridad que deja el SwitchUserFilter
     * ({@link SwitchUserGrantedAuthority}, ROLE_PREVIOUS_ADMINISTRATOR), no por el
     * nombre del principal: es la autoridad la que habilita el "volver", así que
     * es la fuente de verdad de que hay una sesión que restaurar.
     */
    public static boolean activa(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (a instanceof SwitchUserGrantedAuthority) return true;
        }
        return false;
    }

    /** El rol que se está viendo ("GERENTE"), o null si no hay vista previa. */
    public static String rolActual(Authentication auth) {
        if (!activa(auth)) return null;
        String nombre = auth.getName();
        return nombre != null && nombre.startsWith(PREFIJO)
                ? nombre.substring(PREFIJO.length())
                : null;
    }
}
