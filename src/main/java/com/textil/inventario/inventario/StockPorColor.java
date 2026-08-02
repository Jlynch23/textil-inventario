package com.textil.inventario.inventario;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arma la vista "Stock por color" a partir del stock ya cargado (y ya filtrado):
 * agrupa por COLOR, y dentro de cada color por UBICACIÓN y por ARTÍCULO
 * (tipo de tela + título). Es solo lógica de presentación: no toca la BD ni el
 * stock; recibe la misma lista de StockActual que la tabla de detalle, así los
 * filtros de la pantalla aplican igual a las dos vistas.
 *
 * El stock ya viene "unido" por (artículo, color, ubicación) — constraint único
 * de stock_actual —; acá solo se suma para responder "cuánto tengo de este color
 * y dónde está".
 */
public final class StockPorColor {

    private StockPorColor() {}

    @Getter @AllArgsConstructor
    public static class Linea {
        private final String tipoTela;
        private final String titulo;
        private final int rollos;
        private final BigDecimal peso;
    }

    @Getter @AllArgsConstructor
    public static class PorUbicacion {
        private final String ubicacion;
        private final int rollos;
        private final BigDecimal peso;
        private final List<Linea> lineas;
    }

    @Getter @AllArgsConstructor
    public static class PorColor {
        private final Long colorId;
        private final String nombre;
        private final String codigoFastDye;
        private final String hex;         // muestra visual aproximada (ver colorHex)
        private final int rollos;
        private final BigDecimal peso;
        private final boolean bajo;       // total por debajo del umbral de stock bajo
        private final int anchoBarra;     // % de la barra respecto al color con más stock
        private final List<PorUbicacion> ubicaciones;
    }

    @Getter @AllArgsConstructor
    public static class Resumen {
        private final int totalRollos;
        private final BigDecimal totalPeso;
        private final int totalColores;
        private final int totalUbicaciones;
        private final List<PorColor> colores;
    }

    public static Resumen construir(List<StockActual> stock, int umbralBajo) {
        Map<Long, ColorAcc> porColor = new LinkedHashMap<>();
        Set<Long> ubicacionIds = new HashSet<>();
        int totalRollos = 0;
        BigDecimal totalPeso = BigDecimal.ZERO;

        for (StockActual s : stock) {
            int rollos = s.getRollos() == null ? 0 : s.getRollos();
            BigDecimal peso = s.getPesoKg() == null ? BigDecimal.ZERO : s.getPesoKg();
            totalRollos += rollos;
            totalPeso = totalPeso.add(peso);
            ubicacionIds.add(s.getUbicacion().getId());

            ColorAcc ca = porColor.computeIfAbsent(s.getColor().getId(), k -> new ColorAcc(
                    s.getColor().getId(), s.getColor().getNombreMostrar(), s.getColor().getCodigoFastDye()));
            ca.rollos += rollos;
            ca.peso = ca.peso.add(peso);

            UbicAcc ua = ca.ubicaciones.computeIfAbsent(s.getUbicacion().getId(),
                    k -> new UbicAcc(s.getUbicacion().getNombre()));
            ua.rollos += rollos;
            ua.peso = ua.peso.add(peso);

            String artKey = s.getArticulo().getTipoTela().getNombre() + "|" + s.getArticulo().getTitulo().getValor();
            LineaAcc la = ua.lineas.computeIfAbsent(artKey, k -> new LineaAcc(
                    s.getArticulo().getTipoTela().getNombre(), s.getArticulo().getTitulo().getValor()));
            la.rollos += rollos;
            la.peso = la.peso.add(peso);
        }

        int maxRollos = porColor.values().stream().mapToInt(c -> c.rollos).max().orElse(1);
        if (maxRollos <= 0) maxRollos = 1;

        List<PorColor> colores = new ArrayList<>();
        for (ColorAcc ca : porColor.values()) {
            List<PorUbicacion> ubis = new ArrayList<>();
            for (UbicAcc ua : ca.ubicaciones.values()) {
                List<Linea> lineas = new ArrayList<>();
                for (LineaAcc la : ua.lineas.values()) {
                    lineas.add(new Linea(la.tipoTela, la.titulo, la.rollos, la.peso));
                }
                lineas.sort((a, b) -> Integer.compare(b.getRollos(), a.getRollos()));
                ubis.add(new PorUbicacion(ua.ubicacion, ua.rollos, ua.peso, lineas));
            }
            ubis.sort((a, b) -> Integer.compare(b.getRollos(), a.getRollos()));

            int ancho = (int) Math.round(ca.rollos * 100.0 / maxRollos);
            if (ancho < 3 && ca.rollos > 0) ancho = 3; // barra mínima visible
            colores.add(new PorColor(ca.colorId, ca.nombre, ca.codigoFastDye, colorHex(ca.nombre),
                    ca.rollos, ca.peso, ca.rollos < umbralBajo, ancho, ubis));
        }
        colores.sort((a, b) -> Integer.compare(b.getRollos(), a.getRollos()));

        return new Resumen(totalRollos, totalPeso, colores.size(), ubicacionIds.size(), colores);
    }

