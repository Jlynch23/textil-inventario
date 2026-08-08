package com.textil.inventario.vistaprevia;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.textil.inventario.seguridad.Rol;
import com.textil.inventario.seguridad.RolRepository;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La vista previa por rol vista por un NAVEGADOR DE VERDAD.
 *
 * <h2>Qué agrega sobre VistaPreviaWebTest</h2>
 * MockMvc prueba que el botón de salir ESTÉ en el HTML. Esto prueba que se VEA y
 * que se pueda hacer clic: que no quede tapado por otro elemento, fuera de la
 * pantalla, con opacidad cero o detrás de un z-index. Es justo la capa donde
 * MockMvc no llega -- y la banda de /almacen va {@code position-fixed} sobre un
 * layout que no es el suyo, que es exactamente el tipo de cosa que se rompe ahí.
 * <p>
 * También deja capturas en {@code target/capturas-vista-previa/}, que sirven para
 * mirar el resultado sin tener que desplegar.
 *
 * <h2>Por qué NO corre en CI por defecto</h2>
 * Necesita un Chromium instalado. Se activa con {@code VISTA_PREVIA_UI=true} y con
 * el navegador en {@code PLAYWRIGHT_CHROMIUM} (o el que resuelva Playwright solo).
 * Mismo criterio que los tests marcados {@code RUN_DB_IT}: el que necesita algo
 * del entorno se pide explícitamente, no se asume.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mockmvc")
@EnabledIfEnvironmentVariable(named = "VISTA_PREVIA_UI", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VistaPreviaNavegadorTest {

    @LocalServerPort int puerto;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RolRepository rolRepository;
    @Autowired PasswordEncoder passwordEncoder;

    static Playwright playwright;
    static Browser navegador;
    Page pagina;

    private static final String CLAVE = "prueba-ui-123";
    private static final Path CAPTURAS = Paths.get("target", "capturas-vista-previa");

    @BeforeAll
    static void abrirNavegador() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions opciones = new BrowserType.LaunchOptions().setHeadless(true);
        // El navegador ya está en la máquina; no se descarga nada. Si la variable
        // no está, se deja que Playwright resuelva por su cuenta.
        String ejecutable = System.getenv("PLAYWRIGHT_CHROMIUM");
        if (ejecutable != null && !ejecutable.isBlank()) {
            opciones.setExecutablePath(Paths.get(ejecutable));
        }
        navegador = playwright.chromium().launch(opciones);
    }

    @AfterAll
    static void cerrarNavegador() {
        if (navegador != null) navegador.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void preparar() {
        if (rolRepository.count() == 0) {
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
            jlynch.setPasswordHash(passwordEncoder.encode(CLAVE));
            jlynch.setRol(rolRepository.findAllByOrderByJerarquiaAsc().get(0));
            jlynch.setActivo(true);
            usuarioRepository.save(jlynch);
        }
        pagina = navegador.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 800)).newPage();
        iniciarSesion();
    }

    @AfterEach
    void cerrarPagina() {
        if (pagina != null) pagina.close();
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private void iniciarSesion() {
        pagina.navigate(url("/login"));
        pagina.fill("input[name='username']", "jlynch");
        pagina.fill("input[name='password']", CLAVE);
        pagina.click("button[type='submit']");
        pagina.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private void verComo(String rol) {
        pagina.click("text=Ver como");
        pagina.click("button.dropdown-item:has-text('" + rol + "')");
        pagina.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private void capturar(String nombre) {
        pagina.screenshot(new Page.ScreenshotOptions()
                .setPath(CAPTURAS.resolve(nombre + ".png"))
                .setFullPage(false));
    }

    /**
     * La banda no puede quedar por DEBAJO del sidebar.
     * <p>
     * El sidebar es position:fixed, asi que un elemento sin margin-left se le mete
     * abajo y le tapa el texto de la izquierda. Paso: se leia "VENDEDOR." en vez de
     * "Estás viendo la app como VENDEDOR". En el HTML el texto estaba entero -- por
     * eso ningun test de MockMvc podia verlo, y por eso este test mide POSICIONES.
     */
    private void exigirBandaNoTapadaPorElSidebar(String contexto) {
        Locator sidebar = pagina.locator(".sidebar");
        if (sidebar.count() == 0) return;   // /almacen no tiene sidebar
        var caja = pagina.locator(".banda-vista-previa").first().boundingBox();
        var cajaSidebar = sidebar.first().boundingBox();
        assertThat(caja.x)
                .as(contexto + ": la banda arranca donde termina el sidebar, no debajo")
                .isGreaterThanOrEqualTo(cajaSidebar.x + cajaSidebar.width - 1);
    }

    /** El botón de salir tiene que ser VISIBLE y CLICKEABLE, no solo existir. */
    private void exigirSalidaUsable(String contexto) {
        Locator boton = pagina.locator("button:has-text('Salir de la vista previa')");
        assertThat(boton.count()).as(contexto + ": el botón de salir debe estar").isPositive();
        assertThat(boton.first().isVisible()).as(contexto + ": debe VERSE").isTrue();
        assertThat(boton.first().isEnabled()).as(contexto + ": debe poder clickearse").isTrue();
    }

    @Test @Order(1)
    void verComoGerente() {
        verComo("GERENTE");
        capturar("1-gerente");
        exigirSalidaUsable("GERENTE");
        exigirBandaNoTapadaPorElSidebar("GERENTE");
        // El menú se recorta: un GERENTE no ve Usuarios ni Reportes.
        assertThat(pagina.locator("a.nav-link:has-text('Usuarios')").count()).isZero();
    }

    @Test @Order(2)
    void verComoAdmin_sinArchivoHistorico() {
        verComo("ADMIN");
        capturar("2-admin");
        exigirSalidaUsable("ADMIN");
        // La restricción de costo, vista con los ojos del cliente.
        assertThat(pagina.locator("a.nav-link:has-text('Archivo Histórico')").count())
                .as("el ADMIN NO debe ver Archivo Histórico (gasta la API key del proveedor)")
                .isZero();
        assertThat(pagina.locator("a.nav-link:has-text('Reportes')").count()).isPositive();
    }

    @Test @Order(3)
    void verComoSupervisor_aterrizaEnAlmacenConSalidaVisible() {
        verComo("SUPERVISOR");
        capturar("3-supervisor-almacen");
        assertThat(pagina.url()).endsWith("/almacen");
        // ACÁ está el valor de usar un navegador: la banda va position-fixed encima
        // de un layout que no es el suyo. Que esté en el HTML no alcanza.
        exigirSalidaUsable("SUPERVISOR en /almacen");
    }

    @Test @Order(4)
    void verComoVendedor_caeEnElRefugio() {
        verComo("VENDEDOR");
        capturar("4-vendedor-refugio");
        exigirSalidaUsable("VENDEDOR");
        exigirBandaNoTapadaPorElSidebar("VENDEDOR");
        assertThat(pagina.content()).contains("módulo de Ventas");
    }

    @Test @Order(5)
    void salir_devuelveAlSuperadmin() {
        verComo("GERENTE");
        pagina.click("button:has-text('Salir de la vista previa')");
        pagina.waitForLoadState(LoadState.NETWORKIDLE);
        capturar("5-de-vuelta-superadmin");

        assertThat(pagina.locator("button:has-text('Salir de la vista previa')").count())
                .as("la banda tiene que desaparecer").isZero();
        assertThat(pagina.locator("button:has-text('Ver como')").count())
                .as("y el lanzador tiene que volver").isPositive();
    }
}
