package com.textil.inventario.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda de la vista previa por rol: que el SwitchUserFilter quede REALMENTE
 * utilizable, no solo construido.
 *
 * <h2>El fallo que este test existe para que no vuelva</h2>
 * El filtro no es un @Bean (si lo fuera, Spring Boot lo montaria ademas fuera de la
 * cadena de seguridad, sin autorizacion previa). El precio es que nadie ejecuta su
 * ciclo de vida, y {@code SwitchUserFilter.afterPropertiesSet()} no es una
 * validacion opcional: es donde se CONSTRUYE el successHandler a partir del
 * targetUrl. Sin esa llamada, {@code doFilter} desreferencia un null al entrar Y al
 * salir.
 *
 * <p>No lo agarro nada: compila, los tests unitarios no montan la cadena, y la app
 * arranca perfecto porque el NPE recien ocurre al USAR el filtro. Se descubrio a
 * mano en dev. Por eso este test no mira la configuracion sino el COMPORTAMIENTO:
 * manda los dos POST reales y comprueba que redirigen.
 */
class VistaPreviaSwitchUserFilterFactoryTest {

    private final UserDetailsService rolesFalsos = username -> new User(
            "vista:" + username.toUpperCase(), "x",
            List.of(new SimpleGrantedAuthority("ROLE_" + username.toUpperCase())));

    private final SwitchUserFilter filtro = VistaPreviaSwitchUserFilterFactory.crear(rolesFalsos);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private Authentication superadmin() {
        return new UsernamePasswordAuthenticationToken(
                "jlynch", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
    }

    /**
     * OJO con el servletPath: AntPathRequestMatcher no compara contra el
     * requestURI sino contra lo que arma UrlUtils.buildRequestUrl(), que usa
     * servletPath cuando no es null. MockHttpServletRequest lo deja en "" por
     * defecto, asi que sin esta linea NINGUN matcher engancha y el filtro deja
     * pasar todo -- el test daria 200 y parecerian rotos el filtro o la fabrica,
     * cuando lo que esta mal armado es el request de mentira.
     */
    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        req.setServletPath(uri);
        return req;
    }

    private MockHttpServletResponse enviar(String uri, String rol) throws Exception {
        MockHttpServletRequest req = post(uri);
        if (rol != null) req.setParameter("rol", rol);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, Mockito.mock(FilterChain.class));
        return res;
    }

    @Test
    void entrarEnLaVistaPrevia_redirigeYCambiaElRolEfectivo() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(superadmin());

        MockHttpServletResponse res = enviar("/vista-previa", "GERENTE");

        // Antes del arreglo esto tiraba NullPointerException en vez de redirigir.
        assertThat(res.getStatus()).isEqualTo(302);
        assertThat(res.getRedirectedUrl()).isEqualTo("/");

        Authentication ahora = SecurityContextHolder.getContext().getAuthentication();
        assertThat(ahora.getName()).isEqualTo("vista:GERENTE");
        assertThat(ahora.getAuthorities()).extracting(a -> a.getAuthority())
                .contains("ROLE_GERENTE", "ROLE_PREVIOUS_ADMINISTRATOR");
    }

    @Test
    void salirDeLaVistaPrevia_devuelveLaSesionOriginal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(superadmin());
        enviar("/vista-previa", "GERENTE");

        MockHttpServletResponse res = enviar("/vista-previa/salir", null);

        // La otra mitad del mismo fallo: la rama de salida usa el MISMO
        // successHandler, asi que tambien reventaba.
        assertThat(res.getStatus()).isEqualTo(302);
        assertThat(res.getRedirectedUrl()).isEqualTo("/");

        Authentication ahora = SecurityContextHolder.getContext().getAuthentication();
        assertThat(ahora.getName()).isEqualTo("jlynch");
        assertThat(ahora.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_SUPERADMIN")
                .doesNotContain("ROLE_PREVIOUS_ADMINISTRATOR");
    }

    @Test
    void unRolQueNoSeResuelve_vaAlaUrlDeFallo_noAUnError() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(superadmin());
        UserDetailsService queSiempreFalla = username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("no");
        };
        SwitchUserFilter otro = VistaPreviaSwitchUserFilterFactory.crear(queSiempreFalla);

        MockHttpServletRequest req = post("/vista-previa");
        req.setParameter("rol", "INVENTADO");
        MockHttpServletResponse res = new MockHttpServletResponse();
        otro.doFilter(req, res, Mockito.mock(FilterChain.class));

        // Igual que el successHandler, el failureHandler tambien nace en
        // afterPropertiesSet(): sin el, un rol invalido daba NPE en vez de un aviso.
        assertThat(res.getStatus()).isEqualTo(302);
        assertThat(res.getRedirectedUrl()).isEqualTo("/usuarios?vistaPreviaError");
    }

    @Test
    void unaUrlCualquiera_noLaTocaElFiltro() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(superadmin());
        MockHttpServletRequest req = post("/recepciones/nueva");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilter(req, res, chain);

        Mockito.verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("jlynch");
    }
}
