package com.textil.inventario.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sirve el manifest de la PWA de forma DINAMICA, con el NOMBRE_EMPRESA de esta
 * instancia. Asi, al instalar la web en el celular, la app aparece con el
 * nombre del negocio (ej. "Laura & Clemente") en vez de "TexControl" generico.
 * Como cada cliente corre con su propio NOMBRE_EMPRESA, cada uno instala su app
 * con su nombre. Reemplaza al antiguo static/manifest.webmanifest fijo.
 */
@RestController
public class PwaController {

    // Mismo origen que el subtitulo del sidebar (ver GlobalModelAttributes).
    @Value("${app.nombre-empresa:TexControl}")
    private String nombreEmpresa;

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json;charset=UTF-8")
    public String manifest() {
        String nombre = escaparJson(nombreEmpresa);
        return """
                {
                  "name": "%s",
                  "short_name": "%s",
                  "description": "Gestion de inventario textil: recepcion, almacenamiento, transferencias y reportes.",
                  "lang": "es",
                  "start_url": "/",
                  "scope": "/",
                  "display": "standalone",
                  "orientation": "portrait-primary",
                  "background_color": "#f0f4f8",
                  "theme_color": "#1F4E79",
                  "icons": [
                    { "src": "/img/pwa/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any" },
                    { "src": "/img/pwa/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any" },
                    { "src": "/img/pwa/icon-maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" }
                  ]
                }
                """.formatted(nombre, nombre);
    }

    // Escapa backslash y comillas para no romper el JSON si el nombre las trae.
    private String escaparJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
