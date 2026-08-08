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
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La vista previa por rol es de SOLO LECTURA.
 *
 * No es prudencia: el principal `vista:GERENTE` no existe en la tabla usuarios,
 * asi que un alta no tendria a quien atribuirse -- RecepcionService y
 * TransferenciaService sellan cada movimiento con obtenerUsuarioActual(), que
 * LANZA si no lo encuentra. Sin este filtro un POST reventaria a mitad de camino,
 * y en el peor caso despues de haber tocado algo.
 */
class VistaPreviaSoloLecturaFilterTest {

    private final VistaPreviaSoloLecturaFilter filtro = new VistaPreviaSoloLecturaFilter();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void enVistaPrevia() {
        Authentication original = new UsernamePasswordAuthenticationToken(
                "jlynch", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "vista:GERENTE", "x",
                List.of(new SimpleGrantedAuthority("ROLE_GERENTE"),
                        new SwitchUserGrantedAuthority("ROLE_PREVIOUS_ADMINISTRATOR", original))));
    }

    private void sesionNormal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "jlynch", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))));
    }

    private MockHttpServletResponse pasar(String metodo, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.setRequestURI(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);
        filtro.doFilter(req, res, chain);
        // 200 por defecto = el filtro dejo pasar; 403 = lo corto.
        return res;
    }

    @Test
    void enVistaPrevia_seBloqueaCualquierEscritura() throws Exception {
        enVistaPrevia();

        assertThat(pasar("POST", "/recepciones/nueva").getStatus()).isEqualTo(403);
        assertThat(pasar("POST", "/transferencias/1/confirmar-salida").getStatus()).isEqualTo(403);
        assertThat(pasar("DELETE", "/catalogo/colores/3").getStatus()).isEqualTo(403);
        assertThat(pasar("PUT", "/programas/1").getStatus()).isEqualTo(403);
    }

    @Test
    void enVistaPrevia_laLecturaPasa() throws Exception {
        enVistaPrevia();

        assertThat(pasar("GET", "/dashboard").getStatus()).isEqualTo(200);
        assertThat(pasar("GET", "/inventario/stock").getStatus()).isEqualTo(200);
    }

    @Test
    void enVistaPrevia_salirYCerrarSesionSiempreSePueden() throws Exception {
        enVistaPrevia();

        // Si estas dos se bloquearan, la sesion quedaria atrapada en el rol sin
        // mas salida que borrar la cookie a mano.
        assertThat(pasar("POST", "/vista-previa/salir").getStatus()).isEqualTo(200);
        assertThat(pasar("POST", "/logout").getStatus()).isEqualTo(200);
    }

    @Test
    void enVistaPrevia_miCuentaSeCortaConMensaje() throws Exception {
        enVistaPrevia();

        // Es el UNICO GET que resuelve el usuario actual con la variante que lanza
        // (UsuarioController:360). Con un principal sintetico daria un 500 pelado.
        MockHttpServletResponse res = pasar("GET", "/usuarios/mi-cuenta");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("vista previa");
    }

    @Test
    void enSesionNormal_elFiltroNoSeMete() throws Exception {
        sesionNormal();

        assertThat(pasar("POST", "/recepciones/nueva").getStatus()).isEqualTo(200);
        assertThat(pasar("GET", "/usuarios/mi-cuenta").getStatus()).isEqualTo(200);
    }

    @Test
    void sinSesion_elFiltroNoSeMete() throws Exception {
        SecurityContextHolder.clearContext();

        assertThat(pasar("POST", "/login").getStatus()).isEqualTo(200);
    }
}
