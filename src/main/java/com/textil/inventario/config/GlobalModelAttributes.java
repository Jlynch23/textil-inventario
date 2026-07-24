package com.textil.inventario.config;

import com.textil.inventario.catalogo.EmpresaRepository;
import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioActualService;
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

    @ModelAttribute("nombreUsuarioActual")
    public String nombreUsuarioActual() {
        Usuario usuario = usuarioActualService.obtenerUsuarioActualOrNull();
        return usuario != null ? usuario.getNombre() : "";
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
        Usuario usuario = usuarioActualService.obtenerUsuarioActualOrNull();
        return usuario != null && usuario.getRol() != null
                && "GERENTE".equalsIgnoreCase(usuario.getRol().getNombre());
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
        Usuario usuario = usuarioActualService.obtenerUsuarioActualOrNull();
        if (usuario == null || usuario.getRol() == null) return false;
        String rol = usuario.getRol().getNombre();
        return "ADMIN".equalsIgnoreCase(rol) || "SUPERADMIN".equalsIgnoreCase(rol);
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
