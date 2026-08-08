package com.textil.inventario.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que un 403 durante la vista previa NO encierre la sesión ni entre en bucle.
 *
 * Los dos fallos que este test existe para que no vuelvan, los dos vistos en dev:
 *  - Sin manejador: el 403 no renderiza plantilla, no hay banda, no hay boton de
 *    salir. La unica vuelta era borrar la cookie.
 *  - Con el primer manejador: devolvia al rol a SU pantalla de inicio, dando por
 *    hecho que todo rol puede abrir algo. VENDEDOR no puede abrir nada, asi que
 *    403 -> "/" -> 403 -> "/" ... bucle infinito.
 */
class VistaPreviaAccessDeniedHandlerTest {

    private static final String REFUGIO = "/vista-previa/sin-permiso";
    private final VistaPreviaAccessDeniedHandler handler = new VistaPreviaAccessDeniedHandler(REFUGIO);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void enVistaPreviaComo(String rol) {
        Authentication original = new UsernamePasswordAuthenticationToken(
                "jlynch", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "vista:" + rol, "x",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol),
                        new SwitchUserGrantedAuthority("ROLE_PREVIOUS_ADMINISTRATOR", original))));
    }

    private MockHttpServletResponse denegar(String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.handle(req, res, new AccessDeniedException("denegado"));
        return res;
    }

    @Test
    void enVistaPrevia_mandaAlRefugio() throws Exception {
        enVistaPreviaComo("GERENTE");

        MockHttpServletResponse res = denegar("/reportes");

        assertThat(res.getStatus()).isEqualTo(302);
        assertThat(res.getRedirectedUrl()).isEqualTo(REFUGIO);
    }

    @Test
    void vendedorQueNoPuedeAbrirNada_tambienLlegaAlRefugio_yNoAlInicio() throws Exception {
        // El caso que rompia: VENDEDOR no tiene permiso sobre "/" tampoco, asi que
        // redirigirlo ahi lo devolvia al mismo 403, una y otra vez.
        enVistaPreviaComo("VENDEDOR");

        MockHttpServletResponse res = denegar("/");

        assertThat(res.getRedirectedUrl())
                .as("nunca al inicio del rol: ahi es donde se armaba el bucle")
                .isEqualTo(REFUGIO)
                .isNotEqualTo("/");
    }

    @Test
    void unDenegadoEnElRefugioMismo_noSeRedirige() throws Exception {
        // Cinturon extra: si el refugio llegara a denegar, redirigir a el seria un
        // bucle de una sola parada. Se corta con un 403 de verdad.
        enVistaPreviaComo("VENDEDOR");

        MockHttpServletResponse res = denegar(REFUGIO);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getRedirectedUrl()).isNull();
    }

    @Test
    void fueraDeLaVistaPrevia_elComportamientoEsElDeSiempre() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "gerente1", "x", List.of(new SimpleGrantedAuthority("ROLE_GERENTE"))));

        MockHttpServletResponse res = denegar("/reportes");

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getRedirectedUrl()).isNull();
    }

    @Test
    void sinSesion_403() throws Exception {
        SecurityContextHolder.clearContext();

        assertThat(denegar("/reportes").getStatus()).isEqualTo(403);
    }
}
