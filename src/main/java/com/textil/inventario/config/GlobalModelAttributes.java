package com.textil.inventario.config;

import com.textil.inventario.catalogo.EmpresaRepository;
import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioActualService;
import com.textil.inventario.seguridad.VistaPreviaRol;
import com.textil.inventario.seguridad.VistaPreviaRolUserDetailsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Inyecta atributos comunes en el modelo de TODAS las vistas que usan
 * layout/base.html (via @Controller), sin tener que agregarlos a mano en
 * cada metodo de cada controlador.
 * <p>
 * nombreUsuarioActual: usado en la topbar para el saludo "Bienvenido, [nombre]".
 * Se resuelve con obtenerUsuarioActualOrNull() (tolerante a fallos) porque
 * este metodo corre en CADA request, incluida la pantalla de login donde
 * todavia no hay sesion -- no debe romper la pagina si no hay usuario.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final UsuarioActualService usuarioActualService;
    private final EmpresaRepository empresaRepository;
    private final VistaPreviaRolUserDetailsService vistaPreviaRolUserDetailsService;

    // Etiqueta del entorno (ej. "BETA" en staging), vacia en produccion.
    @Value("${app.entorno-etiqueta:}")
    private String entornoEtiqueta;

    /**
     * Etiqueta del entorno para mostrar en el login (ej. "BETA" en dev). Vacia
     * en produccion -> la vista oculta el distintivo. Sirve para no confundir
     * el login de staging (dev.texcontrol.pe) con el de produccion.
     */
    @ModelAttribute("entornoEtiqueta")
    public String entornoEtiqueta() {
        return entornoEtiqueta != null ? entornoEtiqueta : "";
    }

    /**
     * #13: nonce de CSP del request actual (lo genera CspNonceFilter y lo deja como
     * atributo de request). Se expone al modelo para que las plantillas lo pongan en
     * sus &lt;script&gt; inline (nonce=${cspNonce}); sin él, la CSP los bloquea.
     * Thymeleaf 3.1 ya no tiene ${#request}, por eso se puentea aquí.
     */
    @ModelAttribute("cspNonce")
    public String cspNonce(HttpServletRequest request) {
        Object nonce = request.getAttribute(CspNonceFilter.NONCE_ATTR);
        return nonce != null ? nonce.toString() : "";
    }

    @ModelAttribute("nombreUsuarioActual")
    public String nombreUsuarioActual() {
        Usuario usuario = usuarioActualService.obtenerUsuarioActualOrNull();
        return usuario != null ? usuario.getNombre() : "";
    }

    /**
     * Rol que se está previsualizando ("GERENTE"), o vacío si no hay vista previa.
     * La plantilla lo usa para pintar la banda de aviso; sin ella sería muy fácil
     * olvidarse de que la sesión está en otro rol y creer que faltan permisos.
     */
    @ModelAttribute("vistaPreviaRol")
    public String vistaPreviaRol() {
        String rol = VistaPreviaRol.rolActual(auth());
        return rol != null ? rol : "";
    }

    /** Roles que el SUPERADMIN puede previsualizar (todos menos SUPERADMIN). */
    @ModelAttribute("rolesPrevisualizables")
    public List<String> rolesPrevisualizables() {
        try {
            Authentication a = auth();
            boolean esSuperadmin = a != null && a.getAuthorities().stream()
                    .anyMatch(g -> "ROLE_SUPERADMIN".equals(g.getAuthority()));
            // Durante la vista previa la sesion NO es SUPERADMIN, asi que la lista
            // sale vacia y el lanzador no se dibuja. Es lo correcto: desde adentro
            // se sale primero y despues se elige otro rol.
            return esSuperadmin ? vistaPreviaRolUserDetailsService.rolesPrevisualizables() : List.of();
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Rutas base de los enlaces del sidebar (layout/base.html). Incluye tambien
     * rutas que NO son del menu pero cuelgan de una que si lo es (ej.
     * /usuarios/mi-cuenta): al ganar ellas la comparacion, evitan que se
     * encienda el enlace padre en una pagina que no le corresponde.
     */
    private static final List<String> RUTAS_MENU = List.of(
            "/inventario/stock", "/inventario/kardex",
            "/recepciones", "/recepciones/facturar",
            "/almacen/revision", "/log",
            "/usuarios", "/usuarios/mi-cuenta",
            "/programas", "/transferencias", "/documentos",
            "/reportes", "/archivo-historico",
            "/catalogo/colores", "/catalogo/articulos", "/catalogo/ubicaciones",
            "/catalogo/empresas", "/catalogo/tipos-tela", "/catalogo/titulos",
            "/catalogo/composiciones", "/catalogo/acabados");

    /**
     * Ruta del menu que corresponde a la pagina actual, para marcarla como
     * activa en el sidebar. La plantilla solo compara este texto con el href de
     * cada enlace (nada de logica en el HTML).
     * <p>
     * Se elige la coincidencia MAS LARGA, no la primera: /recepciones/facturar
     * empieza con /recepciones, y sin esta regla se encenderian los dos enlaces
     * al mismo tiempo. Ojo con Thymeleaf 3.1 (Spring Boot 3.x): ya NO existen
     * ${#httpServletRequest} ni ${#request}, por eso la ruta se calcula aca.
     */
    @ModelAttribute("rutaMenuActiva")
    public String rutaMenuActiva(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return "";
        // El dashboard responde en las dos rutas; se normaliza a "/".
        if ("/".equals(uri) || "/dashboard".equals(uri)) return "/";
        String mejor = "";
        for (String ruta : RUTAS_MENU) {
            boolean coincide = uri.equals(ruta) || uri.startsWith(ruta + "/");
            if (coincide && ruta.length() > mejor.length()) mejor = ruta;
        }
        return mejor;
    }

    /**
     * Controla si el sidebar arranca colapsado por defecto. Para GERENTE
     * (perfil pensado para celular, uso ocasional) arranca escondido para
     * no saturar la pantalla; el resto de los roles mantienen el
     * comportamiento historico (expandido salvo que el usuario lo haya
     * colapsado el mismo, guardado en localStorage).
     */
    @ModelAttribute("sidebarInicialColapsado")
    public boolean sidebarInicialColapsado() {
        return tieneRol("GERENTE");
    }

    /**
     * Usado en vistas de catalogo (colores, ubicaciones, articulos) y en los
     * detalles de recepcion/transferencia para centrar la tabla cuando el panel
     * lateral de creacion/edicion esta oculto -- sin esto, la tabla queda pegada
     * a la izquierda con un hueco vacio donde iria el formulario.
     * <p>
     * Es true para ADMIN (dueño-cliente) y SUPERADMIN (proveedor): ambos ven los
     * paneles de escritura. Para GERENTE (solo lectura) es false.
     */
    @ModelAttribute("esAdmin")
    public boolean esAdmin() {
        return tieneRol("ADMIN") || tieneRol("SUPERADMIN");
    }

    /**
     * El rol sale de las AUTHORITIES de la sesión, no de releer `usuarios` en base.
     * <p>
     * Es lo correcto de por sí -- la autorización la decide el token autenticado, y
     * de paso se ahorran dos consultas por request --, pero acá además es lo único
     * que funciona: durante la vista previa por rol el principal es sintético y NO
     * existe en la tabla, así que la búsqueda por username devolvía null y
     * `esAdmin` daba false. O sea que "ver como ADMIN" se habría dibujado sin los
     * paneles de escritura: la vista previa mintiendo justo en lo que se quiere
     * mirar. Para una sesión normal el resultado es idéntico (las authorities se
     * arman del rol de la BD al loguear, y al cambiarle el rol a alguien se le
     * expira la sesión -- R-A1).
     */
    private boolean tieneRol(String rol) {
        Authentication a = auth();
        if (a == null) return false;
        String buscada = "ROLE_" + rol.toUpperCase();
        return a.getAuthorities().stream().anyMatch(g -> buscada.equals(g.getAuthority()));
    }

    /**
     * Subtitulo del sidebar (bajo el logo TEXCONTROL). TEXCONTROL es el nombre
     * del producto y queda fijo; esto es el nombre del NEGOCIO que lo usa. Se
     * arma con las EMPRESAS activas cargadas unidas por " & " (ej. duo
     * "TEXTIL LAURA & TEXTIL CLEMENTE"; un solo cliente = su nombre): al vender
     * una instancia, basta con dar de alta la(s) empresa(s) y la marca se
     * actualiza sola, sin tocar codigo ni entorno.
     * <p>
     * Si NO hay empresas activas cargadas, devuelve vacio -> el subtitulo no se
     * muestra (la vista oculta el elemento). Asi una copia recien entregada, sin
     * empresas, no exhibe ningun nombre de otro cliente.
     */
    @ModelAttribute("nombreEmpresa")
    public String nombreEmpresa() {
        try {
            return empresaRepository.findByActivoTrue().stream()
                    .map(Empresa::getNombre)
                    .filter(n -> n != null && !n.isBlank())
                    .collect(Collectors.joining(" & "));
        } catch (Exception ignore) {
            // Tolerante a fallos: corre en CADA request (incluido el login sin
            // sesion). Ante cualquier problema, no mostramos nada.
            return "";
        }
    }
}
