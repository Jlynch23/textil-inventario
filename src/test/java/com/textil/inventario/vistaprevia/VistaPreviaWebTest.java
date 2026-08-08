package com.textil.inventario.vistaprevia;

import com.textil.inventario.seguridad.Rol;
import com.textil.inventario.seguridad.RolRepository;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * La vista previa por rol, abierta DE VERDAD: cadena de seguridad completa y
 * plantillas Thymeleaf renderizadas, sobre H2 en memoria.
 *
 * <h2>Por qué existe este test</h2>
 * La vista previa fallo CUATRO veces seguidas en dev, y ningun test unitario podia
 * verlo porque todos los defectos vivian en el armado: a donde redirige cada rol,
 * si la pantalla de destino dibuja el boton de salir, si un corte deja al usuario
 * sin camino de vuelta. Eso solo se ve abriendo la app. Esto la abre.
 *
 * <p>La regla que se cuida, y que es LA regla de esta funcion: <b>cualquier corte
 * durante la vista previa tiene que terminar en una pagina que muestre el boton de
 * salir</b>. Nunca en un 403 pelado, nunca en texto plano, nunca en un bucle. Por
 * eso casi todos los tests terminan comprobando lo mismo: que el HTML traiga el
 * boton.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mockmvc")
class VistaPreviaWebTest {

    /** Texto del boton de la banda. Si esto esta, hay salida. */
    private static final String BOTON_SALIR = "Salir de la vista previa";
    private static final String REFUGIO = "/vista-previa/sin-permiso";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RolRepository rolRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void sembrar() {
        if (rolRepository.count() > 0) return;
        int jerarquia = 0;
        for (String nombre : new String[]{"SUPERADMIN", "ADMIN", "GERENTE", "SUPERVISOR", "VENDEDOR"}) {
            Rol r = new Rol();
            r.setNombre(nombre);
            r.setActivo(true);
            r.setJerarquia(jerarquia++);
            rolRepository.save(r);
        }
        Usuario jlynch = new Usuario();
        jlynch.setNombre("Joseph Lynch");
        jlynch.setUsername("jlynch");
        jlynch.setPasswordHash(passwordEncoder.encode("x"));
        jlynch.setRol(rolRepository.findAllByOrderByJerarquiaAsc().get(0));
        jlynch.setActivo(true);
        usuarioRepository.save(jlynch);
    }

