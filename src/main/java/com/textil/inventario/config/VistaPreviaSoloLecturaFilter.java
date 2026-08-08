package com.textil.inventario.config;

import com.textil.inventario.seguridad.VistaPreviaRol;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Mientras el SUPERADMIN está VIENDO la app como otro rol, la sesión es de SOLO
 * LECTURA: se rechaza todo lo que no sea GET.
 *
 * <h2>Por qué, en concreto</h2>
 * <ol>
 *   <li><b>No hay a quién atribuir la escritura.</b> El principal de la vista
 *       previa ({@code vista:GERENTE}) no existe en la tabla usuarios, y
 *       {@code UsuarioActualService.obtenerUsuarioActual()} -- que usan
 *       RecepcionService y TransferenciaService para sellar quién hizo cada
 *       movimiento -- no lo encontraría. Sin este filtro, un POST reventaría a
 *       mitad de camino, y peor: podría hacerlo DESPUÉS de tocar algo.</li>
 *   <li><b>Envenenaría la trazabilidad.</b> El kardex y el log de auditoría son
 *       el argumento de venta del producto. Una fila escrita "por un rol" y no
 *       por una persona no tiene arreglo posterior.</li>
 *   <li><b>Choca con el ocultamiento del log.</b> El log ya esconde al ADMIN las
 *       acciones del SUPERADMIN (LogEventoRepository.buscarConFiltros). Escribir
 *       durante la vista previa dejaría acciones del proveedor con OTRO rol
 *       encima, visibles para el cliente.</li>
 * </ol>
 * Y para lo que la vista previa existe -- mirar qué muestra cada rol -- alcanza
 * de sobra con GET.
 *
 * <h2>Las dos excepciones</h2>
 * <ul>
 *   <li>{@code POST /vista-previa/salir}: es la salida. Bloquearla dejaría la
 *       sesión atrapada en el rol, sin más camino que borrar la cookie.</li>
 *   <li>{@code POST /logout}: cerrar sesión siempre tiene que poder.</li>
 * </ul>
 *
 * <h2>Y una tercera, que es un GET</h2>
 * {@code /usuarios/mi-cuenta} es el ÚNICO GET del sistema que resuelve el usuario
 * actual con la variante que LANZA si no existe (UsuarioController:360). Con un
 * principal sintético daría un 500 sin explicación. Se corta acá con un mensaje
 * que se entiende. Si mañana aparece otro GET así, este es el lugar.
 */
public class VistaPreviaSoloLecturaFilter extends OncePerRequestFilter {

    static final String SALIR = "/vista-previa/salir";
    static final String LOGOUT = "/logout";
    static final String MI_CUENTA = "/usuarios/mi-cuenta";

    /** Motivos que entiende la pantalla de refugio para elegir el texto. */
    static final String MOTIVO_ESCRITURA = "escritura";
    static final String MOTIVO_MI_CUENTA = "mi-cuenta";

    private final String refugio;

    public VistaPreviaSoloLecturaFilter(String refugio) {
        this.refugio = refugio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!VistaPreviaRol.activa(SecurityContextHolder.getContext().getAuthentication())) {
            chain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        if (MI_CUENTA.equals(uri)) {
            desviarAlRefugio(response, MOTIVO_MI_CUENTA);
            return;
        }

        boolean esLectura = "GET".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod());
        boolean esSalida = SALIR.equals(uri) || LOGOUT.equals(uri);

        if (esLectura || esSalida) {
            chain.doFilter(request, response);
            return;
        }

        desviarAlRefugio(response, MOTIVO_ESCRITURA);
    }

    /**
     * Se REDIRIGE al refugio en vez de escribir la explicación en la respuesta.
     * <p>
     * La primera versión devolvía un 403 con el texto en text/plain. Explicaba bien
     * lo que había pasado y aun así estaba mal: una respuesta cruda no tiene menú,
     * ni banda, ni botón de salir. O sea, otra pantalla sin salida -- el mismo
     * defecto que ya habíamos arreglado para el 403 de la cadena de seguridad, que
     * a esta altura es el error a no repetir en esta función. El refugio SÍ usa el
     * layout, así que ahí el botón está siempre a mano.
     */
    private void desviarAlRefugio(HttpServletResponse response, String motivo) throws IOException {
        response.sendRedirect(refugio + "?motivo=" + motivo);
    }
}
