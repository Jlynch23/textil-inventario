package com.textil.inventario.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La pantalla de refugio de la vista previa por rol.
 *
 * <h2>Para qué existe</h2>
 * Es la ÚNICA página que un rol previsualizado puede abrir SIEMPRE, sea cual sea
 * el rol. Sin ella hay dos agujeros, y los dos mordieron:
 *
 * <ol>
 *   <li><b>Bucle infinito de redirecciones.</b> El manejador de 403 devolvía al
 *       rol a su pantalla de inicio. Eso da por hecho que todo rol puede abrir
 *       ALGO, y VENDEDOR no puede abrir nada: hoy está sin permisos a propósito,
 *       esperando el módulo de Ventas. Resultado: 403 -> redirijo a "/" -> 403 ->
 *       redirijo a "/"… hasta que el navegador corta. Redirigir acá no puede
 *       hacer bucle, porque esta ruta está permitida durante toda vista previa.</li>
 *   <li><b>Quedarse encerrado en el rol.</b> Un 403 de la cadena de seguridad no
 *       renderiza ninguna plantilla, así que tampoco aparece la banda con el botón
 *       de salir. Esta página sí usa el layout, o sea que el botón está siempre a
 *       la vista.</li>
 * </ol>
 *
 * <h2>Y es la vista de VENDEDOR</h2>
 * Previsualizar VENDEDOR aterriza acá, que es lo correcto: ese rol todavía no
 * tiene pantallas. Mostrar una página que lo dice es más honesto que un 403, y
 * más útil que una en blanco.
 *
 * <p>No toca la base de datos a propósito: es el refugio, tiene que funcionar
 * aunque lo de al lado esté roto.
 */
@Controller
public class VistaPreviaController {

    @GetMapping("/vista-previa/sin-permiso")
    public String sinPermiso(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String motivo,
            Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("titulo", "Vista previa");
        model.addAttribute("rolVisto", VistaPreviaRol.rolActual(auth));
        // Tres formas de llegar acá, y conviene decir CUÁL fue: si no, el mensaje
        // "este rol no tiene permiso" aparecería también cuando lo que pasó es que
        // se intentó guardar algo, y suena a que faltan permisos cuando en realidad
        // la vista previa es de solo lectura para cualquier rol.
        model.addAttribute("motivo", motivo != null ? motivo : "sin-permiso");
        return "vistaprevia/sin-permiso";
    }
}
