package com.textil.inventario.config;

import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioActualService;
import lombok.RequiredArgsConstructor;
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

    // Subtitulo del sidebar (bajo el logo TEXCONTROL). TEXCONTROL es el
    // nombre del producto y queda fijo; esto es el nombre del NEGOCIO que
    // lo usa, que cambia por cada cliente/instalacion -- se configura por
    // variable de entorno (NOMBRE_EMPRESA) en vez de quedar hardcodeado,
    // para poder desplegar el mismo codigo a un cliente nuevo sin fork.
    @Value("${app.nombre-empresa:Laura & Clemente}")
    private String nombreEmpresa;

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
     * Usado en vistas de catalogo (colores, ubicaciones, articulos) para
     * centrar la tabla cuando el panel lateral de creacion esta oculto
     * (no-SUPERADMIN) -- sin esto, la tabla queda pegada a la izquierda
     * con un hueco vacio donde antes estaba el formulario.
     */
    @ModelAttribute("esSuperadmin")
    public boolean esSuperadmin() {
        Usuario usuario = usuarioActualService.obtenerUsuarioActualOrNull();
        return usuario != null && usuario.getRol() != null
                && "SUPERADMIN".equalsIgnoreCase(usuario.getRol().getNombre());
    }

    @ModelAttribute("nombreEmpresa")
    public String nombreEmpresa() {
        return nombreEmpresa;
    }
}
