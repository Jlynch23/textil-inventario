package com.textil.inventario.config;

import com.textil.inventario.seguridad.VistaPreviaRol;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Qué hacer con un 403 cuando la sesión está VIENDO la app como otro rol.
 *
 * <h2>El problema</h2>
 * Un 403 de la cadena de seguridad no renderiza ninguna plantilla, así que tampoco
 * aparece la banda con el botón de "salir de la vista previa". El único camino de
 * vuelta sería borrar la cookie: la sesión queda encerrada en el rol.
 *
 * <h2>Y el arreglo que NO funcionó</h2>
 * El primer intento devolvía al rol a su pantalla de inicio. Daba por hecho que
 * todo rol puede abrir ALGO, y VENDEDOR no puede abrir nada — hoy está sin permisos
 * a propósito, esperando el módulo de Ventas. Resultado: 403 → redirijo a "/" → 403
 * → redirijo a "/" … <b>bucle infinito de redirecciones</b> hasta que el navegador
 * corta.
 *
 * <p>Por eso el destino es SIEMPRE el refugio ({@code /vista-previa/sin-permiso}),
 * que está autorizado por la autoridad del switch y no por rol: cualquier rol
 * previsualizado lo puede abrir, así que no puede rebotar. Y por las dudas, si el
 * 403 viene del refugio mismo, se corta con un 403 de verdad en vez de redirigir —
 * un cinturón más contra el bucle.
 *
 * <p>Fuera de la vista previa el comportamiento es el de siempre: 403 pelado.
 */
public class VistaPreviaAccessDeniedHandler implements AccessDeniedHandler {

    private final String refugio;

    public VistaPreviaAccessDeniedHandler(String refugio) {
        this.refugio = refugio;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException denegado) throws IOException {

        boolean enVistaPrevia = VistaPreviaRol.activa(
                SecurityContextHolder.getContext().getAuthentication());
        boolean yaEsElRefugio = refugio.equals(request.getRequestURI());

        if (enVistaPrevia && !yaEsElRefugio) {
            response.sendRedirect(refugio);
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