    /**
     * Sesion EN vista previa, armada como la deja el SwitchUserFilter de verdad.
     * <p>
     * OJO: no alcanza con user(...).roles("PREVIOUS_ADMINISTRATOR"). Eso crea una
     * SimpleGrantedAuthority con ese nombre, y VistaPreviaRol.activa() pregunta por
     * la CLASE SwitchUserGrantedAuthority -- que es la unica que trae adentro la
     * sesion original a la que volver. Con el atajo, los filtros de la vista previa
     * no se activan y el test pasa por al lado del comportamiento que quiere probar.
     */
    private RequestPostProcessor enVistaPreviaComo(String rol) {
        Authentication original = new UsernamePasswordAuthenticationToken(
                "jlynch", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
        return authentication(new UsernamePasswordAuthenticationToken(
                "vista:" + rol, "x",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol),
                        new SwitchUserGrantedAuthority("ROLE_PREVIOUS_ADMINISTRATOR", original))));
    }

    /** Entra en vista previa y devuelve a donde te mando. */
    private String entrarComo(String rol) throws Exception {
        MvcResult res = mvc.perform(post("/vista-previa")
                        .param("rol", rol)
                        .with(user("jlynch").roles("SUPERADMIN"))
                        .with(csrf()))
                .andReturn();
        assertThat(res.getResponse().getStatus())
                .as("entrar en vista previa como " + rol + " deberia redirigir")
                .isEqualTo(302);
        return res.getResponse().getRedirectedUrl();
    }

    // ---------------------------------------------------------------- destinos

    /** El HTML de una pantalla, vista con el rol previsualizado. */
    private String htmlComo(String rol, String uri) throws Exception {
        return mvc.perform(get(uri).with(enVistaPreviaComo(rol)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void gerente_aterrizaEnElDashboard_yEsePantallazoTieneSalida() throws Exception {
        String destino = entrarComo("GERENTE");
        assertThat(destino).isEqualTo("/");
        assertThat(htmlComo("GERENTE", destino)).contains(BOTON_SALIR);
    }

    @Test
    void supervisor_aterrizaEnAlmacen_yEsePantallazoTieneSalida() throws Exception {
        // Dos fallos distintos en una sola prueba, los dos vistos en dev:
        //  1. El SUPERVISOR no tiene permiso sobre "/": mandarlo ahi era un 403.
        //  2. /almacen tiene <head> y <body> PROPIOS, no hereda de layout/base. La
        //     banda vivia solo en el layout, asi que aterrizabas sin boton de salir
        //     -- encerrado en el rol. Por eso no alcanza con mirar el redirect:
        //     hay que abrir la pantalla y buscar el boton.
        String destino = entrarComo("SUPERVISOR");
        assertThat(destino).isEqualTo("/almacen");
        assertThat(htmlComo("SUPERVISOR", destino)).contains(BOTON_SALIR);
    }

    @Test
    void lasTresPantallasDeAlmacen_tienenSalida() throws Exception {
        // Las tres declaran su propio <head>: si alguien agrega una cuarta y se
        // olvida del fragmento, se repite el encierro.
        for (String uri : new String[]{"/almacen", "/almacen/entrada", "/almacen/salida"}) {
            assertThat(htmlComo("SUPERVISOR", uri)).as(uri).contains(BOTON_SALIR);
        }
    }

    @Test
    void superadmin_noSePuedePrevisualizar() throws Exception {
        // Rebota a la url de fallo, no a un error.
        assertThat(entrarComo("SUPERADMIN")).contains("vistaPreviaError");
    }

    // ------------------------------------------------- que NUNCA falte la salida

    @Test
    void elRefugio_muestraElBotonDeSalir() throws Exception {
        String html = mvc.perform(get(REFUGIO)
                        .with(enVistaPreviaComo("VENDEDOR")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(BOTON_SALIR);
    }

    @Test
    void unaPantallaProhibida_desviaAlRefugio_noAUnBucle() throws Exception {
        // El caso VENDEDOR: no tiene permiso sobre NADA, asi que mandarlo a la
        // pantalla de inicio de su rol era 403 -> "/" -> 403 -> "/" ... infinito.
        MvcResult res = mvc.perform(get("/reportes")
                        .with(enVistaPreviaComo("VENDEDOR")))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(302);
        assertThat(res.getResponse().getRedirectedUrl())
                .as("al refugio, que ningun rol previsualizado puede rebotar")
                .isEqualTo(REFUGIO);
    }

    @Test
    void intentarGuardarAlgo_desviaAlRefugio_noAUnTextoPelado() throws Exception {
        MvcResult res = mvc.perform(post("/catalogo/acabados/guardar")
                        .with(enVistaPreviaComo("ADMIN"))
                        .with(csrf()))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(302);
        assertThat(res.getResponse().getRedirectedUrl()).isEqualTo(REFUGIO + "?motivo=escritura");
    }

    @Test
    void miCuenta_desviaAlRefugio_enVezDeReventar() throws Exception {
        // El principal sintetico no existe en `usuarios`, y /usuarios/mi-cuenta es
        // el unico GET que lo resuelve con la variante que LANZA: daria un 500.
        MvcResult res = mvc.perform(get("/usuarios/mi-cuenta")
                        .with(enVistaPreviaComo("ADMIN")))
                .andReturn();

        assertThat(res.getResponse().getRedirectedUrl()).isEqualTo(REFUGIO + "?motivo=mi-cuenta");
    }

    @Test
    void salir_devuelveLaSesionYRedirige() throws Exception {
        MvcResult res = mvc.perform(post("/vista-previa/salir")
                        .with(enVistaPreviaComo("GERENTE"))
                        .with(csrf()))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(302);
    }

    // --------------------------------------------------------------- permisos

    @Test
    void soloElSuperadminPuedeEntrarEnVistaPrevia() throws Exception {
        mvc.perform(post("/vista-previa").param("rol", "GERENTE")
                        .with(user("unadmin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(r -> assertThat(r.getResponse().getStatus())
                        .as("un ADMIN no puede cambiarse el rol")
                        .isEqualTo(403));
    }
}