    /**
     * Color aproximado para la MUESTRA visual, deducido del nombre. Solo asigna un
     * tono cuando reconoce una palabra de color clara; si no, gris neutro (NO
     * inventa un color que pueda confundir, ej. un "rojo" que salga azul). No es el
     * color exacto de la tela — es una ayuda visual. Más adelante se puede sumar un
     * campo de color real (picker) por color en el catálogo.
     */
    static String colorHex(String nombre) {
        if (nombre == null) return NEUTRO;
        String n = nombre.toLowerCase();
        if (n.contains("botella")) return "#0b6b3a";
        if (n.contains("turques") || n.contains("turquez") || n.contains("aqua")) return "#17a2a2";
        if (n.contains("celeste")) return "#7ec8e3";
        if (n.contains("marino") || n.contains("azulino") || n.contains("petroleo")) return "#1b3a6b";
        if (n.contains("azul") || n.contains("bebe")) return "#2f6fb3";
        if (n.contains("rojo")) return "#d32f2f";
        if (n.contains("negro")) return "#222831";
        if (n.contains("blanco") || n.contains("cremos") || n.contains("crema") || n.contains("hueso")
                || n.contains("marfil")) return "#ece7d8";
        if (n.contains("vino") || n.contains("borgo") || n.contains("guinda") || n.contains("granate")) return "#7b1e3a";
        if (n.contains("verde") || n.contains("jade") || n.contains("menta") || n.contains("limon")) return "#2e8b57";
        if (n.contains("amarillo") || n.contains("oro") || n.contains("mostaza") || n.contains("canario")) return "#e6b800";
        if (n.contains("rosa") || n.contains("rosado") || n.contains("fucsia") || n.contains("palo")) return "#e57ea3";
        if (n.contains("naranja") || n.contains("mango") || n.contains("melon") || n.contains("coral")
                || n.contains("ladrillo")) return "#e8722a";
        if (n.contains("lila") || n.contains("morad") || n.contains("violet") || n.contains("uva")
                || n.contains("lavanda")) return "#9b59b6";
        if (n.contains("marron") || n.contains("cafe") || n.contains("cocoa") || n.contains("chocolat")
                || n.contains("camel") || n.contains("tierra")) return "#6b4226";
        if (n.contains("gris") || n.contains("plomo") || n.contains("acero") || n.contains("humo")) return "#8b96a3";
        if (n.contains("beige") || n.contains("arena") || n.contains("nude") || n.contains("khaki")
                || n.contains("caqui")) return "#d9c9a8";
        return NEUTRO;
    }

    private static final String NEUTRO = "#b8c0cc";

    // --- Acumuladores mutables (privados) ---
    private static final class ColorAcc {
        final Long colorId; final String nombre; final String codigoFastDye;
        int rollos = 0; BigDecimal peso = BigDecimal.ZERO;
        final Map<Long, UbicAcc> ubicaciones = new LinkedHashMap<>();
        ColorAcc(Long id, String n, String c) { colorId = id; nombre = n; codigoFastDye = c; }
    }
    private static final class UbicAcc {
        final String ubicacion; int rollos = 0; BigDecimal peso = BigDecimal.ZERO;
        final Map<String, LineaAcc> lineas = new LinkedHashMap<>();
        UbicAcc(String u) { ubicacion = u; }
    }
    private static final class LineaAcc {
        final String tipoTela; final String titulo; int rollos = 0; BigDecimal peso = BigDecimal.ZERO;
        LineaAcc(String t, String ti) { tipoTela = t; titulo = ti; }
    }
}
